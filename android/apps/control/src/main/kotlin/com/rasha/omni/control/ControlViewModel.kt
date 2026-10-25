package com.rasha.omni.control

import android.app.Application
import android.content.Context
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

data class ControlUi(
    val inputs: List<InputItem> = emptyList(), val network: NetworkInfoUi? = null, val audio: List<AudioOut> = emptyList(),
    val display: DisplayInfo? = null, val caps: List<Capability> = emptyList(), val about: List<Pair<String, String>> = emptyList(), val loaded: Boolean = false,
)

/** Reads the platform on a worker thread and republishes when the platform says something changed (callbacks), debounced so a burst is one refresh. */
class ControlViewModel(app: Application) : AndroidViewModel(app) {
    private val reader = DeviceReader(app)
    private val prefs = app.getSharedPreferences("control", Context.MODE_PRIVATE)
    private val _ui = MutableStateFlow(ControlUi())
    val ui: StateFlow<ControlUi> = _ui.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private var pending: Job? = null

    init { refresh() }

    fun refresh() {
        pending?.cancel()
        pending = viewModelScope.launch {
            delay(300)                                   // debounce: several callbacks in a burst become one read
            val next = withContext(Dispatchers.IO) {
                ControlUi(
                    inputs = reader.inputs().map { it.copy(customLabel = prefs.getString("label_${it.id}", null)) }, network = reader.network(), audio = reader.audio(),
                    display = reader.display(), caps = reader.capabilities(), about = reader.summary(), loaded = true,
                )
            }
            _ui.value = next
        }
    }

    fun setLabel(inputId: String, label: String?) {
        prefs.edit().apply { if (label == null) remove("label_$inputId") else putString("label_$inputId", label) }.apply()
        refresh()
    }

    /** Asks the system to show the input. Never reports success it cannot know: the platform gives no result for this request. */
    fun switchTo(item: InputItem, start: (android.content.Intent) -> Unit) {
        try {
            start(reader.switchIntent(item.id))
            _message.value = "Asked the TV to show ${item.title}. If nothing changes, use the Input button on your remote."
        } catch (e: Exception) {
            _message.value = "This TV did not accept the request. Use the Input button on your remote."
        }
    }

    fun open(intent: android.content.Intent, start: (android.content.Intent) -> Unit) {
        try { start(intent) } catch (e: Exception) { _message.value = "That settings page could not be opened." }
    }

    fun clearMessage() { _message.value = null }
}
