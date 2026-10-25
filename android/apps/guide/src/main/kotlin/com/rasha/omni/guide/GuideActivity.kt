package com.rasha.omni.guide

import android.content.Intent
import android.media.tv.TvContract
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.rasha.omni.design.OmniTheme

class GuideActivity : ComponentActivity() {
    private val vm: GuideViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OmniTheme {
                var granted by remember { mutableStateOf(vm.hasPermission()) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (ok) vm.refresh() }
                GuideScreen(vm, granted, onAsk = { ask.launch(Permission.LISTINGS) }, onWatch = { ch ->
                    try { startActivity(Intent(Intent.ACTION_VIEW, TvContract.buildChannelUri(ch.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { vm.say("The TV did not open ${ch.name}.") }
                })
            }
        }
    }

    override fun onResume() { super.onResume(); vm.refresh() }
}
