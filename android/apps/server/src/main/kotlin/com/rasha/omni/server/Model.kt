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
            s.tempC?.let { if (it >= HOT_C) add(Alert(Level.Warn, "The server is running hot (%.0f °C; warning from %.0f). Check airflow and what else it is running.".format(it, HOT_C))) }
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

object Fmt {
    fun bytes(b: Long): String = when { b >= 1L shl 40 -> "%.1f TB".format(b / 1099511627776.0); b >= 1L shl 30 -> "%.0f GB".format(b / 1073741824.0); else -> "%.0f MB".format(b / 1048576.0) }
    fun rate(bps: Long): String = when { bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0); bps >= 1_000 -> "%.0f kB/s".format(bps / 1_000.0); else -> "$bps B/s" }
    fun duration(s: Long): String = when { s >= 86400 -> "${s / 86400} d ${s % 86400 / 3600} h"; s >= 3600 -> "${s / 3600} h ${s % 3600 / 60} min"; else -> "${s / 60} min" }
}
