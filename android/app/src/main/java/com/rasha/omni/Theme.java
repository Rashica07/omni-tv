package com.rasha.omni;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * OMNI's single source of design tokens (see docs/design/OMNI-TV-DESIGN-SPEC.md). The OS chrome is monochrome:
 * colour appears only inside media pixels (app icons and banners at focus, posters, backdrops).
 */
final class Theme {
    // surface scale
    static final int BG = 0xFF0A0A0A;
    static final int BLACK = 0xFF000000;
    static final int S1 = 0xFF141414;
    static final int S2 = 0xFF1C1C1C;
    static final int S3 = 0xFF2A2A2A;
    static final int HAIR = 0xFF2E2E2E;
    // text scale
    static final int WHITE = 0xFFFFFFFF;
    static final int TEXT = WHITE;
    static final int TEXT2 = 0xFFCACACA;
    static final int TEXT3 = 0xFFA0A0A0;
    // shape and focus geometry (dp)
    static final float RADIUS = 12f;
    static final float FOCUS_SCALE = 0.04f;
    static final float FOCUS_LIFT = 3f;
    static final float RING = 2f;
    static final float RING_GAP = 2f;
    /** Safe area (dp): about 5.5% top/bottom and 64 dp sides (Apple, Amazon, Roku, LG margins: docs/research/platforms.md). */
    static final int SAFE_SIDE = 64;
    static final int SAFE_VERT = 30;
    /** cubic-bezier(.16, 1, .3, 1): the expo-out curve used across the kiqa-dev sites. */
    static final PathInterpolator EXPO = new PathInterpolator(0.16f, 1f, 0.3f, 1f);

    static float density = 1f;
    static Typeface geist = Typeface.DEFAULT;
    /** True when the system has animations switched off: springs then jump straight to their target. */
    static boolean reduceMotion;

    private Theme() { }

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        reduceMotion = android.provider.Settings.Global.getFloat(c.getContentResolver(),
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        try { geist = Typeface.createFromAsset(c.getAssets(), "Geist.ttf"); } catch (Exception ignored) { }
    }

    static int dp(float v) { return (int) (v * density + 0.5f); }

    /** Applies Geist at the given size, colour and variable weight (0 = font default). */
    static <T extends TextView> T text(T t, float sp, int color, int wght) {
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(geist);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        if (wght > 0 && Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' " + wght);
        return t;
    }

    /** Linear per-channel blend without boxing (called per tile per frame). */
    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (int) (((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
        int ar = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int ag = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int ab = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (aa << 24) | (ar << 16) | (ag << 8) | ab;
    }

    static int alpha(int c, int a) { return (c & 0xFFFFFF) | (a << 24); }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
