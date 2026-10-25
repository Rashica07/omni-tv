package com.rasha.omni.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** A compact pill button for heroes, toolbars and filters. [primary] is the one main action: solid white with black text. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, selected: Boolean = false) {
    val shape = RoundedCornerShape(50)
    val fill = if (primary) Color.White else if (selected) Color(0xFF2E2E2E) else Color(0xCC1C1C1C)
    Box(modifier.omniFocus(shape).background(fill, shape).border(1.dp, if (primary) Color.White else OmniColor.Hairline, shape).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, maxLines = 1, style = OmniType.Body.copy(fontSize = 14.sp, color = if (primary) Color.Black else if (selected) Color.White else OmniColor.Text2))
    }
}
