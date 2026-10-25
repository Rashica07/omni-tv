package com.rasha.omni.server

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniTheme
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.PinPad
import com.rasha.omni.library.Discovery
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class ServerActivity : ComponentActivity() {
    private val vm: ServerViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { OmniTheme { Root(vm) } }; debugPair(intent) }
    override fun onNewIntent(intent: android.content.Intent) { super.onNewIntent(intent); debugPair(intent) }

    /** Developer setup only (`setprop log.tag.OmniDebugOpen DEBUG`): pair from adb instead of the number pad. Ignored in normal use. */
    private fun debugPair(i: android.content.Intent) {
        if (!android.util.Log.isLoggable("OmniDebugOpen", android.util.Log.DEBUG)) return
        val a = i.getStringExtra("pair") ?: return; val p = i.getStringExtra("pin") ?: return
        lifecycleScope.launch { vm.pair(a, p) }
    }
    override fun onStart() { super.onStart(); vm.start() }
    override fun onStop() { super.onStop(); vm.stop() }
}

@Composable
private fun Root(vm: ServerViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    if (!ui.paired) Pairing(vm) else Dashboard(vm, ui)
}

@Composable
private fun Pairing(vm: ServerViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<String>?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { found = Discovery.findServers(ctx); if (found!!.size == 1) chosen = found!![0] }
    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("OMNI Server", style = OmniType.Heading.copy(fontSize = 26.sp))
            val f = found
            if (f == null) Text("Looking for your server on the network…", style = OmniType.Body.copy(color = OmniColor.Text2))
            else if (f.isEmpty()) Text("No OMNI server answered. Is omniptvd running on the laptop?", style = OmniType.Body.copy(color = OmniColor.Warning))
            else if (chosen == null) f.forEach { a -> OmniCard(onClick = { chosen = a }, modifier = Modifier.width(320.dp)) { Text(a, style = OmniType.Body.copy(fontSize = 18.sp)) } }
            else {
                Text("Server $chosen. Enter its 8-digit PIN (the file /etc/omniptv/read.pin on the server).", style = OmniType.Label.copy(color = OmniColor.Text2))
                PinPad(pin) { pin = it; status = null }
                OmniCard(onClick = { status = "Connecting…"; scope.launch { status = vm.pair(chosen!!, pin) } }, modifier = Modifier.width(260.dp)) { Text("Connect", style = OmniType.Body.copy(fontSize = 18.sp)) }
            }
            status?.let { Text(it, style = OmniType.Label.copy(color = OmniColor.Warning)) }
        }
    }
}
