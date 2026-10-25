package com.rasha.omni.weather

import com.rasha.omni.network.HttpJson

/**
 * Loads a forecast through the shared cached client. A response younger than [maxAgeMs] is served from disk with no request (so opening the app
 * never hits the network needlessly); a refresh that fails returns the last good forecast flagged with the reason rather than an error screen.
 */
class WeatherRepository(private val http: HttpJson, private val maxAgeMs: Long = 30 * 60_000L) {
    /** The stored forecast for a place (no request), or null. Lets the screen open instantly on the last data. */
    suspend fun cached(place: Place): Loaded? = http.peek(OpenMeteo.url(place))?.let { runCatching { Loaded(OpenMeteo.parse(it.body), it.fetchedAtMs, true) }.getOrNull() }

    suspend fun load(place: Place, force: Boolean = false): Loaded {
        val r = http.get(OpenMeteo.url(place), maxAgeMs, force)
        return Loaded(OpenMeteo.parse(r.body), r.fetchedAtMs, r.fromCache, r.error)
    }
}
