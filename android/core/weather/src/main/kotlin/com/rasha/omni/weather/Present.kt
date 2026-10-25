package com.rasha.omni.weather

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Pure presentation rules (compass, UV words, ranges). Mirrors WeatherFormat.java in the launcher so both apps say the same things. */
object Present {
    private val COMPASS = arrayOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
    fun compass(deg: Double): String = COMPASS[((((deg % 360) + 360) % 360) / 22.5).roundToInt() % 16]
    fun uvLabel(uv: Double): String = when { uv < 0 -> ""; uv < 3 -> "Low"; uv < 6 -> "Moderate"; uv < 8 -> "High"; uv < 11 -> "Very high"; else -> "Extreme" }
    fun windWord(kmh: Double): String = when { kmh < 2 -> "Calm"; kmh < 12 -> "Light breeze"; kmh < 29 -> "Breezy"; kmh < 50 -> "Windy"; kmh < 75 -> "Strong wind"; else -> "Storm force" }
    fun temp(v: Double): String = if (v.isNaN() || v < -80 || v > 70) "" else "${v.roundToInt().let { if (it == 0) 0 else it }}°"
    fun pop(p: Int): String = if (p <= 0) "" else "$p%"
    fun clock(iso: String): String = if (iso.length >= 16 && iso[10] == 'T') iso.substring(11, 16) else ""
    fun hour(h: Int, first: Boolean, h24: Boolean): String = when {
        first -> "Now"
        h24 -> "%02d".format(h)
        else -> "${if (h % 12 == 0) 12 else h % 12}${if (h < 12) "am" else "pm"}"
    }

    /** Start and end (0..1) of a day's low-high bar within the week's range; always at least a small visible bar. */
    fun range(lo: Double, hi: Double, weekLo: Double, weekHi: Double): Pair<Float, Float> {
        val span = maxOf(1.0, weekHi - weekLo)
        val a = ((lo - weekLo) / span).toFloat().coerceIn(0f, 1f)
        var b = ((hi - weekLo) / span).toFloat().coerceIn(0f, 1f)
        if (b - a < 0.06f) b = minOf(1f, a + 0.06f)
        return a to b
    }

    fun humidityWord(h: Int) = when { h < 0 -> ""; h < 30 -> "Dry"; h < 60 -> "Comfortable"; h < 80 -> "Humid"; else -> "Very humid" }
    fun pressureWord(p: Double) = if (p < 1000) "Low" else if (p < 1020) "Normal" else "High"
}

data class HourUi(val label: String, val kind: Kind, val day: Boolean, val temp: String, val pop: String, val tempC: Double = 0.0, val popN: Int = 0, val hour24: Int = 0, val text: String = "")
data class DayUi(val label: String, val kind: Kind, val pop: String, val lo: String, val hi: String, val barStart: Float, val barEnd: Float, val text: String = "", val popN: Int = 0)
data class FactUi(val label: String, val value: String)
data class TileUi(val title: String, val value: String, val sub: String)

/** Everything the screen draws, as ready-made strings. Built once per update on a background dispatcher; composables only read it. */
data class WeatherUi(
    val place: String, val dateLine: String, val temp: String, val condition: String, val hiLo: String, val feels: String,
    val kind: Kind, val day: Boolean, val hours: List<HourUi>, val days: List<DayUi>, val tiles: List<TileUi>,
    val fetchedAtMs: Long, val fromCache: Boolean, val refreshError: String?,
    val sunriseMin: Int = 7 * 60, val sunsetMin: Int = 18 * 60, val nowMin: Int = 12 * 60, val cloudiness: Float = 0f, val facts: List<FactUi> = emptyList(),
)

object UiMapper {
    /** "07:12" → minutes since midnight, or null. */
    fun minutes(clock: String): Int? = if (clock.length == 5 && clock[2] == ':') clock.substring(0, 2).toIntOrNull()?.let { h -> clock.substring(3).toIntOrNull()?.let { m -> h * 60 + m } } else null

    /** How cloudy the sky should look: the reported cloud cover when there is one, otherwise what the weather kind implies. */
    fun cloudiness(kind: Kind, cover: Int): Float = if (cover in 0..100) cover / 100f else when (kind) { Kind.Clear -> 0.05f; Kind.Partly -> 0.4f; Kind.Cloud, Kind.Fog -> 0.9f; Kind.Rain, Kind.Snow, Kind.Storm -> 1f; Kind.Unknown -> 0.5f }

    fun map(l: Loaded, place: String, locale: Locale, h24: Boolean, now: Date = Date()): WeatherUi {
        val f = l.forecast
        val kind = kindOf(f.code)
        val dateFmt = java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale)
        val dayIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dayOut = SimpleDateFormat("EEE", locale)
        val wl = f.days.minOfOrNull { it.min } ?: 0.0
        val wh = f.days.maxOfOrNull { it.max } ?: 0.0
        val today = f.days.firstOrNull()
        return WeatherUi(
            place = place,
            dateLine = SimpleDateFormat("EEEE d MMMM", locale).format(now),
            temp = Present.temp(f.temp).removeSuffix("°"),
            condition = kind.label() + if (kind == Kind.Clear && !f.day) "  ·  Night" else "",
            hiLo = (today?.let { "H ${Present.temp(it.max)}   L ${Present.temp(it.min)}      " } ?: "") + "Feels like ${Present.temp(f.feels)}",
            feels = Present.temp(f.feels), kind = kind, day = f.day,
            hours = f.hours.take(24).mapIndexed { i, h -> HourUi(Present.hour(h.hour, i == 0, h24), kindOf(h.code), h.day, Present.temp(h.temp), Present.pop(h.pop), h.temp, h.pop, h.hour, kindOf(h.code).label()) },
            days = f.days.take(7).mapIndexed { i, d ->
                val (a, b) = Present.range(d.min, d.max, wl, wh)
                val name = if (i == 0) "Today" else runCatching { dayOut.format(dayIn.parse(d.date)!!) }.getOrDefault(d.date.takeLast(5))
                DayUi(name.replaceFirstChar { it.uppercase() }, kindOf(d.code), Present.pop(d.pop), Present.temp(d.min), Present.temp(d.max), a, b, kindOf(d.code).label(), d.pop)
            },
            tiles = listOf(
                TileUi("HUMIDITY", if (f.humidity < 0) "–" else "${f.humidity}%", Present.humidityWord(f.humidity)),
                TileUi("WIND", "${f.wind.roundToInt()} km/h", "${Present.compass(f.windDir)}  ·  ${Present.windWord(f.wind)}"),
                TileUi("PRESSURE", "${f.pressure.roundToInt()} hPa", Present.pressureWord(f.pressure)),
                TileUi("UV INDEX", today?.uv?.takeIf { it >= 0 }?.let { "%.1f".format(Locale.US, it) } ?: "–", Present.uvLabel(today?.uv ?: -1.0)),
                TileUi("SUNRISE", today?.sunrise?.let { Present.clock(it) }?.ifEmpty { "–" } ?: "–", ""),
                TileUi("SUNSET", today?.sunset?.let { Present.clock(it) }?.ifEmpty { "–" } ?: "–", if (f.cloud >= 0) "Cloud cover ${f.cloud}%" else ""),
            ),
            fetchedAtMs = l.fetchedAtMs, fromCache = l.fromCache, refreshError = l.refreshError,
            sunriseMin = today?.sunrise?.let { minutes(Present.clock(it)) } ?: 7 * 60, sunsetMin = today?.sunset?.let { minutes(Present.clock(it)) } ?: 18 * 60,
            nowMin = (f.hours.firstOrNull()?.hour ?: 12) * 60 + java.util.Calendar.getInstance().apply { time = now }.get(java.util.Calendar.MINUTE),
            cloudiness = cloudiness(kind, f.cloud),
            facts = buildList {
                add(FactUi("Wind", "${f.wind.roundToInt()} km/h ${Present.compass(f.windDir)}"))
                if (f.humidity >= 0) add(FactUi("Humidity", "${f.humidity}%"))
                val rain = f.hours.take(12).maxOfOrNull { it.pop } ?: 0
                add(FactUi("Rain, next 12 h", if (rain <= 0) "none expected" else "up to $rain%"))
                today?.uv?.takeIf { it >= 3 }?.let { add(FactUi("UV", "%.0f %s".format(Locale.US, it, Present.uvLabel(it)))) }
            },
        )
    }
}
