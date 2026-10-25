package com.rasha.omni;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Hand-drawn device-category icons and the card bitmaps for the Inputs page. Cards are rendered once per state change and drawn by
 * {@link ShelfStack} like any other tile, so inputs behave exactly like apps (focus, ring, grayscale until focused, accessibility).
 */
final class DeviceArt {
    static final int W = 400, H = 224;

    private DeviceArt() { }

    /** Draws the icon for a device type centred at (cx, cy) inside a size x size box, in the given colour. */
    static void icon(Canvas c, int type, float cx, float cy, float size, int color) {
        float u = size / 24f;
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setStrokeWidth(1.7f * u);
        ink.setColor(color);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        int sv = c.save();
        c.translate(cx - 12f * u, cy - 12f * u);
        RectF r = new RectF();
        Path p = new Path();
        switch (type) {
            case Inputs.T_CONSOLE:       // game controller
                p.moveTo(7f * u, 8f * u);
                p.cubicTo(3.5f * u, 8f * u, 1.8f * u, 14f * u, 2.6f * u, 17.5f * u);
                p.cubicTo(3.2f * u, 19.6f * u, 5.6f * u, 19.6f * u, 6.8f * u, 17.4f * u);
                p.lineTo(8.4f * u, 15f * u); p.lineTo(15.6f * u, 15f * u); p.lineTo(17.2f * u, 17.4f * u);
                p.cubicTo(18.4f * u, 19.6f * u, 20.8f * u, 19.6f * u, 21.4f * u, 17.5f * u);
                p.cubicTo(22.2f * u, 14f * u, 20.5f * u, 8f * u, 17f * u, 8f * u);
                p.close();
                c.drawPath(p, ink);
                c.drawLine(7.3f * u, 10.5f * u, 7.3f * u, 13.5f * u, ink);
                c.drawLine(5.8f * u, 12f * u, 8.8f * u, 12f * u, ink);
                c.drawCircle(15.8f * u, 10.9f * u, 0.95f * u, fill);
                c.drawCircle(17.7f * u, 13.1f * u, 0.95f * u, fill);
                break;
            case Inputs.T_PC:            // monitor on a stand
                r.set(3f * u, 4.5f * u, 21f * u, 15.5f * u);
                c.drawRoundRect(r, 2f * u, 2f * u, ink);
                c.drawLine(12f * u, 15.5f * u, 12f * u, 19f * u, ink);
                c.drawLine(7.5f * u, 19.5f * u, 16.5f * u, 19.5f * u, ink);
                break;
            case Inputs.T_STREAMER:      // a stick with its HDMI plug
                r.set(6f * u, 9f * u, 21f * u, 15f * u);
                c.drawRoundRect(r, 2.2f * u, 2.2f * u, ink);
                c.drawLine(6f * u, 10.6f * u, 3f * u, 10.6f * u, ink);
                c.drawLine(6f * u, 13.4f * u, 3f * u, 13.4f * u, ink);
                c.drawCircle(18.3f * u, 12f * u, 0.9f * u, fill);
                break;
            case Inputs.T_BOX:           // set-top box
                r.set(2.5f * u, 8.5f * u, 21.5f * u, 16.5f * u);
                c.drawRoundRect(r, 2.2f * u, 2.2f * u, ink);
                c.drawLine(6f * u, 12.5f * u, 12f * u, 12.5f * u, ink);
                c.drawCircle(17f * u, 12.5f * u, 0.9f * u, fill);
                c.drawCircle(19f * u, 12.5f * u, 0.9f * u, fill);
                break;
            case Inputs.T_AUDIO:         // soundbar
                r.set(2f * u, 9.5f * u, 22f * u, 15f * u);
                c.drawRoundRect(r, 2.6f * u, 2.6f * u, ink);
                c.drawCircle(6.5f * u, 12.25f * u, 1.7f * u, ink);
                c.drawCircle(17.5f * u, 12.25f * u, 1.7f * u, ink);
                c.drawLine(10.5f * u, 12.25f * u, 13.5f * u, 12.25f * u, ink);
                break;
            case Inputs.T_TUNER:         // aerial
                c.drawLine(12f * u, 20f * u, 12f * u, 10f * u, ink);
                c.drawLine(12f * u, 20f * u, 8f * u, 20f * u, ink);
                c.drawLine(12f * u, 20f * u, 16f * u, 20f * u, ink);
                c.drawCircle(12f * u, 8.5f * u, 1.3f * u, fill);
                r.set(7f * u, 4f * u, 17f * u, 14f * u);
                c.drawArc(r, 215f, 110f, false, ink);
                r.set(4.5f * u, 1.5f * u, 19.5f * u, 16.5f * u);
                c.drawArc(r, 220f, 100f, false, ink);
                break;
            case Inputs.T_AV:            // analogue connectors
                c.drawCircle(6.5f * u, 12f * u, 2.6f * u, ink);
                c.drawCircle(12f * u, 12f * u, 2.6f * u, ink);
                c.drawCircle(17.5f * u, 12f * u, 2.6f * u, ink);
                break;
            default:                     // a generic HDMI source
                p.moveTo(3.5f * u, 7f * u); p.lineTo(20.5f * u, 7f * u); p.lineTo(20.5f * u, 13f * u);
                p.lineTo(17f * u, 17f * u); p.lineTo(7f * u, 17f * u); p.lineTo(3.5f * u, 13f * u); p.close();
                c.drawPath(p, ink);
                c.drawLine(8f * u, 10.5f * u, 8f * u, 13f * u, ink);
                c.drawLine(12f * u, 10.5f * u, 12f * u, 13f * u, ink);
                c.drawLine(16f * u, 10.5f * u, 16f * u, 13f * u, ink);
        }
        c.restoreToCount(sv);
    }

    /** One card: plate, large device icon, name, status line, and a dot that shows whether a signal is present. */
    static Bitmap card(Inputs.Entry e) {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Theme.S2);
        c.drawRoundRect(new RectF(0, 0, W, H), 24f, 24f, p);
        // quiet top sheen, the same cue the glass material uses
        p.setShader(new android.graphics.LinearGradient(0, 0, 0, H * 0.6f, 0x22FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, W, H), 24f, 24f, p);
        p.setShader(null);
        icon(c, e.type, 78, 88, 96, Theme.WHITE);
        TextPaint t = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        t.setTypeface(Theme.geist);
        if (Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 700");
        t.setTextSize(34);
        t.setColor(Theme.WHITE);
        CharSequence name = TextUtils.ellipsize(e.displayName(), t, W - 56, TextUtils.TruncateAt.END);
        c.drawText(name, 0, name.length(), 28, H - 56, t);
        if (Build.VERSION.SDK_INT >= 26) t.setFontVariationSettings("'wght' 500");
        t.setTextSize(24);
        t.setColor(Theme.TEXT2);
        String sub = e.hdmi && e.cecName != null ? e.portLabel + ", " + e.stateText().toLowerCase(java.util.Locale.ROOT) : e.stateText();
        CharSequence st = TextUtils.ellipsize(sub, t, W - 56, TextUtils.TruncateAt.END);
        c.drawText(st, 0, st.length(), 28, H - 22, t);
        // signal dot: solid when a source is present, ring when standby, hollow dashed look when nothing is connected
        boolean on = e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED;
        boolean standby = e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY;
        Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
        d.setColor(Theme.WHITE);
        d.setStyle(on ? Paint.Style.FILL : Paint.Style.STROKE);
        d.setStrokeWidth(4f);
        if (!on && !standby) d.setAlpha(110);
        c.drawCircle(W - 40, 40, 11f, d);
        return b;
    }
}
