package com.rasha.omni.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a scan is doing, for the Sources screen. Nothing here is invented: counts come from the source and the database. */
data class ScanReport(val found: Int = 0, val added: Int = 0, val removed: Int = 0, val scraped: Int = 0, val unmatched: Int = 0, val errors: List<String> = emptyList(), val note: String? = null)

/**
 * Scan → parse names → store → scrape, in that order, so a film is in the library (by its file name) the moment it is found and gets its poster and
 * overview as the scraper reaches it. If the network or TMDB is down, items simply stay unmatched and are tried again at the next scan.
 */
class Indexer(private val db: LibraryDb, private val meta: MetadataProvider?) {
    suspend fun scan(sourceId: Long, source: MediaSource, onProgress: (String) -> Unit = {}): ScanReport = withContext(Dispatchers.IO) {
        val errors = ArrayList<String>()
        onProgress("Looking for files…")
        val all = Scan.walk(source, onError = { d, e -> errors += "${d.ifEmpty { "/" }}: ${e.message}" })
        if (all.isEmpty() && errors.isNotEmpty()) return@withContext ScanReport(errors = errors, note = "Could not read the source")      // never wipe a library because a drive was unplugged
        val videos = Scan.videos(all)
        val known = db.files(sourceId)
        val plan = Scan.diff(known, videos)
        val siblings = all.map { it.path }
        val safe = Scan.removalAllowed(known.size, videos.size)
        if (safe) for (gone in plan.removed) db.removeFile(sourceId, gone)
        val fresh = plan.added + plan.changed
        for ((i, f) in fresh.withIndex()) {
            onProgress("Adding ${i + 1}/${fresh.size}")
            val p = MediaName.parse(f.path)
            val showId = if (p is Parsed.Episode) db.showFor(Matching.norm(p.show), p.show, null) else 0L
            db.putFile(sourceId, f)
            db.putItem(sourceId, f.path, p, showId)
        }
        var scraped = 0
        if (meta != null) scraped = scrape(sourceId, source, siblings, onProgress, errors)
        ScanReport(videos.size, plan.added.size, if (safe) plan.removed.size else 0, scraped, db.unmatched(sourceId).size, errors,
            if (safe) null else "The source returned no videos, so nothing was removed (is it connected?)")
    }

    private suspend fun scrape(sourceId: Long, source: MediaSource, siblings: List<String>, onProgress: (String) -> Unit, errors: MutableList<String>): Int {
        val m = meta ?: return 0
        var done = 0
        val todo = db.unmatched(sourceId)
        // shows first, once each
        val showIds = HashMap<Long, Int?>()
        for ((i, t) in todo.withIndex()) {
            val (path, isEp, nameYear) = t
            onProgress("Identifying ${i + 1}/${todo.size}")
            try {
                if (!isEp) {
                    val nfo = Sidecars.nfo(path, siblings)?.let { n -> try { NfoParser.parse(source.read(n).toString(Charsets.UTF_8)) } catch (e: Exception) { null } }          // an id in a Kodi .nfo beats any name guess
                    var id: Int? = nfo?.tmdbId ?: nfo?.imdbId?.let { m.findMovieByImdb(it) }
                    if (id == null) {
                        val (title, year) = nameYear
                        id = Matching.best(m.searchMovie(title, year), title, year)?.id
                            ?: year?.let { Matching.best(m.searchMovie("$title $it", null), "$title $it", null)?.id }          // "Blade Runner 2049.mkv": the number was part of the name
                    }
                    id?.let { m.movie(it) }?.let { db.matchMovie(sourceId, path, it); done++ }
                } else {
                    val parsed = MediaName.parse(path) as? Parsed.Episode ?: continue
                    val showId = db.showFor(Matching.norm(parsed.show), parsed.show, null)
                    val tmdbShow = showIds.getOrPut(showId) {
                        db.showTmdbId(showId) ?: Matching.best(m.searchShow(parsed.show, null), parsed.show, null)?.id?.also { id -> m.show(id)?.let { db.matchShow(showId, it) } }
                    } ?: continue
                    m.season(tmdbShow, parsed.season).firstOrNull { it.episode == parsed.episode }?.let { db.matchEpisode(sourceId, path, it); done++ }
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { errors += "scrape ${path.substringAfterLast('/')}: ${e.message}"; if (errors.size > 5) break }
        }
        return done
    }
}
