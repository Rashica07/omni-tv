import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import import_m3u as m

SAMPLE = """﻿#EXTM3U x-tvg-url="http://example.invalid/epg.xml"
#EXTINF:-1 tvg-id="RTK1.xk" tvg-name="RTK 1" tvg-country="XK" tvg-language="Albanian" group-title="News",RTK 1
http://a.example/rtk1.m3u8
#EXTINF:-1 tvg-id="RTK1.xk" group-title="News",RTK 1 HD
http://a.example/rtk1-hd.m3u8
#EXTINF:-1 tvg-id="" group-title="Music",Top Channel
https://b.example/top.m3u8
#EXTVLCOPT:http-user-agent=x
#EXTINF:-1,Evil file
file:///etc/passwd
#EXTINF:-1,Evil js
javascript:alert(1)
#EXTINF:-1,Creds
http://user:pw@c.example/x.m3u8
#EXTINF:-1,
http://d.example/noname.m3u8
http://e.example/orphan.m3u8
#EXTINF:-1,Line\x07Break
https://f.example/ok.m3u8
"""

class T(unittest.TestCase):
    def test_identity_and_duplicates(self):
        rows, rep = m.parse(SAMPLE, "Source One")
        ids = {r["id"] for r in rows}
        self.assertIn("m3u:source-one:rtk1-xk", ids)
        rtk = [r for r in rows if r["id"].endswith("rtk1-xk")][0]
        self.assertEqual(len(rtk["streams"]), 2, "same tvg-id in one source: two urls on one channel")
        self.assertEqual(rep["duplicates_merged"], 1)
    def test_two_sources_never_merge_by_name(self):
        a, _ = m.parse(SAMPLE, "A")
        b, _ = m.parse(SAMPLE, "B")
        self.assertFalse({r["id"] for r in a} & {r["id"] for r in b})
    def test_untrusted_input(self):
        rows, rep = m.parse(SAMPLE, "S")
        urls = [s["url"] for r in rows for s in r["streams"]]
        self.assertTrue(all(u.startswith(("http://", "https://")) and "@" not in u for u in urls))
        self.assertEqual(rep["unsafe_url"], 3)
        self.assertEqual(rep["no_name"], 1)
        self.assertEqual(rep["malformed"], 1, "a url line with no EXTINF before it")
    def test_never_enabled_and_tier_c(self):
        rows, _ = m.parse(SAMPLE, "S")
        self.assertTrue(all(not s["enabled"] and s["tier"] == "C" for r in rows for s in r["streams"]))
    def test_control_characters_removed(self):
        rows, _ = m.parse(SAMPLE, "S")
        self.assertTrue(any(r["name"] == "Line Break" for r in rows))
    def test_not_a_playlist(self):
        rows, rep = m.parse("<html>nope</html>", "S")
        self.assertEqual(rows, [])
        self.assertEqual(rep["malformed"], 1)
    def test_merge_keeps_existing(self):
        doc = {"schema": 2, "channels": [{"id": "RTK3.xk", "name": "RTK 3", "streams": []}]}
        rows, _ = m.parse(SAMPLE, "S")
        added = m.merge(doc, rows)
        self.assertEqual(added, len(rows))
        self.assertEqual(doc["channels"][0]["id"], "RTK3.xk")
    def test_deterministic(self):
        a, _ = m.parse(SAMPLE, "S")
        b, _ = m.parse(SAMPLE, "S")
        self.assertEqual([r["id"] for r in a], [r["id"] for r in b])
    def test_size_limit_on_entries(self):
        big = "#EXTM3U\n" + "".join("#EXTINF:-1,Ch %d\nhttp://h.example/%d.m3u8\n" % (i, i) for i in range(m.MAX_ENTRIES + 50))
        rows, _ = m.parse(big, "S")
        self.assertLessEqual(len(rows), m.MAX_ENTRIES)

if __name__ == "__main__":
    unittest.main()
