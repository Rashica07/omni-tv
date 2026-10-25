package com.rasha.omni.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design tokens, identical in value to the launcher's Theme.java so every OMNI app looks like the same system. */
object OmniColor {
    val Bg = Color(0xFF0A0A0A)
    val Black = Color(0xFF000000)
    val Surface1 = Color(0xFF141414)
    val Surface2 = Color(0xFF1C1C1C)
    val Surface3 = Color(0xFF2A2A2A)
    val Hairline = Color(0xFF2E2E2E)
    val White = Color(0xFFFFFFFF)
    val Text = Color(0xFFFFFFFF)
    val Text2 = Color(0xFFCACACA)
    val Text3 = Color(0xFFA0A0A0)
    val Positive = Color(0xFF35C759)
    val Warning = Color(0xFFFFB020)
    val Rain = Color(0xFF7FC2FF)
    val Sun = Color(0xFFFFC83D)
}

object OmniSpace {
    val SafeSide = 64.dp
    val SafeVertical = 30.dp
    val Radius = 12.dp
    val Gap = 14.dp
    val Ring = 3.dp
    val RingGap = 3.dp
}
