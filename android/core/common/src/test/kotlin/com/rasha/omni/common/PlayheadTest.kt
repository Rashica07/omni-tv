package com.rasha.omni.common

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayheadTest {
    @Test fun advancesWhilePlaying() = assertEquals(65_000L, Playhead.at(62_000, 1_000, 4_000, 1f, true, 214_000))
    @Test fun respectsSpeed() = assertEquals(68_000L, Playhead.at(62_000, 1_000, 4_000, 2f, true, 214_000))
    @Test fun frozenWhenPaused() = assertEquals(62_000L, Playhead.at(62_000, 1_000, 900_000, 1f, false, 214_000))
    @Test fun neverPastTheEnd() = assertEquals(214_000L, Playhead.at(210_000, 0, 60_000, 1f, true, 214_000))
    @Test fun unknownPositionStaysUnknown() = assertEquals(-1L, Playhead.at(-1, 0, 5_000, 1f, true, 214_000))
    @Test fun noDurationStillCountsUp() = assertEquals(9_000L, Playhead.at(5_000, 0, 4_000, 1f, true, 0))
}
