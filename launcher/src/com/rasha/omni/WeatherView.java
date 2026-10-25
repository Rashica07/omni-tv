package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
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
 * The weather app screen: a sky tinted by the real condition and time of day, a large reading, an hourly strip, a 7-day forecast with temperature
 * range bars and six detail tiles. Everything shown comes from the stored Open-Meteo response; with no data it says so. Two panels (Down/Up),
 * Left/Right move along the hours, OK refreshes, hold OK or Menu changes the city, Back leaves.
 */
final class WeatherView extends View {
    interface Listener { void refresh(); void pickPlace(Weather.Place p); void close(); }

    private final Listener listener;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), gp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tileBig = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);      // its own weight, set once: switching a variable font's weight per frame rebuilds the Typeface
    private final TextPaint big = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), mid = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), small = new TextPaint(TextPaint.ANTI_ALIAS_FLAG),
            cap = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private Weather.Full f = new Weather.Full();
    private String placeName = "";
    private String status = "";
    private int panel;                 // 0 hero + hours, 1 week + details
    private float scroll;              // 0..1
    private final Spring scrollSpring = new Spring(260f, 0.9f, 0f, 0.002f, v -> { scroll = v; invalidate(); });
    private int hourSel;
    private boolean picking;
    private int pickSel;
    private boolean longPressed;
    private boolean animating;
    private float angle;
    private long last;
    private final float[][] drops = new float[70][3];      // x (0..1), y (0..1), speed
    private int seed = 7;

    // Everything below is computed once per data update, never per frame (the per-frame cost used to be 98 ms, almost all of it here).
    private String[] dayLabels = new String[0];
    private String dateLine = "";
    private boolean h24;
    private LinearGradient[] barShaders = new LinearGradient[7];
    private float[][] bars = new float[7][];
    private String[] hourLabels = new String[0];
    private LinearGradient skyShader;
    private int skyKey, skyW, skyH;

    WeatherView(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        setFocusableInTouchMode(true);
        for (TextPaint t : new TextPaint[]{big, mid, small, cap}) t.setTypeface(Theme.geist);
        big.setColor(Theme.WHITE);
        tileBig.setTypeface(Theme.geist);
        tileBig.setColor(Theme.WHITE);
        mid.setColor(Theme.WHITE);
        small.setColor(0xCCFFFFFF);
        cap.setColor(0x99FFFFFF);
        big.setTextSize(dp(120));
        mid.setTextSize(dp(18));
        small.setTextSize(dp(14));
        cap.setTextSize(dp(11.5f));
        if (Build.VERSION.SDK_INT >= 26) {
            big.setFontVariationSettings("'wght' 260");
            tileBig.setFontVariationSettings("'wght' 500");
            mid.setFontVariationSettings("'wght' 600");
            small.setFontVariationSettings("'wght' 500");
            cap.setFontVariationSettings("'wght' 700");
            cap.setLetterSpacing(0.12f);
        }
        for (float[] d : drops) { d[0] = rnd(); d[1] = rnd(); d[2] = 0.5f + rnd(); }
    }

    private float rnd() { seed = seed * 1103515245 + 12345; return ((seed >>> 8) & 0xFFFF) / 65535f; }

    /** Prepares every string and shader the draw needs. Runs when data changes, not per frame. */
    private void prepare() {
        Locale loc = getContext().getResources().getConfiguration().locale;
        h24 = DateFormat.is24HourFormat(getContext());
        dateLine = DateFormat.format(DateFormat.getBestDateTimePattern(loc, "EEEEdMMMM"), new Date()).toString();
        java.text.SimpleDateFormat in = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new java.text.SimpleDateFormat("EEE", loc);
        int n = Math.min(7, f.dates.size());
        dayLabels = new String[n];
        double wl = 1e9, wh = -1e9;
        for (double[] dr : f.dayRange) { wl = Math.min(wl, dr[0]); wh = Math.max(wh, dr[1]); }
        float bx = dp(300), bw = dp(106);
        for (int i = 0; i < n; i++) {
            String day = "Today";
            if (i > 0) { try { day = out.format(in.parse(f.dates.get(i))); } catch (Exception e) { day = f.dates.get(i).substring(5); } }
            dayLabels[i] = day.isEmpty() ? day : Character.toUpperCase(day.charAt(0)) + day.substring(1);
            double[] rg = f.dayRange.get(i);
            bars[i] = WeatherFormat.range(rg[0], rg[1], wl, wh);
            barShaders[i] = new LinearGradient(bx + bw * bars[i][0], 0, bx + bw * bars[i][1] + 1, 0, 0xFF7FC2FF, 0xFFFFC83D, Shader.TileMode.CLAMP);
        }
        hourLabels = new String[f.hours.size()];
        for (int i = 0; i < hourLabels.length; i++) hourLabels[i] = WeatherFormat.hour(f.hours.get(i)[0], i == 0, h24);
    }

    void setData(Weather.Full full, String place, String statusText) {
        f = full;
        if (f.valid()) prepare();
        placeName = place == null ? "" : place;
        status = statusText == null ? "" : statusText;
        hourSel = Math.min(hourSel, Math.max(0, f.hours.size() - 1));
        updateAnimation();
        invalidate();
    }

    private int kind() { return f.valid() ? WeatherCodes.kind(f.code) : WeatherCodes.UNKNOWN; }

    private void updateAnimation() {
        int k = kind();
        boolean want = f.valid() && !Theme.reduceMotion && (k == WeatherCodes.RAIN || k == WeatherCodes.SNOW || k == WeatherCodes.STORM || (k == WeatherCodes.CLEAR && f.day));
        if (want && !animating && isAttachedToWindow()) { animating = true; last = 0; postOnAnimation(this::tick); }
        if (!want) animating = false;
    }

    private void tick() {
        if (!animating) return;
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        angle = (angle + dt * 8f) % 360f;
        int k = kind();
        for (float[] d : drops) {
            d[1] += dt * d[2] * (k == WeatherCodes.SNOW ? 0.10f : 0.9f);
            if (k == WeatherCodes.SNOW) d[0] += dt * 0.02f * (d[2] - 1f);
            if (d[1] > 1.05f) { d[1] = -0.05f; d[0] = rnd(); }
        }
        invalidate();
        postOnAnimation(this::tick);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation(); }

    @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); animating = false; }

    // ------------------------------------------------------------ keys

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (picking) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_UP: pickSel = Math.max(0, pickSel - 1); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: pickSel = Math.min(Weather.PLACES.length - 1, pickSel + 1); invalidate(); return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                    if (e.getRepeatCount() == 0) { picking = false; listener.pickPlace(Weather.PLACES[pickSel]); invalidate(); }
                    return true;
                case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_DPAD_LEFT: picking = false; invalidate(); return true;
                default: return true;
            }
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_DOWN: if (panel == 0) { panel = 1; scrollSpring.to(1f); } return true;
            case KeyEvent.KEYCODE_DPAD_UP: if (panel == 1) { panel = 0; scrollSpring.to(0f); } return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: if (panel == 0 && hourSel > 0) { hourSel--; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (panel == 0 && hourSel < f.hours.size() - 1) { hourSel++; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER:
                if (e.getRepeatCount() == 0) { longPressed = false; e.startTracking(); }
                return true;
            case KeyEvent.KEYCODE_MENU: openPicker(); return true;
            case KeyEvent.KEYCODE_BACK: listener.close(); return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) { longPressed = true; openPicker(); return true; }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) && !picking) {
            if (!longPressed) listener.refresh();
            longPressed = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    void openPicker() {
        picking = true;
        pickSel = 0;
        for (int i = 0; i < Weather.PLACES.length; i++) if (Weather.PLACES[i].name.equals(placeName)) pickSel = i;
        invalidate();
    }

    // ------------------------------------------------------------ drawing

    private long drawNs, draws;
    private final long[] seg = new long[5];
    private long mark;

    private void lap(int i) { long n = System.nanoTime(); seg[i] += n - mark; mark = n; }

    @Override protected void onDraw(Canvas c) {
        long t0 = System.nanoTime();
        drawInner(c);
        drawNs += System.nanoTime() - t0;
        if (++draws % 30 == 0 && OmniLog.flag("OmniWxProf")) {
            OmniLog.state("wx onDraw avg_ms=" + (drawNs / draws / 1000) / 1000f + " n=" + draws + " | sky=" + seg[0] / draws / 1000 + "us hero=" + seg[1] / draws / 1000 + "us week=" + seg[2] / draws / 1000 + "us foot=" + seg[3] / draws / 1000 + "us");
        }
    }

    private void drawInner(Canvas c) {
        float w = getWidth(), h = getHeight(), d = Theme.density;
        mark = System.nanoTime();
        boolean ok = f.valid();
        int k = kind();
        int[] sky = WeatherFormat.sky(k, !ok || f.day);
        int key = sky[0] * 31 + sky[1];
        if (skyShader == null || key != skyKey || (int) h != skyH) {
            skyShader = new LinearGradient(0, 0, 0, h, new int[]{sky[0], sky[1], 0xFF050608}, new float[]{0f, 0.62f, 1f}, Shader.TileMode.CLAMP);
            skyKey = key;
            skyH = (int) h;
        }
        gp.setShader(skyShader);
        c.drawRect(0, 0, w, h, gp);
        gp.setShader(null);
        if (ok) particles(c, w, h, k);
        lap(0);

        c.save();
        c.translate(0, -scroll * h);
        if (ok) {
            if (scroll < 0.995f) heroPanel(c, d, k);
            lap(1);
            if (scroll > 0.005f) weekPanel(c, d, h);
            lap(2);
        } else empty(c, d);
        c.restore();

        // footer
        small.setTextSize(dp(11.5f));
        small.setColor(0x99FFFFFF);
        String foot = (ok ? "Updated " + WeatherCodes.age(System.currentTimeMillis() - f.fetched) : status) + "     Weather data by Open-Meteo.com (CC BY 4.0)     Hold OK: change city   Back: leave";
        c.drawText(foot, dp(64), h - dp(14), small);
        small.setTextSize(dp(14));
        small.setColor(0xCCFFFFFF);
        if (picking) picker(c, w, h);
        lap(3);
    }

    private void particles(Canvas c, float w, float h, int k) {
        if (k != WeatherCodes.RAIN && k != WeatherCodes.STORM && k != WeatherCodes.SNOW) return;
        p.setColor(k == WeatherCodes.SNOW ? 0xCCFFFFFF : 0x55BFE0FF);
        p.setStrokeCap(Paint.Cap.ROUND);
        for (float[] dr : drops) {
            float x = dr[0] * w, y = dr[1] * h;
            if (k == WeatherCodes.SNOW) { p.setStyle(Paint.Style.FILL); c.drawCircle(x, y, dp(1.6f + dr[2]), p); }
            else { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1.4f)); c.drawLine(x, y, x - dp(5), y + dp(16 * dr[2]), p); }
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void glass(Canvas c, float l, float t, float rr, float b, float radius) {
        r.set(l, t, rr, b);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x55000000);
        c.drawRoundRect(r, radius, radius, p);
        p.setColor(0x1CFFFFFF);
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        p.setColor(0x30FFFFFF);
        r.inset(dp(0.5f), dp(0.5f));
        c.drawRoundRect(r, radius, radius, p);
        p.setStyle(Paint.Style.FILL);
    }

    private static String temp(double v) { return WeatherCodes.temp(v); }

    private void heroPanel(Canvas c, float d, int k) {
        mid.setTextSize(dp(22));
        c.drawText(placeName.isEmpty() ? f.place : placeName, dp(64), dp(60), mid);
        small.setColor(0xCCFFFFFF);
        c.drawText(dateLine, dp(64), dp(84), small);

        big.setTextSize(dp(124));
        String t = temp(f.temp);
        c.drawText(t.replace("°", ""), dp(60), dp(210), big);
        float tw = big.measureText(t.replace("°", ""));
        big.setTextSize(dp(60));
        c.drawText("°", dp(60) + tw + dp(2), dp(176), big);
        mid.setTextSize(dp(26));
        c.drawText(WeatherCodes.label(k) + (k == WeatherCodes.CLEAR && !f.day ? "  ·  Night" : ""), dp(64), dp(252), mid);
        small.setTextSize(dp(15));
        double[] today = f.dayRange.isEmpty() ? null : f.dayRange.get(0);
        String line = (today != null ? "H " + temp(today[1]) + "   L " + temp(today[0]) + "      " : "") + "Feels like " + temp(f.feels);
        c.drawText(line, dp(64), dp(278), small);
        small.setTextSize(dp(14));

        WeatherGlyphs.draw(c, k, f.day, dp(700), dp(176), dp(200), p, angle);

        // hourly strip
        glass(c, dp(64), dp(322), dp(896), dp(486), dp(22));
        c.drawText("HOURLY FORECAST", dp(88), dp(348), cap);
        int cols = 11, first = Math.max(0, Math.min(f.hours.size() - cols, hourSel - cols / 2));
        float colW = dp(756) / cols;
        for (int i = 0; i < cols && first + i < f.hours.size(); i++) {
            int idx = first + i;
            int[] hr = f.hours.get(idx);
            float cx = dp(88) + colW * i + colW / 2f - dp(8);
            if (idx == hourSel) {
                p.setColor(0x26FFFFFF);
                r.set(cx - colW / 2f + dp(4), dp(356), cx + colW / 2f - dp(4), dp(478));
                c.drawRoundRect(r, dp(14), dp(14), p);
            }
            small.setColor(idx == hourSel ? 0xFFFFFFFF : 0xCCFFFFFF);
            String lab = hourLabels[idx];
            c.drawText(lab, cx - small.measureText(lab) / 2f, dp(380), small);
            WeatherGlyphs.draw(c, WeatherCodes.kind(hr[1]), hr[3] == 1, cx, dp(414), dp(34), p, 0);
            String tt = temp(f.hourTemp.get(idx));
            mid.setTextSize(dp(17));
            c.drawText(tt, cx - mid.measureText(tt) / 2f, dp(450), mid);
            String pop = WeatherFormat.percent(hr[2]);
            if (!pop.isEmpty()) {
                small.setColor(0xFF7FC2FF);
                small.setTextSize(dp(11.5f));
                c.drawText(pop, cx - small.measureText(pop) / 2f, dp(468), small);
                small.setTextSize(dp(14));
            }
        }
        small.setColor(0xCCFFFFFF);
        // a hint that there is more below
        cap.setColor(0x80FFFFFF);
        c.drawText("DOWN FOR 7-DAY FORECAST AND DETAILS", dp(64), dp(512), cap);
        cap.setColor(0x99FFFFFF);
    }

    private void weekPanel(Canvas c, float d, float h) {
        c.save();
        c.translate(0, h);
        glass(c, dp(64), dp(30), dp(484), dp(500), dp(22));
        c.drawText("7-DAY FORECAST", dp(88), dp(58), cap);
        for (int i = 0; i < dayLabels.length; i++) {
            float y = dp(70) + i * dp(60);
            if (i > 0) { p.setColor(0x22FFFFFF); c.drawRect(dp(88), y - dp(4), dp(460), y - dp(3), p); }
            mid.setTextSize(dp(18));
            c.drawText(dayLabels[i], dp(88), y + dp(32), mid);
            int[] dc = f.dayCodes.get(i);
            WeatherGlyphs.draw(c, WeatherCodes.kind(dc[0]), true, dp(186), y + dp(24), dp(34), p, 0);
            String pop = WeatherFormat.percent(dc[1]);
            small.setTextSize(dp(12));
            small.setColor(0xFF7FC2FF);
            if (!pop.isEmpty()) c.drawText(pop, dp(210), y + dp(29), small);
            small.setColor(0xCCFFFFFF);
            small.setTextSize(dp(14));
            double[] rg = f.dayRange.get(i);
            mid.setTextSize(dp(17));
            c.drawText(temp(rg[0]), dp(254), y + dp(30), mid);
            float[] bar = bars[i];
            float bx = dp(300), bw = dp(106);
            p.setColor(0x33FFFFFF);
            r.set(bx, y + dp(24), bx + bw, y + dp(30));
            c.drawRoundRect(r, dp(3), dp(3), p);
            gp.setShader(barShaders[i]);
            r.set(bx + bw * bar[0], y + dp(24), bx + bw * bar[1], y + dp(30));
            c.drawRoundRect(r, dp(3), dp(3), gp);
            gp.setShader(null);
            c.drawText(temp(rg[1]), dp(418), y + dp(30), mid);
        }
        // detail tiles
        double uv = f.dayRange.isEmpty() ? -1 : f.dayRange.get(0)[2];
        String sr = f.sunrise.isEmpty() ? "" : WeatherFormat.clock(f.sunrise.get(0)), ss = f.sunset.isEmpty() ? "" : WeatherFormat.clock(f.sunset.get(0));
        tile(c, 0, "HUMIDITY", f.humidity < 0 ? "–" : f.humidity + "%", f.humidity < 0 ? "" : f.humidity < 30 ? "Dry" : f.humidity < 60 ? "Comfortable" : f.humidity < 80 ? "Humid" : "Very humid");
        tile(c, 1, "WIND", WeatherFormat.speed(f.wind), WeatherFormat.compass(f.windDir) + "  ·  " + WeatherFormat.windWord(f.wind));
        tile(c, 2, "PRESSURE", Math.round(f.pressure) + " hPa", f.pressure < 1000 ? "Low" : f.pressure < 1020 ? "Normal" : "High");
        tile(c, 3, "UV INDEX", uv < 0 ? "–" : String.format(Locale.US, "%.1f", uv), WeatherFormat.uvLabel(uv));
        tile(c, 4, "SUNRISE", sr.isEmpty() ? "–" : sr, "");
        tile(c, 5, "SUNSET", ss.isEmpty() ? "–" : ss, f.cloud >= 0 ? "Cloud cover " + f.cloud + "%" : "");
        c.restore();
    }

    private void tile(Canvas c, int i, String title, String value, String sub) {
        float l = dp(508) + (i % 2) * dp(198), t = dp(30) + (i / 2) * dp(160), w = dp(182), h = dp(144);
        glass(c, l, t, l + w, t + h, dp(20));
        c.drawText(title, l + dp(18), t + dp(30), cap);
        tileBig.setTextSize(dp(value.length() > 6 ? 31 : 40));                    // long values (986 hPa) shrink to fit the tile
        c.drawText(value, l + dp(18), t + dp(82), tileBig);
        small.setTextSize(dp(13));
        c.drawText(sub, l + dp(18), t + dp(112), small);
        small.setTextSize(dp(14));
    }

    private void empty(Canvas c, float d) {
        mid.setTextSize(dp(26));
        c.drawText(status.isEmpty() ? "Getting the forecast…" : status, dp(64), dp(220), mid);
        small.setColor(0xCCFFFFFF);
        c.drawText("No forecast is stored on this TV yet. OK to try again, hold OK to choose a city.", dp(64), dp(250), small);
    }

    private void picker(Canvas c, float w, float h) {
        p.setColor(0xB0000000);
        c.drawRect(0, 0, w, h, p);
        float pw = dp(340), ph = Math.min(h - dp(60), dp(Weather.PLACES.length * 40 + 70)), l = (w - pw) / 2f, t = (h - ph) / 2f;
        glass(c, l, t, l + pw, t + ph, dp(22));
        c.drawText("CHOOSE A CITY", l + dp(24), t + dp(34), cap);
        for (int i = 0; i < Weather.PLACES.length; i++) {
            float y = t + dp(50) + i * dp(40);
            if (i == pickSel) { p.setColor(0xFFFFFFFF); r.set(l + dp(10), y, l + pw - dp(10), y + dp(36)); c.drawRoundRect(r, dp(12), dp(12), p); }
            mid.setTextSize(dp(17));
            mid.setColor(i == pickSel ? 0xFF000000 : 0xFFFFFFFF);
            c.drawText(Weather.PLACES[i].name, l + dp(28), y + dp(25), mid);
        }
        mid.setColor(Theme.WHITE);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription(f.valid() ? "Weather in " + placeName + ", " + temp(f.temp) + ", " + WeatherCodes.label(kind()) : "Weather, no data");
    }
}
