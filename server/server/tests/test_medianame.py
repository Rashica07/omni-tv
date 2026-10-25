import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
import medianame as m


class NameTests(unittest.TestCase):
    def t(self, path, title, year):
        self.assertEqual((m.parse_movie(path)["title"], m.parse_movie(path)["year"]), (title, year), path)

    def test_paren_year(self):
        self.t("Movies/His Girl Friday (1940)/His Girl Friday (1940).mp4", "His Girl Friday", 1940)
        self.t("Blade Runner 2049 (2017).mkv", "Blade Runner 2049", 2017)
        self.t("1917 (2019).mkv", "1917", 2019)
        self.t("Interstellar (2014)/Interstellar (2014).mkv", "Interstellar", 2014)

    def test_dotted(self):
        self.t("The.Matrix.1999.1080p.BluRay.x264-GRP.mkv", "The Matrix", 1999)
        self.t("2012.2009.720p.mkv", "2012", 2009)
        self.t("Arrival.2016.WEB-DL.mkv", "Arrival", 2016)

    def test_no_year(self):
        self.t("Some.Film.1080p.WEB-DL.mkv", "Some Film", None)
        self.t("Plain Name.avi", "Plain Name", None)

    def test_number_inside_title(self):
        self.t("Blade Runner 2049 Final Cut.mkv", "Blade Runner 2049 Final Cut", None)

    def test_generic_file_uses_folder(self):
        self.t("Heat (1995)/movie.mkv", "Heat", 1995)
        self.t("Heat (1995)/video.mp4", "Heat", 1995)

    def test_helpers(self):
        self.assertTrue(m.is_video("a.MKV")); self.assertFalse(m.is_video("a.srt")); self.assertFalse(m.is_video(".hidden.mp4"))
        self.assertEqual(m.fold("Amélie  Poulain!"), "amelie poulain")
        self.assertEqual(m.strip_ext("a.b.mkv"), "a.b")


if __name__ == "__main__":
    unittest.main()
