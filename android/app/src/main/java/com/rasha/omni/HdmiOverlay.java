package com.rasha.omni;

import android.animation.ValueAnimator;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/**
 * The HDMI picture. It lives in one overlay window owned by this service, not in an activity: an activity's TvView dies when the TV maker's TV app takes the
 * foreground, a window of this kind stays on top. The same window, with the same single tune, is the small live preview on a card and the full-screen view:
 * EXPAND grows it to the whole screen and shows the OMNI panel inside it, so nothing is torn down or re-tuned and the picture never goes black in between.
 * Needs "draw over other apps" (`adb shell appops set com.rasha.omni SYSTEM_ALERT_WINDOW allow`).
 */
public class HdmiOverlay extends Service {
    static volatile boolean launcherResumed, viewerResumed, fullscreen;
    static final String EXTRA_KEEP = "omni_keep", ACTION_STOP = "stop", ACTION_EXPAND = "expand", ACTION_PANEL = "panel";
    static final String EXTRA_INPUT = "input", EXTRA_X = "x", EXTRA_Y = "y", EXTRA_W = "w", EXTRA_H = "h", EXTRA_SECONDS = "s", EXTRA_NAME = "name", EXTRA_PORT = "port";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private FrameLayout holder;
    private TvView tv;
    private PlayerActivity.Panel panel;
    private WindowManager.LayoutParams lp;
    private String input;
    private boolean playing;
    private long hiddenSince, tunedAt;
    private int polls;
    private final Runnable hidePanel = () -> { if (panel != null) panel.show(false); };
    private int retries;
    /** The picture can fail to come back after the TV maker's app grabs the input. If there is still no video, tune again (a fresh session), up to three times. */
    private final Runnable ensureVideo = new Runnable() {
        @Override public void run() {
            if (tv == null || playing) return;
            if (retries >= 3) { setPanel(PlayerActivity.Panel.S_ERROR, "No picture from this input. Is the device switched on? Press Back and try again."); return; }
            retries++;
            OmniLog.state("hdmi-overlay no video yet, tuning again (" + retries + ")");
            try { tv.reset(); tv.setStreamVolume(fullscreen ? 1f : 0f); tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input)); } catch (RuntimeException e) { OmniLog.state("hdmi-overlay retune threw"); }
            ui.postDelayed(this, 2500);
        }
    };

    @Override public IBinder onBind(Intent i) { return null; }

    /** The TV maker's app launches itself ~2 s after an HDMI tune: whoever should be in front (the viewer when full screen, else the launcher) is brought back at once. */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            polls++;
            long now = SystemClock.uptimeMillis();
            boolean front = fullscreen ? viewerResumed : launcherResumed;
            if (fullscreen && launcherResumed) { OmniLog.state("hdmi-overlay stop: user went Home"); stopSelf(); return; }
            if (front) hiddenSince = 0;
            else {
                if (hiddenSince == 0) hiddenSince = now;
                if (now - tunedAt < 6000 && now - hiddenSince > 40) {          // only while the TV maker's app is taking over; after that, leaving means the user left
                    try {
                        Intent back = fullscreen ? new Intent(HdmiOverlay.this, HdmiViewerActivity.class) : new Intent(HdmiOverlay.this, MainActivity.class);
                        startActivity(back.putExtra(EXTRA_KEEP, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                        OmniLog.state("hdmi-overlay front " + (fullscreen ? "viewer" : "launcher"));
                    } catch (RuntimeException e) { OmniLog.state("hdmi-overlay front FAILED"); }
                } else if (now - hiddenSince > 3500) { OmniLog.state("hdmi-overlay stop: nobody in front"); stopSelf(); return; }
            }
            ui.postDelayed(this, now - tunedAt < 4500 ? 60 : 250);          // the TV maker's app appears ~2 s after the tune: look often then, rarely afterwards
        }
    };

    @Override public int onStartCommand(Intent i, int flags, int id) {
        if (i == null || !android.provider.Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        String a = i.getAction();
        if (ACTION_STOP.equals(a)) { stopSelf(); return START_NOT_STICKY; }
        if (ACTION_PANEL.equals(a)) { showPanel(); return START_NOT_STICKY; }
        if (ACTION_EXPAND.equals(a)) {
            if (holder == null) { begin(i, true); }          // not previewing yet: start straight in full screen
            expand(i.getStringExtra(EXTRA_NAME), i.getStringExtra(EXTRA_PORT));
            return START_NOT_STICKY;
        }
        begin(i, false);
        return START_NOT_STICKY;
    }

    private void begin(Intent i, boolean full) {
        remove();
        Theme.init(this);
        input = i.getStringExtra(EXTRA_INPUT);
        playing = false;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        holder = new FrameLayout(this);
        holder.setBackgroundColor(0xFF000000);
        tv = new TvView(this);
        holder.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        lp = new WindowManager.LayoutParams(full ? sw : i.getIntExtra(EXTRA_W, 480), full ? sh : i.getIntExtra(EXTRA_H, 270), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = full ? 0 : i.getIntExtra(EXTRA_X, 64);
        lp.y = full ? 0 : i.getIntExtra(EXTRA_Y, 60);
        try { wm.addView(holder, lp); } catch (RuntimeException e) { OmniLog.state("hdmi-overlay addView FAILED " + e.getClass().getSimpleName()); stopSelf(); return; }
        tunedAt = SystemClock.uptimeMillis();
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String x) { playing = false; setPanel(PlayerActivity.Panel.S_ERROR, "The TV did not accept this input. Use the Input button on your remote."); }
            @Override public void onDisconnected(String x) { playing = false; setPanel(PlayerActivity.Panel.S_ERROR, "The input connection was lost."); }
            @Override public void onVideoAvailable(String x) {
                playing = true; retries = 0;
                OmniLog.state("hdmi-overlay VIDEO AVAILABLE after " + (SystemClock.uptimeMillis() - tunedAt) + " ms");
                setPanel(PlayerActivity.Panel.S_PLAYING, "");
                ui.removeCallbacks(hidePanel);
                if (fullscreen) ui.postDelayed(hidePanel, 3500);
            }
            @Override public void onVideoUnavailable(String x, int r) {
                OmniLog.state("hdmi-overlay video unavailable reason=" + r);
                if (r == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING || r == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) { playing = false; setPanel(PlayerActivity.Panel.S_LOADING, ""); }
                else { playing = false; setPanel(PlayerActivity.Panel.S_ERROR, "No picture from this input. Is the device switched on?"); }
            }
        });
        try {
            tv.setStreamVolume(full ? 1f : 0f);
            tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input));
            OmniLog.state("hdmi-overlay tuned " + input + (full ? " (full screen)" : ""));
        } catch (RuntimeException e) { OmniLog.state("hdmi-overlay tune threw " + e.getClass().getSimpleName()); }
        polls = 0; hiddenSince = 0; retries = 0;
        ui.removeCallbacks(watch);
        ui.postDelayed(watch, 500);
        ui.removeCallbacks(ensureVideo);
        ui.postDelayed(ensureVideo, 5000);                               // by then the TV maker's app has had its turn
        ui.postDelayed(this::stopSelf, i.getIntExtra(EXTRA_SECONDS, 600) * 1000L);
    }

    /** Grows the window the picture is already in to the whole screen and shows the panel on top of it. No second tune: the picture keeps playing through the move. */
    private void expand(String name, String port) {
        if (holder == null || lp == null) return;
        fullscreen = true;
        tv.setStreamVolume(1f);                                       // the preview is muted; the full-screen view has sound
        if (panel == null) {
            panel = new PlayerActivity.Panel(this);
            holder.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        }
        panel.setChannel(name == null ? "HDMI" : name, port == null ? "Input" : port, 1, 1);
        OmniLog.state("hdmi-overlay expand playing=" + playing);
        if (playing) { panel.setState(PlayerActivity.Panel.S_PLAYING, ""); ui.removeCallbacks(hidePanel); ui.postDelayed(hidePanel, 3500); }
        else panel.setState(PlayerActivity.Panel.S_LOADING, "");
        panel.show(true);
        final int x0 = lp.x, y0 = lp.y, w0 = lp.width, h0 = lp.height;
        final int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(280);
        va.setInterpolator(new DecelerateInterpolator(1.6f));
        va.addUpdateListener(an -> {
            if (holder == null) return;
            float t = (float) an.getAnimatedValue();
            lp.x = (int) (x0 * (1 - t)); lp.y = (int) (y0 * (1 - t));
            lp.width = (int) (w0 + (sw - w0) * t); lp.height = (int) (h0 + (sh - h0) * t);
            try { wm.updateViewLayout(holder, lp); } catch (RuntimeException ignored) { }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() { @Override public void onAnimationEnd(android.animation.Animator a) { OmniLog.state("hdmi-overlay expanded to " + lp.width + "x" + lp.height + " playing=" + playing); } });
        va.start();
        polls = 0; hiddenSince = 0; retries = 0;
        ui.removeCallbacks(watch);
        ui.postDelayed(watch, 500);
        ui.removeCallbacks(ensureVideo);
        if (!playing) ui.postDelayed(ensureVideo, 1200);
        OmniLog.state("hdmi-overlay expand");
    }

    private void showPanel() {
        if (panel == null || !fullscreen) return;
        panel.show(true);
        ui.removeCallbacks(hidePanel);
        ui.postDelayed(hidePanel, 3500);
    }

    private void setPanel(int state, String detail) {
        OmniLog.state("hdmi-overlay panel state=" + state + " fullscreen=" + fullscreen + " panel=" + (panel != null));
        if (panel == null) return;
        panel.setState(state, detail);
        if (state != PlayerActivity.Panel.S_PLAYING) panel.show(true);
    }

    private void remove() {
        ui.removeCallbacks(watch);
        ui.removeCallbacks(ensureVideo);
        ui.removeCallbacks(hidePanel);
        if (tv != null) { try { tv.reset(); } catch (RuntimeException ignored) { } }        // releases the TV input session: without this every preview leaves one open and the HDMI service eventually refuses new ones
        if (holder != null && wm != null) { try { wm.removeView(holder); } catch (RuntimeException ignored) { } }
        holder = null; panel = null; tv = null;
    }

    @Override public void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        remove();
        fullscreen = false;
        super.onDestroy();
    }
}
