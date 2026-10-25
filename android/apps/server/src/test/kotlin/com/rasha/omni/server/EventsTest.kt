package com.rasha.omni.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsTest {
    private fun up(vararg a: Pair<String, String>) = Snapshot(true, mapOf(*a))
    private val down = Snapshot(false, emptyMap())

    @Test fun steadyStateMakesNoEvents() {
        assertEquals(0, Events.diff(up("hot" to "x"), up("hot" to "x"), 1).size)
        assertEquals(0, Events.diff(up(), up(), 1).size)
    }

    @Test fun lostAndRegained() {
        assertEquals("Lost contact with the server", Events.diff(up(), down, 5)[0].text)
        assertEquals("Server reachable again", Events.diff(down, up(), 6)[0].text)
        assertEquals(0, Events.diff(down, down, 7).size)
    }

    @Test fun warningRaisedAndCleared() {
        val raised = Events.diff(up(), up("hot" to "Running hot (96)"), 1)
        assertEquals(listOf("Running hot (96)"), raised.map { it.text }); assertTrue(raised[0].warn)
        val cleared = Events.diff(up("hot" to "x"), up(), 2)
        assertEquals("Resolved: temperature back to normal", cleared[0].text); assertTrue(!cleared[0].warn)
    }

    @Test fun anUnreachableServerDoesNotClaimWarningsCleared() {
        assertEquals(1, Events.diff(up("hot" to "x"), down, 1).size)         // only "lost contact": we cannot know the warnings are gone
    }

    @Test fun firstPollWhileDown() {
        assertEquals("Server not reachable", Events.diff(null, down, 1)[0].text)
        assertEquals(0, Events.diff(null, up("hot" to "x"), 1).size)
    }
}
