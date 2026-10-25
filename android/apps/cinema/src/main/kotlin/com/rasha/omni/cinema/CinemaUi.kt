package com.rasha.omni.cinema

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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

/**
 * The top navigation: the OMNI Cinema mark, then Home, Movies, Shows, My List, Search, and Sources on the right. Quiet text; the tab you are on is
 * ivory with a crimson mark under it, the focused one is an ivory pill.
 */
@Composable
fun TopNav(current: Tab, onTab: (Tab) -> Unit, onSources: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(Cine.Crimson, CircleShape))
        Text("OMNI", Modifier.padding(start = 8.dp), style = CineType.Nav.copy(fontWeight = FontWeight.Bold, letterSpacing = 3.sp))
        Text("CINEMA", Modifier.padding(start = 6.dp, end = 26.dp), style = CineType.Nav.copy(fontWeight = FontWeight.Light, letterSpacing = 3.sp, color = Cine.Ivory2))
        Tab.values().forEach { t ->
            NavItem(t.label, selected = t == current, onClick = { onTab(t) })
        }
        Box(Modifier.weight(1f))
        NavItem("Sources", selected = false, onClick = onSources)
    }
}

@Composable
private fun NavItem(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Cine.Ivory else Color.Transparent, tween(120), label = "nav")
    androidx.compose.foundation.layout.Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(50)).background(bg).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
            Text(text, style = CineType.Nav.copy(color = when { focused -> Cine.Ink; selected -> Cine.Ivory; else -> Cine.Ivory3 }, fontWeight = if (selected || focused) FontWeight.SemiBold else FontWeight.Medium))
        }
        Box(Modifier.padding(top = 3.dp).width(18.dp).height(2.dp).background(if (selected && !focused) Cine.Crimson else Color.Transparent, RoundedCornerShape(1.dp)))
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
