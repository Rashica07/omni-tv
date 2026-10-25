package com.rasha.omni.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.tv.material3.Text
import com.rasha.omni.common.UiState

/** The overscan-safe frame every OMNI screen sits in. */
@Composable
fun OmniScreen(modifier: Modifier = Modifier, background: Color = OmniColor.Bg, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    Box(
        modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = OmniSpace.SafeSide, vertical = OmniSpace.SafeVertical),
    ) { content() }
}

/**
 * OMNI's focus language: a white ring drawn outside the item with a black gap (so it stays visible on white artwork) and a small lift. Drawing
 * is done in draw phase only (drawBehind), so moving focus never recomposes the content.
 */
fun Modifier.omniFocus(shape: RoundedCornerShape = RoundedCornerShape(OmniSpace.Radius), onFocus: (Boolean) -> Unit = {}): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused; onFocus(it.isFocused) }
        .scale(if (focused) 1.05f else 1f)
        .drawBehind {
            if (focused) {
                val gap = OmniSpace.RingGap.toPx()
                val ring = OmniSpace.Ring.toPx()
                val r = OmniSpace.Radius.toPx() + gap
                drawRoundRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(-gap / 2, -gap / 2), size = Size(size.width + gap, size.height + gap), cornerRadius = CornerRadius(r), style = Stroke(gap + 1f))
                drawRoundRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(-gap - ring / 2, -gap - ring / 2), size = Size(size.width + 2 * gap + ring, size.height + 2 * gap + ring), cornerRadius = CornerRadius(r + ring), style = Stroke(ring))
            }
        }
        .focusable()
}

/** A focusable card: charcoal surface, hairline edge, the OMNI focus ring. */
@Composable
fun OmniCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(OmniSpace.Radius)
    Box(
        modifier
            .omniFocus(shape)
            .background(OmniColor.Surface2, shape)
            .border(1.dp, OmniColor.Hairline, shape)
            .clickable(onClick = onClick)
            .padding(18.dp),
    ) { content() }
}

/** Translucent panel: fill, faint tint and a hairline rim. No blur and no shadow: those are what make glass expensive on a TV GPU. */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, radius: androidx.compose.ui.unit.Dp = 22.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(radius)
    Box(modifier.background(Color(0x66000000), shape).background(Color(0x1CFFFFFF), shape).border(1.dp, Color(0x30FFFFFF), shape)) { content() }
}

/** Loading, empty, error and content in one place so every app handles all four the same way. */
@Composable
fun <T> StateHost(state: UiState<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (UiState.Content<T>) -> Unit) {
    when (state) {
        is UiState.Content -> content(state)
        is UiState.Loading -> Centered(modifier) { Text("Loading…", style = OmniType.Body.copy(color = OmniColor.Text2)) }
        is UiState.Empty -> Centered(modifier) { Text(state.message, style = OmniType.Body.copy(color = OmniColor.Text2)) }
        is UiState.Error -> Centered(modifier) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, style = OmniType.Body.copy(color = OmniColor.Text2))
                if (state.canRetry) OmniCard(onClick = onRetry) { Text("Try again", style = OmniType.Body) }
            }
        }
    }
}

@Composable
private fun Centered(modifier: Modifier, content: @Composable () -> Unit) = Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
