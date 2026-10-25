package com.rasha.omni;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Colourful filled weather icons, drawn from shapes (no image files). Sizes scale freely, so the same code makes the hero icon and the small ones. */
final class WeatherGlyphs {
    private WeatherGlyphs() { }

    static final int SUN = 0xFFFFC83D, MOON = 0xFFEDE8CC, CLOUD = 0xFFEAF0F6, CLOUD_GRAY = 0xFFB3BFCB, CLOUD_DARK = 0xFF8D97B0, DROP = 0xFF6EB6FF, BOLT = 0xFFFFD84D;

    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    /** angle rotates the sun's rays (degrees); pass 0 for a still icon. */
    static void draw(Canvas c, int kind, boolean day, float cx, float cy, float s, Paint p, float angle) {
        p.setStyle(Paint.Style.FILL);
        switch (kind) {
            case WeatherCodes.CLEAR:
                if (day) sun(c, cx, cy, s, p, angle); else moon(c, cx, cy, s, p);
                break;
            case WeatherCodes.PARTLY:
                if (day) sun(c, cx - s * 0.16f, cy - s * 0.14f, s * 0.75f, p, angle); else moon(c, cx - s * 0.16f, cy - s * 0.14f, s * 0.75f, p);
                cloud(c, cx + s * 0.08f, cy + s * 0.10f, s * 0.86f, p, CLOUD);
                break;
            case WeatherCodes.CLOUD: cloud(c, cx, cy, s, p, CLOUD); break;
            case WeatherCodes.FOG:
                p.setColor(CLOUD_GRAY);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.075f);
                for (int i = 0; i < 4; i++) {
                    float y = cy - s * 0.24f + i * s * 0.16f, inset = (i % 2) * s * 0.08f;
                    c.drawLine(cx - s * 0.34f + inset, y, cx + s * 0.34f - inset, y, p);
                }
                p.setStyle(Paint.Style.FILL);
                break;
            case WeatherCodes.RAIN:
                cloud(c, cx, cy - s * 0.1f, s * 0.92f, p, CLOUD_GRAY);
                p.setColor(DROP);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(s * 0.065f);
                for (int i = 0; i < 3; i++) c.drawLine(cx - s * 0.2f + i * s * 0.2f, cy + s * 0.22f, cx - s * 0.26f + i * s * 0.2f, cy + s * 0.4f, p);
                p.setStyle(Paint.Style.FILL);
                break;
            case WeatherCodes.SNOW:
                cloud(c, cx, cy - s * 0.1f, s * 0.92f, p, CLOUD);
                p.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) c.drawCircle(cx - s * 0.2f + i * s * 0.2f, cy + s * (i == 1 ? 0.38f : 0.3f), s * 0.05f, p);
                break;
            case WeatherCodes.STORM:
                cloud(c, cx, cy - s * 0.1f, s * 0.92f, p, CLOUD_DARK);
                p.setColor(BOLT);
                PATH.reset();
                PATH.moveTo(cx + s * 0.04f, cy + s * 0.12f);
                PATH.lineTo(cx - s * 0.1f, cy + s * 0.32f);
                PATH.lineTo(cx + s * 0.0f, cy + s * 0.32f);
                PATH.lineTo(cx - s * 0.08f, cy + s * 0.5f);
                PATH.lineTo(cx + s * 0.16f, cy + s * 0.26f);
                PATH.lineTo(cx + s * 0.05f, cy + s * 0.26f);
                PATH.close();
                c.drawPath(PATH, p);
                break;
            default: cloud(c, cx, cy, s, p, CLOUD_GRAY);
        }
    }

    private static void sun(Canvas c, float cx, float cy, float s, Paint p, float angle) {
        p.setColor(SUN);
        c.drawCircle(cx, cy, s * 0.2f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(s * 0.06f);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(angle + i * 45);
            c.drawLine(cx + (float) Math.cos(a) * s * 0.31f, cy + (float) Math.sin(a) * s * 0.31f, cx + (float) Math.cos(a) * s * 0.43f, cy + (float) Math.sin(a) * s * 0.43f, p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    /** The crescent is built once (a path boolean-op per frame was needlessly slow) and scaled to size. */
    private static final Path MOON_UNIT = new Path();
    static {
        MOON_UNIT.addCircle(0f, 0f, 0.28f, Path.Direction.CW);
        Path cut = new Path();
        cut.addCircle(0.16f, -0.1f, 0.25f, Path.Direction.CW);
        MOON_UNIT.op(cut, Path.Op.DIFFERENCE);
    }

    private static void moon(Canvas c, float cx, float cy, float s, Paint p) {
        p.setColor(MOON);
        c.save();
        c.translate(cx, cy);
        c.scale(s, s);
        c.drawPath(MOON_UNIT, p);
        c.restore();
    }

    private static void cloud(Canvas c, float cx, float cy, float s, Paint p, int color) {
        p.setColor(color);
        c.drawCircle(cx - s * 0.18f, cy + s * 0.04f, s * 0.19f, p);
        c.drawCircle(cx + s * 0.04f, cy - s * 0.08f, s * 0.26f, p);
        c.drawCircle(cx + s * 0.25f, cy + s * 0.06f, s * 0.17f, p);
        R.set(cx - s * 0.37f, cy + s * 0.02f, cx + s * 0.42f, cy + s * 0.23f);
        c.drawRoundRect(R, s * 0.1f, s * 0.1f, p);
    }
}
