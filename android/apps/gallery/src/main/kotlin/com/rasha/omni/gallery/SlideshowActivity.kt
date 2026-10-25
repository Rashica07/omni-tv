package com.rasha.omni.gallery

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity

class SlideshowActivity : ComponentActivity() {
    private lateinit var view: SlideshowView
    private lateinit var store: PhotoStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        store = PhotoStore(this)
        view = SlideshowView(this, store)
        setContentView(view)
        val s = GallerySettings(this)
        view.configure(s.playlist(store), s.intervalSec, s.shuffle, s.clock, s.info)
        view.post { view.start() }
        Toast.makeText(this, "Left/Right: change photo   OK: pause   Up: favorite   Back: leave", Toast.LENGTH_LONG).show()
    }

    override fun onKeyDown(keyCode: Int, e: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT -> { view.go(+1); true }
        KeyEvent.KEYCODE_DPAD_LEFT -> { view.go(-1); true }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { Toast.makeText(this, if (view.togglePause()) "Paused" else "Playing", Toast.LENGTH_SHORT).show(); true }
        KeyEvent.KEYCODE_DPAD_UP -> {
            view.current?.let { val now = store.toggleFavorite(it.id); Toast.makeText(this, if (now) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show() }
            true
        }
        else -> super.onKeyDown(keyCode, e)
    }

    override fun onStop() { super.onStop(); view.stop() }
}
