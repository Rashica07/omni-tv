package com.rasha.omni.library

/** One file seen on a source. [path] is relative to the source root with '/' separators. */
data class FileEntry(val path: String, val size: Long, val mtime: Long)

/** A directory item as a source reports it. */
data class Item(val name: String, val isDir: Boolean, val size: Long = 0, val mtime: Long = 0)

/** Anything that can list a directory: the OMNI server, a USB drive, local storage. Scanning never needs to know which. */
interface Lister {
    suspend fun list(dir: String): List<Item>
}

data class ScanPlan(val added: List<FileEntry>, val changed: List<FileEntry>, val removed: List<String>, val unchanged: Int)

object Scan {
    /** Walks [root] depth-first, bounded, collecting video files and their same-folder sidecars (subtitles, nfo, artwork). One unreadable folder skips that folder only. */
    suspend fun walk(lister: Lister, maxDepth: Int = 6, maxFiles: Int = 20000, onError: (String, Exception) -> Unit = { _, _ -> }): List<FileEntry> {
        val out = ArrayList<FileEntry>()
        suspend fun go(dir: String, depth: Int) {
            if (depth > maxDepth || out.size >= maxFiles) return
            val items = try { lister.list(dir) } catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e } catch (e: Exception) { onError(dir, e); return }
            for (it in items) {
                if (it.name.startsWith(".")) continue
                val p = if (dir.isEmpty()) it.name else "$dir/${it.name}"
                if (it.isDir) go(p, depth + 1)
                else if (out.size < maxFiles) out += FileEntry(p, it.size, it.mtime)
            }
        }
        go("", 0)
        return out
    }

    /** Incremental scan: only new or changed files are worth scraping again; files that vanished are removed (and keep their watch history if they return). */
    fun diff(known: Map<String, FileEntry>, found: List<FileEntry>): ScanPlan {
        val seen = HashSet<String>()
        val added = ArrayList<FileEntry>(); val changed = ArrayList<FileEntry>(); var same = 0
        for (f in found) {
            seen += f.path
            val k = known[f.path]
            when { k == null -> added += f; k.size != f.size || k.mtime != f.mtime -> changed += f; else -> same++ }
        }
        return ScanPlan(added, changed, known.keys.filter { it !in seen }, same)
    }

    /** A scan that finds nothing in a source that had files is treated as "cannot read it right now" (unplugged drive, server down), never as "everything was deleted". */
    fun removalAllowed(knownCount: Int, foundCount: Int) = !(foundCount == 0 && knownCount > 0)

    fun videos(files: List<FileEntry>) = files.filter { MediaName.isVideo(it.path.substringAfterLast('/')) }
}
