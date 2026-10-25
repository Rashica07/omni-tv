package com.rasha.omni.server

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Sample(val atMs: Long, val load: Double, val tempC: Double?, val memUsed: Double)

data class ServerUi(
    val paired: Boolean = false, val address: String = "", val status: SysStatus? = null, val health: Health? = null, val alerts: List<Alert> = emptyList(),
    val history: List<Sample> = emptyList(), val lastOkMs: Long = 0, val error: String? = null, val latencyMs: Long = 0,
    val events: List<Event> = emptyList(), val reachable: Boolean = true,
)

class ServerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("server", 0)
    private val _ui = MutableStateFlow(ServerUi(paired = prefs.contains("pin"), address = prefs.getString("addr", "") ?: "", events = loadEvents()))
    private var prevSnap: Snapshot? = null

    init { if (!prefs.contains("pin")) autoPair(app) }

    /** Cinema and this screen are one app: if Cinema already knows your OMNI server (address and PIN), use it instead of asking again. */
    private fun autoPair(app: Application) {
        try {
            val db = com.rasha.omni.library.LibraryDb(app)
            val row = try { db.sources().firstOrNull { it.kind == com.rasha.omni.library.SourceKind.Lan } } finally { db.close() } ?: return
            val pin = app.getSharedPreferences("cinema", 0).getString("pin_${row.id}", null) ?: return
            val addr = row.base.removePrefix("http://")
            prefs.edit().putString("addr", addr).putString("pin", pin).apply()
            _ui.value = _ui.value.copy(paired = true, address = addr)
        } catch (e: Exception) { }
    }
    val ui: StateFlow<ServerUi> = _ui.asStateFlow()
    private var loop: Job? = null

    private fun loadEvents(): List<Event> = (prefs.getString("events", "") ?: "").lines().mapNotNull { l -> val p = l.split('|', limit = 3); if (p.size == 3) p[0].toLongOrNull()?.let { Event(it, p[2], p[1] == "1") } else null }
    private fun record(new: List<Event>) {
        val last = _ui.value.events.firstOrNull()
        val fresh = new.filterNot { n -> last != null && last.text == n.text && n.atMs - last.atMs < 10 * 60_000 }       // the same news twice within ten minutes is one event
        if (fresh.isEmpty()) return
        val all = (fresh + _ui.value.events).take(30)
        prefs.edit().putString("events", all.joinToString("\n") { "${it.atMs}|${if (it.warn) 1 else 0}|${it.text.replace('\n', ' ').replace('|', '/')}" }).apply()
        _ui.value = _ui.value.copy(events = all)
    }

    /** Polls every 5 s while the screen is visible; stops when it is not (nothing runs in the background). */
    fun start() {
        stop()
        if (!_ui.value.paired) return
        loop = viewModelScope.launch {
            while (isActive) { refresh(); delay(5000) }
        }
    }
    fun stop() { loop?.cancel(); loop = null }

    suspend fun refresh() {
        val addr = prefs.getString("addr", null) ?: return; val pin = prefs.getString("pin", null) ?: return
        val c = ServerClient("http://$addr", pin)
        val t0 = System.currentTimeMillis()
        try {
            val h = c.health(); val s = c.status()
            val now = System.currentTimeMillis()
            val hist = (_ui.value.history + Sample(now, s.load1, s.tempC, s.memUsedFraction)).takeLast(60)
            val alerts = Alerts.from(s, h, now / 1000.0)
            _ui.value = _ui.value.copy(address = addr, status = s, health = h, alerts = alerts, history = hist, lastOkMs = now, error = null, latencyMs = now - t0, reachable = true)
            val snap = Snapshot(true, alerts.associate { it.key to it.text }); record(Events.diff(prevSnap, snap, now)); prevSnap = snap
        } catch (e: Unauthorized) {
            _ui.value = _ui.value.copy(error = "The server rejected the PIN. Pair again.")
        } catch (e: TooOld) {
            _ui.value = _ui.value.copy(error = "The server was downgraded or has no status page.")
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(error = "Cannot reach the server. Showing the last values.", reachable = false)
            val snap = Snapshot(false, emptyMap()); record(Events.diff(prevSnap, snap, System.currentTimeMillis())); prevSnap = snap
        }
    }

    /** Checks the PIN against the real server before saving it. Returns an error text or null. */
    suspend fun pair(addr: String, pin: String): String? {
        if (!Regex("""\d{8}""").matches(pin)) return "The PIN is 8 digits"
        return try { ServerClient("http://$addr", pin).status(); prefs.edit().putString("addr", addr).putString("pin", pin).apply(); _ui.value = ServerUi(paired = true, address = addr); start(); null }
        catch (e: Unauthorized) { "Wrong PIN" } catch (e: TooOld) { "This server is running an older OMNI server version without a status page. Run \"sudo ./deploy/install.sh\" on the server to update it." } catch (e: Exception) { "Could not connect: ${e.javaClass.simpleName}" }
    }

    fun unpair() { stop(); prefs.edit().clear().apply(); prevSnap = null; _ui.value = ServerUi() }
}
