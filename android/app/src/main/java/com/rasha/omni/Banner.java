package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import static com.rasha.omni.Theme.dp;

/**
 * A glass capsule across the top of the screen that says what is happening. Two uses:
 *  - an editing mode (Move): stays for as long as the mode lasts and covers the navigation, so nobody can mistake what is active;
 *  - a short confirmation (Placed, Pinned, Hidden, Cancelled) that dismisses itself.
 * It never takes focus and ignores touches, so it cannot get in the way of the D-pad.
 */
final class Banner extends View {
    static final int MODE_MOVE = 0, MODE_DONE = 1, MODE_CANCEL = 2;
    static final float H = 46f;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final TextPaint title = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final TextPaint sub = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float shown;
    private boolean wanted;
    private final Spring appear = new Spring(300f, 0.86f, 0f, 0.002f, v -> {
        shown = v;
        setAlpha(Math.max(0f, Math.min(1f, v * 1.4f)));
        setTranslationY(-dp(18) * (1f - v));
        invalidate();
        if (v <= 0.001f && !wanted) setVisibility(GONE);
    });
    private String t1 = "", t2 = "";
    private int mode;
    private Ambient ambient;
    private final Runnable autoHide = this::hide;

    Banner(Context c) {
        super(c);
        setFocusable(false);
        setClickable(false);
        title.setTypeface(Theme.geist);
        title.setTextSize(dp(20));
        title.setColor(Theme.WHITE);
        sub.setTypeface(Theme.geist);
        sub.setTextSize(dp(14));
        sub.setColor(Theme.TEXT2);
        if (Build.VERSION.SDK_INT >= 26) {
            title.setFontVariationSettings("'wght' 700");
            sub.setFontVariationSettings("'wght' 500");
        }
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        setVisibility(GONE);
        setAlpha(0f);
    }

    void setAmbient(Ambient a) { ambient = a; }

    boolean isShowing() { return wanted; }

    /** True while an editing mode (not a short confirmation) is on screen. */
    boolean isEditing() { return wanted && mode == MODE_MOVE; }

    /** An editing mode: stays until {@link #hide()}. */
    void show(int mode, String heading, String instructions) {
        ui.removeCallbacks(autoHide);
        set(mode, heading, instructions);
    }

    /** A confirmation that goes away by itself. */
    void flash(int mode, String heading, String detail) {
        set(mode, heading, detail);
        ui.removeCallbacks(autoHide);
        ui.postDelayed(autoHide, 2400);
    }

    void hide() {
        ui.removeCallbacks(autoHide);
        wanted = false;
        appear.to(0f);
    }

    private void set(int m, String a, String b) {
        mode = m;
        t1 = a;
        t2 = b;
        wanted = true;
        setVisibility(VISIBLE);
        appear.to(1f);
        invalidate();
    }

    /** The width the current text needs, so the capsule hugs it. */
    private float wantedWidth() {
        float w = Math.max(title.measureText(t1), sub.measureText(t2));
        return dp(76) + w + dp(30);
    }

    @Override protected void onDraw(Canvas c) {
        float h = dp(H);
        float w = Math.min(getWidth(), Math.max(dp(540), wantedWidth()));      // always wide enough to cover the logo and the whole navigation pill, never half of it
        r.set(0, 0, w, h);
        Glass.draw(c, r, h / 2f, ambient, getLeft(), getTop(), 1f, false);
        drawGlyph(c, dp(28), h / 2f, dp(26));
        CharSequence a = TextUtils.ellipsize(t1, title, w - dp(100), TextUtils.TruncateAt.END);
        CharSequence b = TextUtils.ellipsize(t2, sub, w - dp(100), TextUtils.TruncateAt.END);
        c.drawText(a, 0, a.length(), dp(66), h / 2f - dp(2), title);
        c.drawText(b, 0, b.length(), dp(66), h / 2f + dp(17), sub);
    }

    private void drawGlyph(Canvas c, float cx, float cy, float s) {
        ink.setColor(Theme.WHITE);
        ink.setStrokeWidth(dp(3));
        float u = s / 2f;
        if (mode == MODE_MOVE) {            // two chevrons pointing away from a centre dot: "this goes left or right"
            c.drawLine(cx - u * 0.35f, cy - u * 0.62f, cx - u, cy, ink);
            c.drawLine(cx - u, cy, cx - u * 0.35f, cy + u * 0.62f, ink);
            c.drawLine(cx + u * 0.35f, cy - u * 0.62f, cx + u, cy, ink);
            c.drawLine(cx + u, cy, cx + u * 0.35f, cy + u * 0.62f, ink);
            ink.setStyle(Paint.Style.FILL);
            c.drawCircle(cx, cy, dp(3), ink);
            ink.setStyle(Paint.Style.STROKE);
        } else if (mode == MODE_DONE) {     // a check mark
            c.drawLine(cx - u * 0.7f, cy + u * 0.05f, cx - u * 0.15f, cy + u * 0.6f, ink);
            c.drawLine(cx - u * 0.15f, cy + u * 0.6f, cx + u * 0.75f, cy - u * 0.55f, ink);
        } else {                            // a cross
            c.drawLine(cx - u * 0.6f, cy - u * 0.6f, cx + u * 0.6f, cy + u * 0.6f, ink);
            c.drawLine(cx - u * 0.6f, cy + u * 0.6f, cx + u * 0.6f, cy - u * 0.6f, ink);
        }
    }
}
