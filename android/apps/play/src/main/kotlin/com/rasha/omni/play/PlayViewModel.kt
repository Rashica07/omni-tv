package com.rasha.omni.play

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayUi(
    val games: List<GameItem> = emptyList(), val recent: List<GameItem> = emptyList(), val favorites: List<GameItem> = emptyList(),
    val clients: List<GameItem> = emptyList(), val missing: List<String> = emptyList(), val controllers: List<Controller> = emptyList(),
    val hdmi: List<ConsoleInput> = emptyList(), val controlInstalled: Boolean = false, val loaded: Boolean = false,
)

class PlayViewModel(app: Application) : AndroidViewModel(app) {
    val data = PlayData(app)
    private val _ui = MutableStateFlow(PlayUi())
    val ui: StateFlow<PlayUi> = _ui.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            delay(250)                                           // several change callbacks in a burst become one read
            _ui.value = withContext(Dispatchers.IO) {
                val games = data.games()
                val clients = data.streamingClients()
                val all = games + clients
                val fav = data.favorites()
                PlayUi(
                    games = games, clients = clients, missing = data.missingClients(), controllers = data.controllers(), hdmi = data.hdmi(),
                    favorites = all.filter { it.pkg in fav }, recent = all.filter { data.lastPlayed(it.pkg) > 0 }.sortedByDescending { data.lastPlayed(it.pkg) }.take(10),
                    controlInstalled = data.launch("com.rasha.omni.control") != null, loaded = true,
                )
            }
        }
    }

    /** Artwork is drawn off the main thread and cached; null until ready. */
    suspend fun artwork(pkg: String): Bitmap? = withContext(Dispatchers.IO) { try { data.artwork(pkg) } catch (e: Exception) { null } }

    fun say(m: String?) { _message.value = m }
}
