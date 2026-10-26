package com.rasha.omni.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.library.ApiPath
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Entry
import com.rasha.omni.library.PlayRules
import com.rasha.omni.library.SourceKind
import com.rasha.omni.library.Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun sourceName(k: SourceKind?) = when (k) { SourceKind.Lan -> "OMNI server"; SourceKind.Local -> "This TV or USB"; SourceKind.Archive -> "Internet Archive"; else -> "Source" }

/** Titles that share a genre with this one, best rated first. Only from the library: nothing is recommended that you cannot open. */
private fun related(t: Title, all: List<Title>): List<Title> {
    val g = t.genres.split(", ").filter { it.isNotBlank() }.toSet()
    if (g.isEmpty()) return emptyList()
    return all.filter { it.id != t.id && it.genres.split(", ").any { x -> x in g } }.sortedWith(compareByDescending<Title> { o -> o.genres.split(", ").count { it in g } }.thenByDescending { it.rating }).take(12)
}

@Composable
fun TitleScreen(vm: CinemaViewModel, s: Screen.Title, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()                          // reload after watch-state changes
    val saved by vm.myList.collectAsStateWithLifecycle()
    var t by remember { mutableStateOf<Title?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(s, ui) { t = vm.title(s.id); loaded = true }
    val fr = remember { FocusRequester() }
    LaunchedEffect(t != null) { if (t != null) try { fr.requestFocus() } catch (x: Exception) { } }
    val title = t
    if (title != null && title.kind == "show") { ShowScreen(vm, Screen.Show(title.copies.first().showId), play); return }
    val tint = rememberTint(vm.lib, title?.backdrop)
    Box(Modifier.fillMaxSize().background(Cine.Bg)) {
        HeroBackdrop(vm.lib, title?.backdrop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to tint.copy(alpha = 0.16f))))
        if (!loaded) return@Box
        if (title == null) {
            Column(Modifier.padding(start = 64.dp, top = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Not available", style = CineType.Hero)
                Text(s.notice ?: "This title is not in your library.", Modifier.widthIn(max = 520.dp), style = CineType.Body)
                CineButton("Back", { vm.back() || run { vm.go(Screen.Home); true } }, Modifier.focusRequester(fr), BtnKind.Primary)
            }
            LaunchedEffect(Unit) { try { fr.requestFocus() } catch (x: Exception) { } }
            return@Box
        }
        val rel = remember(title, ui.titles) { related(title, ui.titles) }
        // the page fits one screen: a fixed column, so focusing Play never scrolls the title away
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 56.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            run {
                Column(Modifier.widthIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(title.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = if (title.title.length < 22) CineType.Hero.copy(fontSize = 44.sp) else CineType.Hero)
                    MetaLine(title)
                    s.notice?.let { Text(it, style = CineType.Body.copy(color = Cine.CrimsonHi)) }
                    Text(title.overview.ifEmpty { "No description yet. This title has not been matched with a metadata provider; it will be when the server or this TV is online." },
                        maxLines = 3, overflow = TextOverflow.Ellipsis, style = CineType.Body.copy(fontSize = 15.sp, lineHeight = 21.sp))
                    if (title.positionMs > 0 && title.durationMs > 0 && !title.watched) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.width(160.dp)) { ProgressBar(title.positionMs / title.durationMs.toFloat()) }
                        Text("${PlayRules.clock(title.durationMs - title.positionMs)} left", style = CineType.Small)
                    }
                    val resume = PlayRules.resumeAt(title.positionMs, title.durationMs)
                    val inList = title.id.text in saved
                    Row(Modifier.padding(top = 6.dp).rowFence(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CineButton(if (resume != null) "Resume  ${PlayRules.clock(resume)}" else if (title.watched) "Watch Again" else "Play", { vm.playTitle(title, play) }, Modifier.focusRequester(fr), BtnKind.Primary, icon = "▶")
                        if (resume != null) CineButton("From the Start", { vm.setWatched(title.copies.first(), false) { vm.playTitle(title, play) } }, icon = "↺")
                        CineButton(if (inList) "In My List" else "My List", { vm.toggleList(title.id.text) }, icon = if (inList) "✓" else "+", iconColor = if (inList) Cine.Gold else null)
                        CineButton(if (title.watched) "Unwatched" else "Watched", { title.copies.forEach { c -> vm.setWatched(c, !title.watched) } }, icon = if (title.watched) "○" else "✓")
                    }
                }
            }
            Sources(vm, title)
            if (rel.isNotEmpty()) run {
                Column(Modifier.padding(top = 8.dp)) {
                    Text("More Like This", style = CineType.Row)
                    LazyRow(Modifier.rowFence(), horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 4.dp, end = 30.dp, top = 12.dp, bottom = 4.dp)) {
                        items(rel, key = { "rel" + it.id.text }) { r -> PosterCard(vm.lib, r, onClick = { vm.go(Screen.Title(r.id.text)) }, width = 100.dp, saved = r.id.text in saved) }
                    }
                }
            }
        }
        vm.message.collectAsStateWithLifecycle().value?.let {
            Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).background(Color(0xF0232327), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp), style = CineType.Body.copy(color = Cine.Ivory))
        }
    }
}

/** Where the film plays from: every copy, with whether its source answers right now. Honest, small, below the actions. */
@Composable
private fun Sources(vm: CinemaViewModel, title: Title) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val av by produceState<Map<Long, Availability>>(emptyMap(), title.id) { value = withContext(Dispatchers.IO) { vm.lib.availabilities() } }
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("AVAILABLE FROM", style = CineType.Tag.copy(color = Cine.Ivory3))
        title.copies.forEach { c ->
            val state = av[c.sourceId]
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(7.dp).background(when (state) { Availability.PLAYABLE -> Color(0xFF6FCF8A); null -> Cine.Ivory3; else -> Cine.CrimsonHi }, CircleShape))
                Text(sourceName(ui.kinds[c.sourceId]), style = CineType.Meta.copy(color = Cine.Ivory))
                Text(if (ApiPath.isApi(c.path)) "server library" else c.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 300.dp), style = CineType.Small)
                Text(when (state) { Availability.PLAYABLE -> "ready"; Availability.OFFLINE -> "not reachable"; Availability.ACCOUNT -> "sign in again"; Availability.METADATA -> "no file"; null -> "checking…" }, style = CineType.Small)
            }
        }
    }
}

/** A series: its backdrop and story, the episode to watch next as the main action, then every episode with its state. */
@Composable
fun ShowScreen(vm: CinemaViewModel, s: Screen.Show, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val eps by produceState(emptyList<Entry>(), s, ui) { value = withContext(Dispatchers.IO) { vm.lib.db.episodes(s.id) } }
    val show = ui.shows.firstOrNull { it.id == s.id }
    val next = eps.firstOrNull { it.resumeMs > 0 && !it.watched } ?: eps.firstOrNull { !it.watched } ?: eps.firstOrNull()
    val fr = remember { FocusRequester() }
    LaunchedEffect(next != null) { if (next != null) try { fr.requestFocus() } catch (x: Exception) { } }
    Box(Modifier.fillMaxSize().background(Cine.Bg)) {
        HeroBackdrop(vm.lib, show?.backdrop)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 64.dp, end = 64.dp, top = 64.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Column(Modifier.widthIn(max = 580.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(show?.title ?: "", maxLines = 2, style = CineType.Hero)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        show?.year?.let { Text(it.toString(), style = CineType.Meta) }
                        Text("${eps.size} episodes", style = CineType.Meta)
                        if (show != null && show.watched > 0) Text("${show.watched} watched", style = CineType.Meta)
                    }
                    Text(show?.overview.orEmpty(), maxLines = 3, overflow = TextOverflow.Ellipsis, style = CineType.Body.copy(fontSize = 15.sp))
                    if (next != null) {
                        val r = PlayRules.resumeAt(next.resumeMs, next.durationMs)
                        CineButton((if (r != null) "Resume" else if (next.watched) "Play Again" else "Play") + "  S${next.season} E${next.number}", { play(next) }, Modifier.focusRequester(fr).padding(top = 4.dp), BtnKind.Primary, icon = "▶")
                    }
                }
            }
            item { Text("Episodes", Modifier.padding(top = 10.dp), style = CineType.Row) }
            items(eps, key = { it.sourceId.toString() + it.path }) { e -> EpisodeRow(e) { play(e) } }
        }
    }
}

@Composable
private fun EpisodeRow(e: Entry, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.widthIn(max = 720.dp).fillMaxWidth().onFocusChanged { focused = it.isFocused }.background(if (focused) Cine.Ivory else Color(0x14E8DCC8), RoundedCornerShape(8.dp))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        val ink = if (focused) Cine.Ink else Cine.Ivory
        Text("S${e.season} E${e.number}", Modifier.width(70.dp), style = CineType.Meta.copy(color = if (focused) Cine.Ink else Cine.Ivory3))
        Text(e.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = CineType.Row.copy(fontSize = 14.sp, color = ink, fontWeight = FontWeight.Medium))
        when {
            e.watched -> Text("✓", style = CineType.Row.copy(color = if (focused) Cine.Ink else Cine.Gold))
            e.resumeMs > 0 && e.durationMs > 0 -> Box(Modifier.width(60.dp)) { ProgressBar(e.resumeMs / e.durationMs.toFloat()) }
        }
    }
}
