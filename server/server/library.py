"""OMNI personal media library: a persistent index of the films on this machine.

* Roots are configured folders (never hardcoded). A scan walks them, adds new files, notices changed, renamed, moved and missing ones, and keeps every id stable where it can.
  A scan NEVER deletes or modifies a media file, and a vanished file is only marked missing (so an unplugged drive or a briefly unmounted folder does not wipe the library or its watch history).
* Containers and codecs are read from file headers (probe.py), so the TV can be told honestly whether a file is likely to play.
* Metadata (TMDB when a token is configured) is matched from names and cached on disk; local poster/backdrop/nfo files override it; an unmatched film is still listed by its file name.
* Playback progress is stored per film, shared by every device.
Standard library only. All database access goes through one lock; the scan and the metadata work run on their own threads and never hold it for long, so streaming requests are never blocked by them.
"""
import hashlib, json, os, re, sqlite3, threading, time, xml.etree.ElementTree as ET

import medianame, mediaprobe as probe

MIN_RENAME_SIZE = 1_000_000
MAX_FILES = 20000
MAX_DEPTH = 6
WATCHED_AT = 0.90
MIN_SAVE_MS = 60_000

SCHEMA = """
CREATE TABLE IF NOT EXISTS movie(
  id TEXT PRIMARY KEY, root TEXT NOT NULL, rel TEXT NOT NULL, size INTEGER, mtime INTEGER,
  title TEXT, fold TEXT DEFAULT '', year INTEGER, tmdb_id INTEGER, overview TEXT DEFAULT '', genres TEXT DEFAULT '', runtime INTEGER DEFAULT 0, rating REAL DEFAULT 0,
  cast_json TEXT DEFAULT '[]', director TEXT DEFAULT '', poster TEXT, backdrop TEXT, local_poster TEXT, local_backdrop TEXT,
  container TEXT, vcodec TEXT, acodec TEXT, w INTEGER DEFAULT 0, h INTEGER DEFAULT 0, duration_ms INTEGER DEFAULT 0,
  verdict TEXT DEFAULT 'unknown', verdict_note TEXT DEFAULT '', subs_json TEXT DEFAULT '[]',
  status TEXT DEFAULT 'ok', matched INTEGER DEFAULT 0, match_note TEXT DEFAULT '', added INTEGER, scanned INTEGER,
  UNIQUE(root, rel));
CREATE TABLE IF NOT EXISTS progress(movie_id TEXT PRIMARY KEY, pos_ms INTEGER DEFAULT 0, dur_ms INTEGER DEFAULT 0, watched INTEGER DEFAULT 0, plays INTEGER DEFAULT 0, updated INTEGER, device TEXT DEFAULT '');
CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT);
"""


def movie_id(root, rel):
    return "m_" + hashlib.sha1((root + "\0" + rel).encode("utf-8")).hexdigest()[:16]


def _inside(path, root_real):
    return path == root_real or path.startswith(root_real + os.sep)


class Library:
    def __init__(self, db_path, roots, data_dir, tmdb=None, clock=time.time):
        """roots: [{'name': 'Movies', 'path': '/srv/omni-tv/Movies'}, ...]. tmdb: object with search(title, year) and details(id) (see Tmdb), or None."""
        self.roots = [{"name": r["name"], "path": r["path"]} for r in roots]
        self.data_dir = data_dir
        self.art_dir = os.path.join(data_dir, "art")
        os.makedirs(self.art_dir, exist_ok=True)
        self.tmdb = tmdb
        self.clock = clock
        self.lock = threading.RLock()
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        with self.lock:
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.executescript(SCHEMA)
            self.db.commit()
        self.state = {"state": "idle", "phase": "", "started": 0, "finished": 0, "files": 0, "added": 0, "changed": 0, "renamed": 0, "missing": 0, "identified": 0, "errors": []}
        self._scan_thread = None
        self._stop = threading.Event()

    # ------------------------------------------------------------------ scanning
    def scan_status(self):
        with self.lock:
            s = dict(self.state); s["errors"] = list(self.state["errors"])
            s["roots"] = [{"name": r["name"], "path": r["path"], "available": os.path.isdir(r["path"])} for r in self.roots]
            s["tmdb"] = self.tmdb is not None
            return s

    def start_scan(self):
        """Starts a scan in the background unless one is running. Returns True when one was started."""
        with self.lock:
            if self._scan_thread and self._scan_thread.is_alive():
                return False
            self._scan_thread = threading.Thread(target=self.scan, name="library-scan", daemon=True)
            self._scan_thread.start()
            return True

    def _err(self, text):
        with self.lock:
            if len(self.state["errors"]) < 20:
                self.state["errors"].append(text)

    def scan(self):
        with self.lock:
            self.state.update(state="scanning", phase="reading folders", started=self.clock(), files=0, added=0, changed=0, renamed=0, missing=0, identified=0, errors=[])
        seen_real = set()                                            # a file reachable through two overlapping roots is one film
        try:
            roots_real = []
            for r in self.roots:
                real = os.path.realpath(r["path"])
                if any(_inside(real, other) for other in roots_real):          # a root inside another root would list its films twice
                    self._err("%s is inside another library folder and was skipped" % r["name"]); continue
                roots_real.append(real)
                self._scan_root(r, real, seen_real)
            with self.lock:
                self.state.update(phase="identifying films")
            self.enrich()
        except Exception as e:                                       # a bad scan must never take the service down
            self._err("scan failed: %s" % type(e).__name__)
        finally:
            with self.lock:
                self.state.update(state="idle", phase="", finished=self.clock())
                self.db.execute("INSERT OR REPLACE INTO meta(k,v) VALUES('last_scan', ?)", (str(int(self.clock())),)); self.db.commit()

    def _walk(self, root_real):
        videos, dirs = {}, {}
        stack = [("", 0)]
        while stack and len(videos) < MAX_FILES and not self._stop.is_set():
            rel_dir, depth = stack.pop()
            full = os.path.join(root_real, rel_dir) if rel_dir else root_real
            try:
                with os.scandir(full) as it:
                    entries = list(it)
            except OSError as e:
                self._err("cannot read %s (%s)" % (rel_dir or "/", type(e).__name__)); continue
            names = set()
            for en in entries:
                if en.name.startswith("."): continue
                rel = (rel_dir + "/" + en.name) if rel_dir else en.name
                try:
                    if en.is_dir(follow_symlinks=False):
                        if depth < MAX_DEPTH: stack.append((rel, depth + 1))
                        continue
                    if not en.is_file(follow_symlinks=True): continue
                    if en.is_symlink() and not _inside(os.path.realpath(en.path), root_real): continue      # a link out of the library is not part of it
                    names.add(en.name)
                    if medianame.is_video(en.name):
                        st = en.stat()
                        videos[rel] = (st.st_size, int(st.st_mtime), os.path.realpath(en.path))
                except OSError:
                    continue
            dirs[rel_dir] = names
        return videos, dirs

    def _scan_root(self, root, root_real, seen_real):
        name = root["name"]
        if not os.path.isdir(root_real):
            self._err("%s is not available (is the drive connected?). Nothing was changed." % name); return
        videos, dirs = self._walk(root_real)
        with self.lock:
            self.state["files"] += len(videos)
        with self.lock:
            rows = {r["rel"]: r for r in self.db.execute("SELECT * FROM movie WHERE root=?", (name,))}
        # safety: a folder that suddenly lists nothing while it held films is "cannot read it", not "everything was deleted"
        if not videos and rows:
            self._err("%s lists no videos right now, so nothing was marked missing." % name); return
        now = int(self.clock())
        found = {rel: v for rel, v in videos.items() if v[2] not in seen_real}
        for rel, v in videos.items(): seen_real.add(v[2])
        vanished = {rel: r for rel, r in rows.items() if rel not in found and r["status"] != "gone"}
        new = {rel: v for rel, v in found.items() if rel not in rows}
        # renamed or moved: a "new" file with the size and time of one that vanished is the same film (it keeps its id, watch history and metadata)
        by_sig = {}
        for rel, r in vanished.items():
            if (r["size"] or 0) >= MIN_RENAME_SIZE: by_sig.setdefault((r["size"], r["mtime"]), []).append(rel)
        renamed = 0
        for rel in list(new):
            size, mt, _ = new[rel]
            cand = by_sig.get((size, mt))
            if size >= MIN_RENAME_SIZE and cand:
                old = cand.pop(0)
                with self.lock:
                    self.db.execute("UPDATE movie SET rel=?, status='ok', scanned=? WHERE root=? AND rel=?", (rel, now, name, old))
                    self.db.commit()
                rows[rel] = rows.pop(old); vanished.pop(old, None); new.pop(rel); renamed += 1
                self._refresh_sidecars(name, rel, dirs)
        added = changed = 0
        for rel, (size, mt, _) in found.items():
            row = rows.get(rel)
            if row is not None and row["size"] == size and row["mtime"] == mt and row["status"] == "ok":
                continue
            if row is None and rel not in new: continue
            info = probe.probe(os.path.join(root_real, rel))
            ver, note = probe.verdict(info, medianame.extension(rel))
            v0 = (info.get("video") or [{}])[0]
            sidecars = self._sidecars(rel, dirs)
            with self.lock:
                if row is None:
                    p = medianame.parse_movie(rel)
                    self.db.execute("INSERT INTO movie(id,root,rel,size,mtime,title,fold,year,container,vcodec,acodec,w,h,duration_ms,verdict,verdict_note,subs_json,local_poster,local_backdrop,status,added,scanned) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'ok',?,?)",
                                    (movie_id(name, rel), name, rel, size, mt, p["title"], medianame.fold(p["title"]), p["year"], info.get("container"), v0.get("codec", ""), ",".join(info.get("audio", [])), v0.get("w", 0), v0.get("h", 0),
                                     info.get("duration_ms", 0), ver, note, json.dumps(sidecars["subs"]), sidecars["poster"], sidecars["backdrop"], now, now))
                    added += 1
                else:
                    self.db.execute("UPDATE movie SET size=?, mtime=?, container=?, vcodec=?, acodec=?, w=?, h=?, duration_ms=?, verdict=?, verdict_note=?, subs_json=?, local_poster=?, local_backdrop=?, status='ok', scanned=? WHERE id=?",
                                    (size, mt, info.get("container"), v0.get("codec", ""), ",".join(info.get("audio", [])), v0.get("w", 0), v0.get("h", 0), info.get("duration_ms", 0), ver, note,
                                     json.dumps(sidecars["subs"]), sidecars["poster"], sidecars["backdrop"], now, row["id"]))
                    changed += 1
                self.db.commit()
        for rel, r in rows.items():                                      # unchanged films still pick up new subtitles or artwork dropped next to them
            if rel in found and r["size"] == found[rel][0] and r["mtime"] == found[rel][1]: self._refresh_sidecars(name, rel, dirs)
        missing = 0
        with self.lock:
            for rel, r in vanished.items():
                if rel in rows and rows[rel]["status"] != "missing":
                    self.db.execute("UPDATE movie SET status='missing', scanned=? WHERE id=?", (now, r["id"])); missing += 1
            self.db.commit()
            self.state["added"] += added; self.state["changed"] += changed; self.state["renamed"] += renamed; self.state["missing"] += missing

    def _sidecars(self, rel, dirs):
        folder = rel.rsplit("/", 1)[0] if "/" in rel else ""
        names = dirs.get(folder, set())
        base = medianame.strip_ext(rel.rsplit("/", 1)[-1])
        subs = []
        for n in sorted(names):
            if medianame.extension(n) not in medianame.SUB_EXT: continue
            stem = medianame.strip_ext(n)
            if stem.lower() != base.lower() and not stem.lower().startswith(base.lower() + "."): continue
            tags = [t.lower() for t in stem[len(base):].split(".") if t]
            lang = next((t for t in tags if re.fullmatch(r"[a-z]{2,3}", t) and t != "forced"), None)
            subs.append({"file": (folder + "/" if folder else "") + n, "lang": lang, "forced": "forced" in tags})
        def art(kinds):
            for k in kinds:
                for ext in ("jpg", "jpeg", "png"):
                    for cand in (k + "." + ext, base + "-" + k + "." + ext):
                        for n in names:
                            if n.lower() == cand.lower(): return (folder + "/" if folder else "") + n
            return None
        return {"subs": subs, "poster": art(["poster", "folder", "cover"]), "backdrop": art(["fanart", "backdrop", "background"])}

    def _refresh_sidecars(self, root, rel, dirs):
        sc = self._sidecars(rel, dirs)
        with self.lock:
            self.db.execute("UPDATE movie SET subs_json=?, local_poster=?, local_backdrop=? WHERE root=? AND rel=?", (json.dumps(sc["subs"]), sc["poster"], sc["backdrop"], root, rel)); self.db.commit()

    # ------------------------------------------------------------------ metadata
    def enrich(self, limit=200):
        """Matches films that are not identified yet, using the configured provider. Network failures leave them unmatched to be tried again next time; nothing is hidden."""
        if self.tmdb is None: return 0
        with self.lock:
            todo = [dict(r) for r in self.db.execute("SELECT id,root,rel,title,year FROM movie WHERE matched=0 AND status='ok' LIMIT ?", (limit,))]
        done = 0
        for r in todo:
            if self._stop.is_set(): break
            try:
                tid = self._nfo_id(r) or self._best(r["title"], r["year"])
                if tid is None:
                    with self.lock:
                        self.db.execute("UPDATE movie SET match_note=? WHERE id=?", ("no confident match for '%s'" % r["title"], r["id"])); self.db.commit()
                    continue
                self.apply_details(r["id"], tid); done += 1
                with self.lock: self.state["identified"] += 1
            except Exception as e:
                self._err("metadata lookup failed (%s); the film is still listed" % type(e).__name__)
                break                                              # the network or the provider is down: stop, retry at the next scan
        return done

    def _nfo_id(self, r):
        """A Kodi-style .nfo next to the film names its TMDB id: that beats any guess from the name."""
        for root in self.roots:
            if root["name"] != r["root"]: continue
            folder = os.path.join(os.path.realpath(root["path"]), r["rel"].rsplit("/", 1)[0]) if "/" in r["rel"] else os.path.realpath(root["path"])
            base = medianame.strip_ext(r["rel"].rsplit("/", 1)[-1])
            for n in (base + ".nfo", "movie.nfo"):
                p = os.path.join(folder, n)
                if os.path.isfile(p) and os.path.getsize(p) < 200_000:
                    try:
                        with open(p, encoding="utf-8", errors="replace") as f: txt = f.read()
                        if "<!DOCTYPE" in txt or "<!ENTITY" in txt: continue
                        node = ET.fromstring(txt.lstrip("﻿").strip())
                        if node.tag != "movie": continue
                        for u in node.iter("uniqueid"):
                            if (u.get("type") or "").lower() == "tmdb" and (u.text or "").strip().isdigit(): return int(u.text.strip())
                        t = node.findtext("tmdbid")
                        if t and t.strip().isdigit(): return int(t.strip())
                    except (ET.ParseError, OSError):
                        pass
        return None

    def _best(self, title, year):
        cands = self.tmdb.search(title, year) or (self.tmdb.search("%s %s" % (title, year), None) if year else [])
        return best_match(cands, title, year)

    def apply_details(self, mid, tmdb_id):
        d = self.tmdb.details(tmdb_id)
        if not d: raise LookupError("no details")
        with self.lock:
            self.db.execute("UPDATE movie SET tmdb_id=?, title=?, fold=?, year=COALESCE(?, year), overview=?, genres=?, runtime=?, rating=?, cast_json=?, director=?, poster=?, backdrop=?, matched=1, match_note='' WHERE id=?",
                            (tmdb_id, d["title"], medianame.fold(d["title"]), d.get("year"), d.get("overview", ""), ", ".join(d.get("genres", [])), d.get("runtime", 0), d.get("rating", 0), json.dumps(d.get("cast", [])), d.get("director", ""),
                             d.get("poster"), d.get("backdrop"), mid))
            self.db.commit()

    def set_match(self, mid, tmdb_id):
        """Manual correction: pin a film to a TMDB id (and fetch its details). Returns an error text or None."""
        if self.tmdb is None: return "no metadata provider is configured on the server"
        if self.get(mid) is None: return "no such film"
        try: self.apply_details(mid, int(tmdb_id))
        except (LookupError, ValueError): return "the provider has no film with that id"
        except Exception as e: return "the provider could not be reached (%s)" % type(e).__name__
        return None

    def refresh(self, mid):
        m = self.get(mid)
        if m is None: return "no such film"
        if m["tmdb_id"]: return self.set_match(mid, m["tmdb_id"])
        with self.lock:
            self.db.execute("UPDATE movie SET matched=0 WHERE id=?", (mid,)); self.db.commit()
        self.enrich(5)
        return None

    # ------------------------------------------------------------------ reading
    def get(self, mid):
        with self.lock:
            r = self.db.execute("SELECT m.*, p.pos_ms, p.dur_ms, p.watched, p.updated AS played_at FROM movie m LEFT JOIN progress p ON p.movie_id=m.id WHERE m.id=?", (mid,)).fetchone()
        return dict(r) if r else None

    def list(self, q="", sort="title", genre="", only="", offset=0, limit=50):
        """Films, paged. only: 'continue' | 'unwatched' | 'watched' | 'playable'. sort: title | year | added | rating | runtime."""
        where, args = ["m.status='ok'"], []
        words = medianame.fold(q).split()
        for w in words:
            where.append("(' ' || m.fold || ' ') LIKE ?"); args.append("% " + w + "%")
        if genre: where.append("(', ' || m.genres || ',') LIKE ?"); args.append("%, " + genre + ",%")
        if only == "continue": where.append("p.pos_ms > 0 AND COALESCE(p.watched,0)=0")
        elif only == "unwatched": where.append("COALESCE(p.watched,0)=0")
        elif only == "watched": where.append("p.watched=1")
        elif only == "playable": where.append("m.verdict IN ('direct','maybe')")
        order = {"title": "m.fold", "year": "m.year DESC, m.fold", "added": "m.added DESC, m.fold", "rating": "m.rating DESC, m.fold", "runtime": "m.duration_ms DESC, m.fold", "continue": "p.updated DESC"}.get(sort, "m.fold")
        if only == "continue": order = "p.updated DESC"
        sql = "FROM movie m LEFT JOIN progress p ON p.movie_id=m.id WHERE " + " AND ".join(where)
        with self.lock:
            total = self.db.execute("SELECT COUNT(*) " + sql, args).fetchone()[0]
            rows = self.db.execute("SELECT m.*, p.pos_ms, p.dur_ms, p.watched, p.updated AS played_at " + sql + " ORDER BY " + order + " LIMIT ? OFFSET ?", args + [max(1, min(200, limit)), max(0, offset)]).fetchall()
        return total, [dict(r) for r in rows]

    def genres(self):
        counts = {}
        with self.lock:
            for (g,) in self.db.execute("SELECT genres FROM movie WHERE status='ok' AND genres<>''"):
                for x in g.split(", "): counts[x] = counts.get(x, 0) + 1
        return [{"name": k, "count": v} for k, v in sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))]

    def counts(self):
        with self.lock:
            c = {s: n for s, n in self.db.execute("SELECT status, COUNT(*) FROM movie GROUP BY status")}
            unm = self.db.execute("SELECT COUNT(*) FROM movie WHERE status='ok' AND matched=0").fetchone()[0]
            last = self.db.execute("SELECT v FROM meta WHERE k='last_scan'").fetchone()
        return {"movies": c.get("ok", 0), "missing": c.get("missing", 0), "unmatched": unm, "last_scan": int(last[0]) if last else None}

    def file_path(self, mid):
        """The absolute path of a film's file, only if it is inside its configured root. Never takes a path from a request."""
        m = self.get(mid)
        if not m or m["status"] != "ok": return None
        for r in self.roots:
            if r["name"] == m["root"]:
                real = os.path.realpath(r["path"]); p = os.path.realpath(os.path.join(real, m["rel"]))
                return p if _inside(p, real) and os.path.isfile(p) else None
        return None

    def sidecar_path(self, mid, rel):
        """A subtitle or artwork file next to the film, by the name this index recorded for it: a request can only choose among those, never name a path."""
        m = self.get(mid)
        if not m: return None
        allowed = {s["file"] for s in json.loads(m["subs_json"] or "[]")} | {x for x in (m["local_poster"], m["local_backdrop"]) if x}
        if rel not in allowed: return None
        for r in self.roots:
            if r["name"] == m["root"]:
                real = os.path.realpath(r["path"]); p = os.path.realpath(os.path.join(real, rel))
                return p if _inside(p, real) and os.path.isfile(p) else None
        return None

    # ------------------------------------------------------------------ progress
    def put_progress(self, mid, pos_ms, dur_ms, device=""):
        """The save rules shared with the TV app: a film past 90% is watched and starts over; under a minute is not saved. Returns the stored state or None if there is no such film."""
        m = self.get(mid)
        if m is None: return None
        pos_ms, dur_ms = max(0, int(pos_ms)), max(0, int(dur_ms or m["duration_ms"] or 0))
        now = int(self.clock())
        with self.lock:
            cur = self.db.execute("SELECT * FROM progress WHERE movie_id=?", (mid,)).fetchone()
            if dur_ms > 0 and pos_ms >= dur_ms * WATCHED_AT:
                self.db.execute("INSERT OR REPLACE INTO progress(movie_id,pos_ms,dur_ms,watched,plays,updated,device) VALUES(?,?,?,?,?,?,?)", (mid, 0, dur_ms, 1, (cur["plays"] if cur else 0) + 1, now, device))
            elif pos_ms >= MIN_SAVE_MS:
                self.db.execute("INSERT OR REPLACE INTO progress(movie_id,pos_ms,dur_ms,watched,plays,updated,device) VALUES(?,?,?,?,?,?,?)", (mid, pos_ms, dur_ms, 0, cur["plays"] if cur else 0, now, device))
            self.db.commit()
            r = self.db.execute("SELECT * FROM progress WHERE movie_id=?", (mid,)).fetchone()
        return dict(r) if r else {"movie_id": mid, "pos_ms": 0, "dur_ms": dur_ms, "watched": 0, "plays": 0, "updated": None, "device": ""}

    def progress_all(self):
        with self.lock:
            return [dict(r) for r in self.db.execute("SELECT * FROM progress ORDER BY updated DESC")]

    def close(self):
        self._stop.set()
        with self.lock:
            self.db.close()


# ---------------------------------------------------------------------- matching
def best_match(cands, title, year):
    """The best candidate, or None when nothing is convincingly right: an unmatched film shown by its file name beats a wrong poster. Port of Cinema's Matching.best."""
    t = medianame.fold(title)
    best, best_s = None, -1e9
    for c in cands or []:
        names = [medianame.fold(c.get("title", "")), medianame.fold(c.get("original", ""))]
        s = 0.0
        if t in names: s += 10
        elif any(n.startswith(t) or (t.startswith(n) and len(n) >= 4) for n in names if n): s += 4
        if year and c.get("year"):
            d = abs(year - c["year"]); s += 6 if d == 0 else 3 if d == 1 else -6
        s += min(c.get("popularity", 0), 100) / 100.0
        if s > best_s: best, best_s = c, s
    return best["id"] if best is not None and best_s >= 9.0 else None


# ---------------------------------------------------------------------- the provider
class Tmdb:
    """TMDB v3 with an on-disk cache so nothing is asked twice. The credential comes from the environment or a file outside the repository, is only ever sent to api.themoviedb.org over https, and is never logged."""
    BASE = "https://api.themoviedb.org/3"
    IMAGE = "https://image.tmdb.org/t/p/"

    def __init__(self, credential, cache_dir, fetch=None, min_interval=0.3):
        self.cred = credential.strip()
        self.bearer = self.cred.startswith("eyJ")
        self.cache_dir = cache_dir
        os.makedirs(cache_dir, exist_ok=True)
        self.fetch = fetch or self._http
        self.min_interval = min_interval
        self._last = 0.0

    def _http(self, url, headers):
        import urllib.request
        if not url.startswith("https://api.themoviedb.org/") and not url.startswith("https://image.tmdb.org/"):
            raise ValueError("refusing to send the credential anywhere but TMDB")
        wait = self.min_interval - (time.time() - self._last)
        if wait > 0: time.sleep(wait)
        self._last = time.time()
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=15) as r:
            return r.read(8_000_000)

    def _get(self, path, **q):
        import urllib.parse
        if not self.bearer: q["api_key"] = self.cred
        q["language"] = "en-US"
        key = hashlib.sha1((path + json.dumps(sorted(q.items()), default=str).replace(self.cred, "")).encode()).hexdigest()
        cp = os.path.join(self.cache_dir, key + ".json")
        if os.path.isfile(cp):
            try:
                with open(cp) as f: return json.load(f)
            except (OSError, ValueError): pass
        hdr = {"Authorization": "Bearer " + self.cred} if self.bearer else {}
        data = json.loads(self.fetch(self.BASE + path + "?" + urllib.parse.urlencode(q), hdr))
        tmp = cp + ".tmp"
        with open(tmp, "w") as f: json.dump(data, f)
        os.replace(tmp, cp)
        return data

    def search(self, title, year):
        q = {"query": title}
        if year: q["year"] = year
        out = []
        for o in (self._get("/search/movie", **q).get("results") or [])[:10]:
            out.append({"id": o["id"], "title": o.get("title", ""), "original": o.get("original_title", ""), "year": int(o["release_date"][:4]) if (o.get("release_date") or "")[:4].isdigit() else None, "popularity": o.get("popularity", 0)})
        return out

    def details(self, tmdb_id):
        o = self._get("/movie/%d" % int(tmdb_id), append_to_response="credits")
        if not o or "id" not in o: return None
        credits = o.get("credits") or {}
        return {"title": o.get("title", ""), "year": int(o["release_date"][:4]) if (o.get("release_date") or "")[:4].isdigit() else None, "overview": o.get("overview", ""),
                "genres": [g["name"] for g in o.get("genres", [])], "runtime": o.get("runtime") or 0, "rating": o.get("vote_average") or 0,
                "cast": [c["name"] for c in (credits.get("cast") or [])[:8]], "director": next((c["name"] for c in (credits.get("crew") or []) if c.get("job") == "Director"), ""),
                "poster": o.get("poster_path"), "backdrop": o.get("backdrop_path")}

    def image(self, path, size="w500"):
        """Poster/backdrop bytes, cached on disk. Only TMDB's image host, only a plain path."""
        if not re.fullmatch(r"/[A-Za-z0-9_.-]{1,80}", path or ""): return None
        cp = os.path.join(self.cache_dir, "img_%s_%s" % (size, path.strip("/")))
        if os.path.isfile(cp):
            with open(cp, "rb") as f: return f.read()
        data = self.fetch(self.IMAGE + size + path, {})
        with open(cp + ".tmp", "wb") as f: f.write(data)
        os.replace(cp + ".tmp", cp)
        return data


def load_tmdb(conf, data_dir):
    """The provider if a credential is configured (environment OMNI_TMDB_TOKEN, or the file named by conf['tmdb_token_file']); None otherwise. Never reads a credential from the repository."""
    cred = os.environ.get("OMNI_TMDB_TOKEN", "")
    if not cred and conf.get("tmdb_token_file"):
        try:
            with open(conf["tmdb_token_file"]) as f: cred = f.read().strip()
        except OSError: cred = ""
    return Tmdb(cred, os.path.join(data_dir, "tmdb-cache")) if cred else None
