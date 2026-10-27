package com.rasha.omni.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class BingFeedTest {
    private val sample = """{"images":[
      {"startdate":"20261027","urlbase":"/th?id=OHR.AutumnForest_EN-US1234567890","copyright":"Autumn forest near Berchtesgaden, Bavaria, Germany (© Jane Doe/Getty Images)","title":"Gold in the woods"},
      {"startdate":"20261026","urlbase":"/th?id=OHR.Lagoon_EN-US999","copyright":"A lagoon at dusk","title":""},
      {"startdate":"bad","urlbase":"/th?id=OHR.Nope_EN-US1","copyright":"x"},
      {"startdate":"20261025","urlbase":"https://evil.example/th?id=OHR.X","copyright":"x"}]}"""

    @Test fun parsesGoodEntriesAndDropsMalformedOnes() {
        val l = BingFeed.parse(sample)
        assertEquals(listOf("20261027", "20261026"), l.map { it.date })
        assertEquals("Gold in the woods", l[0].title)
        assertEquals("Autumn forest near Berchtesgaden, Bavaria, Germany", l[0].caption)
        assertEquals("© Jane Doe/Getty Images", l[0].attribution)
    }

    @Test fun missingAttributionIsEmptyNotInvented() {
        val (c, a) = BingFeed.split("A lagoon at dusk")
        assertEquals("A lagoon at dusk", c); assertEquals("", a)
    }

    @Test fun garbageAndWrongShapesGiveNothing() {
        assertTrue(BingFeed.parse("<html>").isEmpty())
        assertTrue(BingFeed.parse("""{"images":"no"}""").isEmpty())
        assertTrue(BingFeed.parse("""{"other":1}""").isEmpty())
    }

    @Test fun imageUrlIsOnBingAtTvResolution() =
        assertEquals("https://www.bing.com/th?id=OHR.A_EN-US1_1920x1080.jpg", BingFeed.imageUrl("/th?id=OHR.A_EN-US1"))

    @Test fun datesFormatInTheLocale() {
        assertEquals("27 Oct 2026", BingFeed.prettyDate("20261027", Locale.UK))
        assertEquals("", BingFeed.prettyDate("20261327", Locale.UK))
        assertEquals("", BingFeed.prettyDate("junk", Locale.UK))
    }

    @Test fun refreshIsRareAndBacksOffAfterFailure() {
        val h = 3_600_000L; val now = 100 * h
        assertTrue(BingFeed.due(now, 0, 0))                       // never fetched
        assertFalse(BingFeed.due(now, now - 3 * h, 0))            // fresh enough
        assertTrue(BingFeed.due(now, now - 13 * h, now - 13 * h)) // a new day
        assertFalse(BingFeed.due(now, now - 13 * h, now - h / 4)) // just failed: wait
        assertTrue(BingFeed.due(now, 0, now - h))                 // failed an hour ago: try again
    }

    @Test fun cacheKeepsOnlyTheNewestDays() {
        val dates = (1..15).map { "202610%02d".format(it) }
        val keep = BingFeed.keepNewest(dates + dates)
        assertEquals(BingProvider.KEEP, keep.size)
        assertTrue("20261015" in keep && "20261006" in keep && "20261005" !in keep)
    }
}
