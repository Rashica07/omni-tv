package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import static com.rasha.omni.Theme.dp;

/**
 * The strip above the channel grid: Search, five filters (All, Kosovo, Albania, Antenna, Favorites) and a Sort control. One focusable view:
 * Left/Right move between zones, OK activates, Down enters the grid, Up goes to the navigation. The active filter is underlined so "chosen" and
 * "focused" stay different; changing a filter or the sort never moves focus away from the bar.
 */
final class TvToolbar extends View {
    interface Listener { void onSearch(); void onFilter(int filter); void onSortNext(); void onUp(); void onDown(); }

    static final String[] FILTERS = TvToolbarKeys.FILTERS;
    static final String[] SORTS = TvToolbarKeys.SORTS;
    private static final float H = 44f, PAD = 13f, GAP = 2f, ICON = 18f;
    private static final int ZONES = 7;                      // 0 search, 1..5 filters, 6 sort

    private final Listener listener;
    private final TextPaint text = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float[] w = new float[ZONES];
    private int zone = 1;
    private int filter, sort;
    private float filled;
    private final Spring fillSpring = new Spring(420f, 0.9f, 0f, 0.002f, v -> { filled = v; invalidate(); });
    private Ambient ambient;

    TvToolbar(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        text.setTypeface(Theme.geist);
        text.setTextSize(dp(15));
        if (Build.VERSION.SDK_INT >= 26) text.setFontVariationSettings("'wght' 600");
        w[0] = dp(ICON);
        for (int i = 0; i < 5; i++) w[i + 1] = text.measureText(Tr.t(FILTERS[i]));
        w[6] = text.measureText(Tr.t("Sort") + ": " + Tr.t(SORTS[0]));          // refitted to the chosen sort in set(), so the bar is never wider than its text
        p.setStrokeCap(Paint.Cap.ROUND);
    }

    void setAmbient(Ambient a) { ambient = a; }

    void set(int filter, int sort) {
        this.filter = filter; this.sort = sort;
        float sw = text.measureText(Tr.t("Sort") + ": " + Tr.t(SORTS[sort]));
        if (sw != w[6]) { w[6] = sw; requestLayout(); }
        invalidate();
    }

    private float zoneWidth(int z) { return w[z] + dp(PAD) * 2; }

    int desiredWidth() {
        float t = dp(6) * 2 + dp(10);                       // a wider gap before the Sort zone
        for (int i = 0; i < ZONES; i++) t += zoneWidth(i) + dp(GAP);
        return (int) t;
    }

    @Override protected void onMeasure(int wm, int hm) { setMeasuredDimension(desiredWidth(), dp(H)); }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        fillSpring.to(gained ? 1f : 0f);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (zone > 0) { zone--; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (zone < ZONES - 1) { zone++; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_UP: listener.onUp(); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: listener.onDown(); return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) {
                    if (zone == 0) listener.onSearch();
                    else if (zone == 6) listener.onSortNext();
                    else { filter = zone - 1; invalidate(); listener.onFilter(filter); }
                }
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override protected void onDraw(Canvas c) {
        float h = dp(H), cy = h / 2f;
        r.set(0, 0, getWidth(), h);
        Glass.draw(c, r, h / 2f, ambient, getLeft(), getTop(), 1f, false);
        float x = dp(6);
        for (int z = 0; z < ZONES; z++) {
            if (z == 6) x += dp(10);
            float zw = zoneWidth(z);
            boolean foc = z == zone && filled > 0.01f;
            if (foc) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.WHITE, (int) (255 * filled)));
                r.set(x, dp(4), x + zw, h - dp(4));
                c.drawRoundRect(r, dp(18), dp(18), p);
            }
            boolean chosen = z >= 1 && z <= 5 && filter == z - 1;
            int fg = foc ? Theme.lerp(Theme.WHITE, Theme.BLACK, filled) : chosen ? Theme.WHITE : Theme.TEXT2;
            text.setColor(fg);
            float tx = x + dp(PAD);
            if (z == 0) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp(2));
                p.setColor(fg);
                float u = dp(ICON), iy = cy - u / 2f;
                c.drawCircle(tx + u * 0.42f, iy + u * 0.42f, u * 0.32f, p);
                c.drawLine(tx + u * 0.66f, iy + u * 0.66f, tx + u * 0.95f, iy + u * 0.95f, p);
            } else {
                String s = z == 6 ? Tr.t("Sort") + ": " + Tr.t(SORTS[sort]) : Tr.t(FILTERS[z - 1]);
                c.drawText(s, tx, cy + dp(5.5f), text);
                if (chosen && !foc) {
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Theme.WHITE);
                    c.drawRoundRect(tx, h - dp(8), tx + w[z], h - dp(6), dp(1), dp(1), p);
                }
            }
            x += zw + dp(GAP);
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription(zone == 0 ? "Search channels" : zone == 6 ? "Sort: " + SORTS[sort] : "Filter: " + FILTERS[zone - 1] + (filter == zone - 1 ? ", selected" : ""));
    }
}
