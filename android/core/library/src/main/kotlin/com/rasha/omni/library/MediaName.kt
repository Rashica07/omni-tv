package com.rasha.omni.library

/** What a file (or folder) name says about a video. Like Kodi's scanner, only names and folders are used here; the scrapers decide the rest. */
sealed class Parsed {
    data class Movie(val title: String, val year: Int?) : Parsed()
    data class Episode(val show: String, val season: Int, val episode: Int, val lastEpisode: Int = episode, val title: String? = null) : Parsed()
}

object MediaName {
    val VideoExt = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "mpg", "mpeg", "wmv")

    private val junk = Regex("""(?i)^(2160p|1080[pi]|720p|576p|480p|360p|4k|uhd|hdr\d*|bluray|blu-ray|bdrip|brrip|dvdrip|dvd|webrip|web-dl|web|hdtv|hdrip|x264|x265|h264|h265|hevc|xvid|divx|aac\d*|ac3|dts|ddp?\d*|atmos|remux|proper|repack|extended|unrated|remastered|internal|multi|subbed|dubbed|10bit|8bit|amzn|nf|yify|yts|rarbg)$""")
    private val sxxexx = Regex("""(?i)\bS(\d{1,2})[ ._-]?E(\d{1,3})(?:[ ._]?(?:E|-[ ._]?E?)(\d{1,3})(?!\d|p))?""")
    private val nxnn = Regex("""(?i)(?<![\dA-Za-z])(\d{1,2})x(\d{2,3})(?![\dA-Za-z])""")
    private val wordy = Regex("""(?i)\bseason[ ._-]*(\d{1,2})[ ._-]*episode[ ._-]*(\d{1,3})""")
    private val seasonFolder = Regex("""(?i)^(?:season|series|staffel|stagione)[ ._-]*(\d{1,2})$|^s(\d{1,2})$""")
    private val yearRe = Regex("""(?<!\d)(19\d{2}|20\d{2})(?!\d)""")

    fun extension(name: String) = name.substringAfterLast('.', "").lowercase()
    fun isVideo(name: String) = extension(name) in VideoExt && !name.startsWith(".")
    fun stripExt(name: String) = if (name.contains('.') && extension(name).length in 2..4) name.substringBeforeLast('.') else name

    /** Words of a name, with separators turned into spaces. */
    private fun clean(s: String) = s.replace(Regex("""[._]+"""), " ").replace(Regex("""\[[^\]]*]"""), " ").replace(Regex("""\s+"""), " ").trim(' ', '-', '.', '_')

    /** [path] is relative to the source root, e.g. "Shows/Seinfeld/Season 02/Seinfeld S02E03 The Revenge.mkv". */
    fun parse(path: String): Parsed {
        val parts = path.split('/').filter { it.isNotEmpty() }
        val file = stripExt(parts.last())
        val folders = parts.dropLast(1)
        episodeOf(file)?.let { (s, e, last, rest) ->
            val before = clean(rest.first)
            val show = before.ifEmpty { showFromFolders(folders) ?: "" }
            if (show.isNotEmpty()) return Parsed.Episode(show, s, e, last, clean(rest.second).takeIf { it.isNotEmpty() })
        }
        // folder-only layout: "Show/Season 2/03 - Title.mkv"
        val sf = folders.lastOrNull()?.let { seasonFolder.matchEntire(it.trim()) }
        if (sf != null && folders.size >= 2) {
            val n = Regex("""^(\d{1,3})(?!\d)""").find(file)?.value?.toIntOrNull()
            if (n != null) return Parsed.Episode(clean(folders[folders.size - 2]), (sf.groupValues[1].ifEmpty { sf.groupValues[2] }).toInt(), n, n, clean(file.replaceFirst(Regex("""^\d{1,3}[ ._-]*"""), "")).takeIf { it.isNotEmpty() })
        }
        return movie(file, folders)
    }

    private fun showFromFolders(folders: List<String>): String? {
        val f = folders.filterNot { seasonFolder.matches(it.trim()) }
        return f.lastOrNull()?.let { clean(it) }?.takeIf { it.isNotEmpty() }
    }

    private data class Ep(val s: Int, val e: Int, val last: Int, val rest: Pair<String, String>)

    private fun episodeOf(file: String): Ep? {
        sxxexx.find(file)?.let { m ->
            val e = m.groupValues[2].toInt(); val l = m.groupValues[3].toIntOrNull()?.takeIf { it >= e } ?: e
            return Ep(m.groupValues[1].toInt(), e, l, file.substring(0, m.range.first) to file.substring(m.range.last + 1))
        }
        wordy.find(file)?.let { m -> val e = m.groupValues[2].toInt(); return Ep(m.groupValues[1].toInt(), e, e, file.substring(0, m.range.first) to file.substring(m.range.last + 1)) }
        nxnn.find(file)?.let { m -> val e = m.groupValues[2].toInt(); return Ep(m.groupValues[1].toInt(), e, e, file.substring(0, m.range.first) to file.substring(m.range.last + 1)) }
        return null
    }

    private fun movie(file: String, folders: List<String>): Parsed.Movie {
        // a "Title (Year)" folder holding a generically named file ("movie.mkv", "video.mp4") names the film
        val generic = Regex("""(?i)^(movie|video|film|main|feature|title\d*|\d+)$""")
        val src = if (generic.matches(file.trim()) && folders.isNotEmpty()) folders.last() else file
        val parenYear = Regex("""[(\[](19\d{2}|20\d{2})[)\]]""").findAll(src).lastOrNull()
        val words = clean(src)
        if (parenYear != null) {
            val title = clean(src.substring(0, parenYear.range.first)).ifEmpty { clean(src) }
            return Parsed.Movie(title.ifEmpty { words }, parenYear.groupValues[1].toInt())
        }
        // bare years: the film's year is the last bare year (not the first word) that comes before the quality junk and ends the title, so "2012.2009" is the film 2012 from 2009
        // and "Blade Runner 2049 Final Cut" keeps 2049 as part of its title.
        val toks = words.split(' ')
        val junkAt = toks.indexOfFirst { junk.matches(it) }.let { if (it > 0) it else toks.size }
        var year: Int? = null
        var cut = junkAt
        for (i in 1 until junkAt) if (yearRe.matches(toks[i]) && i == junkAt - 1) { year = toks[i].toInt(); cut = i }
        val title = toks.take(cut).joinToString(" ").ifEmpty { words }
        return Parsed.Movie(title, year)
    }
}
