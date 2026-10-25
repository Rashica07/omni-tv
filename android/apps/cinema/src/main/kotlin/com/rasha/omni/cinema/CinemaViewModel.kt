package com.rasha.omni.cinema

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rasha.omni.library.Entry
import com.rasha.omni.library.ShowRow
import com.rasha.omni.library.SourceKind
import com.rasha.omni.library.SourceRow
import com.rasha.omni.library.MediaId
import com.rasha.omni.library.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    object Home : Screen()
    data class Title(val id: String, val notice: String? = null) : Screen()
    data class Show(val id: Long) : Screen()
    object Sources : Screen()
    object Library : Screen()
    object AddServer : Screen()
    object AddArchive : Screen()
    object AddLocal : Screen()
}

data class CinemaUi(val movies: List<Entry> = emptyList(), val shows: List<ShowRow> = emptyList(), val cont: List<Entry> = emptyList(), val recent: List<Entry> = emptyList(),
                    val sources: List<SourceRow> = emptyList(), val loaded: Boolean = false, val kinds: Map<Long, SourceKind> = emptyMap(),
                    val titles: List<Title> = emptyList(), val shelves: List<com.rasha.omni.library.Shelf> = emptyList(), val offlineHint: Boolean = false)

class CinemaViewModel(app: Application) : AndroidViewModel(app) {
    val lib = (app as CinemaApp).lib
    private val _ui = MutableStateFlow(CinemaUi())
    val ui: StateFlow<CinemaUi> = _ui.asStateFlow()
    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()
    private val _msg = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _msg.asStateFlow()
    /** Which poster had focus on Home, so coming back from a detail screen lands on it again. */
    var lastFocus: String? = null
    private val back = ArrayDeque<Screen>()

    fun go(s: Screen) { back.addLast(_screen.value); _screen.value = s }
    fun back(): Boolean { val p = back.removeLastOrNull() ?: return false; _screen.value = p; reload(); return true }
    fun say(m: String?) { _msg.value = m }

    fun reload() {
        viewModelScope.launch {
            _ui.value = withContext(Dispatchers.IO) { val d = lib.db; val src = d.sources(); val t = lib.titles(); CinemaUi(d.movies(), d.shows(), d.continueWatching(), d.recentlyAdded(), src, true, src.associate { it.id to it.kind }, t, com.rasha.omni.library.Catalogue.shelves(t), t.any { it.availability != com.rasha.omni.library.Availability.PLAYABLE }) }
        }
    }

    /** Where Cinema starts: from the launcher (a route in the intent) or Home. The route becomes the root, so Back leaves Cinema. */
    fun start(route: String?, id: String?, notice: String?) {
        back.clear()
        _screen.value = when (route) { "title" -> Screen.Title(id.orEmpty(), notice); "sources" -> Screen.Sources; else -> Screen.Home }
        reload()
    }

    suspend fun title(id: String): Title? = withContext(Dispatchers.IO) { val m = MediaId.parse(id) ?: return@withContext null; lib.titles().firstOrNull { it.id == m } }

    fun idOf(e: Entry): String = MediaId.of(e, _ui.value.kinds[e.sourceId] ?: SourceKind.Local).text

    /** Plays the best reachable copy of a title; if none is reachable the reason is shown, never a blank player. */
    fun playTitle(t: Title, play: (Entry) -> Unit) {
        viewModelScope.launch {
            val copy = withContext(Dispatchers.IO) { val av = lib.availabilities(); t.copies.firstOrNull { av[it.sourceId] == com.rasha.omni.library.Availability.PLAYABLE } }
            if (copy == null) say(com.rasha.omni.cinema.Router.reason(t.copy(availability = com.rasha.omni.library.Availability.OFFLINE)).ifEmpty { "This title cannot be played right now." }) else play(copy)
        }
    }

    fun scan(row: SourceRow) { viewModelScope.launch { lib.scan(row); reload() } }
    /** Scans every source that can be scanned, reading the live list (a source added a moment ago is not in the screen's copy yet). */
    fun scanAll() { viewModelScope.launch { withContext(Dispatchers.IO) { lib.db.sources() }.filter { it.kind != SourceKind.Archive }.forEach { launch { lib.scan(it); reload() } } } }

    fun addServer(name: String, host: String, pin: String, done: (String?) -> Unit) { viewModelScope.launch { val e = lib.addLan(name, host, pin); if (e == null) reload(); done(e) } }
    fun addLocal(name: String, path: String, done: (String?) -> Unit) { viewModelScope.launch { val e = lib.addLocal(name, path); if (e == null) reload(); done(e) } }
    fun addArchive(id: String, done: (String?) -> Unit) { viewModelScope.launch { val e = lib.addArchive(id); if (e == null) reload(); done(e) } }
    fun remove(row: SourceRow) { viewModelScope.launch(Dispatchers.IO) { lib.remove(row); reload() } }
    fun setWatched(e: Entry, w: Boolean, after: () -> Unit = {}) { viewModelScope.launch { withContext(Dispatchers.IO) { lib.db.setWatched(e.sourceId, e.path, w) }; reload(); after() } }
}
