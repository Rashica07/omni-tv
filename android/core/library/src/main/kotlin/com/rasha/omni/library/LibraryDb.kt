package com.rasha.omni.library

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

enum class SourceKind { Lan, Local, Archive }
data class SourceRow(val id: Long, val name: String, val kind: SourceKind, val base: String)

/** One playable thing as the screens see it: a film, or an episode with its show. */
data class Entry(
    val sourceId: Long, val path: String, val episode: Boolean, val title: String, val year: Int?, val overview: String, val poster: String?, val backdrop: String?,
    val rating: Double, val runtimeMin: Int, val genres: String, val matched: Boolean, val showId: Long, val showTitle: String?, val season: Int, val number: Int,
    val resumeMs: Long, val durationMs: Long, val watched: Boolean, val lastPlayed: Long, val added: Long,
    val tmdbId: Int = 0, val showTmdbId: Int = 0,
)
data class ShowRow(val id: Long, val title: String, val year: Int?, val overview: String, val poster: String?, val backdrop: String?, val episodes: Int, val watched: Int)

/**
 * The library database (SQLite), shaped like Kodi's: sources → files → items (movie or episode) → shows, with watch state kept by (source, path) so it
 * survives a re-scan, a renamed source row, or a file that goes away and comes back. All access is off the main thread (callers use Dispatchers.IO).
 */
class LibraryDb(ctx: Context, name: String = "library.db") : SQLiteOpenHelper(ctx, name, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE source(id INTEGER PRIMARY KEY, name TEXT NOT NULL, kind TEXT NOT NULL, base TEXT NOT NULL)")
        db.execSQL("CREATE TABLE file(source_id INTEGER NOT NULL, path TEXT NOT NULL, size INTEGER, mtime INTEGER, PRIMARY KEY(source_id, path))")
        db.execSQL("CREATE TABLE show(id INTEGER PRIMARY KEY, key TEXT UNIQUE, title TEXT, year INTEGER, tmdb_id INTEGER, overview TEXT, poster TEXT, backdrop TEXT, genres TEXT)")
        db.execSQL("""CREATE TABLE item(source_id INTEGER NOT NULL, path TEXT NOT NULL, episode INTEGER NOT NULL, title TEXT, year INTEGER, tmdb_id INTEGER, show_id INTEGER DEFAULT 0, season INTEGER DEFAULT 0,
            number INTEGER DEFAULT 0, overview TEXT DEFAULT '', poster TEXT, backdrop TEXT, rating REAL DEFAULT 0, runtime INTEGER DEFAULT 0, genres TEXT DEFAULT '', matched INTEGER DEFAULT 0,
            added INTEGER, PRIMARY KEY(source_id, path))""")
        db.execSQL("CREATE TABLE state(source_id INTEGER NOT NULL, path TEXT NOT NULL, pos INTEGER DEFAULT 0, dur INTEGER DEFAULT 0, watched INTEGER DEFAULT 0, plays INTEGER DEFAULT 0, last INTEGER DEFAULT 0, PRIMARY KEY(source_id, path))")
        db.execSQL("CREATE INDEX item_show ON item(show_id, season, number)")
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(false) }

    // ---- sources
    fun sources(): List<SourceRow> = readableDatabase.rawQuery("SELECT id,name,kind,base FROM source ORDER BY id", null).use { c -> buildList { while (c.moveToNext()) add(SourceRow(c.getLong(0), c.getString(1), SourceKind.valueOf(c.getString(2)), c.getString(3))) } }
    fun addSource(name: String, kind: SourceKind, base: String): Long = writableDatabase.insertOrThrow("source", null, ContentValues().apply { put("name", name); put("kind", kind.name); put("base", base) })
    fun removeSource(id: Long) = writableDatabase.run {
        beginTransaction()
        try { delete("source", "id=?", arrayOf("$id")); delete("file", "source_id=?", arrayOf("$id")); delete("item", "source_id=?", arrayOf("$id")); setTransactionSuccessful() } finally { endTransaction() }
        delete("show", "id NOT IN (SELECT show_id FROM item WHERE show_id>0)", null)
    }

    // ---- files
    fun files(sourceId: Long): Map<String, FileEntry> = readableDatabase.rawQuery("SELECT path,size,mtime FROM file WHERE source_id=?", arrayOf("$sourceId")).use { c -> buildMap { while (c.moveToNext()) put(c.getString(0), FileEntry(c.getString(0), c.getLong(1), c.getLong(2))) } }
    fun putFile(sourceId: Long, f: FileEntry) { writableDatabase.insertWithOnConflict("file", null, ContentValues().apply { put("source_id", sourceId); put("path", f.path); put("size", f.size); put("mtime", f.mtime) }, SQLiteDatabase.CONFLICT_REPLACE) }
    /** Removes a vanished file and its item; its watch state is kept so history returns with the file. */
    fun removeFile(sourceId: Long, path: String) { val a = arrayOf("$sourceId", path); writableDatabase.delete("file", "source_id=? AND path=?", a); writableDatabase.delete("item", "source_id=? AND path=?", a) }

    // ---- items
    fun putItem(sourceId: Long, path: String, p: Parsed, showId: Long) {
        val v = ContentValues().apply {
            put("source_id", sourceId); put("path", path); put("added", System.currentTimeMillis())
            when (p) {
                is Parsed.Movie -> { put("episode", 0); put("title", p.title); p.year?.let { put("year", it) } }
                is Parsed.Episode -> { put("episode", 1); put("title", p.title ?: "Episode ${p.episode}"); put("show_id", showId); put("season", p.season); put("number", p.episode) }
            }
            put("matched", 0)
        }
        writableDatabase.insertWithOnConflict("item", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun matchMovie(sourceId: Long, path: String, m: MovieMeta) { writableDatabase.update("item", ContentValues().apply { put("title", m.title); put("year", m.year); put("tmdb_id", m.tmdbId); put("overview", m.overview); put("poster", m.poster); put("backdrop", m.backdrop); put("rating", m.rating); put("runtime", m.runtimeMin); put("genres", m.genres.joinToString(", ")); put("matched", 1) }, "source_id=? AND path=?", arrayOf("$sourceId", path)) }
    fun matchEpisode(sourceId: Long, path: String, e: EpisodeMeta) { writableDatabase.update("item", ContentValues().apply { put("title", e.title.ifEmpty { "Episode ${e.episode}" }); put("overview", e.overview); put("backdrop", e.still); put("matched", 1) }, "source_id=? AND path=?", arrayOf("$sourceId", path)) }
    fun unmatched(sourceId: Long): List<Triple<String, Boolean, Pair<String, Int?>>> = readableDatabase.rawQuery("SELECT i.path,i.episode,COALESCE(s.title,i.title),i.year,i.season,i.number FROM item i LEFT JOIN show s ON s.id=i.show_id WHERE i.source_id=? AND i.matched=0", arrayOf("$sourceId")).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getInt(1) == 1, c.getString(2) to (if (c.isNull(3)) null else c.getInt(3)))) } }

    fun showFor(key: String, title: String, year: Int?): Long {
        writableDatabase.insertWithOnConflict("show", null, ContentValues().apply { put("key", key); put("title", title); year?.let { put("year", it) } }, SQLiteDatabase.CONFLICT_IGNORE)
        return readableDatabase.rawQuery("SELECT id FROM show WHERE key=?", arrayOf(key)).use { it.moveToFirst(); it.getLong(0) }
    }
    fun matchShow(id: Long, m: ShowMeta) { writableDatabase.update("show", ContentValues().apply { put("title", m.title); put("year", m.year); put("tmdb_id", m.tmdbId); put("overview", m.overview); put("poster", m.poster); put("backdrop", m.backdrop); put("genres", m.genres.joinToString(", ")) }, "id=?", arrayOf("$id")) }
    fun unmatchedShows(): List<ShowRow> = shows().filter { it.poster == null && it.overview.isEmpty() }
    fun showTmdbId(id: Long): Int? = readableDatabase.rawQuery("SELECT tmdb_id FROM show WHERE id=?", arrayOf("$id")).use { if (it.moveToFirst() && !it.isNull(0)) it.getInt(0) else null }

    // ---- reading for screens
    private val sel = """SELECT i.source_id,i.path,i.episode,i.title,i.year,i.overview,i.poster,i.backdrop,i.rating,i.runtime,i.genres,i.matched,i.show_id,s.title,i.season,i.number,
        COALESCE(t.pos,0),COALESCE(t.dur,0),COALESCE(t.watched,0),COALESCE(t.last,0),i.added,COALESCE(i.tmdb_id,0),COALESCE(s.tmdb_id,0)
        FROM item i LEFT JOIN show s ON s.id=i.show_id LEFT JOIN state t ON t.source_id=i.source_id AND t.path=i.path"""
    private fun Cursor.entry() = Entry(getLong(0), getString(1), getInt(2) == 1, getString(3) ?: "", if (isNull(4)) null else getInt(4), getString(5) ?: "", getString(6), getString(7), getDouble(8), getInt(9), getString(10) ?: "", getInt(11) == 1,
        getLong(12), getString(13), getInt(14), getInt(15), getLong(16), getLong(17), getInt(18) == 1, getLong(19), getLong(20), getInt(21), getInt(22))
    private fun query(where: String, args: Array<String>, order: String, limit: Int = 500): List<Entry> = readableDatabase.rawQuery("$sel WHERE $where ORDER BY $order LIMIT $limit", args).use { c -> buildList { while (c.moveToNext()) add(c.entry()) } }

    fun movies(limit: Int = 500): List<Entry> = query("i.episode=0", emptyArray(), "i.title COLLATE NOCASE", limit)
    fun recentlyAdded(): List<Entry> = query("i.episode=0", emptyArray(), "i.added DESC", 20)
    fun continueWatching(): List<Entry> = query("t.pos>0 AND t.watched=0", emptyArray(), "t.last DESC", 20)
    fun episodes(showId: Long): List<Entry> = query("i.show_id=?", arrayOf("$showId"), "i.season, i.number")
    fun entry(sourceId: Long, path: String): Entry? = query("i.source_id=? AND i.path=?", arrayOf("$sourceId", path), "i.path", 1).firstOrNull()
    /** The episode to play next for a show: the one in progress, else the first unwatched, else the first. */
    fun nextEpisode(showId: Long): Entry? { val e = episodes(showId); return e.firstOrNull { it.resumeMs > 0 && !it.watched } ?: e.firstOrNull { !it.watched } ?: e.firstOrNull() }
    fun shows(): List<ShowRow> = readableDatabase.rawQuery("""SELECT s.id,s.title,s.year,s.overview,s.poster,s.backdrop,COUNT(i.path),SUM(COALESCE(t.watched,0))
        FROM show s JOIN item i ON i.show_id=s.id LEFT JOIN state t ON t.source_id=i.source_id AND t.path=i.path GROUP BY s.id ORDER BY s.title COLLATE NOCASE""", null).use { c ->
        buildList { while (c.moveToNext()) add(ShowRow(c.getLong(0), c.getString(1) ?: "", if (c.isNull(2)) null else c.getInt(2), c.getString(3) ?: "", c.getString(4), c.getString(5), c.getInt(6), c.getInt(7))) } }
    fun counts(): Pair<Int, Int> = readableDatabase.rawQuery("SELECT SUM(episode=0),SUM(episode=1) FROM item", null).use { it.moveToFirst(); it.getInt(0) to it.getInt(1) }

    // ---- watch state
    fun saveProgress(sourceId: Long, path: String, posMs: Long, durMs: Long) {
        val keep = PlayRules.toSave(posMs, durMs) ?: return
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO state(source_id,path) VALUES(?,?)", arrayOf<Any>(sourceId, path))
        db.execSQL("UPDATE state SET pos=?, dur=?, watched=CASE WHEN ? THEN 1 ELSE watched END, plays=plays+CASE WHEN ? THEN 1 ELSE 0 END, last=? WHERE source_id=? AND path=?",
            arrayOf<Any>(keep.first, durMs, if (keep.second) 1 else 0, if (keep.second) 1 else 0, System.currentTimeMillis(), sourceId, path))
    }
    /** Applies a watch position that came from the server, only if it is newer than ours. Returns true when it changed anything. */
    fun applyRemoteProgress(sourceId: Long, path: String, posMs: Long, durMs: Long, watched: Boolean, updatedSec: Long): Boolean {
        val local = readableDatabase.rawQuery("SELECT last FROM state WHERE source_id=? AND path=?", arrayOf("$sourceId", path)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
        if (!ProgressMerge.remoteWins(local, updatedSec)) return false
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO state(source_id,path) VALUES(?,?)", arrayOf<Any>(sourceId, path))
        db.execSQL("UPDATE state SET pos=?, dur=?, watched=?, last=? WHERE source_id=? AND path=?", arrayOf<Any>(posMs, durMs, if (watched) 1 else 0, updatedSec * 1000, sourceId, path))
        return true
    }
    fun setWatched(sourceId: Long, path: String, watched: Boolean) {
        val db = writableDatabase
        db.execSQL("INSERT OR IGNORE INTO state(source_id,path) VALUES(?,?)", arrayOf<Any>(sourceId, path))
        db.execSQL("UPDATE state SET watched=?, pos=0, last=? WHERE source_id=? AND path=?", arrayOf<Any>(if (watched) 1 else 0, System.currentTimeMillis(), sourceId, path))
    }
}
