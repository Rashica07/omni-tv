package com.rasha.omni.cinema

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.rasha.omni.library.Entry
import com.rasha.omni.library.PlayRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen player. Position is saved every 10 s and on leaving, so "Resume" is right even after a crash; the end of a film marks it watched. */
class PlayerActivity : ComponentActivity() {
    private val lib by lazy { (application as CinemaApp).lib }
    private var player: ExoPlayer? = null
    private lateinit var view: PlayerView
    private lateinit var note: TextView
    private var req: PlayRequest? = null
    private var entry: Entry? = null
    private val ui = Handler(Looper.getMainLooper())
    private val saver = object : Runnable { override fun run() { save(); ui.postDelayed(this, 10_000) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = PlayerView(this).apply { setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING); controllerShowTimeoutMs = 3500; setBackgroundColor(Color.BLACK) }
        note = TextView(this).apply { setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER; setPadding(80, 0, 80, 0); visibility = TextView.GONE }
        setContentView(FrameLayout(this).apply { addView(view, -1, -1); addView(note, FrameLayout.LayoutParams(-1, -1)) })
        val sid = intent.getLongExtra("s", -1); val path = intent.getStringExtra("p") ?: return finish()
        lifecycleScope.launch {
            val e = withContext(Dispatchers.IO) { lib.db.entry(sid, path) } ?: return@launch finish()
            entry = e; req = lib.playRequest(e)
            start(req!!)
        }
    }

    private fun start(r: PlayRequest) {
        val http = DefaultHttpDataSource.Factory().setUserAgent("OMNI-Cinema/1.0").setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(8000).setReadTimeoutMs(15000).setDefaultRequestProperties(r.headers)
        val subs = r.subtitles.map { (u, lang) ->
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(u)).setMimeType(if (u.substringBefore('?').endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP).setLanguage(lang).setLabel(lang?.uppercase() ?: "Subtitles").build()
        }
        val item = MediaItem.Builder().setUri(r.url).setSubtitleConfigurations(subs).build()
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http))).build()
        player = p; view.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                note.text = "This video cannot be played on this TV.\n\n${error.errorCodeName}\n${(error.cause?.message ?: error.message).orEmpty().take(160)}\n\nPress Back to return."
                note.visibility = TextView.VISIBLE
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) finished() }
        })
        p.setMediaItem(item, r.resumeMs)
        p.prepare(); p.playWhenReady = true
        ui.postDelayed(saver, 10_000)
    }

    private fun save() {
        val p = player ?: return; val r = req ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        val pos = p.currentPosition
        lib.db.saveProgress(r.sourceId, r.path, pos, dur)
        lib.pushProgress(r.sourceId, r.path, pos, dur)
    }

    private fun finished() {
        val p = player ?: return; val r = req ?: return; val e = entry ?: return
        lib.db.saveProgress(r.sourceId, r.path, p.duration, p.duration)
        lib.pushProgress(r.sourceId, r.path, p.duration, p.duration)
        if (e.episode) {
            val eps = lib.db.episodes(e.showId); val i = eps.indexOfFirst { it.path == e.path && it.sourceId == e.sourceId }
            eps.getOrNull(i + 1)?.let { n -> player?.release(); player = null; startActivity(Intent(this, PlayerActivity::class.java).putExtra("s", n.sourceId).putExtra("p", n.path)); finish(); return }
        }
        finish()
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val p = player
        if (p != null && e.action == KeyEvent.ACTION_DOWN && !view.isControllerFullyVisible) when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_RIGHT -> { p.seekTo((p.currentPosition + 30_000).coerceAtMost(p.duration.coerceAtLeast(0))); view.showController(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)); view.showController(); return true }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { if (p.isPlaying) p.pause() else p.play(); view.showController(); return true }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> { view.showController(); return true }
        }
        return super.dispatchKeyEvent(e)
    }

    override fun onStop() { super.onStop(); save(); ui.removeCallbacks(saver); player?.pause() }
    override fun onDestroy() { super.onDestroy(); ui.removeCallbacks(saver); player?.release(); player = null }
}
