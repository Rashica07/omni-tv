package com.rasha.omni.play

import android.content.Intent
import android.graphics.Bitmap
import android.media.tv.TvContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.omniFocus

@Composable
fun PlayScreen(vm: PlayViewModel, launch: (String) -> Unit, openIntent: (Intent) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    OmniScreen {
        Box(Modifier.fillMaxSize()) {
            if (!ui.loaded) Text("Looking for games…", style = OmniType.Body.copy(color = OmniColor.Text2))
            else LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("OMNI Play", style = OmniType.Heading.copy(fontSize = 26.sp)); Text("${ui.games.size} games, ${ui.clients.size} streaming clients, ${ui.controllers.size} controllers connected", style = OmniType.Label.copy(color = OmniColor.Text2)) }
                if (ui.favorites.isNotEmpty()) item { Shelf("FAVORITES", ui.favorites, vm, launch) }
                if (ui.recent.isNotEmpty()) item { Shelf("RECENTLY PLAYED FROM OMNI PLAY", ui.recent, vm, launch) }
                if (ui.games.isNotEmpty()) item { Shelf("GAMES", ui.games, vm, launch) }
                else item { Info("No games are installed on this TV. Games installed from the store will appear here.") }
                item {
                    if (ui.clients.isNotEmpty()) Shelf("GAME STREAMING", ui.clients, vm, launch)
                    else Info("No game-streaming client is installed.")
                    if (ui.missing.isNotEmpty()) Text("Not installed: ${ui.missing.joinToString(", ")}", Modifier.padding(top = 6.dp), style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
                }
                item { Controllers(ui.controllers) }
                if (ui.hdmi.isNotEmpty()) item { Consoles(ui.hdmi, ui.controlInstalled, vm, openIntent) }
            }
            message?.let { m -> Text(m, Modifier.align(Alignment.BottomStart).clickable { vm.say(null) }, style = OmniType.Label.copy(color = OmniColor.Warning)) }
        }
    }
}

@Composable
private fun Info(t: String) = Text(t, style = OmniType.Body.copy(fontSize = 16.sp, color = OmniColor.Text2))

@Composable
private fun Shelf(title: String, items: List<GameItem>, vm: PlayViewModel, launch: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = OmniType.Caption)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 6.dp)) {
            items(items, key = { it.pkg }) { Tile(it, vm, launch) }
        }
    }
}

@Composable
private fun Tile(g: GameItem, vm: PlayViewModel, launch: (String) -> Unit) {
    val art by produceState<Bitmap?>(null, g.pkg) { value = vm.artwork(g.pkg) }
    var long by remember { mutableStateOf(false) }
    Column(
        Modifier.width(200.dp).omniFocus()
            .onKeyEvent { e ->
                if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 && !long) { long = true; val now = vm.data.toggleFavorite(g.pkg); vm.say(if (now) "${g.label} added to favorites" else "${g.label} removed from favorites"); vm.refresh() }
                    if (e.type == KeyEventType.KeyUp) { if (!long) launch(g.pkg); long = false }
                    true
                } else false
            },
    ) {
        Box(Modifier.width(200.dp).height(112.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C1C))) {
            art?.let { Image(it.asImageBitmap(), g.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(g.label, Modifier.padding(top = 6.dp), maxLines = 1, style = OmniType.Label.copy(fontSize = 13.sp))
    }
}

@Composable
private fun Controllers(list: List<Controller>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("CONTROLLERS", style = OmniType.Caption)
        if (list.isEmpty()) Info("No game controller is connected. Pair a Bluetooth controller in the TV's settings; it appears here when it connects.")
        else list.forEach { c ->
            GlassPanel(Modifier.fillMaxWidth(), radius = 14.dp) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, Modifier.weight(1f), style = OmniType.Body.copy(fontSize = 18.sp))
                    Text("${c.kind}  ·  USB/Bluetooth id ${c.vendorProduct}", style = OmniType.Label.copy(color = OmniColor.Text2))
                }
            }
        }
    }
}

@Composable
private fun Consoles(list: List<ConsoleInput>, control: Boolean, vm: PlayViewModel, openIntent: (Intent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HDMI INPUTS (WHAT THE TV REPORTS)", style = OmniType.Caption)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
            list.forEach { c ->
                OmniCard(onClick = {
                    try { openIntent(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(c.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    catch (e: Exception) { vm.say("This TV did not accept the switch. Use the Input button on your remote.") }
                }, modifier = Modifier.width(250.dp)) {
                    Column {
                        Text(c.port, style = OmniType.Heading.copy(fontSize = 20.sp))
                        Text(c.tvReports?.let { "TV reports: $it" } ?: "TV reports no device name", style = OmniType.Label.copy(color = OmniColor.Text2))
                        Text(c.state + "  ·  OK to switch", style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
                    }
                }
            }
        }
        Text("OMNI Play cannot start or control a console: it can only ask the TV to show the input.", style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
    }
}
