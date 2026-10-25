package com.rasha.omni.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Locale

class WeatherTest {
    private fun sample() = javaClass.classLoader!!.getResource("forecast.json")!!.readText()

    @Test fun kinds() {
        assertEquals(Kind.Clear, kindOf(0)); assertEquals(Kind.Partly, kindOf(2)); assertEquals(Kind.Cloud, kindOf(3)); assertEquals(Kind.Rain, kindOf(63))
        assertEquals(Kind.Snow, kindOf(73)); assertEquals(Kind.Storm, kindOf(96)); assertEquals(Kind.Fog, kindOf(45)); assertEquals(Kind.Unknown, kindOf(999))
    }

    @Test fun compassAndWords() {
        assertEquals("N", Present.compass(359.0)); assertEquals("E", Present.compass(90.0)); assertEquals("NW", Present.compass(315.0)); assertEquals("N", Present.compass(-10.0))
        assertEquals("Moderate", Present.uvLabel(3.0)); assertEquals("Extreme", Present.uvLabel(12.0)); assertEquals("", Present.uvLabel(-1.0))
        assertEquals("Calm", Present.windWord(1.0)); assertEquals("Breezy", Present.windWord(20.0))
    }

    @Test fun temperatures() {
        assertEquals("18°", Present.temp(18.4)); assertEquals("-3°", Present.temp(-2.6)); assertEquals("0°", Present.temp(-0.2))
        assertEquals("", Present.temp(Double.NaN)); assertEquals("", Present.temp(999.0))
    }

    @Test fun rangeBars() {
        val (a, b) = Present.range(8.0, 14.0, 5.0, 20.0)
        assertEquals(0.2f, a, 1e-4f); assertEquals(0.6f, b, 1e-4f)
        val (c, d) = Present.range(10.0, 10.0, 10.0, 10.0)
        assertTrue(d - c >= 0.06f)
    }

    @Test fun parsesARealResponse() {
        val f = OpenMeteo.parse(sample())
        assertEquals(7, f.days.size)
        assertEquals(24, f.hours.size)
        assertEquals("2026-10-24", f.days[0].date)
        assertTrue("hours start at the current hour", f.hours[0].hour == 21)
        assertFalse("21:00 is after sunset", f.hours[0].day)
    }

    @Test fun malformedBodyThrowsInsteadOfShowingGarbage() {
        var threw = false
        try { OpenMeteo.parse("{\"current\": {}}") } catch (e: Exception) { threw = true }
        assertTrue(threw)
        threw = false
        try { OpenMeteo.parse("not json") } catch (e: Exception) { threw = true }
        assertTrue(threw)
    }

    @Test fun uiModelIsPrecomputed() {
        val ui = UiMapper.map(Loaded(OpenMeteo.parse(sample()), 1L, false), "Civate", Locale.ITALY, h24 = true)
        assertEquals(7, ui.days.size); assertEquals("Today", ui.days[0].label)
        assertEquals("Now", ui.hours[0].label); assertEquals("22", ui.hours[1].label)
        assertEquals(6, ui.tiles.size)
        assertTrue(ui.days.all { it.barEnd - it.barStart >= 0.06f })
    }
}
