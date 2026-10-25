package com.rasha.omni.reference

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.common.UiState
import com.rasha.omni.common.ageText
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.StateHost

@Composable
fun ReferenceScreen(vm: ReferenceViewModel) {
    val facts by vm.facts.collectAsStateWithLifecycle()
    val net by vm.network.collectAsStateWithLifecycle()
    var demo by remember { mutableStateOf<UiState<String>>(UiState.Empty("Pick a state to preview it")) }
    OmniScreen {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("OMNI Reference", style = OmniType.Title)
            Text("Kotlin, Jetpack Compose and tv-material on this TV. Every number below is read from the device.", style = OmniType.Body.copy(color = OmniColor.Text2))
            Text("THIS TV", style = OmniType.Caption)
            StateHost(facts, onRetry = vm::loadFacts, modifier = Modifier.height(120.dp)) { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    c.data.take(3).forEach { FactCard(it.title, it.value, it.sub) }
                }
            }
            (facts as? UiState.Content)?.let { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { c.data.drop(3).forEach { FactCard(it.title, it.value, it.sub) } }
            }
            Text("NETWORK AND STATES", style = OmniType.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OmniCard(onClick = vm::checkNetwork) { Text("Check network", style = OmniType.Body) }
                OmniCard(onClick = { demo = UiState.Loading }) { Text("Loading", style = OmniType.Body) }
                OmniCard(onClick = { demo = UiState.Empty("Nothing here yet") }) { Text("Empty", style = OmniType.Body) }
                OmniCard(onClick = { demo = UiState.Error("Something failed") }) { Text("Error", style = OmniType.Body) }
            }
            val n = net
            if (n is UiState.Content) Text("${n.data.value}   ${n.data.sub}   (${ageText(System.currentTimeMillis() - n.fetchedAtMs)})", style = OmniType.Body.copy(color = OmniColor.Text2))
            else if (n is UiState.Error) Text(n.message, style = OmniType.Body.copy(color = OmniColor.Warning))
            else if (n is UiState.Loading) Text("Checking…", style = OmniType.Body.copy(color = OmniColor.Text2))
            StateHost(demo, onRetry = { demo = UiState.Empty("Retried") }, modifier = Modifier.height(80.dp)) { }
        }
    }
}

@Composable
private fun FactCard(title: String, value: String, sub: String) {
    OmniCard(onClick = {}, modifier = Modifier.width(250.dp)) {
        Column {
            Text(title.uppercase(), style = OmniType.Caption)
            Spacer(Modifier.height(6.dp))
            Text(value, style = OmniType.Heading)
            if (sub.isNotEmpty()) Text(sub, style = OmniType.Label.copy(color = OmniColor.Text2))
        }
    }
}
