package com.rasha.omni.server

import com.rasha.omni.common.readUpTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class Unauthorized : Exception("wrong PIN")
class TooOld : Exception("older server")
class NoLibraryApi : Exception("no library API")

/** Client for omniptvd. The PIN goes in a header only, never in a URL or a log. It reads status, health, the library and watch progress, and can start a library scan: nothing else. */
class ServerClient(private val base: String, private val pin: String) {
    private suspend fun get(path: String, auth: Boolean, method: String = "GET"): String = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method; c.connectTimeout = 2500; c.readTimeout = 4000; c.instanceFollowRedirects = false
        if (auth) c.setRequestProperty("Authorization", "Bearer $pin")
        try {
            if (c.responseCode == 401) throw Unauthorized()
            if (c.responseCode == 404 && auth && path.startsWith("/api/")) throw NoLibraryApi()
            if (c.responseCode == 404 && auth) throw TooOld()          // an older omniptvd has no /status
            check(c.responseCode in 200..202) { "http ${c.responseCode}" }
            c.inputStream.readUpTo(200_000).toString(Charsets.UTF_8)
        } finally { c.disconnect() }
    }
    suspend fun health() = Parse.health(get("/health", false))
    suspend fun status() = Parse.status(get("/status", true))
    suspend fun library() = Parse.library(get("/api/v1/library", true))
    suspend fun devices() = Parse.devices(get("/api/v1/progress", true))
    /** The one operation the server offers a client: start a library scan (it runs on the server; a running scan is not restarted). Returns whether a new scan started. */
    suspend fun scanNow(): Boolean = Parse.scanStarted(get("/api/v1/library/scan", true, "POST"))
}
