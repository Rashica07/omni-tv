package com.rasha.omni.cinema

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.omniFocus
import com.rasha.omni.library.Availability
import com.rasha.omni.library.Library
import com.rasha.omni.library.PlayRules
import com.rasha.omni.library.SourceKind
import com.rasha.omni.library.Title
import com.rasha.omni.library.TmdbClient

/** Where a title comes from, in one or two words. Honest and short: this sits on a poster. */
fun badgeOf(t: Title): String? = when {
    t.availability == Availability.OFFLINE -> "OFFLINE"
    t.availability == Availability.ACCOUNT -> "SIGN IN"
    t.availability == Availability.METADATA -> "INFO ONLY"
    SourceKind.Archive in t.sourceKinds && t.sourceKinds.size == 1 -> "FREE"
    SourceKind.Lan in t.sourceKinds && t.sourceKinds.size == 1 -> "SERVER"
    SourceKind.Local in t.sourceKinds && t.sourceKinds.size == 1 -> "THIS TV"
    t.sourceKinds.size > 1 -> "${t.sourceKinds.size} SOURCES"
    else -> null
}

@Composable
private fun Badge(text: String, warn: Boolean, modifier: Modifier = Modifier) {
    Text(text, modifier.background(Color(0xCC000000), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        style = OmniType.Caption.copy(fontSize = 10.sp, color = if (warn) OmniColor.Warning else Color.White))
}

/**
 * One title tile. [wide] shows the landscape artwork (used for Continue watching, where the resume bar sits on the picture); otherwise the poster.
 * No artwork? A plain card with the title: nothing is invented. Offline titles are dimmed and say so.
 */
@Composable
fun TitleCard(lib: Library, t: Title, onClick: () -> Unit, modifier: Modifier = Modifier, wide: Boolean = false, width: Dp = 132.dp, focusKey: String? = null, restoreKey: String? = null, onFocused: (Title) -> Unit = {}) {
    val path = if (wide) (t.backdrop ?: t.poster) else t.poster
    val bmp = produceState<android.graphics.Bitmap?>(null, path, wide) { value = lib.images.load(TmdbClient.image(path, if (wide) "w300" else "w342"), if (wide) 300 else 342) }.value
    val fr = remember { FocusRequester() }
    if (focusKey != null && focusKey == restoreKey) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    val h = if (wide) width * 9f / 16f else width * 1.5f
    val offline = t.availability != Availability.PLAYABLE
    Column(modifier.width(width).focusRequester(fr).onFocusChanged { if (it.isFocused) onFocused(t) }.omniFocus()
        .onKeyEvent { e -> if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) { onClick(); true } else e.key == Key.DirectionCenter || e.key == Key.Enter }) {
        Box(Modifier.width(width).height(h).clip(RoundedCornerShape(10.dp)).background(OmniColor.Surface2)) {
            if (bmp != null) Image(bmp.asImageBitmap(), t.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(t.title, Modifier.align(Alignment.Center).padding(10.dp), maxLines = 4, style = OmniType.Label.copy(fontSize = 14.sp, textAlign = TextAlign.Center))
            if (offline) Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            badgeOf(t)?.let { Badge(it, offline, Modifier.align(Alignment.TopStart).padding(6.dp)) }
            if (t.watched) Text("✓", Modifier.align(Alignment.TopEnd).padding(6.dp).background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 1.dp), style = OmniType.Label.copy(fontSize = 12.sp))
            if (t.positionMs > 0 && t.durationMs > 0 && !t.watched)
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color(0x99000000))) { Box(Modifier.fillMaxHeight().fillMaxWidth((t.positionMs / t.durationMs.toFloat()).coerceIn(0.03f, 1f)).background(Color.White)) }
        }
        Text(t.title, Modifier.padding(top = 6.dp), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = OmniType.Label.copy(fontSize = 13.sp))
        val sub = if (wide && t.positionMs > 0 && t.durationMs > 0) "${PlayRules.clock(t.durationMs - t.positionMs)} left" else t.year?.toString()
        if (sub != null) Text(sub, maxLines = 1, style = OmniType.Label.copy(fontSize = 11.sp, color = OmniColor.Text3))
    }
}

/** The full-bleed backdrop with its scrim baked in; falls back to plain black when the title has none. */
@Composable
fun HeroBackdrop(lib: Library, path: String?) {
    val bmp = produceState<android.graphics.Bitmap?>(null, path) { value = lib.images.scrimmed(path) }.value
    androidx.compose.animation.Crossfade(targetState = bmp, animationSpec = androidx.compose.animation.core.tween(350), label = "backdrop") { b ->
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize())
    }
}
