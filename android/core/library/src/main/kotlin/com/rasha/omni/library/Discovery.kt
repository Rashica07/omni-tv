package com.rasha.omni.library

import com.rasha.omni.common.readUpTo
import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

/** Finds OMNI servers on the TV's own /24 by asking each address for /health (which reveals nothing sensitive). Nothing leaves the local network. */
object Discovery {
    suspend fun findServers(ctx: Context, ports: List<Int> = listOf(8765, 8766)): List<String> = coroutineScope {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mine = cm.getLinkProperties(cm.activeNetwork)?.linkAddresses?.map { it.address }?.filterIsInstance<Inet4Address>()?.firstOrNull() ?: return@coroutineScope emptyList()
        val b = mine.address
        val gate = Semaphore(48)
        (1..254).flatMap { n -> ports.map { port -> n to port } }.map { (n, port) ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val ip = "${b[0].toInt() and 255}.${b[1].toInt() and 255}.${b[2].toInt() and 255}.$n"
                    if (ip == mine.hostAddress) return@withPermit null
                    try {
                        val c = URL("http://$ip:$port/health").openConnection() as HttpURLConnection
                        c.connectTimeout = 400; c.readTimeout = 800
                        try { if (c.responseCode == 200 && JSONObject(c.inputStream.readUpTo(4000).toString(Charsets.UTF_8)).optString("service") == "omniptvd") "$ip:$port" else null } finally { c.disconnect() }
                    } catch (e: Exception) { null }
                }
            }
        }.awaitAll().filterNotNull()
    }
}
