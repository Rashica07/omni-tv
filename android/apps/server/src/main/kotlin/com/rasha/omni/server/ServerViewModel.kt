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

data class Sample(val atMs: Long, val load: Double, val tempC: Double?, val memUsed: Double, val rxBps: Long? = null, val txBps: Long? = null)

data class ServerUi(
    val paired: Boolean = false, val address: String = "", val status: SysStatus? = null, val health: Health? = null, val alerts: List<Alert> = emptyList(),
    val history: List<Sample> = emptyList(), val lastOkMs: Long = 0, val error: String? = null, val latencyMs: Long = 0,
    val events: List<Event> = emptyList(), val reachable: Boolean = true,
    /** Library and devices come from a different part of the server than /status: each can fail or be missing on its own, and says so. */
    val library: LibraryInfo? = null, val libraryNote: String? = null, val libraryAtMs: Long = 0,
    val devices: List<DeviceSeen>? = null, val scanMessage: String? = null,
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
    private var round = 0

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
            while (isActive) {
                refresh()
                if (round++ % 3 == 0 || _ui.value.library?.scan?.scanning == true) refreshLibrary()          // 15 s normally, 5 s while a scan runs
                delay(5000)
            }
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
            val hist = (_ui.value.history + Sample(now, s.load1, s.tempC, s.memUsedFraction, s.net?.rxBps, s.net?.txBps)).takeLast(120)
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

    /** The library and the devices that have synced, each failing on its own: a server without the library API is still a working server. */
    suspend fun refreshLibrary() {
        val addr = prefs.getString("addr", null) ?: return; val pin = prefs.getString("pin", null) ?: return
        val c = ServerClient("http://$addr", pin)
        val now = System.currentTimeMillis()
        try { _ui.value = _ui.value.copy(library = c.library(), libraryNote = null, libraryAtMs = now) }
        catch (e: NoLibraryApi) { _ui.value = _ui.value.copy(library = null, libraryNote = "This server is older and has no media library. Update it with \"sudo ./deploy/install.sh\".") }
        catch (e: Unauthorized) { _ui.value = _ui.value.copy(libraryNote = "The server rejected the PIN.") }
        catch (e: Exception) { _ui.value = _ui.value.copy(libraryNote = if (_ui.value.library != null) "Could not refresh the library; showing the last reading." else "Could not read the library.") }
        try { _ui.value = _ui.value.copy(devices = c.devices()) } catch (e: Exception) { }
    }

    /** Starts a library scan on the server. The answer says whether one began or one was already running. */
    suspend fun scanNow() {
        val addr = prefs.getString("addr", null) ?: return; val pin = prefs.getString("pin", null) ?: return
        _ui.value = _ui.value.copy(scanMessage = "Asking the server\u2026")
        val msg = try { if (ServerClient("http://$addr", pin).scanNow()) "Scan started" else "A scan is already running" } catch (e: Exception) { "The server did not accept the request" }
        _ui.value = _ui.value.copy(scanMessage = msg)
        refreshLibrary()
    }

    /** Checks the PIN against the real server before saving it. Returns an error text or null. */
    suspend fun pair(addr: String, pin: String): String? {
        if (!Regex("""\d{8}""").matches(pin)) return "The PIN is 8 digits"
        return try { ServerClient("http://$addr", pin).status(); prefs.edit().putString("addr", addr).putString("pin", pin).apply(); _ui.value = ServerUi(paired = true, address = addr); start(); null }
        catch (e: Unauthorized) { "Wrong PIN" } catch (e: TooOld) { "This server is running an older OMNI server version without a status page. Run \"sudo ./deploy/install.sh\" on the server to update it." } catch (e: Exception) { "Could not connect: ${e.javaClass.simpleName}" }
    }

    fun unpair() { stop(); prefs.edit().clear().apply(); prevSnap = null; _ui.value = ServerUi() }
}
