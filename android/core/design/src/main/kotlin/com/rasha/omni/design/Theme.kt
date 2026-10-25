package com.rasha.omni.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
/** Geist (SIL OFL 1.1) is a variable font; each weight is a separate Font entry, created once. */
val Geist = FontFamily(
    Font(R.font.geist, FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.geist, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(450))),
    Font(R.font.geist, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(550))),
    Font(R.font.geist, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(650))),
    Font(R.font.geist, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(750))),
)

private fun t(size: Int, w: FontWeight, spacing: Float = 0f) = TextStyle(fontFamily = Geist, fontSize = size.sp, fontWeight = w, letterSpacing = spacing.sp, color = OmniColor.Text)

object OmniType {
    val Display = t(96, FontWeight.Light, -2f)
    val Title = t(44, FontWeight.Bold, -0.5f)
    val Heading = t(24, FontWeight.SemiBold)
    val Body = t(18, FontWeight.Medium)
    val Label = t(14, FontWeight.Medium)
    val Caption = TextStyle(fontFamily = Geist, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = OmniColor.Text2)
}

@Composable
fun OmniTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = OmniColor.Bg, surface = OmniColor.Surface1, surfaceVariant = OmniColor.Surface2,
            onBackground = OmniColor.Text, onSurface = OmniColor.Text, onSurfaceVariant = OmniColor.Text2,
            primary = OmniColor.White, onPrimary = OmniColor.Black, border = OmniColor.Hairline,
        ),
        typography = Typography(),
        content = content,
    )
}
