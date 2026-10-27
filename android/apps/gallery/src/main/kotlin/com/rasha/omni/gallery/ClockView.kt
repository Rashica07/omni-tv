package com.rasha.omni.gallery

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator

/** The typeface used across the ambient display: Geist (bundled with OMNI), a variable font, so the weight is a setting. Falls back to the system's thin sans. */
object Fonts {
    @Volatile private var geist: Typeface? = null
    fun geist(ctx: Context): Typeface = geist ?: (try { Typeface.createFromAsset(ctx.assets, "Geist.ttf") } catch (e: Exception) { Typeface.create("sans-serif-light", Typeface.NORMAL) }).also { geist = it }
}

/**
 * The oversized clock. Thin numerals in the default (minimal) style; the expressive style is heavier with a soft translucent gradient and a trace of
 * depth. No outline. A change of minute fades the old digits up and the new ones in. It draws two text runs, nothing else, and only animates for
 * half a second once a minute.
 */
class ClockView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val time = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.geist(ctx) }
    private val date = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.geist(ctx) }
    private var timeText = ""; private var dateText = ""
    private var prevText: String? = null
    private var shift = 1f                         // 0..1 progress of the digit change
    private var anim: ValueAnimator? = null
    private var placement = Placement(Spot.Center, false)
    private var expressive = false
    private val animate = android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    fun style(expressive: Boolean) { if (this.expressive != expressive) { this.expressive = expressive; shader = null; invalidate() } }

    fun place(p: Placement) { placement = p; shader = null; invalidate() }

    fun set(t: String, dt: String) {
        val changed = timeText.isNotEmpty() && t != timeText
        if (changed && animate && isShown) {
            prevText = timeText
            anim?.cancel()
            anim = ValueAnimator.ofFloat(0f, 1f).setDuration(520).apply {
                interpolator = DecelerateInterpolator(1.6f)
                addUpdateListener { shift = it.animatedValue as Float; invalidate() }
                addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { prevText = null } })
                start()
            }
        }
        timeText = t; dateText = dt
        invalidate()
    }

    override fun hasOverlappingRendering() = false
    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    private var shader: Shader? = null

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || timeText.isEmpty()) return
        val sp = placement.spot
        val big = when (sp) { Spot.Center -> 0.36f; Spot.Left, Spot.Right -> 0.31f; else -> 0.2f } * h
        time.textSize = big
        time.letterSpacing = if (expressive) 0f else 0.015f
        time.setFontVariationSettings(if (expressive) "'wght' 640" else "'wght' 190")
        date.textSize = (if (sp == Spot.Top || sp == Spot.Bottom) 0.034f else 0.039f) * h
        date.letterSpacing = 0.06f
        date.setFontVariationSettings("'wght' 420")
        val ink = if (placement.darkInk) 0xFF1A1A1D.toInt() else 0xFFE8DCC8.toInt()
        date.color = (ink and 0xFFFFFF) or (0xDD shl 24)
        val tw = time.measureText(timeText)
        val side = 0.07f * w
        val x = when (sp) { Spot.Center -> (w - tw) / 2f; Spot.Right -> w - side - tw; else -> side }
        val dateH = date.textSize * 1.9f
        val blockH = big * 0.74f + dateH                                   // cap height of the numerals plus the date line above them
        val top = when (sp) { Spot.Top -> 0.07f * h; Spot.Bottom -> h - 0.07f * h - blockH; Spot.Center -> (h - blockH) / 2f - 0.02f * h; else -> (h - blockH) / 2f }
        val base = top + dateH + big * 0.74f
        // the date sits above the numerals, aligned with their left edge (or centred for the centred layout)
        val dateX = when (sp) { Spot.Center -> (w - date.measureText(dateText)) / 2f; Spot.Right -> w - side - date.measureText(dateText); else -> side + 0.006f * w }
        c.drawText(dateText, dateX, top + date.textSize, date)
        if (expressive) {
            if (shader == null) shader = LinearGradient(0f, base - big * 0.74f, 0f, base, ink and 0xFFFFFF or (0xF2 shl 24), ink and 0xFFFFFF or (0x80 shl 24), Shader.TileMode.CLAMP)
            time.shader = shader
            if (!placement.darkInk) time.setShadowLayer(14f * d, 0f, 4f * d, 0x40000000) else time.clearShadowLayer()
        } else { time.shader = null; time.color = ink; time.clearShadowLayer() }
        val prev = prevText
        if (prev != null && shift < 1f) {
            val a = expressive.let { 255 }
            time.alpha = (a * (1f - shift)).toInt()
            c.drawText(prev, x, base - shift * 14f * d, time)
            time.alpha = (a * shift).toInt()
            c.drawText(timeText, x, base + (1f - shift) * 14f * d, time)
            time.alpha = 255
        } else c.drawText(timeText, x, base, time)
    }
}
