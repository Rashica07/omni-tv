package com.rasha.omni.play

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.view.InputDevice
import android.util.LruCache

data class GameItem(val pkg: String, val label: String, val streaming: Boolean)
data class Controller(val id: Int, val name: String, val kind: String, val vendorProduct: String)
data class ConsoleInput(val id: String, val port: String, val tvReports: String?, val state: String)

/** Known game-streaming clients. They are recognised by package name only; OMNI Play launches them and never speaks their protocols. */
private val StreamingClients = mapOf(
    "com.limelight" to "Moonlight", "com.valvesoftware.steamlink" to "Steam Link", "com.nvidia.geforcenow" to "GeForce NOW", "com.microsoft.xcloud" to "Xbox Cloud Gaming",
    "com.playstation.remoteplay" to "PS Remote Play", "com.parsec.client" to "Parsec", "com.amazon.luna.client" to "Amazon Luna",
)

class PlayData(private val ctx: Context) {
    private val pm get() = ctx.packageManager

    /** Games: apps the system itself flags as games (FLAG_IS_GAME, or category GAME), launchable on this TV. Streaming clients are listed separately. */
    fun games(): List<GameItem> {
        val seen = HashSet<String>()
        val out = ArrayList<GameItem>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(cat), 0).forEach { ri ->
                val ai = ri.activityInfo.applicationInfo
                val isGame = (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0 || ai.category == ApplicationInfo.CATEGORY_GAME
                val pkg = ri.activityInfo.packageName
                if (isGame && pkg != ctx.packageName && seen.add(pkg)) out += GameItem(pkg, ri.loadLabel(pm).toString(), false)
            }
        }
        return out.sortedBy { it.label.lowercase() }
    }

    fun streamingClients(): List<GameItem> = StreamingClients.mapNotNull { (pkg, name) ->
        try { pm.getPackageInfo(pkg, 0); if (launch(pkg) != null) GameItem(pkg, name, true) else null } catch (e: PackageManager.NameNotFoundException) { null }
    }

    /** Streaming clients that are not installed (so the screen can say what could be added, without claiming they are available here). */
    fun missingClients(): List<String> = StreamingClients.filter { (pkg, _) -> try { pm.getPackageInfo(pkg, 0); false } catch (e: PackageManager.NameNotFoundException) { true } }.values.toList()

    fun launch(pkg: String): Intent? = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)

    /** Real input devices that identify as game controllers right now. */
    fun controllers(): List<Controller> {
        return InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter { d ->
            // A controller either has an analogue (joystick) source, or is a gamepad with face buttons that is not one of the system's own virtual devices.
            // (The TV's voice remote registers a virtual HID "virtual-search" that claims the gamepad source and the face-button keys; it is not a controller.)
            val joystick = (d.sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            val pad = (d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
            val faceButtons = d.hasKeys(android.view.KeyEvent.KEYCODE_BUTTON_A, android.view.KeyEvent.KEYCODE_BUTTON_B).all { it }
            !d.isVirtual && !d.name.startsWith("virtual-") && (joystick || (pad && faceButtons))
        }.map { d ->
            val kind = if ((d.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) "Gamepad" else "Joystick"
            Controller(d.id, d.name, kind, "%04x:%04x".format(d.vendorId, d.productId))
        }
    }

    /** HDMI ports and the names the TV reports for what is connected (a CEC device appears as a child of its port). Nothing is guessed from a name. */
    fun hdmi(): List<ConsoleInput> = try {
        val tv = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        val all = tv.tvInputList.filter { it.isPassthroughInput && it.type == TvInputInfo.TYPE_HDMI }
        val kids = all.filter { it.parentId != null }.groupBy { it.parentId }
        all.filter { it.parentId == null }.map { i ->
            val st = listOf(i.id).plus(kids[i.id].orEmpty().map { it.id }).map { tv.getInputState(it) }
            val state = if (st.any { it == TvInputManager.INPUT_STATE_CONNECTED }) "Signal" else if (st.any { it == TvInputManager.INPUT_STATE_CONNECTED_STANDBY }) "Connected, standby" else "No signal"
            ConsoleInput(i.id, i.loadLabel(ctx).toString(), kids[i.id]?.firstOrNull()?.loadLabel(ctx)?.toString() ?: i.loadCustomLabel(ctx)?.toString()?.takeIf { it != i.loadLabel(ctx).toString() }, state)
        }.sortedBy { it.port }
    } catch (e: RuntimeException) { emptyList() }

    // ---- play history (this app's own launches only: it cannot see what happens inside other apps)
    private val prefs = ctx.getSharedPreferences("play", Context.MODE_PRIVATE)
    fun lastPlayed(pkg: String) = prefs.getLong("last_$pkg", 0L)
    fun launches(pkg: String) = prefs.getInt("n_$pkg", 0)
    fun markLaunched(pkg: String) = prefs.edit().putLong("last_$pkg", System.currentTimeMillis()).putInt("n_$pkg", launches(pkg) + 1).apply()
    fun favorites(): Set<String> = prefs.getStringSet("fav", emptySet()) ?: emptySet()
    fun toggleFavorite(pkg: String): Boolean { val s = favorites().toMutableSet(); val now = s.add(pkg); if (!now) s.remove(pkg); prefs.edit().putStringSet("fav", s).apply(); return now }

    private val art = LruCache<String, Bitmap>(24)

    /** The app's own banner, else its icon, drawn to a bitmap once (call off the main thread). */
    fun artwork(pkg: String): Bitmap {
        art.get(pkg)?.let { return it }
        val d = pm.getApplicationBanner(pkg) ?: pm.getApplicationIcon(pkg)
        val bmp = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val wide = d.intrinsicWidth.toFloat() / maxOf(1, d.intrinsicHeight) > 1.3f
        if (wide) d.setBounds(0, 0, 320, 180) else { c.drawColor(0xFF1C1C1C.toInt()); d.setBounds(90, 10, 230, 150) }
        d.draw(c)
        art.put(pkg, bmp)
        return bmp
    }
}
