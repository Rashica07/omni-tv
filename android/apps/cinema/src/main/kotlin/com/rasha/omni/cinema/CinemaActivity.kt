package com.rasha.omni.cinema

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniTheme
import com.rasha.omni.design.OmniType
import com.rasha.omni.library.Entry

class CinemaActivity : ComponentActivity() {
    private val vm: CinemaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) debugSetup(intent)
        if (savedInstanceState == null) vm.start(intent.getStringExtra(Router.R), intent.getStringExtra(Router.ID), intent.getStringExtra(Router.NOTICE))
        setContent { OmniTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(Cine.Bg)) { Root(vm, exit = { finish() }, play = { e -> startActivity(Intent(this@CinemaActivity, PlayerActivity::class.java).putExtra("s", e.sourceId).putExtra("p", e.path)) }) } } }
    }

    /** Developer setup only (`setprop log.tag.OmniDebugOpen DEBUG`): add sources and scan from adb. Ignored in normal use. */
    private fun debugSetup(i: Intent) {
        if (!android.util.Log.isLoggable("OmniDebugOpen", android.util.Log.DEBUG)) return
        i.getStringExtra("addArchive")?.let { id -> vm.addArchive(id) { e -> android.util.Log.d("OmniCinema", "addArchive $id -> ${e ?: "ok"}") } }
        val lan = i.getStringExtra("addLan"); val pin = i.getStringExtra("pin")
        if (lan != null && pin != null) vm.addServer("OMNI server", lan, pin) { e -> android.util.Log.d("OmniCinema", "addLan $lan -> ${e ?: "ok"}"); if (e == null) vm.scanAll() }
        if (i.getBooleanExtra("scan", false)) vm.scanAll()
        i.getStringExtra("tab")?.let { t -> Tab.values().firstOrNull { it.name.equals(t, true) }?.let { vm.start(null, null, null); vm.tab(it) } }
        i.getStringExtra("q")?.let { vm.query.value = it }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); debugSetup(intent); vm.start(intent.getStringExtra(Router.R), intent.getStringExtra(Router.ID), intent.getStringExtra(Router.NOTICE)) }

    override fun onStart() { super.onStart(); vm.reload() }
}

@Composable
private fun Root(vm: CinemaViewModel, play: (Entry) -> Unit, exit: () -> Unit) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val scanning by vm.lib.scanning.collectAsStateWithLifecycle()
    // Back goes to the previous Cinema screen; at the root of a task (Home, or a title opened from the launcher) it leaves, back to where the user came from
    val tab by vm.tab.collectAsStateWithLifecycle()
    BackHandler(enabled = screen !is Screen.Home) { if (!vm.back()) exit() }
    BackHandler(enabled = screen is Screen.Home && tab != Tab.Home) { vm.tab(Tab.Home) }      // other tabs go back to Home first; Back on Home leaves Cinema
    LaunchedEffect(scanning.isEmpty()) { vm.reload() }              // when a scan finishes the shelves refresh
    when (val s = screen) {
        Screen.Home -> BrowseScreen(vm, play)
        is Screen.Title -> TitleScreen(vm, s, play)
        is Screen.Show -> ShowScreen(vm, s, play)
        Screen.Sources -> SourcesScreen(vm)
        Screen.Library -> LibraryScreen(vm, play)
        Screen.AddServer -> AddServerScreen(vm)
        Screen.AddArchive -> AddArchiveScreen(vm)
        Screen.AddLocal -> AddLocalScreen(vm)
    }
}
