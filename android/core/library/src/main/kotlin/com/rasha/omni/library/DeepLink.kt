package com.rasha.omni.library

/** Where a link into Cinema points. Built only from validated parts: ids go through [MediaId.parse], source ids are plain small integers. */
sealed class Route {
    object Home : Route()
    data class Movie(val id: MediaId) : Route()
    data class Show(val id: MediaId) : Route()
    data class Episode(val id: MediaId) : Route()
    data class Source(val id: Long) : Route()
    data class Play(val id: MediaId) : Route()
    /** A link that was well-formed enough to read but refers to nothing valid; Cinema says so instead of opening a blank screen. */
    data class Invalid(val why: String) : Route()
}

object DeepLink {
    const val SCHEME = "omni-cinema"

    fun parse(uri: String?): Route {
        if (uri == null || uri.length > 120) return Route.Invalid("The link is missing or too long")
        if (!uri.startsWith("$SCHEME://")) return Route.Invalid("Not an OMNI Cinema link")
        val rest = uri.removePrefix("$SCHEME://")
        if (rest.any { it.code < 0x21 || it.code > 0x7e } || rest.contains('?') || rest.contains('#') || rest.contains("..") || rest.contains('\\')) return Route.Invalid("The link contains characters that are not allowed")
        val verb = rest.substringBefore('/')
        val arg = if (rest.contains('/')) rest.substringAfter('/') else ""
        fun media(): MediaId? = MediaId.parse(arg)
        return when (verb) {
            "home" -> if (arg.isEmpty()) Route.Home else Route.Invalid("Unexpected text after home")
            "movie" -> media().let { if (it is MediaId.Movie || it is MediaId.File) Route.Movie(it) else Route.Invalid("That is not a movie id") }
            "show" -> media().let { if (it is MediaId.Show || it is MediaId.File) Route.Show(it) else Route.Invalid("That is not a series id") }
            "episode" -> media().let { if (it is MediaId.Episode || it is MediaId.File) Route.Episode(it) else Route.Invalid("That is not an episode id") }
            "play" -> media()?.let { Route.Play(it) } ?: Route.Invalid("That is not a valid title id")
            "source" -> arg.takeIf { Regex("[1-9]\\d{0,9}").matches(it) }?.toLongOrNull()?.let { Route.Source(it) } ?: Route.Invalid("That is not a valid source")
            else -> Route.Invalid("Unknown link type")
        }
    }

    fun movie(id: MediaId) = "$SCHEME://movie/${id.text}"
    fun show(id: MediaId) = "$SCHEME://show/${id.text}"
    fun play(id: MediaId) = "$SCHEME://play/${id.text}"
    fun forTitle(t: Title) = when (t.kind) { "show" -> show(t.id); else -> movie(t.id) }
}
