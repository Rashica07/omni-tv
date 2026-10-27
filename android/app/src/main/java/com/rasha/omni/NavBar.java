package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import static com.rasha.omni.Theme.dp;

/**
 * The global navigation: a large OMNI mark and one glass capsule holding five oversized icons. The selected destination opens
 * horizontally to show its name; the others stay compact. It is a single focusable view (Left/Right move the selection, Down leaves for
 * the page), so there is exactly one place for "where am I", and every motion is an interruptible spring.
 *
 * When the bar has focus the open destination becomes a solid white pill with dark content (the OMNI control-focus rule); when focus
 * is on the page it relaxes to a quiet glass pill.
 */
final class NavBar extends View {
    interface Listener { void onSelect(int index); }

    static final int HOME = 0, WATCH = 1, APPS = 2, INPUTS = 3, SETTINGS = 4;
    private static final String[] NAMES = {"Home", "Watch", "Apps", "TV", "Settings"};

    // geometry (dp)
    private static final float H = 46f;
    private static final float PAD = 5f;
    private static final float ITEM_H = 36f;
    private static final float COMPACT_W = 44f;
    private static final float ICON = 23f;
    private static final float GAP = 2f;

    private final Listener listener;
    private final float[] open = new float[5];
    private final Spring[] springs = new Spring[5];
    private float filled;             // 1 when this view has focus
    private final Spring fillSpring = new Spring(420f, 0.9f, 0f, 0.002f, v -> { filled = v; invalidate(); });
    private int selected;
    private final float[] labelW = new float[5];

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint wordmark = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();
    private final RectF item = new RectF();
    private Ambient ambient;

    NavBar(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        for (int i = 0; i < 5; i++) {
            final int k = i;
            springs[i] = new Spring(320f, 0.86f, i == 0 ? 1f : 0f, 0.002f, v -> { open[k] = v; invalidate(); });
            open[i] = i == 0 ? 1f : 0f;
        }
        label.setTypeface(Theme.geist);
        label.setTextSize(dp(14));
        wordmark.setTypeface(Theme.geist);
        wordmark.setTextSize(dp(20));
        wordmark.setLetterSpacing(0.22f);
        wordmark.setColor(Theme.WHITE);
        if (Build.VERSION.SDK_INT >= 26) {
            label.setFontVariationSettings("'wght' 650");
            wordmark.setFontVariationSettings("'wght' 800");
        }
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < 5; i++) labelW[i] = label.measureText(NAMES[i]);
    }

    void setAmbient(Ambient a) { ambient = a; }

    int selected() { return selected; }

    /** Moves the selection (no callback). Used when the page changes for another reason, e.g. Home pressed. */
    void select(int i, boolean animate) {
        i = Math.max(0, Math.min(4, i));
        selected = i;
        for (int k = 0; k < 5; k++) {
            if (animate) springs[k].to(k == i ? 1f : 0f);
            else springs[k].snap(k == i ? 1f : 0f);
        }
        invalidate();
    }

    /** Width this bar needs: the mark, the capsule at its widest (every icon compact plus the longest label). */
    static int widthPx() {
        return dp(MARK_W + 24 + PAD * 2 + 5 * COMPACT_W + 4 * GAP + 78);
    }

    static int heightPx() { return dp(H); }

    private static final float MARK_W = 138f;

    // ------------------------------------------------------------ input

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        fillSpring.to(gained ? 1f : 0f);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: change(selected - 1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (selected == 4) return false;               // past the last destination: on to the status area
                change(selected + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP: return true;           // nothing above the header
            default: return super.onKeyDown(code, e);              // Down leaves for the page
        }
    }

    private void change(int i) {
        if (i < 0 || i > 4 || i == selected) return;
        select(i, true);
        listener.onSelect(i);
    }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        float h = dp(H);
        drawMark(c, 0, h / 2f);
        float x0 = dp(MARK_W + 24), y0 = 0;
        // capsule width follows the open amounts
        float w = dp(PAD * 2);
        for (int i = 0; i < 5; i++) w += itemWidth(i) + (i < 4 ? dp(GAP) : 0);
        r.set(x0, y0, x0 + w, y0 + h);
        Glass.draw(c, r, h / 2f, ambient, getLeft() + 0f, getTop() + 0f, 1f, false);

        float x = x0 + dp(PAD);
        float cy = h / 2f;
        for (int i = 0; i < 5; i++) {
            float iw = itemWidth(i);
            item.set(x, cy - dp(ITEM_H) / 2f, x + iw, cy + dp(ITEM_H) / 2f);
            float o = open[i];
            boolean sel = i == selected;
            if (o > 0.01f) {
                // open pill: glass when the page has focus, solid white when the bar has focus
                float rad = dp(ITEM_H) / 2f;
                p.setStyle(Paint.Style.FILL);
                p.setShader(null);
                p.setColor(Theme.alpha(Theme.WHITE, (int) (255 * (0.20f * (1f - filled) + filled) * Math.min(1f, o * 1.4f))));
                c.drawRoundRect(item, rad, rad, p);
            }
            int fg = Theme.lerp(Theme.TEXT2, Theme.lerp(Theme.WHITE, Theme.BLACK, filled), o);
            drawIcon(c, i, x + dp(COMPACT_W) / 2f, cy, fg);
            if (o > 0.05f) {
                label.setColor(Theme.alpha(fg, (int) (255 * Math.min(1f, Math.max(0f, (o - 0.3f) / 0.7f)))));
                c.drawText(NAMES[i], x + dp(COMPACT_W) - dp(2), cy + dp(5), label);
            }
            x += iw + dp(GAP);
        }
    }

    private float itemWidth(int i) { return dp(COMPACT_W) + (labelW[i] + dp(14)) * open[i]; }

    /** The OMNI mark: a ring open at the lower right with a dot inside, then the wordmark. */
    private void drawMark(Canvas c, float x, float cy) {
        float s = dp(30), cx = x + s / 2f + dp(2);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(dp(4.2f));
        p.setColor(Theme.WHITE);
        r.set(cx - s / 2f + dp(3), cy - s / 2f + dp(3), cx + s / 2f - dp(3), cy + s / 2f - dp(3));
        c.drawArc(r, -30f, 300f, false, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(cx, cy, dp(5f), p);
        c.drawText("OMNI", x + s + dp(11), cy + dp(7.5f), wordmark);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    /** Hand-drawn 28 dp icons on a 24-unit grid; stroke weight is shared so they read as one family. */
    private void drawIcon(Canvas c, int i, float cx, float cy, int color) {
        float u = dp(ICON) / 24f;
        ink.setColor(color);
        ink.setStrokeWidth(2.1f * u);
        ink.setStyle(Paint.Style.STROKE);
        int sv = c.save();
        c.translate(cx - 12f * u, cy - 12f * u);
        switch (i) {
            case HOME:
                path.reset();
                path.moveTo(3f * u, 11.5f * u); path.lineTo(12f * u, 4f * u); path.lineTo(21f * u, 11.5f * u);
                path.moveTo(5.5f * u, 10f * u); path.lineTo(5.5f * u, 20f * u); path.lineTo(18.5f * u, 20f * u); path.lineTo(18.5f * u, 10f * u);
                path.moveTo(10f * u, 20f * u); path.lineTo(10f * u, 14f * u); path.lineTo(14f * u, 14f * u); path.lineTo(14f * u, 20f * u);
                c.drawPath(path, ink);
                break;
            case WATCH:
                r.set(3f * u, 5f * u, 21f * u, 19f * u);
                c.drawRoundRect(r, 3.5f * u, 3.5f * u, ink);
                p.setStyle(Paint.Style.FILL); p.setColor(color); p.setShader(null);
                path.reset(); path.moveTo(10f * u, 9f * u); path.lineTo(15.5f * u, 12f * u); path.lineTo(10f * u, 15f * u); path.close();
                c.drawPath(path, p);
                break;
            case APPS:
                for (int gx = 0; gx < 2; gx++) for (int gy = 0; gy < 2; gy++) {
                    r.set((3.5f + gx * 9.5f) * u, (3.5f + gy * 9.5f) * u, (11f + gx * 9.5f) * u, (11f + gy * 9.5f) * u);
                    c.drawRoundRect(r, 2.2f * u, 2.2f * u, ink);
                }
                break;
            case INPUTS:     // a television with an antenna: live channels and the inputs you plug in
                r.set(3f * u, 8f * u, 21f * u, 19.5f * u);
                c.drawRoundRect(r, 2.8f * u, 2.8f * u, ink);
                c.drawLine(8.5f * u, 4f * u, 12f * u, 8f * u, ink);
                c.drawLine(15.5f * u, 4f * u, 12f * u, 8f * u, ink);
                break;
            default:         // settings: a gear
                c.drawCircle(12f * u, 12f * u, 3.4f * u, ink);
                for (int k = 0; k < 8; k++) {
                    double t = Math.toRadians(k * 45);
                    c.drawLine(12f * u + (float) Math.cos(t) * 7f * u, 12f * u + (float) Math.sin(t) * 7f * u,
                            12f * u + (float) Math.cos(t) * 9.6f * u, 12f * u + (float) Math.sin(t) * 9.6f * u, ink);
                }
                c.drawCircle(12f * u, 12f * u, 7f * u, ink);
        }
        c.restoreToCount(sv);
    }

    // ------------------------------------------------------------ accessibility

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(NAMES[selected]);
        info.setContentDescription("Navigation, " + NAMES[selected] + ", " + (selected + 1) + " of 5");
    }
}
