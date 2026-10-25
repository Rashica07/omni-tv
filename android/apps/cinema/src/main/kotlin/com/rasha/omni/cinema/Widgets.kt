package com.rasha.omni.cinema

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import com.rasha.omni.library.Library
import com.rasha.omni.library.TmdbClient

@Composable
fun rememberImage(lib: Library, path: String?, size: String, px: Int): Bitmap? =
    produceState<Bitmap?>(null, path, size) { value = lib.images.load(TmdbClient.image(path, size), px) }.value

/** A poster tile. With no artwork it shows the title on a plain card: nothing is invented. [progress] draws a resume bar; [watched] a check. */
@Composable
fun Poster(lib: Library, poster: String?, title: String, sub: String?, onClick: () -> Unit, modifier: Modifier = Modifier, progress: Float = 0f, watched: Boolean = false,
           width: Dp = 150.dp, focusKey: String? = null, restoreKey: String? = null, onFocused: (String) -> Unit = {}) {
    val bmp = rememberImage(lib, poster, "w342", 342)
    val fr = remember { FocusRequester() }
    if (focusKey != null && focusKey == restoreKey) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    Column(modifier.width(width).focusRequester(fr).onFocusChanged { if (it.isFocused && focusKey != null) onFocused(focusKey) }.omniFocus()
        .onKeyEvent { e -> if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) { onClick(); true } else e.key == Key.DirectionCenter || e.key == Key.Enter }) {
        Box(Modifier.width(width).height(width * 1.5f).clip(RoundedCornerShape(10.dp)).background(OmniColor.Surface2)) {
            if (bmp != null) Image(bmp.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(title, Modifier.align(Alignment.Center).padding(10.dp), style = OmniType.Label.copy(fontSize = 14.sp, textAlign = TextAlign.Center))
            if (watched) Text("✓", Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xCC000000), RoundedCornerShape(50)).padding(horizontal = 7.dp, vertical = 2.dp), style = OmniType.Label.copy(fontSize = 13.sp))
            if (progress > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(5.dp).background(Color(0x99000000))) { Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(Color.White)) }
        }
        Text(title, Modifier.padding(top = 6.dp), maxLines = 1, style = OmniType.Label.copy(fontSize = 13.sp))
        if (sub != null) Text(sub, maxLines = 1, style = OmniType.Label.copy(fontSize = 11.sp, color = OmniColor.Text3))
    }
}

@Composable
fun Backdrop(lib: Library, path: String?) {
    val bmp = rememberImage(lib, path, "w780", 780)
    if (bmp != null) Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.55f)
}
