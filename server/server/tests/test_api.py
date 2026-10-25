"""End-to-end: the real server on a free port with a temporary library; every call is real HTTP."""
import json, os, socket, subprocess, sys, tempfile, time, unittest, urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from test_mediaprobe import mp4
SERVER = os.path.join(HERE, "..", "omniptvd.py")
PIN = "12345678"
FILM = mp4() + bytes(range(256)) * 4000          # valid header + ~1 MB


def free_port():
    s = socket.socket(); s.bind(("127.0.0.1", 0)); p = s.getsockname()[1]; s.close(); return p


class ApiTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(); t = cls.tmp.name
        cls.root = os.path.join(t, "media"); os.makedirs(os.path.join(cls.root, "Movies", "Heat (1995)"))
        cls.film = os.path.join(cls.root, "Movies", "Heat (1995)", "Heat (1995).mp4"); open(cls.film, "wb").write(FILM)
        open(os.path.join(cls.root, "Movies", "Heat (1995)", "Heat (1995).en.srt"), "w").write("1\n00:00:01,000 --> 00:00:02,000\nhi\n")
        open(os.path.join(t, "catalog.json"), "w").write(json.dumps({"schema": 2, "channels": []}))
        open(os.path.join(t, "read.pin"), "w").write(PIN)
        cls.port = free_port()
        json.dump({"bind": "127.0.0.1", "port": cls.port, "catalog": os.path.join(t, "catalog.json"), "state": os.path.join(t, "state.json"), "epg_cache": os.path.join(t, "epg.xml"),
                   "probe_every_s": 99999, "admin_token_file": os.path.join(t, "none"), "read_token_file": os.path.join(t, "read.pin"), "media_root": cls.root, "library_dir": os.path.join(t, "lib")},
                  open(os.path.join(t, "c.json"), "w"))
        cls.p = subprocess.Popen([sys.executable, "-I", SERVER], env={"OMNIPTV_CONFIG": os.path.join(t, "c.json"), "PATH": os.environ["PATH"]}, stderr=subprocess.DEVNULL)
        for _ in range(60):
            try: urllib.request.urlopen(cls.u("/api/v1/health")).read(); break
            except Exception: time.sleep(0.1)
        for _ in range(60):                                   # the first scan runs in the background
            if cls.get("/api/v1/movies")[1].get("total"): break
            time.sleep(0.1)

    @classmethod
    def tearDownClass(cls): cls.p.terminate(); cls.p.wait(); cls.tmp.cleanup()

    @classmethod
    def u(cls, path): return "http://127.0.0.1:%d%s" % (cls.port, path)

    @classmethod
    def call(cls, method, path, body=None, pin=PIN, headers=None):
        h = dict(headers or {})
        if pin: h["Authorization"] = "Bearer " + pin
        data = json.dumps(body).encode() if body is not None else None
        try:
            r = urllib.request.urlopen(urllib.request.Request(cls.u(path), data=data, method=method, headers=h)); return r.status, r.read(), r.headers
        except urllib.error.HTTPError as e: return e.code, e.read(), e.headers

    @classmethod
    def get(cls, path, **kw):
        c, b, h = cls.call("GET", path, **kw)
        try: return c, json.loads(b)
        except ValueError: return c, b

    def mid(self): return self.get("/api/v1/movies")[1]["items"][0]["id"]

    def test_health_is_open_everything_else_needs_the_pin(self):
        self.assertEqual(self.get("/api/v1/health", pin=None)[0], 200)
        for path in ("/api/v1/library", "/api/v1/movies"):      # few tries: ten wrong PINs lock the address out (that is tested in test_media)
            self.assertEqual(self.get(path, pin=None)[0], 401, path); self.assertEqual(self.get(path, pin="00000000")[0], 401, path)

    def test_library_and_listing(self):
        c, lib = self.get("/api/v1/library")
        self.assertEqual((c, lib["counts"]["movies"], lib["metadata"], lib["roots"][0]["available"]), (200, 1, False, True))
        c, ml = self.get("/api/v1/movies?q=heat")
        self.assertEqual((ml["total"], ml["items"][0]["title"], ml["items"][0]["year"], ml["items"][0]["verdict"]), (1, "Heat", 1995, "direct"))
        self.assertEqual(self.get("/api/v1/movies?q=zzz")[1]["total"], 0)
        self.assertEqual(self.get("/api/v1/movies?sort=bogus")[0], 400); self.assertEqual(self.get("/api/v1/movies?only=bogus")[0], 400)
        self.assertEqual(self.get("/api/v1/movies?limit=abc&offset=-5")[0], 200)

    def test_detail_and_ids(self):
        d = self.get("/api/v1/movies/" + self.mid())[1]
        self.assertEqual((d["playable"], d["file"], d["subtitles"][0]["lang"], d["stream"].endswith("/stream")), (True, "Heat (1995).mp4", "en", True))
        self.assertNotIn("/home", json.dumps(d)); self.assertNotIn(self.root, json.dumps(d))         # no server path leaks
        self.assertEqual(self.get("/api/v1/movies/m_0123456789abcdef")[0], 404)
        for bad in ("../etc/passwd", "m_xyz", "%2e%2e%2f", "m_0123456789abcdef/../x"): self.assertIn(self.get("/api/v1/movies/" + bad)[0], (400, 404), bad)

    def test_stream_ranges(self):
        sid = "/api/v1/movies/%s/stream" % self.mid()
        c, b, h = self.call("GET", sid)
        self.assertEqual((c, b, h["Accept-Ranges"]), (200, FILM, "bytes")); self.assertEqual(h["Content-Type"], "video/mp4")
        c, b, h = self.call("GET", sid, headers={"Range": "bytes=100-199"})
        self.assertEqual((c, b, h["Content-Range"]), (206, FILM[100:200], "bytes 100-199/%d" % len(FILM)))
        c, b, h = self.call("GET", sid, headers={"Range": "bytes=-50"}); self.assertEqual((c, b), (206, FILM[-50:]))
        c, b, h = self.call("GET", sid, headers={"Range": "bytes=%d-" % (len(FILM) - 10)}); self.assertEqual((c, b), (206, FILM[-10:]))
        self.assertEqual(self.call("GET", sid, headers={"Range": "bytes=999999999-"})[0], 416)
        self.assertEqual(self.call("GET", sid, pin=None)[0], 401)

    def test_subtitles_art_and_errors(self):
        mid = self.mid()
        c, b, h = self.call("GET", "/api/v1/movies/%s/subtitles/0" % mid); self.assertEqual(c, 200); self.assertIn(b"hi", b)
        self.assertEqual(self.get("/api/v1/movies/%s/subtitles/9" % mid)[0], 404); self.assertEqual(self.get("/api/v1/movies/%s/subtitles/x" % mid)[0], 404)
        self.assertEqual(self.get("/api/v1/movies/%s/art/poster" % mid)[0], 404)             # no provider, no local art: honest 404, not a fake image
        self.assertEqual(self.call("POST", "/api/v1/movies/%s/match" % mid, {"tmdb_id": 949})[0], 400)    # no provider configured
        self.assertEqual(self.get("/api/v1/nonsense")[0], 404); self.assertEqual(self.call("DELETE", "/api/v1/movies")[0] in (404, 405, 501), True)

    def test_progress_roundtrip_and_validation(self):
        mid = self.mid()
        c, r, _ = self.call("PUT", "/api/v1/progress/" + mid, {"position_ms": 1_200_000, "duration_ms": 5_500_000, "device": "tv"})
        self.assertEqual(c, 200); self.assertEqual(json.loads(r)["pos_ms"], 1_200_000)
        d = self.get("/api/v1/movies/" + mid)[1]; self.assertEqual(d["position_ms"], 1_200_000)
        self.assertEqual(self.get("/api/v1/movies?only=continue")[1]["total"], 1)
        self.assertEqual(self.get("/api/v1/progress")[1]["items"][0]["pos_ms"], 1_200_000)
        for body in ({}, {"position_ms": "x"}, {"position_ms": -1}, [1]): self.assertEqual(self.call("PUT", "/api/v1/progress/" + mid, body)[0], 400, body)
        self.assertEqual(self.call("PUT", "/api/v1/progress/m_0123456789abcdef", {"position_ms": 100000})[0], 404)
        self.assertEqual(self.call("PUT", "/api/v1/progress/" + mid, {"position_ms": 1}, pin=None)[0], 401)
        c, r, _ = self.call("PUT", "/api/v1/progress/" + mid, {"position_ms": 5_300_000, "duration_ms": 5_500_000}); self.assertEqual(json.loads(r)["watched"], 1)
        self.assertEqual(self.get("/api/v1/movies?only=continue")[1]["total"], 0)

    def test_scan_endpoint_and_new_file(self):
        extra = os.path.join(self.root, "Movies", "Alien (1979).mp4"); open(extra, "wb").write(FILM)
        c, r, _ = self.call("POST", "/api/v1/library/scan"); self.assertIn(c, (200, 202))
        for _ in range(60):
            if self.get("/api/v1/movies")[1]["total"] == 2 and self.get("/api/v1/library/scan")[1]["state"] == "idle": break
            time.sleep(0.1)
        self.assertEqual(self.get("/api/v1/movies")[1]["total"], 2)
        os.remove(extra); self.call("POST", "/api/v1/library/scan")
        for _ in range(60):
            if self.get("/api/v1/movies")[1]["total"] == 1: break
            time.sleep(0.1)
        self.assertEqual(self.get("/api/v1/movies")[1]["total"], 1)
        self.assertTrue(os.path.isfile(self.film))

    def test_oversized_body_and_lockout_do_not_break_it(self):
        mid = self.mid()
        self.assertEqual(self.call("PUT", "/api/v1/progress/" + mid, {"position_ms": 1, "pad": "x" * 9000})[0], 413)
        self.assertEqual(self.get("/api/v1/health", pin=None)[0], 200)


if __name__ == "__main__":
    unittest.main()
