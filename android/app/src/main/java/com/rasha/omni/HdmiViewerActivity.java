package com.rasha.omni;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * An HDMI input full screen inside OMNI, with sound, in the OMNI panel. Normally this activity has no picture of its own: it asks {@link HdmiOverlay} to grow the window the
 * live preview is already using to the whole screen (seamless: no second tune, no black frame), and it only takes the keys (Back leaves, OK shows the name).
 * Without the "draw over other apps" permission it falls back to a TvView of its own, which works but starts from black.
 */
public class HdmiViewerActivity extends Activity {
    static final String EXTRA_ID = "input", EXTRA_NAME = "name", EXTRA_PORT = "port";

    /** Opens an HDMI input full screen inside OMNI (used by the TV page and the Control Center). */
    static void open(android.content.Context c, Inputs.Entry e) {
        c.startActivity(new Intent(c, HdmiViewerActivity.class).putExtra(EXTRA_ID, e.playId()).putExtra(EXTRA_NAME, e.displayName()).putExtra(EXTRA_PORT, e.portLabel));
        OmniLog.state("viewer input=" + e.id);
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean overlayMode;
    // fallback only
    private TvView tv;
    private PlayerActivity.Panel panel;
    private String inputId;
    private long startedAt;
    private final Runnable hide = () -> { if (panel != null) panel.show(false); };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        inputId = getIntent().getStringExtra(EXTRA_ID);
        if (inputId == null) { finish(); return; }
        String name = getIntent().getStringExtra(EXTRA_NAME), port = getIntent().getStringExtra(EXTRA_PORT);
        if (name == null) for (Inputs.Entry e : Inputs.query(this)) if (e.id.equals(inputId)) { name = e.displayName(); port = e.portLabel; }
        overlayMode = android.provider.Settings.canDrawOverlays(this);
        FrameLayout root = new FrameLayout(this);
        if (overlayMode) {
            root.setBackgroundColor(0x00000000);                      // transparent: the picture is the overlay window above this one
            setContentView(root);
            HdmiOverlay.gone = () -> { if (!isFinishing()) finish(); };       // if the picture window ends for any reason, this empty screen must not stay over the launcher
            startService(new Intent(this, HdmiOverlay.class).setAction(HdmiOverlay.ACTION_EXPAND).putExtra(HdmiOverlay.EXTRA_INPUT, inputId)
                    .putExtra(HdmiOverlay.EXTRA_NAME, name).putExtra(HdmiOverlay.EXTRA_PORT, port));
            return;
        }
        root.setBackgroundColor(0xFF000000);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        panel = new PlayerActivity.Panel(this);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        panel.setChannel(name == null ? "HDMI" : name, port == null ? "Input" : port, 1, 1);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String i) { panel.setState(PlayerActivity.Panel.S_ERROR, "The TV did not accept this input. Use the Input button on your remote."); panel.show(true); }
            @Override public void onDisconnected(String i) { panel.setState(PlayerActivity.Panel.S_ERROR, "The input connection was lost."); panel.show(true); }
            @Override public void onVideoAvailable(String i) { OmniLog.state("hdmi-viewer video ms=" + (SystemClock.uptimeMillis() - startedAt)); panel.setState(PlayerActivity.Panel.S_PLAYING, ""); ui.removeCallbacks(hide); ui.postDelayed(hide, 3500); }
            @Override public void onVideoUnavailable(String i, int reason) {
                if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING || reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) panel.setState(PlayerActivity.Panel.S_LOADING, "");
                else panel.setState(PlayerActivity.Panel.S_ERROR, "No picture from this input. Is the device switched on?");
                panel.show(true);
            }
        });
        panel.setState(PlayerActivity.Panel.S_LOADING, "");
        panel.show(true);
        startedAt = SystemClock.uptimeMillis();
        try { tv.setStreamVolume(1f); tv.tune(inputId, TvContract.buildChannelUriForPassthroughInput(inputId)); }
        catch (RuntimeException e) { panel.setState(PlayerActivity.Panel.S_ERROR, "The TV did not allow this input to be opened from OMNI."); }
    }

    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); }      // brought back in front: keep playing, do not start again

    @Override protected void onResume() { super.onResume(); HdmiOverlay.viewerResumed = true; }

    @Override protected void onPause() { HdmiOverlay.viewerResumed = false; super.onPause(); }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (overlayMode) startService(new Intent(this, HdmiOverlay.class).setAction(HdmiOverlay.ACTION_STOP));
            finish();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) {
            if (overlayMode) startService(new Intent(this, HdmiOverlay.class).setAction(HdmiOverlay.ACTION_PANEL));
            else { ui.removeCallbacks(hide); panel.show(true); ui.postDelayed(hide, 3500); }
            return true;
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onDestroy() {
        HdmiOverlay.gone = null;
        ui.removeCallbacksAndMessages(null);
        if (tv != null) tv.reset();
        super.onDestroy();
    }
}
