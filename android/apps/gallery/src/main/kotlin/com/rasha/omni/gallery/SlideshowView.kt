package com.rasha.omni.gallery

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.rasha.omni.common.NowPlayingSource
import com.rasha.omni.common.Track
import java.util.Date
import java.util.concurrent.Executors

/**
 * The ambient display (and the full-screen viewer). Two ImageViews cross-fade; the next photograph is decoded off the main thread at screen size, and a
 * stale decode (the person moved on) is dropped by a generation number. Each photograph drifts very slowly (a 6% scale over its whole time on screen)
 * unless animations are off, the TV is low-memory or the setting says still. The clock goes in the quietest part of each photograph ([ClockPlacement]).
 * Nothing allocates per frame: the only animations are property animations on view layers. Used by the app, the screensaver and the viewer.
 */
class SlideshowView(ctx: Context, private val store: PhotoStore) : FrameLayout(ctx) {
    private val a = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val b = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0f }
    private val veil = View(ctx)
    private val infoVeil = View(ctx)
    private val clock = ClockView(ctx)
    private val infoBox = LinearLayout(ctx)
    private val titleV = TextView(ctx); private val subV = TextView(ctx); private val creditV = TextView(ctx)
    private val capsule = LinearLayout(ctx)
    private val capArt = ImageView(ctx); private val capTitle = TextView(ctx); private val capArtist = TextView(ctx)
    private val ui = Handler(Looper.getMainLooper())
    private var io = Executors.newSingleThreadExecutor()
    private val settings = GallerySettings(ctx)
    private val animate = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    private val lowRam = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).isLowRamDevice
    private var media: NowPlayingSource? = null

    private var photos: List<Photo> = emptyList()
    private var order = SlideOrder(0, false)
    private var intervalMs = 20_000L
    private var showClock = true
    private var showInfo = true
    private var viewer = false
    private var shuffleOn = true
    private var startIndex = -1
    private var vIdx = 0
    private var front = a
    private var gen = 0
    private var running = false
    private var spot = Spot.Center
    private var ink = false
    var paused = false; private set
    private val history = ArrayList<Int>()
    private var pos = -1
    var current: Photo? = null; private set
    private val tick = Runnable { go(+1) }
    private val clockTick = object : Runnable { override fun run() { updateClock(); ui.postDelayed(this, 61_000 - System.currentTimeMillis() % 60_000) } }

    init {
        setBackgroundColor(Color.BLACK)
        val d = resources.displayMetrics.density
        val geist = Fonts.geist(ctx)
        addView(a, LayoutParams(-1, -1)); addView(b, LayoutParams(-1, -1))
        addView(veil, LayoutParams(-1, -1))
        addView(infoVeil, LayoutParams(-1, (200 * d).toInt(), Gravity.BOTTOM))
        addView(clock, LayoutParams(-1, -1))
        titleV.apply { typeface = geist; setTextColor(0xFFE8DCC8.toInt()); textSize = 21f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        subV.apply { typeface = geist; setTextColor(0xCCE8DCC8.toInt()); textSize = 14f; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        creditV.apply { typeface = geist; setTextColor(0x99E8DCC8.toInt()); textSize = 12f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END }
        infoBox.orientation = LinearLayout.VERTICAL
        infoBox.addView(titleV); infoBox.addView(subV); infoBox.addView(creditV)
        addView(infoBox, LayoutParams((560 * d).toInt(), -2, Gravity.BOTTOM or Gravity.START).apply { setMargins((64 * d).toInt(), 0, 0, (34 * d).toInt()) })
        capsule.apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), (8 * d).toInt(), (18 * d).toInt(), (8 * d).toInt())
            background = GradientDrawable().apply { setColor(0xB3141416.toInt()); cornerRadius = 16 * d }
            alpha = 0f; visibility = GONE
        }
        capArt.apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; outlineProvider = object : android.view.ViewOutlineProvider() { override fun getOutline(view: View, o: android.graphics.Outline) = o.setRoundRect(0, 0, view.width, view.height, 8 * d) } }
        capsule.addView(capArt, LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt()))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        capTitle.apply { typeface = geist; setTextColor(Color.WHITE); textSize = 14f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = (240 * d).toInt() }
        capArtist.apply { typeface = geist; setTextColor(0xFFC4C4C8.toInt()); textSize = 12f; setSingleLine(); ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = (240 * d).toInt() }
        col.addView(capTitle); col.addView(capArtist)
        capsule.addView(col, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = (12 * d).toInt() })
        addView(capsule, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, (64 * d).toInt(), (34 * d).toInt()) })
        infoVeil.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0x99000000.toInt(), 0))
    }

    /** [startAt] is the photograph to begin with (or -1). In [viewer] mode the view is a plain full-screen viewer: sequential, paused, no clock, credit always shown. */
    fun configure(list: List<Photo>, intervalSec: Int, shuffle: Boolean, clock: Boolean, info: Boolean, startAt: Int = -1, viewer: Boolean = false) {
        photos = list; this.viewer = viewer; shuffleOn = shuffle
        order = SlideOrder(list.size, shuffle && !viewer); intervalMs = intervalSec * 1000L
        showClock = clock && !viewer; showInfo = info || viewer
        this.clock.visibility = if (showClock) VISIBLE else GONE
        this.clock.style(settings.clockStyle == 1)
        startIndex = if (viewer) startAt.coerceIn(0, (list.size - 1).coerceAtLeast(0)) else if (startAt >= 0) startAt else if (list.firstOrNull()?.source == Source.Bing) 0 else -1
        vIdx = startIndex.coerceAtLeast(0) - 1
        paused = viewer
        history.clear(); pos = -1
    }

    fun start() {
        if (running) return
        if (io.isShutdown) io = Executors.newSingleThreadExecutor()
        running = true
        updateClock(); ui.postDelayed(clockTick, 61_000 - System.currentTimeMillis() % 60_000)
        if (!viewer) {
            io.execute {
                if (settings.useBing) try { BingProvider(context).refreshIfDue() } catch (e: Exception) { }     // one small request at most twice a day; the last good photographs stay if it fails
                if (photos.isEmpty()) {                                                                         // first run: the frame fills itself as soon as there is something to show
                    val l = settings.playlist(store)
                    if (l.isNotEmpty()) post { if (running && photos.isEmpty()) { configure(l, (intervalMs / 1000).toInt(), shuffleOn, showClock, showInfo); go(+1) } }
                }
            }
            media = NowPlayingSource(context, ComponentName(context, "com.rasha.omni.MediaListener")) { t -> post { showTrack(t) }; kotlin.Unit }.also { it.start() }
        }
        go(+1)
    }

    fun stop() { running = false; gen++; ui.removeCallbacksAndMessages(null); io.shutdownNow(); media?.stop(); media = null; a.animate().cancel(); b.animate().cancel() }

    fun togglePause(): Boolean { paused = !paused; ui.removeCallbacks(tick); if (!paused) ui.postDelayed(tick, 600); return paused }

    /** Shows or hides the credit line (the viewer's OK key). */
    fun toggleInfo(): Boolean { val on = infoBox.alpha < 0.5f; infoBox.animate().alpha(if (on) 1f else 0f).setDuration(200).start(); infoVeil.animate().alpha(if (on) 1f else 0f).setDuration(200).start(); return on }

    /** +1 next, -1 previous (the photographs already shown, in order; in the viewer simply the neighbour). */
    fun go(dir: Int) {
        if (photos.isEmpty()) { titleV.text = "No photographs to show"; subV.text = "Open OMNI Gallery to download some"; creditV.text = ""; return }
        ui.removeCallbacks(tick)
        val idx = when {
            viewer -> { vIdx = Math.floorMod(vIdx + dir, photos.size); vIdx }
            history.isEmpty() && startIndex >= 0 -> { history.add(startIndex); pos = 0; startIndex }
            dir < 0 && pos > 0 -> { pos--; history[pos] }
            pos < history.size - 1 -> { pos++; history[pos] }
            else -> { val n = order.next(); history.add(n); pos = history.size - 1; n }
        }
        val g = ++gen
        val w = width.coerceAtLeast(1280); val h = height.coerceAtLeast(720)
        io.execute {
            val bmp = store.decode(photos[idx], w, h)
            val luma = bmp?.let { lumaGrid(it) }
            post { if (g == gen && running) show(bmp, luma, photos[idx]) }
        }
    }

    private fun lumaGrid(bmp: Bitmap): FloatArray? = try {
        val s = Bitmap.createScaledBitmap(bmp, ClockPlacement.GW, ClockPlacement.GH, true)
        val px = IntArray(ClockPlacement.GW * ClockPlacement.GH); s.getPixels(px, 0, ClockPlacement.GW, 0, 0, ClockPlacement.GW, ClockPlacement.GH)
        if (s !== bmp) s.recycle()
        FloatArray(px.size) { ClockPlacement.luma(px[it]) }
    } catch (e: Exception) { null }

    private fun show(bmp: Bitmap?, luma: FloatArray?, p: Photo) {
        if (bmp == null) { go(+1); return }                   // an unreadable file is skipped, never a blank screen
        val next = if (front === a) b else a
        val old = front
        next.setImageBitmap(bmp)
        current = p
        val d = resources.displayMetrics.density
        val pl = if (luma != null) ClockPlacement.choose(luma, settings.forcedSpot()) else Placement(settings.forcedSpot() ?: Spot.Center, false)
        arrange(pl, d)
        // credit: title, then Bing's description, then credit and date. Missing parts are simply absent.
        titleV.text = p.title; subV.text = p.caption; subV.visibility = if (p.caption.isBlank()) GONE else VISIBLE; creditV.text = p.credit
        infoBox.visibility = if (showInfo) VISIBLE else GONE; infoVeil.visibility = if (showInfo) VISIBLE else GONE
        if (animate && !viewer) {
            next.animate().cancel(); old.animate().cancel()
            next.scaleX = 1f; next.scaleY = 1f
            next.alpha = 0f
            next.setLayerType(LAYER_TYPE_HARDWARE, null); old.setLayerType(LAYER_TYPE_HARDWARE, null)
            ObjectAnimator.ofFloat(next, ALPHA, 0f, 1f).setDuration(1400).start()
            ObjectAnimator.ofFloat(old, ALPHA, old.alpha, 0f).setDuration(1400).apply {
                addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(an: Animator) { old.setLayerType(LAYER_TYPE_NONE, null); next.setLayerType(LAYER_TYPE_NONE, null); old.setImageDrawable(null); old.animate().cancel(); old.scaleX = 1f; old.scaleY = 1f } })
                start()
            }
            if (settings.motion == 0 && !lowRam) {
                next.pivotX = if (spot == Spot.Right) width * 0.2f else if (spot == Spot.Left) width * 0.8f else width / 2f
                next.pivotY = height / 2f
                next.animate().scaleX(1.06f).scaleY(1.06f).setDuration(intervalMs + 3000).setInterpolator(LinearInterpolator()).start()
            }
        } else { next.alpha = 1f; old.alpha = 0f; old.setImageDrawable(null) }
        front = next
        if (!paused) ui.postDelayed(tick, intervalMs)
    }

    /** Puts the clock, its soft legibility gradient and the credit where this photograph wants them. The gradient exists only on the clock's side and only for light ink. */
    private fun arrange(pl: Placement, d: Float) {
        val moved = pl != Placement(spot, ink)
        spot = pl.spot; ink = pl.darkInk
        val g: GradientDrawable? = if (pl.darkInk) null else when (pl.spot) {
            Spot.Left -> GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0x70000000, 0))
            Spot.Right -> GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, intArrayOf(0x70000000, 0))
            Spot.Top -> GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x66000000, 0))
            Spot.Bottom -> GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0x66000000, 0))
            Spot.Center -> GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = 520 * d; setGradientCenter(0.5f, 0.5f); colors = intArrayOf(0x4D000000, 0) }
        }
        veil.background = g
        if (showClock && moved && animate) { clock.alpha = 0f; clock.place(pl); clock.animate().alpha(1f).setDuration(900).setStartDelay(500).start() } else clock.place(pl)
        // the credit goes to the top-left when the clock takes the bottom
        (infoBox.layoutParams as LayoutParams).gravity = (if (pl.spot == Spot.Bottom) Gravity.TOP else Gravity.BOTTOM) or Gravity.START
        (infoBox.layoutParams as LayoutParams).setMargins((64 * d).toInt(), if (pl.spot == Spot.Bottom) (34 * d).toInt() else 0, 0, if (pl.spot == Spot.Bottom) 0 else (34 * d).toInt())
        infoBox.requestLayout()
        infoVeil.visibility = if (showInfo && pl.spot != Spot.Bottom) VISIBLE else GONE
        if (!viewer) { infoBox.alpha = 0f; infoBox.animate().alpha(1f).setDuration(900).setStartDelay(600).start() } else infoBox.alpha = 1f
    }

    private fun showTrack(t: Track?) {
        if (t == null || !t.playing) { capsule.animate().alpha(0f).setDuration(300).withEndAction { capsule.visibility = GONE }.start(); return }
        capTitle.text = t.title
        capArtist.text = t.artist.orEmpty(); capArtist.visibility = if (t.artist.isNullOrBlank()) GONE else VISIBLE
        capArt.visibility = if (t.art == null) GONE else VISIBLE
        capArt.setImageBitmap(t.art)
        capsule.visibility = VISIBLE
        capsule.animate().alpha(1f).setDuration(400).start()
    }

    private fun updateClock() {
        if (!showClock) return
        val l = resources.configuration.locale
        val t = DateFormat.format(DateFormat.getBestDateTimePattern(l, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"), Date()).toString()
        val d = DateFormat.format(DateFormat.getBestDateTimePattern(l, "EEEEdMMMM"), Date()).toString()
        clock.set(if (DateFormat.is24HourFormat(context)) t else t.replace(Regex("\\s?[AaPp]\\.?[Mm]\\.?"), "").trim(), d)
    }
}
