package com.rasha.omni.cinema

import com.rasha.omni.common.readUpTo
import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.rasha.omni.design.OmniCard
import com.rasha.omni.design.OmniColor
import com.rasha.omni.design.OmniScreen
import com.rasha.omni.design.OmniType
import com.rasha.omni.design.PinPad
import com.rasha.omni.library.Discovery
import com.rasha.omni.library.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun SourcesScreen(vm: CinemaViewModel) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val scanning by vm.lib.scanning.collectAsStateWithLifecycle()
    val reports by vm.lib.reports.collectAsStateWithLifecycle()
    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sources", style = OmniType.Heading.copy(fontSize = 26.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OmniCard(onClick = { vm.go(Screen.AddServer) }, modifier = Modifier.width(250.dp)) { Text("Add OMNI server", style = OmniType.Body.copy(fontSize = 17.sp)) }
                OmniCard(onClick = { vm.go(Screen.AddLocal) }, modifier = Modifier.width(250.dp)) { Text("Add USB / folder", style = OmniType.Body.copy(fontSize = 17.sp)) }
                OmniCard(onClick = { vm.go(Screen.AddArchive) }, modifier = Modifier.width(250.dp)) { Text("Internet Archive films", style = OmniType.Body.copy(fontSize = 17.sp)) }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                items(ui.sources, key = { it.id }) { r ->
                    val rep = reports[r.id]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = OmniType.Body.copy(fontSize = 19.sp))
                                Text(when (r.kind) { SourceKind.Lan -> "OMNI server  ·  ${r.base.removePrefix("http://")}"; SourceKind.Local -> "Folder  ·  ${r.base}"; SourceKind.Archive -> "Internet Archive (public domain / open licence)" }, style = OmniType.Label.copy(color = OmniColor.Text3))
                            }
                            if (r.kind != SourceKind.Archive) OmniCard(onClick = { vm.scan(r) }, modifier = Modifier.width(150.dp)) { Text(if (scanning.containsKey(r.id)) "Scanning…" else "Scan", style = OmniType.Body.copy(fontSize = 16.sp)) }
                            OmniCard(onClick = { vm.remove(r) }, modifier = Modifier.width(150.dp)) { Text("Remove", style = OmniType.Body.copy(fontSize = 16.sp)) }
                        }
                        scanning[r.id]?.let { Text(it, style = OmniType.Label.copy(color = OmniColor.Text2)) }
                        if (rep != null && !scanning.containsKey(r.id)) Text(rep.note ?: "Found ${rep.found} videos  ·  ${rep.added} new  ·  ${rep.removed} removed  ·  ${rep.scraped} identified  ·  ${rep.unmatched} not identified" +
                            (rep.errors.firstOrNull()?.let { "  ·  $it" } ?: ""), style = OmniType.Label.copy(fontSize = 12.sp, color = OmniColor.Text2))
                    }
                }
            }
            if (ui.sources.isEmpty()) Text("No sources yet.", style = OmniType.Body.copy(color = OmniColor.Text2))
        }
    }
}

@Composable
fun AddServerScreen(vm: CinemaViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<String>?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { found = Discovery.findServers(ctx); if (found!!.size == 1) chosen = found!![0] }
    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add OMNI server", style = OmniType.Heading.copy(fontSize = 26.sp))
            val f = found
            if (f == null) Text("Looking for servers on your network…", style = OmniType.Body.copy(color = OmniColor.Text2))
            else if (f.isEmpty()) Text("No OMNI server answered on port 8765. Is omniptvd running on the laptop?", style = OmniType.Body.copy(color = OmniColor.Warning))
            else {
                Text(if (chosen == null) "Choose a server" else "Server: $chosen", style = OmniType.Label.copy(color = OmniColor.Text2))
                if (chosen == null) f.forEach { a -> OmniCard(onClick = { chosen = a }, modifier = Modifier.width(320.dp)) { Text(a, style = OmniType.Body.copy(fontSize = 18.sp)) } }
                else {
                    Text("Enter the 8-digit PIN from /etc/omniptv/read.pin on the server", style = OmniType.Label.copy(color = OmniColor.Text2))
                    PinPad(pin) { pin = it; status = null }
                    OmniCard(onClick = { status = "Connecting…"; vm.addServer("OMNI server", chosen!!, pin) { err -> status = err ?: "Connected"; if (err == null) { vm.back(); vm.scanAll() } } }, modifier = Modifier.width(260.dp)) { Text("Connect", style = OmniType.Body.copy(fontSize = 18.sp)) }
                }
            }
            status?.let { Text(it, style = OmniType.Label.copy(color = if (it == "Connected") OmniColor.Positive else OmniColor.Warning)) }
        }
    }
}

@Composable
fun AddLocalScreen(vm: CinemaViewModel) {
    var status by remember { mutableStateOf<String?>(null) }
    val roots = remember {
        buildList {
            add(Environment.getExternalStorageDirectory().path + "/Movies"); add(Environment.getExternalStorageDirectory().path + "/Download"); add(Environment.getExternalStorageDirectory().path)
            File("/storage").listFiles()?.filter { it.name != "emulated" && it.name != "self" }?.forEach { add(it.path) }     // USB drives
        }.distinct().filter { File(it).isDirectory }
    }
    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Add USB drive or folder", style = OmniType.Heading.copy(fontSize = 26.sp))
            Text("Folders found on this TV. Reading them needs the storage permission (Settings › Apps › OMNI Cinema).", style = OmniType.Label.copy(color = OmniColor.Text2))
            roots.forEach { p -> OmniCard(onClick = { status = "Checking…"; vm.addLocal("", p) { err -> status = err ?: "Added"; if (err == null) { vm.back(); vm.scanAll() } } }, modifier = Modifier.width(520.dp)) { Text(p, style = OmniType.Body.copy(fontSize = 17.sp)) } }
            if (roots.isEmpty()) Text("No readable folders were found.", style = OmniType.Body.copy(color = OmniColor.Warning))
            status?.let { Text(it, style = OmniType.Label.copy(color = OmniColor.Warning)) }
        }
    }
}

private data class IaRow(val id: String, val title: String, val year: String)

@Composable
fun AddArchiveScreen(vm: CinemaViewModel) {
    var rows by remember { mutableStateOf<List<IaRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val added = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) {
            try {
                val q = java.net.URLEncoder.encode("collection:feature_films AND mediatype:movies AND licenseurl:*publicdomain*", "UTF-8")
                val c = URL("https://archive.org/advancedsearch.php?q=$q&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year&rows=24&sort%5B%5D=downloads+desc&output=json").openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 10000
                try { check(c.responseCode == 200); val d = JSONObject(c.inputStream.readUpTo(500_000).toString(Charsets.UTF_8)).getJSONObject("response").getJSONArray("docs")
                    (0 until d.length()).map { d.getJSONObject(it) }.map { IaRow(it.getString("identifier"), it.optString("title"), it.optString("year")) } } finally { c.disconnect() }
            } catch (e: Exception) { error = "Could not reach the Internet Archive."; emptyList() }
        }
    }
    OmniScreen {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Internet Archive: public-domain films", style = OmniType.Heading.copy(fontSize = 26.sp))
            Text("Only items the Archive marks as public domain are listed. OK adds a film to your library; it streams from archive.org.", style = OmniType.Label.copy(color = OmniColor.Text2))
            status?.let { Text(it, style = OmniType.Label.copy(color = OmniColor.Warning)) }
            error?.let { Text(it, style = OmniType.Body.copy(color = OmniColor.Warning)) }
            if (rows == null) Text("Loading…", style = OmniType.Body.copy(color = OmniColor.Text2))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                items(rows.orEmpty(), key = { it.id }) { r ->
                    OmniCard(onClick = { status = "Adding ${r.title}…"; vm.addArchive(r.id) { err -> status = err ?: "Added ${r.title}"; if (err == null) added += r.id } }, modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(r.title, Modifier.weight(1f), maxLines = 1, style = OmniType.Body.copy(fontSize = 17.sp)); Text(r.year, style = OmniType.Label.copy(color = OmniColor.Text3)); if (r.id in added) Text("✓", style = OmniType.Label)
                        }
                    }
                }
            }
        }
    }
}
