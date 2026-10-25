package com.rasha.omni.cinema

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Library
import com.rasha.omni.library.PlayRules
import com.rasha.omni.library.SourceKind
import com.rasha.omni.library.Title
import com.rasha.omni.library.TmdbClient

/** The one thing worth saying on artwork: problems, and "free". Where a film comes from is on its page, not stamped on every poster. */
fun badgeOf(t: Title): Pair<String, Color>? = when {
    t.availability == Availability.OFFLINE -> "OFFLINE" to Cine.CrimsonHi
    t.availability == Availability.ACCOUNT -> "SIGN IN" to Cine.CrimsonHi
    t.availability == Availability.METADATA -> "INFO ONLY" to Cine.Ivory2
    SourceKind.Archive in t.sourceKinds && t.sourceKinds.size == 1 -> "FREE" to Cine.Gold
    else -> null
}

@Composable
private fun rememberArt(lib: Library, path: String?, size: String, px: Int): android.graphics.Bitmap? =
    produceState<android.graphics.Bitmap?>(null, path, size) { value = lib.images.load(TmdbClient.image(path, size), px) }.value

/** No artwork: a composed card (charcoal, a crimson rule, the title set in ivory). Nothing is invented and it still looks intentional. */
@Composable
private fun NoArt(title: String, year: Int?, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.verticalGradient(listOf(Cine.Surface, Cine.Raised)))) {
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Box(Modifier.width(18.dp).height(2.dp).background(Cine.Crimson))
            Text(title, Modifier.padding(top = 6.dp), maxLines = 4, overflow = TextOverflow.Ellipsis, style = CineType.Row.copy(fontSize = 14.sp))
            if (year != null) Text(year.toString(), style = CineType.Small)
        }
    }
}

/**
 * A poster (2:3). Artwork carries it; the title appears under it only while it has focus (and always on cards without artwork), the way TV platforms
 * keep rows calm. Dimmed when the film cannot be played right now.
 */
@Composable
fun PosterCard(lib: Library, t: Title, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 112.dp, saved: Boolean = false,
               focusKey: String? = null, restoreKey: String? = null, onFocused: (Title) -> Unit = {}) {
    val bmp = rememberArt(lib, t.poster, "w342", 342)
    val fr = remember { FocusRequester() }
    if (focusKey != null && focusKey == restoreKey) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    var focused by remember { mutableStateOf(false) }
    val label by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "label")
    val offline = t.availability != Availability.PLAYABLE
    Column(modifier.width(width)) {
        Box(Modifier.width(width).height(width * 1.5f).focusRequester(fr).cineFocus(onFocus = { focused = it; if (it) onFocused(t) })
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))) {
            if (bmp != null) Image(bmp.asImageBitmap(), t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else NoArt(t.title, t.year, Modifier.fillMaxSize())
            if (offline) Box(Modifier.fillMaxSize().background(Color(0x8C141416)))
            badgeOf(t)?.let { (txt, c) -> Tag(txt, c, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            if (saved) Text("★", Modifier.align(Alignment.TopEnd).padding(6.dp), style = CineType.Row.copy(color = Cine.Gold, fontSize = 13.sp))
            if (t.watched) Tag("✓ WATCHED", Cine.Ivory2, Modifier.align(Alignment.BottomStart).padding(6.dp))
            else if (t.positionMs > 0 && t.durationMs > 0) ProgressBar(t.positionMs / t.durationMs.toFloat(), Modifier.align(Alignment.BottomStart))
        }
        Column(Modifier.padding(top = 8.dp).height(32.dp).alpha(if (bmp == null) 0f else label)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = CineType.Meta.copy(color = Cine.Ivory, fontSize = 12.sp))
            Text(listOfNotNull(t.year?.toString(), t.rating.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString("  "), maxLines = 1, style = CineType.Small.copy(fontSize = 10.sp))
        }
    }
}

/** A landscape card (16:9) for Continue watching: the frame, a crimson progress line and how much is left, on the picture. */
@Composable
fun LandscapeCard(lib: Library, t: Title, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 236.dp, focusKey: String? = null, restoreKey: String? = null, onFocused: (Title) -> Unit = {}) {
    val bmp = rememberArt(lib, t.backdrop ?: t.poster, "w780", 480)
    val fr = remember { FocusRequester() }
    if (focusKey != null && focusKey == restoreKey) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    Box(modifier.width(width).height(width * 9f / 16f).focusRequester(fr).cineFocus(onFocus = { if (it) onFocused(t) })
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).clip(RoundedCornerShape(8.dp))) {
        if (bmp != null) Image(bmp.asImageBitmap(), t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else NoArt(t.title, t.year, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xE6141416))))
        Column(Modifier.align(Alignment.BottomStart).padding(start = 10.dp, end = 10.dp, bottom = 12.dp)) {
            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = CineType.Row.copy(fontSize = 14.sp))
            if (t.positionMs > 0 && t.durationMs > 0) Text("${PlayRules.clock(t.durationMs - t.positionMs)} left", style = CineType.Small)
        }
        if (t.positionMs > 0 && t.durationMs > 0) ProgressBar(t.positionMs / t.durationMs.toFloat(), Modifier.align(Alignment.BottomStart))
    }
}

@Composable
fun ProgressBar(f: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color(0x66141416))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0.03f, 1f)).background(Cine.Crimson))
    }
}

/**
 * The full-bleed backdrop. Its fades into the charcoal ground (left, for the text, and bottom, for the rows) are baked into the bitmap once, so a frame
 * is a single image draw. Changing titles cross-fades.
 */
@Composable
fun HeroBackdrop(lib: Library, path: String?) {
    val bmp = produceState<android.graphics.Bitmap?>(null, path) { value = lib.images.scrimmed(path, ground = Cine.BG_ARGB) }.value
    Crossfade(targetState = bmp, animationSpec = tween(420), label = "backdrop") { b ->
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize())
    }
}

/** The picture's mood colour, for the ambient light under the rows. */
@Composable
fun rememberTint(lib: Library, path: String?): Color =
    produceState(Color(0xFF3A3A40), path) { lib.images.tint(path)?.let { value = Color(it) } }.value
