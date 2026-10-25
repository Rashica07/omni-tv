package com.rasha.omni.play

import android.content.Context
import android.hardware.input.InputManager
import android.media.tv.TvInputManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.rasha.omni.design.OmniTheme

class PlayActivity : ComponentActivity() {
    private val vm: PlayViewModel by viewModels()
    private val main = Handler(Looper.getMainLooper())
    private val inputs by lazy { getSystemService(Context.INPUT_SERVICE) as InputManager }
    private val tv by lazy { getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager }
    private val deviceCb = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = vm.refresh()
        override fun onInputDeviceRemoved(id: Int) = vm.refresh()
        override fun onInputDeviceChanged(id: Int) = vm.refresh()
    }
    private val tvCb = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) = vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { PlayScreen(vm, launch = { pkg -> start(pkg) }, openIntent = { startActivity(it) }) } }
    }

    private fun start(pkg: String) {
        val i = vm.data.launch(pkg)
        if (i == null) { vm.say("That app can no longer be opened."); vm.refresh(); return }
        vm.data.markLaunched(pkg)
        try { startActivity(i) } catch (e: Exception) { vm.say("The TV could not open it.") }
    }

    override fun onStart() { super.onStart(); inputs.registerInputDeviceListener(deviceCb, main); tv.registerCallback(tvCb, main); vm.refresh() }

    override fun onStop() { super.onStop(); inputs.unregisterInputDeviceListener(deviceCb); tv.unregisterCallback(tvCb) }
}
