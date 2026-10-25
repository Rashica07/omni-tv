package com.rasha.omni.library

import com.rasha.omni.common.readUpTo
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class PlayRequest(val sourceId: Long, val path: String, val title: String, val url: String, val headers: Map<String, String>, val subtitles: List<Pair<String, String?>>, val resumeMs: Long)

/** The one object the app talks to: sources, scanning, adding items, building what the player needs. */
class Library(private val ctx: Context, tmdbCredential: String) {
    val db = LibraryDb(ctx)
    val images = Images(ctx)
    val tmdb = TmdbClient(tmdbCredential).takeIf { it.available }
    private val prefs = ctx.getSharedPreferences("cinema", Context.MODE_PRIVATE)
    private val _scan = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** Live text per source while it is scanning; an entry disappears when the scan ends. */
    val scanning: StateFlow<Map<Long, String>> = _scan.asStateFlow()
    private val _reports = MutableStateFlow<Map<Long, ScanReport>>(emptyMap())
    val reports: StateFlow<Map<Long, ScanReport>> = _reports.asStateFlow()

    fun sourceOf(row: SourceRow): MediaSource = when (row.kind) {
        SourceKind.Lan -> LanSource(row.base, prefs.getString("pin_${row.id}", "") ?: "")
        SourceKind.Local -> LocalSource(File(row.base))
        SourceKind.Archive -> ArchiveSource()
    }

    suspend fun addLan(name: String, hostPort: String, pin: String): String? = withContext(Dispatchers.IO) {
        if (!Regex("""[A-Za-z0-9.\-]+(:\d{1,5})?""").matches(hostPort)) return@withContext "Enter an address like 192.168.1.183:8765"
        if (!Regex("""\d{8}""").matches(pin)) return@withContext "The PIN is 8 digits"
        val base = "http://$hostPort"
        LanSource(base, pin).check()?.let { return@withContext "Could not connect: $it" }
        val id = db.addSource(name.ifBlank { hostPort }, SourceKind.Lan, base)
        prefs.edit().putString("pin_$id", pin).apply()
        null
    }

    suspend fun addLocal(name: String, path: String): String? = withContext(Dispatchers.IO) {
        val f = File(path)
        if (!f.isDirectory || f.listFiles() == null) return@withContext "Cannot read that folder (is the storage permission granted?)"
        db.addSource(name.ifBlank { f.name }, SourceKind.Local, f.canonicalPath); null
    }

    fun remove(row: SourceRow) { db.removeSource(row.id); prefs.edit().remove("pin_${row.id}").apply() }

    suspend fun scan(row: SourceRow) {
        if (row.kind == SourceKind.Archive) return                 // Archive films are added one by one; there is nothing to list
        if (_scan.value.containsKey(row.id)) return
        _scan.value = _scan.value + (row.id to "Starting…")
        try {
            val api = apiOf(row)
            val r = if (api != null && api.available()) syncFromServer(row, api) { t -> _scan.value = _scan.value + (row.id to t) }
                    else Indexer(db, tmdb).scan(row.id, sourceOf(row)) { t -> _scan.value = _scan.value + (row.id to t) }
            _reports.value = _reports.value + (row.id to r)
        } catch (e: Exception) { _reports.value = _reports.value + (row.id to ScanReport(errors = listOf(e.message ?: e.javaClass.simpleName), note = "Scan failed")) }
        finally { _scan.value = _scan.value - row.id }
    }

    private fun apiOf(row: SourceRow): ServerApi? = if (row.kind == SourceKind.Lan) ServerApi(row.base, prefs.getString("pin_${row.id}", "") ?: "") else null

    /**
     * A server that runs the library API already scanned, parsed and matched its films, so the TV does not repeat any of that (and needs no TMDB credential for them):
     * it takes the list, keeps what it already had (so "added" dates and history stay), refreshes the details, and pulls watch positions that are newer than its own.
     */
    private suspend fun syncFromServer(row: SourceRow, api: ServerApi, onProgress: (String) -> Unit): ScanReport = withContext(Dispatchers.IO) {
        onProgress("Reading the server library…")
        val films = try { api.movies() } catch (e: Exception) { return@withContext ScanReport(errors = listOf(e.message ?: e.javaClass.simpleName), note = "Could not read the server library") }
        val known = db.files(row.id)
        if (!Scan.removalAllowed(known.size, films.size)) return@withContext ScanReport(note = "The server listed no films, so nothing was removed (is its drive connected?)")
        val have = HashSet<String>(); var added = 0; var matched = 0
        for (f in films) {
            val path = ApiPath.of(f.id); have += path
            if (path !in known) { db.putFile(row.id, FileEntry(path, f.durationMs, f.added)); db.putItem(row.id, path, Parsed.Movie(f.title, f.year), 0); added++ }
            if (f.matched && f.tmdbId > 0) { db.matchMovie(row.id, path, MovieMeta(f.tmdbId, f.title, f.year, f.overview, f.tmdbPoster, f.tmdbBackdrop, f.rating, f.runtimeMin, f.genres)); matched++ }
        }
        val gone = known.keys.filter { ApiPath.isApi(it) && it !in have }
        gone.forEach { db.removeFile(row.id, it) }
        onProgress("Syncing watch positions…")
        try { api.progress().forEach { p -> if (ApiPath.of(p.movieId) in have) db.applyRemoteProgress(row.id, ApiPath.of(p.movieId), p.posMs, p.durMs, p.watched, p.updatedSec) } } catch (e: Exception) { }
        ScanReport(found = films.size, added = added, removed = gone.size, scraped = matched, unmatched = films.size - matched, note = "Library read from the server")
    }

    private val pushes = Executors.newSingleThreadExecutor()
    /** Tells the server where this TV stopped, for films that come from the server library. Fire and forget. */
    fun pushProgress(sourceId: Long, path: String, posMs: Long, durMs: Long) {
        if (!ApiPath.isApi(path)) return
        pushes.execute { db.sources().firstOrNull { it.id == sourceId }?.let { apiOf(it) }?.putProgress(ApiPath.id(path), posMs, durMs, "tv") }
    }

    /** Adds one Internet Archive film by identifier, only if it carries a public-domain or open licence and has a playable file. Returns an error text, or null. */
    suspend fun addArchive(identifier: String): String? = withContext(Dispatchers.IO) {
        if (!Regex("""[A-Za-z0-9._\-]{2,100}""").matches(identifier)) return@withContext "That is not an Internet Archive identifier"
        val json = try { val c = URL("https://archive.org/metadata/$identifier").openConnection() as HttpURLConnection; c.connectTimeout = 6000; c.readTimeout = 10000; try { check(c.responseCode == 200) { "http ${c.responseCode}" }; c.inputStream.readUpTo(2_000_000).toString(Charsets.UTF_8) } finally { c.disconnect() } } catch (e: Exception) { return@withContext "Could not reach the Internet Archive" }
        val item = Archive.parseMetadata(json) ?: return@withContext "No such item"
        if (!Archive.openLicence(item.license)) return@withContext "This item has no open or public-domain licence, so it is not added"
        val video = item.video ?: return@withContext "No playable MP4 in this item"
        val src = db.sources().firstOrNull { it.kind == SourceKind.Archive }?.id ?: db.addSource("Internet Archive", SourceKind.Archive, "archive.org")
        val path = "${item.id}/$video"
        db.putFile(src, FileEntry(path, 0, 0))
        db.putItem(src, path, Parsed.Movie(item.title, item.year), 0)
        item.subtitle?.let { prefs.edit().putString("sub_$path", "${item.id}/$it").apply() }
        tmdb?.let { m -> try { Matching.best(m.searchMovie(item.title, item.year), item.title, item.year)?.id?.let { id -> m.movie(id)?.let { db.matchMovie(src, path, it) } } } catch (e: Exception) { } }
        null
    }

    // ---- availability: is a source reachable right now? Cached for a minute; probes are short and run in parallel, so callers (the launcher's provider) never wait long.
    private val health = ConcurrentHashMap<Long, Pair<Availability, Long>>()
    private val probes = Executors.newCachedThreadPool()

    fun probe(row: SourceRow): Availability = try {
        when (row.kind) {
            SourceKind.Local -> if (File(row.base).let { it.isDirectory && it.listFiles() != null }) Availability.PLAYABLE else Availability.OFFLINE
            SourceKind.Lan -> {
                val c = URL(row.base + "/media/").openConnection() as HttpURLConnection
                c.connectTimeout = 1200; c.readTimeout = 1500; c.setRequestProperty("Authorization", "Bearer " + (prefs.getString("pin_${row.id}", "") ?: ""))
                try { when (c.responseCode) { 200 -> Availability.PLAYABLE; 401 -> Availability.ACCOUNT; else -> Availability.OFFLINE } } finally { c.disconnect() }
            }
            SourceKind.Archive -> {
                val c = URL("https://archive.org/").openConnection() as HttpURLConnection
                c.connectTimeout = 1500; c.readTimeout = 1500; c.requestMethod = "HEAD"
                try { if (c.responseCode in 200..399) Availability.PLAYABLE else Availability.OFFLINE } finally { c.disconnect() }
            }
        }
    } catch (e: Exception) { Availability.OFFLINE }

    /** Refreshes stale health entries in parallel (bounded to ~2 s in total) and returns the current picture. */
    fun availabilities(maxAgeMs: Long = 60_000): Map<Long, Availability> {
        val now = System.currentTimeMillis()
        val rows = db.sources()
        val stale = rows.filter { (health[it.id]?.second ?: 0) + maxAgeMs < now }
        val futures = stale.map { r -> probes.submit<Pair<Long, Availability>> { r.id to probe(r) } }
        futures.forEach { f -> try { val (id, a) = f.get(2200, TimeUnit.MILLISECONDS); health[id] = a to System.currentTimeMillis() } catch (e: Exception) { f.cancel(true) } }
        return rows.associate { it.id to (health[it.id]?.first ?: Availability.OFFLINE) }
    }

    /** All titles with honest availability. Blocking; call off the main thread. */
    fun titles(): List<Title> {
        val av = availabilities()
        val sources = db.sources().associateBy { it.id }
        val movies = Catalogue.movies(db.movies(5000), { sources[it]?.kind }, { av[it] ?: Availability.OFFLINE })
        val shows = Catalogue.shows(db.shows(), { db.episodes(it) }, { sources[it]?.kind }, { av[it] ?: Availability.OFFLINE }, { db.showTmdbId(it) ?: 0 })
        return movies + shows
    }

    suspend fun playRequest(e: Entry): PlayRequest = withContext(Dispatchers.IO) {
        val row = db.sources().first { it.id == e.sourceId }
        val src = sourceOf(row)
        val subs: List<Pair<String, String?>> = if (row.kind == SourceKind.Archive) {
            listOfNotNull(prefs.getString("sub_${e.path}", null)?.let { src.playUrl(it) to "en" })
        } else if (ApiPath.isApi(e.path)) {
            try { apiOf(row)?.subtitles(ApiPath.id(e.path)) ?: emptyList() } catch (ex: Exception) { emptyList() }
        } else try {
            val dir = e.path.substringBeforeLast('/', "")
            val sib = src.list(dir).filter { !it.isDir }.map { (if (dir.isEmpty()) "" else "$dir/") + it.name }
            Sidecars.subtitles(e.path, sib).map { src.playUrl(it.path) to it.language }
        } catch (ex: Exception) { emptyList() }
        PlayRequest(e.sourceId, e.path, if (e.episode) "${e.showTitle ?: ""} S%02dE%02d".format(e.season, e.number) else e.title, src.playUrl(e.path), src.headers(), subs, PlayRules.resumeAt(e.resumeMs, e.durationMs) ?: 0L)
    }
}
