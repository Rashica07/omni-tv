package com.rasha.omni.gallery

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** A source of ambient photographs. The ambient display only knows this interface, so Bing can be replaced without touching it. */
interface ImageProvider {
    /** What is already on disk (works offline). */
    fun cached(): List<Photo>
    /** Fetches new photographs only when they are due. Off the main thread. Returns how many were added; throws only when the network was needed and failed. */
    fun refreshIfDue(now: Long = System.currentTimeMillis()): Int
}

/** One entry of Bing's daily-image feed. Only what the feed really contains: nothing here is guessed. */
data class BingImage(val date: String, val urlBase: String, val title: String, val caption: String, val attribution: String)

/**
 * Pure parsing and scheduling for Bing's daily photograph. The feed (www.bing.com/HPImageArchive.aspx) is the one Bing's own home page uses; it is not a
 * documented API, which is why it is isolated here behind [ImageProvider]. Bing's `copyright` field reads "Description, Place (© Photographer / Agency)":
 * it is split on the "(©" marker, never interpreted further (a place is shown only as part of the description Bing wrote).
 */
object BingFeed {
    private val BASE = Regex("^/th\\?id=OHR\\.[A-Za-z0-9_\\-]{3,120}$")
    private val DATE = Regex("^[0-9]{8}$")

    fun parse(json: String): List<BingImage> {
        val arr: JSONArray = try { JSONObject(json).getJSONArray("images") } catch (e: Exception) { return emptyList() }
        val out = ArrayList<BingImage>()
        for (i in 0 until minOf(arr.length(), 16)) {
            val o = arr.optJSONObject(i) ?: continue
            val base = o.optString("urlbase")
            val date = o.optString("startdate")
            if (!BASE.matches(base) || !DATE.matches(date)) continue                        // malformed or unexpected entries are dropped, not repaired
            val (caption, attribution) = split(o.optString("copyright"))
            out += BingImage(date, base, o.optString("title").trim().take(120), caption, attribution)
        }
        return out
    }

    /** "Autumn forest, Bavaria (© Jane Doe/Getty Images)" -> ("Autumn forest, Bavaria", "© Jane Doe/Getty Images"). Without the marker the whole text is the description. */
    fun split(copyright: String): Pair<String, String> {
        val t = copyright.replace(Regex("\\s+"), " ").trim()
        val i = t.indexOf("(©")
        if (i < 0) return t.take(200) to ""
        return t.substring(0, i).trim().take(200) to t.substring(i + 1).trim().removeSuffix(")").trim().take(120)
    }

    /** Image URL at television resolution. */
    fun imageUrl(urlBase: String) = "https://www.bing.com$urlBase" + "_1920x1080.jpg"

    /** "20261027" -> "27 Oct 2026" in the given locale; anything unparsable gives "". */
    fun prettyDate(yyyymmdd: String, locale: Locale): String = try {
        val f = java.text.SimpleDateFormat("yyyyMMdd", Locale.US).apply { isLenient = false }
        java.text.SimpleDateFormat("d MMM yyyy", locale).format(f.parse(yyyymmdd)!!)
    } catch (e: Exception) { "" }

    /**
     * Whether to ask Bing now: never while a good reading is under 12 hours old (the image changes once a day), and not more often than every 30 minutes
     * after a failure, so an offline TV does not retry in a loop.
     */
    fun due(now: Long, lastOk: Long, lastTry: Long): Boolean = (lastOk <= 0 || now - lastOk >= 12 * H) && (lastTry <= 0 || now - lastTry >= H / 2)

    /** The dates to keep: the newest [BingProvider.KEEP], so the cache cannot grow without bound. */
    fun keepNewest(dates: List<String>): Set<String> = dates.distinct().sortedDescending().take(BingProvider.KEEP).toSet()

    private const val H = 3_600_000L
}

/** Bing's daily photographs, cached on disk (the last [KEEP] days) and refreshed at most twice a day. Personal use, credit kept with each photograph. */
class BingProvider(private val ctx: Context) : ImageProvider {
    private val dir = File(ctx.filesDir, "bing").also { it.mkdirs() }
    private val prefs = ctx.getSharedPreferences("gallery_bing", Context.MODE_PRIVATE)
    private val ua = "OMNI-Gallery/1.0 (personal Android TV photo frame)"

    override fun cached(): List<Photo> = try {
        val a = JSONArray(File(dir, "index.json").takeIf { it.exists() }?.readText() ?: "[]")
        val loc = Locale.getDefault()
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val name = o.getString("file")
            val f = File(dir, name)
            if (!f.exists() || !name.matches(Regex("b_[0-9]{8}\\.jpg"))) return@mapNotNull null
            val title = o.optString("title").ifBlank { o.optString("caption") }
            val credit = listOf(o.optString("attribution"), "Bing daily photo", BingFeed.prettyDate(o.optString("date"), loc)).filter { it.isNotEmpty() }.joinToString("  ·  ")
            Photo("b:$name", f.path, title, credit, Source.Bing, date = o.optString("date"), caption = o.optString("caption").takeIf { it != title }.orEmpty())
        }.sortedByDescending { it.date }
    } catch (e: Exception) { emptyList() }

    /** The person asked to update now: skip the schedule once. */
    fun refreshNow(): Int { prefs.edit().remove("ok").remove("try").apply(); return refreshIfDue() }

    override fun refreshIfDue(now: Long): Int {
        if (!BingFeed.due(now, prefs.getLong("ok", 0), prefs.getLong("try", 0))) return 0
        prefs.edit().putLong("try", now).apply()
        val tag = Locale.getDefault().toLanguageTag().takeIf { it.matches(Regex("[a-z]{2,3}-[A-Z]{2}")) } ?: "en-US"
        val feed = BingFeed.parse(String(getBytes("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=" + URLEncoder.encode(tag, "UTF-8"), 200_000), Charsets.UTF_8))
        if (feed.isEmpty()) throw java.io.IOException("empty or unexpected feed")
        val index = JSONArray(File(dir, "index.json").takeIf { it.exists() }?.readText() ?: "[]")
        var added = 0
        for (img in feed.take(3)) {                                                          // today's and the two before it: enough, and little to download
            val file = "b_${img.date}.jpg"
            if (File(dir, file).exists()) continue                                           // never the same image twice
            val data = try { getBytes(BingFeed.imageUrl(img.urlBase), 3_500_000) } catch (e: Exception) { continue }
            if (!PhotoStore.looksLikeJpeg(data)) continue
            FileOutputStream(File(dir, file)).use { it.write(data) }
            index.put(JSONObject().put("file", file).put("date", img.date).put("title", img.title).put("caption", img.caption).put("attribution", img.attribution))
            added++
        }
        val keep = BingFeed.keepNewest((0 until index.length()).map { index.getJSONObject(it).getString("date") })
        val next = JSONArray()
        for (i in 0 until index.length()) {
            val o = index.getJSONObject(i)
            if (o.getString("date") in keep) next.put(o) else File(dir, o.getString("file")).delete()
        }
        File(dir, "index.json").writeText(next.toString())
        prefs.edit().putLong("ok", now).apply()                                              // only a feed that parsed counts as a successful reading
        return added
    }

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

    companion object { const val KEEP = 10 }
}
