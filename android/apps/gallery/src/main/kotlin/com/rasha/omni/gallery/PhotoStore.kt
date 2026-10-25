package com.rasha.omni.gallery

import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Source { Curated, Local }

/** One photograph. [uri] is a file path (curated) or a content URI (this TV's photos). Credit and licence travel with it so they can always be shown. */
data class Photo(val id: String, val uri: String, val title: String, val credit: String, val source: Source)

/**
 * Where photographs come from. Curated landscapes are downloaded from Wikimedia Commons (open licences only, credit kept, cached on disk so the frame
 * works offline). This TV's own photos come from the media store and need the storage permission. Nothing is ever uploaded anywhere.
 */
class PhotoStore(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "curated").also { it.mkdirs() }
    private val prefs = ctx.getSharedPreferences("gallery", Context.MODE_PRIVATE)
    private val ua = "OMNI-Gallery/1.0 (personal Android TV photo frame)"

    fun hasStoragePermission() = ctx.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun curated(): List<Photo> = try {
        val a = JSONArray(File(dir, "index.json").takeIf { it.exists() }?.readText() ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val f = File(dir, o.getString("file"))
            if (f.exists() && o.getString("file").matches(Regex("c_[0-9a-f]+\\.jpg"))) Photo("c:" + o.getString("file"), f.path, o.optString("title"), o.optString("credit"), Source.Curated) else null
        }
    } catch (e: Exception) { emptyList() }

    fun local(limit: Int = 600): List<Photo> {
        if (!hasStoragePermission()) return emptyList()
        val out = ArrayList<Photo>()
        try {
            ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    if (c.getInt(2) in 1..399 || c.getInt(3) in 1..399) continue            // thumbnails and icons are not photographs
                    val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0))
                    out += Photo("l:" + c.getLong(0), uri.toString(), c.getString(1).orEmpty().substringBeforeLast('.'), "On this TV", Source.Local)
                }
            }
        } catch (e: SecurityException) { }
        return out
    }

    // ---- favourites
    fun favorites(): Set<String> = prefs.getStringSet("fav", emptySet()) ?: emptySet()
    fun toggleFavorite(id: String): Boolean { val s = favorites().toMutableSet(); val now = s.add(id); if (!now) s.remove(id); prefs.edit().putStringSet("fav", s).apply(); return now }

    // ---- network (call off the main thread)
    /** Adds up to [max] new curated photographs. Returns how many were added; throws only if the very first request fails (so the caller can say offline). */
    fun refreshCurated(max: Int = 6): Int {
        val list = JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&format=json&list=categorymembers&cmtitle=Category:Featured_pictures_of_landscapes&cmtype=file&cmlimit=500&cmprop=title", 300_000)).getJSONObject("query").getJSONArray("categorymembers")
        if (list.length() == 0) return 0
        val day = Math.floorDiv(System.currentTimeMillis(), 86_400_000L)
        val titles = (0 until 18).joinToString("|") { k -> list.getJSONObject(Math.floorMod(day * 18 + k * 7, list.length().toLong()).toInt()).getString("title") }
        val info = JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=1920&iiextmetadatafilter=LicenseShortName|Artist&titles=" + URLEncoder.encode(titles, "UTF-8"), 500_000))
            .getJSONObject("query").getJSONObject("pages")
        val index = JSONArray(File(dir, "index.json").takeIf { it.exists() }?.readText() ?: "[]")
        var added = 0
        val keys = info.keys()
        while (keys.hasNext() && added < max) {
            val pg = info.getJSONObject(keys.next())
            val i0 = pg.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
            val meta = i0.optJSONObject("extmetadata")
            val lic = meta?.optJSONObject("LicenseShortName")?.optString("value").orEmpty()
            if (!acceptable(i0.optInt("width"), i0.optInt("height")) || !licenceAllowed(lic)) continue
            val url = i0.optString("thumburl")
            if (!url.startsWith("https://upload.wikimedia.org/") && !url.startsWith("https://thumb.wikimedia.org/")) continue
            val file = "c_" + java.lang.Long.toHexString(url.hashCode().toLong() and 0xffffffffL) + ".jpg"
            if ((0 until index.length()).any { index.getJSONObject(it).getString("file") == file }) continue
            val data = try { getBytes(url, 3_500_000) } catch (e: Exception) { continue }
            if (!looksLikeJpeg(data)) continue
            FileOutputStream(File(dir, file)).use { it.write(data) }
            val title = plain(pg.optString("title").removePrefix("File:").substringBeforeLast('.'), 80)
            val artist = plain(meta?.optJSONObject("Artist")?.optString("value").orEmpty(), 60)
            index.put(JSONObject().put("file", file).put("title", title).put("credit", listOf(artist, lic, "Wikimedia Commons").filter { it.isNotEmpty() }.joinToString("  ·  ")))
            added++
        }
        while (index.length() > 40) { File(dir, index.getJSONObject(0).getString("file")).delete(); index.remove(0) }
        File(dir, "index.json").writeText(index.toString())
        return added
    }

    private fun get(url: String, max: Int) = String(getBytes(url, max), Charsets.UTF_8)

    private fun getBytes(url: String, max: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 15000; c.setRequestProperty("User-Agent", ua)
        try {
            check(c.responseCode == 200) { "http ${c.responseCode}" }
            c.inputStream.use { ins ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(16384)
                while (true) { val n = ins.read(buf); if (n < 0) break; out.write(buf, 0, n); check(out.size() <= max) { "too large" } }
                return out.toByteArray()
            }
        } finally { c.disconnect() }
    }

    /** Decodes near the screen size (never the full-resolution original), applying the photo's own orientation. Off the main thread only. */
    fun decode(p: Photo, w: Int, h: Int): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        fun open() = if (p.source == Source.Curated) java.io.FileInputStream(p.uri) else ctx.contentResolver.openInputStream(Uri.parse(p.uri))!!
        open().use { BitmapFactory.decodeStream(it, null, opts) }
        var s = 1
        while (opts.outWidth / (s * 2) >= w && opts.outHeight / (s * 2) >= h) s *= 2
        val raw = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s; inPreferredConfig = Bitmap.Config.RGB_565 }) }
        val bmp = raw?.let { fitToScreen(it, w, h) }
        if (bmp != null && p.source == Source.Local) rotate(p, bmp) else bmp
    } catch (e: Exception) { null } catch (e: OutOfMemoryError) { null }

    /** Never keep more pixels than the screen shows: this TV's GPU is the bottleneck, and a 1080p photo on a 720p panel doubles the blend cost for nothing. */
    private fun fitToScreen(b: Bitmap, w: Int, h: Int): Bitmap {
        val f = maxOf(w / b.width.toFloat(), h / b.height.toFloat())
        if (f >= 1f) return b
        val out = Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
        if (out !== b) b.recycle()
        return out
    }

    private fun rotate(p: Photo, bmp: Bitmap): Bitmap {
        val deg = try {
            ctx.contentResolver.openInputStream(Uri.parse(p.uri))!!.use { ins ->
                when (ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
            }
        } catch (e: Exception) { 0f }
        return if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
    }

    companion object {
        fun acceptable(w: Int, h: Int) = w >= 1600 && h > 0 && (w / h.toFloat()) in 1.4f..2.0f
        fun licenceAllowed(l: String): Boolean { val s = l.lowercase(); return s.startsWith("cc by") || s.startsWith("cc0") || s.contains("public domain") || s == "pd" }
        fun looksLikeJpeg(b: ByteArray) = b.size > 20_000 && b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()
        fun plain(html: String, max: Int) = html.replace(Regex("(?s)<[^>]*>"), " ").replace("&amp;", "&").replace("&quot;", "\"").replace(Regex("\\s+"), " ").trim().take(max)
    }
}
