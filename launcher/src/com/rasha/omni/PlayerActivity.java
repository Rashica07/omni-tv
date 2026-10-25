package com.rasha.omni;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * Live playback with the platform MediaPlayer (HLS is built in; no library). Up/Down or CH+/CH- switch channel in the list you came from,
 * OK shows the channel panel, Menu/Right opens audio and subtitle tracks when the stream has any. Failure is shown as it is: loading,
 * buffering, "this source stopped", "no network". If a channel has alternate URLs they are tried in order. No programme data is shown
 * unless an EPG exists; today none is configured, so the panel says so.
 */
public class PlayerActivity extends Activity implements SurfaceHolder.Callback {
    static final String EXTRA_KEYS = "keys", EXTRA_INDEX = "index";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private SurfaceView surface;
    private Panel panel;
    private MediaPlayer mp;
    private List<PtvModel.Channel> list = new ArrayList<>();
    private long playingSince;                // elapsedRealtime when the picture started, 0 when not playing
    private int idx;
    private int urlIdx;
    private int retries;
    private int state;                       // see Panel.S_*
    private long startedAt;
    private boolean surfaceReady;
    private boolean menuOpen;
    private final List<String> menuLabels = new ArrayList<>();
    private final List<int[]> menuTracks = new ArrayList<>();
    private int menuSel;
    private final Runnable hide = () -> { if (state == Panel.S_PLAYING) panel.show(false); };
    private final Runnable retry = this::startCurrent;
    private final Runnable digitCommit = this::commitDigits;
    private final StringBuilder digits = new StringBuilder();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        surface = new SurfaceView(this);
        surface.getHolder().addCallback(this);
        root.addView(surface, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        panel = new Panel(this);
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        List<PtvModel.Channel> all = PtvCatalog.load(this);
        String[] keys = getIntent().getStringArrayExtra(EXTRA_KEYS);
        // ids from the page, resolved against the catalogue by id (never by position or name)
        if (keys != null) for (String k : keys) for (PtvModel.Channel c : all) if (c.id.equals(k) && c.playable()) list.add(c);
        idx = Math.max(0, Math.min(list.size() - 1, getIntent().getIntExtra(EXTRA_INDEX, 0)));
        if (list.isEmpty()) { finish(); return; }
    }

    @Override public void surfaceCreated(SurfaceHolder h) {
        surfaceReady = true;
        if (mp == null) startCurrent(); else mp.setDisplay(h);
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }

    @Override public void surfaceDestroyed(SurfaceHolder h) { surfaceReady = false; if (mp != null) mp.setDisplay(null); }

    private PtvModel.Channel cur() { return list.get(idx); }

    private void startCurrent() {
        ui.removeCallbacks(retry);
        release();
        if (!surfaceReady) return;
        PtvModel.Channel ch = cur();
        List<String> urls = ch.urls();
        if (urlIdx >= urls.size()) urlIdx = 0;
        String url = urls.get(urlIdx);
        setState(Panel.S_LOADING, "");
        panel.setChannel(ch.name, PtvRules.group(ch.country), idx + 1, list.size());
        flushUsage();
        panel.show(true);
        startedAt = SystemClock.uptimeMillis();
        OmniLog.state("play id=" + ch.id + " channel=" + ch.name + " host=" + host(url) + " alt=" + urlIdx);
        try {
            mp = new MediaPlayer();
            mp.setDisplay(surface.getHolder());
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(url);
            mp.setOnPreparedListener(m -> {
                m.start();
                retries = 0;
                OmniLog.state("play ready ms=" + (SystemClock.uptimeMillis() - startedAt));
                setState(Panel.S_PLAYING, "");
                if (playingSince == 0) playingSince = SystemClock.elapsedRealtime();
                ui.removeCallbacks(hide);
                ui.postDelayed(hide, 4000);
            });
            mp.setOnInfoListener((m, what, extra) -> {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) setState(Panel.S_BUFFERING, "");
                else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) setState(Panel.S_PLAYING, "");
                return false;
            });
            mp.setOnVideoSizeChangedListener((m, w, h) -> fit(w, h));
            mp.setOnErrorListener((m, what, extra) -> { failed(what, extra); return true; });
            mp.prepareAsync();
        } catch (Exception e) {
            failed(-1, 0);
        }
    }

    private void failed(int what, int extra) {
        OmniLog.state("play error what=" + what + " extra=" + extra + " alt=" + urlIdx);
        release();
        flushUsage();
        PtvModel.Channel ch = cur();
        if (urlIdx + 1 < ch.urls().size()) { urlIdx++; ui.post(retry); return; }          // try the next alternate URL
        if (retries < 2) {                                                                // then retry the first after a pause
            retries++;
            urlIdx = 0;
            setState(Panel.S_LOADING, "Trying again (" + retries + " of 2)");
            ui.postDelayed(retry, 2500);
            return;
        }
        boolean online = true;
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            online = cm.getActiveNetwork() != null;
        } catch (RuntimeException ignored) { }
        setState(Panel.S_ERROR, online ? "This channel stopped responding. Its source may be down or blocking this TV. Up/Down to change channel."
                : "The TV is not connected to a network.");
        panel.show(true);
    }

    private void setState(int s, String detail) {
        state = s;
        panel.setState(s, detail);
    }

    private void fit(int w, int h) {
        if (w <= 0 || h <= 0) return;
        int sw = surface.getRootView().getWidth(), sh = surface.getRootView().getHeight();
        float r = Math.min(sw / (float) w, sh / (float) h);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (w * r), (int) (h * r), Gravity.CENTER);
        surface.setLayoutParams(lp);
    }

    private void release() {
        if (mp != null) {
            try { mp.setOnPreparedListener(null); mp.setOnErrorListener(null); mp.setOnInfoListener(null); mp.release(); } catch (RuntimeException ignored) { }
            mp = null;
        }
    }

    /** Adds the time this channel was actually on screen to the local statistics (ignored below the session threshold). */
    private void flushUsage() {
        if (playingSince == 0 || list.isEmpty()) return;
        long ms = SystemClock.elapsedRealtime() - playingSince;
        playingSince = 0;
        PtvCatalog.recordWatch(this, cur().id, ms);
        OmniLog.state("watched id=" + cur().id + " ms=" + ms);
    }

    private void go(int to) {
        if (list.size() < 2) return;
        flushUsage();
        idx = (to + list.size()) % list.size();
        urlIdx = 0;
        retries = 0;
        menuOpen = false;
        startCurrent();
    }

    private void commitDigits() {
        if (digits.length() > 0) {
            int n = Integer.parseInt(digits.toString());
            digits.setLength(0);
            if (n >= 1 && n <= list.size()) go(n - 1);
        }
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (menuOpen) return menuKey(code);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_CHANNEL_UP: go(idx - 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: case KeyEvent.KEYCODE_CHANNEL_DOWN: go(idx + 1); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                ui.removeCallbacks(hide);
                panel.show(!panel.visible());
                if (panel.visible() && state == Panel.S_PLAYING) ui.postDelayed(hide, 6000);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: case KeyEvent.KEYCODE_MENU: openMenu(); return true;
            default:
                if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9 && digits.length() < 3) {
                    digits.append(code - KeyEvent.KEYCODE_0);
                    panel.setDigits(digits.toString());
                    ui.removeCallbacks(digitCommit);
                    ui.postDelayed(digitCommit, 1500);
                    return true;
                }
                return super.onKeyDown(code, e);
        }
    }

    private void openMenu() {
        menuLabels.clear();
        menuTracks.clear();
        try {
            if (mp != null) {
                MediaPlayer.TrackInfo[] t = mp.getTrackInfo();
                for (int i = 0; i < t.length; i++) {
                    int type = t[i].getTrackType();
                    if (type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO || type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT
                            || type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                        String lang = t[i].getLanguage();
                        boolean audio = type == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO;
                        menuLabels.add((audio ? "Audio" : "Subtitles") + (lang == null || lang.equals("und") ? "" : ": " + lang));
                        menuTracks.add(new int[]{i});
                    }
                }
            }
        } catch (RuntimeException ignored) { }
        if (menuLabels.isEmpty()) {
            panel.setMenu(null, 0, "This stream offers no audio or subtitle choices.");
            panel.show(true);
            ui.removeCallbacks(hide);
            ui.postDelayed(hide, 3000);
            return;
        }
        menuOpen = true;
        menuSel = 0;
        panel.setMenu(menuLabels, menuSel, null);
        panel.show(true);
        ui.removeCallbacks(hide);
    }

    private boolean menuKey(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: menuSel = Math.max(0, menuSel - 1); break;
            case KeyEvent.KEYCODE_DPAD_DOWN: menuSel = Math.min(menuLabels.size() - 1, menuSel + 1); break;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                try { if (mp != null) mp.selectTrack(menuTracks.get(menuSel)[0]); } catch (RuntimeException ignored) { }
                closeMenu();
                return true;
            case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_DPAD_LEFT: closeMenu(); return true;
            default: return true;
        }
        panel.setMenu(menuLabels, menuSel, null);
        return true;
    }

    private void closeMenu() {
        menuOpen = false;
        panel.setMenu(null, 0, null);
        ui.postDelayed(hide, 2500);
    }

    @Override protected void onStop() {
        super.onStop();
        flushUsage();
        ui.removeCallbacksAndMessages(null);
        release();
    }

    @Override protected void onStart() {
        super.onStart();
        if (surfaceReady && mp == null) startCurrent();
    }

    private static String host(String u) {
        try { return new URI(u).getHost(); } catch (Exception e) { return "?"; }
    }

    /** Overlay: channel panel (glass), state messages, digit entry, track menu. Drawn, not inflated. */
    static final class Panel extends View {   // also used by TunerActivity
        static final int S_LOADING = 0, S_BUFFERING = 1, S_PLAYING = 2, S_ERROR = 3;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG), mid = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float shown;
        private boolean want;
        private final Spring spring = new Spring(300f, 0.88f, 0f, 0.002f, v -> { shown = v; invalidate(); });
        private String name = "", group = "", detail = "", digits = "", note;
        private int num, total, state;
        private List<String> menu;
        private int sel;

        Panel(Context c) {
            super(c);
            setFocusable(false);
            for (TextPaint t : new TextPaint[]{big, mid, small}) { t.setTypeface(Theme.geist); t.setColor(Theme.WHITE); }
            big.setTextSize(dp(34));
            mid.setTextSize(dp(18));
            small.setTextSize(dp(15));
            if (android.os.Build.VERSION.SDK_INT >= 26) { big.setFontVariationSettings("'wght' 800"); mid.setFontVariationSettings("'wght' 600"); small.setFontVariationSettings("'wght' 500"); }
            mid.setColor(Theme.TEXT2);
            small.setColor(Theme.TEXT2);
        }

        void setChannel(String n, String g, int i, int t) { name = n; group = g; num = i; total = t; invalidate(); }
        void setState(int s, String d) { state = s; detail = d; invalidate(); }
        void setDigits(String d) { digits = d; invalidate(); }
        void setMenu(List<String> m, int s, String n) { menu = m; sel = s; note = n; invalidate(); }
        boolean visible() { return want; }
        void show(boolean on) { want = on; spring.to(on ? 1f : 0f); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            boolean problem = state == S_ERROR || state == S_LOADING;
            float a = Math.max(shown, problem || state == S_BUFFERING ? 1f : 0f);
            if (a <= 0.01f) return;
            float ph = dp(110), pw = Math.min(w - dp(120), dp(760)), left = dp(60), top = h - dp(52) - ph + (1f - shown) * dp(24);
            r.set(left, top, left + pw, top + ph);
            Glass.draw(c, r, dp(22), null, 0, 0, Math.min(1f, a * (want || problem ? 1f : 0f)) , false);
            c.save();
            big.setAlpha((int) (255 * a));
            c.drawText(name, left + dp(28), top + dp(48), big);
            String stat = state == S_LOADING ? "Connecting..." : state == S_BUFFERING ? "Buffering..." : state == S_ERROR ? "Not playing" : "Live";
            mid.setAlpha((int) (255 * a));
            c.drawText(group + "   " + (total > 0 ? num + " of " + total : "") + "   " + stat, left + dp(28), top + dp(76), mid);
            small.setAlpha((int) (200 * a));
            String line = note != null ? note : detail.length() > 0 ? detail : state == S_PLAYING ? "No programme information for this channel." : "";
            c.drawText(line.length() > 90 ? line.substring(0, 90) : line, left + dp(28), top + dp(98), small);
            c.restore();
            if (digits.length() > 0) {
                big.setAlpha(255);
                c.drawText(digits, w - dp(120), dp(90), big);
            }
            if (menu != null) {
                float mh = dp(40) * menu.size() + dp(24), mw = dp(380), ml = w - mw - dp(60), mt = dp(60);
                r.set(ml, mt, ml + mw, mt + mh);
                Glass.draw(c, r, dp(18), null, 0, 0, 1f, false);
                for (int i = 0; i < menu.size(); i++) {
                    float y = mt + dp(12) + i * dp(40);
                    if (i == sel) {
                        p.setColor(Theme.WHITE);
                        r.set(ml + dp(8), y, ml + mw - dp(8), y + dp(36));
                        c.drawRoundRect(r, dp(12), dp(12), p);
                    }
                    mid.setColor(i == sel ? Theme.BLACK : Theme.WHITE);
                    c.drawText(menu.get(i), ml + dp(22), y + dp(25), mid);
                }
                mid.setColor(Theme.TEXT2);
            }
        }
    }
}
