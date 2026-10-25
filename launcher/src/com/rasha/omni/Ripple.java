package com.rasha.omni;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.ViewGroup;

/** AirDrop-style launch wave: soft rings expand from the card across the screen. */
final class Ripple extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float cx;
    private final float cy;
    private final int color;
    private float t;

    private Ripple(Context c, float cx, float cy, int color) {
        super(c);
        this.cx = cx;
        this.cy = cy;
        this.color = color;
        p.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
    }

    static void play(ViewGroup root, View from, int color) {
        int[] loc = new int[2];
        from.getLocationInWindow(loc);
        play(root, loc[0] + from.getWidth() / 2f, loc[1] + from.getHeight() / 2f, color);
    }

    static void play(final ViewGroup root, float cx, float cy, int color) {
        final Ripple r = new Ripple(root.getContext(), cx, cy, color);
        root.addView(r, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(750);
        a.setInterpolator(Theme.EXPO);
        a.addUpdateListener(v -> {
            r.t = (float) v.getAnimatedValue();
            r.invalidate();
            if (r.t >= 1f) root.removeView(r);
        });
        a.start();
    }

    @Override protected void onDraw(Canvas c) {
        float max = (float) Math.hypot(getWidth(), getHeight());
        for (int i = 0; i < 3; i++) {
            float k = Math.max(0f, Math.min(1f, (t - i * 0.1f) / (1f - i * 0.1f)));
            if (k <= 0f) continue;
            p.setStrokeWidth(Theme.dp(3) * (1f - k) + 1f);
            p.setColor(Theme.alpha(Theme.WHITE, (int) (110 * (1f - k))));
            c.drawCircle(cx, cy, max * k * 0.8f, p);
        }
    }
}
