package com.rasha.omni.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideOrderTest {
    @Test fun sequentialGoesInOrderAndWraps() {
        val o = SlideOrder(3, shuffle = false)
        assertEquals(listOf(0, 1, 2, 0, 1, 2), (1..6).map { o.next() })
    }

    @Test fun shuffleShowsEveryPhotoOncePerRound() {
        val o = SlideOrder(10, shuffle = true, seed = 5)
        repeat(4) { assertEquals((0 until 10).toSet(), (1..10).map { o.next() }.toSet()) }
    }

    @Test fun shuffleNeverRepeatsAcrossRoundBoundary() {
        for (seed in 1L..200L) {
            val o = SlideOrder(5, shuffle = true, seed = seed)
            val seq = (1..50).map { o.next() }
            assertTrue("seed $seed", seq.zipWithNext().none { (a, b) -> a == b })
        }
    }

    @Test fun emptyAndSingle() {
        assertEquals(-1, SlideOrder(0, true).next())
        val one = SlideOrder(1, true)
        assertEquals(listOf(0, 0, 0), (1..3).map { one.next() })
    }
}
