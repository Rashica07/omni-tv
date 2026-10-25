package com.rasha.omni.cinema

import android.content.Context
import com.rasha.omni.library.Library

/** One library for the whole Cinema process: the database, the image cache and the scan state are shared by the screens, the player and the catalogue provider. */
object CinemaLib {
    @Volatile private var lib: Library? = null
    fun get(ctx: Context): Library = lib ?: synchronized(this) { lib ?: Library(ctx.applicationContext, BuildConfig.TMDB).also { lib = it } }
}
