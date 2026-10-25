package com.rasha.omni.control

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.content.Context
import com.rasha.omni.design.OmniTheme

class ControlActivity : ComponentActivity() {
    private val vm: ControlViewModel by viewModels()
    private val main = Handler(Looper.getMainLooper())
    private val tv by lazy { getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager }
    private val cm by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    private val am by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val tvCb = object : TvInputManager.TvInputCallback() {
        override fun onInputStateChanged(inputId: String, state: Int) = vm.refresh()
        override fun onInputAdded(inputId: String) = vm.refresh()
        override fun onInputRemoved(inputId: String) = vm.refresh()
    }
    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = vm.refresh()
        override fun onLost(network: Network) = vm.refresh()
        override fun onCapabilitiesChanged(network: Network, c: android.net.NetworkCapabilities) = vm.refresh()
    }
    private val audioCb = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = vm.refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniTheme { ControlScreen(vm, startIntent = { startActivity(it) }) } }
    }

    override fun onStart() {
        super.onStart()
        tv.registerCallback(tvCb, main)
        try { cm.registerDefaultNetworkCallback(netCb) } catch (e: RuntimeException) { }
        am.registerAudioDeviceCallback(audioCb, main)
        vm.refresh()
    }

    override fun onStop() {
        super.onStop()
        tv.unregisterCallback(tvCb)
        try { cm.unregisterNetworkCallback(netCb) } catch (e: RuntimeException) { }
        am.unregisterAudioDeviceCallback(audioCb)
    }
}
