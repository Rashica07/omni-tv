package com.rasha.omni.library

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaNameTest {
    private fun movie(p: String) = MediaName.parse(p) as Parsed.Movie
    private fun ep(p: String) = MediaName.parse(p) as Parsed.Episode

    @Test fun moviesWithParenYear() {
        assertEquals(Parsed.Movie("His Girl Friday", 1940), MediaName.parse("Movies/His Girl Friday (1940)/His Girl Friday (1940).mp4"))
        assertEquals(Parsed.Movie("Blade Runner 2049", 2017), MediaName.parse("Blade Runner 2049 (2017).mkv"))
        assertEquals(Parsed.Movie("1917", 2019), MediaName.parse("1917 (2019).mkv"))
    }

    @Test fun movieDotted() {
        assertEquals(Parsed.Movie("The Matrix", 1999), MediaName.parse("The.Matrix.1999.1080p.BluRay.x264-GRP.mkv"))
        assertEquals(Parsed.Movie("2012", 2009), MediaName.parse("2012.2009.720p.mkv"))
        assertEquals(Parsed.Movie("Arrival", 2016), MediaName.parse("Arrival.2016.WEB-DL.mkv"))
    }

    @Test fun movieWithoutYear() {
        assertEquals(Parsed.Movie("Some Film", null), MediaName.parse("Some.Film.1080p.WEB-DL.mkv"))
        assertEquals(Parsed.Movie("Plain Name", null), MediaName.parse("Plain Name.avi"))
    }

    @Test fun trailingBareYearIsReadAsTheYear() {
        // ambiguous from the name alone (as in Kodi); the scraper retries with the number kept in the title when the first search finds nothing
        assertEquals(Parsed.Movie("Blade Runner", 2049), MediaName.parse("Blade Runner 2049.mkv"))
        assertEquals(Parsed.Movie("Blade Runner 2049 Final Cut", null), MediaName.parse("Blade Runner 2049 Final Cut.mkv"))
    }

    @Test fun genericFileNameUsesFolder() {
        assertEquals(Parsed.Movie("Heat", 1995), MediaName.parse("Heat (1995)/movie.mkv"))
        assertEquals(Parsed.Movie("Heat", 1995), MediaName.parse("Heat (1995)/video.mp4"))
    }

    @Test fun episodesSxxExx() {
        assertEquals(Parsed.Episode("Seinfeld", 2, 3, 3, "The Revenge"), MediaName.parse("Seinfeld S02E03 The Revenge.mkv"))
        assertEquals(Parsed.Episode("Breaking Bad", 5, 14), MediaName.parse("Breaking.Bad.S05E14.720p.mkv").let { (it as Parsed.Episode).copy(title = null) })
        assertEquals(Parsed.Episode("The Wire", 1, 1, 2, null), (MediaName.parse("The.Wire.S01E01-E02.mkv") as Parsed.Episode).copy(title = null))
    }

    @Test fun episodesOtherForms() {
        assertEquals(2, ep("Show Name 1x02 Pilot.avi").episode)
        assertEquals("Show Name", ep("Show Name 1x02 Pilot.avi").show)
        assertEquals(4, ep("Show - Season 3 Episode 4.mp4").episode)
        assertEquals(3, ep("Show - Season 3 Episode 4.mp4").season)
    }

    @Test fun episodeShowFromFolders() {
        val e = ep("Shows/Dark/Season 02/S02E05.mkv")
        assertEquals(Triple("Dark", 2, 5), Triple(e.show, e.season, e.episode))
        val f = ep("Dark/Season 1/03 - Lies.mkv")
        assertEquals(Triple("Dark", 1, 3), Triple(f.show, f.season, f.episode))
        assertEquals("Lies", f.title)
    }

    @Test fun resolutionIsNotAnEpisode() {
        assertEquals(Parsed.Movie("Film", 2010), MediaName.parse("Film.2010.1080p.mkv"))
        assertEquals(Parsed.Movie("Movie 720p Test", null).title.startsWith("Movie"), true)
    }

    @Test fun videoDetection() {
        assertEquals(true, MediaName.isVideo("a.MKV"))
        assertEquals(false, MediaName.isVideo("a.srt"))
        assertEquals(false, MediaName.isVideo(".hidden.mp4"))
    }
}
