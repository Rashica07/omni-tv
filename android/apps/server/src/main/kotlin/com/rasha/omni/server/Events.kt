package com.rasha.omni.server

data class Event(val atMs: Long, val text: String, val warn: Boolean)

/** What was true at one poll. [reachable] is whether the server answered at all: an unreachable server and a failing service are different things. */
data class Snapshot(val reachable: Boolean, val alerts: Map<String, String>)

object Events {
    /** Real transitions only (server reached or lost, a warning raised or cleared). A steady state produces nothing, so the list never fills with noise. */
    fun diff(prev: Snapshot?, now: Snapshot, atMs: Long): List<Event> = buildList {
        if (prev == null) { if (!now.reachable) add(Event(atMs, "Server not reachable", true)); return@buildList }
        if (prev.reachable && !now.reachable) add(Event(atMs, "Lost contact with the server", true))
        if (!prev.reachable && now.reachable) add(Event(atMs, "Server reachable again", false))
        if (now.reachable && prev.reachable) {
            for ((k, t) in now.alerts) if (k !in prev.alerts) add(Event(atMs, t, true))
            for ((k, _) in prev.alerts) if (k !in now.alerts) add(Event(atMs, "Resolved: " + label(k), false))
        }
    }

    private fun label(key: String) = when {
        key == "hot" -> "temperature back to normal"; key == "load" -> "load back to normal"; key == "memory" -> "memory pressure"; key.startsWith("disk:") -> "disk space (${key.substringAfter(':')})"
        key == "battery" -> "power"; key == "channels" -> "channel check"; key == "probe-stale" -> "channel check is recent again"; else -> key
    }
}
