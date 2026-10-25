package com.rasha.omni.library

import com.rasha.omni.common.readUpTo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Poster/backdrop loader: disk cache first, https only from TMDB's image host, decoded at the size the screen needs, memory bounded. Never on the main thread. */
class Images(ctx: Context) {
    private val dir = File(ctx.cacheDir, "cinema-img").also { it.mkdirs() }
    private val mem = object : LruCache<String, Bitmap>(12 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }

    private val scrimMem = object : LruCache<String, Bitmap>(10 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }

    /**
     * A full-screen backdrop with the reading scrim baked in (dark on the left where the text sits, dark at the bottom where the shelves are), made once and cached.
     * Compositing it later is a single bitmap draw: no full-screen gradients are drawn per frame, which this TV's GPU cannot afford.
     */
    suspend fun scrimmed(tmdbPath: String?, w: Int = 1280, h: Int = 720, ground: Int = 0xFF0A0A0A.toInt(), bottomFrom: Float = 0.38f, bottomTo: Float = 1f, topTo: Float = 0f): Bitmap? {
        if (tmdbPath == null || !Regex("/[A-Za-z0-9_.-]{1,80}").matches(tmdbPath)) return null
        val key = "$tmdbPath@$w#$ground/$bottomFrom/$bottomTo/$topTo"
        scrimMem.get(key)?.let { return it }
        val src = load("https://image.tmdb.org/t/p/w1280$tmdbPath", w) ?: return null
        return withContext(Dispatchers.Default) {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(out)
            c.drawColor(ground)
            val g0 = ground and 0x00FFFFFF
            val s = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
            val dw = src.width * s; val dh = src.height * s
            c.drawBitmap(src, null, android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            val p = android.graphics.Paint()
            p.shader = android.graphics.LinearGradient(0f, 0f, w * 0.72f, 0f, intArrayOf(g0 or 0xF0000000.toInt(), g0 or 0xB0000000.toInt(), g0), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = android.graphics.LinearGradient(0f, h * bottomFrom, 0f, h * bottomTo, g0, ground, android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            if (topTo > 0f) {                                            // a shade under a navigation bar, so it reads on bright pictures
                p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h * topTo, g0 or 0xC0000000.toInt(), g0, android.graphics.Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, w.toFloat(), h * topTo, p)
            }
            scrimMem.put(key, out)
            out
        }
    }

    /** The picture's mood colour: the average of its most saturated part, made a little more vivid. Cheap (one 12x7 downscale), cached per picture. */
    private val tints = LruCache<String, Int>(64)
    suspend fun tint(tmdbPath: String?): Int? {
        if (tmdbPath == null || !Regex("/[A-Za-z0-9_.-]{1,80}").matches(tmdbPath)) return null
        tints.get(tmdbPath)?.let { return it }
        val src = load("https://image.tmdb.org/t/p/w300$tmdbPath", 300) ?: return null
        return withContext(Dispatchers.Default) {
            val small = Bitmap.createScaledBitmap(src, 12, 7, true)
            val hsv = FloatArray(3); var best = 0f; var r = 0f; var g = 0f; var b = 0f; var n = 0f
            for (y in 0 until 7) for (x in 0 until 12) {
                val c = small.getPixel(x, y); android.graphics.Color.colorToHSV(c, hsv)
                val wgt = hsv[1] * (0.25f + hsv[2])                     // saturated, not black: what the eye reads as the picture's colour
                if (wgt > 0.05f) { r += android.graphics.Color.red(c) * wgt; g += android.graphics.Color.green(c) * wgt; b += android.graphics.Color.blue(c) * wgt; n += wgt }
                best = maxOf(best, wgt)
            }
            val out = if (n <= 0f) 0xFF3A3A40.toInt() else {
                android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
                hsv[1] = (hsv[1] * 1.25f).coerceAtMost(0.8f); hsv[2] = hsv[2].coerceIn(0.35f, 0.75f)
                android.graphics.Color.HSVToColor(hsv)
            }
            tints.put(tmdbPath, out); out
        }
    }

    suspend fun load(url: String?, maxW: Int): Bitmap? {
        if (url == null || !url.startsWith("https://image.tmdb.org/")) return null
        val key = url + "@" + maxW
        mem.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                val f = File(dir, MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) })
                if (!f.exists()) {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 6000; c.readTimeout = 10000
                    try { check(c.responseCode == 200); val b = c.inputStream.readUpTo(3_000_000); File(dir, f.name + ".tmp").apply { writeBytes(b); renameTo(f) } } finally { c.disconnect() }
                }
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(f.path, o)
                var s = 1; while (o.outWidth / (s * 2) >= maxW) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 })?.also { mem.put(key, it) }
            } catch (e: Exception) { null }
        }
    }
}
