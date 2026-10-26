package com.rasha.omni.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HonestyTest {
    private val min = 60_000L
    @Test fun freshnessFollowsAgeAndFailure() {
        assertEquals(Freshness.Live, Honesty.freshness(5 * min, false))
        assertEquals(Freshness.Live, Honesty.freshness(40 * min, true))                 // a failed refresh right after a fresh load is not yet a problem
        assertEquals(Freshness.Aging, Honesty.freshness(90 * min, false))
        assertEquals(Freshness.Stale, Honesty.freshness(90 * min, true))                // old AND the refresh failed: not live
        assertEquals(Freshness.Stale, Honesty.freshness(4 * 60 * min, false))           // very old is stale even with no error shown
    }
    @Test fun statusNeverCallsOldDataLive() {
        assertEquals("Updated 5 min ago", Honesty.status(Freshness.Live, "5 min ago", false))
        assertEquals("Updating…", Honesty.status(Freshness.Stale, "5 h ago", true))
        assertTrue(Honesty.status(Freshness.Stale, "5 h ago", false).contains("Not live"))
    }

    private fun h(i: Int, k: Kind, pop: Int) = HourUi("", k, true, "", "", 0.0, pop, (10 + i) % 24)
    @Test fun outlookFromRealHours() {
        val dry = (0..11).map { h(it, Kind.Clear, 0) }
        assertEquals("Dry for the next 12 hours", Outlook.sentence(dry))
        val comingRain = (0..11).map { h(it, if (it >= 4) Kind.Rain else Kind.Cloud, if (it >= 4) 70 else 10) }
        assertEquals("Rain likely around 14:00", Outlook.sentence(comingRain))
        val raining = (0..11).map { h(it, if (it < 3) Kind.Rain else Kind.Cloud, if (it < 3) 80 else 10) }
        assertEquals("Rain now, easing around 13:00", Outlook.sentence(raining))
        assertNull(Outlook.sentence(dry.take(3)))                                       // too little data to say anything
        val mixed = (0..11).map { h(it, Kind.Cloud, 30) }
        assertNull(Outlook.sentence(mixed))                                             // unclear: say nothing rather than guess
    }
}
