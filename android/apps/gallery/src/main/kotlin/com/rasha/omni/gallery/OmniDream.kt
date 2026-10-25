package com.rasha.omni.gallery

import android.service.dreams.DreamService

/** The screensaver. Choose "OMNI Gallery" in the TV's settings under Screen saver; the slideshow and its settings are the same as in the app. */
class OmniDream : DreamService() {
    private var view: SlideshowView? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        val store = PhotoStore(this)
        val v = SlideshowView(this, store)
        val s = GallerySettings(this)
        v.configure(s.playlist(store), s.intervalSec, s.shuffle, s.clock, s.info)
        view = v
        setContentView(v)
    }

    override fun onDreamingStarted() { super.onDreamingStarted(); view?.post { view?.start() } }

    override fun onDreamingStopped() { super.onDreamingStopped(); view?.stop() }
}
