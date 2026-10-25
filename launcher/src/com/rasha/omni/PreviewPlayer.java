package com.rasha.omni;

import android.content.Context;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import static com.rasha.omni.Theme.dp;

/**
 * One muted live preview inside the focused channel card. This TV's player (MediaTek) renders video through a hardware overlay plane and
 * never feeds a TextureView, so the preview is a SurfaceView with media-overlay ordering, laid over the card inside its focus ring. Consequences,
 * both accepted: the picture has square corners, and nothing in the window can be drawn on top of it.
 * A single MediaPlayer is reused (never several at once). Every request carries a generation number so a late callback from an old request can
 * never show or hide anything for the wrong channel. If the stream fails or shows no picture in time the view stays hidden and the card keeps its
 * own artwork: another channel's video is never substituted.
 */
final class PreviewPlayer {
    interface Listener {
        /** A picture is visible. */
        void onFirstFrame(long ms);
        /** Nothing playable appeared (error, or no picture within the time limit). */
        void onFailed(long ms);
        /** The stream runs but this TV's player attaches no video to an in-page surface (a hardware limit, not a stream problem). */
        void onUnsupported();
    }

    private static final long TIMEOUT_MS = 8000L;

    private final FrameLayout parent;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SurfaceView view;
    private MediaPlayer mp;
    private int gen;
    private boolean live;
    private boolean surfaceReady;
    private boolean reported;
    private long startedAt;
    private Listener listener;
    private String pendingUrl;
    private RectF area = new RectF();

    PreviewPlayer(Context c, FrameLayout parent) {
        this.parent = parent;
        view = new SurfaceView(c);
        view.setZOrderMediaOverlay(true);
        view.setVisibility(View.GONE);
        view.setFocusable(false);
        view.getHolder().setFixedSize(1280, 720);       // a video-sized buffer; the overlay scales it down to the card
        view.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override public void surfaceCreated(SurfaceHolder h) {
                surfaceReady = true;
                if (pendingUrl != null) begin(pendingUrl);
            }
            @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
            @Override public void surfaceDestroyed(SurfaceHolder h) {
                surfaceReady = false;
                if (mp != null) { try { mp.setDisplay(null); } catch (RuntimeException ignored) { } }
            }
        });
        parent.addView(view, new FrameLayout.LayoutParams(dp(200), dp(112), Gravity.TOP | Gravity.START));
    }

    boolean active() { return live; }

    /** Starts previewing url over bounds (the card, in the parent's coordinates). A previous request is cancelled first. */
    void start(RectF bounds, String url, Listener l) {
        stop();
        gen++;
        live = true;
        reported = false;
        listener = l;
        startedAt = SystemClock.uptimeMillis();
        float inset = dp(Theme.RING + Theme.RING_GAP + 1);                    // stay inside the white focus ring
        area = new RectF(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        layout(area);
        view.setVisibility(View.VISIBLE);
        final int my = gen;
        ui.postDelayed(() -> { if (my == gen && live && !reported) fail(); }, TIMEOUT_MS);
        if (surfaceReady) begin(url); else pendingUrl = url;                   // the surface is created once the view is first shown
    }

    private void layout(RectF r) {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = Math.round(r.width());
        lp.height = Math.round(r.height());
        lp.leftMargin = Math.round(r.left);
        lp.topMargin = Math.round(r.top);
        view.setLayoutParams(lp);
    }

    private void begin(String url) {
        pendingUrl = null;
        final int my = gen;
        try {
            if (mp == null) mp = new MediaPlayer(); else mp.reset();
            mp.setDisplay(view.getHolder());
            mp.setVolume(0f, 0f);
            mp.setDataSource(url);
            mp.setOnPreparedListener(m -> {
                if (my != gen || !live) return;
                m.start();
                // A started stream with no video size after a moment means this player never attached video to the surface (MediaTek: video only
                // reaches the dedicated full-screen player). That is a device limit: stop at once instead of waiting for the timeout.
                ui.postDelayed(() -> {
                    if (my != gen || !live || reported || mp == null) return;
                    int vw = 0;
                    try { vw = mp.getVideoWidth(); } catch (RuntimeException ignored) { }
                    if (vw <= 0) {
                        OmniLog.state("pv no video attached after start (device limit)");
                        Listener l = listener;
                        stop();
                        if (l != null) l.onUnsupported();
                    }
                }, 2200);
            });
            mp.setOnInfoListener((m, what, extra) -> {
                if (my == gen && live && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) shown();
                return false;
            });
            mp.setOnVideoSizeChangedListener((m, w, h) -> {
                if (my != gen || !live || w <= 0 || h <= 0) return;
                fitToVideo(w, h);
                // this player does not always send RENDERING_START: a known video size, a moment after start, counts as a picture
                ui.postDelayed(() -> { if (my == gen && live && !reported && mp != null && mp.isPlaying()) shown(); }, 700);
            });
            mp.setOnErrorListener((m, what, extra) -> { if (my == gen && live) fail(); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            if (my == gen) fail();
        }
    }

    /** Fits the picture inside the card without stretching (bars show the card behind, never a distorted picture). */
    private void fitToVideo(int vw, int vh) {
        float va = vw / (float) vh, ca = area.width() / area.height();
        float w = area.width(), h = area.height();
        if (va > ca) h = w / va; else w = h * va;
        float cx = area.centerX(), cy = area.centerY();
        layout(new RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f));
    }

    private void shown() {
        if (reported) return;
        reported = true;
        long ms = SystemClock.uptimeMillis() - startedAt;
        if (listener != null) listener.onFirstFrame(ms);
    }

    private void fail() {
        long ms = SystemClock.uptimeMillis() - startedAt;
        Listener l = listener;
        stop();
        if (l != null) l.onFailed(ms);
    }

    /** Stops playback and hides the view. Safe at any time; the player object is kept for the next preview. */
    void stop() {
        gen++;
        live = false;
        reported = false;
        pendingUrl = null;
        listener = null;
        view.setVisibility(View.GONE);
        if (mp != null) {
            try { mp.setOnPreparedListener(null); mp.setOnInfoListener(null); mp.setOnErrorListener(null); mp.setOnVideoSizeChangedListener(null); mp.reset(); }
            catch (RuntimeException ignored) { }
        }
    }

    /** Frees the player (page hidden or destroyed). */
    void release() {
        stop();
        if (mp != null) { try { mp.release(); } catch (RuntimeException ignored) { } mp = null; }
    }
}
