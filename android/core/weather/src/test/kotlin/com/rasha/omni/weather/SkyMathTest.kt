package com.rasha.omni.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyMathTest {
    private val sr = 7 * 60; private val ss = 18 * 60          // 07:00 to 18:00

    @Test fun noonIsThePeakAndTheHorizonIsZero() {
        assertEquals(1.0, SkyMath.altitude(12 * 60 + 30, sr, ss), 0.001)
        assertEquals(0.0, SkyMath.altitude(sr, sr, ss), 0.001)
        assertTrue(SkyMath.altitude(ss + 1, sr, ss) <= 0.0)
    }

    @Test fun nightIsNegativeAndDeepestAtMidNight() {
        val midnightOfNight = ((ss + (24 * 60 - (ss - sr)) / 2) % 1440)
        assertEquals(-1.0, SkyMath.altitude(midnightOfNight, sr, ss), 0.001)
        assertTrue(SkyMath.altitude(2 * 60, sr, ss) < 0)
    }

    @Test fun theBodyMovesForwardAcrossItsArc() {
        val a = (sr until ss step 30).map { SkyMath.arc(it, sr, ss) }
        assertTrue(a.zipWithNext().all { (x, y) -> y > x }); assertEquals(0.0, a.first(), 0.0); assertTrue(a.last() <= 1.0)
        val night = listOf(ss + 10, 22 * 60, 23 * 60 + 50, 2 * 60, 5 * 60, sr - 5).map { SkyMath.arc(it, sr, ss) }
        assertTrue(night.zipWithNext().all { (x, y) -> y > x })
    }

    @Test fun midnightWrapsAndNegativeMinutesAreSafe() {
        assertEquals(SkyMath.arc(5 * 60, sr, ss), SkyMath.arc(5 * 60 + 1440, sr, ss), 1e-9)
        assertTrue(SkyMath.arc(-60, sr, ss) in 0.0..1.0)
    }

    @Test fun weightsAlwaysSumToOne() {
        for (alt in listOf(-1.0, -0.5, -0.2, -0.06, 0.0, 0.06, 0.2, 0.5, 1.0)) {
            val (d, t, n) = SkyMath.weights(alt)
            assertEquals(1.0, d + t + n, 1e-9); assertTrue(d >= 0 && t >= -1e-9 && n >= 0)
        }
        assertEquals(1.0, SkyMath.weights(1.0).first, 1e-9); assertEquals(1.0, SkyMath.weights(-1.0).third, 1e-9); assertEquals(1.0, SkyMath.weights(0.0).second, 1e-9)
    }

    @Test fun mapperParsesTimesAndCloudiness() {
        assertEquals(432, UiMapper.minutes("07:12")); assertEquals(null, UiMapper.minutes("7:12")); assertEquals(null, UiMapper.minutes(""))
        assertEquals(0.4f, UiMapper.cloudiness(Kind.Partly, -1), 0f); assertEquals(0.73f, UiMapper.cloudiness(Kind.Clear, 73), 0.001f)
    }

    @Test fun moonPhasesMatchKnownNewAndFullMoons() {
        assertEquals(0.0, SkyMath.moonIllumination(947182440_000L), 0.01)             // new moon 2000-01-06 18:14 UT
        assertEquals(1.0, SkyMath.moonIllumination(948429600_000L), 0.02)             // full moon 2000-01-21 04:40 UT
        assertTrue(SkyMath.moonIllumination(1792987920_000L) > 0.97)                  // full moon 2026-10-26 about 04:12 UT
        assertTrue(SkyMath.moonWaxing(948000000_000L)); assertTrue(!SkyMath.moonWaxing(948700000_000L))
    }
}
