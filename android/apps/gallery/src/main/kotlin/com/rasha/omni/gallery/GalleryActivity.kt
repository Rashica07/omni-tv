package com.rasha.omni.gallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniTheme
import com.rasha.omni.design.OmniType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { GalleryHome() } }
    }
}

@Composable
private fun GalleryHome() {
    val ctx = LocalContext.current
    val store = remember { PhotoStore(ctx) }
    val s = remember { GallerySettings(ctx) }
    val scope = rememberCoroutineScope()
    var curated by remember { mutableStateOf(0) }
    var local by remember { mutableStateOf(0) }
    var perm by remember { mutableStateOf(store.hasStoragePermission()) }
    var status by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }       // forces the setting labels to redraw

    fun count() { scope.launch { withContext(Dispatchers.IO) { curated = store.curated().size; local = store.local().size } } }
    LaunchedEffect(perm) { count() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm = it; count() }

    fun refresh() {
        status = "Downloading photographs…"
        scope.launch {
            status = try { val n = withContext(Dispatchers.IO) { store.refreshCurated() }; count(); if (n == 0) "Nothing new to add right now." else "Added $n photographs." }
            catch (e: Exception) { "Could not reach Wikimedia Commons. Check the network; saved photographs still work." }
        }
    }

    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("OMNI Gallery", style = OmniType.Heading.copy(fontSize = 26.sp))
            Text("$curated landscapes saved, $local photographs on this TV" + if (tick < 0) "" else "", style = OmniType.Label.copy(color = OmniColor.Text2))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmniCard(onClick = { ctx.startActivity(Intent(ctx, SlideshowActivity::class.java)) }, modifier = Modifier.width(260.dp)) { Text("Start slideshow", style = OmniType.Heading.copy(fontSize = 20.sp)) }
                OmniCard(onClick = { refresh() }, modifier = Modifier.width(260.dp)) { Text("Download landscapes", style = OmniType.Heading.copy(fontSize = 20.sp)) }
                if (!perm) OmniCard(onClick = { ask.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) }, modifier = Modifier.width(260.dp)) { Text("Allow this TV's photos", style = OmniType.Heading.copy(fontSize = 20.sp)) }
            }
            status?.let { Text(it, style = OmniType.Label.copy(color = OmniColor.Text2)) }
            Text("SETTINGS", Modifier.padding(top = 8.dp), style = OmniType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmniCard(onClick = { val i = GallerySettings.Intervals; s.intervalSec = i[(i.indexOf(s.intervalSec) + 1) % i.size]; tick++ }, modifier = Modifier.width(260.dp)) { Text("Every ${s.intervalSec} s", style = OmniType.Body) }
                OmniCard(onClick = { s.shuffle = !s.shuffle; tick++ }, modifier = Modifier.width(260.dp)) { Text(if (s.shuffle) "Shuffle: on" else "In order", style = OmniType.Body) }
                OmniCard(onClick = { s.clock = !s.clock; tick++ }, modifier = Modifier.width(260.dp)) { Text(if (s.clock) "Clock: on" else "Clock: off", style = OmniType.Body) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmniCard(onClick = { s.info = !s.info; tick++ }, modifier = Modifier.width(260.dp)) { Text(if (s.info) "Credits: on" else "Credits: off", style = OmniType.Body) }
                OmniCard(onClick = { s.favoritesOnly = !s.favoritesOnly; tick++ }, modifier = Modifier.width(260.dp)) { Text(if (s.favoritesOnly) "Favorites only" else "All photographs", style = OmniType.Body) }
                OmniCard(onClick = { s.useLocal = !s.useLocal; tick++ }, modifier = Modifier.width(260.dp)) { Text(if (s.useLocal) "This TV's photos: on" else "This TV's photos: off", style = OmniType.Body) }
            }
            Text("Screensaver: choose “OMNI Gallery” in the TV's settings under Screen saver. Landscapes are open-licence photographs from Wikimedia Commons; the credit is shown with each one.",
                Modifier.padding(top = 8.dp), style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
        }
    }
}
