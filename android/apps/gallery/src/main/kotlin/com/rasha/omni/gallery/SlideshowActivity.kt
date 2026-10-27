package com.rasha.omni.gallery

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Full-screen, three ways in. Ambient (the default, from Gallery or the launcher): the clock over the photographs; Left/Right change photograph, OK pauses, Up
 * favourites, Back or any other key leaves. Idle (started by the launcher's idle timer): every key leaves, so an accidental press never traps anyone. Viewer (a
 * thumbnail was chosen): the photograph alone; Left/Right browse, OK shows or hides the credit, Up favourites, Back returns to the grid.
 * The screen is held on only for the hours chosen in the settings; after that the TV's own power management takes over.
 */
class SlideshowActivity : ComponentActivity() {
    private lateinit var view: SlideshowView
    private lateinit var store: PhotoStore
    private var viewer = false
    private var idle = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewer = intent.getBooleanExtra("viewer", false)
        idle = intent.getBooleanExtra("idle", false)
        val s = GallerySettings(this)
        if (s.keepAwakeH > 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ui.postDelayed({ window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }, s.keepAwakeH * 3_600_000L)
        }
        store = PhotoStore(this)
        view = SlideshowView(this, store)
        setContentView(view)
        @Suppress("DEPRECATION") window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        view.configure(s.playlist(store), s.intervalSec, s.shuffle, s.clock, s.info, startAt = intent.getIntExtra("index", -1), viewer = viewer)
        view.post { view.start() }
        if (!viewer && !idle) Toast.makeText(this, "Left/Right: photograph   OK: pause   Up: favorite   Back: leave", Toast.LENGTH_LONG).show()
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_HOME) return super.onKeyDown(keyCode, e)
        if (idle) { finish(); return true }                                   // woken by the idle timer: any key brings the launcher back
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { view.go(+1); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { view.go(-1); true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                if (viewer) { view.toggleInfo(); true } else { Toast.makeText(this, if (view.togglePause()) "Paused" else "Playing", Toast.LENGTH_SHORT).show(); true }
            KeyEvent.KEYCODE_DPAD_UP -> {
                view.current?.let { val now = store.toggleFavorite(it.id); Toast.makeText(this, if (now) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show() }
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_MENU -> { if (!viewer) finish(); true }
            else -> super.onKeyDown(keyCode, e)
        }
    }

    override fun onStop() { super.onStop(); view.stop(); ui.removeCallbacksAndMessages(null); if (!isFinishing) finish() }
}
