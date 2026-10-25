package com.rasha.omni;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;

/** The card for a TV input, drawn like a channel card (same size, same type) so inputs read as one more kind of thing to watch. Nothing here is a picture of the input. */
final class InputArt {
    private InputArt() { }

    private static final java.util.Map<String, Bitmap> CACHE = new java.util.HashMap<>();

    static Bitmap cached(Inputs.Entry e) {
        String k = e.id + "|" + e.displayName() + "|" + e.state;
        synchronized (CACHE) {
            Bitmap b = CACHE.get(k);
            if (b == null) { b = card(e); CACHE.put(k, b); }
            return b;
        }
    }

    private static Bitmap card(Inputs.Entry e) {
        Bitmap b = Bitmap.createBitmap(ChannelArt.W, ChannelArt.H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.scale(ChannelArt.W / 400f, ChannelArt.H / 224f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        boolean live = e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED;
        p.setShader(new LinearGradient(0, 0, 160, 224, live ? 0xFF2A2F3A : 0xFF1C1C1C, 0xFF0A0A0A, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 400, 224, p);
        p.setShader(null);
        TextPaint t = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
        t.setTypeface(Theme.geist);
        t.setColor(0xFFFFFFFF);
        t.setTextSize(44);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 800");
        CharSequence n = TextUtils.ellipsize(e.displayName(), t, 400 - 56, TextUtils.TruncateAt.END);
        c.drawText(n, 0, n.length(), 28, 224 - 56, t);
        t.setTextSize(24);
        t.setColor(0xCCFFFFFF);
        if (android.os.Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 600");
        c.drawText(e.portLabel + "  ·  " + e.stateText(), 28, 224 - 22, t);
        p.setColor(live ? 0xFF35C759 : 0x88FFFFFF);                          // the one colour on the card: whether there is a signal
        c.drawCircle(400 - 34, 34, 9, p);
        return IconArt.rounded(b, 18f);
    }
}
