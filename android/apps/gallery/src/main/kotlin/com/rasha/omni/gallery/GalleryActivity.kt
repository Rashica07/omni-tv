package com.rasha.omni.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.rasha.omni.design.Brand
import com.rasha.omni.design.Geist
import com.rasha.omni.design.Glyph
import com.rasha.omni.design.NavEntry
import com.rasha.omni.design.OmniTheme
import com.rasha.omni.design.OmniTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { GalleryHome() } }
    }
}

private val Charcoal = Color(0xFF1A1A1D)
private fun t(size: Int, w: FontWeight = FontWeight.Medium, c: Color = Brand.Ivory, ls: Float = 0f) = TextStyle(fontFamily = Geist, fontSize = size.sp, fontWeight = w, color = c, letterSpacing = ls.sp)
private fun cap() = t(10, FontWeight.Bold, Brand.Ivory3, 1.3f)

/** Thumbnails are small (about 360 px) and bounded: a few MB in all, however large the library is. */
private object Thumbs {
    val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
}

@Composable
private fun GalleryHome() {
    val ctx = LocalContext.current
    val store = remember { PhotoStore(ctx) }
    val s = remember { GallerySettings(ctx) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf("photos") }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    var videos by remember { mutableStateOf<List<Video>?>(null) }
    var perm by remember { mutableStateOf(store.hasStoragePermission()) }
    var status by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }                 // forces labels and the photo list to redraw after a change
    var back by remember { mutableIntStateOf(0) }

    fun load() { scope.launch { withContext(Dispatchers.IO) { val p = s.playlist(store); val v = store.videos(); withContext(Dispatchers.Main) { photos = p; videos = v } } } }
    LaunchedEffect(perm, tick, back) { load() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { perm = it; tick++ }
    BackHandler(enabled = page == "settings") { page = "photos" }

    fun update() {
        status = "Updating photographs…"
        scope.launch {
            status = try {
                val n = withContext(Dispatchers.IO) { var n = 0; if (s.useBing) n += BingProvider(ctx).refreshNow(); if (s.useCurated) n += store.refreshCurated(); n }
                tick++
                if (n == 0) "Already up to date." else "Added $n new photograph${if (n == 1) "" else "s"}."
            } catch (e: Exception) { "Could not reach the photo sources. Saved photographs still work offline." }
        }
    }

    Box(Modifier.fillMaxSize().background(Charcoal)) {
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 30.dp, bottom = 26.dp)) {
            OmniTopBar("Gallery",
                left = listOf(NavEntry("photos", Glyph.Photo, "Photos"), NavEntry("videos", Glyph.Movies, "Videos")),
                right = listOf(NavEntry("ambient", Glyph.Clock, "Ambient display"), NavEntry("settings", Glyph.Gear, "Gallery settings")),
                selected = page.takeIf { it != "ambient" },
                onSelect = { id -> if (id == "ambient") ctx.startActivity(Intent(ctx, SlideshowActivity::class.java)) else page = id })
            Box(Modifier.fillMaxSize().padding(top = 14.dp)) {
                when (page) {
                    "photos" -> PhotosPage(store, photos, perm, status, onAllow = { ask.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) }, onUpdate = { update() },
                        onOpen = { i -> ctx.startActivity(Intent(ctx, SlideshowActivity::class.java).putExtra("viewer", true).putExtra("index", i)) }, tick = tick)
                    "videos" -> VideosPage(store, videos, perm, onAllow = { ask.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) }, onOpen = { v ->
                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(v.uri), "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } catch (e: Exception) { status = "No video player found on this TV." } })
                    else -> SettingsPage(s, tick, status, onChange = { tick++ }, onUpdate = { update() }, onAllow = { ask.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE) }, perm = perm)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ browse

@Composable
private fun rememberThumb(key: String, load: () -> Bitmap?): Bitmap? =
    produceState<Bitmap?>(Thumbs.cache.get(key), key) { if (value == null) value = withContext(Dispatchers.IO) { load()?.also { Thumbs.cache.put(key, it) } } }.value

@Composable
private fun Cell(title: String, sub: String, key: String, favorite: Boolean, badge: String?, first: Boolean, onClick: () -> Unit, load: () -> Bitmap?) {
    val bmp = rememberThumb(key, load)
    val fr = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    var focused by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (focused) 1.035f else 1f, tween(140), label = "thumb")
    val shape = RoundedCornerShape(10.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(1.6f).scale(sc).focusRequester(fr).onFocusChanged { focused = it.isFocused }.clip(shape).background(Color(0xFF232327))
        .border(if (focused) 2.dp else 0.dp, if (focused) Brand.Ivory else Color.Transparent, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)) {
        if (bmp != null) Image(bmp.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (focused) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color(0xCC141416))))
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = t(13, FontWeight.SemiBold))
                if (sub.isNotEmpty()) Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = t(11, c = Brand.Ivory2))
            }
        }
        if (favorite) Text("★", Modifier.align(Alignment.TopEnd).padding(8.dp), style = t(14, c = Brand.Gold))
        badge?.let { Text(it, Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xB3141416), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp), style = t(11, FontWeight.SemiBold)) }
    }
}

@Composable
private fun Empty(text: String, vararg actions: Pair<String, () -> Unit>) {
    Column(Modifier.padding(top = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(text, style = t(16, c = Brand.Ivory2))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions.forEachIndexed { i, (l, f) -> Btn(l, f, primary = i == 0, first = i == 0) } }
    }
}

@Composable
private fun Btn(text: String, onClick: () -> Unit, primary: Boolean = false, first: Boolean = false) {
    val fr = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg = when { focused -> Brand.Ivory; primary -> Brand.Crimson; else -> Color(0x40E8DCC8) }
    Box(Modifier.focusRequester(fr).onFocusChanged { focused = it.isFocused }.clip(shape).background(bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp)) {
        Text(text, maxLines = 1, style = t(14, FontWeight.SemiBold, if (focused) Brand.Ink else Brand.Ivory))
    }
}

@Composable
private fun PhotosPage(store: PhotoStore, photos: List<Photo>?, perm: Boolean, status: String?, onAllow: () -> Unit, onUpdate: () -> Unit, onOpen: (Int) -> Unit, tick: Int) {
    val fav = remember(tick) { store.favorites() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Photos", style = t(24, FontWeight.Bold))
            Text(photos?.let { "${it.size} photograph${if (it.size == 1) "" else "s"}" } ?: "Loading…", style = t(13, c = Brand.Ivory3))
            status?.let { Text(it, style = t(13, c = Brand.Gold)) }
        }
        when {
            photos == null -> Unit
            photos.isEmpty() -> Empty("No photographs yet. Update to download today's Bing photograph and open-licence landscapes, or allow this TV's own photos.",
                "Update photographs" to onUpdate, *(if (perm) emptyArray() else arrayOf("Allow this TV's photos" to onAllow)))
            else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(photos, key = { _, p -> p.id }) { i, p ->
                    Cell(p.title.ifBlank { "Photograph" }, p.credit, p.id, p.id in fav, if (p.source == Source.Bing && i == 0) "TODAY" else null, i == 0, { onOpen(i) }) { store.decode(p, 400, 250) }
                }
            }
        }
    }
}

@Composable
private fun VideosPage(store: PhotoStore, videos: List<Video>?, perm: Boolean, onAllow: () -> Unit, onOpen: (Video) -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Videos", style = t(24, FontWeight.Bold))
            Text(videos?.let { "${it.size} on this TV" } ?: "Loading…", style = t(13, c = Brand.Ivory3))
        }
        when {
            videos == null -> Unit
            videos.isEmpty() -> Empty(if (perm) "No videos found on this TV." else "OMNI needs permission to list this TV's videos. They open in the TV's own player.", *(if (perm) emptyArray() else arrayOf("Allow this TV's media" to onAllow)))
            else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(bottom = 24.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
                    val m = v.durationMs / 60000; val sec = v.durationMs / 1000 % 60
                    Cell(v.title.ifBlank { "Video" }, "", "v:${v.id}", false, if (v.durationMs > 0) "%d:%02d".format(m, sec) else null, i == 0, { onOpen(v) }) { store.videoThumb(v, 400, 250) }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ settings

@Composable
private fun Opt(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.onFocusChanged { focused = it.isFocused }.clip(shape).background(if (focused) Brand.Ivory else Color(0xFF232327))
        .border(1.dp, if (focused) Brand.Ivory else Brand.Line, shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(label.uppercase(), style = t(10, FontWeight.Bold, if (focused) Brand.Ink.copy(alpha = 0.6f) else Brand.Ivory3, 1.2f))
        Text(value, maxLines = 1, style = t(15, FontWeight.SemiBold, if (focused) Brand.Ink else Brand.Ivory))
    }
}

private fun <T> next(list: List<T>, cur: T): T = list[(list.indexOf(cur) + 1).mod(list.size)]

@Composable
private fun SettingsPage(s: GallerySettings, tick: Int, status: String?, onChange: () -> Unit, onUpdate: () -> Unit, onAllow: () -> Unit, perm: Boolean) {
    val m = Modifier.width(266.dp)
    @Suppress("UNUSED_EXPRESSION") tick
    val spots = listOf("Automatic", "Center", "Left", "Right", "Top", "Bottom")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("AMBIENT DISPLAY", style = cap())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Opt("Clock", if (s.clock) "On" else "Off", { s.clock = !s.clock; onChange() }, m)
            Opt("Clock style", if (s.clockStyle == 0) "Minimal" else "Expressive", { s.clockStyle = 1 - s.clockStyle; onChange() }, m)
            Opt("Clock position", spots[s.clockSpot], { s.clockSpot = (s.clockSpot + 1) % spots.size; onChange() }, m)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Opt("Change photograph", "every ${if (s.intervalSec in GallerySettings.Intervals) s.intervalSec else 20} s", { s.intervalSec = next(GallerySettings.Intervals, if (s.intervalSec in GallerySettings.Intervals) s.intervalSec else 20); onChange() }, m)
            Opt("Motion", if (s.motion == 0) "Slow drift" else "Still", { s.motion = 1 - s.motion; onChange() }, m)
            Opt("Credits", if (s.info) "Shown" else "Hidden", { s.info = !s.info; onChange() }, m)
        }
        Text("AUTOMATIC", Modifier.padding(top = 8.dp), style = cap())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Opt("Start when idle", if (s.idleMin == 0) "Never" else "After ${s.idleMin} min", { s.idleMin = next(GallerySettings.IdleChoices, if (s.idleMin in GallerySettings.IdleChoices) s.idleMin else 0); onChange() }, m)
            Opt("While music plays", if (s.musicMode == 0) "Start, show the track" else "Don't start", { s.musicMode = 1 - s.musicMode; onChange() }, m)
            Opt("Keep screen on", "up to ${s.keepAwakeH} h", { s.keepAwakeH = next(GallerySettings.AwakeChoices, if (s.keepAwakeH in GallerySettings.AwakeChoices) s.keepAwakeH else 2); onChange() }, m)
        }
        Text("PHOTOGRAPHS", Modifier.padding(top = 8.dp), style = cap())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Opt("Bing daily photograph", if (s.useBing) "On" else "Off", { s.useBing = !s.useBing; onChange() }, m)
            Opt("Open-licence landscapes", if (s.useCurated) "On" else "Off", { s.useCurated = !s.useCurated; onChange() }, m)
            Opt("This TV's photos", if (s.useLocal) "On" else "Off", { s.useLocal = !s.useLocal; onChange() }, m)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Opt("Order", if (s.shuffle) "Shuffled" else "In order", { s.shuffle = !s.shuffle; onChange() }, m)
            Opt("Show", if (s.favoritesOnly) "Favorites only" else "All photographs", { s.favoritesOnly = !s.favoritesOnly; onChange() }, m)
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Btn("Update photographs now", onUpdate, primary = true)
            if (!perm) Btn("Allow this TV's photos", onAllow)
            status?.let { Text(it, style = t(13, c = Brand.Ivory2)) }
        }
        Text("Bing's daily photograph is downloaded at most twice a day and the last ones are kept for offline use; each photograph is credited to its photographer, for personal use. The screensaver: choose “OMNI Gallery” in the TV's settings under Screen saver.",
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 20.dp), style = t(12, c = Brand.Ivory3))
    }
}
