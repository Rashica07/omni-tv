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
    suspend fun scrimmed(tmdbPath: String?, w: Int = 1280, h: Int = 720): Bitmap? {
        if (tmdbPath == null || !Regex("/[A-Za-z0-9_.-]{1,80}").matches(tmdbPath)) return null
        val key = "$tmdbPath@$w"
        scrimMem.get(key)?.let { return it }
        val src = load("https://image.tmdb.org/t/p/w1280$tmdbPath", w) ?: return null
        return withContext(Dispatchers.Default) {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            val c = android.graphics.Canvas(out)
            c.drawColor(0xFF0A0A0A.toInt())
            val s = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
            val dw = src.width * s; val dh = src.height * s
            c.drawBitmap(src, null, android.graphics.RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
            val p = android.graphics.Paint()
            p.shader = android.graphics.LinearGradient(0f, 0f, w * 0.72f, 0f, intArrayOf(0xF00A0A0A.toInt(), 0xB00A0A0A.toInt(), 0x000A0A0A), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = android.graphics.LinearGradient(0f, h * 0.38f, 0f, h.toFloat(), 0x000A0A0A, 0xFF0A0A0A.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            scrimMem.put(key, out)
            out
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
