package com.rasha.omni.server

import com.rasha.omni.common.readUpTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class Unauthorized : Exception("wrong PIN")
class TooOld : Exception("older server")

/** Read-only client for omniptvd. The PIN goes in a header only, never in a URL, and nothing here can change anything on the server. */
class ServerClient(private val base: String, private val pin: String) {
    private suspend fun get(path: String, auth: Boolean): String = withContext(Dispatchers.IO) {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.connectTimeout = 2500; c.readTimeout = 4000; c.instanceFollowRedirects = false
        if (auth) c.setRequestProperty("Authorization", "Bearer $pin")
        try {
            if (c.responseCode == 401) throw Unauthorized()
            if (c.responseCode == 404 && auth) throw TooOld()          // an older omniptvd has no /status
            check(c.responseCode == 200) { "http ${c.responseCode}" }
            c.inputStream.readUpTo(200_000).toString(Charsets.UTF_8)
        } finally { c.disconnect() }
    }
    suspend fun health() = Parse.health(get("/health", false))
    suspend fun status() = Parse.status(get("/status", true))
}
