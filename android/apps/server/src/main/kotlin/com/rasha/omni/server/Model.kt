package com.rasha.omni.server

import org.json.JSONObject

data class Net(val iface: String, val rxBps: Long?, val txBps: Long?, val linkMbps: Int?) {
    val total get() = (rxBps ?: 0) + (txBps ?: 0)
    /** Share of the link in use, when the driver reported its speed. */
    val fraction get() = if (linkMbps != null && linkMbps > 0 && rxBps != null) (total * 8.0 / (linkMbps * 1_000_000.0)).coerceIn(0.0, 1.0) else null
}
data class Disk(val total: Long, val free: Long) { val usedFraction get() = if (total > 0) 1.0 - free.toDouble() / total else 0.0 }
data class SysStatus(val uptimeS: Long, val load1: Double, val cpus: Int, val memTotal: Long, val memAvailable: Long, val tempC: Double?, val disks: Map<String, Disk>, val batteryPercent: Int?, val batteryState: String?, val ac: Boolean, val mediaRoot: String = "", val net: Net? = null) {
    val memUsedFraction get() = if (memTotal > 0) 1.0 - memAvailable.toDouble() / memTotal else 0.0
    val loadPerCpu get() = if (cpus > 0) load1 / cpus else load1
}
data class Health(val ok: Boolean, val uptimeS: Long, val channels: Int, val enabled: Int, val playing: Int, val lastProbe: Double?, val probing: Boolean, val epg: Boolean)

/** The media library as the server reports it (/api/v1/library). */
data class LibRoot(val name: String, val available: Boolean)
data class ScanInfo(val scanning: Boolean, val phase: String, val files: Int, val added: Int, val changed: Int, val renamed: Int, val missing: Int, val errors: Int, val finishedS: Long?)
data class LibraryInfo(val movies: Int, val missing: Int, val unmatched: Int, val lastScanS: Long?, val roots: List<LibRoot>, val scan: ScanInfo, val metadata: Boolean)

/** A device that has reported watch progress to the server. The server does not track connections, so this is "seen", never "connected". */
data class DeviceSeen(val name: String, val lastSeenS: Long, val films: Int, val inProgress: Int, val watched: Int)

enum class Level { Info, Warn }
data class Alert(val level: Level, val text: String, val key: String = "")

object Parse {
    fun status(json: String): SysStatus {
        val o = JSONObject(json)
        val d = o.optJSONObject("disks"); val disks = LinkedHashMap<String, Disk>()
        d?.keys()?.forEach { k -> d.getJSONObject(k).let { disks[k] = Disk(it.getLong("total"), it.getLong("free")) } }
        val b = if (o.isNull("battery")) null else o.optJSONObject("battery")
        return SysStatus(o.optLong("uptime_s"), o.optJSONArray("load")?.optDouble(0, 0.0) ?: 0.0, o.optInt("cpus", 1), o.optLong("mem_total"), o.optLong("mem_available"),
            if (o.isNull("temp_c")) null else o.optDouble("temp_c"), disks, b?.optInt("percent"), b?.optString("status"), o.optBoolean("ac"), o.optString("media_root"), o.optJSONObject("net")?.let { n -> Net(n.optString("iface"), if (n.isNull("rx_bps")) null else n.optLong("rx_bps"), if (n.isNull("tx_bps")) null else n.optLong("tx_bps"), if (n.isNull("link_mbps")) null else n.optInt("link_mbps")) })
    }
    fun library(json: String): LibraryInfo {
        val o = JSONObject(json); val c = o.optJSONObject("counts"); val s = o.optJSONObject("scan")
        val roots = o.optJSONArray("roots")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { LibRoot(it.optString("name"), it.optBoolean("available")) } } ?: emptyList()
        val scan = ScanInfo(s?.optString("state") == "scanning", s?.optString("phase").orEmpty(), s?.optInt("files") ?: 0, s?.optInt("added") ?: 0, s?.optInt("changed") ?: 0, s?.optInt("renamed") ?: 0, s?.optInt("missing") ?: 0,
            s?.optJSONArray("errors")?.length() ?: 0, if (s == null || s.isNull("finished") || s.optDouble("finished") <= 0) null else s.optDouble("finished").toLong())
        return LibraryInfo(c?.optInt("movies") ?: 0, c?.optInt("missing") ?: 0, c?.optInt("unmatched") ?: 0, if (c == null || c.isNull("last_scan")) null else c.optLong("last_scan"), roots, scan, o.optBoolean("metadata"))
    }

    /** Devices that have synced watch positions, newest first. Names are what each device called itself. */
    fun devices(json: String): List<DeviceSeen> {
        val a = JSONObject(json).optJSONArray("items") ?: return emptyList()
        val rows = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
        return rows.groupBy { it.optString("device").ifBlank { "Unnamed device" } }.map { (name, r) ->
            DeviceSeen(name, r.maxOf { it.optLong("updated") }, r.size, r.count { it.optLong("pos_ms") > 0 && it.optInt("watched") == 0 }, r.count { it.optInt("watched") == 1 })
        }.sortedByDescending { it.lastSeenS }
    }

    fun scanStarted(json: String): Boolean = JSONObject(json).optBoolean("started")

    fun health(json: String): Health {
        val o = JSONObject(json)
        return Health(o.optBoolean("ok"), o.optLong("uptime_s"), o.optInt("channels"), o.optInt("enabled"), o.optInt("playing"), if (o.isNull("last_probe")) null else o.optDouble("last_probe"), o.optBoolean("probing"), o.optBoolean("epg"))
    }
}

/** Plain-language warnings, from numbers the server reported. Thresholds are stated in the text so nothing is a mystery. */
object Alerts {
    const val HOT_C = 85.0
    fun from(s: SysStatus?, h: Health?, nowS: Double): List<Alert> = buildList {
        if (s != null) {
            s.tempC?.let { if (it >= HOT_C) add(Alert(Level.Warn, "The server is running hot (%.0f °C; warning from %.0f). Check airflow and what else it is running.".format(it, HOT_C), "hot")) }
            if (s.loadPerCpu > 1.5) add(Alert(Level.Warn, "The server is overloaded (load %.1f on %d cores).".format(s.load1, s.cpus), "load"))
            if (s.memTotal > 0 && s.memAvailable < s.memTotal * 0.08) add(Alert(Level.Warn, "Memory is almost full (under 8% available).", "memory"))
            s.disks.forEach { (name, d) -> if (d.total > 0 && d.free < d.total * 0.05) add(Alert(Level.Warn, "Disk \"$name\" is nearly full (under 5% free).", "disk:$name")) }
            if (s.batteryPercent != null && !s.ac && s.batteryPercent < 20) add(Alert(Level.Warn, "The server is on battery (${s.batteryPercent}%). It will shut down if the power stays off.", "battery"))
            else if (s.batteryPercent != null && !s.ac) add(Alert(Level.Info, "The server is running on battery (${s.batteryPercent}%).", "battery"))
        }
        if (h != null) {
            if (h.enabled > 0 && h.playing < h.enabled / 2) add(Alert(Level.Warn, "Only ${h.playing} of ${h.enabled} enabled channels passed the last check.", "channels"))
            val lp = h.lastProbe
            if (lp != null && nowS - lp > 3 * 3600) add(Alert(Level.Warn, "Channels were last checked ${Math.round((nowS - lp) / 3600).toInt()} hours ago.", "probe-stale"))
        }
    }
}

/** Time-series helpers. Only real polled samples go in; nothing is smoothed into existence. */
object Series {
    /** "last 5 min" style span of a series, from its first to its last timestamp. */
    fun span(atMs: List<Long>): String { if (atMs.size < 2) return ""; val s = (atMs.last() - atMs.first()) / 1000; return if (s < 90) "last ${s} s" else "last ${s / 60} min" }
    /** A chart needs enough points to say something; below that the honest thing is the current value alone. */
    const val MIN_POINTS = 6
    fun enough(n: Int) = n >= MIN_POINTS
}

object Ago {
    fun text(nowS: Long, thenS: Long): String { val d = (nowS - thenS).coerceAtLeast(0); return when { d < 45 -> "just now"; d < 3600 -> "${(d + 30) / 60} min ago"; d < 86400 -> "${d / 3600} h ago"; else -> "${d / 86400} d ago" } }
    /** A device is "active" if it reported progress in the last few minutes (it plays and saves every 10 s); beyond that it is only "seen". */
    fun active(nowS: Long, thenS: Long) = nowS - thenS <= 300
}

object Fmt {
    fun bytes(b: Long): String = when { b >= 1L shl 40 -> "%.1f TB".format(b / 1099511627776.0); b >= 1L shl 30 -> "%.0f GB".format(b / 1073741824.0); else -> "%.0f MB".format(b / 1048576.0) }
    fun rate(bps: Long): String = when { bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0); bps >= 1_000 -> "%.0f kB/s".format(bps / 1_000.0); else -> "$bps B/s" }
    fun duration(s: Long): String = when { s >= 86400 -> "${s / 86400} d ${s % 86400 / 3600} h"; s >= 3600 -> "${s / 3600} h ${s % 3600 / 60} min"; else -> "${s / 60} min" }
}
