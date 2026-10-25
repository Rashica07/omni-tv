"""OMNI library API v1: routing and rules, with no sockets in it, so it is tested directly. omniptvd turns a Result into an HTTP response.

  GET  /api/v1/health                   open: that the service is up (nothing private)
  GET  /api/v1/library                  roots (name, path, availability), counts, scan state, whether metadata is configured, genres
  GET  /api/v1/movies                   ?q= &sort=title|year|added|rating|runtime &genre= &only=continue|unwatched|watched|playable &offset= &limit=
  GET  /api/v1/movies/{id}              one film, with how it will play on this TV (verdict), subtitles and URLs
  GET  /api/v1/movies/{id}/stream       the original file, byte ranges (no transcoding)
  GET  /api/v1/movies/{id}/subtitles    list;   .../subtitles/{n}  one subtitle file (n is an index into that list)
  GET  /api/v1/movies/{id}/art/{poster|backdrop}
  POST /api/v1/movies/{id}/match        {"tmdb_id": N}  correct a wrong match;   POST .../refresh  look it up again
  GET  /api/v1/library/scan             scan progress;   POST /api/v1/library/scan   start one
  GET  /api/v1/progress                 everything with progress;   PUT /api/v1/progress/{id}   {"position_ms","duration_ms","device"}
Everything except health needs the read PIN (checked by the server before this module is called). Errors are {"error": code, "message": text}.
"""
import json, os, re, time

API = 1
ID = re.compile(r"^m_[0-9a-f]{16}$")
MAX_BODY = 4096


class Result:
    def __init__(self, code=200, body=None, file=None, ctype=None, headers=None):
        self.code, self.body, self.file, self.ctype, self.headers = code, body, file, ctype, headers or {}


def err(code, error, message): return Result(code, {"error": error, "message": message})


def _int(v, default, lo, hi):
    try: return max(lo, min(hi, int(v)))
    except (TypeError, ValueError): return default


class Api:
    def __init__(self, lib, started=None, art_fetch=None):
        self.lib, self.started = lib, started or time.time()

    # ---- shapes
    def summary(self, m):
        return {"id": m["id"], "title": m["title"], "year": m["year"], "overview": (m["overview"] or "")[:300], "genres": [g for g in (m["genres"] or "").split(", ") if g], "runtime_min": m["runtime"] or round((m["duration_ms"] or 0) / 60000),
                "rating": m["rating"], "matched": bool(m["matched"]), "poster": self.art_url(m, "poster"), "tmdb_id": m["tmdb_id"], "tmdb_poster": m["poster"], "tmdb_backdrop": m["backdrop"], "added": m["added"], "duration_ms": m["duration_ms"], "verdict": m["verdict"],
                "position_ms": m.get("pos_ms") or 0, "watched": bool(m.get("watched")), "last_played": m.get("played_at"), "container": m["container"], "video": m["vcodec"], "audio": m["acodec"]}

    def art_url(self, m, kind):
        if kind == "poster" and (m["local_poster"] or (m["poster"] and self.lib.tmdb)): return "/api/v1/movies/%s/art/poster" % m["id"]
        if kind == "backdrop" and (m["local_backdrop"] or (m["backdrop"] and self.lib.tmdb)): return "/api/v1/movies/%s/art/backdrop" % m["id"]
        return None

    def detail(self, m):
        d = self.summary(m)
        subs = json.loads(m["subs_json"] or "[]")
        d.update({"overview": m["overview"], "cast": json.loads(m["cast_json"] or "[]"), "director": m["director"], "tmdb_id": m["tmdb_id"], "file": os.path.basename(m["rel"]), "size": m["size"], "width": m["w"], "height": m["h"],
                  "backdrop": self.art_url(m, "backdrop"), "verdict_note": m["verdict_note"], "match_note": m["match_note"],
                  "playable": m["verdict"] in ("direct", "maybe") and m["status"] == "ok", "status": m["status"],
                  "stream": "/api/v1/movies/%s/stream" % m["id"], "subtitles": [{"index": i, "lang": s["lang"], "forced": s["forced"], "url": "/api/v1/movies/%s/subtitles/%d" % (m["id"], i)} for i, s in enumerate(subs)]})
        return d

    # ---- routing
    def handle(self, method, path, query, body=b""):
        q = {k: v[-1] for k, v in query.items()} if isinstance(query, dict) else {}
        parts = [p for p in path.split("/") if p]
        if parts[:2] != ["api", "v1"]: return err(404, "not_found", "unknown path")
        parts = parts[2:]
        try:
            return self._route(method, parts, q, body)
        except Exception as e:                                                   # a bug must be a 500 answer, never a dead connection
            return err(500, "server_error", "internal error (%s)" % type(e).__name__)

    def _route(self, method, p, q, body):
        lib = self.lib
        if p == ["health"] and method == "GET": return Result(200, {"service": "omniptvd", "api": API, "ok": True, "uptime_s": int(time.time() - self.started)})
        if p == ["library"] and method == "GET":
            return Result(200, {"api": API, "roots": lib.scan_status()["roots"], "counts": lib.counts(), "scan": lib.scan_status(), "metadata": lib.tmdb is not None, "genres": lib.genres()})
        if p == ["library", "scan"]:
            if method == "GET": return Result(200, lib.scan_status())
            if method == "POST": started = lib.start_scan(); return Result(202 if started else 200, {"started": started, "scan": lib.scan_status()})
        if p == ["movies"] and method == "GET":
            off, lim = _int(q.get("offset"), 0, 0, 10**7), _int(q.get("limit"), 50, 1, 200)
            sort = q.get("sort", "title")
            if sort not in ("title", "year", "added", "rating", "runtime"): return err(400, "bad_request", "unknown sort")
            only = q.get("only", "")
            if only not in ("", "continue", "unwatched", "watched", "playable"): return err(400, "bad_request", "unknown filter")
            total, rows = lib.list(q.get("q", "")[:80], sort, q.get("genre", "")[:40], only, off, lim)
            return Result(200, {"total": total, "offset": off, "limit": lim, "items": [self.summary(r) for r in rows]})
        if len(p) >= 2 and p[0] == "movies":
            mid = p[1]
            if not ID.match(mid): return err(400, "bad_id", "that is not a film id")
            m = lib.get(mid)
            if m is None: return err(404, "not_found", "no such film")
            rest = p[2:]
            if not rest and method == "GET": return Result(200, self.detail(m))
            if rest == ["stream"] and method == "GET":
                if m["status"] != "ok": return err(404, "file_missing", "the file is not on the server any more")
                path = lib.file_path(mid)
                if path is None: return err(404, "file_unavailable", "the file cannot be read right now (is the drive connected?)")
                return Result(200, file=path, ctype=_ctype(path), headers={"X-Omni-Verdict": m["verdict"], "X-Omni-Verdict-Note": (m["verdict_note"] or "")[:200].encode("ascii", "replace").decode()})
            if rest == ["subtitles"] and method == "GET": return Result(200, {"items": self.detail(m)["subtitles"]})
            if len(rest) == 2 and rest[0] == "subtitles" and method == "GET":
                subs = json.loads(m["subs_json"] or "[]")
                if not rest[1].isdigit() or int(rest[1]) >= len(subs): return err(404, "not_found", "no such subtitle")
                path = lib.sidecar_path(mid, subs[int(rest[1])]["file"])
                return Result(200, file=path, ctype="text/vtt" if path.endswith(".vtt") else "application/x-subrip") if path else err(404, "file_unavailable", "the subtitle file cannot be read")
            if len(rest) == 2 and rest[0] == "art" and rest[1] in ("poster", "backdrop") and method == "GET": return self._art(m, rest[1])
            if rest == ["match"] and method == "POST":
                try: tid = int(json.loads(body or b"{}").get("tmdb_id"))
                except (ValueError, TypeError, AttributeError): return err(400, "bad_request", "send {\"tmdb_id\": number}")
                e = lib.set_match(mid, tid)
                return err(503 if e and "reached" in e else 400 if e else 200, "match_failed", e) if e else Result(200, self.detail(lib.get(mid)))
            if rest == ["refresh"] and method == "POST":
                e = lib.refresh(mid)
                return err(503, "provider", e) if e else Result(200, self.detail(lib.get(mid)))
        if p == ["progress"] and method == "GET": return Result(200, {"items": lib.progress_all()})
        if len(p) == 2 and p[0] == "progress" and method == "PUT":
            if not ID.match(p[1]): return err(400, "bad_id", "that is not a film id")
            try:
                d = json.loads(body or b"{}")
                pos, dur = int(d["position_ms"]), int(d.get("duration_ms") or 0)
                dev = str(d.get("device", ""))[:40]
                if pos < 0 or dur < 0 or pos > 10**10: raise ValueError
            except (ValueError, TypeError, KeyError, AttributeError):
                return err(400, "bad_request", "send {\"position_ms\": number, \"duration_ms\": number}")
            r = lib.put_progress(p[1], pos, dur, dev)
            return Result(200, r) if r is not None else err(404, "not_found", "no such film")
        return err(404 if method in ("GET", "HEAD") else 405, "not_found" if method in ("GET", "HEAD") else "method_not_allowed", "no such endpoint")

    def _art(self, m, kind):
        local = m["local_poster"] if kind == "poster" else m["local_backdrop"]
        if local:
            path = self.lib.sidecar_path(m["id"], local)
            if path: return Result(200, file=path, ctype=_ctype(path))
        tpath = m["poster"] if kind == "poster" else m["backdrop"]
        if tpath and self.lib.tmdb:
            try:
                data = self.lib.tmdb.image(tpath, "w500" if kind == "poster" else "w1280")
            except Exception:
                return err(503, "provider", "the artwork could not be fetched right now")
            if data: return Result(200, data, ctype="image/jpeg", headers={"Cache-Control": "max-age=86400"})
        return err(404, "no_art", "no %s for this film" % kind)


def _ctype(path):
    e = path.rsplit(".", 1)[-1].lower()
    return {"mp4": "video/mp4", "m4v": "video/mp4", "mov": "video/quicktime", "mkv": "video/x-matroska", "webm": "video/webm", "ts": "video/mp2t", "jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "vtt": "text/vtt", "srt": "application/x-subrip"}.get(e, "application/octet-stream")
