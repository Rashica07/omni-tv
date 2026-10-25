package com.rasha.omni.weather.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.rasha.omni.weather.Kind
import com.rasha.omni.weather.SkyMath
import com.rasha.omni.weather.WeatherUi
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

data class SkyColors(val top: Color, val mid: Color, val bottom: Color)

private val Night = SkyColors(Color(0xFF0B1534), Color(0xFF0A1226), Color(0xFF05070D))
private val Twilight = SkyColors(Color(0xFF3B4A86), Color(0xFFD4805E), Color(0xFF1A1A28))
private val Daytime = SkyColors(Color(0xFF2A78CF), Color(0xFF4F97D8), Color(0xFF0E2A48))

private fun mix(a: SkyColors, b: SkyColors, t: Float) = SkyColors(lerp(a.top, b.top, t), lerp(a.mid, b.mid, t), lerp(a.bottom, b.bottom, t))

/** How much a condition drains the colour out of the sky and how dark it makes it. */
private fun greyness(k: Kind) = when (k) { Kind.Clear -> 0f; Kind.Partly -> 0.18f; Kind.Cloud -> 0.55f; Kind.Fog -> 0.6f; Kind.Rain -> 0.65f; Kind.Snow -> 0.4f; Kind.Storm -> 0.72f; Kind.Unknown -> 0.4f }

/** The sky for this moment: time of day from the real sunrise and sunset, then the weather on top. The bottom is always dark so text on it stays readable. */
fun skyFor(ui: WeatherUi): SkyColors {
    val alt = SkyMath.altitude(ui.nowMin, ui.sunriseMin, ui.sunsetMin)
    val (d, t, n) = SkyMath.weights(alt)
    val base = SkyColors(
        Color(Night.top.red * n.toFloat() + Twilight.top.red * t.toFloat() + Daytime.top.red * d.toFloat(), Night.top.green * n.toFloat() + Twilight.top.green * t.toFloat() + Daytime.top.green * d.toFloat(), Night.top.blue * n.toFloat() + Twilight.top.blue * t.toFloat() + Daytime.top.blue * d.toFloat()),
        Color(Night.mid.red * n.toFloat() + Twilight.mid.red * t.toFloat() + Daytime.mid.red * d.toFloat(), Night.mid.green * n.toFloat() + Twilight.mid.green * t.toFloat() + Daytime.mid.green * d.toFloat(), Night.mid.blue * n.toFloat() + Twilight.mid.blue * t.toFloat() + Daytime.mid.blue * d.toFloat()),
        Color(Night.bottom.red * n.toFloat() + Twilight.bottom.red * t.toFloat() + Daytime.bottom.red * d.toFloat(), Night.bottom.green * n.toFloat() + Twilight.bottom.green * t.toFloat() + Daytime.bottom.green * d.toFloat(), Night.bottom.blue * n.toFloat() + Twilight.bottom.blue * t.toFloat() + Daytime.bottom.blue * d.toFloat()),
    )
    val g = greyness(ui.kind)
    fun grey(c: Color): Color { val l = c.luminance() * 0.95f + 0.04f; return lerp(c, Color(l * 0.96f, l, l * 1.04f), g) }
    val dark = if (ui.kind == Kind.Rain || ui.kind == Kind.Storm) 0.78f else 1f
    fun fin(c: Color) = lerp(Color(0xFF000000), grey(c), dark)
    return SkyColors(fin(base.top), fin(base.mid), lerp(fin(base.bottom), Color(0xFF05070A), 0.5f))
}

/** A soft cloud bank: puffs with feathered edges, drawn once into a bitmap, then tinted and faded at draw time. No hard rectangle, so the underside fades like real cloud. */
private fun cloudBitmap(tint: Color): ImageBitmap {
    val bmp = ImageBitmap(640, 220)
    val c = androidx.compose.ui.graphics.Canvas(bmp)
    fun puff(x: Float, y: Float, r: Float, a: Float) {
        val p = Paint().apply {
            isAntiAlias = true
            shader = android.graphics.RadialGradient(x, y, r, intArrayOf(android.graphics.Color.argb((255 * a).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt()), android.graphics.Color.argb((255 * a * 0.85f).toInt(), (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt()), android.graphics.Color.argb(0, (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt())), floatArrayOf(0f, 0.55f, 1f), android.graphics.Shader.TileMode.CLAMP)
        }
        c.drawCircle(Offset(x, y), r, p)
    }
    // body of the bank, then taller billows on top, then thin wisps along the underside
    for ((x, y, r) in listOf(Triple(90f, 140f, 70f), Triple(190f, 130f, 85f), Triple(300f, 135f, 95f), Triple(410f, 132f, 88f), Triple(510f, 140f, 74f), Triple(585f, 150f, 52f))) puff(x, y, r, 0.95f)
    for ((x, y, r) in listOf(Triple(170f, 85f, 62f), Triple(285f, 70f, 74f), Triple(395f, 82f, 64f), Triple(485f, 100f, 48f))) puff(x, y, r, 0.9f)
    for ((x, y, r) in listOf(Triple(130f, 176f, 46f), Triple(250f, 182f, 52f), Triple(370f, 180f, 50f), Triple(470f, 178f, 42f))) puff(x, y, r, 0.45f)
    return bmp
}

/** The whole sky as one canvas. Colours change smoothly when the conditions or the place change; only a few cheap shapes move. */
@Composable
fun SkyScene(ui: WeatherUi, animate: Boolean) {
    val target = remember(ui.kind, ui.nowMin / 5, ui.sunriseMin, ui.sunsetMin) { skyFor(ui) }
    val top by animateColorAsState(target.top, tween(if (animate) 800 else 0), label = "top")
    val mid by animateColorAsState(target.mid, tween(if (animate) 800 else 0), label = "mid")
    val bottom by animateColorAsState(target.bottom, tween(if (animate) 800 else 0), label = "bottom")
    val stars = remember { List(70) { Triple(Random(it).nextFloat(), Random(it + 100).nextFloat() * 0.55f, 0.4f + Random(it + 200).nextFloat() * 0.6f) } }
    var drift by remember { mutableFloatStateOf(0f) }
    if (animate) LaunchedEffect(Unit) { while (true) { delay(100); drift = (drift + 0.00055f) % 1000f } }       // ten redraws a second of a handful of shapes
    val alt = SkyMath.altitude(ui.nowMin, ui.sunriseMin, ui.sunsetMin)
    val (_, _, nightW) = SkyMath.weights(alt)
    val dayTime = SkyMath.isDay(ui.nowMin, ui.sunriseMin, ui.sunsetMin)
    val arc = SkyMath.arc(ui.nowMin, ui.sunriseMin, ui.sunsetMin).toFloat()
    val now = remember(ui.nowMin / 30) { System.currentTimeMillis() }
    val illum = remember(now) { SkyMath.moonIllumination(now).toFloat() }
    val waxing = remember(now) { SkyMath.moonWaxing(now) }
    val density = LocalDensity.current.density
    val cloudTint = when { ui.kind == Kind.Rain || ui.kind == Kind.Storm -> Color(0xFF5D6673); !dayTime -> Color(0xFF8C9AB8); ui.kind == Kind.Cloud || ui.kind == Kind.Fog -> Color(0xFFD5DCE4); else -> Color.White }
    val clouds = remember(cloudTint) { cloudBitmap(cloudTint) }                       // tinted once, so drawing is a plain blit with no per-frame colour filter
    val layers = (ui.cloudiness * 4f).toInt().coerceIn(if (ui.kind == Kind.Clear) 0 else 1, 4)
    val flash = remember { Animatable(0f) }
    if (animate && ui.kind == Kind.Storm) LaunchedEffect(Unit) { while (true) { delay(Random.nextLong(7000, 13000)); flash.animateTo(0.55f, tween(60)); flash.animateTo(0f, tween(240)); delay(120); flash.animateTo(0.35f, tween(50)); flash.animateTo(0f, tween(300)) } }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = drift
            drawRect(Brush.verticalGradient(0f to top, 0.55f to mid, 1f to bottom))
            val w = size.width; val h = size.height
            // stars, only on a clear-ish night
            if (nightW > 0.3 && ui.cloudiness < 0.8f) for ((sx, sy, sa) in stars) drawCircle(Color.White.copy(alpha = (sa * (1f - ui.cloudiness) * nightW.toFloat()).coerceIn(0f, 0.9f)), (0.8f + sa) * density, Offset(sx * w, sy * h))
            // the sun, or the moon with its real phase, on its real arc between sunrise and sunset
            val cx = w * (0.10f + 0.80f * arc); val cy = h * (0.44f - 0.32f * sin(PI.toFloat() * arc))
            val vis = (1f - 0.8f * ui.cloudiness).coerceIn(0.15f, 1f)
            val disc = (1f - 1.15f * ui.cloudiness).coerceIn(0f, 1f)                         // behind thick cloud there is a glow, not a disc
            if (dayTime) {
                val r = 26f * density
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A8).copy(alpha = 0.75f * vis), Color(0x00FFC83D)), Offset(cx, cy), r * 5.5f), r * 5.5f, Offset(cx, cy))
                if (disc > 0.05f) drawCircle(Color(0xFFFFF4CC).copy(alpha = disc), r, Offset(cx, cy))
            } else {
                val r = 22f * density
                drawCircle(Brush.radialGradient(listOf(Color(0xFFDDE6FF).copy(alpha = 0.28f * vis), Color(0x00DDE6FF)), Offset(cx, cy), r * 4f), r * 4f, Offset(cx, cy))
                if (disc > 0.05f) drawCircle(Color(0x33FFFFFF).copy(alpha = 0.2f * disc), r, Offset(cx, cy))     // the dark side, faintly
                val k = 1f - 2f * illum                                                      // 1 new … -1 full
                if (illum > 0.02f && disc > 0.05f) withTransform({ if (!waxing) scale(-1f, 1f, Offset(cx, cy)) }) {
                    val lit = Path().apply {
                        moveTo(cx, cy - r)
                        arcTo(Rect(cx - r, cy - r, cx + r, cy + r), -90f, 180f, false)
                        val ex = abs(k) * r
                        arcTo(Rect(cx - ex, cy - r, cx + ex, cy + r), 90f, if (k > 0) -180f else 180f, false)
                        close()
                    }
                    drawPath(lit, Color(0xFFEDE8CC).copy(alpha = disc))
                }
            }
            // clouds drift slowly; each layer a little different in size, height and speed
            for (i in 0 until layers) {
                val scale = 0.85f + 0.22f * (i % 3)
                val cw = (w * 0.55f * scale).toInt(); val ch = (cw * 220f / 640f).toInt()
                val x = (((t * (0.55f + 0.18f * i) + i * 0.37f) % 1.4f) - 0.3f) * w
                val y = h * (0.07f + 0.075f * i)
                drawImage(clouds, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(cw, ch), alpha = (0.16f + 0.26f * ui.cloudiness) * (if (i % 2 == 0) 1f else 0.7f))
            }
            if (ui.kind == Kind.Fog) for (i in 0 until 4) {
                val y = h * (0.18f + 0.13f * i); val x = ((t * 0.3f + i * 0.21f) % 1f) * 0.2f * w
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x30FFFFFF), Color.Transparent), x - w * 0.1f, x + w * 1.1f), Offset(0f, y), Size(w, 26f * density))
            }
            if (flash.value > 0f) drawRect(Color.White.copy(alpha = flash.value))
        }
        if (animate) Particles(ui.kind)
    }
}

/** Rain or snow. The animation clock is read only inside the draw lambda, so a frame redraws 70 short lines and recomposes nothing. */
@Composable
fun Particles(kind: Kind) {
    if (kind != Kind.Rain && kind != Kind.Storm && kind != Kind.Snow) return
    val snow = kind == Kind.Snow
    val xs = remember { FloatArray(70) { Random.nextFloat() } }
    val ys = remember { FloatArray(70) { Random.nextFloat() } }
    val sp = remember { FloatArray(70) { 0.5f + Random.nextFloat() } }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(kind) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                val dt = if (last == 0L) 0.016f else minOf(0.05f, (n - last) / 1e9f)
                last = n
                for (i in xs.indices) {
                    ys[i] += dt * sp[i] * (if (snow) 0.10f else 0.9f)
                    if (snow) xs[i] += dt * 0.02f * (sp[i] - 1f)
                    if (ys[i] > 1.05f) { ys[i] = -0.05f; xs[i] = Random.nextFloat() }
                }
                tick = n
            }
        }
    }
    val d = LocalDensity.current.density
    Canvas(Modifier.fillMaxSize()) {
        tick                                                   // reading the clock here makes this a draw-only invalidation
        val c = if (snow) Color(0xCCFFFFFF) else Color(0x55BFE0FF)
        for (i in xs.indices) {
            val x = xs[i] * size.width; val y = ys[i] * size.height
            if (snow) drawCircle(c, (1.6f + sp[i]) * d, Offset(x, y))
            else drawLine(c, Offset(x, y), Offset(x - 5 * d, y + 16 * sp[i] * d), 1.4f * d, StrokeCap.Round)
        }
    }
}

/** A calm backdrop for the loading and error states, before there is a forecast to paint a sky from. */
@Composable
fun PlainSky() {
    Box(Modifier.fillMaxSize().androidx_background())
}

private fun Modifier.androidx_background(): Modifier = this.then(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF2B3440), Color(0xFF0B0D10)))))
