package com.rasha.omni.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.common.UiState
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private const val PAST = 30 * 60_000L          // the grid starts 30 minutes before now
private const val SPAN = 3 * 3_600_000L        // and shows three hours

@Composable
fun GuideScreen(vm: GuideViewModel, granted: Boolean, onAsk: () -> Unit, onWatch: (GChannel) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    var shownProgram by remember { mutableStateOf<GProgram?>(null) }
    var shownChannel by remember { mutableStateOf<GChannel?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }      // the "now" line moves twice a minute, never per frame
    OmniScreen {
      Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text("OMNI Guide", style = OmniType.Heading.copy(fontSize = 26.sp))
            when {
                !granted -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("The programme guide comes from your TV's own antenna listings.", style = OmniType.Body)
                    Text("Android asks for permission to read TV listings. Nothing leaves this TV.", style = OmniType.Body.copy(color = OmniColor.Text2))
                    Spacer(Modifier.height(16.dp))
                    OmniCard(onClick = onAsk) { Text("Allow", style = OmniType.Body) }
                }
                else -> when (val s = state) {
                    is UiState.Loading -> Text("Reading the TV's guide…", style = OmniType.Body.copy(color = OmniColor.Text2))
                    is UiState.Empty -> Text(s.message, style = OmniType.Body.copy(color = OmniColor.Text2))
                    is UiState.Error -> Text(s.message, style = OmniType.Body.copy(color = OmniColor.Warning))
                    is UiState.Content -> Grid(s.data, now, onFocusProgram = { c, p -> shownChannel = c; shownProgram = p }, onWatch = onWatch)
                }
            }
        }
        val d = (state as? UiState.Content)?.data
        if (granted && d != null) Details(shownChannel, shownProgram, now, d, Modifier.align(Alignment.BottomStart))
        note?.let { Text(it, Modifier.align(Alignment.BottomEnd), style = OmniType.Label.copy(color = OmniColor.Warning)) }
      }
    }
}

@Composable
private fun Grid(d: GuideData, now: Long, onFocusProgram: (GChannel, GProgram?) -> Unit, onWatch: (GChannel) -> Unit) {
    val start = now - PAST
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(26.dp)) {
            Spacer(Modifier.width(150.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                for (h in 0..3) {
                    val t = (start / 1_800_000L + 1) * 1_800_000L + h * 3_600_000L
                    val x = maxWidth * ((t - start).toFloat() / SPAN)
                    if (t - start in 0..SPAN) Text(fmt.format(Date(t)), Modifier.offset(x = x), style = OmniType.Caption.copy(color = OmniColor.Text2))
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(318.dp)) {
            val timelineW = maxWidth - 150.dp
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(d.channels, key = { it.id }) { ch ->
                    val progs = d.programs[ch.id].orEmpty()
                    Row(Modifier.fillMaxWidth().height(50.dp)) {
                        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.Center) {
                            Text(ch.name, style = OmniType.Body.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (progs.isEmpty()) OmniColor.Text3 else Color.White), maxLines = 1)
                            Text("channel ${ch.number}", style = OmniType.Label.copy(fontSize = 11.sp, color = OmniColor.Text3))
                        }
                        Box(Modifier.width(timelineW).fillMaxHeight()) {
                            if (progs.isEmpty()) {
                                var f by remember { mutableStateOf(false) }
                                Text("No guide data from the broadcast", Modifier.fillMaxSize().onFocusChanged { f = it.isFocused; if (it.isFocused) onFocusProgram(ch, null) }.focusable().clickable { onWatch(ch) }
                                    .background(if (f) Color(0x33FFFFFF) else Color(0x14FFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 14.dp), style = OmniType.Label.copy(color = OmniColor.Text3))
                            } else progs.forEach { p ->
                                val s = maxOf(p.start, start); val e = minOf(p.end, start + SPAN)
                                if (e > s) Block(p, ch, now, Modifier.offset(x = timelineW * ((s - start).toFloat() / SPAN)).width(timelineW * ((e - s).toFloat() / SPAN) - 3.dp), onFocusProgram, onWatch)
                            }
                            if (now in start..(start + SPAN)) Box(Modifier.offset(x = timelineW * (PAST.toFloat() / SPAN)).width(2.dp).fillMaxHeight().background(Color(0xFFFF5A4F)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Block(p: GProgram, ch: GChannel, now: Long, modifier: Modifier, onFocus: (GChannel, GProgram?) -> Unit, onWatch: (GChannel) -> Unit) {
    var f by remember { mutableStateOf(false) }
    val live = now in p.start..p.end
    Box(
        modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp)).onFocusChanged { f = it.isFocused; if (it.isFocused) onFocus(ch, p) }.focusable().clickable { onWatch(ch) }
            .background(if (f) Color.White else if (live) Color(0xFF2B3A4A) else Color(0xFF1E1E1E)),
    ) {
        Text(p.title, Modifier.padding(horizontal = 10.dp, vertical = 8.dp), maxLines = 2, style = OmniType.Label.copy(fontSize = 13.sp, color = if (f) Color.Black else Color.White))
        if (live) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(p.progress(now)).height(3.dp).background(if (f) Color(0xFF444444) else Color(0xFF7FC2FF)))
    }
}

@Composable
private fun Details(ch: GChannel?, p: GProgram?, now: Long, d: GuideData, modifier: Modifier) {
    val fmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    GlassPanel(modifier.fillMaxWidth().height(96.dp), radius = 16.dp) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            if (ch == null) Text("Move onto a programme to see it. Guide data from the TV's broadcast reaches ${horizonText(d, now)}.", style = OmniType.Label.copy(color = OmniColor.Text2))
            else if (p == null) Text("${ch.name}: the broadcast carries no guide data for this channel. OK to watch it.", style = OmniType.Label.copy(color = OmniColor.Text2))
            else {
                Text(p.title, style = OmniType.Body.copy(fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
                Text("${ch.name}   ·   ${fmt.format(Date(p.start))}–${fmt.format(Date(p.end))}   ·   ${p.genre.ifEmpty { "no genre given" }}" + if (now in p.start..p.end) "   ·   ${(p.progress(now) * 100).toInt()}% through" else "", style = OmniType.Label.copy(color = OmniColor.Text2), maxLines = 1)
                if (p.description.isNotEmpty()) Text(p.description, style = OmniType.Label.copy(fontSize = 12.sp, color = Color(0xFFDDDDDD)), maxLines = 2)
            }
        }
    }
}

private fun horizonText(d: GuideData, now: Long): String {
    if (d.horizon <= now) return "no time ahead"
    val h = (d.horizon - now) / 3_600_000.0
    return if (h < 1) "under an hour ahead" else "about ${"%.0f".format(h)} h ahead"
}
