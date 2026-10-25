"""Integration tests for omniptvd's authenticated read endpoints. They start the real server on a free port with a temporary media root."""
import json, os, socket, subprocess, sys, tempfile, time, unittest, urllib.error, urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER = os.path.join(HERE, "..", "omniptvd.py")
PIN = "12345678"


def free_port():
    s = socket.socket(); s.bind(("127.0.0.1", 0)); p = s.getsockname()[1]; s.close(); return p


class MediaTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        t = cls.tmp.name
        cls.root = os.path.join(t, "media"); os.makedirs(os.path.join(cls.root, "Movies"))
        open(os.path.join(cls.root, "Movies", "A (2001).mp4"), "wb").write(bytes(range(256)) * 40)     # 10240 bytes
        open(os.path.join(cls.root, "secret.txt"), "w").write("not media")
        open(os.path.join(t, "outside.mp4"), "wb").write(b"x" * 100)
        os.symlink(os.path.join(t, "outside.mp4"), os.path.join(cls.root, "link.mp4"))                  # symlink leaving the root
        open(os.path.join(t, "catalog.json"), "w").write(json.dumps({"schema": 2, "channels": []}))
        open(os.path.join(t, "read.pin"), "w").write(PIN)
        cls.port = free_port()
        json.dump({"bind": "127.0.0.1", "port": cls.port, "catalog": os.path.join(t, "catalog.json"), "state": os.path.join(t, "state.json"),
                   "epg_cache": os.path.join(t, "epg.xml"), "probe_every_s": 99999, "admin_token_file": os.path.join(t, "none"),
                   "read_token_file": os.path.join(t, "read.pin"), "media_root": cls.root}, open(os.path.join(t, "c.json"), "w"))
        cls.p = subprocess.Popen([sys.executable, "-I", SERVER], env={"OMNIPTV_CONFIG": os.path.join(t, "c.json"), "PATH": os.environ["PATH"]}, stderr=subprocess.DEVNULL)
        for _ in range(50):
            try: urllib.request.urlopen(cls.url("/health")).read(); break
            except Exception: time.sleep(0.1)

    @classmethod
    def tearDownClass(cls):
        cls.p.terminate(); cls.p.wait(); cls.tmp.cleanup()

    @classmethod
    def url(cls, path): return "http://127.0.0.1:%d%s" % (cls.port, path)

    def get(self, path, pin=PIN, headers=None):
        h = dict(headers or {})
        if pin: h["Authorization"] = "Bearer " + pin
        try:
            r = urllib.request.urlopen(urllib.request.Request(self.url(path), headers=h)); return r.status, r.read(), r.headers
        except urllib.error.HTTPError as e:
            return e.code, e.read(), e.headers

    def test_listing_and_file(self):
        c, b, _ = self.get("/media/")
        self.assertEqual(c, 200)
        names = [i["name"] for i in json.loads(b)["items"]]
        self.assertIn("Movies", names)
        self.assertNotIn("secret.txt", names)          # not a media extension
        self.assertNotIn("link.mp4", names)            # symlink to outside the root is hidden
        c, b, _ = self.get("/media/Movies/A%20(2001).mp4")
        self.assertEqual((c, len(b)), (200, 10240))

    def test_range(self):
        c, b, h = self.get("/media/Movies/A%20(2001).mp4", headers={"Range": "bytes=100-199"})
        self.assertEqual((c, len(b), h["Content-Range"]), (206, 100, "bytes 100-199/10240"))
        self.assertEqual(b[0], 100)
        c, b, _ = self.get("/media/Movies/A%20(2001).mp4", headers={"Range": "bytes=-16"})
        self.assertEqual((c, len(b)), (206, 16))
        c, b, _ = self.get("/media/Movies/A%20(2001).mp4", headers={"Range": "bytes=10000-"})
        self.assertEqual((c, len(b)), (206, 240))
        c, _, _ = self.get("/media/Movies/A%20(2001).mp4", headers={"Range": "bytes=99999-"})
        self.assertEqual(c, 416)

    def test_traversal_and_symlink_refused(self):
        for p in ("/media/../outside.mp4", "/media/%2e%2e/outside.mp4", "/media/Movies/../../outside.mp4", "/media//etc/passwd", "/media/link.mp4", "/media/secret.txt"):
            self.assertEqual(self.get(p)[0], 404, p)

    def test_auth_required(self):
        self.assertEqual(self.get("/media/", pin=None)[0], 401)
        self.assertEqual(self.get("/status", pin=None)[0], 401)
        self.assertEqual(self.get("/health", pin=None)[0], 200)      # health stays open and reveals nothing sensitive

    def test_status_fields(self):
        c, b, _ = self.get("/status")
        d = json.loads(b)
        self.assertEqual(c, 200)
        for k in ("uptime_s", "load", "mem_total", "mem_available", "disks", "cpus"):
            self.assertIn(k, d)
        self.assertIn("media", d["disks"])
        self.assertEqual(d["media_root"], self.root)
        self.assertIn("net", d)                                  # null on a machine with no interface, otherwise an object

    def test_zz_lockout_after_ten_wrong(self):
        for _ in range(10): self.assertEqual(self.get("/status", pin="00000000")[0], 401)
        self.assertEqual(self.get("/status")[0], 401)               # even the right PIN is refused while locked out


if __name__ == "__main__":
    unittest.main()
