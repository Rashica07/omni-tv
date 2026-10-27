package com.rasha.omni.gallery

import android.content.Context

/** Ambient and gallery settings, shared by the app, the ambient display, the screensaver and the launcher's idle timer. */
class GallerySettings(private val ctx: Context) {
    /** The gallery runs in its own process and the launcher reads these too, so every access re-checks the file (MODE_MULTI_PROCESS reloads it when it changed). */
    @Suppress("DEPRECATION")
    private val p get() = ctx.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS)
    var intervalSec: Int get() = p.getInt("interval", 20); set(v) = p.edit().putInt("interval", v).apply()
    var shuffle: Boolean get() = p.getBoolean("shuffle", true); set(v) = p.edit().putBoolean("shuffle", v).apply()
    var clock: Boolean get() = p.getBoolean("clock", true); set(v) = p.edit().putBoolean("clock", v).apply()
    var info: Boolean get() = p.getBoolean("info", true); set(v) = p.edit().putBoolean("info", v).apply()
    var useBing: Boolean get() = p.getBoolean("bing", true); set(v) = p.edit().putBoolean("bing", v).apply()
    var useCurated: Boolean get() = p.getBoolean("curated", true); set(v) = p.edit().putBoolean("curated", v).apply()
    var useLocal: Boolean get() = p.getBoolean("local", true); set(v) = p.edit().putBoolean("local", v).apply()
    var favoritesOnly: Boolean get() = p.getBoolean("favonly", false); set(v) = p.edit().putBoolean("favonly", v).apply()
    /** 0 = minimal (thin numerals, the default), 1 = expressive (heavier, translucent). */
    var clockStyle: Int get() = p.getInt("clockstyle", 0); set(v) = p.edit().putInt("clockstyle", v).apply()
    /** 0 = automatic (the quietest part of each photograph), then a fixed spot in [Spot] order +1. */
    var clockSpot: Int get() = p.getInt("clockspot", 0); set(v) = p.edit().putInt("clockspot", v).apply()
    /** 0 = a slow drift on each photograph, 1 = still. Also still when the system's animations are off. */
    var motion: Int get() = p.getInt("motion", 0); set(v) = p.edit().putInt("motion", v).apply()
    /** Minutes of no remote input on the launcher before the ambient display starts by itself; 0 = never (the default). */
    var idleMin: Int get() = p.getInt("idle", 0); set(v) = p.edit().putInt("idle", v).apply()
    /** 0 = still start while music plays (the track is shown), 1 = never start by itself while something is playing. */
    var musicMode: Int get() = p.getInt("music", 0); set(v) = p.edit().putInt("music", v).apply()
    /** Hours the ambient display holds the screen on; afterwards the TV's own power management takes over. */
    var keepAwakeH: Int get() = p.getInt("awake", 2); set(v) = p.edit().putInt("awake", v).apply()

    fun forcedSpot(): Spot? = if (clockSpot == 0) null else Spot.values()[(clockSpot - 1).coerceIn(0, Spot.values().size - 1)]

    /** Today's Bing photograph first, then the rest. Ambient shows these; the gallery browses the same list. */
    fun playlist(store: PhotoStore): List<Photo> {
        val all = (if (useBing) store.bing() else emptyList()) + (if (useCurated) store.curated() else emptyList()) + (if (useLocal) store.local() else emptyList())
        return if (favoritesOnly) { val f = store.favorites(); all.filter { it.id in f } } else all
    }

    companion object {
        val Intervals = listOf(10, 20, 60, 300)
        val IdleChoices = listOf(0, 5, 10, 30)
        val AwakeChoices = listOf(1, 2, 4, 8)
    }
}
