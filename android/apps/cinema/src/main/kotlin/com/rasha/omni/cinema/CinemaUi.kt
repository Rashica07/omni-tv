package com.rasha.omni.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Text
import com.rasha.omni.design.Geist

/**
 * OMNI Cinema's own look, inside the OMNI system: charcoal ground, ivory type, crimson for the one main action, gold for ratings and things you saved.
 * The artwork supplies the rest of the colour. No live blur anywhere: depth comes from baked gradients, a soft shadow and a gentle scale on focus.
 */
object Cine {
    val Bg = Color(0xFF1A1A1D)
    val Raised = Color(0xFF232327)
    val Surface = Color(0xFF2B2B30)
    val Line = Color(0x26E8DCC8)
    val Ivory = Color(0xFFE8DCC8)
    val Ivory2 = Color(0xC7E8DCC8)
    val Ivory3 = Color(0x8CE8DCC8)
    val Crimson = Color(0xFFC83246)
    val CrimsonHi = Color(0xFFDD4459)
    val Gold = Color(0xFFE8B44F)
    val Ink = Color(0xFF141416)
    const val BG_ARGB = 0xFF1A1A1D.toInt()
}

object CineType {
    private fun t(size: Int, w: FontWeight, spacing: Float = 0f, color: Color = Cine.Ivory) = TextStyle(fontFamily = Geist, fontSize = size.sp, fontWeight = w, letterSpacing = spacing.sp, color = color)
    val Hero = t(40, FontWeight.Bold, -0.6f)
    val HeroSmall = t(32, FontWeight.Bold, -0.4f)
    val Page = t(28, FontWeight.Bold, -0.3f)
    val Row = t(16, FontWeight.SemiBold, 0.1f)
    val Meta = t(13, FontWeight.Medium, 0.2f, Cine.Ivory2)
    val Body = t(14, FontWeight.Normal, 0.1f, Cine.Ivory2)
    val Button = t(14, FontWeight.SemiBold, 0.2f)
    val Small = t(11, FontWeight.Medium, 0.3f, Cine.Ivory3)
    val Tag = t(10, FontWeight.Bold, 1.2f)
    val Nav = t(15, FontWeight.Medium, 0.2f)
}

/**
 * The focus treatment for artwork: a gentle 6% lift, an ivory edge and a soft shadow, all animated briefly (160 ms) so held keys never wait for it.
 * The focused card is drawn above its neighbours.
 */
fun Modifier.cineFocus(radius: Dp = 8.dp, scaleTo: Float = 1.06f, onFocus: (Boolean) -> Unit = {}): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val k by animateFloatAsState(if (focused) 1f else 0f, tween(160), label = "focus")
    this
        .zIndex(if (focused) 1f else 0f)
        .onFocusChanged { if (focused != it.isFocused) { focused = it.isFocused; onFocus(it.isFocused) } }
        .graphicsLayer { val s = 1f + (scaleTo - 1f) * k; scaleX = s; scaleY = s }
        .drawBehind {
            if (k > 0.01f) {
                val r = radius.toPx()
                for (i in 1..3) {                                      // a stacked soft shadow: three translucent rounded rects, no blur
                    val g = (4 * i).dp.toPx()
                    drawRoundRect(Color.Black.copy(alpha = 0.16f * k), Offset(-g / 2, g * 0.6f), Size(size.width + g, size.height + g * 0.4f), CornerRadius(r + g / 2))
                }
                val w = 2.dp.toPx()
                drawRoundRect(Cine.Ivory.copy(alpha = k), Offset(-w * 1.5f, -w * 1.5f), Size(size.width + w * 3, size.height + w * 3), CornerRadius(r + w * 1.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            }
        }
        .focusable()
}

enum class BtnKind { Primary, Glass }

/** Actions: crimson for the one main action, glass for the rest. Focused, a glass button turns ivory with dark text (the television convention: the focused control is the bright one). */
@Composable
fun CineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.Glass, icon: String? = null, iconColor: Color? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg by animateColorAsState(when { kind == BtnKind.Primary && focused -> Cine.CrimsonHi; kind == BtnKind.Primary -> Cine.Crimson; focused -> Cine.Ivory; else -> Color(0x40E8DCC8) }, tween(140), label = "btn")
    val fg = if (focused && kind == BtnKind.Glass) Cine.Ink else Cine.Ivory
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(140), label = "btnScale")
    Row(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused }
        .then(if (focused && kind == BtnKind.Primary) Modifier.border(2.dp, Cine.Ivory, shape) else Modifier)
        .clip(shape).background(bg).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) Text(icon, style = CineType.Button.copy(color = if (focused && kind == BtnKind.Glass) Cine.Ink else iconColor ?: fg, fontSize = 15.sp))
        Text(text, maxLines = 1, style = CineType.Button.copy(color = fg))
    }
}

/** A small rounded tag on artwork or in a meta line ("FREE", "OFFLINE"). */
@Composable
fun Tag(text: String, color: Color = Cine.Ivory, modifier: Modifier = Modifier) {
    Text(text, modifier.background(Color(0xD9141416), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 2.dp), style = CineType.Tag.copy(color = color))
}

/** The navigation glyphs: a few strokes each, drawn (never images), so they match the launcher's custom-drawn icons and cost nothing. */
enum class Glyph { Home, Movies, Shows, Search, List, Sources }

@Composable
fun NavGlyph(g: Glyph, color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.size(20.dp)) {
        val u = size.minDimension / 24f
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * u, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        fun P(x: Float, y: Float) = Offset(x * u, y * u)
        when (g) {
            Glyph.Home -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(3 * u, 11 * u); lineTo(12 * u, 3.5f * u); lineTo(21 * u, 11 * u); moveTo(5.5f * u, 9.5f * u); lineTo(5.5f * u, 20.5f * u); lineTo(18.5f * u, 20.5f * u); lineTo(18.5f * u, 9.5f * u) }, color, style = st)
            Glyph.Movies -> {
                drawRoundRect(color, P(3f, 5f), Size(18 * u, 14 * u), CornerRadius(3 * u), style = st)
                drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(10f * u, 9f * u); lineTo(10f * u, 15f * u); lineTo(15.5f * u, 12f * u); close() }, color)
            }
            Glyph.Shows -> { drawRoundRect(color, P(3f, 4.5f), Size(18 * u, 12 * u), CornerRadius(3 * u), style = st); drawLine(color, P(8f, 20f), P(16f, 20f), 2f * u, androidx.compose.ui.graphics.StrokeCap.Round) }
            Glyph.Search -> { drawCircle(color, 6.5f * u, P(10.5f, 10.5f), style = st); drawLine(color, P(15.5f, 15.5f), P(20.5f, 20.5f), 2f * u, androidx.compose.ui.graphics.StrokeCap.Round) }
            Glyph.List -> drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(6f * u, 3.5f * u); lineTo(18f * u, 3.5f * u); lineTo(18f * u, 20.5f * u); lineTo(12f * u, 16f * u); lineTo(6f * u, 20.5f * u); close() }, color, style = st)
            Glyph.Sources -> for (k in 0..1) { val y = 4f + k * 8.5f; drawRoundRect(color, P(3.5f, y), Size(17 * u, 7 * u), CornerRadius(2.5f * u), style = st); drawCircle(color, 1.2f * u, P(16.5f, y + 3.5f)) }
        }
    }
}

/** One navigation item. Compact (icon only) at rest; the selected item and the focused item open to show their name. Focused = solid ivory with dark content (the launcher's control-focus rule). */
@Composable
private fun NavItem(g: Glyph, label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val open = selected || focused
    val bg by animateColorAsState(when { focused -> Cine.Ivory; selected -> Color(0x33E8DCC8); else -> Color.Transparent }, tween(120), label = "navBg")
    val fg = if (focused) Cine.Ink else if (selected) Cine.Ivory else Cine.Ivory2
    Row(Modifier.height(38.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(50)).background(bg)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).animateContentSize(tween(180))
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        NavGlyph(g, fg)
        if (open) Text(label, Modifier.padding(start = 8.dp), maxLines = 1, style = CineType.Nav.copy(color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp))
    }
}

@Composable
private fun Capsule(content: @Composable () -> Unit) {
    Row(Modifier.height(46.dp).background(Color(0x99141416), RoundedCornerShape(50)).border(1.dp, Cine.Line, RoundedCornerShape(50)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) { content() }
}

/**
 * The top bar, built like the launcher's: the OMNI mark on the left, a glass capsule of icons (Home, Movies, Shows), and on the right a second small capsule
 * with the utilities (Search, My List, Sources). Only the selected or focused item shows its name. Inside the 64 dp / 30 dp safe area.
 */
@Composable
fun TopNav(current: Tab, onTab: (Tab) -> Unit, onSources: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
            drawCircle(Cine.Ivory, size.minDimension * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.6.dp.toPx()))
            drawCircle(Cine.Crimson, size.minDimension * 0.15f)
        }
        Column(Modifier.padding(start = 10.dp, end = 26.dp)) {
            Text("OMNI", style = CineType.Nav.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp, fontSize = 15.sp))
            Text("CINEMA", style = CineType.Tag.copy(color = Cine.Ivory3, letterSpacing = 3.2.sp))
        }
        Capsule {
            NavItem(Glyph.Home, Tab.Home.label, current == Tab.Home) { onTab(Tab.Home) }
            NavItem(Glyph.Movies, Tab.Movies.label, current == Tab.Movies) { onTab(Tab.Movies) }
            NavItem(Glyph.Shows, Tab.Shows.label, current == Tab.Shows) { onTab(Tab.Shows) }
        }
        Box(Modifier.weight(1f))
        Capsule {
            NavItem(Glyph.Search, Tab.Search.label, current == Tab.Search) { onTab(Tab.Search) }
            NavItem(Glyph.List, Tab.MyList.label, current == Tab.MyList) { onTab(Tab.MyList) }
            NavItem(Glyph.Sources, "Sources", false, onSources)
        }
    }
}

/** A focusable surface for the Sources pages: a quiet raised tile that lifts on focus (lighter, with an ivory edge), so text of any colour stays readable. */
@Composable
fun CineCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Cine.Surface else Cine.Raised, tween(120), label = "card")
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.onFocusChanged { focused = it.isFocused }.graphicsLayer { val k = if (focused) 1.03f else 1f; scaleX = k; scaleY = k }
        .then(if (focused) Modifier.border(2.dp, Cine.Ivory, shape) else Modifier)
        .clip(shape).background(bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) { content() }
}

/**
 * Remote-control rule for rows: Left and Right stay inside (at the end of a short row focus stops, it never jumps to the navigation bar); Up and Down
 * leave as usual. [left] = true lets Left out (the search results, whose keyboard is on the left).
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.rowFence(left: Boolean = false): Modifier = this
    .focusProperties {
        exit = { d -> if (d == androidx.compose.ui.focus.FocusDirection.Right || (d == androidx.compose.ui.focus.FocusDirection.Left && !left)) androidx.compose.ui.focus.FocusRequester.Cancel else androidx.compose.ui.focus.FocusRequester.Default }
    }
    .focusGroup()
