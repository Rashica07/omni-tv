package com.rasha.omni.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.PillButton
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.library.Entry
import com.rasha.omni.library.PlayRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun meta(e: Entry) = listOfNotNull(e.year?.toString(), e.runtimeMin.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" }, e.rating.takeIf { it > 0 }?.let { "%.1f / 10".format(it) }, e.genres.takeIf { it.isNotEmpty() }).joinToString("   ·   ")

@Composable
fun TitleScreen(vm: CinemaViewModel, s: Screen.Title, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()                          // reload after watch-state changes
    val msg by vm.message.collectAsStateWithLifecycle()
    var t by remember { mutableStateOf<com.rasha.omni.library.Title?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(s, ui) { t = vm.title(s.id); loaded = true }
    val fr = remember { FocusRequester() }
    LaunchedEffect(t != null) { if (t != null) try { fr.requestFocus() } catch (x: Exception) { } }
    val title = t
    if (title != null && title.kind == "show") { ShowScreen(vm, Screen.Show(title.copies.first().showId), play); return }
    Box(Modifier.fillMaxSize().background(OmniColor.Bg)) {
    HeroBackdrop(vm.lib, title?.backdrop)
    OmniScreen(background = androidx.compose.ui.graphics.Color.Transparent) {
        if (!loaded) return@OmniScreen
        if (title == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Not available", style = OmniType.Heading.copy(fontSize = 28.sp))
                Text(s.notice ?: "This title is not in your library.", style = OmniType.Body.copy(fontSize = 16.sp, color = OmniColor.Text2))
                PillButton("Back to library", { vm.back() || run { vm.go(Screen.Home); true } }, Modifier.focusRequester(fr), primary = true)
            }
            LaunchedEffect(Unit) { try { fr.requestFocus() } catch (x: Exception) { } }
            return@OmniScreen
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Poster(vm.lib, title.poster, title.title, null, {}, width = 140.dp, modifier = Modifier.padding(top = 12.dp), watched = title.watched)
            Column(Modifier.weight(1f).widthIn(max = 520.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title.title, maxLines = 2, style = OmniType.Heading.copy(fontSize = if (title.title.length < 26) 32.sp else 26.sp))
                Text(listOfNotNull(title.year?.toString(), title.rating.takeIf { it > 0 }?.let { "\u2605 %.1f".format(it) }, title.runtimeMin.takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" }, title.genres.takeIf { it.isNotEmpty() }).joinToString("    "), style = OmniType.Label.copy(fontSize = 13.sp, color = OmniColor.Text2))
                s.notice?.let { Text(it, style = OmniType.Body.copy(fontSize = 14.sp, color = OmniColor.Warning)) }
                Text(title.overview.ifEmpty { "No description found. This item has not been matched with a metadata provider yet; scan again when online." }, maxLines = 5, style = OmniType.Body.copy(fontSize = 13.sp, color = OmniColor.Text2))
                Text("WHERE IT PLAYS FROM", Modifier.padding(top = 4.dp), style = OmniType.Caption)
                val av = remember(title) { mutableStateOf<Map<Long, com.rasha.omni.library.Availability>>(emptyMap()) }
                LaunchedEffect(title.id) { av.value = withContext(Dispatchers.IO) { vm.lib.availabilities() } }
                title.copies.forEach { c ->
                    val kind = ui.kinds[c.sourceId]
                    val state = av.value[c.sourceId]
                    Text((when (kind) { com.rasha.omni.library.SourceKind.Lan -> "OMNI server"; com.rasha.omni.library.SourceKind.Local -> "This TV or USB"; com.rasha.omni.library.SourceKind.Archive -> "Internet Archive"; else -> "Source" }) +
                        "    " + (if (com.rasha.omni.library.ApiPath.isApi(c.path)) "server library" else c.path.substringAfterLast('/')) + "    " + (when (state) { com.rasha.omni.library.Availability.PLAYABLE -> "reachable"; com.rasha.omni.library.Availability.OFFLINE -> "not reachable"; com.rasha.omni.library.Availability.ACCOUNT -> "sign in again"; else -> "checking" }),
                        maxLines = 1, style = OmniType.Label.copy(fontSize = 12.sp, color = if (state == com.rasha.omni.library.Availability.PLAYABLE) OmniColor.Text2 else OmniColor.Warning))
                }
                val resume = PlayRules.resumeAt(title.positionMs, title.durationMs)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    PillButton(if (resume != null) "Resume from ${PlayRules.clock(resume)}" else if (title.watched) "Watch again" else "Play", { vm.playTitle(title, play) }, Modifier.focusRequester(fr), primary = true)
                    PillButton(if (title.watched) "Mark as unwatched" else "Mark as watched", { title.copies.forEach { c -> vm.setWatched(c, !title.watched) } })
                }
                msg?.let { Text(it, style = OmniType.Body.copy(fontSize = 14.sp, color = OmniColor.Warning)) }
            }
        }
    }
    }
}

@Composable
fun ShowScreen(vm: CinemaViewModel, s: Screen.Show, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val eps by produceState(emptyList<Entry>(), s, ui) { value = withContext(Dispatchers.IO) { vm.lib.db.episodes(s.id) } }
    val show = ui.shows.firstOrNull { it.id == s.id }
    val next = eps.firstOrNull { it.resumeMs > 0 && !it.watched } ?: eps.firstOrNull { !it.watched } ?: eps.firstOrNull()
    val fr = remember { FocusRequester() }
    LaunchedEffect(next != null) { if (next != null) try { fr.requestFocus() } catch (x: Exception) { } }
    OmniScreen {
        Backdrop(vm.lib, show?.backdrop)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(show?.title ?: "", style = OmniType.Heading.copy(fontSize = 30.sp))
            Text(show?.overview.orEmpty(), maxLines = 3, style = OmniType.Body.copy(fontSize = 15.sp, color = OmniColor.Text2))
            if (next != null) OmniCard(onClick = { play(next) }, modifier = Modifier.width(380.dp).focusRequester(fr)) {
                val r = PlayRules.resumeAt(next.resumeMs, next.durationMs)
                Text((if (r != null) "Resume" else if (next.watched) "Play again" else "Play") + "  S${next.season} E${next.number}", style = OmniType.Body.copy(fontSize = 18.sp))
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(eps, key = { it.sourceId.toString() + it.path }) { e ->
                    OmniCard(onClick = { play(e) }, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("S${e.season} E${e.number}", Modifier.width(80.dp), style = OmniType.Label.copy(color = OmniColor.Text2))
                            Text(e.title, Modifier.weight(1f), maxLines = 1, style = OmniType.Body.copy(fontSize = 17.sp))
                            if (e.watched) Text("✓", style = OmniType.Label) else if (e.resumeMs > 0) Text(PlayRules.clock(e.resumeMs), style = OmniType.Label.copy(color = OmniColor.Text3))
                        }
                    }
                }
            }
        }
    }
}
