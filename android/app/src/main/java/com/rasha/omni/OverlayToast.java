package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import static com.rasha.omni.Theme.dp;

/**
 * A notice that appears above any app (top-right glass-dark pill, 4.5 s), for device events that happen while OMNI is not in front, such as casting
 * starting, which brings the cast receiver forward. Needs "display over other apps" (the same permission as the live HDMI preview); without it nothing is shown.
 * Never takes focus or touches: it cannot get in the way of what is playing.
 */
final class OverlayToast {
    private static View current;
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private OverlayToast() { }

    static void show(Context c, String heading, String detail) {
        final Context app = c.getApplicationContext();
        if (!Settings.canDrawOverlays(app)) return;
        UI.post(() -> {
            final WindowManager wm = (WindowManager) app.getSystemService(Context.WINDOW_SERVICE);
            hide(wm);
            Pill v = new Pill(app, heading, detail);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(dp(380), dp(74),
                    Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.x = dp(28);
            lp.y = dp(24);
            lp.windowAnimations = android.R.style.Animation_Toast;
            try { wm.addView(v, lp); current = v; } catch (RuntimeException e) { return; }
            UI.postDelayed(() -> { if (current == v) hide(wm); }, 4500);
        });
    }

    private static void hide(WindowManager wm) {
        if (current != null) { try { wm.removeView(current); } catch (RuntimeException ignored) { } current = null; }
    }

    private static final class Pill extends View {
        private final String heading, detail;
        private final TextPaint h = new TextPaint(Paint.ANTI_ALIAS_FLAG), d = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Pill(Context c, String heading, String detail) {
            super(c);
            this.heading = heading;
            this.detail = detail == null ? "" : detail;
            Theme.init(c);
            h.setTypeface(Theme.geist); d.setTypeface(Theme.geist);
            h.setTextSize(dp(16)); h.setColor(Theme.WHITE);
            d.setTextSize(dp(13)); d.setColor(Theme.TEXT2);
            if (Build.VERSION.SDK_INT >= 26) { h.setFontVariationSettings("'wght' 700"); d.setFontVariationSettings("'wght' 500"); }
        }

        @Override protected void onDraw(Canvas c) {
            r.set(0, 0, getWidth(), getHeight());
            float rad = getHeight() / 2f;
            p.setColor(0xE6161616); c.drawRoundRect(r, rad, rad, p);             // no wallpaper to sample above other apps: a dark glass tone instead
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(1)); p.setColor(0x40FFFFFF);
            r.inset(dp(0.5f), dp(0.5f)); c.drawRoundRect(r, rad, rad, p); p.setStyle(Paint.Style.FILL);
            p.setColor(Theme.WHITE); c.drawCircle(dp(30), getHeight() / 2f, dp(5), p);
            float x = dp(50), w = getWidth() - x - dp(26);
            CharSequence hh = TextUtils.ellipsize(heading, h, w, TextUtils.TruncateAt.END), dd = TextUtils.ellipsize(detail, d, w, TextUtils.TruncateAt.END);
            c.drawText(hh, 0, hh.length(), x, dp(32), h);
            c.drawText(dd, 0, dd.length(), x, dp(53), d);
        }
    }
}
