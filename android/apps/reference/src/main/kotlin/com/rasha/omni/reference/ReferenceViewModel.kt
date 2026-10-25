package com.rasha.omni.reference

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rasha.omni.common.UiState
import com.rasha.omni.network.HttpJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Fact(val title: String, val value: String, val sub: String = "")

/** Real facts about the device this app runs on, read on a worker thread. Nothing here is invented. */
class ReferenceViewModel(app: Application) : AndroidViewModel(app) {
    private val http = HttpJson(java.io.File(app.cacheDir, "http"), "OMNI-Reference/1.0")
    private val _facts = MutableStateFlow<UiState<List<Fact>>>(UiState.Loading)
    val facts: StateFlow<UiState<List<Fact>>> = _facts.asStateFlow()
    private val _network = MutableStateFlow<UiState<Fact>>(UiState.Empty("Not checked yet"))
    val network: StateFlow<UiState<Fact>> = _network.asStateFlow()

    init { loadFacts() }

    fun loadFacts() {
        viewModelScope.launch {
            _facts.value = UiState.Loading
            _facts.value = withContext(Dispatchers.IO) { UiState.Content(readFacts(), System.currentTimeMillis()) }
        }
    }

    private fun readFacts(): List<Fact> {
        val ctx = getApplication<Application>()
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val dm = ctx.resources.displayMetrics
        val startMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        return listOf(
            Fact("Device", Build.MODEL, Build.MANUFACTURER),
            Fact("Android", "API ${Build.VERSION.SDK_INT}", "release ${Build.VERSION.RELEASE}"),
            Fact("CPU", Build.SUPPORTED_ABIS.first(), "${Runtime.getRuntime().availableProcessors()} cores"),
            Fact("Screen", "${dm.widthPixels}×${dm.heightPixels}", "${dm.densityDpi} dpi, ${"%.2f".format(dm.density)}×"),
            Fact("Memory", "${mi.totalMem / 1048576} MB", "${mi.availMem / 1048576} MB free" + if (am.isLowRamDevice) ", low-RAM device" else ""),
            Fact("This process", "${startMs} ms", "since the process started"),
        )
    }

    /** A real request through the shared client: latency, or the honest failure. */
    fun checkNetwork() {
        viewModelScope.launch {
            _network.value = UiState.Loading
            val t0 = SystemClock.elapsedRealtime()
            _network.value = try {
                val r = http.get("https://api.open-meteo.com/v1/forecast?latitude=45.83&longitude=9.34&current=temperature_2m", maxAgeMs = 0, force = true)
                val ms = SystemClock.elapsedRealtime() - t0
                UiState.Content(Fact("Network", if (r.fromCache) "Offline, using cache" else "$ms ms", if (r.fromCache) "request failed: ${r.error}" else "api.open-meteo.com reachable"), r.fetchedAtMs)
            } catch (e: Exception) {
                UiState.Error("No answer from the internet (${e.javaClass.simpleName})")
            }
        }
    }
}
