package com.rasha.omni;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/**
 * Turns whatever an app ships (banner, adaptive icon, legacy PNG, vector, transparent glyph) into bitmaps that are cheap to draw:
 * pre-rounded and pre-cropped once on the scan thread, so the render thread never needs a clip path.
 *
 * Rules (docs/design spec, icon pipeline):
 *  - a banner is drawn as a wide tile, cover-fitted and rounded once;
 *  - an icon that already has its own opaque silhouette (rounded-square PNG, adaptive icon) IS the tile: no plate behind it, no extra
 *    corners or padding; transparent margins baked into the file are cropped away so every icon fills its tile the same way;
 *  - an icon with a free-form silhouette (a glyph, a circle) keeps its transparency and sits on a neutral plate at a smaller optical size.
 */
final class IconArt {
    enum Kind { BANNER, SELF, FREEFORM }

    /** Pixel size of normalised icon bitmaps. */
    static final int ICON_PX = 160;
    /** Banner bitmaps are rendered at 2 px per dp of a 200x112 dp tile. */
    static final int BANNER_W = 400;
    static final int BANNER_H = 224;

    /** Shape of the tile this app gets on a shelf. */
    final Kind kind;
    /** BANNER: the pre-rounded widescreen tile. SELF / FREEFORM: the same as {@link #icon}. */
    final Bitmap tile;
    /** The launcher icon, cropped to its content and fitted to a square (always present; used for collapsed strips and collages). */
    final Bitmap icon;
    /** True when {@link #icon} carries its own opaque silhouette (so it is drawn full-bleed with no plate behind it). */
    final boolean iconSelf;
    final String info;

    private IconArt(Kind kind, Bitmap tile, Bitmap icon, boolean iconSelf, String info) {
        this.kind = kind;
        this.tile = tile;
        this.icon = icon;
        this.iconSelf = iconSelf;
        this.info = info;
    }

    /** True when an image is plausibly a widescreen banner (Android TV banners are 320x180, i.e. 16:9). */
    static boolean looksLikeBanner(Drawable d) {
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        if (w <= 0 || h <= 0) return true;               // vectors without a size: trust the app
        float r = (float) w / h;
        return r >= 1.45f && r <= 2.1f;
    }

    /** Builds the art for one app; call off the main thread. banner may be null. */
    static IconArt build(Drawable icon, Drawable banner, float density) {
        // 1. the launcher icon, normalised once
        Bitmap src = render(icon, 256, 256);
        int[] bb = contentBounds(src);
        float opaque = opaqueFraction(src, bb);
        boolean self = opaque >= 0.78f || icon instanceof AdaptiveIconDrawable;
        Bitmap fitted = fitSquare(src, bb, ICON_PX, self ? 0f : 0.04f);
        src.recycle();
        String ii = describe(icon) + " bbox=" + bb[0] + "," + bb[1] + "," + bb[2] + "," + bb[3] + " opaque=" + Math.round(opaque * 100) + "%";
        // 2. a real widescreen banner becomes the tile; a square "banner" is treated as an icon, never cropped into a wide tile
        if (banner != null && looksLikeBanner(banner)) {
            int bw = banner.getIntrinsicWidth() > 0 ? Math.max(256, Math.min(BANNER_W, banner.getIntrinsicWidth())) : BANNER_W;
            int bh = Math.round(bw * 9f / 16f);
            Bitmap b = renderCover(banner, bw, bh);
            roundCorners(b, 12f * density * (bw / (200f * density)));
            return new IconArt(Kind.BANNER, b, fitted, self, "banner " + describe(banner) + " as " + bw + "x" + bh + "; icon " + ii);
        }
        String why = banner != null ? "square banner ignored (" + describe(banner) + "); " : "";
        return new IconArt(self ? Kind.SELF : Kind.FREEFORM, fitted, fitted, self, why + ii);
    }

    static String describe(Drawable d) {
        String n = d.getClass().getSimpleName();
        int w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
        return n + " " + (w < 0 ? "?" : w) + "x" + (h < 0 ? "?" : h);
    }

    private static Bitmap render(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Rect old = d.copyBounds();
        d.setBounds(0, 0, w, h);
        d.draw(c);
        d.setBounds(old);
        return b;
    }

    /** Draws the drawable scaled to cover w x h (centre-cropped). */
    private static Bitmap renderCover(Drawable d, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int dw = d.getIntrinsicWidth(), dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) { dw = w; dh = h; }
        float s = Math.max((float) w / dw, (float) h / dh);
        int sw = Math.round(dw * s), sh = Math.round(dh * s);
        Rect old = d.copyBounds();
        d.setBounds((w - sw) / 2, (h - sh) / 2, (w + sw) / 2, (h + sh) / 2);
        d.draw(c);
        d.setBounds(old);
        return b;
    }

    /** Multiplies the bitmap's alpha by a rounded-rect mask, in place. */
    static void roundCorners(Bitmap b, float radiusPx) {
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        Bitmap mask = Bitmap.createBitmap(b.getWidth(), b.getHeight(), Bitmap.Config.ALPHA_8);
        Canvas mc = new Canvas(mask);
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mc.drawRoundRect(new RectF(0, 0, b.getWidth(), b.getHeight()), radiusPx, radiusPx, mp);
        c.drawBitmap(mask, 0, 0, p);
        mask.recycle();
    }

    /** Returns a rounded copy (used for posters). */
    static Bitmap rounded(Bitmap src, float radiusPx) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        roundCorners(b, radiusPx);
        return b;
    }

    /** {left, top, right, bottom} of pixels with alpha above a small threshold (whole bitmap when fully empty). */
    static int[] contentBounds(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int l = w, t = h, r = -1, bt = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((px[y * w + x] >>> 24) > 24) {
                    if (x < l) l = x;
                    if (x > r) r = x;
                    if (y < t) t = y;
                    if (y > bt) bt = y;
                }
            }
        }
        if (r < 0) return new int[]{0, 0, w, h};
        return new int[]{l, t, r + 1, bt + 1};
    }

    /** Share of the content box that is (nearly) opaque: a rounded square is about 0.9, a circle 0.78, a glyph far less. */
    static float opaqueFraction(Bitmap b, int[] bb) {
        int w = b.getWidth();
        int[] px = new int[w * b.getHeight()];
        b.getPixels(px, 0, w, 0, 0, w, b.getHeight());
        long solid = 0, area = (long) (bb[2] - bb[0]) * (bb[3] - bb[1]);
        for (int y = bb[1]; y < bb[3]; y++) for (int x = bb[0]; x < bb[2]; x++) if ((px[y * w + x] >>> 24) >= 230) solid++;
        return area == 0 ? 0f : (float) solid / area;
    }

    /** Crops to the content box and centres it in a size x size bitmap, keeping the aspect ratio. pad = fraction of margin. */
    static Bitmap fitSquare(Bitmap src, int[] bb, int size, float pad) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float cw = bb[2] - bb[0], ch = bb[3] - bb[1];
        float avail = size * (1f - 2f * pad);
        float s = Math.min(avail / cw, avail / ch);
        float dw = cw * s, dh = ch * s;
        RectF dst = new RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        c.drawBitmap(src, new Rect(bb[0], bb[1], bb[2], bb[3]), dst, p);
        return out;
    }
}
