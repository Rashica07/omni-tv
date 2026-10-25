package com.rasha.omni.library

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private class Fake(val tree: Map<String, List<Item>>, val broken: Set<String> = emptySet()) : Lister {
        override suspend fun list(dir: String): List<Item> { if (dir in broken) error("denied"); return tree[dir] ?: emptyList() }
    }

    @Test fun walkFindsNestedAndSkipsHiddenAndBrokenFolders() = runBlocking {
        val l = Fake(mapOf("" to listOf(Item("Movies", true), Item("Shows", true), Item(".trash", true), Item("a.mp4", false, 5, 1)),
            "Movies" to listOf(Item("b.mkv", false, 9, 2)), "Shows" to listOf(Item("s.mkv", false, 1, 1))), broken = setOf("Shows"))
        val errors = ArrayList<String>()
        val files = Scan.walk(l, onError = { d, _ -> errors += d })
        assertEquals(setOf("a.mp4", "Movies/b.mkv"), files.map { it.path }.toSet())
        assertEquals(listOf("Shows"), errors)
    }

    @Test fun walkRespectsDepthAndFileLimits() = runBlocking {
        val l = Fake(mapOf("" to listOf(Item("d", true)), "d" to listOf(Item("e", true)), "d/e" to listOf(Item("x.mp4", false))))
        assertEquals(0, Scan.walk(l, maxDepth = 1).size)
        assertEquals(1, Scan.walk(l, maxDepth = 2).size)
        val many = Fake(mapOf("" to (1..50).map { Item("f$it.mp4", false) }))
        assertEquals(10, Scan.walk(many, maxFiles = 10).size)
    }

    @Test fun diffIsIncremental() {
        val known = mapOf("a.mp4" to FileEntry("a.mp4", 1, 1), "b.mp4" to FileEntry("b.mp4", 2, 2), "gone.mp4" to FileEntry("gone.mp4", 3, 3))
        val p = Scan.diff(known, listOf(FileEntry("a.mp4", 1, 1), FileEntry("b.mp4", 2, 99), FileEntry("new.mp4", 4, 4)))
        assertEquals(listOf("new.mp4"), p.added.map { it.path })
        assertEquals(listOf("b.mp4"), p.changed.map { it.path })
        assertEquals(listOf("gone.mp4"), p.removed)
        assertEquals(1, p.unchanged)
    }

    @Test fun emptyScanNeverWipesAPopulatedSource() {
        assertEquals(false, Scan.removalAllowed(knownCount = 12, foundCount = 0))     // drive unplugged / server down
        assertEquals(true, Scan.removalAllowed(knownCount = 12, foundCount = 3))     // some files really were deleted
        assertEquals(true, Scan.removalAllowed(knownCount = 0, foundCount = 0))      // nothing to protect
    }

    @Test fun subtitlesPairByName() {
        val sib = listOf("M/Film (2001).mp4", "M/Film (2001).srt", "M/Film (2001).en.srt", "M/Film (2001).eng.forced.srt", "M/Other.srt", "N/Film (2001).srt", "M/Film (2001).nfo")
        val s = Sidecars.subtitles("M/Film (2001).mp4", sib)
        assertEquals(listOf("M/Film (2001).en.srt", "M/Film (2001).srt", "M/Film (2001).eng.forced.srt"), s.map { it.path })
        assertEquals("en", s[0].language); assertNull(s[1].language); assertTrue(s[2].forced)
        assertEquals("M/Film (2001).nfo", Sidecars.nfo("M/Film (2001).mp4", sib))
    }

    @Test fun localArtwork() {
        val sib = listOf("M/poster.jpg", "M/fanart.png", "M/Film-poster.jpg")
        assertEquals("M/poster.jpg", Sidecars.poster("M/Film.mp4", sib))
        assertEquals("M/fanart.png", Sidecars.fanart("M/Film.mp4", sib))
        assertNull(Sidecars.poster("X/Film.mp4", sib))
    }

    @Test fun resumeAndWatchedRules() {
        val h = 7_200_000L
        assertNull(PlayRules.resumeAt(30_000, h))                       // barely started
        assertNull(PlayRules.resumeAt(h / 100, h))                      // first 5%
        assertEquals(1_800_000L, PlayRules.resumeAt(1_800_000, h))
        assertNull(PlayRules.resumeAt((h * 0.95).toLong(), h))          // finished
        assertEquals(0L to true, PlayRules.toSave((h * 0.92).toLong(), h))
        assertEquals(1_800_000L to false, PlayRules.toSave(1_800_000, h))
        assertNull(PlayRules.toSave(10_000, h))
        assertNull(PlayRules.toSave(5000, 0))
        assertEquals("1:05:03", PlayRules.clock(3_903_000)); assertEquals("5:07", PlayRules.clock(307_000))
    }

    @Test fun nfoReadsIdsAndRejectsHostileXml() {
        val n = NfoParser.parse("""<?xml version="1.0"?><movie><title>Heat</title><year>1995</year><uniqueid type="tmdb" default="true">949</uniqueid><uniqueid type="imdb">tt0113277</uniqueid></movie>""")
        assertNotNull(n); assertEquals(949, n!!.tmdbId); assertEquals("tt0113277", n.imdbId); assertEquals(1995, n.year)
        assertEquals(603, NfoParser.parse("<movie><tmdbid>603</tmdbid></movie>")!!.tmdbId)
        assertNull(NfoParser.parse("""<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><movie><title>&e;</title></movie>"""))
        assertNull(NfoParser.parse("<html><body/></html>")); assertNull(NfoParser.parse("not xml"))
        assertNull(NfoParser.parse("<movie><imdbid>../../x</imdbid></movie>")!!.imdbId)
    }

    @Test fun matchingPicksRightFilmAndRefusesGuesses() {
        val c = listOf(Candidate(1, "Heat", "Heat", 1995, 40.0), Candidate(2, "Heat", "Heat", 1986, 10.0), Candidate(3, "Heat Wave", "Heat Wave", 1995, 60.0))
        assertEquals(1, Matching.best(c, "Heat", 1995)!!.id)
        assertEquals(2, Matching.best(c, "Heat", 1986)!!.id)
        assertNull(Matching.best(c, "Totally Different Film", 1995))
        assertNull(Matching.best(c, "Heat", 2020))                                 // right name, wrong decade: do not guess
        assertEquals(5, Matching.best(listOf(Candidate(5, "Amélie", "Le Fabuleux Destin d'Amélie Poulain", 2001, 30.0)), "Amelie", 2001)!!.id)
        assertEquals(6, Matching.best(listOf(Candidate(6, "The Godfather", "The Godfather", 1972, 90.0)), "Godfather", 1972)!!.id)
    }

    @Test fun tmdbJsonMapping() {
        val s = TmdbJson.candidates("""{"results":[{"id":949,"title":"Heat","original_title":"Heat","release_date":"1995-12-15","popularity":33.2},{"id":1,"title":"X","release_date":""}]}""", false)
        assertEquals(Candidate(949, "Heat", "Heat", 1995, 33.2), s[0]); assertNull(s[1].year)
        val m = TmdbJson.movie("""{"id":949,"title":"Heat","release_date":"1995-12-15","overview":"A band of professional thieves","poster_path":"/p.jpg","backdrop_path":null,"vote_average":7.9,"runtime":170,"genres":[{"id":28,"name":"Action"},{"id":80,"name":"Crime"}]}""")
        assertEquals(listOf("Action", "Crime"), m.genres); assertNull(m.backdrop); assertEquals(170, m.runtimeMin)
        val e = TmdbJson.season("""{"episodes":[{"season_number":1,"episode_number":2,"name":"Two","overview":"","still_path":"/s.jpg","air_date":"2020-01-02"}]}""")
        assertEquals(EpisodeMeta(1, 2, "Two", "", "/s.jpg", "2020-01-02"), e[0])
        assertEquals(603, TmdbJson.findMovieId("""{"movie_results":[{"id":603}]}""")); assertNull(TmdbJson.findMovieId("""{"movie_results":[]}"""))
    }

    @Test fun archivePicksPlayableFileFromRealMetadataShape() {
        // names, formats, sizes and heights are exactly what archive.org returned for items "his_girl_friday" and "house_on_haunted_hill_ipod"
        val json = """{"metadata":{"identifier":"his_girl_friday","title":"His Girl Friday","year":"1940","licenseurl":"http://creativecommons.org/licenses/publicdomain/"},"files":[
          {"name":"his_girl_friday.asr.srt","format":"SubRip","size":"131456"},
          {"name":"his_girl_friday.mp4","format":"h.264","size":"575274912","height":"480"},
          {"name":"his_girl_friday.ogv","format":"Ogg Video","size":"390213679","height":"304"},
          {"name":"his_girl_friday_512kb.mp4","format":"512Kb MPEG4","size":"401207987","height":"240"}]}"""
        val i = Archive.parseMetadata(json)!!
        assertEquals("his_girl_friday.mp4", i.video); assertEquals("his_girl_friday.asr.srt", i.subtitle); assertEquals(1940, i.year)
        assertTrue(Archive.openLicence(i.license))
        assertEquals("https://archive.org/download/his_girl_friday/his_girl_friday.mp4", Archive.fileUrl(i.id, i.video!!))
        assertEquals("https://archive.org/download/a%20b/c%20d.mp4", Archive.fileUrl("a b", "c d.mp4"))
        assertTrue(!Archive.openLicence("https://rightsstatements.org/vocab/InC/1.0/")); assertTrue(!Archive.openLicence(null))
    }
}
