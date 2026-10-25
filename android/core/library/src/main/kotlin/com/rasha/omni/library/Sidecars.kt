package com.rasha.omni.library

data class Sidecar(val path: String, val language: String?, val forced: Boolean)

/** Files that belong to a video by name, the way Kodi pairs them: "Film (2001).srt", "Film (2001).en.srt", "Film (2001).eng.forced.srt", plus folder artwork and nfo. */
object Sidecars {
    private val subExt = setOf("srt", "vtt", "ass", "ssa")
    private val lang = Regex("""^[a-z]{2,3}$""")

    fun subtitles(videoPath: String, siblings: Collection<String>): List<Sidecar> {
        val dir = videoPath.substringBeforeLast('/', "")
        val base = MediaName.stripExt(videoPath.substringAfterLast('/'))
        return siblings.mapNotNull { p ->
            if (p.substringBeforeLast('/', "") != dir) return@mapNotNull null
            val n = p.substringAfterLast('/')
            if (MediaName.extension(n) !in subExt) return@mapNotNull null
            val stem = MediaName.stripExt(n)
            if (!stem.equals(base, true) && !stem.startsWith("$base.", true)) return@mapNotNull null
            val tags = stem.substring(base.length).split('.').filter { it.isNotEmpty() }.map { it.lowercase() }
            Sidecar(p, tags.firstOrNull { lang.matches(it) && it != "forced" }, "forced" in tags)
        }.sortedWith(compareBy({ it.forced }, { it.language ?: "~" }, { it.path }))
    }

    /** The nfo for a video: "Name.nfo" next to it, else "movie.nfo" in its folder. */
    fun nfo(videoPath: String, siblings: Collection<String>): String? {
        val dir = videoPath.substringBeforeLast('/', "")
        val own = (if (dir.isEmpty()) "" else "$dir/") + MediaName.stripExt(videoPath.substringAfterLast('/')) + ".nfo"
        val folder = (if (dir.isEmpty()) "" else "$dir/") + "movie.nfo"
        return siblings.firstOrNull { it.equals(own, true) } ?: siblings.firstOrNull { it.equals(folder, true) }
    }

    /** Local artwork in the video's folder, if any: poster/folder/cover and fanart/backdrop, or "<name>-poster.jpg". */
    fun poster(videoPath: String, siblings: Collection<String>): String? = art(videoPath, siblings, listOf("poster", "folder", "cover"))
    fun fanart(videoPath: String, siblings: Collection<String>): String? = art(videoPath, siblings, listOf("fanart", "backdrop", "background"))

    private fun art(videoPath: String, siblings: Collection<String>, names: List<String>): String? {
        val dir = videoPath.substringBeforeLast('/', "")
        val base = MediaName.stripExt(videoPath.substringAfterLast('/'))
        val prefix = if (dir.isEmpty()) "" else "$dir/"
        val wanted = names.flatMap { n -> listOf("jpg", "jpeg", "png").flatMap { e -> listOf("$prefix$n.$e", "$prefix$base-$n.$e") } }
        return wanted.firstNotNullOfOrNull { w -> siblings.firstOrNull { it.equals(w, true) } }
    }
}
