package com.rasha.omni.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaIdTest {
    @Test fun roundTrips() {
        for (s in listOf("omni:movie:tmdb:949", "omni:show:tmdb:1396", "omni:ep:tmdb:1396:5:14", "omni:file:0123456789abcdef")) assertEquals(s, MediaId.parse(s)!!.text)
        assertTrue(MediaId.parse("omni:movie:tmdb:949") is MediaId.Movie)
        assertEquals(949, (MediaId.parse("omni:movie:tmdb:949") as MediaId.Movie).tmdb)
        val e = MediaId.parse("omni:ep:tmdb:1396:5:14") as MediaId.Episode
        assertEquals(Triple(1396, 5, 14), Triple(e.showTmdb, e.season, e.episode))
    }

    @Test fun refusesAnythingElse() {
        val bad = listOf("", "omni:movie:tmdb:0", "omni:movie:tmdb:-1", "omni:movie:tmdb:abc", "omni:movie:tmdb:1234567890", "omni:movie:949", "OMNI:movie:tmdb:1",
            "omni:file:xyz", "omni:file:0123456789ABCDEF", "omni:file:0123456789abcde", "omni:movie:tmdb:1\n", "omni:movie:tmdb:1 ", "../../etc/passwd", "omni:ep:tmdb:1:2", "omni:ep:tmdb:1:a:3",
            "omni:movie:tmdb:1;drop table", "a".repeat(200), "omni:movie:tmdb:1/../x", "omni:show:tmdb:1:2:3")
        for (b in bad) assertNull("should refuse '$b'", MediaId.parse(b))
        assertNull(MediaId.parse(null))
    }

    @Test fun sameFilmFromTwoSourcesIsOneId() {
        fun e(src: Long, path: String) = Entry(src, path, false, "Heat", 1995, "", null, null, 0.0, 0, "", true, 0, null, 0, 0, 0, 0, false, 0, 0, tmdbId = 949)
        assertEquals(MediaId.of(e(1, "a/Heat.mkv"), SourceKind.Lan), MediaId.of(e(2, "Heat.mp4"), SourceKind.Archive))
    }

    @Test fun unidentifiedFilesGetDistinctStableFileIds() {
        fun e(src: Long, path: String) = Entry(src, path, false, "x", null, "", null, null, 0.0, 0, "", false, 0, null, 0, 0, 0, 0, false, 0, 0)
        val a = MediaId.of(e(1, "a.mkv"), SourceKind.Lan); val b = MediaId.of(e(1, "b.mkv"), SourceKind.Lan)
        assertNotEquals(a, b); assertEquals(a, MediaId.of(e(1, "a.mkv"), SourceKind.Lan)); assertTrue(a is MediaId.File)
        assertEquals(a.text, MediaId.parse(a.text)!!.text)
    }

    @Test fun episodeNeedsRealNumbersElseFileId() {
        fun ep(season: Int, number: Int, show: Int) = Entry(1, "s.mkv", true, "t", null, "", null, null, 0.0, 0, "", true, 5, "Show", season, number, 0, 0, false, 0, 0, tmdbId = 0, showTmdbId = show)
        assertEquals("omni:ep:tmdb:1396:5:14", MediaId.of(ep(5, 14, 1396), SourceKind.Lan).text)
        assertTrue(MediaId.of(ep(0, 14, 1396), SourceKind.Lan) is MediaId.File)
        assertTrue(MediaId.of(ep(5, 14, 0), SourceKind.Lan) is MediaId.File)
    }
}
