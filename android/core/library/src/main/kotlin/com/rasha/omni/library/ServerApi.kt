package com.rasha.omni.library

import com.rasha.omni.common.readUpTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A film as the OMNI server's library describes it (/api/v1/movies). The server did the scanning, name parsing and TMDB matching; the TV only shows it. */
data class ApiMovie(
    val id: String, val title: String, val year: Int?, val overview: String, val genres: List<String>, val runtimeMin: Int, val rating: Double, val matched: Boolean,
    val tmdbId: Int, val tmdbPoster: String?, val tmdbBackdrop: String?, val durationMs: Long, val verdict: String, val added: Long,
)
data class ApiProgress(val movieId: String, val posMs: Long, val durMs: Long, val watched: Boolean, val updatedSec: Long)

/** Library "paths" of server-library films are "api:<movie id>"; every other path is a file path on a /media source. */
object ApiPath {
    const val PREFIX = "api:"
    fun of(id: String) = PREFIX + id
    fun isApi(path: String) = path.startsWith(PREFIX)
    fun id(path: String) = path.removePrefix(PREFIX)
}

object ApiJson {
    private fun JSONObject.str(k: String) = if (isNull(k)) null else optString(k).takeIf { it.isNotEmpty() }
    /** Returns (total, films). */
    fun movies(json: String): Pair<Int, List<ApiMovie>> {
        val o = JSONObject(json); val a = o.optJSONArray("items") ?: JSONArray()
        return o.optInt("total") to (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { m ->
            val g = m.optJSONArray("genres")
            ApiMovie(m.getString("id"), m.optString("title"), if (m.isNull("year")) null else m.optInt("year"), m.optString("overview"), if (g == null) emptyList() else (0 until g.length()).map { g.getString(it) },
                m.optInt("runtime_min"), m.optDouble("rating", 0.0), m.optBoolean("matched"), if (m.isNull("tmdb_id")) 0 else m.optInt("tmdb_id"), m.str("tmdb_poster"), m.str("tmdb_backdrop"),
                m.optLong("duration_ms"), m.optString("verdict", "unknown"), m.optLong("added"))
        }
    }
    fun progress(json: String): List<ApiProgress> {
        val a = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { ApiProgress(it.getString("movie_id"), it.optLong("pos_ms"), it.optLong("dur_ms"), it.optInt("watched") == 1, it.optLong("updated")) }
    }
    fun subtitles(json: String): List<Pair<String, String?>> {
        val a = JSONObject(json).optJSONArray("items") ?: return emptyList()
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { it.getString("url") to it.str("lang") }
    }
    fun progressBody(posMs: Long, durMs: Long, device: String) = JSONObject().put("position_ms", posMs).put("duration_ms", durMs).put("device", device).toString()
}

/** Which side's watch position wins when the TV and the server disagree: whichever was updated last. Server times are seconds, local times are milliseconds. */
object ProgressMerge {
    fun remoteWins(localLastMs: Long, remoteUpdatedSec: Long) = remoteUpdatedSec > 0 && remoteUpdatedSec * 1000 > localLastMs
}

/** Client for the OMNI server library API. Plain http on the LAN, PIN as a bearer header; the PIN never goes into a URL or a log. */
class ServerApi(private val base: String, private val pin: String) {
    private fun open(path: String, method: String = "GET"): HttpURLConnection {
        val c = URL("$base/api/v1$path").openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 4000; c.readTimeout = 15000; c.instanceFollowRedirects = false
        c.setRequestProperty("Authorization", "Bearer $pin")
        return c
    }
    private fun get(path: String): String { val c = open(path); try { check(c.responseCode == 200) { "http ${c.responseCode}" }; return c.inputStream.use { it.readUpTo(8_000_000) }.toString(Charsets.UTF_8) } finally { c.disconnect() } }

    /** True when the server runs the library API (older servers only have /media). */
    suspend fun available(): Boolean = withContext(Dispatchers.IO) {
        try { val c = URL("$base/api/v1/health").openConnection() as HttpURLConnection; c.connectTimeout = 2500; c.readTimeout = 3000; try { c.responseCode == 200 && JSONObject(c.inputStream.readUpTo(2000).toString(Charsets.UTF_8)).optInt("api") >= 1 } finally { c.disconnect() } } catch (e: Exception) { false }
    }
    suspend fun movies(): List<ApiMovie> = withContext(Dispatchers.IO) {
        val out = ArrayList<ApiMovie>(); var off = 0
        while (out.size < 20_000) { val (total, page) = ApiJson.movies(get("/movies?limit=200&offset=$off&sort=added")); out += page; off += 200; if (page.isEmpty() || out.size >= total) break }
        out
    }
    suspend fun progress(): List<ApiProgress> = withContext(Dispatchers.IO) { ApiJson.progress(get("/progress")) }
    suspend fun subtitles(movieId: String): List<Pair<String, String?>> = withContext(Dispatchers.IO) { ApiJson.subtitles(get("/movies/$movieId/subtitles")).map { (u, l) -> "$base$u" to l } }
    fun streamUrl(movieId: String) = "$base/api/v1/movies/$movieId/stream"
    /** Best effort: a missed save only means the next one (10 s later) carries the position. */
    fun putProgress(movieId: String, posMs: Long, durMs: Long, device: String) {
        try {
            val c = open("/progress/$movieId", "PUT"); c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            try { c.outputStream.use { it.write(ApiJson.progressBody(posMs, durMs, device).toByteArray()) }; c.responseCode } finally { c.disconnect() }
        } catch (e: Exception) { }
    }
}
