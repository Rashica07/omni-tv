package com.rasha.omni.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** Number pad for the server PIN: a TV remote has no number row, so digits are chosen with the D-pad. */
@Composable
fun PinPad(pin: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(pin.padEnd(8, '•').chunked(4).joinToString("  ") { it.toList().joinToString(" ") }, style = OmniType.Heading.copy(fontSize = 26.sp))
        listOf(listOf("1", "2", "3", "4", "5"), listOf("6", "7", "8", "9", "0")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { d -> OmniCard(onClick = { if (pin.length < 8) onChange(pin + d) }, modifier = Modifier.width(70.dp)) { Text(d, style = OmniType.Body.copy(fontSize = 20.sp)) } } }
        }
        OmniCard(onClick = { onChange(pin.dropLast(1)) }, modifier = Modifier.width(150.dp)) { Text("Delete", style = OmniType.Body.copy(fontSize = 16.sp)) }
    }
}

