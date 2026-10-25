package com.rasha.omni.server

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.PillButton
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Good = OmniColor.Positive
private val Warn = OmniColor.Warning

/**
 * The server at a glance: one sentence of status first, then the vitals that explain it, the services running on it, and what changed lately.
 * Every number comes from the server's own /status and /health; "unreachable" (cannot ask) is shown differently from "failing" (asked, and it is unwell).
 */
@Composable
fun Dashboard(vm: ServerViewModel, ui: ServerUi) {
    val s = ui.status; val h = ui.health
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
    val warns = ui.alerts.count { it.level == Level.Warn }
    val state = when { !ui.reachable && s == null -> "Unreachable"; !ui.reachable -> "Unreachable"; s == null -> "Connecting"; warns > 0 -> "Needs attention"; else -> "Healthy" }
    val color = when (state) { "Healthy" -> Good; "Connecting" -> OmniColor.Text2; else -> Warn }
    OmniScreen {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            // ---- left: the verdict
            Column(Modifier.width(250.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("OMNI SERVER", style = OmniType.Caption)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Canvas(Modifier.size(12.dp)) { drawCircle(color) }
                    Text(state, style = OmniType.Heading.copy(fontSize = 30.sp))
                }
                Text(when {
                    state == "Healthy" -> "Everything this server reports is normal."
                    state == "Unreachable" -> if (s == null) "This TV has not been able to reach the server yet." else "This TV cannot reach the server right now. The values shown are the last it saw."
                    state == "Connecting" -> "Asking the server\u2026"
                    else -> "${warns} thing${if (warns == 1) "" else "s"} to look at."
                }, style = OmniType.Body.copy(fontSize = 14.sp, color = OmniColor.Text2))
                ui.alerts.filter { it.level == Level.Warn }.take(3).forEach { Text("\u25CF  " + it.text, style = OmniType.Label.copy(fontSize = 12.sp, color = Warn)) }
                Box(Modifier.weight(1f))
                Text(ui.address, style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text2))
                Text(if (ui.lastOkMs > 0) "Updated " + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ui.lastOkMs)) + (if (ui.reachable && ui.latencyMs > 0) "  \u00b7  ${ui.latencyMs} ms" else "") else "Not updated yet", style = OmniType.Label.copy(fontSize = 12.sp, color = if (ui.reachable) OmniColor.Text3 else Warn))
                s?.let { Text("Up ${Fmt.duration(it.uptimeS)}", style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3)) }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Refresh", { scope.launch { vm.refresh() } }, Modifier.focusRequester(first), primary = true)
                    PillButton("Forget", { vm.unpair() })
                }
            }
            // ---- right: the evidence
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassPanel(Modifier.fillMaxWidth(), radius = 16.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("VITALS", style = OmniType.Caption)
                        if (s == null) Text(if (ui.reachable) "Waiting for the first reading\u2026" else "No readings: the server has not answered.", style = OmniType.Body.copy(fontSize = 14.sp, color = OmniColor.Text2))
                        else {
                            val dim = !ui.reachable
                            Vital("Temperature", s.tempC?.let { "%.0f \u00B0C".format(it) } ?: "n/a", s.tempC?.let { ((it - 30) / 70).coerceIn(0.0, 1.0) }, (s.tempC ?: 0.0) >= Alerts.HOT_C, ui.history.mapNotNull { it.tempC }, dim, marker = (Alerts.HOT_C - 30) / 70)
                            Vital("CPU load", "%.1f".format(s.load1), (s.loadPerCpu / 2).coerceIn(0.0, 1.0), s.loadPerCpu > 1.5, ui.history.map { it.load }, dim, note = "${s.cpus} cores")
                            Vital("Memory", "%.0f%%".format(s.memUsedFraction * 100), s.memUsedFraction, s.memAvailable < s.memTotal * 0.08, emptyList(), dim, note = "${Fmt.bytes(s.memTotal - s.memAvailable)} of ${Fmt.bytes(s.memTotal)}")
                            val d = s.disks["media"] ?: s.disks["state"]
                            if (d != null) Vital("Storage", Fmt.bytes(d.free) + " free", d.usedFraction, d.free < d.total * 0.05, emptyList(), dim, note = "of ${Fmt.bytes(d.total)}")
                            s.net?.let { n -> Vital("Network", if (n.rxBps == null) "measuring\u2026" else Fmt.rate(n.total), n.fraction, false, emptyList(), dim, note = if (n.rxBps == null) n.iface else "\u2193 ${Fmt.rate(n.rxBps)}  \u2191 ${Fmt.rate(n.txBps ?: 0)}  \u00b7  ${n.iface}" + (n.linkMbps?.let { " $it Mbit" } ?: "")) }
                            Vital("Power", s.batteryPercent?.let { "$it%" } ?: "mains", s.batteryPercent?.let { it / 100.0 }, !s.ac && (s.batteryPercent ?: 100) < 20, emptyList(), dim, note = (if (s.ac) "plugged in" else "on battery") + (s.batteryState?.let { " \u00b7 $it" } ?: ""))
                        }
                    }
                }
                GlassPanel(Modifier.fillMaxWidth(), radius = 16.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("SERVICES", style = OmniType.Caption)
                        if (h != null) {
                            val ok = h.enabled == 0 || h.playing >= h.enabled / 2
                            Service("OMNIPTV", "${h.playing} of ${h.enabled} channels playing", (h.lastProbe?.let { "checked " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date((it * 1000).toLong())) } ?: "never checked") + (if (h.probing) " \u00b7 checking now" else ""), ok)
                            Service("Guide data", if (h.epg) "available" else "no guide source configured", "", h.epg || true, quiet = !h.epg)
                        }
                        s?.let { Service("Media library", if (it.mediaRoot.isEmpty()) "no media folder configured" else it.mediaRoot, it.disks["media"]?.let { d -> Fmt.bytes(d.free) + " free" } ?: "", it.mediaRoot.isNotEmpty(), quiet = it.mediaRoot.isEmpty()) }
                    }
                }
                GlassPanel(Modifier.fillMaxWidth().weight(1f), radius = 16.dp) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("RECENT EVENTS (SEEN FROM THIS TV)", style = OmniType.Caption)
                        if (ui.events.isEmpty()) Text("Nothing has changed since this TV started watching.", style = OmniType.Body.copy(fontSize = 13.sp, color = OmniColor.Text3))
                        ui.events.take(3).forEach { e ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.atMs)), Modifier.width(48.dp), style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
                                Text(e.text, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = OmniType.Label.copy(fontSize = 12.sp, color = if (e.warn) Warn else OmniColor.Text2))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Vital(label: String, value: String, fraction: Double?, warn: Boolean, series: List<Double>, dim: Boolean, note: String = "", marker: Double? = null) {
    val tone = if (warn) Warn else Color.White
    Row(Modifier.fillMaxWidth().height(30.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(96.dp), style = OmniType.Label.copy(fontSize = 13.sp, color = OmniColor.Text2))
        Text(value, Modifier.width(112.dp), maxLines = 1, style = OmniType.Heading.copy(fontSize = 19.sp, color = if (dim) OmniColor.Text3 else tone))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (fraction != null) Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(Color(0x26FFFFFF), Offset.Zero, size, r)
                drawRoundRect(if (dim) Color(0x66FFFFFF) else tone.copy(alpha = 0.9f), Offset.Zero, Size((size.width * fraction.toFloat()).coerceAtLeast(size.height), size.height), r)
                if (marker != null) drawRect(Warn.copy(alpha = 0.9f), Offset((size.width * marker.toFloat()) - 1f, -2f), Size(2f, size.height + 4f))
            }
            if (note.isNotEmpty()) Text(note, maxLines = 1, style = OmniType.Label.copy(fontSize = 11.sp, color = OmniColor.Text3))
        }
        if (series.size >= 12) Trend(series, if (dim) Color(0x66FFFFFF) else tone, Modifier.padding(start = 12.dp).width(70.dp).height(22.dp)) else Box(Modifier.width(82.dp))
    }
}

/** The last readings as a thin line, drawn only in the draw phase. */
@Composable
private fun Trend(v: List<Double>, color: Color, modifier: Modifier) {
    val lo = v.min(); val hi = v.max().let { if (it - lo < 1e-9) lo + 1 else it }
    Canvas(modifier) {
        val p = Path()
        v.forEachIndexed { i, x -> val px = size.width * i / (v.size - 1); val py = size.height * (1f - ((x - lo) / (hi - lo)).toFloat()); if (i == 0) p.moveTo(px, py) else p.lineTo(px, py) }
        drawPath(p, color.copy(alpha = 0.8f), style = Stroke(width = 2f))
    }
}

@Composable
private fun Service(name: String, state: String, detail: String, ok: Boolean, quiet: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(if (quiet) Color(0x66FFFFFF) else if (ok) Good else Warn) }
        Text(name, Modifier.width(96.dp), style = OmniType.Label.copy(fontSize = 13.sp, color = OmniColor.Text2))
        Text(state, maxLines = 1, style = OmniType.Body.copy(fontSize = 13.sp, color = if (quiet) OmniColor.Text3 else Color.White))
        if (detail.isNotEmpty()) Text(detail, maxLines = 1, style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text3))
    }
}
