package com.rasha.omni.guide

import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

data class GChannel(val id: Long, val number: String, val name: String)
data class GProgram(val channelId: Long, val title: String, val start: Long, val end: Long, val description: String, val genre: String) {
    fun progress(now: Long): Float = if (now <= start) 0f else if (now >= end) 1f else (now - start).toFloat() / (end - start)
}
data class GuideData(val channels: List<GChannel>, val programs: Map<Long, List<GProgram>>, val loadedAt: Long, val horizon: Long)

object Permission { const val LISTINGS = "android.permission.READ_TV_LISTINGS" }

/**
 * Reads the TV's own broadcast guide (DVB EIT) through the public TV provider: channels of real tuner input services, and per channel the programmes in
 * a time range (the only query shape the provider allows ordinary apps). Needs READ_TV_LISTINGS, a runtime permission. Nothing is filled in for channels
 * the broadcast does not describe.
 */
class GuideRepository(private val ctx: Context) {
    fun hasPermission() = ctx.checkSelfPermission(Permission.LISTINGS) == PackageManager.PERMISSION_GRANTED

    fun load(now: Long, pastMs: Long = 90 * 60_000L, aheadMs: Long = 12 * 3_600_000L): GuideData {
        val tv = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val tuners = tv.tvInputList.filter { it.type == TvInputInfo.TYPE_TUNER && !it.isPassthroughInput }.map { it.id }.toSet()
        val channels = ArrayList<GChannel>()
        val seen = HashSet<String>()
        ctx.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            arrayOf(TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER, TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_INPUT_ID, TvContract.Channels.COLUMN_TYPE),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val input = c.getString(3) ?: continue
                val name = c.getString(2)?.trim().orEmpty()
                if (input !in tuners || name.isEmpty() || c.getString(4)?.startsWith("TYPE_DVB") != true) continue
                if (!seen.add(name.lowercase())) continue            // HD/SD twins appear once
                channels += GChannel(c.getLong(0), c.getString(1).orEmpty(), name)
            }
        }
        channels.sortBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
        val programs = HashMap<Long, List<GProgram>>()
        var horizon = 0L
        for (ch in channels) {
            val list = ArrayList<GProgram>()
            ctx.contentResolver.query(
                TvContract.buildProgramsUriForChannel(ch.id, now - pastMs, now + aheadMs),
                arrayOf(TvContract.Programs.COLUMN_TITLE, TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_SHORT_DESCRIPTION, TvContract.Programs.COLUMN_BROADCAST_GENRE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val p = GProgram(ch.id, c.getString(0).orEmpty(), c.getLong(1), c.getLong(2), c.getString(3).orEmpty(), c.getString(4).orEmpty())
                    if (p.title.isNotEmpty() && p.end > p.start) { list += p; horizon = maxOf(horizon, p.end) }
                }
            }
            if (list.isNotEmpty()) programs[ch.id] = list.sortedBy { it.start }
        }
        return GuideData(channels, programs, now, horizon)
    }
}
