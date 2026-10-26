package com.rasha.omni.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** OMNI's brand colours for the screens that use more than the launcher's monochrome (Cinema, Weather). */
object Brand {
    val Ivory = Color(0xFFE8DCC8)
    val Ivory2 = Color(0xC7E8DCC8)
    val Ivory3 = Color(0x8CE8DCC8)
    val Ink = Color(0xFF141416)
    val Crimson = Color(0xFFC83246)
    val Gold = Color(0xFFE8B44F)
    val Line = Color(0x26E8DCC8)
}

/** The navigation glyphs: a few strokes each, drawn (never images), so they match the launcher's custom-drawn icons and cost nothing. */
enum class Glyph { Home, Movies, Shows, Search, List, Sources, Sun, Clock, Calendar, Refresh, Pin }

@Composable
fun NavGlyph(g: Glyph, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        val u = size.minDimension / 24f
        val st = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun P(x: Float, y: Float) = Offset(x * u, y * u)
        when (g) {
            Glyph.Home -> drawPath(Path().apply { moveTo(3 * u, 11 * u); lineTo(12 * u, 3.5f * u); lineTo(21 * u, 11 * u); moveTo(5.5f * u, 9.5f * u); lineTo(5.5f * u, 20.5f * u); lineTo(18.5f * u, 20.5f * u); lineTo(18.5f * u, 9.5f * u) }, color, style = st)
            Glyph.Movies -> { drawRoundRect(color, P(3f, 5f), Size(18 * u, 14 * u), CornerRadius(3 * u), style = st); drawPath(Path().apply { moveTo(10f * u, 9f * u); lineTo(10f * u, 15f * u); lineTo(15.5f * u, 12f * u); close() }, color) }
            Glyph.Shows -> { drawRoundRect(color, P(3f, 4.5f), Size(18 * u, 12 * u), CornerRadius(3 * u), style = st); drawLine(color, P(8f, 20f), P(16f, 20f), 2f * u, StrokeCap.Round) }
            Glyph.Search -> { drawCircle(color, 6.5f * u, P(10.5f, 10.5f), style = st); drawLine(color, P(15.5f, 15.5f), P(20.5f, 20.5f), 2f * u, StrokeCap.Round) }
            Glyph.List -> drawPath(Path().apply { moveTo(6f * u, 3.5f * u); lineTo(18f * u, 3.5f * u); lineTo(18f * u, 20.5f * u); lineTo(12f * u, 16f * u); lineTo(6f * u, 20.5f * u); close() }, color, style = st)
            Glyph.Sources -> for (k in 0..1) { val y = 4f + k * 8.5f; drawRoundRect(color, P(3.5f, y), Size(17 * u, 7 * u), CornerRadius(2.5f * u), style = st); drawCircle(color, 1.2f * u, P(16.5f, y + 3.5f)) }
            Glyph.Sun -> { drawCircle(color, 4.2f * u, P(12f, 12f), style = st); for (a in 0 until 8) { val t = a * Math.PI / 4; drawLine(color, P(12f + (7.2 * Math.cos(t)).toFloat(), 12f + (7.2 * Math.sin(t)).toFloat()), P(12f + (9.6 * Math.cos(t)).toFloat(), 12f + (9.6 * Math.sin(t)).toFloat()), 2f * u, StrokeCap.Round) } }
            Glyph.Clock -> { drawCircle(color, 8.5f * u, P(12f, 12f), style = st); drawPath(Path().apply { moveTo(12f * u, 7f * u); lineTo(12f * u, 12f * u); lineTo(15.5f * u, 14f * u) }, color, style = st) }
            Glyph.Calendar -> { drawRoundRect(color, P(3.5f, 5f), Size(17 * u, 15.5f * u), CornerRadius(3 * u), style = st); drawLine(color, P(3.5f, 10f), P(20.5f, 10f), 2f * u, StrokeCap.Round); drawLine(color, P(8f, 3f), P(8f, 6.5f), 2f * u, StrokeCap.Round); drawLine(color, P(16f, 3f), P(16f, 6.5f), 2f * u, StrokeCap.Round) }
            Glyph.Refresh -> { drawArc(color, 40f, 270f, false, P(4.5f, 4.5f), Size(15 * u, 15 * u), style = st); drawPath(Path().apply { moveTo(15.5f * u, 2.5f * u); lineTo(19.5f * u, 6f * u); lineTo(15f * u, 8f * u) }, color, style = st) }
            Glyph.Pin -> { drawPath(Path().apply { moveTo(12f * u, 21f * u); cubicTo(5f * u, 14f * u, 5f * u, 9f * u, 5f * u, 9f * u); cubicTo(5f * u, 5f * u, 8.2f * u, 3f * u, 12f * u, 3f * u); cubicTo(15.8f * u, 3f * u, 19f * u, 5f * u, 19f * u, 9f * u); cubicTo(19f * u, 9f * u, 19f * u, 14f * u, 12f * u, 21f * u); close() }, color, style = st); drawCircle(color, 2.6f * u, P(12f, 9.5f), style = st) }
        }
    }
}

data class NavEntry(val id: String, val glyph: Glyph, val label: String)

private val NavText = TextStyle(fontFamily = Geist, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp)

/** One navigation item. Compact (icon only) at rest; the selected item and the focused item open to show their name. Focused = solid ivory with dark content (the launcher's control-focus rule). */
@Composable
private fun NavItem(e: NavEntry, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val open = selected || focused
    val bg by animateColorAsState(when { focused -> Brand.Ivory; selected -> Color(0x33E8DCC8); else -> Color.Transparent }, tween(120), label = "navBg")
    val fg = if (focused) Brand.Ink else if (selected) Brand.Ivory else Brand.Ivory2
    Row(Modifier.height(38.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(50)).background(bg)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).animateContentSize(tween(180))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        NavGlyph(e.glyph, fg)
        if (open) Text(e.label, Modifier.padding(start = 8.dp), maxLines = 1, style = NavText.copy(color = fg))
    }
}

@Composable
private fun Capsule(content: @Composable () -> Unit) {
    Row(Modifier.height(46.dp).background(Color(0x99141416), RoundedCornerShape(50)).border(1.dp, Brand.Line, RoundedCornerShape(50)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

/**
 * The top bar shared by OMNI's richer screens, built like the launcher's: the OMNI mark and the screen's name on the left, a glass capsule of icons for the
 * destinations, and on the right a second small capsule for the utilities. Only the selected or focused item shows its name. Use inside the 64 dp / 30 dp safe area.
 */
@Composable
fun OmniTopBar(name: String, left: List<NavEntry>, right: List<NavEntry>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(26.dp)) {
            drawCircle(Brand.Ivory, size.minDimension * 0.42f, style = Stroke(width = 2.6.dp.toPx()))
            drawCircle(Brand.Crimson, size.minDimension * 0.15f)
        }
        Column(Modifier.padding(start = 10.dp, end = 26.dp)) {
            Text("OMNI", style = NavText.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp, color = Brand.Ivory))
            Text(name.uppercase(), style = NavText.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.2.sp, color = Brand.Ivory3))
        }
        Capsule { left.forEach { e -> NavItem(e, e.id == selected) { onSelect(e.id) } } }
        Box(Modifier.weight(1f))
        Capsule { right.forEach { e -> NavItem(e, e.id == selected) { onSelect(e.id) } } }
    }
}
