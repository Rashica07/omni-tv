package com.rasha.omni;

import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * OMNI's glass material for hardware that cannot blur live. Android 11 on this TV has no RenderEffect or AGSL (they arrive in
 * Android 12 and 13), so the layers of the Liquid Glass look are rebuilt from things that cost almost nothing per frame:
 *   1. a pre-blurred, tiny copy of the backdrop (made once by {@link Ambient}) stretched under the surface, so colour bleeds through softly;
 *   2. a dark veil and a faint white tint that keep text readable and lift the surface off the page;
 *   3. a vertical sheen (brighter at the top edge) standing in for the specular highlight;
 *   4. a 1 dp rim that is brightest on top, standing in for edge refraction;
 *   5. a short stacked shadow for depth.
 * See docs/research/LIQUID-GLASS.md. Every draw here is a rounded rect: no clip paths, no saveLayer.
 */
final class Glass {
    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Matrix M = new Matrix();
    private static final RectF TMP = new RectF();
    /** Gradients depend only on a surface's height, and a handful of heights alternate every frame: cache one pair per height. */
    private static final android.util.SparseArray<LinearGradient[]> GRADIENTS = new android.util.SparseArray<>();

    private Glass() { }

    /**
     * @param r        the surface in the coordinates of the canvas being drawn
     * @param ox, oy   where that canvas's origin sits inside the Ambient (so the blurred backdrop lines up)
     * @param strength 0..1, scales every layer (use it to fade a surface in or out)
     */
    static void draw(Canvas c, RectF r, float radius, Ambient amb, float ox, float oy, float strength) {
        draw(c, r, radius, amb, ox, oy, strength, true);
    }

    private static final boolean FLAT = OmniLog.flag("OmniNoGlass");

    static void draw(Canvas c, RectF r, float radius, Ambient amb, float ox, float oy, float strength, boolean shadow) {
        if (strength <= 0.01f) return;
        if (FLAT) {                                                   // profiling: one flat fill instead of the layered glass
            P.setShader(null);
            P.setStyle(Paint.Style.FILL);
            P.setColor(Theme.alpha(Theme.S1, 230));
            c.drawRoundRect(r, radius, radius, P);
            return;
        }
        int a = (int) (255 * Math.min(1f, strength));
        // 5. shadow
        for (int i = 0; shadow && i < 3; i++) {
            float grow = Theme.dp(2 + i * 3), dy = Theme.dp(3 + i * 4);
            P.setShader(null);
            P.setColor(Theme.alpha(Theme.BLACK, a * (i == 0 ? 40 : i == 1 ? 26 : 14) / 255));
            TMP.set(r.left - grow, r.top - grow + dy, r.right + grow, r.bottom + grow + dy);
            c.drawRoundRect(TMP, radius + grow, radius + grow, P);
        }
        // 1. blurred backdrop, or a quiet solid when there is none
        Paint base = amb != null ? amb.blurPaint(ox, oy) : null;
        if (base != null) {
            base.setAlpha(a);
            c.drawRoundRect(r, radius, radius, base);
            P.setShader(null);
            int veil = amb.veilAt(oy + r.top, r.height());              // 2. veil: firmer over a brighter part of the photograph
            P.setColor(Theme.alpha(Theme.BLACK, a * veil / 255));
            c.drawRoundRect(r, radius, radius, P);
        } else {
            P.setShader(null);
            P.setColor(Theme.alpha(Theme.S1, a * 220 / 255));
            c.drawRoundRect(r, radius, radius, P);
        }
        P.setColor(Theme.alpha(Theme.WHITE, a * 20 / 255));          // 2. tint
        c.drawRoundRect(r, radius, radius, P);
        // 3 + 4. sheen and rim, drawn in local coordinates so the cached gradients can be reused
        float h = r.height();
        int key = Math.round(h);
        LinearGradient[] g = GRADIENTS.get(key);
        if (g == null) {
            g = new LinearGradient[]{
                    new LinearGradient(0, 0, 0, h, new int[]{0x38FFFFFF, 0x0CFFFFFF, 0x00FFFFFF}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP),
                    new LinearGradient(0, 0, 0, h, new int[]{0x30FFFFFF, 0x0AFFFFFF, 0x12FFFFFF}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP)};
            if (GRADIENTS.size() > 24) GRADIENTS.clear();     // sheet panels grow and shrink; keep the cache bounded
            GRADIENTS.put(key, g);
        }
        LinearGradient sheen = g[0], rim = g[1];
        int sv = c.save();
        c.translate(r.left, r.top);
        TMP.set(0, 0, r.width(), h);
        P.setStyle(Paint.Style.FILL);
        P.setShader(sheen);
        P.setAlpha(a);
        c.drawRoundRect(TMP, radius, radius, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(Math.max(1f, Theme.dp(1)));
        P.setShader(rim);
        P.setAlpha(a);
        TMP.inset(P.getStrokeWidth() / 2f, P.getStrokeWidth() / 2f);
        c.drawRoundRect(TMP, radius, radius, P);
        c.restoreToCount(sv);
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setAlpha(255);
    }

    /** A solid white control surface (the focus language for controls) with a soft shadow. */
    static void solid(Canvas c, RectF r, float radius, int color, float strength) {
        int a = (int) (255 * Math.min(1f, Math.max(0f, strength)));
        if (a <= 2) return;
        P.setShader(null);
        P.setStyle(Paint.Style.FILL);
        P.setColor(Theme.alpha(Theme.BLACK, a * 60 / 255));
        TMP.set(r.left, r.top + Theme.dp(3), r.right, r.bottom + Theme.dp(5));
        c.drawRoundRect(TMP, radius, radius, P);
        P.setColor(Theme.alpha(color, a));
        c.drawRoundRect(r, radius, radius, P);
    }

    /** Builds the matrix a backdrop shader needs to line up with the screen. */
    static void alignShader(BitmapShader s, int bmpW, int bmpH, int screenW, int screenH, float ox, float oy) {
        M.reset();
        M.setScale((float) screenW / bmpW, (float) screenH / bmpH);
        M.postTranslate(-ox, -oy);
        s.setLocalMatrix(M);
    }
}
