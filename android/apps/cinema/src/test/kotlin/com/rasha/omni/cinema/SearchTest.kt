package com.rasha.omni.cinema

import com.rasha.omni.library.Availability
import com.rasha.omni.library.MediaId
import com.rasha.omni.library.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun t(id: Int, title: String, year: Int?, genres: String = "", overview: String = "") =
        Title(MediaId.Movie(id), "movie", title, year, overview, null, null, 0.0, "OMNI server", Availability.PLAYABLE, 0, 0, false, 0, 0, emptyList(), emptySet(), 0, genres)

    private val all = listOf(t(1, "His Girl Friday", 1940, "Comedy, Romance"), t(2, "House on Haunted Hill", 1959, "Horror"), t(3, "Amélie", 2001, "Comedy", "A shy waitress in Paris"), t(4, "Jungle Book", 1942, "Adventure, Fantasy"))

    @Test fun nothingTypedFindsNothing() { assertTrue(searchTitles("", all).isEmpty()); assertTrue(searchTitles("   ", all).isEmpty()) }
    @Test fun byTitleAccentsAndCaseIgnored() { assertEquals(listOf(3), searchTitles("AMELIE", all).map { (it.id as MediaId.Movie).tmdb }) }
    @Test fun everyWordMustMatch() { assertEquals(listOf(2), searchTitles("haunted house", all).map { (it.id as MediaId.Movie).tmdb }) }
    @Test fun byGenreYearAndStory() {
        assertEquals(setOf(1, 3), searchTitles("comedy", all).map { (it.id as MediaId.Movie).tmdb }.toSet())
        assertEquals(listOf(4), searchTitles("1942", all).map { (it.id as MediaId.Movie).tmdb })
        assertEquals(listOf(3), searchTitles("paris", all).map { (it.id as MediaId.Movie).tmdb })
    }
    @Test fun titleMatchesComeFirst() {
        val l = listOf(t(5, "Paris Blues", 1961), t(3, "Amélie", 2001, "Comedy", "A shy waitress in Paris"))
        assertEquals(5, (searchTitles("paris", l).first().id as MediaId.Movie).tmdb)
    }
    @Test fun runtimeText() { assertEquals("1h 32m", runtimeText(92)); assertEquals("45m", runtimeText(45)); assertEquals(null, runtimeText(0)) }
}
