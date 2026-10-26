package com.rasha.omni.server

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import com.rasha.omni.design.NavGlyph
import com.rasha.omni.design.OmniTopBar
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val Charcoal = Color(0xFF1A1A1D)
private val Raised = Color(0xFF232327)
private val Good = Color(0xFF6FCF8A)
private val Down = Brand.Crimson
private val Cool = Color(0xFF7FC2FF)

private fun t(size: Int, w: FontWeight = FontWeight.Medium, c: Color = Brand.Ivory, ls: Float = 0f) = TextStyle(fontFamily = Geist, fontSize = size.sp, fontWeight = w, color = c, letterSpacing = ls.sp)
private fun cap(c: Color = Brand.Ivory3) = t(10, FontWeight.Bold, c, 1.3f)

private enum class Page(val id: String) { Overview("overview"), Resources("resources"), Library("library"), Devices("devices") }

/** What the server's state is, in one word, from what was really asked. "Unreachable" (could not ask) is not "Needs attention" (asked, and it is unwell). */
private enum class Verdict(val title: String, val color: Color) {
    Healthy("Healthy", Good), Attention("Needs attention", Brand.Gold), Unreachable("Unreachable", Down), Rejected("Not signed in", Down), Connecting("Connecting", Brand.Ivory3)
}

private fun verdictOf(ui: ServerUi): Verdict = when {
    ui.error?.contains("PIN", true) == true -> Verdict.Rejected
    !ui.reachable -> Verdict.Unreachable
    ui.status == null -> Verdict.Connecting
    ui.alerts.any { it.level == Level.Warn } -> Verdict.Attention
    else -> Verdict.Healthy
}

@Composable
fun Dashboard(vm: ServerViewModel, ui: ServerUi) {
    var page by remember { mutableStateOf(Page.Overview) }
    var settings by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val verdict = verdictOf(ui)
    val glow by animateColorAsState(verdict.color.copy(alpha = 0.16f), tween(600), label = "glow")
    BackHandler(enabled = confirmForget) { confirmForget = false }
    BackHandler(enabled = settings && !confirmForget) { settings = false }
    BackHandler(enabled = !settings && page != Page.Overview) { page = Page.Overview }
    Box(Modifier.fillMaxSize().background(Charcoal)) {
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(glow, Color.Transparent), Offset(260f, 120f), 760f)))        // the state's colour, as a still glow: no perpetual motion
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 30.dp, bottom = 26.dp)) {
            OmniTopBar("Server",
                left = listOf(NavEntry("overview", Glyph.Gauge, "Overview"), NavEntry("resources", Glyph.Chart, "Resources"), NavEntry("library", Glyph.Movies, "Library"), NavEntry("devices", Glyph.Device, "Devices")),
                right = listOf(NavEntry("refresh", Glyph.Refresh, "Refresh"), NavEntry("settings", Glyph.Gear, "Server settings")),
                selected = page.id,
                onSelect = { id -> when (id) { "refresh" -> scope.launch { vm.refresh(); vm.refreshLibrary() }; "settings" -> settings = true; else -> page = Page.values().first { it.id == id } } })
            Box(Modifier.fillMaxSize().padding(top = 14.dp)) {
                when (page) {
                    Page.Overview -> Overview(vm, ui, verdict)
                    Page.Resources -> Resources(ui)
                    Page.Library -> LibraryPage(vm, ui)
                    Page.Devices -> DevicesPage(ui)
                }
            }
        }
        if (settings) SettingsSheet(ui, confirmForget, onForget = { confirmForget = true }, onConfirm = { vm.unpair() }, onCancel = { confirmForget = false })
    }
}

// ------------------------------------------------------------------------------------------------ pieces

@Composable
private fun Panel(modifier: Modifier = Modifier, focusable: Boolean = false, onOk: (() -> Unit)? = null, content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.background(Raised.copy(alpha = 0.92f), shape).border(if (focused) 2.dp else 1.dp, if (focused) Brand.Ivory else Brand.Line, shape)
            .then(if (focusable) Modifier.onFocusChanged { focused = it.isFocused }.focusable().onKeyEvent { e -> if (onOk != null && (e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) { onOk(); true } else false } else Modifier),
    ) { content() }
}

@Composable
private fun StatusDot(color: Color, size: Int = 10) { Box(Modifier.size(size.dp).background(color, CircleShape)) }

/** A ring gauge: the value inside, the label under it. A null fraction means "unavailable", drawn as an empty ring with n/a, never as zero. */
@Composable
private fun Ring(label: String, value: String, fraction: Double?, color: Color, note: String, modifier: Modifier = Modifier) {
    val f by animateFloatAsState((fraction ?: 0.0).toFloat(), tween(500), label = "ring")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(108.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx(); val inset = w / 2
                val sz = Size(size.width - w, size.height - w)
                drawArc(Color(0x26E8DCC8), 135f, 270f, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                if (fraction != null) drawArc(color, 135f, 270f * f.coerceIn(0.012f, 1f), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(value, style = t(if (value.length > 6) 17 else 22, FontWeight.SemiBold, if (fraction == null) Brand.Ivory3 else Brand.Ivory))
        }
        Text(label, style = cap())
        Text(note.ifEmpty { " " }, maxLines = 1, style = t(12, c = Brand.Ivory2))
    }
}

// ------------------------------------------------------------------------------------------------ Overview

@Composable
private fun Overview(vm: ServerViewModel, ui: ServerUi, verdict: Verdict) {
    val s = ui.status; val h = ui.health
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
    val warns = ui.alerts.filter { it.level == Level.Warn }
    val dim = !ui.reachable
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
        // the verdict: what matters most, at once
        Panel(Modifier.width(268.dp).fillMaxHeight().focusRequester(first), focusable = true, onOk = { scope.launch { vm.refresh() } }) {
            Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("THE SERVER", style = cap())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatusDot(verdict.color, 14); Text(verdict.title, style = t(28, FontWeight.Bold)) }
                Text(when (verdict) {
                    Verdict.Healthy -> "Everything this server reports is normal."
                    Verdict.Attention -> "${warns.size} thing${if (warns.size == 1) "" else "s"} to look at."
                    Verdict.Unreachable -> if (s == null) "This TV has not been able to reach the server yet." else "This TV cannot reach the server right now. The values shown are the last it saw."
                    Verdict.Rejected -> "The server did not accept the saved PIN. Forget it in the settings and pair again."
                    Verdict.Connecting -> "Asking the server…"
                }, style = t(14, c = Brand.Ivory2))
                warns.take(3).forEach { Text("●  " + it.text, style = t(12, c = Brand.Gold)) }
                Spacer(Modifier.height(10.dp))
                Fact("ADDRESS", ui.address)
                Fact("LAST READING", if (ui.lastOkMs > 0) DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(ui.lastOkMs)) + (if (ui.reachable && ui.latencyMs > 0) "  \u00b7  ${ui.latencyMs} ms" else "") + (if (!ui.reachable) "  \u00b7  not current" else "") else "none yet", warn = !ui.reachable)
                s?.let { Fact("UP FOR", Fmt.duration(it.uptimeS)) }
                Spacer(Modifier.weight(1f))
                Text("OK refreshes", style = t(11, c = Brand.Ivory3))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("VITALS", style = cap())
                    if (s == null) Text(if (ui.reachable) "Waiting for the first reading…" else "No readings: the server has not answered.", style = t(14, c = Brand.Ivory2))
                    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val d = s.disks["media"] ?: s.disks["state"]
                        val memWarn = s.memAvailable < s.memTotal * 0.08
                        val hot = (s.tempC ?: 0.0) >= Alerts.HOT_C
                        Ring("CPU LOAD", "%.1f".format(s.load1), (s.loadPerCpu / 2).coerceIn(0.0, 1.0), if (dim) Brand.Ivory3 else if (s.loadPerCpu > 1.5) Brand.Gold else Cool, "${s.cpus} cores", Modifier.weight(1f))
                        Ring("MEMORY", "%.0f%%".format(s.memUsedFraction * 100), s.memUsedFraction, if (dim) Brand.Ivory3 else if (memWarn) Brand.Gold else Cool, "${Fmt.bytes(s.memTotal - s.memAvailable)} of ${Fmt.bytes(s.memTotal)}", Modifier.weight(1f))
                        Ring("STORAGE", d?.let { "%.0f%%".format(it.usedFraction * 100) } ?: "n/a", d?.usedFraction, if (dim) Brand.Ivory3 else if (d != null && d.free < d.total * 0.05) Brand.Gold else Cool, d?.let { "${Fmt.bytes(it.free)} free" } ?: "not reported", Modifier.weight(1f))
                        Ring("TEMPERATURE", s.tempC?.let { "%.0f°".format(it) } ?: "n/a", s.tempC?.let { ((it - 30) / 70).coerceIn(0.0, 1.0) }, if (dim) Brand.Ivory3 else if (hot) Brand.Gold else Cool, s.tempC?.let { if (hot) "hot: warning from ${Alerts.HOT_C.toInt()}°" else "normal" } ?: "no sensor", Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.weight(1f)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("NETWORK", style = cap())
                        val n = s?.net
                        if (n == null || n.rxBps == null) Text(if (s == null) "No reading" else "Measuring…", style = t(18, FontWeight.SemiBold, Brand.Ivory3))
                        else Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Column { Text("↓ DOWN", style = cap(Cool)); Text(Fmt.rate(n.rxBps), style = t(20, FontWeight.SemiBold)) }
                            Column { Text("↑ UP", style = cap(Brand.Gold)); Text(Fmt.rate(n.txBps ?: 0), style = t(20, FontWeight.SemiBold)) }
                        }
                        Text(n?.let { it.iface + (it.linkMbps?.let { m -> "  ·  $m Mbit link" } ?: "") } ?: " ", style = t(12, c = Brand.Ivory3))
                    }
                }
                Panel(Modifier.weight(1.5f)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("SERVICES", style = cap())
                        if (h != null) {
                            val ok = h.enabled == 0 || h.playing >= h.enabled / 2
                            ServiceRow("OMNIPTV", "${h.playing} of ${h.enabled} channels playing", (h.lastProbe?.let { "checked " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date((it * 1000).toLong())) } ?: "never checked") + (if (h.probing) " · checking now" else ""), if (ok) Good else Brand.Gold)
                            ServiceRow("Guide data", if (h.epg) "available" else "no guide source configured", "", if (h.epg) Good else Brand.Ivory3)
                        }
                        ui.library?.let { l -> ServiceRow("Media library", "${l.movies} film${if (l.movies == 1) "" else "s"}", if (l.scan.scanning) "scanning…" else l.lastScanS?.let { "scanned " + Ago.text(System.currentTimeMillis() / 1000, it) } ?: "never scanned", if (l.roots.all { it.available }) Good else Brand.Gold) }
                        if (ui.library == null && ui.libraryNote != null) ServiceRow("Media library", "unavailable", ui.libraryNote.take(60), Brand.Ivory3)
                        if (h == null && ui.library == null && ui.libraryNote == null) Text(if (ui.reachable) "Waiting…" else "No reading", style = t(14, c = Brand.Ivory2))
                    }
                }
            }
            if (ui.events.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("RECENT CHANGES, SEEN FROM THIS TV", style = cap())
                    ui.events.take(2).forEach { e ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.atMs)), Modifier.width(48.dp), style = t(12, c = Brand.Ivory3))
                            Text(e.text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = t(12, c = if (e.warn) Brand.Gold else Brand.Ivory2))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, warn: Boolean = false) {
    Column(Modifier.padding(bottom = 6.dp)) { Text(label, style = cap()); Text(value, maxLines = 1, style = t(14, FontWeight.SemiBold, if (warn) Brand.Gold else Brand.Ivory)) }
}

@Composable
private fun ServiceRow(name: String, state: String, detail: String, dot: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusDot(dot, 8)
        Text(name, Modifier.width(100.dp), style = t(13, c = Brand.Ivory2))
        Text(state, maxLines = 1, style = t(14, FontWeight.SemiBold))
        if (detail.isNotEmpty()) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = t(12, c = Brand.Ivory3))
    }
}

// ------------------------------------------------------------------------------------------------ Resources

/** Real time-series only: each point is one reading this TV took while this screen was open. With too few points there is no chart, just the current value. */
@Composable
private fun Resources(ui: ServerUi) {
    val hist = ui.history; val s = ui.status
    val n = hist.size
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (e: Exception) { } }
    val span = Series.span(hist.map { it.atMs })
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Resources", style = t(24, FontWeight.Bold))
            Text(if (Series.enough(n)) "Readings every 5 seconds while this screen is open ($span, $n points)" else "Collecting readings: $n of ${Series.MIN_POINTS} before a chart can say anything", style = t(13, c = Brand.Ivory3))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Chart("CPU LOAD", s?.let { "%.1f".format(it.load1) } ?: "n/a", s?.let { "${it.cpus} cores" } ?: "", listOf(hist.map { it.load }), listOf(Cool), 0.0, s?.cpus?.toDouble() ?: 2.0, { "%.1f".format(it) }, n, Modifier.weight(1f).focusRequester(first), line = s?.cpus?.toDouble())
            Chart("MEMORY IN USE", s?.let { "%.0f%%".format(it.memUsedFraction * 100) } ?: "n/a", s?.let { "${Fmt.bytes(it.memTotal - it.memAvailable)} of ${Fmt.bytes(it.memTotal)}" } ?: "", listOf(hist.map { it.memUsed * 100 }), listOf(Cool), 0.0, 100.0, { "%.0f%%".format(it) }, n, Modifier.weight(1f))
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val rx = hist.mapNotNull { it.rxBps?.toDouble() }; val tx = hist.mapNotNull { it.txBps?.toDouble() }
            val top = maxOf(rx.maxOrNull() ?: 0.0, tx.maxOrNull() ?: 0.0, 50_000.0)
            Chart("NETWORK", s?.net?.rxBps?.let { "↓ ${Fmt.rate(it)}" } ?: "n/a", s?.net?.txBps?.let { "↑ ${Fmt.rate(it)}  ·  ${s.net.iface}" } ?: "", listOf(rx, tx), listOf(Cool, Brand.Gold), 0.0, top, { Fmt.rate(it.toLong()) }, minOf(rx.size, tx.size), Modifier.weight(1f))
            val temps = hist.mapNotNull { it.tempC }
            Chart("TEMPERATURE", s?.tempC?.let { "%.0f°C".format(it) } ?: "no sensor", if ((s?.tempC ?: 0.0) >= Alerts.HOT_C) "above the warning level" else "", listOf(temps), listOf(if ((s?.tempC ?: 0.0) >= Alerts.HOT_C) Brand.Gold else Cool), 30.0, maxOf(90.0, temps.maxOrNull() ?: 0.0), { "%.0f°".format(it) }, temps.size, Modifier.weight(1f), line = Alerts.HOT_C)
        }
    }
}

@Composable
private fun Chart(title: String, value: String, note: String, series: List<List<Double>>, colors: List<Color>, lo: Double, hi: Double, fmt: (Double) -> String, points: Int, modifier: Modifier, line: Double? = null) {
    Panel(modifier.fillMaxHeight(), focusable = true) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = cap()); Spacer(Modifier.weight(1f))
                Text(value, style = t(20, FontWeight.SemiBold, if (value == "n/a") Brand.Ivory3 else Brand.Ivory))
            }
            Text(note.ifEmpty { " " }, maxLines = 1, style = t(12, c = Brand.Ivory3))
            if (!Series.enough(points)) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(if (value == "n/a" || value == "no sensor") "Not reported by the server" else "Only the current value so far", style = t(13, c = Brand.Ivory3)) }
            else Row(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.width(44.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) { Text(fmt(hi), style = t(10, c = Brand.Ivory3), maxLines = 1); Text(fmt(lo), style = t(10, c = Brand.Ivory3), maxLines = 1) }
                Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp)) {
                    val span = (hi - lo).takeIf { it > 1e-9 } ?: 1.0
                    drawLine(Color(0x1FE8DCC8), Offset(0f, size.height), Offset(size.width, size.height), 1f)
                    drawLine(Color(0x1FE8DCC8), Offset(0f, 0f), Offset(size.width, 0f), 1f)
                    line?.let { l -> if (l in lo..hi) { val y = size.height * (1f - ((l - lo) / span).toFloat()); drawLine(Brand.Gold.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.5f) } }
                    series.forEachIndexed { si, v ->
                        if (v.size < 2) return@forEachIndexed
                        val p = Path(); val area = Path()
                        v.forEachIndexed { i, x ->
                            val px = size.width * i / (v.size - 1); val py = size.height * (1f - ((x - lo) / span).toFloat().coerceIn(0f, 1f))
                            if (i == 0) { p.moveTo(px, py); area.moveTo(px, size.height); area.lineTo(px, py) } else { p.lineTo(px, py); area.lineTo(px, py) }
                        }
                        if (si == 0) { area.lineTo(size.width, size.height); area.close(); drawPath(area, Brush.verticalGradient(listOf(colors[0].copy(alpha = 0.22f), Color.Transparent))) }
                        drawPath(p, colors[si], style = Stroke(width = 2.6f, cap = StrokeCap.Round))
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Library

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bg by animateColorAsState(when { !enabled -> Color(0x1AE8DCC8); primary && focused -> Color(0xFFDD4459); primary -> Brand.Crimson; focused -> Brand.Ivory; else -> Color(0x40E8DCC8) }, tween(120), label = "btn")
    val fg = if (focused && !primary && enabled) Brand.Ink else if (enabled) Brand.Ivory else Brand.Ivory3
    Box(modifier.onFocusChanged { focused = it.isFocused }.clip(shape).background(bg).clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick).padding(horizontal = 18.dp, vertical = 9.dp)) {
        Text(text, maxLines = 1, style = t(14, FontWeight.SemiBold, fg))
    }
}

@Composable
private fun LibraryPage(vm: ServerViewModel, ui: ServerUi) {
    val l = ui.library; val s = ui.status
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    LaunchedEffect(l != null) { try { first.requestFocus() } catch (e: Exception) { } }
    val now = System.currentTimeMillis() / 1000
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Library", style = t(24, FontWeight.Bold))
        if (l == null) {
            Panel(Modifier.fillMaxWidth()) { Text(ui.libraryNote ?: if (ui.reachable) "Reading the library…" else "The server is not reachable, so its library cannot be read.", Modifier.padding(20.dp), style = t(15, c = Brand.Ivory2)) }
            return@Column
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("FILMS", style = cap())
                    Text("${l.movies}", style = t(56, FontWeight.Light, ls = -1f))
                    val bits = buildList { if (l.missing > 0) add("${l.missing} missing from disk"); if (l.unmatched > 0) add("${l.unmatched} without a poster match") }
                    Text(if (bits.isEmpty()) "All present and identified" else bits.joinToString("  ·  "), style = t(14, c = if (bits.isEmpty()) Brand.Ivory2 else Brand.Gold))
                    Spacer(Modifier.weight(1f))
                    Text(if (l.scan.scanning) "Scanning: ${l.scan.phase.ifEmpty { "working" }} (${l.scan.files} files so far)" else l.lastScanS?.let { "Last scan ${Ago.text(now, it)}" + (if (l.scan.errors > 0) "  ·  ${l.scan.errors} problem${if (l.scan.errors == 1) "" else "s"} reported" else "") } ?: "Never scanned", style = t(13, c = if (l.scan.errors > 0 && !l.scan.scanning) Brand.Gold else Brand.Ivory2))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActionButton(if (l.scan.scanning) "Scanning…" else "Scan library now", { scope.launch { vm.scanNow() } }, Modifier.focusRequester(first), primary = true, enabled = !l.scan.scanning)
                        ui.scanMessage?.let { Text(it, style = t(13, c = Brand.Ivory2)) }
                    }
                }
            }
            Column(Modifier.weight(1.2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("FOLDERS", style = cap())
                        if (l.roots.isEmpty()) Text("No media folder is configured on the server.", style = t(14, c = Brand.Ivory2))
                        l.roots.forEach { r -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { StatusDot(if (r.available) Good else Down, 8); Text(r.name, style = t(15, FontWeight.SemiBold)); Text(if (r.available) "reachable" else "not reachable: is the drive connected?", style = t(13, c = if (r.available) Brand.Ivory3 else Brand.Gold)) } }
                    }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("STORAGE", style = cap())
                        val d = s?.disks?.get("media")
                        if (d == null) Text("The server did not report the media disk.", style = t(14, c = Brand.Ivory2))
                        else {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) { Text(Fmt.bytes(d.free), style = t(24, FontWeight.SemiBold)); Text("free of ${Fmt.bytes(d.total)}", style = t(14, c = Brand.Ivory2)) }
                            Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                                val r = CornerRadius(size.height / 2); drawRoundRect(Color(0x26E8DCC8), Offset.Zero, size, r)
                                drawRoundRect(if (d.free < d.total * 0.05) Brand.Gold else Cool, Offset.Zero, Size((size.width * d.usedFraction.toFloat()).coerceAtLeast(size.height), size.height), r)
                            }
                        }
                    }
                }
                Panel(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("POSTERS AND DETAILS", style = cap())
                        Text(if (l.metadata) "On: films are matched with TMDB" else "Off: films are listed by their file names", style = t(14, FontWeight.SemiBold, if (l.metadata) Brand.Ivory else Brand.Ivory2))
                        ui.libraryNote?.let { Text(it, style = t(12, c = Brand.Gold)) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Devices

@Composable
private fun DevicesPage(ui: ServerUi) {
    val devices = ui.devices
    val now = System.currentTimeMillis() / 1000
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Devices", style = t(24, FontWeight.Bold))
            Text("The server does not track connections. A device appears here once it has saved watch progress to it.", style = t(13, c = Brand.Ivory3))
        }
        when {
            devices == null -> Panel(Modifier.fillMaxWidth()) { Text(if (ui.reachable) "Reading…" else "The server is not reachable.", Modifier.padding(20.dp), style = t(15, c = Brand.Ivory2)) }
            devices.isEmpty() -> Panel(Modifier.fillMaxWidth()) { Text("No device has saved watch progress yet. Play a film in Cinema for a minute and it will appear.", Modifier.padding(20.dp), style = t(15, c = Brand.Ivory2)) }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(devices, key = { it.name }) { d -> DeviceRow(d, now, d == devices.first()) } }
        }
    }
}

@Composable
private fun DeviceRow(d: DeviceSeen, now: Long, first: Boolean) {
    val fr = remember { FocusRequester() }
    if (first) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (e: Exception) { } }
    val active = Ago.active(now, d.lastSeenS)
    Panel(Modifier.fillMaxWidth().focusRequester(fr), focusable = true) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            NavGlyph(Glyph.Device, if (active) Brand.Ivory else Brand.Ivory3, Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text(if (d.name.equals("tv", true)) "TV (OMNI Cinema)" else d.name, style = t(18, FontWeight.SemiBold))
                Text("${d.films} film${if (d.films == 1) "" else "s"} synced  ·  ${d.inProgress} in progress  ·  ${d.watched} watched", style = t(13, c = Brand.Ivory2))
            }
            StatusDot(if (active) Good else Brand.Ivory3, 9)
            Text(if (active) "Active just now" else "Last seen ${Ago.text(now, d.lastSeenS)}", style = t(14, FontWeight.SemiBold, if (active) Good else Brand.Ivory2))
        }
    }
}

// ------------------------------------------------------------------------------------------------ Settings

@Composable
private fun SettingsSheet(ui: ServerUi, confirm: Boolean, onForget: () -> Unit, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(confirm) { try { first.requestFocus() } catch (e: Exception) { } }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        Panel(Modifier.width(460.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!confirm) {
                    Text("SERVER", style = cap())
                    Text(ui.address, style = t(22, FontWeight.Bold))
                    Text("Status and charts update every 5 seconds while this screen is open; the library every 15 seconds (every 5 while it scans). Nothing is polled in the background.", style = t(13, c = Brand.Ivory2))
                    Text("The PIN is stored on this TV only and is never shown or logged.", style = t(13, c = Brand.Ivory3))
                    Spacer(Modifier.height(6.dp))
                    ActionButton("Forget this server", onForget, Modifier.focusRequester(first))
                    Text("Back closes", style = t(11, c = Brand.Ivory3))
                } else {
                    Text("FORGET THIS SERVER?", style = cap(Brand.Gold))
                    Text("This TV will stop reading ${ui.address} and the saved PIN is removed. The server and its films are not changed. You can pair again with the PIN.", style = t(14, c = Brand.Ivory2))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton("Cancel", onCancel, Modifier.focusRequester(first))          // the safe choice has focus first
                        ActionButton("Forget", onConfirm, primary = true)
                    }
                }
            }
        }
    }
}
