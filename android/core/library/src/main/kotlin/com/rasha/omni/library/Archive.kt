package com.rasha.omni.library

import org.json.JSONObject

data class IaFile(val name: String, val format: String, val size: Long, val height: Int)
data class IaItem(val id: String, val title: String, val year: Int?, val license: String?, val video: String?, val subtitle: String?)

/** Internet Archive as a media provider: streams are plain https files, no account, no key. Only items with an open or public-domain licence URL are offered. */
object Archive {
    fun openLicence(url: String?) = url != null && (url.contains("publicdomain") || url.contains("/zero/") || url.contains("creativecommons.org/licenses/by"))

    /** The one file worth playing on a small TV: an H.264 MP4, at most 720p, the biggest picture within that; derivatives like "_512kb" only if nothing better exists. */
    fun pickVideo(files: List<IaFile>): IaFile? {
        val mp4 = files.filter { MediaName.extension(it.name) in setOf("mp4", "m4v") && it.size > 1_000_000 }
        val h264 = mp4.filter { it.format.contains("h.264", true) || it.format.equals("MPEG4", true) }.ifEmpty { mp4 }
        val within = h264.filter { it.height in 1..720 }.ifEmpty { h264 }
        val full = within.filterNot { it.name.contains("_512kb", true) || it.name.contains("_ipod", true) }.ifEmpty { within }
        return full.maxWithOrNull(compareBy({ it.height.coerceAtMost(720) }, { it.size }))
    }

    fun pickSubtitle(files: List<IaFile>, video: String): String? {
        val subs = files.filter { MediaName.extension(it.name) in setOf("srt", "vtt") }
        val stem = MediaName.stripExt(video)
        return (subs.firstOrNull { it.name.startsWith(stem) } ?: subs.firstOrNull())?.name
    }

    fun parseMetadata(json: String): IaItem? = try {
        val d = JSONObject(json); val m = d.getJSONObject("metadata"); val fs = d.getJSONArray("files")
        val files = (0 until fs.length()).map { fs.getJSONObject(it) }.map { IaFile(it.optString("name"), it.optString("format"), it.optString("size").toLongOrNull() ?: 0, it.optString("height").toIntOrNull() ?: 0) }
        val v = pickVideo(files)
        IaItem(m.getString("identifier"), m.optString("title"), m.optString("year").toIntOrNull() ?: Regex("(19|20)\\d{2}").find(m.optString("date"))?.value?.toIntOrNull(), m.optString("licenseurl").takeIf { it.isNotEmpty() }, v?.name, v?.let { pickSubtitle(files, it.name) })
    } catch (e: Exception) { null }

    fun fileUrl(id: String, name: String) = "https://archive.org/download/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20") + "/" + name.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}
