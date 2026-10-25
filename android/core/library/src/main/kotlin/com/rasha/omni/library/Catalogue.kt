package com.rasha.omni.library

enum class Availability { PLAYABLE, OFFLINE, ACCOUNT, METADATA }

/** One title as the launcher and Cinema's shelves see it: a film or a series, with every copy we know of. */
data class Title(
    val id: MediaId, val kind: String, val title: String, val year: Int?, val overview: String, val poster: String?, val backdrop: String?, val rating: Double,
    val sourceLabel: String, val availability: Availability, val positionMs: Long, val durationMs: Long, val watched: Boolean, val updatedAt: Long, val added: Long,
    val copies: List<Entry>, val sourceKinds: Set<SourceKind>, val runtimeMin: Int = 0, val genres: String = "",
)
data class Shelf(val id: String, val title: String, val count: Int)

object Catalogue {
    /** Order a film's copies are tried in: local/USB, then the OMNI server, then the Internet Archive. */
    private val preference = listOf(SourceKind.Local, SourceKind.Lan, SourceKind.Archive)

    fun label(kinds: Set<SourceKind>): String = when {
        kinds.size > 1 -> "${kinds.size} sources"
        kinds.firstOrNull() == SourceKind.Lan -> "OMNI server"
        kinds.firstOrNull() == SourceKind.Local -> "On this TV or USB"
        kinds.firstOrNull() == SourceKind.Archive -> "Free · Internet Archive"
        else -> ""
    }

    /** Films: copies with the same id collapse into one title; watch state is the newest across the copies, watched if any copy was finished. */
    fun movies(entries: List<Entry>, kindOf: (Long) -> SourceKind?, health: (Long) -> Availability): List<Title> {
        val groups = LinkedHashMap<MediaId, MutableList<Pair<Entry, SourceKind>>>()
        for (e in entries) { if (e.episode) continue; val k = kindOf(e.sourceId) ?: continue; groups.getOrPut(MediaId.of(e, k)) { ArrayList() } += e to k }
        return groups.map { (id, list) ->
            val best = list.sortedBy { preference.indexOf(it.second) }
            val first = best.first().first
            val kinds = list.map { it.second }.toSet()
            val newest = list.maxByOrNull { it.first.lastPlayed }!!.first
            val avail = best.map { health(it.first.sourceId) }.let { a -> if (Availability.PLAYABLE in a) Availability.PLAYABLE else a.firstOrNull() ?: Availability.OFFLINE }
            val resumable = list.filter { it.first.resumeMs > 0 && !it.first.watched }.maxByOrNull { it.first.lastPlayed }?.first
            Title(id, "movie", first.title, first.year, first.overview, first.poster, first.backdrop, first.rating, label(kinds), avail,
                resumable?.resumeMs ?: 0, (resumable ?: newest).durationMs, list.any { it.first.watched }, newest.lastPlayed, list.maxOf { it.first.added }, best.map { it.first }, kinds, first.runtimeMin, first.genres)
        }
    }

    /** Series: one title per show; its progress is the episode in progress, its availability that of its episodes' sources. */
    fun shows(rows: List<ShowRow>, episodesOf: (Long) -> List<Entry>, kindOf: (Long) -> SourceKind?, health: (Long) -> Availability, showTmdb: (Long) -> Int): List<Title> =
        rows.mapNotNull { r ->
            val eps = episodesOf(r.id); if (eps.isEmpty()) return@mapNotNull null
            val kinds = eps.mapNotNull { kindOf(it.sourceId) }.toSet()
            val tm = showTmdb(r.id)
            val id = if (tm > 0) MediaId.Show(tm) else MediaId.fileId(kinds.firstOrNull() ?: SourceKind.Local, eps.first().sourceId, "show:${r.id}")
            val cur = eps.filter { it.resumeMs > 0 && !it.watched }.maxByOrNull { it.lastPlayed }
            val avail = eps.map { health(it.sourceId) }.let { a -> if (Availability.PLAYABLE in a) Availability.PLAYABLE else a.firstOrNull() ?: Availability.OFFLINE }
            Title(id, "show", r.title, r.year, r.overview, r.poster, r.backdrop, 0.0, label(kinds), avail, cur?.resumeMs ?: 0, cur?.durationMs ?: 0, r.episodes > 0 && r.watched == r.episodes,
                eps.maxOf { it.lastPlayed }, eps.maxOf { it.added }, eps, kinds, 0, "")
        }

    /** Only shelves with something in them. Nothing is padded or invented. */
    fun shelves(all: List<Title>): List<Shelf> = buildList {
        fun add(id: String, title: String, n: Int) { if (n > 0) add(Shelf(id, title, n)) }
        add("continue", "Continue watching", select("continue", all).size)
        add("free", "Free to watch", select("free", all).size)
        add("device", "On your TV", select("device", all).size)
        add("network", "From your network", select("network", all).size)
        add("recent", "Recently added", select("recent", all).size)
    }

    fun select(shelf: String, all: List<Title>): List<Title> = when (shelf) {
        "continue" -> all.filter { it.positionMs > 0 && !it.watched }.sortedByDescending { it.updatedAt }
        "free" -> all.filter { SourceKind.Archive in it.sourceKinds }.sortedBy { it.title.lowercase() }
        "device" -> all.filter { SourceKind.Local in it.sourceKinds }.sortedBy { it.title.lowercase() }
        "network" -> all.filter { SourceKind.Lan in it.sourceKinds }.sortedBy { it.title.lowercase() }
        "recent" -> all.sortedByDescending { it.added }.take(40)
        else -> emptyList()
    }
}
