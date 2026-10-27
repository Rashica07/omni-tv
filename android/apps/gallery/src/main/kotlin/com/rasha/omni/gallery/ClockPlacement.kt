package com.rasha.omni.gallery

enum class Spot { Center, Left, Right, Top, Bottom }

/** Where the clock goes on a photograph, and whether it should be dark ink (a bright photograph) or ivory (a dark one). */
data class Placement(val spot: Spot, val darkInk: Boolean)

/**
 * Picks the quietest part of the photograph for the clock: the region with the least detail (small luminance variation and few edges), so the subject is
 * left clear. Works on a tiny luminance grid (32x18), so it costs nothing. Pure, so it is tested.
 */
object ClockPlacement {
    const val GW = 32
    const val GH = 18

    /** Regions as fractions (x0, y0, x1, y1), and a small bias so Center wins close calls (the lock-screen composition), then the sides, then top/bottom. */
    private val regions = mapOf(
        Spot.Center to floatArrayOf(0.18f, 0.25f, 0.82f, 0.75f),
        Spot.Left to floatArrayOf(0.04f, 0.2f, 0.52f, 0.8f),
        Spot.Right to floatArrayOf(0.48f, 0.2f, 0.96f, 0.8f),
        Spot.Top to floatArrayOf(0.04f, 0.04f, 0.7f, 0.36f),
        Spot.Bottom to floatArrayOf(0.04f, 0.64f, 0.7f, 0.96f),
    )
    private val bias = mapOf(Spot.Center to 0f, Spot.Left to 0.012f, Spot.Right to 0.012f, Spot.Top to 0.07f, Spot.Bottom to 0.07f)

    /** [luma] is GW*GH values in 0..1, row by row. [forced] fixes the spot (the person chose one) but the ink is still chosen from the photograph. */
    fun choose(luma: FloatArray, forced: Spot? = null): Placement {
        if (luma.size < GW * GH) return Placement(forced ?: Spot.Center, false)
        val spot = forced ?: regions.keys.minByOrNull { detail(luma, regions.getValue(it)) + bias.getValue(it) }!!
        return Placement(spot, mean(luma, regions.getValue(spot)) > 0.6f)
    }

    private fun cells(r: FloatArray): IntArray = intArrayOf((r[0] * GW).toInt(), (r[1] * GH).toInt(), (r[2] * GW).toInt().coerceAtMost(GW), (r[3] * GH).toInt().coerceAtMost(GH))

    fun mean(l: FloatArray, r: FloatArray): Float {
        val (x0, y0, x1, y1) = cells(r).toList()
        var s = 0f; var n = 0
        for (y in y0 until y1) for (x in x0 until x1) { s += l[y * GW + x]; n++ }
        return if (n == 0) 0f else s / n
    }

    /** Standard deviation plus the mean absolute difference between neighbours: flat sky scores near 0, a busy subject scores high. */
    fun detail(l: FloatArray, r: FloatArray): Float {
        val (x0, y0, x1, y1) = cells(r).toList()
        val m = mean(l, r)
        var v = 0f; var e = 0f; var n = 0; var en = 0
        for (y in y0 until y1) for (x in x0 until x1) {
            val p = l[y * GW + x]
            v += (p - m) * (p - m); n++
            if (x + 1 < x1) { e += Math.abs(p - l[y * GW + x + 1]); en++ }
            if (y + 1 < y1) { e += Math.abs(p - l[(y + 1) * GW + x]); en++ }
        }
        return if (n == 0) 0f else Math.sqrt((v / n).toDouble()).toFloat() + (if (en == 0) 0f else e / en)
    }

    /** Rec. 709 luminance of an ARGB pixel, 0..1. */
    fun luma(argb: Int): Float = (0.2126f * ((argb shr 16) and 0xFF) + 0.7152f * ((argb shr 8) and 0xFF) + 0.0722f * (argb and 0xFF)) / 255f
}
