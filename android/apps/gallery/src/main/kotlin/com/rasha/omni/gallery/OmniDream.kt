package com.rasha.omni.gallery

import android.service.dreams.DreamService

/**
 * The screensaver. Choose "OMNI Gallery" in the TV's settings under Screen saver: it is the same ambient display as in the app (Bing's daily photograph,
 * the oversized clock, a track capsule while music plays). It reads the media session only; music is never touched. Any remote key ends it as usual.
 */
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
