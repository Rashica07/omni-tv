package com.rasha.omni.common

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** What the active media session says right now. Everything is optional: a field the player did not publish stays null/0 and is simply not shown. */
class Track(
    val pkg: String,
    val title: String?,
    val artist: String?,
    val art: Bitmap?,
    val durationMs: Long,
    private val positionMs: Long,
    private val positionAt: Long,
    val speed: Float,
    val playing: Boolean,
    val canPause: Boolean,
    val canNext: Boolean,
    val canPrev: Boolean,
    private val controller: MediaController,
) {
    /** The position now, advanced from the last report by the playback speed (so nothing has to poll the player). */
    fun position(): Long = Playhead.at(positionMs, positionAt, SystemClock.elapsedRealtime(), speed, playing, durationMs)
    fun playPause() { val c = controller.transportControls; if (playing) c.pause() else c.play() }
    fun next() = controller.transportControls.skipToNext()
    fun prev() = controller.transportControls.skipToPrevious()
}

/** Where the playhead is now, advanced from the player's last report so that nothing has to poll it. Pure, so it is tested. */
object Playhead {
    fun at(positionMs: Long, reportedAt: Long, now: Long, speed: Float, playing: Boolean, durationMs: Long): Long {
        if (positionMs < 0) return -1
        val p = if (playing) positionMs + ((now - reportedAt) * speed).toLong() else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Follows the active media session (Spotify, a cast receiver, any player) through the platform callbacks: no polling. It needs the notification
 * listener access OMNI already declares (it reads no notifications, only the session list); without it [available] stays false and nothing happens.
 * Only one bitmap (the current artwork, at most 320 px) is ever held.
 */
class NowPlayingSource(private val ctx: Context, private val listener: ComponentName, private val onChange: (Track?) -> Unit) {
    private val ui = Handler(Looper.getMainLooper())
    private var manager: MediaSessionManager? = null
    private var watching: MediaController? = null
    private var art: Bitmap? = null
    private var artKey: String? = null
    private var started = false
    var available = false; private set
    var current: Track? = null; private set

    private val sessionsCb = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }
    private val controllerCb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { attach(null); ui.post { refresh() } }
    }

    /** Starts following. Returns false (and the launcher carries on) when the person has not allowed access. */
    fun start(): Boolean {
        if (started) return available
        return try {
            val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            m.addOnActiveSessionsChangedListener(sessionsCb, listener, ui)
            manager = m; started = true; available = true
            pick(m.getActiveSessions(listener))
            true
        } catch (e: SecurityException) { available = false; false } catch (e: RuntimeException) { available = false; false }
    }

    fun stop() {
        if (!started) return
        started = false
        try { manager?.removeOnActiveSessionsChangedListener(sessionsCb) } catch (e: RuntimeException) { }
        attach(null)
        art = null; artKey = null; current = null
    }

    private fun refresh() { try { manager?.let { pick(it.getActiveSessions(listener)) } } catch (e: RuntimeException) { } }

    /** The session that is playing wins; otherwise the first one that has something to show (a paused track stays visible). */
    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) != null && (it.playbackState?.state ?: PlaybackState.STATE_NONE) !in ENDED }
        attach(best)
        publish()
    }

    private fun attach(c: MediaController?) {
        if (watching?.sessionToken == c?.sessionToken) return
        try { watching?.unregisterCallback(controllerCb) } catch (e: RuntimeException) { }
        watching = c
        try { c?.registerCallback(controllerCb, ui) } catch (e: RuntimeException) { }
    }

    private fun publish() {
        val c = watching
        val md = c?.metadata
        val st = c?.playbackState
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }
        val state = st?.state ?: PlaybackState.STATE_NONE
        if (c == null || title == null || state in ENDED) { current = null; art = null; artKey = null; onChange(null); return }
        val artist = (md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))?.takeIf { it.isNotBlank() }
        val key = c.packageName + "|" + title + "|" + artist
        if (key != artKey) {                                       // artwork is decoded/scaled once per track, never on a progress or pause update
            artKey = key
            art = (md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.let { shrink(it) }
        }
        val actions = st?.actions ?: 0L
        val playing = state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
        val t = Track(c.packageName, title, artist, art, md.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 } ?: 0L,
            st?.position ?: -1L, st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(), st?.playbackSpeed?.takeIf { it > 0f } ?: 1f, playing,
            actions and (PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_PAUSE) != 0L,
            actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L, actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L, c)
        current = t
        onChange(t)
    }

    private fun shrink(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        return if (m <= 320) b else Bitmap.createScaledBitmap(b, b.width * 320 / m, b.height * 320 / m, true)
    }

    private companion object { val ENDED = intArrayOf(PlaybackState.STATE_NONE, PlaybackState.STATE_STOPPED, PlaybackState.STATE_ERROR) }
}
