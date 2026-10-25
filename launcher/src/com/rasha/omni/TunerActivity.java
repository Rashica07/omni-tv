package com.rasha.omni;

import android.app.Activity;
import android.media.tv.TvContract;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays the TV's own tuner channels with the public TvView, asking the system's tuner input service to tune. If the service refuses an app (some
 * TV makers allow only their own app), the screen says so plainly. Up/Down or CH+/CH- change channel in the list you came from.
 */
public class TunerActivity extends Activity {
    static final String EXTRA_IDS = "ids", EXTRA_INDEX = "index";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private TvView tv;
    private PlayerActivity.Panel panel;
    private final List<PtvModel.Channel> list = new ArrayList<>();
    private int idx;
    private long playingSince, startedAt;
    private final Runnable hide = () -> panel.show(false);

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        tv = new TvView(this);
        root.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        panel = new PlayerActivity.Panel(this);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        List<PtvModel.Channel> all = TunerSource.load(this);
        String[] ids = getIntent().getStringArrayExtra(EXTRA_IDS);
        if (ids != null) for (String id : ids) for (PtvModel.Channel c : all) if (c.id.equals(id)) list.add(c);
        idx = Math.max(0, Math.min(list.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));
        if (list.isEmpty()) { finish(); return; }
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String inputId) {
                OmniLog.state("tuner connection failed input=" + inputId);
                panel.setState(PlayerActivity.Panel.S_ERROR, "The TV's tuner service did not accept OMNI. Use the TV's own live TV app.");
                panel.show(true);
            }
            @Override public void onDisconnected(String inputId) {
                OmniLog.state("tuner disconnected");
                panel.setState(PlayerActivity.Panel.S_ERROR, "The tuner connection was lost.");
                panel.show(true);
            }
            @Override public void onVideoAvailable(String inputId) {
                OmniLog.state("tuner video ms=" + (SystemClock.uptimeMillis() - startedAt));
                if (playingSince == 0) playingSince = SystemClock.elapsedRealtime();
                panel.setState(PlayerActivity.Panel.S_PLAYING, "");
                ui.removeCallbacks(hide);
                ui.postDelayed(hide, 4000);
            }
            @Override public void onVideoUnavailable(String inputId, int reason) {
                OmniLog.state("tuner video unavailable reason=" + reason);
                if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING || reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_BUFFERING) {
                    panel.setState(PlayerActivity.Panel.S_LOADING, "");
                } else if (reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY) {
                    panel.setState(PlayerActivity.Panel.S_PLAYING, "Radio service: audio only.");
                } else {
                    panel.setState(PlayerActivity.Panel.S_ERROR, reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_WEAK_SIGNAL
                            ? "No signal or a weak signal on this channel. Check the antenna." : "The tuner reports no picture for this channel.");
                }
                panel.show(true);
            }
        });
        tune();
    }

    private PtvModel.Channel cur() { return list.get(idx); }

    private void tune() {
        flushUsage();
        PtvModel.Channel c = cur();
        panel.setChannel(c.name, "Antenna", idx + 1, list.size());
        panel.setState(PlayerActivity.Panel.S_LOADING, "");
        panel.show(true);
        startedAt = SystemClock.uptimeMillis();
        OmniLog.state("tune id=" + c.id + " channel=" + c.name + " no=" + c.number);
        try {
            tv.tune(c.tunerInput, TvContract.buildChannelUri(c.tunerId));
        } catch (RuntimeException e) {
            OmniLog.state("tune threw " + e.getClass().getSimpleName());
            panel.setState(PlayerActivity.Panel.S_ERROR, "The TV did not allow tuning from OMNI.");
        }
    }

    private void flushUsage() {
        if (playingSince == 0 || list.isEmpty()) return;
        long ms = SystemClock.elapsedRealtime() - playingSince;
        playingSince = 0;
        PtvCatalog.recordWatch(this, cur().id, ms);
    }

    private void go(int to) {
        if (list.size() < 2) return;
        idx = (to + list.size()) % list.size();
        tune();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP: go(idx - 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: go(idx + 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                ui.removeCallbacks(hide);
                panel.show(!panel.visible());
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override protected void onStop() {
        super.onStop();
        flushUsage();
        ui.removeCallbacksAndMessages(null);
        tv.reset();
    }
}
