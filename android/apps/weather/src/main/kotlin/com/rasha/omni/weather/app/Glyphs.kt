package com.rasha.omni.weather.app

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.rasha.omni.weather.Kind
import kotlin.math.cos
import kotlin.math.sin

private val SunC = Color(0xFFFFC83D)
private val MoonC = Color(0xFFEDE8CC)
private val CloudC = Color(0xFFEAF0F6)
private val CloudGray = Color(0xFFB3BFCB)
private val CloudDark = Color(0xFF8D97B0)
private val DropC = Color(0xFF6EB6FF)
private val BoltC = Color(0xFFFFD84D)

/** Unit-size paths, built once and scaled by a transform at draw time (a path boolean-op per frame is needlessly slow). */
private val MoonUnit: Path = Path.combine(
    PathOperation.Difference,
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(0f, 0f), 0.28f)) },
    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(0.16f, -0.1f), 0.25f)) },
)
private val BoltUnit: Path = Path().apply {
    moveTo(0.04f, 0.12f); lineTo(-0.1f, 0.32f); lineTo(0f, 0.32f); lineTo(-0.08f, 0.5f); lineTo(0.16f, 0.26f); lineTo(0.05f, 0.26f); close()
}

/** Colourful filled weather icons drawn from shapes, at any size. [angle] spins the sun's rays (degrees). */
fun DrawScope.drawWeather(kind: Kind, day: Boolean, c: Offset, s: Float, angle: Float = 0f) {
    when (kind) {
        Kind.Clear -> if (day) sun(c, s, angle) else moon(c, s)
        Kind.Partly -> {
            val o = Offset(c.x - s * 0.16f, c.y - s * 0.14f)
            if (day) sun(o, s * 0.75f, angle) else moon(o, s * 0.75f)
            cloud(Offset(c.x + s * 0.08f, c.y + s * 0.10f), s * 0.86f, CloudC)
        }
        Kind.Cloud -> cloud(c, s, CloudC)
        Kind.Fog -> for (i in 0 until 4) {
            val y = c.y - s * 0.24f + i * s * 0.16f
            val inset = (i % 2) * s * 0.08f
            drawLine(CloudGray, Offset(c.x - s * 0.34f + inset, y), Offset(c.x + s * 0.34f - inset, y), s * 0.075f, StrokeCap.Round)
        }
        Kind.Rain -> {
            cloud(Offset(c.x, c.y - s * 0.1f), s * 0.92f, CloudGray)
            for (i in 0 until 3) drawLine(DropC, Offset(c.x - s * 0.2f + i * s * 0.2f, c.y + s * 0.22f), Offset(c.x - s * 0.26f + i * s * 0.2f, c.y + s * 0.4f), s * 0.065f, StrokeCap.Round)
        }
        Kind.Snow -> {
            cloud(Offset(c.x, c.y - s * 0.1f), s * 0.92f, CloudC)
            for (i in 0 until 3) drawCircle(Color.White, s * 0.05f, Offset(c.x - s * 0.2f + i * s * 0.2f, c.y + s * (if (i == 1) 0.38f else 0.3f)))
        }
        Kind.Storm -> {
            cloud(Offset(c.x, c.y - s * 0.1f), s * 0.92f, CloudDark)
            withTransform({ translate(c.x, c.y); scale(s, s, Offset.Zero) }) { drawPath(BoltUnit, BoltC) }
        }
        Kind.Unknown -> cloud(c, s, CloudGray)
    }
}

private fun DrawScope.sun(c: Offset, s: Float, angle: Float) {
    drawCircle(SunC, s * 0.2f, c)
    for (i in 0 until 8) {
        val a = Math.toRadians((angle + i * 45).toDouble())
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        drawLine(SunC, Offset(c.x + dx * s * 0.31f, c.y + dy * s * 0.31f), Offset(c.x + dx * s * 0.43f, c.y + dy * s * 0.43f), s * 0.06f, StrokeCap.Round)
    }
}

private fun DrawScope.moon(c: Offset, s: Float) = withTransform({ translate(c.x, c.y); scale(s, s, Offset.Zero) }) { drawPath(MoonUnit, MoonC) }

private fun DrawScope.cloud(c: Offset, s: Float, color: Color) {
    drawCircle(color, s * 0.19f, Offset(c.x - s * 0.18f, c.y + s * 0.04f))
    drawCircle(color, s * 0.26f, Offset(c.x + s * 0.04f, c.y - s * 0.08f))
    drawCircle(color, s * 0.17f, Offset(c.x + s * 0.25f, c.y + s * 0.06f))
    drawRoundRect(color, Offset(c.x - s * 0.37f, c.y + s * 0.02f), Size(s * 0.79f, s * 0.21f), CornerRadius(s * 0.1f))
}
