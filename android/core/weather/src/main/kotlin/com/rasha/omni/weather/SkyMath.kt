package com.rasha.omni.weather

import kotlin.math.PI
import kotlin.math.sin

/** Where the sun (by day) or the moon (by night) is, from the real sunrise and sunset. Pure maths so it is tested. */
object SkyMath {
    private const val DAY = 1440

    /** True when the sun is up. */
    fun isDay(nowMin: Int, sunrise: Int, sunset: Int) = nowMin in sunrise until sunset

    /** 0 at sunrise (or sunset, for the moon) through 1 at the end of the arc: how far along its path the visible body is. */
    fun arc(nowMin: Int, sunrise: Int, sunset: Int): Double {
        val n = ((nowMin % DAY) + DAY) % DAY
        return if (isDay(n, sunrise, sunset)) (n - sunrise).toDouble() / maxOf(1, sunset - sunrise)
        else { val since = ((n - sunset) % DAY + DAY) % DAY; since.toDouble() / maxOf(1, DAY - (sunset - sunrise)) }
    }

    /** Height of the sun as a fraction of its peak: 1 at solar noon, 0 at sunrise/sunset, negative (down to -1) at night. */
    fun altitude(nowMin: Int, sunrise: Int, sunset: Int): Double {
        val a = arc(nowMin, sunrise, sunset)
        val s = sin(PI * a.coerceIn(0.0, 1.0))
        return if (isDay(((nowMin % DAY) + DAY) % DAY, sunrise, sunset)) s else -s
    }

    /** How much of day, twilight and night colours to mix at this sun height. The three always add up to 1. */
    fun weights(alt: Double): Triple<Double, Double, Double> {
        fun smooth(a: Double, b: Double, x: Double): Double { val t = ((x - a) / (b - a)).coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
        val day = smooth(0.06, 0.40, alt); val night = smooth(0.06, 0.40, -alt)
        return Triple(day, 1.0 - day - night, night)
    }

    private const val SYNODIC = 29.530588853
    /** Days since a new moon (reference: 2000-01-06 18:14 UT). Good to about a day, which is all a picture of the moon needs. */
    fun moonAge(epochMs: Long): Double { val days = (epochMs / 1000.0 - 947182440.0) / 86400.0; return ((days % SYNODIC) + SYNODIC) % SYNODIC }

    /** 0 = new, 1 = full. */
    fun moonIllumination(epochMs: Long): Double = (1 - kotlin.math.cos(2 * PI * moonAge(epochMs) / SYNODIC)) / 2

    /** True while the lit part grows (new → full). */
    fun moonWaxing(epochMs: Long) = moonAge(epochMs) < SYNODIC / 2
}
