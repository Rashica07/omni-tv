package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * The Control Center: a liquid-glass panel on the right of the screen (under half its width) that opens over whatever you are doing, with the controls themselves inside it, so nothing
 * sends you to another page: volume you can change, the inputs that have a picture (OK opens one full screen), the server (OK checks it and the answer appears in the row), the weather (OK
 * refreshes it). Round shortcuts at the bottom reach apps. Same material as the navbar ({@link Glass}); the focused row is a white pill that glides between rows, the same language as the
 * navbar's selected tab. One custom-drawn view, no child views: it costs the same as one glass pill to draw.
 * Opened and closed with the remote's INFO button (and Back). Up/Down move, Left/Right change the volume or move along the shortcuts, OK does the thing.
 */
final class ControlCenter extends FrameLayout {
    interface Actions {
        void checkServer();
        void refreshWeather();
        void openNetwork();
        void openInput(Inputs.Entry e);
        void run(String shortcut);
        void closed();
    }

    static final String[] SHORTCUTS = {"mic", "search", "tv", "weather", "play", "gear"};
    static final String[] SHORTCUT_NAMES = {"Assistant", "Search", "TV & inputs", "Weather", "Cinema", "Settings"};

    static String shortcutName(String id) { for (int i = 0; i < SHORTCUTS.length; i++) if (SHORTCUTS[i].equals(id)) return SHORTCUT_NAMES[i]; return id; }

    private static final float W = 440f, PAD = 16f, GAP = 8f, ROW = 52f, IN_ROW = 46f, ROUND = 58f, HEAD = 40f, LABEL = 24f;

    private static final int K_VOLUME = 0, K_INPUT = 1, K_SERVER = 2, K_WEATHER = 3, K_NETWORK = 4, K_ICONS = 5, K_NOTE = 6;

    private static final class Item {
        int kind; String label = "", value = ""; Inputs.Entry input; boolean focusable = true;
        final RectF r = new RectF();
    }

    private final Actions actions;
    private final Ambient ambient;
    private final View scrim;
    private final Panel panel;
    private final Spring in;
    private boolean closing, gone;

    private String sound = "";

    void setSound(String s) { sound = s == null ? "" : s; panel.invalidate(); }

    ControlCenter(Context c, Ambient amb, String network, String server, String weather, boolean cinema, Actions a) {
        super(c);
        actions = a;
        ambient = amb;
        setClickable(true);
        scrim = new View(c);
        scrim.setBackgroundColor(0x80000000);
        scrim.setAlpha(0f);
        addView(scrim, new LayoutParams(-1, -1));
        panel = new Panel(c, network, server, weather, cinema);
        LayoutParams pp = new LayoutParams(dp(W), -1, Gravity.END);
        pp.rightMargin = dp(24);
        pp.topMargin = dp(Theme.SAFE_VERT);
        pp.bottomMargin = dp(Theme.SAFE_VERT);
        addView(panel, pp);
        panel.setTranslationX(dp(W + 40));
        in = new Spring(300f, 0.86f, 0f, 0.002f, v -> {
            panel.setTranslationX(dp(W + 40) * (1f - v));
            scrim.setAlpha(Math.max(0f, Math.min(1f, v)));
            if (closing && v < 0.01f) finish();
        });
    }

    void setServer(String text) { panel.setText(K_SERVER, text); }
    void setWeather(String text) { panel.setText(K_WEATHER, text); }
    void setInputs(List<Inputs.Entry> hdmi) { panel.setInputs(hdmi); }

    void show(ViewGroup root) {
        root.addView(this, new LayoutParams(-1, -1));
        post(() -> { in.snap(0f); in.to(1f); ensureFocus(0); });
    }

    private void ensureFocus(final int attempt) {
        if (closing || !isAttachedToWindow()) return;
        if (panel.hasFocus()) { OmniLog.state("control focus ok attempt=" + attempt); return; }
        panel.requestFocus();
        if (attempt >= 6) { OmniLog.state("control focus FAILED"); return; }
        postDelayed(() -> ensureFocus(attempt + 1), attempt < 2 ? 60 : 160);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!closing && e.getAction() == KeyEvent.ACTION_DOWN) {
            if (isInfo(e)) { dismiss(); return true; }                          // INFO opened it, INFO closes it
            if (!panel.hasFocus()) { panel.requestFocus(); return true; }       // a key with no focus inside takes focus first instead of being lost
        }
        return super.dispatchKeyEvent(e);
    }

    /** The remote's INFO button, however this remote reports it. */
    static boolean isInfo(KeyEvent e) { return e.getKeyCode() == KeyEvent.KEYCODE_INFO || (e.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN && e.getScanCode() == 358); }

    void dismiss() {
        if (closing) return;
        closing = true;
        in.to(0f);
    }

    private void finish() {
        if (gone) return;
        gone = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        actions.closed();
    }

    // ------------------------------------------------------------------------------------------------ the panel

    private final class Panel extends View {
        private final List<Item> items = new ArrayList<>();
        private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG), label = new TextPaint(Paint.ANTI_ALIAS_FLAG), value = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF r = new RectF(), tmp = new RectF();
        private final AudioManager audio;
        private int focus, sub;
        private boolean muted;
        private int hasInputs;
        private final boolean cinema;
        // the white highlight glides: its box is spring-driven toward the focused row
        private float hx, hy, hw, hh;
        private final Spring sx = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hx = v; invalidate(); });
        private final Spring sy = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hy = v; invalidate(); });
        private final Spring sw = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hw = v; invalidate(); });
        private final Spring sh = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hh = v; invalidate(); });
        private final Spring fade = new Spring(420f, 0.9f, 0f, 0.002f, v -> invalidate());
        private boolean laidOut;

        Panel(Context c, String network, String server, String weather, boolean cinema) {
            super(c);
            this.cinema = cinema;
            audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
            setFocusable(true);
            for (TextPaint t : new TextPaint[]{title, label, value, small}) t.setTypeface(Theme.geist);
            title.setTextSize(dp(20)); title.setColor(Theme.WHITE);
            label.setTextSize(dp(14)); label.setColor(Theme.TEXT2);
            value.setTextSize(dp(15)); value.setColor(Theme.WHITE);
            small.setTextSize(dp(11)); small.setColor(Theme.TEXT3);
            if (Build.VERSION.SDK_INT >= 26) { title.setFontVariationSettings("'wght' 700"); label.setFontVariationSettings("'wght' 550"); value.setFontVariationSettings("'wght' 600"); small.setFontVariationSettings("'wght' 700"); }
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeJoin(Paint.Join.ROUND);
            fade.snap(0f);
            fade.to(1f);

            Item v = add(K_VOLUME, "Volume", "");
            add(K_NOTE, "INPUTS", "");                                    // header, replaced by the inputs when they arrive
            add(K_INPUT, "", "").focusable = false;                       // placeholder: "checking" until the list arrives
            if (server != null) add(K_SERVER, "OMNIPTV server", server);                 // Simple mode has no server row
            add(K_WEATHER, "Weather", weather);
            add(K_NETWORK, "Network", network);
            add(K_ICONS, "", "");
            items.get(2).label = "Checking inputs…";
            readVolume();
            focus = 0;
        }

        private Item add(int kind, String label, String value) { Item i = new Item(); i.kind = kind; i.label = label; i.value = value; items.add(i); return i; }

        void setText(int kind, String text) { for (Item i : items) if (i.kind == kind) i.value = text; invalidate(); }

        /** Replaces the placeholder with one row per HDMI input (a handful at most). */
        void setInputs(List<Inputs.Entry> hdmi) {
            String key = focusKey();
            for (int i = items.size() - 1; i >= 0; i--) if (items.get(i).kind == K_INPUT) items.remove(i);
            int at = 2;
            hasInputs = 0;
            List<Inputs.Entry> shown = new ArrayList<>();
            for (Inputs.Entry e : hdmi) if (e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) shown.add(e);
            for (Inputs.Entry e : hdmi) if (e.state != android.media.tv.TvInputManager.INPUT_STATE_CONNECTED && shown.size() < 3 && e.cecName != null) shown.add(e);   // named devices that are asleep
            if (shown.isEmpty()) { Item n = new Item(); n.kind = K_INPUT; n.label = "No input has a signal"; n.focusable = false; items.add(at, n); }
            else for (Inputs.Entry e : shown) {
                Item n = new Item(); n.kind = K_INPUT; n.input = e; n.label = e.displayName();
                n.value = ControlRows.inputState(e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED, e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY);
                items.add(at++, n);
                hasInputs++;
            }
            laidOut = false;
            restoreFocus(key);
            invalidate();
        }

        private String focusKey() { Item i = items.get(Math.min(focus, items.size() - 1)); return i.kind + ":" + (i.input != null ? i.input.id : ""); }

        private void restoreFocus(String key) {
            for (int i = 0; i < items.size(); i++) { Item it = items.get(i); if ((it.kind + ":" + (it.input != null ? it.input.id : "")).equals(key) && it.focusable) { focus = i; return; } }
            focus = 0;
        }

        private void readVolume() { muted = audio != null && Build.VERSION.SDK_INT >= 23 && audio.isStreamMute(AudioManager.STREAM_MUSIC); }

        // ---- layout: every row has a fixed height, so the panel needs no scrolling
        private void layoutItems() {
            float d = Theme.density, y = dp(PAD) + dp(HEAD);
            float x0 = dp(PAD), x1 = getWidth() - dp(PAD);
            for (Item i : items) {
                float h = i.kind == K_ICONS ? dp(ROUND) : i.kind == K_NOTE ? dp(LABEL) : i.kind == K_INPUT ? dp(IN_ROW) : dp(ROW);
                if (i.kind == K_ICONS) y += dp(6);
                i.r.set(x0, y, x1, y + h);
                y += h + dp(GAP);
            }
            laidOut = true;
            moveHighlight(true);
        }

        private RectF focusRect() {
            Item i = items.get(focus);
            if (i.kind != K_ICONS) return i.r;
            int n = shortcutCount();
            float cell = i.r.width() / n, cx = i.r.left + cell * (sub + 0.5f), dia = dp(ROUND);
            tmp.set(cx - dia / 2f, i.r.top, cx + dia / 2f, i.r.top + dia);
            return tmp;
        }

        private int shortcutCount() { return cinema ? 6 : 5; }

        private String shortcutAt(int i) {
            String[] ids = cinema ? SHORTCUTS : new String[]{"mic", "search", "tv", "weather", "gear"};
            return ids[Math.max(0, Math.min(ids.length - 1, i))];
        }

        private void moveHighlight(boolean snap) {
            RectF f = focusRect();
            if (snap || hw == 0) { sx.snap(f.left); sy.snap(f.top); sw.snap(f.width()); sh.snap(f.height()); }
            else { sx.to(f.left); sy.to(f.top); sw.to(f.width()); sh.to(f.height()); }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); laidOut = false; }

        // ---- drawing
        @Override protected void onDraw(Canvas c) {
            if (!laidOut) layoutItems();
            r.set(0, 0, getWidth(), getHeight());
            Glass.draw(c, r, dp(30), ambient, getLeft() + getTranslationX(), getTop(), 1f, true);
            c.drawText("Control Center", dp(PAD) + dp(6), dp(PAD) + dp(26), title);
            float d = Theme.density;
            for (int n = 0; n < items.size(); n++) {
                Item i = items.get(n);
                switch (i.kind) {
                    case K_NOTE: c.drawText(i.label, i.r.left + dp(8), i.r.bottom - dp(6), small); break;
                    case K_ICONS: drawShortcuts(c, i); break;
                    default: drawRow(c, i, n == focus);
                }
            }
            // the white highlight over the focused row (for the shortcut row it is the round button)
            if (hasFocus() && hw > 0) {
                float a = Math.min(1f, fade.value());
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.WHITE, (int) (255 * a)));
                r.set(hx, hy, hx + hw, hy + hh);
                c.drawRoundRect(r, hh / 2f, hh / 2f, p);
                drawFocusedContent(c);
            }
        }

        private void drawRow(Canvas c, Item i, boolean focused) {
            if (i.kind == K_INPUT && i.input == null) { c.drawText(i.label, i.r.left + dp(14), i.r.centerY() + dp(5), label); return; }
            float h = i.r.height();
            p.setStyle(Paint.Style.FILL);
            p.setColor(i.input != null && i.input.state != android.media.tv.TvInputManager.INPUT_STATE_CONNECTED ? 0x14FFFFFF : 0x24FFFFFF);
            c.drawRoundRect(i.r, h / 2f, h / 2f, p);
            drawRowText(c, i, false);
        }

        /** Text and icon of a row in the given state; the focused one is drawn black on the white pill. */
        private void drawRowText(Canvas c, Item i, boolean onWhite) {
            float h = i.r.height(), cy = i.r.centerY();
            int ic = onWhite ? 0xFF000000 : 0xFFFFFFFF;
            label.setColor(onWhite ? 0xFF000000 : Theme.TEXT2);
            value.setColor(onWhite ? 0xCC000000 : Theme.WHITE);
            float x = i.r.left + dp(16);
            String icon = i.kind == K_VOLUME ? (muted ? "mute" : "sound") : i.kind == K_INPUT ? "tv" : i.kind == K_SERVER ? "server" : i.kind == K_WEATHER ? "weather" : "wifi";
            drawGlyph(c, icon, x + dp(12), cy, dp(1) * 0.75f, ic);
            x += dp(36);
            if (i.kind == K_VOLUME) {
                if (sound.isEmpty()) c.drawText(muted ? "Muted" : "Volume", x, cy + dp(5), label);
                else {                                                  // where the sound goes, under the word Volume
                    c.drawText(muted ? "Muted" : "Volume", x, cy - dp(1), label);
                    small.setColor(onWhite ? 0x99000000 : Theme.TEXT3);
                    CharSequence so = TextUtils.ellipsize(sound, small, dp(74), TextUtils.TruncateAt.END);
                    c.drawText(so, 0, so.length(), x, cy + dp(14), small);
                    small.setColor(Theme.TEXT3);
                }
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), cur = muted ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                float tx0 = x + dp(78), tx1 = i.r.right - dp(60), ty = cy;
                p.setStyle(Paint.Style.FILL);
                p.setColor(onWhite ? 0x33000000 : 0x40FFFFFF);
                tmp.set(tx0, ty - dp(3), tx1, ty + dp(3)); c.drawRoundRect(tmp, dp(3), dp(3), p);
                p.setColor(ic);
                float f = max <= 0 ? 0 : cur / (float) max;
                tmp.set(tx0, ty - dp(3), tx0 + (tx1 - tx0) * f, ty + dp(3)); c.drawRoundRect(tmp, dp(3), dp(3), p);
                c.drawCircle(tx0 + (tx1 - tx0) * f, ty, dp(7), p);
                String pc = ControlRows.volumePercent(cur, max) + "%";
                value.setTextAlign(Paint.Align.RIGHT); c.drawText(pc, i.r.right - dp(18), cy + dp(5), value); value.setTextAlign(Paint.Align.LEFT);
                return;
            }
            String l = i.label;
            if (i.kind == K_INPUT) {
                float vw = value.measureText(i.value);
                CharSequence nm = TextUtils.ellipsize(l, label, i.r.width() - dp(36 + 16 + 24) - vw, TextUtils.TruncateAt.END);
                label.setColor(onWhite ? 0xFF000000 : Theme.WHITE);
                c.drawText(nm, 0, nm.length(), x, cy + dp(5), label);
                value.setTextAlign(Paint.Align.RIGHT); c.drawText(i.value, i.r.right - dp(18), cy + dp(5), value); value.setTextAlign(Paint.Align.LEFT);
                return;
            }
            c.drawText(l, x, cy + dp(5), label);
            float lx = x + label.measureText(l) + dp(12);
            CharSequence v = TextUtils.ellipsize(i.value, value, i.r.right - dp(16) - lx, TextUtils.TruncateAt.END);
            value.setTextAlign(Paint.Align.RIGHT);
            c.drawText(v, 0, v.length(), i.r.right - dp(18), cy + dp(5), value);
            value.setTextAlign(Paint.Align.LEFT);
        }

        private void drawShortcuts(Canvas c, Item i) {
            int n = shortcutCount();
            float cell = i.r.width() / n, dia = dp(ROUND);
            for (int s = 0; s < n; s++) {
                float cx = i.r.left + cell * (s + 0.5f);
                p.setStyle(Paint.Style.FILL);
                p.setColor(0x24FFFFFF);
                c.drawCircle(cx, i.r.top + dia / 2f, dia / 2f, p);
                drawGlyph(c, shortcutAt(s), cx, i.r.top + dia / 2f, dp(1), 0xFFFFFFFF);
                String nm = shortcutName(shortcutAt(s));
                small.setTextAlign(Paint.Align.CENTER);
                small.setColor(Theme.TEXT2);
                c.drawText(nm, cx, i.r.top + dia + dp(14), small);
                small.setTextAlign(Paint.Align.LEFT);
                small.setColor(Theme.TEXT3);
            }
        }

        /** Redraws the focused row's content in black over the white pill. */
        private void drawFocusedContent(Canvas c) {
            Item i = items.get(focus);
            if (i.kind == K_ICONS) { drawGlyph(c, shortcutAt(sub), hx + hw / 2f, hy + hh / 2f, dp(1), 0xFF000000); return; }
            c.save();
            c.clipRect(hx, hy, hx + hw, hy + hh);
            drawRowText(c, i, true);
            c.restore();
        }

        /** The glyphs are a few strokes each: nothing is allocated while drawing. [u] scales them (1 = a 72-unit round button). */
        private void drawGlyph(Canvas c, String kind, float cx, float cy, float u, int color) {
            float k = u;
            ink.setColor(color); ink.setStrokeWidth(Math.max(2f, 2.4f * k)); p.setColor(color);
            switch (kind) {
                case "search": c.drawCircle(cx - 2 * k, cy - 2 * k, 8 * k, ink); c.drawLine(cx + 4 * k, cy + 4 * k, cx + 11 * k, cy + 11 * k, ink); break;
                case "tv": r.set(cx - 12 * k, cy - 9 * k, cx + 12 * k, cy + 6 * k); c.drawRoundRect(r, 3 * k, 3 * k, ink); c.drawLine(cx - 6 * k, cy + 11 * k, cx + 6 * k, cy + 11 * k, ink); break;
                case "wifi": for (int a = 1; a <= 3; a++) { float rad = a * 6 * k; r.set(cx - rad, cy + 8 * k - rad, cx + rad, cy + 8 * k + rad); c.drawArc(r, 225, 90, false, ink); } p.setStyle(Paint.Style.FILL); c.drawCircle(cx, cy + 8 * k, 1.8f * k, p); break;
                case "sound": case "mute": {
                    path.reset(); path.moveTo(cx - 11 * k, cy - 4 * k); path.lineTo(cx - 5 * k, cy - 4 * k); path.lineTo(cx + 2 * k, cy - 10 * k); path.lineTo(cx + 2 * k, cy + 10 * k); path.lineTo(cx - 5 * k, cy + 4 * k); path.lineTo(cx - 11 * k, cy + 4 * k); path.close();
                    c.drawPath(path, ink);
                    if (kind.equals("mute")) { c.drawLine(cx + 6 * k, cy - 5 * k, cx + 14 * k, cy + 5 * k, ink); c.drawLine(cx + 14 * k, cy - 5 * k, cx + 6 * k, cy + 5 * k, ink); }
                    else { r.set(cx - 3 * k, cy - 6 * k, cx + 8 * k, cy + 6 * k); c.drawArc(r, -50, 100, false, ink); r.set(cx - 6 * k, cy - 11 * k, cx + 13 * k, cy + 11 * k); c.drawArc(r, -45, 90, false, ink); }
                    break;
                }
                case "server": for (int s = 0; s < 2; s++) { float y = cy - 10 * k + s * 11 * k; r.set(cx - 11 * k, y, cx + 11 * k, y + 8.5f * k); c.drawRoundRect(r, 3 * k, 3 * k, ink); p.setStyle(Paint.Style.FILL); c.drawCircle(cx + 6 * k, y + 4.25f * k, 1.4f * k, p); } break;
                case "weather": path.reset(); path.moveTo(cx - 10 * k, cy + 7 * k); r.set(cx - 13 * k, cy - 2 * k, cx - 4 * k, cy + 7 * k); path.arcTo(r, 90, 180, false); r.set(cx - 7 * k, cy - 10 * k, cx + 4 * k, cy + 1 * k); path.arcTo(r, 180, 200, false); r.set(cx + 1 * k, cy - 4 * k, cx + 12 * k, cy + 7 * k); path.arcTo(r, 270, 180, false); path.close(); c.drawPath(path, ink); break;
                case "mic": r.set(cx - 5 * k, cy - 12 * k, cx + 5 * k, cy + 3 * k); c.drawRoundRect(r, 5 * k, 5 * k, ink); r.set(cx - 9 * k, cy - 6 * k, cx + 9 * k, cy + 7 * k); c.drawArc(r, 0, 180, false, ink); c.drawLine(cx, cy + 7 * k, cx, cy + 12 * k, ink); break;
                case "play": r.set(cx - 12 * k, cy - 9 * k, cx + 12 * k, cy + 9 * k); c.drawRoundRect(r, 4 * k, 4 * k, ink); path.reset(); path.moveTo(cx - 3 * k, cy - 4 * k); path.lineTo(cx - 3 * k, cy + 4 * k); path.lineTo(cx + 4 * k, cy); path.close(); p.setStyle(Paint.Style.FILL); c.drawPath(path, p); break;
                default: c.drawCircle(cx, cy, 4 * k, ink); for (int a = 0; a < 8; a++) { double t = a * Math.PI / 4; c.drawLine(cx + (float) Math.cos(t) * 8 * k, cy + (float) Math.sin(t) * 8 * k, cx + (float) Math.cos(t) * 12 * k, cy + (float) Math.sin(t) * 12 * k, ink); } c.drawCircle(cx, cy, 8 * k, ink);
            }
        }

        // ---- keys
        @Override protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect prev) { super.onFocusChanged(gained, dir, prev); invalidate(); }

        private int nextFocusable(int from, int dir) {
            for (int i = from + dir; i >= 0 && i < items.size(); i += dir) if (items.get(i).focusable && items.get(i).kind != K_NOTE) return i;
            return from;
        }

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            Item cur = items.get(focus);
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: { int n = nextFocusable(focus, -1); if (n != focus) { focus = n; sub = Math.min(sub, shortcutCount() - 1); moveHighlight(false); invalidate(); } return true; }
                case KeyEvent.KEYCODE_DPAD_DOWN: { int n = nextFocusable(focus, 1); if (n != focus) { focus = n; moveHighlight(false); invalidate(); } return true; }
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    int dir = code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    if (cur.kind == K_VOLUME) { changeVolume(dir); return true; }
                    if (cur.kind == K_ICONS) { int n = Math.max(0, Math.min(shortcutCount() - 1, sub + dir)); if (n != sub) { sub = n; moveHighlight(false); invalidate(); } return true; }
                    return true;                                         // the sheet is one column: sideways does nothing, focus never leaves it
                }
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() == 0) activate(cur);
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP: changeVolume(1); return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN: changeVolume(-1); return true;
                default: return super.onKeyDown(code, e);
            }
        }

        private void changeVolume(int dir) {
            if (audio == null) return;
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int now = ControlRows.volumeStep(audio.getStreamVolume(AudioManager.STREAM_MUSIC), max, dir);
            if (muted) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0); muted = false; }
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, now, 0);
            OmniLog.state("control volume=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + max);
            invalidate();
        }

        private void activate(Item i) {
            switch (i.kind) {
                case K_VOLUME:
                    if (audio != null) { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, muted ? AudioManager.ADJUST_UNMUTE : AudioManager.ADJUST_MUTE, 0); readVolume(); invalidate(); }
                    break;
                case K_INPUT: if (i.input != null) actions.openInput(i.input); break;
                case K_SERVER: i.value = "Checking…"; invalidate(); actions.checkServer(); break;
                case K_WEATHER: i.value = "Updating…"; invalidate(); actions.refreshWeather(); break;
                case K_NETWORK: actions.openNetwork(); break;
                case K_ICONS: actions.run(shortcutAt(sub)); break;
                default: break;
            }
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            Item i = items.get(Math.min(focus, items.size() - 1));
            info.setContentDescription(i.kind == K_ICONS ? shortcutName(shortcutAt(sub)) : i.label + " " + i.value);
        }
    }
}
