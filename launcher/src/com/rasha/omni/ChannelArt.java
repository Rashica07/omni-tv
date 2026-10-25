package com.rasha.omni;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Channel cards: a colour taken from the channel name (stable, so a channel always looks the same), the name and the country; where a logo file exists
 * ({@link ChannelLogos}) it sits on a light plate above the name. A channel that cannot be played is dimmed. Everything is drawn once into a cached bitmap.
 */
final class ChannelArt {
    /** 320x180 is plenty for the largest tile (about 266x150 px) and a quarter less texture memory than 400x224. Drawing code below stays in 400x224 units. */
    static final int W = 320, H = 180, THUMB_W = 96, THUMB_H = 54;

    private ChannelArt() { }

    private static final java.util.Map<String, Bitmap> CACHE = new java.util.HashMap<>();

    /** Cards are costly to draw on this CPU: one per (channel, state), made once and shared by the shelves, search and previews. */
    static Bitmap cached(String id, String name, String country, boolean live, boolean favorite) {
        String k = id + (live ? "v" : "x") + (favorite ? "f" : "");
        synchronized (CACHE) {
            Bitmap b = CACHE.get(k);
            if (b == null) { b = card(name, country, live, favorite, ChannelLogos.find(id, name, country == null || country.isEmpty())); CACHE.put(k, b); }
            return b;
        }
    }

    private static final java.util.Map<String, Bitmap> THUMBS = new java.util.HashMap<>();

    /** A small version for collapsed rows and lists: sampling a full card at 50 px every frame is what made the TV page slow. */
    static Bitmap thumb(String id, String name, String country, boolean live, boolean favorite) {
        String k = id + (live ? "v" : "x") + (favorite ? "f" : "");
        synchronized (THUMBS) {
            Bitmap b = THUMBS.get(k);
            if (b == null) { b = Bitmap.createScaledBitmap(cached(id, name, country, live, favorite), THUMB_W, THUMB_H, true); THUMBS.put(k, b); }
            return b;
        }
    }

    static Bitmap card(String name, String country, boolean live) { return card(name, country, live, false, null); }

    static Bitmap card(String name, String country, boolean live, boolean favorite, Bitmap logo) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(W / 400f, H / 224f);
        int h = Math.abs(name.hashCode()) % 360;
        int top = hsv(h, 0.62f, 0.62f), bottom = hsv((h + 24) % 360, 0.70f, 0.28f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 400 * 0.4f, 224, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 400, 224, p);
        p.setShader(null);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Theme.geist);
        t.setColor(0xFFFFFFFF);
        t.setTextSize(logo != null ? 38 : 46);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 800");
        CharSequence n = TextUtils.ellipsize(name, t, 400 - 56, TextUtils.TruncateAt.END);
        c.drawText(n, 0, n.length(), 28, 224 - (logo != null ? 46 : 52), t);
        t.setTextSize(24);
        t.setColor(0xCCFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 600");
        c.drawText(country == null || country.isEmpty() ? "Antenna" : PtvRules.group(country), 28, 224 - 20, t);       // no country = the TV's own tuner
        if (logo != null) {                                       // the channel's own logo on a light plate, so any logo colour stays legible
            p.setColor(0xF2FFFFFF);
            c.drawRoundRect(28, 18, 372, 128, 16, 16, p);
            float sc = Math.min(Math.min(300f / logo.getWidth(), 80f / logo.getHeight()), 2f);
            float lw = logo.getWidth() * sc, lh = logo.getHeight() * sc, lx = 200 - lw / 2f, ly = 73 - lh / 2f;
            Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(logo, null, new RectF(lx, ly, lx + lw, ly + lh), lp);
        }
        if (!live) {                                              // a channel that cannot be played is dimmed; the reason is shown when it is focused
            p.setColor(0x73000000);
            c.drawRect(0, 0, 400, 224, p);
        }
        if (favorite) {                                           // a small star: pinned by the user
            p.setColor(0xFFFFFFFF);
            android.graphics.Path star = new android.graphics.Path();
            float cx = 34, cy = 34, R = 15, r2 = 6.5f;
            for (int i = 0; i < 10; i++) {
                double a = Math.toRadians(-90 + i * 36);
                float rr = i % 2 == 0 ? R : r2;
                float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
                if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
            }
            star.close();
            c.drawPath(star, p);
        }
        return IconArt.rounded(b, 18f);
    }

    private static int hsv(float h, float s, float v) {
        return android.graphics.Color.HSVToColor(new float[]{h, s, v});
    }
}
