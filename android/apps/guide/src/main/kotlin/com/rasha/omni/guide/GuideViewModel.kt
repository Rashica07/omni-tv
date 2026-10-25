package com.rasha.omni.guide

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rasha.omni.common.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GuideViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GuideRepository(app)
    private val _state = MutableStateFlow<UiState<GuideData>>(UiState.Loading)
    val state: StateFlow<UiState<GuideData>> = _state.asStateFlow()
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()
    private var last = 0L

    fun hasPermission() = repo.hasPermission()
    fun say(s: String) { _note.value = s }

    /** Reads on a worker thread; at most once every 30 s (the guide changes when the broadcast does, not by the second). */
    fun refresh() {
        if (!repo.hasPermission()) { _state.value = UiState.Empty("permission"); return }
        val now = System.currentTimeMillis()
        if (now - last < 30_000 && _state.value is UiState.Content) return
        last = now
        viewModelScope.launch {
            try {
                val d = withContext(Dispatchers.IO) { repo.load(now) }
                _state.value = if (d.channels.isEmpty()) UiState.Empty("This TV has no scanned antenna channels.") else UiState.Content(d, now)
            } catch (e: Exception) {
                val cur = _state.value
                _state.value = if (cur is UiState.Content) cur.copy(lastError = e.javaClass.simpleName) else UiState.Error("The TV's guide could not be read.")
            }
        }
    }
}
