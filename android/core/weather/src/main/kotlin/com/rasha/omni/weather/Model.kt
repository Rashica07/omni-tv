package com.rasha.omni.weather

enum class Kind { Clear, Partly, Cloud, Fog, Rain, Snow, Storm, Unknown }

/** WMO weather interpretation codes (Open-Meteo) reduced to the kinds the app can draw. */
fun kindOf(wmo: Int): Kind = when {
    wmo == 0 -> Kind.Clear
    wmo == 1 || wmo == 2 -> Kind.Partly
    wmo == 3 -> Kind.Cloud
    wmo == 45 || wmo == 48 -> Kind.Fog
    wmo in 51..67 || wmo in 80..82 -> Kind.Rain
    wmo in 71..77 || wmo == 85 || wmo == 86 -> Kind.Snow
    wmo in 95..99 -> Kind.Storm
    else -> Kind.Unknown
}

fun Kind.label(): String = when (this) {
    Kind.Clear -> "Clear"; Kind.Partly -> "Partly cloudy"; Kind.Cloud -> "Cloudy"; Kind.Fog -> "Fog"
    Kind.Rain -> "Rain"; Kind.Snow -> "Snow"; Kind.Storm -> "Thunderstorm"; Kind.Unknown -> "Weather"
}

data class Place(val name: String, val lat: Double, val lon: Double)

/** City-centre coordinates the owner picks from (Civate looked up with Open-Meteo's own geocoder). No location permission is used. */
val Places = listOf(
    Place("Civate", 45.82808, 9.34294), Place("Rome", 41.9028, 12.4964), Place("Milan", 45.4642, 9.19), Place("Naples", 40.8518, 14.2681),
    Place("Turin", 45.0703, 7.6869), Place("Bologna", 44.4949, 11.3426), Place("Florence", 43.7696, 11.2558),
    Place("Tirana", 41.3275, 19.8187), Place("Durrës", 41.3246, 19.4565), Place("Pristina", 42.6629, 21.1655), Place("Prizren", 42.2139, 20.7397),
)

data class Hour(val hour: Int, val code: Int, val pop: Int, val day: Boolean, val temp: Double)

data class Day(val date: String, val code: Int, val pop: Int, val min: Double, val max: Double, val uv: Double, val sunrise: String, val sunset: String)

data class Forecast(
    val temp: Double, val feels: Double, val humidity: Int, val code: Int, val day: Boolean, val wind: Double, val windDir: Double,
    val pressure: Double, val cloud: Int, val hours: List<Hour>, val days: List<Day>,
)

/** A forecast with its provenance: when it was fetched, whether it came from the cache, and why a refresh failed (if it did). */
data class Loaded(val forecast: Forecast, val fetchedAtMs: Long, val fromCache: Boolean, val refreshError: String? = null)
