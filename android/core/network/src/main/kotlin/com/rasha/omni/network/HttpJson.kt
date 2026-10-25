package com.rasha.omni.network

import com.rasha.omni.common.AppDispatchers
import com.rasha.omni.common.DefaultDispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A response body with where it came from, so screens can show freshness honestly. */
data class Fetched(val body: String, val fetchedAtMs: Long, val fromCache: Boolean, val error: String? = null)

/**
 * Small HTTP client with a disk cache. Every call runs on the IO dispatcher. A fresh cached copy (younger than [maxAgeMs]) is returned without
 * touching the network; when the network fails the last good copy is returned flagged as cached with the reason, never silently. Only https (and
 * http on the LAN when allowed) is accepted, redirects are not followed across schemes, and bodies are size-limited.
 */
class HttpJson(
    private val cacheDir: File,
    private val userAgent: String,
    private val dispatchers: AppDispatchers = DefaultDispatchers,
    private val maxBytes: Int = 1_000_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init { cacheDir.mkdirs() }

    suspend fun get(url: String, maxAgeMs: Long, force: Boolean = false): Fetched = withContext(dispatchers.io) {
        require(url.startsWith("https://")) { "https only" }
        val file = File(cacheDir, hash(url) + ".json")
        val now = clock()
        val cached = readCache(file)
        if (cached != null && !force && now - cached.second < maxAgeMs) return@withContext Fetched(cached.first, cached.second, true)
        try {
            val body = download(url)
            file.writeText("$now\n$body")
            Fetched(body, now, false)
        } catch (e: Exception) {
            if (cached != null) Fetched(cached.first, cached.second, true, e.javaClass.simpleName) else throw e
        }
    }

    /** The cached copy of a URL with its age, without any network request (for showing something instantly while a refresh runs). */
    suspend fun peek(url: String): Fetched? = withContext(dispatchers.io) {
        readCache(File(cacheDir, hash(url) + ".json"))?.let { Fetched(it.first, it.second, true) }
    }

    private fun readCache(f: File): Pair<String, Long>? = try {
        if (!f.exists()) null else {
            val text = f.readText()
            val nl = text.indexOf('\n')
            text.substring(nl + 1) to text.substring(0, nl).toLong()
        }
    } catch (e: Exception) { null }

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 8000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", userAgent)
        try {
            check(c.responseCode == 200) { "http ${c.responseCode}" }
            c.inputStream.use { ins ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    check(out.size() <= maxBytes) { "response too large" }
                }
                return out.toString("UTF-8")
            }
        } finally { c.disconnect() }
    }

    private fun hash(s: String): String = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
