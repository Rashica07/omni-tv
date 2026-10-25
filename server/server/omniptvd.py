#!/usr/bin/env python3
"""omniptvd: a tiny catalogue + health service for OMNIPTV. Standard library only, no transcoding, no stream relaying.

Serves (LAN only; bind address and port come from the config file):
  GET /health            JSON: service state, uptime, catalogue age, probe summary
  GET /catalog.json      the validated channel catalogue with the latest probe status per channel
  GET /playlist.m3u      M3U of enabled channels (direct upstream URLs, tvg-id/name/logo), UTF-8
  GET /epg.xml           cached XMLTV when an EPG source is configured; 404 otherwise (never invented)
  GET /status            JSON system stats (load, memory, temperature, disk, battery); needs the read PIN
  GET /media/<path>      read-only files from media_root with Range support; a directory returns a JSON listing; needs the read PIN
  POST /probe            admin: re-probe now (needs the token from the config)
Read access (/status, /media) needs "Authorization: Bearer <PIN>" (the 8-digit PIN in read_token_file); 10 wrong tries from one address lock it out for 60 s.
The TV never needs this service to play: the app falls back to its own copy of the catalogue.
"""
import hmac, json, mimetypes, os, re, sys, threading, time, urllib.parse, urllib.request, ssl, signal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))        # -I drops the script folder from the path; the library modules live beside this file
import api as libapi, library

CONF = os.environ.get("OMNIPTV_CONFIG", "/etc/omniptv/config.json")
START = time.time()
LOCK = threading.Lock()
STATE = {"catalog": [], "probed": 0, "probing": False}
UA = "Mozilla/5.0 (Linux; Android 11; TV) OMNIPTV"
SAFE = ("http", "https")

def load_conf():
    c = {"bind": "0.0.0.0", "port": 8765, "catalog": "/var/lib/omniptv/catalog.json", "state": "/var/lib/omniptv/state.json",
         "epg_source": "", "epg_cache": "/var/lib/omniptv/epg.xml", "probe_every_s": 1800, "admin_token_file": "/etc/omniptv/admin.token", "read_token_file": "/etc/omniptv/read.pin", "media_root": ""}
    try:
        c.update(json.load(open(CONF)))
    except FileNotFoundError:
        pass
    return c

def valid_url(u):
    p = urllib.parse.urlparse(u)
    return p.scheme in SAFE and bool(p.hostname) and len(u) < 600 and not re.search(r"[\s<>\"']", u)

def clean(doc):
    """Imported catalogues are untrusted: schema 2 only, known fields only, bounded, http(s) URLs only."""
    out = []
    if not isinstance(doc, dict) or doc.get("schema") != 2:
        return out
    for r in doc.get("channels", [])[:5000]:
        if not isinstance(r, dict) or not str(r.get("id", "")).strip() or not str(r.get("name", "")).strip():
            continue
        streams = []
        for s in (r.get("streams") or [])[:20]:
            if isinstance(s, dict) and valid_url(str(s.get("url", ""))):
                streams.append({"url": s["url"], "quality": str(s.get("quality") or "")[:12], "tier": str(s.get("tier", "C"))[:1],
                                "needs_referrer": bool(s.get("needs_referrer")), "enabled": bool(s.get("enabled")),
                                "probe": str(s.get("probe", "unchecked"))[:16], "checked": str(s.get("checked", ""))[:20]})
        if streams:
            out.append({"id": str(r["id"])[:80], "name": str(r["name"])[:80], "alt": [str(x)[:80] for x in (r.get("alt") or [])[:10]],
                        "country": str(r.get("country", ""))[:3], "languages": [str(x)[:8] for x in (r.get("languages") or [])[:6]],
                        "categories": [str(x)[:24] for x in (r.get("categories") or [])[:6]],
                        "website": r["website"] if valid_url(str(r.get("website") or "")) else "", "streams": streams})
    return out

CTX = ssl.create_default_context()
MEDIA_EXT = {".mkv", ".mp4", ".m4v", ".avi", ".mov", ".webm", ".ts", ".mpg", ".mpeg", ".srt", ".vtt", ".ass", ".nfo", ".jpg", ".jpeg", ".png"}
FAILS = {}   # address -> (count, since)

def locked(addr):
    n, t = FAILS.get(addr, (0, 0))
    if time.time() - t >= 60: FAILS.pop(addr, None); return False
    return n >= 10

def fail(addr):
    n, t = FAILS.get(addr, (0, time.time())); FAILS[addr] = (n + 1, t)

def read_file(path, default=""):
    try:
        with open(path) as f: return f.read(4096).strip()
    except OSError:
        return default

NET = {"t": 0.0, "rx": 0, "tx": 0}

def net_status():
    """Throughput of the busiest real interface since the previous /status call (needs two readings, so the first answer has no rate). Link speed when the driver reports it."""
    best = None
    try:
        for line in open("/proc/net/dev").read().splitlines()[2:]:
            name, _, rest = line.partition(":"); name = name.strip(); f = rest.split()
            if name == "lo" or name.startswith(("docker", "veth", "br-", "virbr")) or len(f) < 16: continue
            rx, tx = int(f[0]), int(f[8])
            if best is None or rx + tx > best[1] + best[2]: best = (name, rx, tx)
    except (OSError, ValueError):
        return None
    if best is None: return None
    now = time.time(); out = {"iface": best[0], "rx_bps": None, "tx_bps": None, "link_mbps": None}
    sp = read_file("/sys/class/net/%s/speed" % best[0])
    if sp.lstrip("-").isdigit() and int(sp) > 0: out["link_mbps"] = int(sp)
    if NET["t"] and now - NET["t"] >= 1.0 and best[1] >= NET["rx"] and best[2] >= NET["tx"]:
        out["rx_bps"] = int((best[1] - NET["rx"]) / (now - NET["t"])); out["tx_bps"] = int((best[2] - NET["tx"]) / (now - NET["t"]))
    if not NET["t"] or now - NET["t"] >= 1.0: NET.update(t=now, rx=best[1], tx=best[2])
    return out

def system_status(conf):
    st = {"uptime_s": int(float(read_file("/proc/uptime", "0 0").split()[0])), "load": [float(x) for x in read_file("/proc/loadavg", "0 0 0").split()[:3]], "cpus": os.cpu_count() or 1}
    mem = {}
    for line in open("/proc/meminfo").read().splitlines():
        k, _, v = line.partition(":")
        if k in ("MemTotal", "MemAvailable"): mem[k] = int(v.split()[0]) * 1024
    st["mem_total"], st["mem_available"] = mem.get("MemTotal", 0), mem.get("MemAvailable", 0)
    temps = []
    for i in range(0, 12):
        v = read_file("/sys/class/thermal/thermal_zone%d/temp" % i)
        if v.lstrip("-").isdigit(): temps.append(int(v) / 1000.0)
    st["temp_c"] = max(temps) if temps else None
    disks = {}
    for name, path in (("state", os.path.dirname(conf["state"])), ("media", conf.get("media_root") or "")):
        if path and os.path.isdir(path):
            u = os.statvfs(path); disks[name] = {"total": u.f_blocks * u.f_frsize, "free": u.f_bavail * u.f_frsize}
    st["disks"] = disks
    st["media_root"] = conf.get("media_root") or ""
    st["net"] = net_status()
    st["version"] = "omniptvd 1"
    bat = None
    for d in ("BAT0", "BAT1"):
        cap = read_file("/sys/class/power_supply/%s/capacity" % d)
        if cap.isdigit(): bat = {"percent": int(cap), "status": read_file("/sys/class/power_supply/%s/status" % d)}; break
    st["battery"] = bat
    st["ac"] = read_file("/sys/class/power_supply/AC/online", read_file("/sys/class/power_supply/ADP1/online", "")) == "1"
    return st

def safe_media_path(root, rel):
    """Resolve rel under root; refuse anything that leaves it (.., absolute, symlinks to elsewhere)."""
    root = os.path.realpath(root)
    full = os.path.realpath(os.path.join(root, urllib.parse.unquote(rel).lstrip("/")))
    return full if full == root or full.startswith(root + os.sep) else None

def probe(url):
    """Manifest + one real media segment. A 200 alone is not 'playing'."""
    try:
        def get(u, n):
            with urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}), timeout=10, context=CTX) as x:
                return x.read(n)
        txt = get(url, 200000).decode("utf8", "replace")
        if "#EXTM3U" not in txt:
            return "fails"
        base = url
        if "#EXT-X-STREAM-INF" in txt:
            base = urllib.parse.urljoin(url, [l for l in txt.splitlines() if l and not l.startswith("#")][0])
            txt = get(base, 200000).decode("utf8", "replace")
        segs = [l for l in txt.splitlines() if l and not l.startswith("#")]
        return "plays" if segs and len(get(urllib.parse.urljoin(base, segs[-1]), 600000)) > 20000 else "fails"
    except urllib.error.HTTPError as e:
        return "blocked-%d" % e.code if e.code in (401, 403, 451) else "fails"
    except Exception:
        return "fails"

def probe_all(conf):
    with LOCK:
        if STATE["probing"]:
            return
        STATE["probing"] = True
    try:
        for ch in list(STATE["catalog"]):
            for st in ch["streams"]:
                if st["enabled"] and not st["needs_referrer"]:
                    st["probe"] = probe(st["url"])
                    st["checked"] = time.strftime("%Y-%m-%d %H:%M", time.gmtime())
        STATE["probed"] = time.time()
        json.dump(STATE["catalog"], open(conf["state"], "w"))
    finally:
        STATE["probing"] = False

def loop(conf):
    while True:
        probe_all(conf)
        refresh_epg(conf)
        time.sleep(conf["probe_every_s"])

def refresh_epg(conf):
    src = conf.get("epg_source", "")
    if not src or not valid_url(src):
        return
    try:
        with urllib.request.urlopen(urllib.request.Request(src, headers={"User-Agent": UA}), timeout=30, context=CTX) as x:
            data = x.read(40_000_000)
        if b"<tv" in data[:2000]:
            open(conf["epg_cache"] + ".tmp", "wb").write(data)
            os.replace(conf["epg_cache"] + ".tmp", conf["epg_cache"])   # a failed refresh leaves the last good file in place
    except Exception as e:
        print("epg refresh failed:", type(e).__name__, file=sys.stderr)

API = None

def make_handler(conf, token, pin):
    class H(BaseHTTPRequestHandler):
        server_version = "omniptvd"
        def log_message(self, f, *a): sys.stderr.write("%s %s\n" % (self.address_string(), f % a))
        def send(self, code, body, ctype="application/json"):
            b = body if isinstance(body, bytes) else body.encode()
            self.send_response(code); self.send_header("Content-Type", ctype + "; charset=utf-8"); self.send_header("Content-Length", str(len(b)))
            self.send_header("Cache-Control", "no-store"); self.end_headers(); self.wfile.write(b)
        def do_GET(self):
            p = self.path.split("?")[0]
            cat = STATE["catalog"]
            if p == "/status" or p == "/media" or p.startswith("/media/"):
                return self.read_only(p)
            if p.startswith("/api/v1"):
                return self.api("GET")
            if p == "/health":
                n = [c for c in cat if any(s["enabled"] for s in c["streams"])]
                self.send(200, json.dumps({"service": "omniptvd", "ok": True, "uptime_s": int(time.time() - START), "channels": len(cat), "enabled": len(n),
                    "playing": sum(any(s["probe"] == "plays" for s in c["streams"] if s["enabled"]) for c in n), "last_probe": STATE["probed"] or None, "probing": STATE["probing"],
                    "epg": os.path.exists(conf["epg_cache"])}))
            elif p == "/catalog.json":
                self.send(200, json.dumps({"schema": 2, "channels": cat}, ensure_ascii=False))
            elif p == "/playlist.m3u":
                lines = ["#EXTM3U"]
                for c in cat:
                    for st in c["streams"]:
                        if st["enabled"] and st["probe"] == "plays" and not st["needs_referrer"]:
                            lines.append('#EXTINF:-1 tvg-id="%s" group-title="%s",%s' % (re.sub(r"[^\w.\-]", "_", c["id"]), c["country"], re.sub(r"[\r\n,]", " ", c["name"])))
                            lines.append(st["url"])
                            break
                self.send(200, "\n".join(lines) + "\n", "audio/x-mpegurl")
            elif p == "/epg.xml" and os.path.exists(conf["epg_cache"]):
                self.send(200, open(conf["epg_cache"], "rb").read(), "application/xml")
            else:
                self.send(404, '{"error":"not found"}')
        def authorised(self):
            addr = self.client_address[0]
            if locked(addr) or not pin: return False
            if hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + pin): return True
            fail(addr); return False
        def read_only(self, p):
            if not self.authorised(): return self.send(401, '{"error":"unauthorized"}')
            if p == "/status": return self.send(200, json.dumps(system_status(conf)))
            root = conf.get("media_root") or ""
            if not root or not os.path.isdir(root): return self.send(404, '{"error":"no media_root configured"}')
            full = safe_media_path(root, p[len("/media"):])
            if full is None or not os.path.exists(full): return self.send(404, '{"error":"not found"}')
            rroot = os.path.realpath(root)
            if os.path.isdir(full):
                items = []
                for n in sorted(os.listdir(full), key=str.lower):
                    fp = os.path.realpath(os.path.join(full, n))
                    if n.startswith(".") or not fp.startswith(rroot + os.sep): continue
                    if os.path.isdir(fp): items.append({"name": n, "dir": True})
                    elif os.path.splitext(n)[1].lower() in MEDIA_EXT:
                        stt = os.stat(fp); items.append({"name": n, "dir": False, "size": stt.st_size, "mtime": int(stt.st_mtime)})
                rel = os.path.relpath(full, rroot)
                return self.send(200, json.dumps({"path": "/" if rel == "." else "/" + rel, "items": items}, ensure_ascii=False))
            if os.path.splitext(full)[1].lower() not in MEDIA_EXT: return self.send(404, '{"error":"not found"}')
            return self.send_file(full, mimetypes.guess_type(full)[0] or "application/octet-stream")
        def send_file(self, full, ctype, extra=None):
            """The file, honouring one byte range. Used by /media and by the library stream; never takes a path from the request itself."""
            size = os.path.getsize(full); start, end = 0, size - 1
            m = re.match(r"bytes=(\d*)-(\d*)$", self.headers.get("Range", ""))
            if m and (m.group(1) or m.group(2)):
                if m.group(1): start = int(m.group(1)); end = int(m.group(2)) if m.group(2) else size - 1
                else: start = max(0, size - int(m.group(2)))
                end = min(end, size - 1)
                if start > end or start >= size:
                    self.send_response(416); self.send_header("Content-Range", "bytes */%d" % size); self.send_header("Content-Length", "0"); self.end_headers(); return
            else:
                m = None
            code = 206 if m else 200
            self.send_response(code)
            self.send_header("Content-Type", ctype)
            for k, v in (extra or {}).items(): self.send_header(k, v)
            self.send_header("Accept-Ranges", "bytes"); self.send_header("Content-Length", str(end - start + 1))
            if code == 206: self.send_header("Content-Range", "bytes %d-%d/%d" % (start, end, size))
            self.end_headers()
            try:
                with open(full, "rb") as f:
                    f.seek(start); left = end - start + 1
                    while left > 0:
                        b = f.read(min(262144, left))
                        if not b: break
                        self.wfile.write(b); left -= len(b)
            except (BrokenPipeError, ConnectionResetError):
                pass
        def api(self, method):
            parsed = urllib.parse.urlsplit(self.path)
            if API is None: return self.send(503, '{"error":"library_off","message":"the media library is not enabled on this server"}')
            if parsed.path.rstrip("/") != "/api/v1/health" and not self.authorised(): return self.send(401, '{"error":"unauthorized","message":"a valid PIN is needed"}')
            n = int(self.headers.get("Content-Length", "0") or 0) if method != "GET" else 0
            if n > 4096: return self.send(413, '{"error":"too_large","message":"request body too large"}')
            body = self.rfile.read(n) if n else b""
            r = API.handle(method, parsed.path, urllib.parse.parse_qs(parsed.query), body)
            if r.file: return self.send_file(r.file, r.ctype or "application/octet-stream", r.headers)
            if isinstance(r.body, (bytes, bytearray)):
                self.send_response(r.code); self.send_header("Content-Type", r.ctype or "application/octet-stream"); self.send_header("Content-Length", str(len(r.body)))
                for k, v in r.headers.items(): self.send_header(k, v)
                self.end_headers(); self.wfile.write(r.body); return
            self.send(r.code, json.dumps(r.body, ensure_ascii=False))
        def do_PUT(self):
            if self.path.startswith("/api/v1"): return self.api("PUT")
            self.send(404, '{"error":"not found"}')
        def do_POST(self):
            if self.path.startswith("/api/v1"): return self.api("POST")
            auth = self.headers.get("Authorization", "")
            if self.path != "/probe" or not token or not hmac.compare_digest(auth, "Bearer " + token):
                return self.send(401, '{"error":"unauthorized"}')
            threading.Thread(target=probe_all, args=(conf,), daemon=True).start()
            self.send(202, '{"started":true}')
    return H

def main():
    conf = load_conf()
    STATE["catalog"] = clean(json.load(open(conf["catalog"])))
    try:
        saved = {st["url"]: st for c in json.load(open(conf["state"])) for st in c.get("streams", [])}
        for c in STATE["catalog"]:
            for st in c["streams"]:
                if st["url"] in saved: st["probe"], st["checked"] = saved[st["url"]]["probe"], saved[st["url"]]["checked"]
    except Exception:
        pass
    try: token = open(conf["admin_token_file"]).read().strip()
    except OSError: token = ""
    pin = read_file(conf["read_token_file"])
    if not re.fullmatch(r"\d{8}", pin): pin = ""            # without a valid PIN the read endpoints stay closed
    global API
    roots = conf.get("library_roots") or ([{"name": "Movies", "path": os.path.join(conf["media_root"], "Movies")}] if conf.get("media_root") else [])
    if roots:
        data_dir = conf.get("library_dir") or os.path.join(os.path.dirname(os.path.abspath(conf["state"])), "library")
        os.makedirs(data_dir, exist_ok=True)
        lib = library.Library(os.path.join(data_dir, "library.db"), roots, data_dir, library.load_tmdb(conf, data_dir))
        API = libapi.Api(lib)
        lib.start_scan()
        threading.Thread(target=lambda: [time.sleep(conf.get("scan_every_s", 900)) or lib.start_scan() for _ in iter(int, 1)], daemon=True).start()
    threading.Thread(target=loop, args=(conf,), daemon=True).start()
    srv = ThreadingHTTPServer((conf["bind"], conf["port"]), make_handler(conf, token, pin))
    signal.signal(signal.SIGTERM, lambda *a: threading.Thread(target=srv.shutdown).start())
    print("omniptvd listening on %s:%d, %d channels" % (conf["bind"], conf["port"], len(STATE["catalog"])), file=sys.stderr)
    srv.serve_forever()

if __name__ == "__main__":
    main()
