package com.rasha.omni.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.util.Date
import java.util.concurrent.Executors

/**
 * The one slideshow, used by the app and by the screensaver. Two ImageViews cross-fade; the next photo is decoded off the main thread at screen size;
 * a stale decode (user moved on) is dropped by a generation number. With animations switched off in the system, photos just change. Nothing here
 * allocates per frame: the only animation is a property animation on a view layer.
 */
class SlideshowView(ctx: Context, private val store: PhotoStore) : FrameLayout(ctx) {
    private val a = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val b = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val clock = TextView(ctx)
    private val info = TextView(ctx)
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val animate = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    private var photos: List<Photo> = emptyList()
    private var order = SlideOrder(0, false)
    private var intervalMs = 10_000L
    private var showClock = true
    private var showInfo = true
    private var front = a
    private var gen = 0
    private var running = false
    var paused = false; private set
    private val history = ArrayList<Int>()
    private var pos = -1
    var current: Photo? = null; private set
    private val tick = Runnable { go(+1) }
    private val clockTick = object : Runnable { override fun run() { updateClock(); ui.postDelayed(this, 30_000) } }

    init {
        setBackgroundColor(Color.BLACK)
        addView(a, LayoutParams(-1, -1)); addView(b, LayoutParams(-1, -1))
        val d = resources.displayMetrics.density
        val scrim = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xCC000000.toInt(), 0))
        addView(View(ctx).apply { background = scrim }, LayoutParams(-1, (180 * d).toInt(), Gravity.BOTTOM))
        clock.apply { setTextColor(Color.WHITE); textSize = 30f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt()) }
        info.apply { setTextColor(0xEEFFFFFF.toInt()); textSize = 16f; setShadowLayer(8f, 0f, 2f, 0xAA000000.toInt()) }
        addView(clock, LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, (36 * d).toInt(), (64 * d).toInt(), 0) })
        addView(info, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins((64 * d).toInt(), 0, (64 * d).toInt(), (34 * d).toInt()) })
    }

    fun configure(list: List<Photo>, intervalSec: Int, shuffle: Boolean, clock: Boolean, info: Boolean) {
        photos = list; order = SlideOrder(list.size, shuffle); intervalMs = intervalSec * 1000L; showClock = clock; showInfo = info
        this.clock.visibility = if (clock) VISIBLE else GONE
        history.clear(); pos = -1
    }

    fun start() {
        if (running) return
        running = true
        updateClock(); ui.postDelayed(clockTick, 30_000)
        go(+1)
    }

    fun stop() { running = false; gen++; ui.removeCallbacksAndMessages(null); io.shutdownNow() }

    fun togglePause(): Boolean { paused = !paused; ui.removeCallbacks(tick); if (!paused) ui.postDelayed(tick, 600); return paused }

    /** +1 next, -1 previous (the photos already shown, in order). */
    fun go(dir: Int) {
        if (photos.isEmpty()) { info.text = "No photographs to show"; return }
        ui.removeCallbacks(tick)
        val idx = if (dir < 0 && pos > 0) { pos--; history[pos] } else { if (pos < history.size - 1) { pos++; history[pos] } else { val n = order.next(); history.add(n); pos = history.size - 1; n } }
        val g = ++gen
        val w = width.coerceAtLeast(1280); val h = height.coerceAtLeast(720)
        io.execute {
            val bmp = store.decode(photos[idx], w, h)
            ui.post { if (g == gen && running) show(bmp, photos[idx]) }
        }
    }

    private fun show(bmp: Bitmap?, p: Photo) {
        if (bmp == null) { go(+1); return }                   // an unreadable file is skipped, never a blank screen
        val next = if (front === a) b else a
        next.setImageBitmap(bmp)
                current = p
        info.text = if (showInfo) "${p.title}\n${p.credit}" else ""
        if (animate) {
            next.animate().cancel(); front.animate().cancel()
            next.alpha = 0f
            next.setLayerType(LAYER_TYPE_HARDWARE, null); front.setLayerType(LAYER_TYPE_HARDWARE, null)
            next.animate().alpha(1f).setDuration(700).start()
            front.animate().alpha(0f).setDuration(700).withEndAction { front.setLayerType(LAYER_TYPE_NONE, null); next.setLayerType(LAYER_TYPE_NONE, null) }.start()
        } else { next.alpha = 1f; front.alpha = 0f }
        front = next
        if (!paused) ui.postDelayed(tick, intervalMs)
    }

    private fun updateClock() {
        if (!showClock) return
        val l = resources.configuration.locale
        val t = DateFormat.format(DateFormat.getBestDateTimePattern(l, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"), Date())
        val d = DateFormat.format(DateFormat.getBestDateTimePattern(l, "EEEEdMMMM"), Date())
        clock.text = "$t\n$d"
    }
}
