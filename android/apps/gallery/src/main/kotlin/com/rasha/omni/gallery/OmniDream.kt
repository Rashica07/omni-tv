package com.rasha.omni.gallery

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.service.dreams.DreamService
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.rasha.omni.common.NowPlayingSource
import com.rasha.omni.common.Track

/**
 * The screensaver. Choose "OMNI Gallery" in the TV's settings under Screen saver; the slideshow and its settings are the same as in the app.
 * While a media session is playing, a small artwork-and-title capsule fades in at the bottom right (it only reads the session: music is never touched,
 * and nothing is shown when no player is active or access has not been allowed). Any remote key ends the dream as usual.
 */
class OmniDream : DreamService() {
    private var view: SlideshowView? = null
    private var source: NowPlayingSource? = null
    private lateinit var capsule: View
    private lateinit var art: ImageView
    private lateinit var title: TextView
    private lateinit var artist: TextView

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
        val root = FrameLayout(this)
        root.addView(v, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildCapsule(), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            val d = resources.displayMetrics.density
            setMargins(0, 0, (64 * d).toInt(), (30 * d).toInt())
        })
        setContentView(root)
    }

    private fun buildCapsule(): View {
        val d = resources.displayMetrics.density
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), (8 * d).toInt(), (18 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply { setColor(0xB3141416.toInt()); cornerRadius = 16 * d }
            alpha = 0f; visibility = View.GONE
        }
        art = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(view: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, view.width, view.height, 8 * d) } }
        box.addView(art, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(this).apply { setTextColor(Color.WHITE); textSize = 14f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = (240 * d).toInt() }
        artist = TextView(this).apply { setTextColor(0xFFC4C4C8.toInt()); textSize = 12f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = (240 * d).toInt() }
        col.addView(title); col.addView(artist)
        box.addView(col, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (12 * d).toInt() })
        capsule = box
        return box
    }

    private fun show(t: Track?) {
        val c = capsule
        if (t == null || !t.playing) { c.animate().alpha(0f).setDuration(300).withEndAction { c.visibility = View.GONE }.start(); return }
        title.text = t.title
        artist.text = t.artist.orEmpty(); artist.visibility = if (t.artist.isNullOrBlank()) View.GONE else View.VISIBLE
        art.visibility = if (t.art == null) View.GONE else View.VISIBLE
        art.setImageBitmap(t.art)
        c.visibility = View.VISIBLE
        c.animate().alpha(1f).setDuration(400).start()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        view?.post { view?.start() }
        source = NowPlayingSource(this, ComponentName(this, "com.rasha.omni.MediaListener")) { t -> show(t) }.also { it.start() }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        source?.stop(); source = null
        view?.stop()
    }
}
