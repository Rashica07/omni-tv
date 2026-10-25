package com.rasha.omni.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.PillButton
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Catalogue
import com.rasha.omni.library.Entry
import com.rasha.omni.library.PlayRules
import com.rasha.omni.library.Title
import kotlinx.coroutines.delay

private fun metaLine(t: Title) = listOfNotNull(t.year?.toString(), t.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, t.runtimeMin.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" }, t.genres.takeIf { it.isNotEmpty() }?.split(", ")?.take(3)?.joinToString(", ")).joinToString("    ")

private fun availText(t: Title): String = when (t.availability) {
    Availability.PLAYABLE -> t.sourceLabel
    Availability.OFFLINE -> "${t.sourceLabel}: not reachable right now"
    Availability.ACCOUNT -> "${t.sourceLabel}: sign in again"
    Availability.METADATA -> "Catalogue entry, no playable file"
}

@Composable
fun HomeScreen(vm: CinemaViewModel, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scanning by vm.lib.scanning.collectAsStateWithLifecycle()
    val lib = vm.lib
    val shelves = ui.shelves
    val featured = remember(ui.titles) { Catalogue.select("continue", ui.titles).firstOrNull() ?: Catalogue.select("recent", ui.titles).firstOrNull() ?: ui.titles.firstOrNull() }
    var focused by remember { mutableStateOf<Title?>(null) }
    var hero by remember { mutableStateOf<Title?>(null) }
    LaunchedEffect(focused?.id, featured?.id) { delay(if (hero == null) 0 else 160); hero = focused ?: featured }      // held keys do not thrash the backdrop
    val h = hero ?: featured

    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(ui.titles.isNotEmpty()) { if (ui.titles.isNotEmpty() && !focusedOnce && vm.lastFocus == null) { focusedOnce = true; try { firstFocus.requestFocus() } catch (e: Exception) { } } }
    Box(Modifier.fillMaxSize().background(OmniColor.Bg)) {
    HeroBackdrop(lib, h?.backdrop)                                    // bleeds to the screen edges; only the content respects the safe area
    OmniScreen(background = Color.Transparent) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("OMNI Cinema", Modifier.weight(1f), style = OmniType.Heading.copy(fontSize = 18.sp, color = OmniColor.Text2))
                PillButton("Library", { vm.go(Screen.Library) })
                Box(Modifier.width(10.dp))
                PillButton("Sources", { vm.go(Screen.Sources) })
            }
            // the hero: whatever has focus (or the featured title), with real actions
            Column(Modifier.padding(top = 6.dp).height(166.dp).width(620.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (h != null) {
                    Text(h.title, maxLines = 1, style = OmniType.Heading.copy(fontSize = if (h.title.length < 24) 34.sp else 28.sp))
                    Text(metaLine(h), style = OmniType.Label.copy(fontSize = 13.sp, color = OmniColor.Text2))
                    Text(h.overview.ifEmpty { if (h.availability == Availability.PLAYABLE) "No description yet. Scan again when online to fetch it." else "" }, maxLines = 2, style = OmniType.Body.copy(fontSize = 12.sp, color = OmniColor.Text2))
                    val resume = PlayRules.resumeAt(h.positionMs, h.durationMs)
                    Text(availText(h) + (resume?.let { "    ·    Resume at ${PlayRules.clock(it)} of ${PlayRules.clock(h.durationMs)}" } ?: ""), style = OmniType.Label.copy(fontSize = 11.sp, color = if (h.availability == Availability.PLAYABLE) OmniColor.Text3 else OmniColor.Warning))
                    Row(Modifier.padding(top = 4.dp)) {
                        PillButton(if (resume != null) "Resume" else "Play", { vm.playTitle(h, play) }, primary = true)
                        Box(Modifier.width(8.dp))
                        PillButton("Details", { vm.go(Screen.Title(h.id.text)) })
                    }
                } else if (ui.loaded) {
                    Text("Your library is empty", style = OmniType.Heading.copy(fontSize = 34.sp))
                    Text("Add the OMNI server, a USB drive or folder, or free public-domain films from the Internet Archive.", style = OmniType.Body.copy(fontSize = 16.sp, color = OmniColor.Text2))
                    PillButton("Add a source", { vm.go(Screen.Sources) }, Modifier.padding(top = 8.dp), primary = true)
                } else Text("Loading your library…", style = OmniType.Body.copy(color = OmniColor.Text2))
            }
            if (scanning.isNotEmpty()) Text(scanning.values.first(), style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text2))
            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (sh in shelves) item(key = sh.id) {
                    val list = Catalogue.select(sh.id, ui.titles)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sh.title.uppercase(), style = OmniType.Caption)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
                            items(list, key = { sh.id + it.id.text }) { t ->
                                TitleCard(lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, wide = sh.id == "continue", width = if (sh.id == "continue") 176.dp else 104.dp,
                                    modifier = if (sh.id == shelves.first().id && t.id == list.first().id) Modifier.focusRequester(firstFocus) else Modifier,
                                    focusKey = sh.id + t.id.text, restoreKey = vm.lastFocus, onFocused = { vm.lastFocus = sh.id + t.id.text; focused = it })
                            }
                        }
                    }
                }
            }
        }
        vm.message.collectAsStateWithLifecycle().value?.let { Text(it, Modifier.align(Alignment.BottomStart), style = OmniType.Label.copy(color = OmniColor.Warning)) }
    }
    }
}

private enum class Sort(val label: String) { Title("A to Z"), Year("Newest film"), Added("Recently added") }

/** Every title, with filters that work: source, unwatched, and the genres that exist in the data. Nothing is listed that is not in the library. */
@Composable
fun LibraryScreen(vm: CinemaViewModel, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var sort by remember { mutableStateOf(Sort.Title) }
    var filter by remember { mutableStateOf("All") }
    val genres = remember(ui.titles) { ui.titles.flatMap { it.genres.split(", ") }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6).map { it.key } }
    val filters = listOf("All", "Unwatched", "Free", "On your TV", "From your network") + genres
    val list = remember(ui.titles, sort, filter) {
        ui.titles.filter { t ->
            when (filter) {
                "All" -> true
                "Unwatched" -> !t.watched
                "Free" -> com.rasha.omni.library.SourceKind.Archive in t.sourceKinds
                "On your TV" -> com.rasha.omni.library.SourceKind.Local in t.sourceKinds
                "From your network" -> com.rasha.omni.library.SourceKind.Lan in t.sourceKinds
                else -> t.genres.split(", ").contains(filter)
            }
        }.let { l -> when (sort) { Sort.Title -> l.sortedBy { it.title.lowercase() }; Sort.Year -> l.sortedByDescending { it.year ?: 0 }; Sort.Added -> l.sortedByDescending { it.added } } }
    }
    OmniScreen {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Library", Modifier.padding(end = 10.dp), style = OmniType.Heading.copy(fontSize = 26.sp))
                Text("${list.size} of ${ui.titles.size}", style = OmniType.Label.copy(color = OmniColor.Text2))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
                items(filters) { f -> Chip(f, selected = f == filter) { filter = f } }
                item { Box(Modifier.width(14.dp)) }
                item { Chip("Sort: ${sort.label}", selected = false) { sort = Sort.values()[(sort.ordinal + 1) % Sort.values().size] } }
            }
            if (list.isEmpty() && ui.loaded) Text(if (ui.titles.isEmpty()) "Nothing here yet. Add a source first." else "No titles match this filter.", style = OmniType.Body.copy(color = OmniColor.Text2))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(7), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(6.dp)) {
                items(list.size, key = { list[it].id.text }) { i ->
                    val t = list[i]
                    TitleCard(vm.lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, width = 128.dp, focusKey = "lib" + t.id.text, restoreKey = vm.lastFocus, onFocused = { vm.lastFocus = "lib" + t.id.text })
                }
            }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    PillButton(text, onClick, selected = selected)
}
