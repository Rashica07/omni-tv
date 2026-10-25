package com.rasha.omni.control

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType

private val Sections = listOf("Inputs", "Network", "Audio", "Display", "Actions", "About")
private val Presets = listOf("PlayStation", "Xbox", "PC", "Streaming box", "Set-top box", "Sound system", "Other")

@Composable
fun ControlScreen(vm: ControlViewModel, startIntent: (Intent) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var section by remember { mutableIntStateOf(0) }
    var labeling by remember { mutableStateOf<InputItem?>(null) }
    OmniScreen {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(210.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OMNI Control", style = OmniType.Heading)
                Spacer(Modifier.height(10.dp))
                Sections.forEachIndexed { i, name -> NavItem(name, selected = i == section, onFocus = { section = i }) }
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                if (!ui.loaded) Text("Reading this TV…", style = OmniType.Body.copy(color = OmniColor.Text2))
                else when (section) {
                    0 -> InputsPane(ui.inputs, onSwitch = { vm.switchTo(it, startIntent) }, onLabel = { labeling = it })
                    1 -> NetworkPane(ui.network)
                    2 -> ListPane(ui.audio.map { Triple(it.name, it.kind, it.detail) }, "No audio outputs reported")
                    3 -> DisplayPane(ui.display)
                    4 -> ActionsPane(ui.caps, onOpen = { vm.open(it, startIntent) })
                    else -> ListPane(ui.about.map { Triple(it.first, it.second, "") }, "")
                }
                message?.let { m -> Text(m, Modifier.align(Alignment.BottomStart).padding(bottom = 4.dp).clickable { vm.clearMessage() }, style = OmniType.Label.copy(fontSize = 14.sp, color = OmniColor.Warning)) }
                labeling?.let { item -> LabelPicker(item, onPick = { vm.setLabel(item.id, it); labeling = null }, onClear = { vm.setLabel(item.id, null); labeling = null }) }
            }
        }
    }
}

@Composable
private fun NavItem(name: String, selected: Boolean, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(
        name,
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.focusable()
            .background(if (focused) Color.White else if (selected) Color(0x26FFFFFF) else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
        style = OmniType.Body.copy(fontSize = 19.sp, color = if (focused) Color.Black else Color.White),
    )
}

@Composable
private fun InputsPane(inputs: List<InputItem>, onSwitch: (InputItem) -> Unit, onLabel: (InputItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("INPUTS", style = OmniType.Caption)
        Text("Names and states come from the TV. A name you set is marked as yours; it never changes what the TV reports. OK asks the TV to show an input, hold OK to set a name.", style = OmniType.Label.copy(color = OmniColor.Text2))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) { items(inputs, key = { it.id }) { InputRow(it, onSwitch, onLabel) } }
    }
}

@Composable
private fun InputRow(i: InputItem, onSwitch: (InputItem) -> Unit, onLabel: (InputItem) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var longPressed by remember { mutableStateOf(false) }
    val dim = i.state == android.media.tv.TvInputManager.INPUT_STATE_DISCONNECTED
    Row(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
            .onKeyEvent { e ->
                if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 && !longPressed) { longPressed = true; onLabel(i) }
                    if (e.type == KeyEventType.KeyUp) { if (!longPressed) onSwitch(i); longPressed = false }
                    true
                } else false
            }
            .background(if (focused) Color.White else Color(0xFF1C1C1C), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (focused) Color.Black else Color.White
        Column(Modifier.weight(1f)) {
            Text(i.title, style = OmniType.Heading.copy(fontSize = 21.sp, color = if (dim && !focused) OmniColor.Text2 else fg))
            val how = buildString { if (i.tvName != null) append("  ·  TV reports: ${i.tvName}"); if (i.customLabel != null) append("  ·  set by you") }
            Text(i.stateText + how, style = OmniType.Label.copy(fontSize = 14.sp, color = if (focused) Color(0xFF333333) else OmniColor.Text2))
        }
        Text(if (i.hdmi) "HDMI" else if (i.type == android.media.tv.TvInputInfo.TYPE_COMPONENT) "Component" else "Input", style = OmniType.Caption.copy(color = if (focused) Color(0xFF333333) else OmniColor.Text2))
    }
}

@Composable
private fun LabelPicker(item: InputItem, onPick: (String) -> Unit, onClear: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(360.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("WHAT IS PLUGGED INTO ${item.port.uppercase()}?", style = OmniType.Caption)
                (Presets + "Clear my name").forEach { p ->
                    var f by remember { mutableStateOf(false) }
                    Text(p, Modifier.fillMaxWidth().onFocusChanged { f = it.isFocused }.focusable().clickable { if (p == "Clear my name") onClear() else onPick(p) }
                        .background(if (f) Color.White else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 8.dp), style = OmniType.Body.copy(fontSize = 17.sp, color = if (f) Color.Black else Color.White))
                }
            }
        }
    }
}

@Composable
private fun NetworkPane(n: NetworkInfoUi?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NETWORK", style = OmniType.Caption)
        if (n == null) { Text("Unavailable", style = OmniType.Body); return }
        Fact("Connection", n.transport + if (n.connected) "" else "")
        Fact("Internet", if (!n.connected) "No network" else if (n.internet) "Reachable (verified by the system)" else "Connected, but the system could not confirm internet access")
        Fact("Address", n.addresses.joinToString(", ").ifEmpty { "none" })
        if (n.downKbps > 0) Fact("Link speed (reported)", "${n.downKbps / 1000} Mbit/s down, ${n.upKbps / 1000} Mbit/s up")
        Text("Signal strength and the network name are not shown: they need a location permission this app does not ask for.", style = OmniType.Label.copy(color = OmniColor.Text3))
    }
}

@Composable
private fun DisplayPane(d: DisplayInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("DISPLAY", style = OmniType.Caption)
        if (d == null) return
        Fact("Current mode", "${d.current}  ·  ${d.refresh}")
        Fact("HDR", if (d.hdr.isEmpty()) "Not supported" else d.hdr.joinToString(", "))
        Fact("Modes offered", d.modes.joinToString("   "))
    }
}

@Composable
private fun ListPane(rows: List<Triple<String, String, String>>, empty: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (rows.isEmpty()) Text(empty, style = OmniType.Body.copy(color = OmniColor.Text2))
        rows.forEach { (a, b, c) -> Fact(a, listOf(b, c).filter { it.isNotEmpty() }.joinToString("  ·  ")) }
    }
}

@Composable
private fun ActionsPane(caps: List<Capability>, onOpen: (Intent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("WHAT THIS TV ALLOWS", style = OmniType.Caption)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(caps, key = { it.title }) { c ->
                var f by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().onFocusChanged { f = it.isFocused }.focusable().then(if (c.action != null) Modifier.clickable { onOpen(c.action) } else Modifier)
                        .background(if (f) Color(0x33FFFFFF) else Color(0xFF161616), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (c.available) "✓" else "–", style = OmniType.Heading.copy(color = if (c.available) OmniColor.Positive else OmniColor.Text3))
                    Column(Modifier.weight(1f)) {
                        Text(c.title, style = OmniType.Body.copy(fontSize = 18.sp))
                        Text(c.note, style = OmniType.Label.copy(color = OmniColor.Text2))
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(k: String, v: String) {
    GlassPanel(Modifier.fillMaxWidth(), radius = 14.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(k.uppercase(), style = OmniType.Caption)
            Text(v, style = OmniType.Body.copy(fontSize = 19.sp))
        }
    }
}
