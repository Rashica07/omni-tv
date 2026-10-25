package com.rasha.omni.weather

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Open-Meteo (open-meteo.com): documented HTTP API, no key for non-commercial use, CC BY 4.0 data (attribution is shown in the app). */
object OpenMeteo {
    fun url(p: Place): String = String.format(
        Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,surface_pressure,precipitation,cloud_cover" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max&timezone=auto&forecast_days=7",
        p.lat, p.lon,
    )

    /** Parses a response. Throws on a malformed body so the caller keeps the last good forecast instead of showing garbage. */
    fun parse(body: String): Forecast {
        val d = JSONObject(body)
        val cur = d.getJSONObject("current")
        val dd = d.getJSONObject("daily")
        val dt = dd.getJSONArray("time")
        val sr = dd.getJSONArray("sunrise")
        val ss = dd.getJSONArray("sunset")
        val days = (0 until dt.length()).map { i ->
            Day(
                date = dt.getString(i), code = dd.getJSONArray("weather_code").optInt(i, -1), pop = dd.getJSONArray("precipitation_probability_max").optInt(i, 0),
                min = dd.getJSONArray("temperature_2m_min").getDouble(i), max = dd.getJSONArray("temperature_2m_max").getDouble(i),
                uv = dd.getJSONArray("uv_index_max").optDouble(i, -1.0), sunrise = sr.optString(i, ""), sunset = ss.optString(i, ""),
            )
        }
        val h = d.getJSONObject("hourly")
        val ht: JSONArray = h.getJSONArray("time")
        val now = cur.optString("time", "")
        val nowHour = if (now.length >= 13) now.substring(0, 13) else ""
        val start = (0 until ht.length()).firstOrNull { ht.getString(it).startsWith(nowHour) } ?: 0
        val hours = ArrayList<Hour>()
        var i = start
        while (i < ht.length() && hours.size < 24) {
            val t = ht.getString(i)
            val hhmm = t.substring(11, 16)
            val di = days.indexOfFirst { it.date == t.substring(0, 10) }
            val day = if (di >= 0 && days[di].sunrise.length >= 16 && days[di].sunset.length >= 16) hhmm >= days[di].sunrise.substring(11, 16) && hhmm < days[di].sunset.substring(11, 16) else true
            hours += Hour(t.substring(11, 13).toInt(), h.getJSONArray("weather_code").optInt(i, -1), h.getJSONArray("precipitation_probability").optInt(i, 0), day, h.getJSONArray("temperature_2m").getDouble(i))
            i++
        }
        return Forecast(
            temp = cur.getDouble("temperature_2m"), feels = cur.optDouble("apparent_temperature", cur.getDouble("temperature_2m")),
            humidity = cur.optInt("relative_humidity_2m", -1), code = cur.optInt("weather_code", -1), day = cur.optInt("is_day", 1) == 1,
            wind = cur.optDouble("wind_speed_10m", 0.0), windDir = cur.optDouble("wind_direction_10m", 0.0), pressure = cur.optDouble("surface_pressure", 0.0),
            cloud = cur.optInt("cloud_cover", -1), hours = hours, days = days,
        )
    }
}
