package com.rasha.omni.gallery

import android.content.Context

/** Slideshow settings, shared by the app, the slideshow and the screensaver. */
class GallerySettings(ctx: Context) {
    private val p = ctx.getSharedPreferences("gallery_settings", Context.MODE_PRIVATE)
    var intervalSec: Int get() = p.getInt("interval", 10); set(v) = p.edit().putInt("interval", v).apply()
    var shuffle: Boolean get() = p.getBoolean("shuffle", true); set(v) = p.edit().putBoolean("shuffle", v).apply()
    var clock: Boolean get() = p.getBoolean("clock", true); set(v) = p.edit().putBoolean("clock", v).apply()
    var info: Boolean get() = p.getBoolean("info", true); set(v) = p.edit().putBoolean("info", v).apply()
    var useCurated: Boolean get() = p.getBoolean("curated", true); set(v) = p.edit().putBoolean("curated", v).apply()
    var useLocal: Boolean get() = p.getBoolean("local", true); set(v) = p.edit().putBoolean("local", v).apply()
    var favoritesOnly: Boolean get() = p.getBoolean("favonly", false); set(v) = p.edit().putBoolean("favonly", v).apply()

    fun playlist(store: PhotoStore): List<Photo> {
        val all = (if (useCurated) store.curated() else emptyList()) + (if (useLocal) store.local() else emptyList())
        return if (favoritesOnly) { val f = store.favorites(); all.filter { it.id in f } } else all
    }

    companion object { val Intervals = listOf(5, 10, 20, 60) }
}
