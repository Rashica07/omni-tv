package com.rasha.omni;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.Date;
import java.util.Locale;

import static com.rasha.omni.Theme.dp;

/**
 * The system status area: network (with the OMNIPTV server dot), weather, account, and the clock, in one glass capsule at the right of the
 * header. One focusable view; Left/Right move between the three zones (and out to the navigation), OK opens the zone's sheet, Down goes to the page.
 * Only facts are drawn: the server dot appears after a real check, the temperature only with a stored location and a reading, the account zone
 * is a generic glyph because the active Google account is not readable by an ordinary app. The clock redraws once a minute.
 */
final class StatusBar extends View {
    interface Listener { void onActivate(int zone); }

    static final int NET = 0, WEATHER = 1, ACCOUNT = 2;
    private static final float H = 56f, ZONE_H = 44f, NET_W = 38f, WX_W = 68f, ACC_W = 38f, PAD = 6f, GAP = 2f, CLOCK_W = 78f;

    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint time = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), temp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), date = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private Ambient ambient;
    private int zone;
    private float filled;
    private final Spring fillSpring = new Spring(420f, 0.9f, 0f, 0.002f, v -> { filled = v; invalidate(); });
    private final Spring moveSpring = new Spring(420f, 0.85f, 0f, 0.5f, v -> { zoneX = v; invalidate(); });
    private float zoneX;

    // data (all set by the activity from real sources)
    private int transport = NetKind.NONE;
    private int serverState = PtvServer.UNSET;
    private boolean checked;                 // a server check has actually completed
    private boolean havePlace;
    private double tempC = Double.NaN;
    private int wxKind = WeatherCodes.UNKNOWN;
    private boolean stale;
    private String timeText = "", dateText = "";

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refreshClock(); }
    };

    StatusBar(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        for (TextPaint t : new TextPaint[]{time, temp, date}) t.setTypeface(Theme.geist);
        time.setTextSize(dp(23));
        time.setColor(Theme.WHITE);
        temp.setTextSize(dp(18));
        date.setTextSize(dp(11.5f));
        date.setColor(Theme.TEXT2);
        if (Build.VERSION.SDK_INT >= 26) {
            time.setFontVariationSettings("'wght' 650");
            temp.setFontVariationSettings("'wght' 600");
            date.setFontVariationSettings("'wght' 500");
        }
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        refreshClock();
    }

    void setAmbient(Ambient a) { ambient = a; }

    static int widthPx() { return dp(PAD * 2 + NET_W + WX_W + ACC_W + GAP * 2 + 10 + CLOCK_W); }

    static int heightPx() { return dp(H); }

    void setNetwork(int transport, boolean serverChecked, int serverState) {
        this.transport = transport;
        this.checked = serverChecked;
        this.serverState = serverState;
        invalidate();
    }

    void setWeather(boolean havePlace, double tempC, int kind, boolean stale) {
        this.havePlace = havePlace;
        this.tempC = tempC;
        this.wxKind = kind;
        this.stale = stale;
        invalidate();
    }

    // ------------------------------------------------------------ input

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        fillSpring.to(gained ? 1f : 0f);
        if (gained && direction == FOCUS_RIGHT) select(NET, false);       // arrived from the navigation on the left
    }

    private void select(int z, boolean animate) {
        zone = z;
        if (animate) moveSpring.to(z); else moveSpring.snap(z);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (zone == 0) return false;                  // leave toward the navigation
                select(zone - 1, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (zone < 2) select(zone + 1, true);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP: return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                listener.onActivate(zone);
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    // ------------------------------------------------------------ clock

    private void refreshClock() {
        Context c = getContext();
        Locale l = c.getResources().getConfiguration().locale;
        Date now = new Date();
        boolean h24 = DateFormat.is24HourFormat(c);
        String t = DateFormat.format(DateFormat.getBestDateTimePattern(l, h24 ? "Hm" : "hm"), now).toString();
        timeText = h24 ? t : t.replace(" AM", "").replace(" PM", "").replace("AM", "").replace("PM", "").trim();
        dateText = DateFormat.format(DateFormat.getBestDateTimePattern(l, "EEEdMMM"), now).toString().replace(".", "");
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        f.addAction(Intent.ACTION_LOCALE_CHANGED);
        getContext().registerReceiver(tick, f);
        refreshClock();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        try { getContext().unregisterReceiver(tick); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ drawing

    private float zoneLeft(int z) {
        float x = dp(PAD);
        if (z >= 1) x += dp(NET_W + GAP);
        if (z >= 2) x += dp(WX_W + GAP);
        return x;
    }

    private float zoneWidth(int z) { return dp(z == 0 ? NET_W : z == 1 ? WX_W : ACC_W); }

    @Override protected void onDraw(Canvas c) {
        float h = dp(H), cy = h / 2f;
        r.set(0, 0, getWidth(), h);
        Glass.draw(c, r, h / 2f, ambient, getLeft(), getTop(), 1f, false);

        // zone highlight: white pill when the bar has focus (the control-focus rule), a faint glass pill only on the selected zone otherwise
        float zl = zoneLeft(0) + (zoneLeft(1) - zoneLeft(0)) * Math.min(zoneX, 1f) + (zoneLeft(2) - zoneLeft(1)) * Math.max(0f, zoneX - 1f);
        float zw = zoneWidth(0) + (zoneWidth(1) - zoneWidth(0)) * Math.min(zoneX, 1f) + (zoneWidth(2) - zoneWidth(1)) * Math.max(0f, zoneX - 1f);
        if (filled > 0.01f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.alpha(Theme.WHITE, (int) (255 * filled)));
            r.set(zl, cy - dp(ZONE_H) / 2f, zl + zw, cy + dp(ZONE_H) / 2f);
            c.drawRoundRect(r, dp(ZONE_H) / 2f, dp(ZONE_H) / 2f, p);
        }
        for (int z = 0; z < 3; z++) {
            float sel = filled * Math.max(0f, 1f - Math.abs(zoneX - z));
            int fg = Theme.lerp(Theme.TEXT2, Theme.BLACK, sel);
            if (sel < 0.5f) fg = Theme.lerp(Theme.TEXT2, Theme.WHITE, 0.55f);       // unfocused glyphs stay readable, not dim
            float left = zoneLeft(z), cx = left + (z == 1 ? dp(24) : zoneWidth(z) / 2f);
            if (z == NET) drawNet(c, cx, cy, fg, sel);
            else if (z == WEATHER) drawWeather(c, left, cx, cy, fg);
            else drawAccount(c, cx, cy, fg);
        }

        // divider and clock
        float dx = zoneLeft(2) + dp(ACC_W) + dp(5);
        p.setColor(Theme.alpha(Theme.WHITE, 40));
        c.drawRect(dx, cy - dp(14), dx + 1, cy + dp(14), p);
        float cx = dx + dp(10);
        c.drawText(timeText, cx, cy + dp(2), time);
        c.drawText(dateText, cx, cy + dp(16), date);
    }

    private void drawNet(Canvas c, float cx, float cy, int fg, float sel) {
        float u = dp(22) / 24f;
        ink.setColor(fg);
        ink.setStrokeWidth(2.1f * u);
        ink.setStyle(Paint.Style.STROKE);
        int sv = c.save();
        c.translate(cx - 12f * u, cy - 12f * u);
        if (transport == NetKind.ETHERNET) {                     // a wired port
            r.set(4f * u, 6f * u, 20f * u, 15f * u);
            c.drawRoundRect(r, 2f * u, 2f * u, ink);
            c.drawLine(8f * u, 15f * u, 8f * u, 18f * u, ink);
            c.drawLine(16f * u, 15f * u, 16f * u, 18f * u, ink);
            c.drawLine(8f * u, 10f * u, 16f * u, 10f * u, ink);
        } else {                                                  // Wi-Fi arcs; with no connection they are drawn with a slash
            for (int i = 0; i < 3; i++) {
                float rad = (4.5f + i * 4.2f) * u;
                r.set(12f * u - rad, 19f * u - rad, 12f * u + rad, 19f * u + rad);
                c.drawArc(r, -135f, 90f, false, ink);
            }
            p.setStyle(Paint.Style.FILL);
            p.setColor(fg);
            c.drawCircle(12f * u, 18.5f * u, 1.5f * u, p);
            if (transport == NetKind.NONE) c.drawLine(3f * u, 3f * u, 21f * u, 21f * u, ink);
        }
        c.restoreToCount(sv);
        // server dot: only after a real check, and meaningful colour only here
        if (checked && serverState != PtvServer.UNSET) {
            boolean ok = serverState == PtvServer.REACHABLE;
            p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.BLACK);
            c.drawCircle(cx + dp(10), cy + dp(10), dp(5.5f), p);
            p.setColor(ok ? 0xFF35C759 : 0xFFFFB020);
            c.drawCircle(cx + dp(10), cy + dp(10), dp(4f), p);
        }
    }

    private void drawWeather(Canvas c, float left, float cx, float cy, int fg) {
        drawGlyph(c, wxKind, cx, cy, fg);
        temp.setColor(fg);
        String t = havePlace ? WeatherCodes.temp(tempC) : "";
        if (t.isEmpty()) t = havePlace ? "–" : "";
        temp.setAlpha(stale ? 150 : 255);
        c.drawText(t, left + dp(40), cy + dp(6.5f), temp);
        temp.setAlpha(255);
    }

    /** Small weather glyphs on a 24-unit grid, one stroke weight. */
    private void drawGlyph(Canvas c, int kind, float cx, float cy, int color) {
        float u = dp(24) / 24f;
        ink.setColor(color);
        ink.setStrokeWidth(2f * u);
        ink.setStyle(Paint.Style.STROKE);
        p.setColor(color);
        int sv = c.save();
        c.translate(cx - 12f * u, cy - 12f * u);
        switch (kind) {
            case WeatherCodes.CLEAR:
                c.drawCircle(12f * u, 12f * u, 4.2f * u, ink);
                for (int k = 0; k < 8; k++) {
                    double a = Math.toRadians(k * 45);
                    c.drawLine(12f * u + (float) Math.cos(a) * 7f * u, 12f * u + (float) Math.sin(a) * 7f * u, 12f * u + (float) Math.cos(a) * 9.4f * u, 12f * u + (float) Math.sin(a) * 9.4f * u, ink);
                }
                break;
            case WeatherCodes.PARTLY:
                c.drawCircle(9f * u, 9f * u, 3.4f * u, ink);
                c.drawLine(9f * u, 2.5f * u, 9f * u, 3.6f * u, ink);
                c.drawLine(2.5f * u, 9f * u, 3.6f * u, 9f * u, ink);
                cloud(c, u, 2f);
                break;
            case WeatherCodes.FOG:
                for (int k = 0; k < 4; k++) c.drawLine((4f + (k % 2) * 2f) * u, (7f + k * 3.4f) * u, (20f - (k % 2) * 2f) * u, (7f + k * 3.4f) * u, ink);
                break;
            case WeatherCodes.RAIN:
                cloud(c, u, -1f);
                for (int k = 0; k < 3; k++) c.drawLine((8f + k * 4f) * u, 17f * u, (7f + k * 4f) * u, 20.5f * u, ink);
                break;
            case WeatherCodes.SNOW:
                cloud(c, u, -1f);
                p.setStyle(Paint.Style.FILL);
                for (int k = 0; k < 3; k++) c.drawCircle((8f + k * 4f) * u, 19f * u, 1.1f * u, p);
                break;
            case WeatherCodes.STORM:
                cloud(c, u, -1f);
                path.reset();
                path.moveTo(13f * u, 15.5f * u); path.lineTo(10f * u, 19.5f * u); path.lineTo(13f * u, 19.5f * u); path.lineTo(11f * u, 23f * u);
                c.drawPath(path, ink);
                break;
            case WeatherCodes.CLOUD:
                cloud(c, u, 1f);
                break;
            default:                                           // no reading: an outline sun, honestly "no data yet"
                c.drawCircle(12f * u, 12f * u, 4.2f * u, ink);
        }
        c.restoreToCount(sv);
    }

    private void cloud(Canvas c, float u, float dy) {
        path.reset();
        path.moveTo(7f * u, (18f + dy) * u);
        path.cubicTo(3.5f * u, (18f + dy) * u, 3.5f * u, (12f + dy) * u, 7.5f * u, (12f + dy) * u);
        path.cubicTo(8.5f * u, (7.5f + dy) * u, 15.5f * u, (7.5f + dy) * u, 16.5f * u, (12f + dy) * u);
        path.cubicTo(21f * u, (12f + dy) * u, 21f * u, (18f + dy) * u, 17f * u, (18f + dy) * u);
        path.close();
        c.drawPath(path, ink);
    }

    private void drawAccount(Canvas c, float cx, float cy, int fg) {
        float u = dp(22) / 24f;
        ink.setColor(fg);
        ink.setStrokeWidth(2.1f * u);
        ink.setStyle(Paint.Style.STROKE);
        c.drawCircle(cx, cy - 4f * u, 4f * u, ink);
        r.set(cx - 8f * u, cy + 2f * u, cx + 8f * u, cy + 14f * u);
        c.drawArc(r, 200f, 140f, false, ink);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        String[] n = {"Network", "Weather", "Account"};
        info.setContentDescription("Status, " + n[zone] + ", " + timeText);
    }
}
