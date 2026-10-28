package com.rasha.omni;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;

import static com.rasha.omni.Theme.dp;

/**
 * The zoom launch (off by default). A window owned by OMNI, above everything, that shows the tile and grows it from its place to the whole screen while the
 * app starts behind it, then dissolves once the app is on screen (the launcher being stopped is the signal), or after a hard limit. It does not make the
 * app load faster (Android always lays an app out at full size); it gives instant feedback and hides the load. It needs the "draw over other apps"
 * permission OMNI already asks for the HDMI picture; without it the system's own scale-up is used. See docs/plans/ZOOM-LAUNCH.md.
 */
final class LaunchShell extends View {
    private static LaunchShell current;

    private final WindowManager wm;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF from = new RectF(), now = new RectF(), src = new RectF(), dst = new RectF();
    private final Bitmap art, icon;
    private final boolean banner;
    private static final float SLOW = OmniLog.flag("OmniSlowZoom") ? 6f : 1f;      // developer only: the whole zoom 6x slower, to inspect frames
    private final int color;
    private final long start = SystemClock.uptimeMillis();
    private float t;                // 0 tile .. 1 full screen
    private float alpha = 1f;
    private boolean ready, dissolving;
    private ValueAnimator grow;

    private LaunchShell(Context c, RectF tile, Bitmap art, Bitmap icon, boolean banner, int color) {
        super(c);
        wm = (WindowManager) c.getSystemService(Context.WINDOW_SERVICE);
        from.set(tile);
        this.art = art; this.icon = icon; this.banner = banner;
        this.color = color == 0 ? Theme.S2 : Theme.lerp(0xFF0A0A0A, color | 0xFF000000, 0.55f);       // the app's own colour, dark enough that the picture on it stays readable
        line.setStyle(Paint.Style.STROKE); line.setStrokeCap(Paint.Cap.ROUND);
        setClickable(false); setFocusable(false);
    }

    /** Starts the zoom from the tile's rectangle (screen pixels). Returns false (and nothing is shown) when the overlay window cannot be added. */
    static boolean start(Context c, RectF tile, Bitmap art, Bitmap icon, boolean banner, int color) {
        cancel();
        LaunchShell s = new LaunchShell(c.getApplicationContext(), tile, art, icon, banner, color);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        try { s.wm.addView(s, lp); } catch (RuntimeException e) { OmniLog.state("zoom addView failed " + e.getClass().getSimpleName()); return false; }
        current = s;
        s.grow = ValueAnimator.ofFloat(0f, 1f).setDuration((long) (ZoomRules.GROW_MS * SLOW));
        s.grow.setInterpolator(Theme.EXPO);
        s.grow.addUpdateListener(a -> { s.t = (float) a.getAnimatedValue(); s.invalidate(); });
        s.grow.start();
        s.ui.post(s.tick);
        OmniLog.state("zoom start");
        return true;
    }

    /** The app is on screen (the launcher was stopped): the shell may dissolve once its minimum time has passed. */
    static void appReady() { if (current != null) current.ready = true; }

    /** The launch failed or the person is back on the launcher: remove the shell at once. */
    static void cancel() {
        LaunchShell s = current;
        current = null;
        if (s != null) s.remove();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long el = SystemClock.uptimeMillis() - start;
            if (!dissolving && ZoomRules.shouldDissolve((long) (el / SLOW), ready)) { dissolve(); return; }
            invalidate();                                           // the progress line moves while a slow app loads
            ui.postDelayed(this, 16);
        }
    };

    private void dissolve() {
        dissolving = true;
        ValueAnimator a = ValueAnimator.ofFloat(1f, 0f).setDuration((long) (ZoomRules.DISSOLVE_MS * SLOW));
        a.addUpdateListener(v -> { alpha = (float) v.getAnimatedValue(); invalidate(); });
        a.addListener(new AnimatorListenerAdapter() { @Override public void onAnimationEnd(Animator an) { if (current == LaunchShell.this) current = null; remove(); } });
        a.start();
    }

    private void remove() {
        ui.removeCallbacksAndMessages(null);
        if (grow != null) grow.cancel();
        try { wm.removeView(this); } catch (RuntimeException ignored) { }
        OmniLog.state("zoom end");
    }

    @Override public boolean hasOverlappingRendering() { return false; }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0) return;
        now.set(ZoomRules.lerp(from.left, 0f, t), ZoomRules.lerp(from.top, 0f, t), ZoomRules.lerp(from.right, w, t), ZoomRules.lerp(from.bottom, h, t));
        float rad = ZoomRules.radius(t, dp(ZoomRules.TILE_RADIUS_DP));
        int a = (int) (255 * alpha);
        fill.setColor(Theme.alpha(color, a));
        c.drawRoundRect(now, rad, rad, fill);
        int s0 = c.save();
        clip.reset(); clip.addRoundRect(now, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        Bitmap tileArt = art != null ? art : icon;
        if (tileArt != null) {                                         // the tile's own artwork grows with the rectangle, then gives way to the calm colour
            bmp.setAlpha((int) (a * ZoomRules.artAlpha(t)));
            if (banner) {                                              // banner artwork fills the rectangle, centre-cropped
                float sc = Math.max(now.width() / tileArt.getWidth(), now.height() / tileArt.getHeight());
                float sw = tileArt.getWidth() * sc, sh = tileArt.getHeight() * sc;
                dst.set(now.centerX() - sw / 2f, now.centerY() - sh / 2f, now.centerX() + sw / 2f, now.centerY() + sh / 2f);
            } else {                                                   // an icon sits in the middle of its colour, ending at a size that is not stretched
                float sz = ZoomRules.lerp(Math.min(from.width(), from.height()) * 0.62f, dp(ZoomRules.END_ICON_DP), t);
                dst.set(now.centerX() - sz / 2f, now.centerY() - sz / 2f, now.centerX() + sz / 2f, now.centerY() + sz / 2f);
            }
            c.drawBitmap(tileArt, null, dst, bmp);
        }
        if (banner && icon != null) {                                  // a banner's small icon fades in at the centre as the full-screen colour settles
            float sz = dp(ZoomRules.END_ICON_DP);
            bmp.setAlpha((int) (a * ZoomRules.iconAlpha(t)));
            dst.set(now.centerX() - sz / 2f, now.centerY() - sz / 2f, now.centerX() + sz / 2f, now.centerY() + sz / 2f);
            c.drawBitmap(icon, null, dst, bmp);
        }
        c.restoreToCount(s0);
        long el = SystemClock.uptimeMillis() - start;
        if (!dissolving && ZoomRules.showProgress(el, ready)) {        // a slow app: one thin line sweeping along the bottom, nothing else
            float p = (el % 1100) / 1100f;
            line.setStrokeWidth(dp(2)); line.setColor(0x66FFFFFF);
            float y = h - dp(Theme.SAFE_VERT);
            float x0 = w * 0.3f, x1 = w * 0.7f, seg = (x1 - x0) * 0.28f, px = x0 + (x1 - x0 - seg) * p;
            c.drawLine(x0, y, x1, y, line);
            line.setColor(0xCCFFFFFF);
            c.drawLine(px, y, px + seg, y, line);
        }
    }
}
