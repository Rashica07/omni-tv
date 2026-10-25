package com.rasha.omni.cinema

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.rasha.omni.library.Catalogue
import com.rasha.omni.library.MediaId
import com.rasha.omni.library.Title

/**
 * The read-only catalogue the OMNI launcher shows on its Watch page (contract v1, see docs/CINEMA-INTEGRATION.md).
 * Guarded by the signature permission com.rasha.omni.permission.CINEMA in the manifest, so only apps signed with the OMNI key can read it.
 * It returns titles, years, public TMDB image paths, progress and an availability state. No PINs, no paths, nothing writable.
 */
class CinemaProvider : ContentProvider() {
    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTH, "version", 1); addURI(AUTH, "shelves", 2); addURI(AUTH, "items", 3); addURI(AUTH, "item/*", 4)
    }
    @Volatile private var cache: Pair<Long, List<Title>>? = null

    override fun onCreate() = true

    private fun titles(): List<Title> {
        val now = System.currentTimeMillis()
        cache?.let { if (now - it.first < 5000) return it.second }
        val lib = (context!!.applicationContext as CinemaApp).lib
        return lib.titles().also { cache = now to it }
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? = when (matcher.match(uri)) {
        1 -> MatrixCursor(arrayOf("contract", "app_version", "ready")).apply { addRow(arrayOf<Any>(1, "1.0", 1)) }
        2 -> MatrixCursor(arrayOf("shelf_id", "title", "count")).apply { Catalogue.shelves(titles()).forEach { addRow(arrayOf<Any>(it.id, it.title, it.count)) } }
        3 -> {
            val shelf = uri.getQueryParameter("shelf").orEmpty()
            val limit = (uri.getQueryParameter("limit")?.toIntOrNull() ?: 20).coerceIn(1, 60)
            val offset = (uri.getQueryParameter("offset")?.toIntOrNull() ?: 0).coerceAtLeast(0)
            cursor().also { c -> Catalogue.select(shelf, titles()).drop(offset).take(limit).forEach { add(c, it) } }
        }
        4 -> {
            val id = MediaId.parse(uri.lastPathSegment)
            cursor().also { c -> if (id != null) titles().firstOrNull { it.id == id }?.let { add(c, it) } }
        }
        else -> null
    }

    private fun cursor() = MatrixCursor(COLUMNS)

    private fun add(c: MatrixCursor, t: Title) {
        c.addRow(arrayOf<Any?>(t.id.text, t.kind, t.title, t.year, t.overview.take(400), t.poster?.let { "https://image.tmdb.org/t/p/w342$it" }, t.backdrop?.let { "https://image.tmdb.org/t/p/w780$it" },
            t.rating, t.sourceLabel, t.availability.name, t.positionMs, t.durationMs, if (t.watched) 1 else 0, t.updatedAt))
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0

    companion object {
        const val AUTH = "com.rasha.omni.cinema.catalogue"
        val COLUMNS = arrayOf("media_id", "kind", "title", "year", "overview", "poster", "backdrop", "rating", "source_label", "availability", "position_ms", "duration_ms", "watched", "updated_at")
    }
}
