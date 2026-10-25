package com.rasha.omni.cinema

import android.app.Application
import com.rasha.omni.library.Library

class CinemaApp : Application() {
    /** One library for the whole process: the database, the image cache and the scan state are shared by the screens and the player. */
    val lib by lazy { Library(this, BuildConfig.TMDB) }
}
