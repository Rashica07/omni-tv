package com.rasha.omni.cinema

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Catalogue
import com.rasha.omni.library.Entry
import com.rasha.omni.library.PlayRules
import com.rasha.omni.library.SourceKind
import com.rasha.omni.library.Title
import kotlinx.coroutines.delay

// ------------------------------------------------------------------------------------------------ shared bits

fun runtimeText(min: Int) = if (min <= 0) null else if (min >= 60) "${min / 60}h ${min % 60}m" else "${min}m"

private fun availText(t: Title): String? = when (t.availability) {
    Availability.PLAYABLE -> null
    Availability.OFFLINE -> "${t.sourceLabel} is not reachable right now"
    Availability.ACCOUNT -> "${t.sourceLabel} needs you to sign in again"
    Availability.METADATA -> "Catalogue entry only: no playable file"
}

/** Year · rating (gold) · runtime · genres: only what the metadata really has. */
@Composable
fun MetaLine(t: Title, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        t.year?.let { Text(it.toString(), style = CineType.Meta) }
        if (t.rating > 0) Text("★ %.1f".format(t.rating), style = CineType.Meta.copy(color = Cine.Gold, fontWeight = FontWeight.SemiBold))
        runtimeText(t.runtimeMin)?.let { Text(it, style = CineType.Meta) }
        if (t.kind == "show") Text("Series", style = CineType.Meta)
        if (t.genres.isNotEmpty()) Text(t.genres.split(", ").take(3).joinToString("  ·  "), maxLines = 1, style = CineType.Meta)
        badgeOf(t)?.let { (txt, c) -> Tag(txt, c) }
    }
}

/** The page frame every Cinema tab shares: charcoal ground, the backdrop of what you are looking at, the film's mood colour rising from the bottom, and the navigation on top. */
@Composable
fun CinemaPage(vm: CinemaViewModel, backdrop: String?, content: @Composable () -> Unit) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val tint by animateColorAsState(rememberTint(vm.lib, backdrop), tween(700), label = "tint")
    Box(Modifier.fillMaxSize().background(Cine.Bg)) {
        HeroBackdrop(vm.lib, backdrop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to tint.copy(alpha = 0.18f))))
        Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 20.dp)) {
            TopNav(tab, onTab = { vm.tab(it) }, onSources = { vm.go(Screen.Sources) })
            val scanning by vm.lib.scanning.collectAsStateWithLifecycle()
            if (scanning.isNotEmpty()) Text(scanning.values.first(), Modifier.padding(start = 16.dp), style = CineType.Small)
            content()
        }
        vm.message.collectAsStateWithLifecycle().value?.let {
            Text(it, Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp).background(Color(0xF0232327), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp), style = CineType.Body.copy(color = Cine.Ivory))
            LaunchedEffect(it) { delay(4500); vm.say(null) }
        }
    }
}

/** Browse: the five tabs share one page so the navigation stays put while the content changes. */
@Composable
fun BrowseScreen(vm: CinemaViewModel, play: (Entry) -> Unit) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    when (tab) {
        Tab.Home -> HomeTab(vm, play)
        Tab.Movies -> GridTab(vm, Tab.Movies)
        Tab.Shows -> GridTab(vm, Tab.Shows)
        Tab.MyList -> GridTab(vm, Tab.MyList)
        Tab.Search -> SearchTab(vm)
    }
}

// ------------------------------------------------------------------------------------------------ Home

private data class RowSpec(val id: String, val title: String, val items: List<Title>, val landscape: Boolean = false)

/** Rows that the library can really fill, in the order people use them. Genres only when at least two titles share one. */
private fun rowsFor(titles: List<Title>, saved: Set<String>): List<RowSpec> = buildList {
    fun add(r: RowSpec) { if (r.items.isNotEmpty()) add(r) }
    add(RowSpec("continue", "Continue Watching", Catalogue.select("continue", titles), landscape = true))
    add(RowSpec("mylist", "My List", titles.filter { it.id.text in saved }))
    add(RowSpec("recent", "Recently Added", Catalogue.select("recent", titles)))
    add(RowSpec("network", "From Your Server", Catalogue.select("network", titles)))
    add(RowSpec("free", "Free to Watch", Catalogue.select("free", titles)))
    add(RowSpec("device", "On This TV", Catalogue.select("device", titles)))
    add(RowSpec("shows", "Series", titles.filter { it.kind == "show" }))
    titles.flatMap { t -> t.genres.split(", ").filter { it.isNotBlank() } }.groupingBy { it }.eachCount().entries
        .filter { it.value >= 2 }.sortedByDescending { it.value }.take(4)
        .forEach { (g, _) -> add(RowSpec("g:$g", g, titles.filter { g in it.genres.split(", ") }.sortedByDescending { it.rating })) }
}

@Composable
private fun HomeTab(vm: CinemaViewModel, play: (Entry) -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.myList.collectAsStateWithLifecycle()
    val rows = remember(ui.titles, saved) { rowsFor(ui.titles, saved) }
    val featured = remember(ui.titles) {
        Catalogue.select("continue", ui.titles).firstOrNull { it.backdrop != null }
            ?: ui.titles.filter { it.backdrop != null && it.availability == Availability.PLAYABLE }.maxByOrNull { it.added }
            ?: ui.titles.firstOrNull()
    }
    var focused by remember { mutableStateOf<Title?>(null) }
    var hero by remember { mutableStateOf<Title?>(null) }
    LaunchedEffect(focused?.id, featured?.id) { delay(if (hero == null) 0 else 180); hero = focused ?: featured }      // held keys do not thrash the backdrop
    val h = hero ?: featured
    val heroFocus = remember { FocusRequester() }
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(h != null) { if (h != null && !placed && vm.lastFocus == null) { placed = true; delay(60); try { heroFocus.requestFocus() } catch (e: Exception) { } } }

    CinemaPage(vm, h?.backdrop) {
        Box(Modifier.height(232.dp).fillMaxWidth().padding(top = 18.dp, start = 4.dp)) {
            if (h != null) AnimatedContent(targetState = h, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "hero") { t -> HeroText(t) }
            else if (ui.loaded) EmptyLibrary(vm)
            else Text("Loading your library…", style = CineType.Body)
            if (h != null) HeroActions(vm, h, play, Modifier.align(Alignment.BottomStart).padding(bottom = 10.dp), heroFocus) { focused = null }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (r in rows) item(key = r.id) {
                Column {
                    Row(Modifier.padding(start = 4.dp, top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(r.title, style = CineType.Row)
                        Text(r.items.size.toString(), style = CineType.Small)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(start = 6.dp, end = 30.dp, top = 12.dp, bottom = 4.dp)) {
                        items(r.items, key = { r.id + it.id.text }) { t ->
                            val key = r.id + t.id.text
                            if (r.landscape) LandscapeCard(vm.lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, focusKey = key, restoreKey = vm.lastFocus, onFocused = { vm.lastFocus = key; focused = it })
                            else PosterCard(vm.lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, saved = t.id.text in saved, focusKey = key, restoreKey = vm.lastFocus, onFocused = { vm.lastFocus = key; focused = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroText(t: Title) {
    Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = if (t.title.length < 22) CineType.Hero else CineType.HeroSmall)
        MetaLine(t)
        val about = t.overview.ifEmpty { availText(t) ?: "" }
        if (about.isNotEmpty()) Text(about, maxLines = 2, overflow = TextOverflow.Ellipsis, style = CineType.Body.copy(lineHeight = 19.sp))
        availText(t)?.takeIf { t.overview.isNotEmpty() }?.let { Text(it, style = CineType.Small.copy(color = Cine.CrimsonHi)) }
    }
}

@Composable
private fun HeroActions(vm: CinemaViewModel, t: Title, play: (Entry) -> Unit, modifier: Modifier, first: FocusRequester, onFocus: () -> Unit) {
    val saved by vm.myList.collectAsStateWithLifecycle()
    val inList = t.id.text in saved
    val resume = PlayRules.resumeAt(t.positionMs, t.durationMs)
    Row(modifier.onFocusChanged { if (it.hasFocus) onFocus() }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (t.kind != "show") CineButton(if (resume != null) "Resume  ${PlayRules.clock(resume)}" else "Play", { vm.playTitle(t, play) }, Modifier.focusRequester(first), BtnKind.Primary, icon = "▶")
        CineButton("More Info", { vm.go(Screen.Title(t.id.text)) }, if (t.kind == "show") Modifier.focusRequester(first) else Modifier, icon = "ⓘ")
        CineButton(if (inList) "In My List" else "My List", { vm.toggleList(t.id.text) }, icon = if (inList) "✓" else "+", iconColor = if (inList) Cine.Gold else null)
    }
}

@Composable
private fun EmptyLibrary(vm: CinemaViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Your cinema is empty", style = CineType.Hero)
        Text("Add your OMNI server, a USB drive or folder, or free public-domain films from the Internet Archive.", Modifier.widthIn(max = 520.dp), style = CineType.Body)
        CineButton("Add a source", { vm.go(Screen.Sources) }, Modifier.padding(top = 6.dp), BtnKind.Primary, icon = "+")
    }
}

// ------------------------------------------------------------------------------------------------ Movies, Shows, My List

private enum class Sort(val label: String) { Added("Recently added"), Title("A to Z"), Year("Newest"), Rating("Top rated") }

@Composable
private fun GridTab(vm: CinemaViewModel, tab: Tab) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.myList.collectAsStateWithLifecycle()
    var sort by remember(tab) { mutableStateOf(Sort.Added) }
    var genre by remember(tab) { mutableStateOf("All") }
    val base = remember(ui.titles, saved, tab) {
        when (tab) { Tab.Movies -> ui.titles.filter { it.kind != "show" }; Tab.Shows -> ui.titles.filter { it.kind == "show" }; else -> ui.titles.filter { it.id.text in saved } }
    }
    val genres = remember(base) { listOf("All") + base.flatMap { it.genres.split(", ") }.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key } }
    val list = remember(base, sort, genre) {
        base.filter { genre == "All" || genre in it.genres.split(", ") }.let { l ->
            when (sort) { Sort.Added -> l.sortedByDescending { it.added }; Sort.Title -> l.sortedBy { it.title.lowercase() }; Sort.Year -> l.sortedByDescending { it.year ?: 0 }; Sort.Rating -> l.sortedByDescending { it.rating } }
        }
    }
    var focused by remember { mutableStateOf<Title?>(null) }
    CinemaPage(vm, (focused ?: list.firstOrNull())?.backdrop) {
        Row(Modifier.padding(top = 18.dp, start = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(tab.label, style = CineType.Page)
            Text(if (list.size == 1) "1 title" else "${list.size} titles", Modifier.padding(bottom = 4.dp), style = CineType.Small)
        }
        if (base.size > 1) LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            items(genres) { g -> Chip(g, g == genre) { genre = g } }
            item { Spacer(Modifier.width(10.dp)) }
            item { Chip("Sort: ${sort.label}", false) { sort = Sort.values()[(sort.ordinal + 1) % Sort.values().size] } }
        }
        if (list.isEmpty()) {
            Text(when {
                !ui.loaded -> "Loading…"
                tab == Tab.MyList -> "Nothing saved yet. Choose My List on any film or series to keep it here."
                tab == Tab.Shows -> "No series in your library yet. Episodes named like “Show S01E02” on your server or USB drive appear here."
                else -> "No films here yet. Add a source in Sources."
            }, Modifier.padding(top = 24.dp, start = 4.dp).widthIn(max = 560.dp), style = CineType.Body)
        } else LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxSize().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 40.dp)) {
            items(list.size, key = { list[it].id.text }) { i ->
                val t = list[i]
                PosterCard(vm.lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, width = 104.dp, saved = t.id.text in saved, focusKey = tab.name + t.id.text, restoreKey = vm.lastFocus,
                    onFocused = { vm.lastFocus = tab.name + t.id.text; focused = it })
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(when { focused -> Cine.Ivory; selected -> Color(0x33E8DCC8); else -> Color(0x14E8DCC8) }, tween(120), label = "chip")
    Box(Modifier.onFocusChanged { focused = it.isFocused }.background(bg, RoundedCornerShape(50)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 6.dp)) {
        Text(text, maxLines = 1, style = CineType.Meta.copy(color = if (focused) Cine.Ink else if (selected) Cine.Ivory else Cine.Ivory2, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium))
    }
}

/** The old Library entry point (deep links and the Sources page still lead here): the Movies grid. */
@Composable
fun LibraryScreen(vm: CinemaViewModel, play: (Entry) -> Unit) {
    LaunchedEffect(Unit) { vm.tab(Tab.Movies) }
    BrowseScreen(vm, play)
}

// ------------------------------------------------------------------------------------------------ Search

private val KEYS = "abcdefghijklmnopqrstuvwxyz1234567890".map { it.toString() }

/** Titles whose name, year, genres or description contain every word typed. Accents and case are ignored. */
fun searchTitles(q: String, all: List<Title>): List<Title> {
    fun norm(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
    val words = norm(q).split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()
    return all.map { t -> t to norm(t.title) }.filter { (t, name) -> val hay = "$name ${t.year ?: ""} ${norm(t.genres)} ${norm(t.overview)}"; words.all { it in hay } }
        .sortedWith(compareByDescending<Pair<Title, String>> { (_, n) -> words.all { it in n } }.thenByDescending { (_, n) -> n.startsWith(words.first()) }.thenBy { (_, n) -> n })
        .map { it.first }
}

@Composable
private fun SearchTab(vm: CinemaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val saved by vm.myList.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val results = remember(q, ui.titles) { searchTitles(q, ui.titles) }
    var focused by remember { mutableStateOf<Title?>(null) }
    val firstKey = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); try { firstKey.requestFocus() } catch (e: Exception) { } }
    CinemaPage(vm, focused?.backdrop) {
        Row(Modifier.fillMaxSize().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(Modifier.width(276.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().background(Color(0x1FE8DCC8), RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(if (q.isEmpty()) "Search titles, genres, years" else q.takeLast(24), maxLines = 1, overflow = TextOverflow.Clip, style = CineType.Row.copy(color = if (q.isEmpty()) Cine.Ivory3 else Cine.Ivory, fontWeight = if (q.isEmpty()) FontWeight.Medium else FontWeight.SemiBold))
                }
                for (row in KEYS.chunked(6)) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { k -> Key(k, Modifier.weight(1f).then(if (k == "a") Modifier.focusRequester(firstKey) else Modifier)) { if (q.length < 40) vm.query.value = q + k } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Key("Space", Modifier.weight(2f)) { if (q.isNotEmpty() && !q.endsWith(" ")) vm.query.value = "$q " }
                    Key("⌫", Modifier.weight(1f)) { vm.query.value = q.dropLast(1) }
                    Key("Clear", Modifier.weight(1.5f)) { vm.query.value = "" }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(when { q.isBlank() -> "Your library: ${ui.titles.size} titles"; results.isEmpty() -> "Nothing in your library matches “$q”"; else -> "${results.size} found" }, style = CineType.Row)
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(start = 6.dp, end = 6.dp, top = 14.dp, bottom = 40.dp)) {
                    val shown = if (q.isBlank()) ui.titles.sortedByDescending { it.added } else results
                    items(shown.size, key = { shown[it].id.text }) { i ->
                        val t = shown[i]
                        PosterCard(vm.lib, t, onClick = { vm.go(Screen.Title(t.id.text)) }, width = 100.dp, saved = t.id.text in saved, onFocused = { focused = it })
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Cine.Ivory else Color(0x14E8DCC8), tween(90), label = "key")
    Box(modifier.height(34.dp).onFocusChanged { focused = it.isFocused }.background(bg, RoundedCornerShape(7.dp))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = CineType.Button.copy(color = if (focused) Cine.Ink else Cine.Ivory, fontSize = if (label.length > 1) 12.sp else 15.sp))
    }
}
