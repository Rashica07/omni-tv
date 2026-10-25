import json, os, shutil, sys, tempfile, time, unittest
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import library, mediaprobe as probe
from test_mediaprobe import mp4, mkv

BIG = mp4() + b"\0" * 1_100_000          # over the rename-detection size floor; the header is still a valid mp4


class FakeTmdb:
    def __init__(self, table, fail=False):
        self.table, self.fail, self.searches, self.detail_calls = table, fail, [], []
    def search(self, title, year):
        if self.fail: raise OSError("network down")
        self.searches.append((title, year))
        return [c for c in self.table if title.lower().split()[0] in c["title"].lower()]
    def details(self, tid):
        if self.fail: raise OSError("network down")
        self.detail_calls.append(tid)
        return {"title": {949: "Heat", 157336: "Interstellar"}.get(tid, "Film %d" % tid), "year": {949: 1995, 157336: 2014}.get(tid), "overview": "plot", "genres": ["Crime", "Drama"] if tid == 949 else ["Sci-Fi"], "runtime": 170,
                "rating": 7.9, "cast": ["A", "B"], "director": "D", "poster": "/p.jpg", "backdrop": "/b.jpg"}


class Base(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.root = os.path.join(self.tmp, "Movies"); os.makedirs(self.root)
        self.data = os.path.join(self.tmp, "data"); os.makedirs(self.data)
        self.t = [1000.0]
        self.lib = None

    def tearDown(self):
        if self.lib: self.lib.close()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def open(self, tmdb=None, roots=None):
        if self.lib: self.lib.close()
        self.lib = library.Library(os.path.join(self.data, "lib.db"), roots or [{"name": "Movies", "path": self.root}], self.data, tmdb, clock=lambda: self.t[0])
        return self.lib

    def put(self, rel, data=BIG, mtime=None):
        p = os.path.join(self.root, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb").write(data)
        if mtime: os.utime(p, (mtime, mtime))
        return p


class ScanTests(Base):
    def test_adds_films_with_names_and_formats(self):
        self.put("Interstellar (2014)/Interstellar (2014).mp4"); self.put("Heat.1995.1080p.BluRay.mkv", mkv(vcodec=b"V_MPEGH/ISO/HEVC", acodec=b"A_DTS"))
        lib = self.open(); lib.scan()
        total, rows = lib.list()
        self.assertEqual(total, 2)
        by = {r["title"]: r for r in rows}
        self.assertEqual((by["Interstellar"]["year"], by["Interstellar"]["verdict"], by["Interstellar"]["container"]), (2014, "direct", "mp4"))
        self.assertEqual((by["Heat"]["year"], by["Heat"]["vcodec"], by["Heat"]["verdict"]), (1995, "hevc", "maybe"))
        self.assertIn("dts", by["Heat"]["verdict_note"])

    def test_second_scan_changes_nothing_and_does_not_reprobe(self):
        self.put("A (2001).mp4"); lib = self.open(); lib.scan()
        calls = []; real = probe.probe
        probe.probe = lambda p: (calls.append(p), real(p))[1]
        try: lib.scan()
        finally: probe.probe = real
        self.assertEqual(calls, [])
        self.assertEqual((lib.state["added"], lib.state["changed"]), (0, 0))

    def test_changed_file_is_reread_and_keeps_its_id(self):
        p = self.put("A (2001).mp4", mtime=1_700_000_000); lib = self.open(); lib.scan()
        mid = lib.list()[1][0]["id"]
        self.put("A (2001).mp4", mp4(vcodec=b"hvc1") + b"\0" * 1_200_000, mtime=1_700_000_500); lib.scan()
        r = lib.get(mid)
        self.assertEqual((r["vcodec"], lib.list()[0]), ("hevc", 1))

    def test_rename_and_move_keep_id_history_and_metadata(self):
        self.put("Heat (1995).mp4", mtime=1_700_000_000); lib = self.open(FakeTmdb([{"id": 949, "title": "Heat", "original": "Heat", "year": 1995, "popularity": 50}])); lib.scan()
        mid = lib.list()[1][0]["id"]
        lib.put_progress(mid, 600_000, 6_000_000)
        os.makedirs(os.path.join(self.root, "Crime")); os.rename(os.path.join(self.root, "Heat (1995).mp4"), os.path.join(self.root, "Crime", "Heat 1995 remux.mp4"))
        lib.scan()
        total, rows = lib.list()
        self.assertEqual((total, rows[0]["id"], rows[0]["rel"], rows[0]["tmdb_id"], rows[0]["pos_ms"]), (1, mid, "Crime/Heat 1995 remux.mp4", 949, 600_000))
        self.assertEqual(lib.state["renamed"], 1)

    def test_deleted_file_is_marked_missing_not_forgotten_and_never_touches_other_files(self):
        self.put("A (2001).mp4"); keep = self.put("B (2002).mp4"); lib = self.open(); lib.scan()
        mid = [r for r in lib.list()[1] if r["title"] == "A"][0]["id"]
        lib.put_progress(mid, 900_000, 6_000_000)
        os.remove(os.path.join(self.root, "A (2001).mp4")); lib.scan()
        self.assertEqual(lib.list()[0], 1)
        self.assertEqual(lib.get(mid)["status"], "missing"); self.assertEqual(lib.counts()["missing"], 1)
        self.assertTrue(os.path.isfile(keep))
        self.put("A (2001).mp4"); lib.scan()                                   # it comes back: same id, history intact
        self.assertEqual((lib.get(mid)["status"], lib.get(mid)["pos_ms"]), ("ok", 900_000))

    def test_unavailable_root_or_empty_listing_does_not_wipe_the_library(self):
        self.put("A (2001).mp4"); lib = self.open(); lib.scan()
        shutil.move(self.root, self.root + ".unplugged"); lib.scan()
        self.assertEqual(lib.list()[0], 1); self.assertTrue(any("not available" in e for e in lib.state["errors"]))
        os.makedirs(self.root); lib.scan()                                     # mounted but empty
        self.assertEqual(lib.list()[0], 1); self.assertTrue(any("lists no videos" in e for e in lib.state["errors"]))

    def test_overlapping_roots_and_links_do_not_duplicate_or_escape(self):
        self.put("A (2001).mp4")
        outside = os.path.join(self.tmp, "outside.mp4"); open(outside, "wb").write(BIG)
        os.symlink(outside, os.path.join(self.root, "link.mp4"))                # leaves the library: ignored
        lib = self.open(roots=[{"name": "Movies", "path": self.root}, {"name": "Sub", "path": os.path.join(self.root)}, {"name": "Inside", "path": self.root + "/."}])
        lib.scan()
        self.assertEqual(lib.list()[0], 1)

    def test_sidecars_subtitles_and_local_art(self):
        self.put("Heat (1995)/Heat (1995).mp4"); self.put("Heat (1995)/Heat (1995).en.srt", b"1"); self.put("Heat (1995)/Heat (1995).it.forced.srt", b"1"); self.put("Heat (1995)/Other.srt", b"1")
        self.put("Heat (1995)/poster.jpg", b"x"); self.put("Heat (1995)/fanart.png", b"x")
        lib = self.open(); lib.scan()
        r = lib.list()[1][0]
        subs = json.loads(r["subs_json"])
        self.assertEqual([(s["lang"], s["forced"]) for s in subs], [("en", False), ("it", True)])
        self.assertEqual((r["local_poster"], r["local_backdrop"]), ("Heat (1995)/poster.jpg", "Heat (1995)/fanart.png"))
        self.assertIsNotNone(lib.sidecar_path(r["id"], "Heat (1995)/Heat (1995).en.srt"))
        self.assertIsNone(lib.sidecar_path(r["id"], "Heat (1995)/Other.srt")); self.assertIsNone(lib.sidecar_path(r["id"], "../outside.mp4"))

    def test_damaged_file_is_listed_and_reported_not_hidden(self):
        self.put("Broken (2000).mp4", b"\0" * 5000); lib = self.open(); lib.scan()
        r = lib.list()[1][0]
        self.assertEqual((r["title"], r["verdict"]), ("Broken", "unsupported"))

    def test_persistence_across_restart(self):
        self.put("A (2001).mp4"); lib = self.open(); lib.scan(); mid = lib.list()[1][0]["id"]; lib.put_progress(mid, 700_000, 6_000_000)
        lib = self.open()
        self.assertEqual((lib.list()[0], lib.get(mid)["pos_ms"]), (1, 700_000))

    def test_file_path_only_inside_the_root(self):
        self.put("A (2001).mp4"); lib = self.open(); lib.scan(); mid = lib.list()[1][0]["id"]
        self.assertTrue(lib.file_path(mid).startswith(os.path.realpath(self.root)))
        lib.db.execute("UPDATE movie SET rel='../../etc/passwd' WHERE id=?", (mid,)); lib.db.commit()
        self.assertIsNone(lib.file_path(mid))


class MetadataTests(Base):
    TABLE = [{"id": 949, "title": "Heat", "original": "Heat", "year": 1995, "popularity": 50}, {"id": 1, "title": "Heat Wave", "original": "Heat Wave", "year": 1995, "popularity": 90},
             {"id": 157336, "title": "Interstellar", "original": "Interstellar", "year": 2014, "popularity": 80}]

    def test_matches_by_name_and_year_and_fills_everything(self):
        self.put("Interstellar (2014)/Interstellar (2014).mp4"); t = FakeTmdb(self.TABLE); lib = self.open(t); lib.scan()
        r = lib.list()[1][0]
        self.assertEqual((r["tmdb_id"], r["matched"], r["genres"], r["director"], r["poster"], r["runtime"]), (157336, 1, "Sci-Fi", "D", "/p.jpg", 170))

    def test_unmatched_film_is_still_listed(self):
        self.put("Zzz Unknown Film (2011).mp4"); lib = self.open(FakeTmdb(self.TABLE)); lib.scan()
        r = lib.list()[1][0]
        self.assertEqual((r["title"], r["matched"], lib.counts()["unmatched"]), ("Zzz Unknown Film", 0, 1)); self.assertIn("no confident match", r["match_note"])

    def test_wrong_year_is_not_guessed(self):
        self.put("Heat (2020).mp4"); lib = self.open(FakeTmdb(self.TABLE)); lib.scan()
        self.assertEqual(lib.list()[1][0]["matched"], 0)

    def test_provider_down_leaves_films_listed_and_is_retried_next_scan(self):
        self.put("Heat (1995).mp4"); lib = self.open(FakeTmdb(self.TABLE, fail=True)); lib.scan()
        self.assertEqual((lib.list()[0], lib.list()[1][0]["matched"]), (1, 0)); self.assertTrue(any("metadata lookup failed" in e for e in lib.state["errors"]))
        lib.tmdb = FakeTmdb(self.TABLE); lib.scan()
        self.assertEqual(lib.list()[1][0]["matched"], 1)

    def test_no_provider_means_no_metadata_and_no_crash(self):
        self.put("Heat (1995).mp4"); lib = self.open(None); lib.scan()
        self.assertEqual((lib.list()[0], lib.scan_status()["tmdb"]), (1, False))

    def test_nfo_id_beats_the_name(self):
        self.put("Mystery/Mystery.mp4"); self.put("Mystery/Mystery.nfo", b'<movie><title>x</title><uniqueid type="tmdb" default="true">949</uniqueid></movie>')
        t = FakeTmdb(self.TABLE); lib = self.open(t); lib.scan()
        self.assertEqual(lib.list()[1][0]["tmdb_id"], 949); self.assertEqual(t.searches, [])

    def test_hostile_nfo_is_ignored(self):
        self.put("M/M (1995).mp4"); self.put("M/M (1995).nfo", b'<!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><movie><tmdbid>5</tmdbid></movie>')
        t = FakeTmdb(self.TABLE); lib = self.open(t); lib.scan()
        self.assertNotIn(5, t.detail_calls)

    def test_manual_correction_and_refresh(self):
        self.put("Zzz (2011).mp4"); lib = self.open(FakeTmdb(self.TABLE)); lib.scan()
        mid = lib.list()[1][0]["id"]
        self.assertIsNone(lib.set_match(mid, 949)); r = lib.get(mid)
        self.assertEqual((r["title"], r["tmdb_id"], r["matched"]), ("Heat", 949, 1))
        self.assertIsNone(lib.refresh(mid))
        self.assertEqual(lib.set_match("m_nope", 1), "no such film")


class QueryTests(Base):
    def make(self):
        for rel, y in (("Alpha (1990)/Alpha (1990).mp4", 1990), ("Beta (2005)/Beta (2005).mp4", 2005), ("Amélie (2001)/Amélie (2001).mp4", 2001), ("Gamma (2015)/Gamma (2015).mp4", 2015)):
            self.put(rel, mtime=1_700_000_000 + y)
        t = FakeTmdb([{"id": 949, "title": "Heat", "original": "Heat", "year": 1995, "popularity": 50}])
        lib = self.open(t); lib.scan(); return lib

    def test_search_sort_and_paging(self):
        lib = self.make()
        self.assertEqual([r["title"] for r in lib.list(sort="title")[1]], ["Alpha", "Amélie", "Beta", "Gamma"])
        self.assertEqual([r["title"] for r in lib.list(sort="year")[1]], ["Gamma", "Beta", "Amélie", "Alpha"])
        self.assertEqual([r["title"] for r in lib.list(q="amelie")[1]], ["Amélie"])               # accents ignored
        self.assertEqual(lib.list(q="al")[0], 1); self.assertEqual(lib.list(q="zzz")[0], 0)
        total, page = lib.list(sort="title", offset=1, limit=2)
        self.assertEqual((total, [r["title"] for r in page]), (4, ["Amélie", "Beta"]))

    def test_continue_watching_and_watched_threshold(self):
        lib = self.make(); ids = {r["title"]: r["id"] for r in lib.list()[1]}
        a = lib.put_progress(ids["Alpha"], 30_000, 6_000_000); self.assertEqual(a["pos_ms"], 0)       # under a minute: not saved
        lib.put_progress(ids["Beta"], 1_800_000, 6_000_000); self.t[0] += 5
        lib.put_progress(ids["Gamma"], 2_400_000, 6_000_000)
        self.assertEqual([r["title"] for r in lib.list(only="continue")[1]], ["Gamma", "Beta"])      # newest first
        done = lib.put_progress(ids["Gamma"], 5_700_000, 6_000_000)                                  # past 90%: watched, starts over
        self.assertEqual((done["watched"], done["pos_ms"], done["plays"]), (1, 0, 1))
        self.assertEqual([r["title"] for r in lib.list(only="continue")[1]], ["Beta"])
        self.assertEqual([r["title"] for r in lib.list(only="watched")[1]], ["Gamma"]); self.assertEqual(lib.list(only="unwatched")[0], 3)

    def test_genres_and_filter(self):
        self.put("Heat (1995)/Heat (1995).mp4"); lib = self.open(FakeTmdb([{"id": 949, "title": "Heat", "original": "Heat", "year": 1995, "popularity": 50}])); lib.scan()
        self.assertEqual([g["name"] for g in lib.genres()], ["Crime", "Drama"])
        self.assertEqual(lib.list(genre="Crime")[0], 1); self.assertEqual(lib.list(genre="Western")[0], 0); self.assertEqual(lib.list(genre="Cri")[0], 0)

    def test_unknown_film_progress_is_refused(self):
        lib = self.make(); self.assertIsNone(lib.put_progress("m_nope", 100000, 6000000))


class MatchTests(unittest.TestCase):
    def test_best_match(self):
        c = [{"id": 1, "title": "Heat", "original": "Heat", "year": 1995, "popularity": 40}, {"id": 2, "title": "Heat", "original": "Heat", "year": 1986, "popularity": 10}, {"id": 3, "title": "Heat Wave", "original": "", "year": 1995, "popularity": 60}]
        self.assertEqual(library.best_match(c, "Heat", 1995), 1); self.assertEqual(library.best_match(c, "Heat", 1986), 2)
        self.assertIsNone(library.best_match(c, "Totally Different", 1995)); self.assertIsNone(library.best_match(c, "Heat", 2020)); self.assertIsNone(library.best_match([], "x", None))
        self.assertEqual(library.best_match([{"id": 5, "title": "Amélie", "original": "Le Fabuleux Destin d'Amélie Poulain", "year": 2001, "popularity": 30}], "Amelie", 2001), 5)


class TmdbTests(unittest.TestCase):
    def test_cache_credential_and_host_rules(self):
        d = tempfile.mkdtemp(); calls = []
        def fetch(url, hdr): calls.append((url, hdr)); return json.dumps({"results": [{"id": 9, "title": "X", "release_date": "2001-01-01", "popularity": 5}]}).encode()
        t = library.Tmdb("eyJSECRETTOKEN", d, fetch=fetch, min_interval=0)
        t.search("x", 2001); t.search("x", 2001)
        self.assertEqual(len(calls), 1)                                                # the second ask came from the disk cache
        self.assertEqual(calls[0][1], {"Authorization": "Bearer eyJSECRETTOKEN"}); self.assertNotIn("SECRET", calls[0][0])      # bearer goes in a header, never in the URL
        self.assertTrue(all("SECRET" not in f for f in os.listdir(d)))                # nor in a file name
        t2 = library.Tmdb("v3key", d, fetch=fetch, min_interval=0); t2.search("y", None)
        self.assertIn("api_key=v3key", calls[-1][0])
        self.assertIsNone(t.image("/../../etc/passwd")); self.assertIsNone(t.image("http://evil/x.jpg"))
        with self.assertRaises(ValueError): library.Tmdb("c", d, min_interval=0)._http("https://evil.example/x", {})

    def test_load_tmdb_from_env_or_file_only(self):
        d = tempfile.mkdtemp(); f = os.path.join(d, "tok"); open(f, "w").write("abc\n")
        os.environ.pop("OMNI_TMDB_TOKEN", None)
        self.assertIsNone(library.load_tmdb({}, d)); self.assertIsNotNone(library.load_tmdb({"tmdb_token_file": f}, d))
        os.environ["OMNI_TMDB_TOKEN"] = "envtoken"
        try: self.assertEqual(library.load_tmdb({}, d).cred, "envtoken")
        finally: os.environ.pop("OMNI_TMDB_TOKEN", None)


if __name__ == "__main__":
    unittest.main()
