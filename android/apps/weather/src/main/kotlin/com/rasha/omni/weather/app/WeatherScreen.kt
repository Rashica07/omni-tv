package com.rasha.omni.weather.app

import android.app.ActivityManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.alpha
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
import com.rasha.omni.design.Brand
import com.rasha.omni.design.Geist
import com.rasha.omni.design.GlassPanel
import com.rasha.omni.design.Glyph
import com.rasha.omni.design.NavEntry
import com.rasha.omni.design.OmniTopBar
import com.rasha.omni.weather.DayUi
import com.rasha.omni.weather.Freshness
import com.rasha.omni.weather.Honesty
import com.rasha.omni.weather.HourUi
import com.rasha.omni.weather.Kind
import com.rasha.omni.weather.label
import com.rasha.omni.weather.Outlook
import com.rasha.omni.weather.Places
import com.rasha.omni.weather.WeatherUi

private val Snow = Color(0xFFFFFFFF)
private val Soft = Color(0xD9FFFFFF)
private val Faint = Color(0xA6FFFFFF)
private val Rain = Color(0xFF8FCBFF)

private fun t(size: Int, w: FontWeight = FontWeight.Medium, c: Color = Snow, ls: Float = 0f) = TextStyle(fontFamily = Geist, fontSize = size.sp, fontWeight = w, color = c, letterSpacing = ls.sp)

private enum class Page(val id: String) { Today("today"), Hourly("hourly"), Week("week") }

/** A developer-only what-if for the look of a condition. Never used in normal operation. */
class SimPreview(val kind: String, val hour: Int, val minute: Int = 20) {
    fun apply(ui: WeatherUi): WeatherUi {
        val k = when (kind) { "rain" -> Kind.Rain; "snow" -> Kind.Snow; "storm" -> Kind.Storm; "fog" -> Kind.Fog; "cloud" -> Kind.Cloud; "partly" -> Kind.Partly; else -> Kind.Clear }
        val now = hour * 60 + minute
        return ui.copy(kind = k, condition = k.label(), day = now in ui.sunriseMin until ui.sunsetMin, nowMin = now, cloudiness = com.rasha.omni.weather.UiMapper.cloudiness(k, -1))
    }
}

@Composable
fun WeatherScreen(vm: WeatherViewModel, sim: SimPreview? = null) {
    val real by vm.state.collectAsStateWithLifecycle()
    val state = if (sim != null && real is UiState.Content<*>) (real as UiState.Content<WeatherUi>).let { it.copy(data = sim.apply(it.data)) } else real
    val place by vm.place.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.Today) }
    val animate = rememberAnimationsEnabled()
    val lite = rememberLiteGraphics()
    BackHandler(enabled = picking) { picking = false }
    BackHandler(enabled = !picking && page != Page.Today) { page = Page.Today }          // like Cinema: Back from a tab goes to the first one, then leaves
    Box(Modifier.fillMaxSize().onPreviewKeyEvent { e -> if (e.key == Key.Menu && e.type == KeyEventType.KeyDown) { picking = true; true } else false }) {
        when (val s = state) {
            is UiState.Content -> SkyScene(s.data, animate, lite)
            else -> PlainSky()
        }
        // legibility: a soft shade from the left and the top, so text is readable on any sky and the sky stays visible on the right
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0x66000000), 0.62f to Color(0x14000000), 1f to Color.Transparent)))
        Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0x73000000), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(start = 64.dp, end = 64.dp, top = 30.dp, bottom = 26.dp)) {
            OmniTopBar("Weather",
                left = listOf(NavEntry("today", Glyph.Sun, "Today"), NavEntry("hourly", Glyph.Clock, "Hourly"), NavEntry("week", Glyph.Calendar, "Week")),
                right = listOf(NavEntry("refresh", Glyph.Refresh, "Refresh"), NavEntry("city", Glyph.Pin, "Change city")),
                selected = page.id,
                onSelect = { id -> when (id) { "refresh" -> vm.refresh(force = true); "city" -> picking = true; else -> page = Page.values().first { it.id == id } } })
            when (val s = state) {
                is UiState.Content -> Pages(s, page, onRefresh = { vm.refresh(force = true) }, onPicker = { picking = true })
                is UiState.Loading -> Centered { Text("Getting the forecast…", style = t(22, FontWeight.SemiBold)) }
                is UiState.Error -> Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(s.message, style = t(20, FontWeight.SemiBold)); Text("Choose Refresh in the bar to try again.", style = t(14, c = Soft))
                    }
                }
                is UiState.Empty -> Centered { Text(s.message, style = t(22, FontWeight.SemiBold)) }
            }
        }
        if (sim != null) Text("SIMULATED PREVIEW", Modifier.align(Alignment.TopCenter).padding(top = 8.dp).background(Brand.Crimson, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 3.dp), style = t(10, FontWeight.Bold, Color.White, 1.4f))
        if (picking) CityPicker(current = place.name, onPick = { vm.pick(it); picking = false })
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }

/** The platform's "remove animations" setting: when the animator scale is 0 nothing moves. */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val ctx = LocalContext.current
    return remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
}

/** Low-memory televisions get a lighter sky: fewer drops and cloud layers, slower drift. Nothing else changes. */
@Composable
private fun rememberLiteGraphics(): Boolean {
    val ctx = LocalContext.current
    return remember { (ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager).let { it.isLowRamDevice || it.memoryClass < 160 } }
}

@Composable
private fun Pages(s: UiState.Content<WeatherUi>, page: Page, onRefresh: () -> Unit, onPicker: () -> Unit) {
    AnimatedContent(page, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "page") { p ->
        when (p) {
            Page.Today -> TodayPage(s, onRefresh, onPicker)
            Page.Hourly -> HourlyPage(s.data)
            Page.Week -> WeekPage(s.data)
        }
    }
}

// ------------------------------------------------------------------------------------------------ Today

/** The freshness of what is on screen, and the words for it: old data is never shown as live. */
@Composable
private fun rememberStatus(s: UiState.Content<WeatherUi>): Pair<Freshness, String> {
    val age = System.currentTimeMillis() - s.fetchedAtMs
    val f = Honesty.freshness(age, s.lastError != null || s.data.refreshError != null)
    return f to Honesty.status(f, ageText(age), s.refreshing)
}

@Composable
private fun TodayPage(s: UiState.Content<WeatherUi>, onRefresh: () -> Unit, onPicker: () -> Unit) {
    val ui = s.data
    val (fresh, status) = rememberStatus(s)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    var longPressed by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    val outlook = remember(ui.hours) { Outlook.sentence(ui.hours) }
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        Column(
            Modifier.focusRequester(focus).onFocusChanged { focused = it.isFocused }.focusable().onKeyEvent { e ->
                if (e.key == Key.DirectionCenter || e.key == Key.Enter) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount >= 1 && !longPressed) { longPressed = true; onPicker() }
                    if (e.type == KeyEventType.KeyUp) { if (!longPressed) onRefresh(); longPressed = false }
                    true
                } else false
            }.padding(start = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.place, style = t(24, FontWeight.SemiBold))
                Text(ui.dateLine, style = t(14, c = Soft))
                StatusPill(fresh, status)
            }
            Row(Modifier.alpha(if (fresh == Freshness.Stale) 0.72f else 1f), verticalAlignment = Alignment.Top) {
                Text(ui.temp, style = t(104, FontWeight.Light, ls = -2f))
                Text("°", Modifier.padding(top = 6.dp), style = t(48, FontWeight.Light))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(38.dp)) { drawWeather(ui.kind, ui.day, Offset(size.width / 2, size.height / 2), size.width) }
                Text(ui.condition, style = t(26, FontWeight.SemiBold))
            }
            Text(ui.hiLo.replace("      ", "    ·    "), Modifier.padding(top = 4.dp), style = t(15, c = Soft))
            if (outlook != null) Text(outlook, Modifier.padding(top = 8.dp).background(Color(0x59000000), RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 6.dp), style = t(14, FontWeight.SemiBold, Brand.Ivory))
        }
        Spacer(Modifier.weight(1f))
        DetailsRow(ui)
    }
}

@Composable
private fun StatusPill(f: Freshness, text: String) {
    val bg = when (f) { Freshness.Live -> Color(0x40000000); Freshness.Aging -> Color(0x59000000); Freshness.Stale -> Brand.Crimson.copy(alpha = 0.85f) }
    Text(text, Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp), style = t(12, FontWeight.SemiBold, if (f == Freshness.Stale) Color.White else Soft))
}

/** Secondary information, quiet and aligned: only what the forecast provides. */
@Composable
private fun DetailsRow(ui: WeatherUi) {
    fun tile(title: String) = ui.tiles.firstOrNull { it.title == title }
    val wind = tile("WIND"); val hum = tile("HUMIDITY"); val uv = tile("UV INDEX")?.takeIf { it.value != "–" }
    val rise = tile("SUNRISE")?.value?.takeIf { it != "–" }; val set = tile("SUNSET")?.value?.takeIf { it != "–" }
    val rain = ui.facts.firstOrNull { it.label.startsWith("Rain") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        wind?.let { Detail("WIND", it.value, it.sub, Modifier.weight(1f)) }
        hum?.let { Detail("HUMIDITY", it.value, it.sub, Modifier.weight(1f)) }
        uv?.let { Detail("UV INDEX", it.value, it.sub, Modifier.weight(1f)) }
        rain?.let { Detail("RAIN, NEXT 12 H", it.value.replaceFirstChar { c -> c.uppercase() }, "", Modifier.weight(1f)) }
        if (rise != null && set != null) Detail("SUN", "↑ $rise   ↓ $set", "", Modifier.weight(1.3f), gold = true)
    }
}

@Composable
private fun Detail(label: String, value: String, sub: String, modifier: Modifier, gold: Boolean = false) {
    GlassPanel(modifier, radius = 14.dp) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = t(10, FontWeight.Bold, Faint, 1.2f))
            Text(value, maxLines = 1, style = t(18, FontWeight.SemiBold, if (gold) Brand.Gold else Snow))
            Text(sub.ifEmpty { " " }, maxLines = 1, style = t(12, c = Soft))
        }
    }
}

// ------------------------------------------------------------------------------------------------ Hourly

@Composable
private fun HourlyPage(ui: WeatherUi) {
    val hours = ui.hours
    var cursor by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    val h = hours.getOrNull(cursor)
    Column(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column {
            Text("Next 24 hours", style = t(22, FontWeight.SemiBold))
            if (h != null) Text("${if (cursor == 0) "Now" else "%02d:00".format(h.hour24)}   ·   ${h.temp}   ·   ${h.text}" + (if (h.popN > 0) "   ·   ${h.popN}% chance of rain" else ""), style = t(15, c = Soft))
        }
        GlassPanel(Modifier.fillMaxWidth(), radius = 16.dp) { HourCurve(ui, cursor, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
            itemsIndexed(hours, key = { i, _ -> i }) { i, hr -> HourCard(hr, i == 0, if (i == 0) Modifier.focusRequester(first) else Modifier) { cursor = i } }
        }
        if (hours.size >= 3) {
            val hiH = hours.maxByOrNull { it.tempC }!!; val loH = hours.minByOrNull { it.tempC }!!; val rain = hours.maxOf { it.popN }
            Text("High ${hiH.temp} at ${"%02d:00".format(hiH.hour24)}     \u00B7     Low ${loH.temp} at ${"%02d:00".format(loH.hour24)}     \u00B7     " + (if (rain <= 0) "No rain expected" else "Rain chance up to $rain%"), style = t(14, c = Soft))
        }
    }
}

@Composable
private fun HourCard(h: HourUi, now: Boolean, modifier: Modifier, onFocus: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Brand.Ivory else if (h.day) Color(0x40000000) else Color(0x66000000), tween(110), label = "card")
    val fg = if (focused) Brand.Ink else Snow
    Column(
        modifier.width(78.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.focusable().background(bg, RoundedCornerShape(14.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(if (now) "Now" else "%02d".format(h.hour24), style = t(13, FontWeight.SemiBold, if (focused) Brand.Ink else Soft))
        Canvas(Modifier.size(34.dp)) { drawWeather(h.kind, h.day, Offset(size.width / 2, size.height / 2), size.width) }
        Text(h.temp, style = t(19, FontWeight.SemiBold, fg))
        Text(h.pop.ifEmpty { " " }, style = t(12, FontWeight.Medium, if (focused) Color(0xFF1F5E99) else Rain))
    }
}

/** The temperature curve for the next 24 hours, with the chance of rain beneath it and a marker at the hour you are looking at. One canvas, 24 points. */
@Composable
private fun HourCurve(ui: WeatherUi, cursor: Int, modifier: Modifier) {
    val hours = ui.hours
    if (hours.size < 3) return
    val measurer = rememberTextMeasurer()
    val labelStyle = t(11, c = Color(0xE6FFFFFF))
    val first = hours.first().hour24
    fun idxOf(minute: Int): Int? { val d = minute / 60.0 - first; val i = if (d < 0) d + 24 else d; return if (i in 0.0..(hours.size - 1).toDouble()) i.toInt() else null }
    val sunriseI = idxOf(ui.sunriseMin); val sunsetI = idxOf(ui.sunsetMin)
    val lo = hours.minOf { it.tempC }; val hi = hours.maxOf { it.tempC }.let { if (it - lo < 2.0) lo + 2.0 else it }
    Canvas(modifier.fillMaxWidth().height(128.dp)) {
        val w = size.width; val ht = size.height; val padTop = 16f; val base = ht - 18f
        fun x(i: Int) = w * (0.012f + 0.976f * i / (hours.size - 1))
        fun y(v: Double) = padTop + (base - padTop - 16f) * (1f - ((v - lo) / (hi - lo)).toFloat())
        for ((i, hr) in hours.withIndex()) if (hr.popN > 0) drawRoundRect(Rain.copy(alpha = 0.55f), Offset(x(i) - 3f, base - (hr.popN / 100f) * 24f), Size(6f, (hr.popN / 100f) * 24f), CornerRadius(3f))
        val line = Path(); val area = Path()
        hours.forEachIndexed { i, hr ->
            val px = x(i); val py = y(hr.tempC)
            if (i == 0) { line.moveTo(px, py); area.moveTo(px, base); area.lineTo(px, py) }
            else { val pxp = x(i - 1); val pyp = y(hours[i - 1].tempC); val mx = (pxp + px) / 2; line.cubicTo(mx, pyp, mx, py, px, py); area.cubicTo(mx, pyp, mx, py, px, py) }
        }
        area.lineTo(x(hours.size - 1), base); area.close()
        drawPath(area, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x00FFFFFF)), padTop, base))
        drawPath(line, Color.White, style = Stroke(width = 3.4f, cap = StrokeCap.Round))
        for ((i, label) in listOfNotNull(sunriseI?.let { it to "↑ sunrise" }, sunsetI?.let { it to "↓ sunset" })) {
            drawLine(Brand.Gold.copy(alpha = 0.55f), Offset(x(i), padTop), Offset(x(i), base), 1.5f)
            drawText(measurer, label, Offset((x(i) + 4f).coerceAtMost(w - 74f), 0f), TextStyle(color = Brand.Gold, fontSize = 10.sp))
        }
        for (i in hours.indices step 3) drawText(measurer, hours[i].temp, Offset(x(i) - 10f, y(hours[i].tempC) - 22f), labelStyle)
        val cx = x(cursor.coerceIn(0, hours.size - 1)); val cy = y(hours[cursor.coerceIn(0, hours.size - 1)].tempC)
        drawLine(Color(0x99FFFFFF), Offset(cx, padTop), Offset(cx, base), 1.6f)
        drawCircle(Brand.Ivory, 7f, Offset(cx, cy)); drawCircle(Color(0x66000000), 3.2f, Offset(cx, cy))
    }
}

// ------------------------------------------------------------------------------------------------ Week

@Composable
private fun WeekPage(ui: WeatherUi) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Column(Modifier.fillMaxSize().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("This week", style = t(22, FontWeight.SemiBold))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            ui.days.forEachIndexed { i, d -> DayRow(d, i == 0, if (i == 0) Modifier.focusRequester(first) else Modifier) }
        }
    }
}

/** One day: name, sky, chance of rain, low, a bar showing the day's range against the week's, high. Today is marked; the focused row names its weather. */
@Composable
private fun DayRow(d: DayUi, today: Boolean, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Brand.Ivory else if (today) Color(0x59000000) else Color(0x33000000), tween(110), label = "day")
    val fg = if (focused) Brand.Ink else Snow
    val sub = if (focused) Color(0xFF3B3B40) else Soft
    Row(
        modifier.fillMaxWidth().height(42.dp).onFocusChanged { focused = it.isFocused }.focusable().background(bg, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(110.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (today) Box(Modifier.size(7.dp).background(Brand.Crimson, androidx.compose.foundation.shape.CircleShape))
            Text(d.label, style = t(16, if (today) FontWeight.Bold else FontWeight.SemiBold, fg))
        }
        Canvas(Modifier.size(28.dp)) { drawWeather(d.kind, true, Offset(size.width / 2, size.height / 2), size.width) }
        Text(d.pop.ifEmpty { "" }, Modifier.width(56.dp).padding(start = 10.dp), style = t(13, FontWeight.Medium, if (focused) Color(0xFF1F5E99) else Rain))
        Text(d.lo, Modifier.width(44.dp), style = t(16, c = sub))
        Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 6.dp)) {
            drawRoundRect(if (focused) Color(0x22000000) else Color(0x33FFFFFF), cornerRadius = CornerRadius(size.height / 2))
            val a = d.barStart * size.width; val b = d.barEnd * size.width
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF7FC2FF), Brand.Gold), a, b), Offset(a, 0f), Size(b - a, size.height), CornerRadius(size.height / 2))
        }
        Text(d.hi, Modifier.width(48.dp), style = t(16, FontWeight.SemiBold, fg), maxLines = 1)
        Text(d.text, Modifier.width(120.dp).alpha(if (focused) 1f else 0f), style = t(13, FontWeight.Medium, sub), maxLines = 1)
    }
}

// ------------------------------------------------------------------------------------------------ City picker

@Composable
private fun CityPicker(current: String, onPick: (com.rasha.omni.weather.Place) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocus() }
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
        GlassPanel(Modifier.width(340.dp).height(400.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("CHOOSE A CITY", style = t(12, FontWeight.Bold, Faint, 1.4f))
                Spacer(Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(Places, key = { it.name }) { p ->
                        var focused by remember { mutableStateOf(false) }
                        val mod = if (p.name == current) Modifier.focusRequester(first) else Modifier
                        Text(
                            p.name + if (p.name == current) "   ✓" else "",
                            mod.fillMaxWidth().onFocusChanged { focused = it.isFocused }.focusable()
                                .onKeyEvent { e -> if ((e.key == Key.DirectionCenter || e.key == Key.Enter) && e.type == KeyEventType.KeyUp) { onPick(p); true } else false }
                                .background(if (focused) Brand.Ivory else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
                            style = t(17, FontWeight.Medium, if (focused) Brand.Ink else Snow),
                        )
                    }
                }
            }
        }
    }
}
