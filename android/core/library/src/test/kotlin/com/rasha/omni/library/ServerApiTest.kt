package com.rasha.omni.library

import org.junit.Assert.*
import org.junit.Test

class ServerApiTest {
    private val page = """{"total":2,"offset":0,"limit":200,"items":[
      {"id":"m_46fcbab1af04f8d5","title":"His Girl Friday","year":1940,"overview":"News.","genres":["Comedy","Romance"],"runtime_min":92,"rating":7.4,"matched":true,"tmdb_id":3085,"tmdb_poster":"/p.jpg","tmdb_backdrop":"/b.jpg","duration_ms":5504566,"verdict":"direct","added":1792939253,"position_ms":0},
      {"id":"m_22dae8c2bbf50b00","title":"Zzz","year":null,"overview":"","genres":[],"runtime_min":74,"rating":0,"matched":false,"tmdb_id":null,"tmdb_poster":null,"tmdb_backdrop":null,"duration_ms":4483217,"verdict":"maybe","added":1792939254}]}"""

    @Test fun parsesFilms() {
        val (total, m) = ApiJson.movies(page)
        assertEquals(2, total); assertEquals(2, m.size)
        assertEquals(ApiMovie("m_46fcbab1af04f8d5", "His Girl Friday", 1940, "News.", listOf("Comedy", "Romance"), 92, 7.4, true, 3085, "/p.jpg", "/b.jpg", 5504566, "direct", 1792939253), m[0])
        assertNull(m[1].year); assertNull(m[1].tmdbPoster); assertEquals(0, m[1].tmdbId); assertFalse(m[1].matched)
    }
    @Test fun emptyAndOddPages() { assertEquals(0 to emptyList<ApiMovie>(), ApiJson.movies("""{"total":0,"items":[]}""")); assertEquals(0, ApiJson.movies("{}").second.size) }
    @Test fun parsesProgressAndSubtitles() {
        val p = ApiJson.progress("""{"items":[{"movie_id":"m_a","pos_ms":600000,"dur_ms":5400000,"watched":0,"plays":0,"updated":1700000000,"device":"tv"}]}""")
        assertEquals(listOf(ApiProgress("m_a", 600000, 5400000, false, 1700000000)), p)
        assertEquals(listOf("/api/v1/movies/m_a/subtitles/0" to "en", "/x" to null), ApiJson.subtitles("""{"items":[{"index":0,"lang":"en","forced":false,"url":"/api/v1/movies/m_a/subtitles/0"},{"index":1,"lang":null,"url":"/x"}]}"""))
    }
    @Test fun pathsAreRecognisedAndReversible() {
        assertTrue(ApiPath.isApi(ApiPath.of("m_1"))); assertEquals("m_1", ApiPath.id(ApiPath.of("m_1"))); assertFalse(ApiPath.isApi("Movies/Heat (1995).mkv"))
    }
    @Test fun latestSideWins() {
        assertTrue(ProgressMerge.remoteWins(1_000_000, 2000)); assertFalse(ProgressMerge.remoteWins(5_000_000, 2000)); assertFalse(ProgressMerge.remoteWins(0, 0)); assertTrue(ProgressMerge.remoteWins(0, 1))
    }
    @Test fun progressBodyIsValidJson() {
        val o = org.json.JSONObject(ApiJson.progressBody(1200, 5000, "tv\"x")); assertEquals(1200, o.getLong("position_ms")); assertEquals("tv\"x", o.getString("device"))
    }
}
