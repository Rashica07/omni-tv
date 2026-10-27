package com.rasha.omni.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockPlacementTest {
    private val gw = ClockPlacement.GW; private val gh = ClockPlacement.GH
    private fun grid(f: (Int, Int) -> Float) = FloatArray(gw * gh) { f(it % gw, it / gw) }

    @Test fun flatSkyKeepsTheCenterComposition() {
        val p = ClockPlacement.choose(grid { _, _ -> 0.2f })
        assertEquals(Spot.Center, p.spot); assertFalse(p.darkInk)
    }

    @Test fun aBusySubjectInTheMiddleMovesTheClockAside() {
        // a bright, detailed subject in the centre-left; quiet on the right
        val g = grid { x, y -> if (x in 3..12 && y in 4..14) (if ((x + y) % 2 == 0) 0.9f else 0.1f) else 0.3f }
        assertEquals(Spot.Right, ClockPlacement.choose(g).spot)
    }

    @Test fun detailAtTheBottomPushesItUp() {
        val g = grid { x, y -> if (y >= 11) (if ((x + y) % 2 == 0) 0.8f else 0.05f) else 0.35f }
        assertTrue(ClockPlacement.choose(g).spot in listOf(Spot.Top, Spot.Center))
    }

    @Test fun brightPhotographsGetDarkInk() {
        assertTrue(ClockPlacement.choose(grid { _, _ -> 0.85f }).darkInk)
        assertFalse(ClockPlacement.choose(grid { _, _ -> 0.45f }).darkInk)
    }

    @Test fun aChosenSpotIsHonouredButInkStillFollowsThePhotograph() {
        val p = ClockPlacement.choose(grid { _, _ -> 0.9f }, forced = Spot.Left)
        assertEquals(Spot.Left, p.spot); assertTrue(p.darkInk)
    }

    @Test fun anUnusableGridFallsBackToCenter() = assertEquals(Spot.Center, ClockPlacement.choose(FloatArray(3)).spot)

    @Test fun lumaIsPerceptual() {
        assertEquals(1f, ClockPlacement.luma(0xFFFFFFFF.toInt()), 0.001f)
        assertEquals(0f, ClockPlacement.luma(0xFF000000.toInt()), 0.001f)
        assertTrue(ClockPlacement.luma(0xFF00FF00.toInt()) > ClockPlacement.luma(0xFF0000FF.toInt()))
    }
}
