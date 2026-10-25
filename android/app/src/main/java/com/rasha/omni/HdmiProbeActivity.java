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
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Developer probe (only runs with `setprop log.tag.OmniDebugOpen DEBUG`): can a third-party app show an HDMI input inside a small TvView, and what does the platform report?
 * Muted, sized like a preview tile, closes itself after 10 s. Evidence for docs/PLAN-NATIVE-OS.md; not a feature.
 */
public class HdmiProbeActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView note;
    private TvView probeView;
    private long t0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!android.util.Log.isLoggable("OmniDebugOpen", android.util.Log.DEBUG)) { finish(); return; }
        Theme.init(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101010);
        TvView tv = new TvView(this);
        probeView = tv;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (480 * Theme.density), (int) (270 * Theme.density), Gravity.TOP | Gravity.START);
        lp.leftMargin = (int) (64 * Theme.density); lp.topMargin = (int) (60 * Theme.density);
        root.addView(tv, lp);
        note = new TextView(this);
        note.setTextColor(0xFFFFFFFF); note.setTextSize(15);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        np.leftMargin = (int) (64 * Theme.density); np.bottomMargin = (int) (30 * Theme.density);
        root.addView(note, np);
        setContentView(root);
        if ("overlay".equals(getIntent().getStringExtra("mode"))) { overlayMode(); return; }
        if ("antenna".equals(getIntent().getStringExtra("mode"))) {            // put the TV on a tuner channel so the HDMI input is not the selected one
            java.util.List<PtvModel.Channel> t = TunerSource.load(this);
            if (!t.isEmpty()) startActivity(new Intent(this, TunerActivity.class).putExtra(TunerActivity.EXTRA_IDS, new String[]{t.get(0).id}).putExtra(TunerActivity.EXTRA_INDEX, 0));
            finish();
            return;
        }
        if ("handoff".equals(getIntent().getStringExtra("mode"))) {        // the real click path: a preview on a card, then the click
            String id = null, nm = null, pt = null;
            for (Inputs.Entry e : Inputs.query(this)) if (e.hdmi && e.state == TvInputManager.INPUT_STATE_CONNECTED) { id = e.playId(); nm = e.displayName(); pt = e.portLabel; break; }
            if (id == null || !android.provider.Settings.canDrawOverlays(this)) { finish(); return; }
            float d = Theme.density;
            startService(new Intent(this, HdmiOverlay.class).putExtra(HdmiOverlay.EXTRA_INPUT, id).putExtra(HdmiOverlay.EXTRA_X, (int) (68 * d)).putExtra(HdmiOverlay.EXTRA_Y, (int) (360 * d))
                    .putExtra(HdmiOverlay.EXTRA_W, (int) (232 * d)).putExtra(HdmiOverlay.EXTRA_H, (int) (126 * d)));
            final String fid = id, fnm = nm, fpt = pt;
            ui.postDelayed(() -> { startActivity(new Intent(this, HdmiViewerActivity.class).putExtra(HdmiViewerActivity.EXTRA_ID, fid).putExtra(HdmiViewerActivity.EXTRA_NAME, fnm).putExtra(HdmiViewerActivity.EXTRA_PORT, fpt)); finish(); }, getIntent().getIntExtra("after_ms", 5000));
            return;
        }
        if ("viewer".equals(getIntent().getStringExtra("mode"))) {
            for (Inputs.Entry e : Inputs.query(this)) if (e.hdmi && e.state == TvInputManager.INPUT_STATE_CONNECTED) {
                startActivity(new Intent(this, HdmiViewerActivity.class).putExtra(HdmiViewerActivity.EXTRA_ID, e.id).putExtra(HdmiViewerActivity.EXTRA_NAME, e.displayName()).putExtra(HdmiViewerActivity.EXTRA_PORT, e.portLabel));
                break;
            }
            finish();
            return;
        }
        String want = getIntent().getStringExtra("input");
        List<Inputs.Entry> es = Inputs.query(this);
        String id = want;
        if (id == null) for (Inputs.Entry e : es) if (e.hdmi && e.state == TvInputManager.INPUT_STATE_CONNECTED) { id = e.id; break; }
        if (id == null) { log("no HDMI input with a signal"); ui.postDelayed(this::finish, 4000); return; }
        final String input = id;
        log("tuning " + input);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String i) { log("connection FAILED " + i); }
            @Override public void onDisconnected(String i) { log("disconnected " + i); }
            @Override public void onVideoAvailable(String i) { log("VIDEO AVAILABLE after " + (SystemClock.uptimeMillis() - t0) + " ms"); }
            @Override public void onVideoUnavailable(String i, int reason) { log("video unavailable reason=" + reason); }
        });
        t0 = SystemClock.uptimeMillis();
        try {
            tv.setStreamVolume(0f);
            tv.tune(input, TvContract.buildChannelUriForPassthroughInput(input));
        } catch (RuntimeException e) { log("tune threw " + e.getClass().getSimpleName() + " " + e.getMessage()); }
        ui.postDelayed(this::finish, 10000);
    }

    @Override protected void onDestroy() { if (probeView != null) probeView.reset(); super.onDestroy(); }

    /** Experiment B+D: picture in a service-owned overlay window; the launcher is brought back in front of the TV maker's app shortly after it takes over. */
    private void overlayMode() {
        String id = null;
        for (Inputs.Entry e : Inputs.query(this)) if (e.hdmi && e.state == TvInputManager.INPUT_STATE_CONNECTED) { id = e.id; break; }
        if (id == null) { log("no HDMI input with a signal"); finish(); return; }
        if (!android.provider.Settings.canDrawOverlays(this)) { log("overlay permission NOT granted"); finish(); return; }
        float d = Theme.density;
        startService(new Intent(this, HdmiOverlay.class).putExtra(HdmiOverlay.EXTRA_INPUT, id).putExtra(HdmiOverlay.EXTRA_X, (int) (64 * d)).putExtra(HdmiOverlay.EXTRA_Y, (int) (60 * d))
                .putExtra(HdmiOverlay.EXTRA_W, (int) (480 * d)).putExtra(HdmiOverlay.EXTRA_H, (int) (270 * d)).putExtra(HdmiOverlay.EXTRA_SECONDS, 15));
        final int relaunchMs = getIntent().getIntExtra("relaunch_ms", 3500);
        log("overlay started; launcher returns in " + relaunchMs + " ms");
        if (relaunchMs > 0) ui.postDelayed(() -> {
            try { startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP)); OmniLog.state("hdmi-probe relaunch sent"); }
            catch (RuntimeException e) { OmniLog.state("hdmi-probe relaunch FAILED " + e.getClass().getSimpleName()); }
            finish();
        }, relaunchMs); else ui.postDelayed(this::finish, 500);
    }

    private void log(String s) {
        OmniLog.state("hdmi-probe " + s);
        if (note != null) note.setText(note.getText() + (note.getText().length() > 0 ? "\n" : "") + s);
    }
}
