package com.rasha.omni.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.view.Display

/** Everything below is read from the platform. Nothing is inferred from a label the user typed. */
data class InputItem(val id: String, val port: String, val tvName: String?, val customLabel: String?, val type: Int, val state: Int, val hdmi: Boolean, val passthrough: Boolean, val parentId: String? = null) {
    val title: String get() = customLabel?.takeIf { it.isNotBlank() } ?: port
    val stateText: String get() = when (state) {
        TvInputManager.INPUT_STATE_CONNECTED -> "Signal"
        TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> if (hdmi) "Connected, standby" else "Connected"
        else -> "No signal"
    }
}

data class NetworkInfoUi(val transport: String, val internet: Boolean, val connected: Boolean, val addresses: List<String>, val downKbps: Int, val upKbps: Int)
data class AudioOut(val name: String, val kind: String, val detail: String)
data class DisplayInfo(val current: String, val refresh: String, val hdr: List<String>, val modes: List<String>)
data class Capability(val title: String, val available: Boolean, val note: String, val action: Intent? = null)

class DeviceReader(private val ctx: Context) {
    private val tv get() = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
    private val cm get() = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    /**
     * Inputs as the TV lists them. A CEC device (a console, say) appears as a child input of its HDMI port: it is folded into the port's card, and the
     * name the TV gives it is shown as the TV's name for what is connected. Nothing here is guessed from the name.
     */
    fun inputs(): List<InputItem> = try {
        val all = tv.tvInputList.filter { it.isPassthroughInput }
        val byParent = all.filter { it.parentId != null }.groupBy { it.parentId }
        all.filter { it.parentId == null }.map { i ->
            val loaded = i.loadLabel(ctx)?.toString()
            val custom = i.loadCustomLabel(ctx)?.toString()
            val child = byParent[i.id]?.firstOrNull()
            val childName = child?.loadLabel(ctx)?.toString()
            val tvName = childName?.takeIf { it.isNotBlank() } ?: custom?.takeIf { it.isNotBlank() && it != loaded }
            val stateId = child?.id ?: i.id
            InputItem(i.id, loaded ?: i.id.substringAfterLast('/'), tvName, null, i.type, maxOf(tv.getInputState(i.id), 0).let { s ->
                val cs = child?.let { tv.getInputState(it.id) } ?: s
                if (cs == TvInputManager.INPUT_STATE_CONNECTED || s == TvInputManager.INPUT_STATE_CONNECTED) TvInputManager.INPUT_STATE_CONNECTED
                else if (cs == TvInputManager.INPUT_STATE_CONNECTED_STANDBY || s == TvInputManager.INPUT_STATE_CONNECTED_STANDBY) TvInputManager.INPUT_STATE_CONNECTED_STANDBY else TvInputManager.INPUT_STATE_DISCONNECTED
            }, i.type == TvInputInfo.TYPE_HDMI, true, null)
        }.sortedWith(compareBy({ !it.hdmi }, { it.port }))
    } catch (e: RuntimeException) { emptyList() }

    fun network(): NetworkInfoUi {
        val n = cm.activeNetwork
        val c = n?.let { cm.getNetworkCapabilities(it) }
        val lp = n?.let { cm.getLinkProperties(it) }
        val transport = when {
            c == null -> "Not connected"
            c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Connected"
        }
        return NetworkInfoUi(transport, c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true, c != null,
            lp?.linkAddresses?.map { it.address.hostAddress.orEmpty() }?.filter { !it.contains(':') } ?: emptyList(),
            c?.linkDownstreamBandwidthKbps ?: 0, c?.linkUpstreamBandwidthKbps ?: 0)
    }

    fun audio(): List<AudioOut> {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { d ->
            val kind = when (d.type) {
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"; AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"; AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in speaker"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"; AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out"; AudioDeviceInfo.TYPE_AUX_LINE -> "Aux"; else -> "Output (${d.type})"
            }
            val rates = d.sampleRates.takeIf { it.isNotEmpty() }?.let { "${it.max() / 1000} kHz max" } ?: ""
            val ch = d.channelCounts.takeIf { it.isNotEmpty() }?.let { "up to ${it.max()} ch" } ?: ""
            AudioOut(d.productName?.toString().orEmpty().ifBlank { kind }, kind, listOf(rates, ch).filter { it.isNotEmpty() }.joinToString(", "))
        }
    }

    fun display(): DisplayInfo {
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        val m = d.mode
        val hdr = d.hdrCapabilities?.supportedHdrTypes?.map {
            when (it) { Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"; Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"; Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"; Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"; else -> "HDR ($it)" }
        } ?: emptyList()
        return DisplayInfo("${m.physicalWidth}×${m.physicalHeight}", "${"%.0f".format(m.refreshRate)} Hz", hdr, d.supportedModes.map { "${it.physicalWidth}×${it.physicalHeight} @ ${"%.0f".format(it.refreshRate)} Hz" }.distinct())
    }

    /**
     * The capability registry: each row says whether this device actually offers the action and why not otherwise. An action is shown as usable only
     * if an activity on this TV can handle its intent (checked now, not assumed).
     */
    fun capabilities(): List<Capability> {
        fun settings(title: String, action: String) = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).let { i ->
            val ok = i.resolveActivity(ctx.packageManager) != null
            Capability(title, ok, if (ok) "Opens the TV's own settings page" else "This TV has no screen for it", if (ok) i else null)
        }
        val hasCec = ctx.packageManager.hasSystemFeature("android.hardware.hdmi.cec")
        val tuner = tv.tvInputList.any { it.type == TvInputInfo.TYPE_TUNER }
        return listOf(
            settings("Network settings", Settings.ACTION_WIFI_SETTINGS), settings("Display settings", Settings.ACTION_DISPLAY_SETTINGS),
            settings("Sound settings", Settings.ACTION_SOUND_SETTINGS), settings("Date and time", Settings.ACTION_DATE_SETTINGS), settings("All settings", Settings.ACTION_SETTINGS),
            Capability("HDMI-CEC feature flag", hasCec, if (hasCec) "Reported by the platform; device control itself is a system API OMNI cannot call" else "Not reported by this TV"),
            Capability("Switch input from an app", true, "Asks the TV's TV app to show an input; some TVs ignore it (then use the remote's Input button)"),
            Capability("TV tuner input", tuner, if (tuner) "A tuner input service is installed" else "No tuner input service"),
            Capability("Silent HDMI switching, CEC power on/off", false, "Needs privileged system permissions an ordinary app cannot hold"),
        )
    }

    fun switchIntent(inputId: String): Intent = Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(inputId)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun summary(): List<Pair<String, String>> = listOf(
        "Android" to "API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})", "Model" to "${Build.MANUFACTURER} ${Build.MODEL}", "CPU" to Build.SUPPORTED_ABIS.first(),
        "Vendor TV services" to listOf("com.mediatek.tvinput", "com.mediatek.tv.service").filter { p -> try { ctx.packageManager.getPackageInfo(p, 0); true } catch (e: PackageManager.NameNotFoundException) { false } }.ifEmpty { listOf("none found") }.joinToString(", "),
    )
}
