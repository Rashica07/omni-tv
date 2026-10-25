package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * The root layout and the one rendering system behind everything: near-black ground, the wallpaper (or a Watch backdrop) drawn once as a single
 * texture, the app-colour bloom over it, and the source the glass surfaces sample. Navigation, clock, sheets and cards are children of this view,
 * so they sit on the same wallpaper in the same coordinates: a glass surface shows the blurred part of the photo that is really behind it, never a
 * second image. A frame is one texture draw (plus one gradient while a bloom is visible).
 */
final class Ambient extends FrameLayout {
    // ---- Watch backdrop (a full-bleed image with scrims baked in)
    private float bmix = 1f;
    private Bitmap prevBg;
    private Bitmap curBg;
    private final Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Rect dst = new Rect();
    private Bitmap blurred;
    private BitmapShader blurShader;
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Spring bgSpring = new Spring(130f, 1f, 1f, 0.002f, v -> {
        bmix = v;
        if (v >= 1f) prevBg = null;
        invalidate();
    });

    // ---- wallpaper
    private Bitmap wall, wallPrev, wallMid, wallSoft, wallSoftPrev;
    private BitmapShader wallShader;
    private float[] band = new float[8];
    private float wmix = 1f;
    private final Spring wallSpring = new Spring(80f, 1f, 1f, 0.002f, v -> {
        wmix = v;
        if (v >= 1f) { wallPrev = null; wallSoftPrev = null; }
        invalidate();
    });

    // ---- focus-dependent blur: crisp while an app is highlighted, 70% blurred otherwise
    private float sharp;                      // 1 = an app tile has focus (crisp wallpaper), 0 = blurred
    private final Spring sharpSpring = new Spring(120f, 1f, 0f, 0.002f, v -> { sharp = v; invalidate(); });

    /** Crisp wallpaper while an app is highlighted; otherwise the blurred copy covers it at 70%. Cross-fades; reduced motion snaps. */
    void setSharp(boolean on) { sharpSpring.to(on || NO_BLUR ? 1f : 0f); }

    private static final boolean NO_BLOOM = OmniLog.flag("OmniNoBloom"), NO_BLUR = OmniLog.flag("OmniNoBlur");

    // ---- app-colour bloom
    private int bloomColor, bloomPrevColor;
    private float bloomX, bloomY, bloomPX, bloomPY;
    private RadialGradient bloomShader, bloomPrevShader;
    private float bloomMix = 1f;
    private final Spring bloomSpring = new Spring(160f, 0.95f, 1f, 0.002f, v -> {
        bloomMix = v;
        if (v >= 1f) { bloomPrevColor = 0; bloomPrevShader = null; }
        invalidate();
    });
    private float level;                    // 0 = no bloom, 1 = full
    private final Spring levelSpring = new Spring(140f, 0.95f, 0f, 0.002f, v -> { level = v; invalidate(); });
    private float boost;                    // extra intensity while an app is opening
    private final Spring boostSpring = new Spring(220f, 0.9f, 0f, 0.002f, v -> { boost = v; invalidate(); });
    private final Paint bloomPaint = new Paint();


    Ambient(Context c) {
        super(c);
        setWillNotDraw(false);
    }

    // ------------------------------------------------------------ Watch backdrop

    /** Cross-fades to a full-bleed backdrop image (null clears it). */
    void setBackdrop(Bitmap b) {
        if (b == curBg) return;
        prevBg = curBg;
        curBg = b;
        blurred = b == null ? null : Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, Math.max(1, b.getWidth() / 8), Math.max(1, b.getHeight() / 8), true), 40, 24, true);
        blurShader = blurred == null ? null : new BitmapShader(blurred, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        bgSpring.snap(0f);
        bgSpring.to(1f);
    }

    // ------------------------------------------------------------ wallpaper

    /** Cross-fades to a new wallpaper. scrimmed is the 16:9 picture with the readability scrims already baked in; mid is a small blurred copy for glass. */
    void setWallpaper(Bitmap scrimmed, Bitmap soft, Bitmap mid, float[] bandLuma) {
        if (scrimmed == wall) return;
        wallPrev = wall;
        wallSoftPrev = wallSoft;
        wall = scrimmed;
        wallSoft = soft;
        wallMid = mid;
        wallShader = mid == null ? null : new BitmapShader(mid, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        if (bandLuma != null && bandLuma.length == 8) band = bandLuma;
        wallSpring.snap(wallPrev == null ? 1f : 0f);
        if (wallPrev != null) wallSpring.to(1f);
        invalidate();
    }

    boolean hasWallpaper() { return wall != null; }

    /** Glass veil alpha (0..255) for a surface spanning [top, top + height) px: darker over a brighter part of the photograph so text stays readable. */
    int veilAt(float top, float height) {
        if (wall == null || getHeight() == 0) return 95;
        float bandH = getHeight() / 8f;
        int a = Math.max(0, Math.min(7, (int) (top / bandH))), b = Math.max(0, Math.min(7, (int) ((top + height) / bandH)));
        float sum = 0;
        for (int i = a; i <= b; i++) sum += band[i];
        return LumaVeil.alpha(sum / (b - a + 1));
    }

    /** A paint that fills a glass surface with the blurred picture aligned to the screen (backdrop first, else the wallpaper), or null when there is neither. */
    Paint blurPaint(float ox, float oy) {
        BitmapShader sh = blurShader != null ? blurShader : wallShader;
        Bitmap bm = blurShader != null ? blurred : wallMid;
        if (sh == null || bm == null || getWidth() == 0) return null;
        Glass.alignShader(sh, bm.getWidth(), bm.getHeight(), getWidth(), getHeight(), ox, oy);
        blurPaint.setShader(sh);
        return blurPaint;
    }

    // ------------------------------------------------------------ app-colour bloom

    /** Ambient light in an app's brand colour, centred on the focused tile. color 0 clears it. The wallpaper stays visible beneath. */
    void setBloom(int color, float cx, float cy) {
        if (color == 0) { clearBloom(); return; }
        if (color == bloomColor && Math.abs(cx - bloomX) < 40 && Math.abs(cy - bloomY) < 40) return;
        bloomPrevColor = bloomColor;
        bloomPrevShader = bloomShader;
        bloomPX = bloomX;
        bloomPY = bloomY;
        bloomColor = color;
        bloomX = cx;
        bloomY = cy;
        bloomShader = new RadialGradient(cx, cy, Math.max(getWidth(), 960) * 0.46f, new int[]{Theme.alpha(color, 255), Theme.alpha(color, 110), Theme.alpha(color, 0)}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        bloomSpring.snap(bloomPrevColor == 0 ? 1f : 0f);
        if (bloomPrevColor != 0) bloomSpring.to(1f);
        levelSpring.to(1f);
    }

    void clearBloom() {
        if (bloomColor == 0 && level == 0f) return;
        levelSpring.to(0f);
        boostSpring.to(0f);
    }

    /** Intensifies the bloom while an app opens, and lets it fall back when OMNI returns. */
    void boost(boolean on) { boostSpring.to(on ? 1f : 0f); }

    /** Old name kept for page code: a null artwork sample means "no colour wash". */
    void setGlow(Bitmap ignored) { clearBloom(); }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        dst.set(0, 0, w, h);
        boolean covered = curBg != null && prevBg == null && bmix >= 1f;
        if (!covered) {
            c.drawColor(Theme.BG);
            // steady state is ONE texture draw: the crisp picture while an app is highlighted, the pre-blurred one otherwise.
            // Two draws happen only during a cross-fade (wallpaper change or focus change), a few hundred milliseconds at a time.
            float sh = Math.max(0f, Math.min(1f, sharp)), wm = Math.max(0f, Math.min(1f, wmix));
            Bitmap base = wallSoft != null ? wallSoft : wall, prev = wallSoftPrev != null ? wallSoftPrev : wallPrev;
            if (wm < 1f && prev != null) { bp.setAlpha(255); c.drawBitmap(sh > 0.5f ? (wallPrev != null ? wallPrev : prev) : prev, null, dst, bp); }
            if (sh < 0.99f && base != null) { bp.setAlpha((int) (255 * wm)); c.drawBitmap(base, null, dst, bp); }
            if (sh > 0.01f && wall != null) { bp.setAlpha((int) (255 * wm * sh)); c.drawBitmap(wall, null, dst, bp); }
            drawBloom(c, w, h);
        }
        if (prevBg != null && bmix < 1f) {
            bp.setAlpha((int) (255 * (1f - Math.min(1f, bmix))));
            c.drawBitmap(prevBg, null, dst, bp);
        }
        if (curBg != null) {
            bp.setAlpha((int) (255 * Math.max(0f, Math.min(1f, bmix))));
            c.drawBitmap(curBg, null, dst, bp);
        }
    }

    private void drawBloom(Canvas c, int w, int h) {
        float lv = Math.max(0f, Math.min(1f, level));
        if (NO_BLOOM || lv <= 0.01f || bloomShader == null) return;
        float peak = 0.36f + 0.24f * Math.max(0f, Math.min(1f, boost));          // restrained at rest, firmer while an app opens
        float m = Math.max(0f, Math.min(1f, bloomMix));
        if (bloomPrevShader != null && m < 1f) {
            bloomPaint.setShader(bloomPrevShader);
            bloomPaint.setAlpha((int) (255 * peak * lv * (1f - m)));
            c.drawRect(0, 0, w, h, bloomPaint);
        }
        bloomPaint.setShader(bloomShader);
        bloomPaint.setAlpha((int) (255 * peak * lv * (bloomPrevShader != null ? m : 1f)));
        c.drawRect(0, 0, w, h, bloomPaint);
    }

    /** Bakes the legibility scrims (left text zone, top nav strip, bottom shelves) into a Watch backdrop image. */
    static Bitmap compose(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        c.drawBitmap(src, 0, 0, null);
        Paint p = new Paint();
        final int solid = 0xF50A0A0A, mid = 0xCC0A0A0A, clear = 0x000A0A0A;
        p.setShader(new LinearGradient(0, 0, w * 0.9f, 0, new int[]{solid, mid, clear}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, 0, 0, h * 0.3f, 0xCC0A0A0A, clear, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        p.setShader(new LinearGradient(0, h * 0.4f, 0, h, clear, 0xFF0A0A0A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        return out;
    }
}
