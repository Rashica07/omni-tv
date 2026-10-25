package com.rasha.omni.library

import java.security.MessageDigest

/**
 * Stable media ids, shared by the launcher and Cinema ("the same title, whatever source provides it").
 *   omni:movie:tmdb:<id>            a film TMDB identified (all copies of it, from any source, are one title)
 *   omni:show:tmdb:<id>             a series
 *   omni:ep:tmdb:<showId>:<s>:<e>   an episode
 *   omni:file:<16 hex>              a file nothing identified: honest, no merging, no metadata
 */
sealed class MediaId(val text: String) {
    class Movie(val tmdb: Int) : MediaId("omni:movie:tmdb:$tmdb")
    class Show(val tmdb: Int) : MediaId("omni:show:tmdb:$tmdb")
    class Episode(val showTmdb: Int, val season: Int, val episode: Int) : MediaId("omni:ep:tmdb:$showTmdb:$season:$episode")
    class File(val hash: String) : MediaId("omni:file:$hash")

    override fun toString() = text
    override fun equals(other: Any?) = other is MediaId && other.text == text
    override fun hashCode() = text.hashCode()

    companion object {
        private val re = Regex("""^omni:(movie|show):tmdb:([1-9]\d{0,8})$|^omni:ep:tmdb:([1-9]\d{0,8}):(\d{1,3}):(\d{1,4})$|^omni:file:([0-9a-f]{16})$""")

        /** Parses and validates an id from outside the process. Anything that is not exactly one of the forms above is refused (null). */
        fun parse(s: String?): MediaId? {
            if (s == null || s.length > 64) return null
            val m = re.matchEntire(s) ?: return null
            val g = m.groupValues
            return when {
                g[1] == "movie" -> Movie(g[2].toInt())
                g[1] == "show" -> Show(g[2].toInt())
                g[3].isNotEmpty() -> Episode(g[3].toInt(), g[4].toInt(), g[5].toInt())
                else -> File(g[6])
            }
        }

        fun fileId(sourceKind: SourceKind, sourceId: Long, path: String): MediaId =
            File(MessageDigest.getInstance("SHA-1").digest("${sourceKind.name}|$sourceId|$path".toByteArray()).take(8).joinToString("") { "%02x".format(it) })

        /** The id of an entry as the screens and the launcher see it. */
        fun of(e: Entry, kind: SourceKind): MediaId = when {
            !e.episode && e.tmdbId > 0 -> Movie(e.tmdbId)
            e.episode && e.showTmdbId > 0 && e.season > 0 && e.number > 0 -> Episode(e.showTmdbId, e.season, e.number)
            else -> fileId(kind, e.sourceId, e.path)
        }
    }
}
