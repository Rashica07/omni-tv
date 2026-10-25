package com.rasha.omni.weather.app

import android.app.Application
import android.content.Context
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rasha.omni.common.UiState
import com.rasha.omni.network.HttpJson
import com.rasha.omni.weather.Loaded
import com.rasha.omni.weather.Place
import com.rasha.omni.weather.Places
import com.rasha.omni.weather.UiMapper
import com.rasha.omni.weather.WeatherRepository
import com.rasha.omni.weather.WeatherUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Opens instantly on the stored forecast, then refreshes in the background. Network, disk and the mapping to ready-made strings all run off the
 * main thread; the screen only ever receives a finished [WeatherUi]. A failed refresh keeps the last good data and says so. Refreshes are
 * debounced (once a minute at most, the repository also serves anything under 30 minutes old from disk) and there is no polling.
 */
class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val repo = WeatherRepository(HttpJson(File(app.cacheDir, "wx"), "OMNI-Weather/1.0 (Android TV)"))
    private val _state = MutableStateFlow<UiState<WeatherUi>>(UiState.Loading)
    val state: StateFlow<UiState<WeatherUi>> = _state.asStateFlow()
    private val _place = MutableStateFlow(Places.firstOrNull { it.name == prefs.getString("place", "Civate") } ?: Places.first())
    val place: StateFlow<Place> = _place.asStateFlow()
    private var job: Job? = null
    private var lastAttempt = 0L

    init { start() }

    private fun start() {
        job?.cancel()
        job = viewModelScope.launch {
            val p = _place.value
            val cached = repo.cached(p)
            if (cached != null) _state.value = content(cached, p, refreshing = true)
            if (cached == null) _state.value = UiState.Loading
            fetch(p, force = false, hadData = cached != null)
        }
    }

    /** Refresh on request or on resume. Ignored if one ran in the last minute. */
    fun refresh(force: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastAttempt < 60_000 || job?.isActive == true) return
        lastAttempt = now
        val p = _place.value
        job = viewModelScope.launch {
            val cur = _state.value
            if (cur is UiState.Content) _state.value = cur.copy(refreshing = true)
            fetch(p, force, hadData = cur is UiState.Content)
        }
    }

    fun pick(p: Place) {
        if (p == _place.value) return
        prefs.edit().putString("place", p.name).apply()
        _place.value = p
        lastAttempt = System.currentTimeMillis()
        start()
    }

    private suspend fun fetch(p: Place, force: Boolean, hadData: Boolean) {
        try {
            val l = repo.load(p, force)
            _state.value = content(l, p, refreshing = false)
        } catch (e: Exception) {
            val cur = _state.value
            _state.value = if (cur is UiState.Content) cur.copy(refreshing = false, lastError = e.javaClass.simpleName)
            else UiState.Error("The weather service could not be reached and nothing is stored yet.")
        }
    }

    private suspend fun content(l: Loaded, p: Place, refreshing: Boolean): UiState<WeatherUi> = withContext(Dispatchers.Default) {
        val ctx = getApplication<Application>()
        val ui = UiMapper.map(l, p.name, Locale.getDefault(), DateFormat.is24HourFormat(ctx))
        UiState.Content(ui, l.fetchedAtMs, refreshing, l.refreshError)
    }
}
