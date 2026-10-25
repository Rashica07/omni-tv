package com.rasha.omni.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogueTest {
    private fun e(src: Long, path: String, tmdb: Int, title: String = "Heat", pos: Long = 0, dur: Long = 0, watched: Boolean = false, last: Long = 0, added: Long = 1) =
        Entry(src, path, false, title, 1995, "", "/p.jpg", null, 7.0, 0, "", tmdb > 0, 0, null, 0, 0, pos, dur, watched, last, added, tmdbId = tmdb)
    private val kinds = mapOf(1L to SourceKind.Lan, 2L to SourceKind.Archive, 3L to SourceKind.Local)
    private fun movies(list: List<Entry>, health: (Long) -> Availability = { Availability.PLAYABLE }) = Catalogue.movies(list, { kinds[it] }, health)

    @Test fun twoCopiesOfOneFilmAreOneTitle() {
        val t = movies(listOf(e(1, "Heat.mkv", 949), e(2, "x/Heat.mp4", 949)))
        assertEquals(1, t.size); assertEquals("omni:movie:tmdb:949", t[0].id.text); assertEquals(2, t[0].copies.size); assertEquals("2 sources", t[0].sourceLabel)
        assertEquals(SourceKind.Lan, t[0].copies[0].let { kinds[it.sourceId] })          // the OMNI server copy is tried before the Archive
    }

    @Test fun localCopyIsTriedFirst() {
        val t = movies(listOf(e(2, "a.mp4", 5), e(1, "b.mkv", 5), e(3, "c.mkv", 5)))
        assertEquals(listOf(3L, 1L, 2L), t[0].copies.map { it.sourceId })
    }

    @Test fun progressFollowsTheFilmNotTheCopy() {
        val t = movies(listOf(e(1, "a.mkv", 7, pos = 600_000, dur = 6_000_000, last = 50), e(2, "b.mp4", 7, last = 0)))[0]
        assertEquals(600_000, t.positionMs); assertEquals(6_000_000, t.durationMs)
        assertTrue(Catalogue.select("continue", listOf(t)).isNotEmpty())
    }

    @Test fun watchedOnAnyCopyCountsAndLeavesContinue() {
        val t = movies(listOf(e(1, "a.mkv", 7, pos = 600_000, dur = 6_000_000, last = 5), e(2, "b.mp4", 7, watched = true, last = 9)))[0]
        assertTrue(t.watched); assertEquals(0, Catalogue.select("continue", listOf(t)).size)
    }

    @Test fun availabilityIsHonest() {
        val offline = movies(listOf(e(1, "a.mkv", 1)), health = { Availability.OFFLINE })[0]
        assertEquals(Availability.OFFLINE, offline.availability)
        val oneUp = movies(listOf(e(1, "a.mkv", 2), e(2, "b.mp4", 2)), health = { if (it == 2L) Availability.PLAYABLE else Availability.OFFLINE })[0]
        assertEquals(Availability.PLAYABLE, oneUp.availability)
    }

    @Test fun unidentifiedFilesStaySeparate() {
        val t = movies(listOf(e(1, "a.mkv", 0, title = "a"), e(1, "b.mkv", 0, title = "b")))
        assertEquals(2, t.size); assertTrue(t.all { it.id is MediaId.File })
    }

    @Test fun shelvesOnlyExistWhenTheyHaveContent() {
        val all = movies(listOf(e(1, "a.mkv", 1), e(2, "b.mp4", 2, title = "Free One")))
        val ids = Catalogue.shelves(all).map { it.id }
        assertEquals(listOf("free", "network", "recent"), ids)
        assertEquals(0, Catalogue.shelves(emptyList()).size)
        assertEquals("Free One", Catalogue.select("free", all)[0].title)
    }
}
