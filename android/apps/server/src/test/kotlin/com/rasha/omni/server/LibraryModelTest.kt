package com.rasha.omni.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelTest {
    private val lib = """{"api":1,"roots":[{"name":"Movies","path":"/srv/omni-media/Movies","available":true},{"name":"Shows","path":"/x","available":false}],
        "counts":{"movies":14,"missing":1,"unmatched":2,"last_scan":1792941093},"metadata":true,
        "scan":{"state":"idle","phase":"","started":1792941000.5,"finished":1792941093.2,"files":14,"added":0,"changed":1,"renamed":0,"missing":1,"identified":0,"errors":["a","b"],"roots":[],"tmdb":true},"genres":[]}"""

    @Test fun parsesLibrary() {
        val l = Parse.library(lib)
        assertEquals(14, l.movies); assertEquals(1, l.missing); assertEquals(2, l.unmatched); assertEquals(1792941093L, l.lastScanS); assertTrue(l.metadata)
        assertEquals(listOf(LibRoot("Movies", true), LibRoot("Shows", false)), l.roots)
        assertFalse(l.scan.scanning); assertEquals(2, l.scan.errors); assertEquals(1792941093L, l.scan.finishedS)
    }
    @Test fun neverScannedIsNullNotZero() {
        val l = Parse.library("""{"roots":[],"counts":{"movies":0,"missing":0,"unmatched":0,"last_scan":null},"scan":{"state":"idle","finished":0,"errors":[]},"metadata":false}""")
        assertNull(l.lastScanS); assertNull(l.scan.finishedS); assertFalse(l.metadata)
    }
    @Test fun runningScan() { assertTrue(Parse.library("""{"counts":{},"scan":{"state":"scanning","phase":"identifying films","files":9,"errors":[]},"roots":[]}""").scan.scanning) }

    @Test fun devicesFromProgress() {
        val d = Parse.devices("""{"items":[
            {"movie_id":"m_a","pos_ms":600000,"dur_ms":5400000,"watched":0,"updated":1000,"device":"tv"},
            {"movie_id":"m_b","pos_ms":0,"dur_ms":5400000,"watched":1,"updated":2000,"device":"tv"},
            {"movie_id":"m_c","pos_ms":900000,"dur_ms":5400000,"watched":0,"updated":1500,"device":"phone"},
            {"movie_id":"m_d","pos_ms":0,"dur_ms":1,"watched":0,"updated":900,"device":""}]}""")
        assertEquals(listOf("tv", "phone", "Unnamed device"), d.map { it.name })
        assertEquals(DeviceSeen("tv", 2000, 2, 1, 1), d[0])
        assertTrue(Parse.devices("""{"items":[]}""").isEmpty())
    }
    @Test fun hotWarningHasAKeySoItsResolutionIsNamed() {
        val s = SysStatus(1000, 0.5, 4, 8_000_000_000, 4_000_000_000, 90.0, emptyMap(), null, null, true)
        val hot = Alerts.from(s, null, 0.0).single { it.text.contains("hot") }
        assertEquals("hot", hot.key)
        val ev = Events.diff(Snapshot(true, mapOf("hot" to hot.text)), Snapshot(true, emptyMap()), 1)
        assertEquals("Resolved: temperature back to normal", ev.single().text)
    }
    @Test fun scanStartedFlag() { assertTrue(Parse.scanStarted("""{"started":true}""")); assertFalse(Parse.scanStarted("""{"started":false,"scan":{}}""")) }

    @Test fun agoAndActivity() {
        assertEquals("just now", Ago.text(1000, 990)); assertEquals("5 min ago", Ago.text(1000, 700)); assertEquals("3 h ago", Ago.text(100000, 100000 - 3 * 3600)); assertEquals("2 d ago", Ago.text(1000000, 1000000 - 2 * 86400))
        assertTrue(Ago.active(1000, 800)); assertFalse(Ago.active(1000, 600))
    }
    @Test fun seriesHonesty() {
        assertFalse(Series.enough(3)); assertTrue(Series.enough(6)); assertEquals("", Series.span(listOf(5L)))
        assertEquals("last 30 s", Series.span(listOf(0L, 30_000L))); assertEquals("last 5 min", Series.span(listOf(0L, 300_000L)))
    }
}
