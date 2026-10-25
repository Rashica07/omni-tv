package com.rasha.omni.weather.app

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.common.UiState
import com.rasha.omni.common.ageText
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.PillButton
import com.rasha.omni.weather.DayUi
import com.rasha.omni.weather.HourUi
import com.rasha.omni.weather.Kind
import com.rasha.omni.weather.Places
import com.rasha.omni.weather.WeatherUi

private val Faint = Color(0xB3FFFFFF)
private val HighlightFill = Color(0x26FFFFFF)
private val Rain = Color(0xFF7FC2FF)

@Composable
fun WeatherScreen(vm: WeatherViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val place by vm.place.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val animate = rememberAnimationsEnabled()
    BackHandler(enabled = picking) { picking = false }
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { e -> if (e.key == Key.Menu && e.type == KeyEventType.KeyDown) { picking = true; true } else false }) {
        when (val s = state) {
            is UiState.Content -> WeatherContent(s, animate, onRefresh = { vm.refresh(force = true) }, onPicker = { picking = true })
            is UiState.Loading -> { PlainSky(); Message("Getting the forecast…") }
            is UiState.Error -> { PlainSky(); Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(s.message, style = OmniType.Heading); PillButton("Try again", { vm.refresh(force = true) }, primary = true)
            } }
            is UiState.Empty -> { PlainSky(); Message(s.message) }
        }
        if (picking) CityPicker(current = place.name, onPick = { vm.pick(it); picking = false })
    }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.Message(text: String) { Text(text, Modifier.align(Alignment.Center), style = OmniType.Heading) }

/** The platform's "remove animations" setting: when the animator scale is 0 nothing moves. */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

@Composable
private fun WeatherContent(s: UiState.Content<WeatherUi>, animate: Boolean, onRefresh: () -> Unit, onPicker: () -> Unit) {
    val ui = s.data
    Box(Modifier.fillMaxSize()) {
        SkyScene(ui, animate)
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TopRow(ui, s, onPicker)
            Hero(ui, onRefresh, onPicker)
            Spacer(Modifier.weight(1f))
            HourCurve(ui)
            WeekStrip(ui)
            Text("OK: refresh     Hold OK or Menu: change city     Back: leave     Weather data by Open-Meteo.com (CC BY 4.0)", style = OmniType.Label.copy(fontSize = 10.5.sp, color = Color(0x80FFFFFF)))
        }
    }
}

@Composable
private fun TopRow(ui: WeatherUi, s: UiState.Content<WeatherUi>, onPicker: () -> Unit) {
    val age = ageText(System.currentTimeMillis() - s.fetchedAtMs)
    val stale = s.lastError != null || ui.fromCache && ui.refreshError != null
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(ui.place, style = OmniType.Heading.copy(fontSize = 21.sp))
            Text(ui.dateLine, style = OmniType.Label.copy(fontSize = 13.sp, color = Faint))
        }
        Text(
            when { s.refreshing -> "Updating…"; stale -> "Offline: showing the forecast from $age"; else -> "Updated $age" },
            Modifier.background(if (stale) Color(0x66FFB020) else Color(0x33000000), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 5.dp),
            style = OmniType.Label.copy(fontSize = 11.5.sp, color = if (stale) Color.White else Faint),
        )
    }
}

@Composable
private fun Hero(ui: WeatherUi, onRefresh: () -> Unit, onPicker: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var longPressed by remember { mutableStateOf(false) }
    Column(
        Modifier.focusRequester(focus).focusable().onKeyEvent { e ->
            if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 && !longPressed) { longPressed = true; onPicker() }
                if (e.type == KeyEventType.KeyUp) { if (!longPressed) onRefresh(); longPressed = false }
                true
            } else false
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(ui.temp, style = OmniType.Display.copy(fontSize = 92.sp, fontWeight = FontWeight.Light))
            Text("°", Modifier.padding(top = 4.dp), style = OmniType.Display.copy(fontSize = 44.sp, fontWeight = FontWeight.Light))
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(ui.condition, style = OmniType.Heading.copy(fontSize = 22.sp))
            Text(ui.hiLo.replace("      ", "   ·   "), Modifier.padding(bottom = 2.dp), style = OmniType.Body.copy(fontSize = 13.sp, color = Faint))
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            ui.facts.forEach { f ->
                Column { Text(f.label.uppercase(), style = OmniType.Caption.copy(fontSize = 9.5.sp, color = Color(0x99FFFFFF))); Text(f.value, style = OmniType.Body.copy(fontSize = 14.sp)) }
            }
        }
    }
}

/**
 * The next 24 hours as a temperature curve with the chance of rain beneath it. Focus it and Left/Right move a cursor along the hours; the header
 * becomes that hour's reading. Drawn in one canvas pass: 24 points, no per-hour composables.
 */
@Composable
private fun HourCurve(ui: WeatherUi) {
    val hours = ui.hours
    if (hours.size < 3) return
    var focused by remember { mutableStateOf(false) }
    var cursor by remember { mutableIntStateOf(0) }
    val measurer = rememberTextMeasurer()
    val labelStyle = OmniType.Label.copy(fontSize = 10.5.sp, color = Color(0xCCFFFFFF))
    val first = hours.first().hour24
    fun idxOf(minute: Int): Int? { val d = minute / 60.0 - first; val i = if (d < 0) d + 24 else d; return if (i in 0.0..(hours.size - 1).toDouble()) i.toInt() else null }
    val sunriseI = idxOf(ui.sunriseMin); val sunsetI = idxOf(ui.sunsetMin)
    val lo = hours.minOf { it.tempC }; val hi = hours.maxOf { it.tempC }.let { if (it - lo < 2.0) lo + 2.0 else it }
    GlassPanel(
        Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused || it.hasFocus }.focusable()
            .onKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) { if (cursor < hours.size - 1) cursor++; true }
                else if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionLeft) { if (cursor > 0) cursor--; true } else false
            },
        radius = 16.dp,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            val h = hours[cursor]
            Text(if (focused) "${h.label.let { if (it == "Now") "Now" else it }}   ${h.temp}   ${h.text}" + (if (h.popN > 0) "   ·   ${h.popN}% chance of rain" else "") else "NEXT 24 HOURS", style = if (focused) OmniType.Body.copy(fontSize = 13.sp) else OmniType.Caption.copy(fontSize = 10.sp, color = Faint))
            Canvas(Modifier.fillMaxWidth().height(62.dp)) {
                val w = size.width; val ht = size.height; val padTop = 14f; val base = ht - 16f
                fun x(i: Int) = w * (0.012f + 0.976f * i / (hours.size - 1))
                fun y(t: Double) = padTop + (base - padTop - 14f) * (1f - ((t - lo) / (hi - lo)).toFloat())
                // rain chance, as quiet bars along the bottom
                for ((i, hr) in hours.withIndex()) if (hr.popN > 0) drawRoundRect(Rain.copy(alpha = 0.55f), Offset(x(i) - 3f, base - (hr.popN / 100f) * 20f), Size(6f, (hr.popN / 100f) * 20f), androidx.compose.ui.geometry.CornerRadius(3f))
                // the curve (smooth), with a faint area beneath
                val line = Path(); val area = Path()
                hours.forEachIndexed { i, hr ->
                    val px = x(i); val py = y(hr.tempC)
                    if (i == 0) { line.moveTo(px, py); area.moveTo(px, base); area.lineTo(px, py) }
                    else { val pxp = x(i - 1); val pyp = y(hours[i - 1].tempC); val mx = (pxp + px) / 2; line.cubicTo(mx, pyp, mx, py, px, py); area.cubicTo(mx, pyp, mx, py, px, py) }
                }
                area.lineTo(x(hours.size - 1), base); area.close()
                drawPath(area, Brush.verticalGradient(listOf(Color(0x30FFFFFF), Color(0x00FFFFFF)), padTop, base))
                drawPath(line, Color.White, style = Stroke(width = 3.2f, cap = StrokeCap.Round))
                // sunrise and sunset, where they fall in this window
                for ((i, label) in listOfNotNull(sunriseI?.let { it to "↑ sunrise" }, sunsetI?.let { it to "↓ sunset" })) {
                    drawLine(Color(0x66FFD87A), Offset(x(i), padTop), Offset(x(i), base), 1.5f)
                    drawText(measurer, label, Offset((x(i) + 4f).coerceAtMost(w - 70f), 0f), TextStyle(color = Color(0xFFFFD87A), fontSize = 9.sp))
                }
                // temperature labels at a few hours
                for (i in hours.indices step 3) drawText(measurer, hours[i].temp, Offset(x(i) - 10f, y(hours[i].tempC) - 20f), labelStyle)
                for (i in hours.indices step 3) drawText(measurer, if (i == 0) "Now" else "%02d".format(hours[i].hour24), Offset(x(i) - 10f, base + 1f), labelStyle)
                if (focused) {
                    drawLine(Color(0x88FFFFFF), Offset(x(cursor), padTop), Offset(x(cursor), base), 1.6f)
                    drawCircle(Color.White, 6f, Offset(x(cursor), y(hours[cursor].tempC)))
                    drawCircle(Color(0x66000000), 3f, Offset(x(cursor), y(hours[cursor].tempC)))
                }
            }
        }
    }
}

/** Seven days as columns: the day, its sky, high over low, and the chance of rain. The focused day also names its weather. */
@Composable
private fun WeekStrip(ui: WeatherUi) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ui.days.forEach { d -> DayCell(d, Modifier.weight(1f)) }
    }
}

@Composable
private fun DayCell(d: DayUi, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.isFocused }.focusable().background(if (focused) HighlightFill else Color(0x14000000), RoundedCornerShape(14.dp)).padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(d.label, style = OmniType.Label.copy(fontSize = 12.sp, color = if (focused) Color.White else Faint, fontWeight = FontWeight.SemiBold))
        Canvas(Modifier.size(24.dp)) { drawWeather(d.kind, true, Offset(size.width / 2, size.height / 2), size.width) }
        Text(d.hi, style = OmniType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
        Text(d.lo, style = OmniType.Body.copy(fontSize = 13.sp, color = Faint))
        Text(if (focused) d.text else d.pop.ifEmpty { " " }, maxLines = 1, style = OmniType.Label.copy(fontSize = 10.5.sp, color = if (focused) Color.White else Rain))
    }
}

@Composable
private fun CityPicker(current: String, onPick: (com.rasha.omni.weather.Place) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xB0000000)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(340.dp).height(400.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("CHOOSE A CITY", style = OmniType.Caption.copy(color = Faint))
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(Places, key = { it.name }) { p ->
                        var focused by remember { mutableStateOf(false) }
                        val mod = if (p.name == current) Modifier.focusRequester(first) else Modifier
                        Text(
                            p.name + if (p.name == current) "   ✓" else "",
                            mod.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
                                .onKeyEvent { e -> if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) { onPick(p); true } else false }
                                .background(if (focused) Color.White else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                            style = OmniType.Body.copy(fontSize = 17.sp, color = if (focused) Color.Black else Color.White),
                        )
                    }
                }
            }
        }
    }
}
