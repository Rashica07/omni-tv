package com.rasha.omni.library

import com.rasha.omni.common.readUpTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A place media lives. Scanning, reading side files and playing never need to know which kind it is. */
interface MediaSource : Lister {
    suspend fun read(path: String, maxBytes: Int = 200_000): ByteArray
    /** What the player opens. */
    fun playUrl(path: String): String
    /** Extra request headers the player must send (the OMNI server's PIN); empty for other sources. */
    fun headers(): Map<String, String>
}

/** The OMNI server on the LAN (omniptvd /media). Plain http on a private network, PIN as bearer token; the PIN never goes into a URL or a log. */
class LanSource(private val base: String, private val pin: String) : MediaSource {
    private fun enc(path: String) = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
    override fun headers() = mapOf("Authorization" to "Bearer $pin")
    override fun playUrl(path: String) = if (ApiPath.isApi(path)) ServerApi(base, "").streamUrl(ApiPath.id(path)) else "$base/media/" + enc(path)

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 4000; c.readTimeout = 10000; c.instanceFollowRedirects = false
        headers().forEach { (k, v) -> c.setRequestProperty(k, v) }
        return c
    }

    override suspend fun list(dir: String): List<Item> = withContext(Dispatchers.IO) {
        val c = open(playUrl(dir).let { if (it.endsWith("/")) it else "$it/" })
        try {
            check(c.responseCode == 200) { when (c.responseCode) { 401 -> "wrong PIN"; 404 -> "not found"; else -> "http ${c.responseCode}" } }
            val items = JSONObject(c.inputStream.readBytes().toString(Charsets.UTF_8)).getJSONArray("items")
            (0 until items.length()).map { items.getJSONObject(it) }.map { Item(it.getString("name"), it.getBoolean("dir"), it.optLong("size"), it.optLong("mtime")) }
        } finally { c.disconnect() }
    }

    override suspend fun read(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val c = open(playUrl(path))
        try { check(c.responseCode == 200) { "http ${c.responseCode}" }; c.inputStream.use { it.readUpTo(maxBytes) } } finally { c.disconnect() }
    }

    /** A real round trip, so the Sources screen can say "connected" or why not, instead of guessing. */
    suspend fun check(): String? = try { list(""); null } catch (e: Exception) { e.message ?: e.javaClass.simpleName }
}

/** A folder on this TV or a USB drive (/storage/<id>/...). Needs the storage permission on this Android version. */
class LocalSource(private val root: File) : MediaSource {
    override fun headers() = emptyMap<String, String>()
    override fun playUrl(path: String) = File(root, path).toURI().toString()
    private fun safe(path: String): File { val f = File(root, path).canonicalFile; require(f.path == root.canonicalPath || f.path.startsWith(root.canonicalPath + File.separator)) { "outside source" }; return f }
    override suspend fun list(dir: String): List<Item> = withContext(Dispatchers.IO) {
        val d = safe(dir)
        (d.listFiles() ?: error("cannot read folder (permission?)")).map { Item(it.name, it.isDirectory, if (it.isFile) it.length() else 0, it.lastModified() / 1000) }
    }
    override suspend fun read(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) { safe(path).inputStream().use { it.readUpTo(maxBytes) } }
}

/** Internet Archive items added by identifier. They are files in the library like any other; their "path" is "<identifier>/<file>". */
class ArchiveSource : MediaSource {
    override fun headers() = emptyMap<String, String>()
    override fun playUrl(path: String) = Archive.fileUrl(path.substringBefore('/'), path.substringAfter('/'))
    override suspend fun list(dir: String): List<Item> = emptyList()
    override suspend fun read(path: String, maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        val c = URL(playUrl(path)).openConnection() as HttpURLConnection
        c.connectTimeout = 6000; c.readTimeout = 10000
        try { check(c.responseCode == 200) { "http ${c.responseCode}" }; c.inputStream.use { it.readUpTo(maxBytes) } } finally { c.disconnect() }
    }
}
