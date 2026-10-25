package com.rasha.omni.library

/** When to offer "Resume", when something counts as watched. Kodi's defaults: ignore the first 5% and the last 10%. */
object PlayRules {
    const val WATCHED_AT = 0.90
    private const val MIN_START_MS = 60_000L

    fun isWatched(posMs: Long, durMs: Long) = durMs > 0 && posMs >= durMs * WATCHED_AT

    /** Where to resume from, or null to start from the beginning. */
    fun resumeAt(posMs: Long, durMs: Long): Long? =
        if (durMs <= 0 || posMs < MIN_START_MS || posMs < durMs * 0.05 || isWatched(posMs, durMs)) null else posMs

    /** The position to save when playback stops: a finished film is saved as 0 and watched; a barely started one is not saved at all. */
    fun toSave(posMs: Long, durMs: Long): Pair<Long, Boolean>? = when {
        durMs <= 0 -> null
        isWatched(posMs, durMs) -> 0L to true
        posMs < MIN_START_MS -> null
        else -> posMs to false
    }

    fun clock(ms: Long): String { val s = ms / 1000; val h = s / 3600; val m = s % 3600 / 60; return if (h > 0) "%d:%02d:%02d".format(h, m, s % 60) else "%d:%02d".format(m, s % 60) }
}
