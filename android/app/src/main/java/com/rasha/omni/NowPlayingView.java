package com.rasha.omni;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;

import com.rasha.omni.common.Track;

import static com.rasha.omni.Theme.dp;

/**
 * Now Playing: a small glass pill (artwork, title, artist, a thin progress line) at the top right of Home, drawn by hand like the shelves.
 * It never takes focus by itself. Reach it with Right from the end of a shelf; OK opens the larger view with
 * previous / play-pause / next; Up, Down or Back closes it (Up again goes to the header, Down back to the shelves). Every field that the player did not publish is left out, nothing is invented.
 * Redraws happen on track/state changes, while the larger view animates, and once a second only while something is playing and visible.
 */
final class NowPlayingView extends View {
    interface Listener { void onRelease(); void onUp(); }

    static final float W_COMPACT = 340f, H_COMPACT = 62f, W_OPEN = 600f, H_OPEN = 232f;

    private final Listener listener;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint titleP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint subP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timeP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF a = new RectF();
    private final Path clip = new Path();

    private Track track;
    private Bitmap art, prevArt, soft;
    private int accent = 0xFFE8E8E8;
    private float open;          // 0 compact .. 1 larger view
    private float artMix = 1f;   // 0 = still the previous artwork, 1 = the new one
    private ValueAnimator openAnim, artAnim;
    private int ctl = 1;         // focused control in the larger view: 0 previous, 1 play/pause, 2 next
    private CharSequence tCompact, sCompact, tOpen, sOpen;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (track != null && track.getPlaying() && getVisibility() == VISIBLE && isAttachedToWindow()) {
                invalidate();
                postDelayed(this, 1000);
            }
        }
    };

    NowPlayingView(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        setFocusableInTouchMode(false);
        setVisibility(GONE);
        titleP.setTypeface(Theme.geist);
        subP.setTypeface(Theme.geist);
        timeP.setTypeface(Theme.geist);
        stroke.setStyle(Paint.Style.STROKE);
        titleP.setFakeBoldText(true);
        setDefaultFocusHighlightEnabled(false);
    }

    static int widthPx() { return dp(W_OPEN); }
    static int heightPx() { return dp(H_OPEN); }
    boolean isOpen() { return open > 0.5f && (openAnim == null || openTarget == 1f); }
    private float openTarget;

    boolean hasTrack() { return track != null; }

    boolean isPlaying() { return track != null && track.getPlaying(); }

    /** A new reading from the media session (or null when nothing is playing any more). */
    void set(Track t) {
        boolean newTrack = t != null && (track == null || !same(track, t));
        track = t;
        if (t == null) {
            setVisibility(GONE);
            collapse(false);
            if (hasFocus()) listener.onRelease();
            art = prevArt = soft = null;
            return;
        }
        if (newTrack) {
            prevArt = art;
            art = t.getArt();
            soft = art == null ? null : Bitmap.createScaledBitmap(art, 14, 14, true);
            accent = art == null ? 0xFFE8E8E8 : accentOf(soft);
            tCompact = sCompact = tOpen = sOpen = null;
            fadeArt();
        }
        if (isShown() || getVisibility() == GONE) { /* visibility is decided by the page (Home only) */ }
        removeCallbacks(tick);
        post(tick);
        invalidate();
    }

    private static boolean same(Track a, Track b) {
        return TextUtils.equals(a.getTitle(), b.getTitle()) && TextUtils.equals(a.getArtist(), b.getArtist()) && TextUtils.equals(a.getPkg(), b.getPkg());
    }

    /** A strong colour from the tiny copy of the artwork, kept bright enough to read as a thin line on the dark surface. */
    private static int accentOf(Bitmap tiny) {
        int[] px = new int[tiny.getWidth() * tiny.getHeight()];
        tiny.getPixels(px, 0, tiny.getWidth(), 0, 0, tiny.getWidth(), tiny.getHeight());
        int c = Dominant.pick(px);
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, Math.max(0.35f, hsv[1]));
        hsv[2] = Math.max(0.78f, hsv[2]);
        return android.graphics.Color.HSVToColor(hsv);
    }

    private void fadeArt() {
        if (artAnim != null) artAnim.cancel();
        if (prevArt == null) { artMix = 1f; return; }
        artMix = 0f;
        artAnim = ValueAnimator.ofFloat(0f, 1f).setDuration(320);
        artAnim.addUpdateListener(an -> { artMix = (float) an.getAnimatedValue(); invalidate(); });
        artAnim.start();
    }

    /** Called by the page: the pill exists only on Home, and only while there is something to show. */
    void setShown(boolean home) {
        setVisibility(home && track != null ? VISIBLE : GONE);
        if (getVisibility() == VISIBLE) { removeCallbacks(tick); post(tick); }
    }

    void expand() { animateOpen(1f); }

    void collapse(boolean animate) {
        if (animate) animateOpen(0f);
        else { if (openAnim != null) openAnim.cancel(); open = 0f; openTarget = 0f; invalidate(); }
    }

    private void animateOpen(float to) {
        if (openAnim != null) openAnim.cancel();
        openTarget = to;
        openAnim = ValueAnimator.ofFloat(open, to).setDuration(260);
        openAnim.setInterpolator(Theme.EXPO);
        openAnim.addUpdateListener(an -> { open = (float) an.getAnimatedValue(); invalidate(); });
        openAnim.start();
    }

    // ------------------------------------------------------------ keys

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (track == null) return super.onKeyDown(code, e);
        boolean big = openTarget == 1f;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:
                if (big) collapse(true); else listener.onUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (big) collapse(true); else listener.onRelease();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (big) { ctl = stepCtl(-1); invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (big) { ctl = stepCtl(1); invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() != 0) return true;
                if (!big) { expand(); return true; }
                act();
                return true;
            default:
                return super.onKeyDown(code, e);
        }
    }

    private boolean has(int c) { return c == 0 ? track.getCanPrev() : c == 2 ? track.getCanNext() : track.getCanPause(); }

    private int stepCtl(int d) {
        for (int c = ctl + d; c >= 0 && c <= 2; c += d) if (has(c)) return c;
        return ctl;
    }

    private void act() {
        if (!has(ctl)) return;
        try {
            if (ctl == 0) track.prev(); else if (ctl == 2) track.next(); else track.playPause();
        } catch (RuntimeException ignored) { }          // the player may have just gone away: the next session callback clears the view
    }

    @Override protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect prev) {
        super.onFocusChanged(gained, dir, prev);
        if (!gained) collapse(true);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        if (openAnim != null) openAnim.cancel();
        if (artAnim != null) artAnim.cancel();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------ drawing

    @Override public boolean hasOverlappingRendering() { return false; }

    @Override protected void onDraw(Canvas c) {
        Track t = track;
        if (t == null) return;
        float o = open;
        float w = dp(W_COMPACT + (W_OPEN - W_COMPACT) * o), h = dp(H_COMPACT + (H_OPEN - H_COMPACT) * o);
        float x0 = getWidth() - w, y0 = 0f;
        float rad = dp(18f + 8f * o);
        boolean focus = hasFocus();
        r.set(x0, y0, x0 + w, y0 + h);

        // surface: charcoal with a trace of the artwork's colour, and a hairline (a bright line when it has focus)
        fill.setColor(Theme.lerp(0xFF161618, accent, 0.07f + 0.05f * o));
        fill.setAlpha(0xEC);
        c.drawRoundRect(r, rad, rad, fill);
        if (o > 0.01f && soft != null) {                                     // a soft wash of the artwork (14 px, scaled up: no blur pass)
            int s = c.save();
            clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(clip);
            bmp.setAlpha((int) (96 * o));
            c.drawBitmap(soft, null, r, bmp);
            bmp.setAlpha(255);
            fill.setColor(0xFF111113); fill.setAlpha((int) (150 * o));
            c.drawRect(r, fill);
            c.restoreToCount(s);
        }
        stroke.setStrokeWidth(focus ? dp(2f) : dp(1f));
        stroke.setColor(focus ? 0xFFFFFFFF : 0x24FFFFFF);
        c.drawRoundRect(r, rad, rad, stroke);

        // artwork
        float pad = dp(8f + 12f * o), side = dp(46f + 94f * o);
        a.set(x0 + pad, y0 + pad, x0 + pad + side, y0 + pad + side);
        float ar = dp(9f + 5f * o);
        drawArt(c, a, ar);

        // text
        float tx = a.right + dp(12f + 6f * o), tw = r.right - tx - dp(14f + (o < 0.5f ? 22f : 0f));
        boolean big = o > 0.5f;
        titleP.setTextSize(dp(14f + 8f * o));
        subP.setTextSize(dp(12f + 3f * o));
        titleP.setColor(0xFFFFFFFF);
        subP.setColor(0xFFB8B8BC);
        CharSequence tt, ss;
        if (big) {
            if (tOpen == null) { tOpen = TextUtils.ellipsize(t.getTitle(), titleP, tw, TextUtils.TruncateAt.END); sOpen = t.getArtist() == null ? "" : TextUtils.ellipsize(t.getArtist(), subP, tw, TextUtils.TruncateAt.END); }
            tt = tOpen; ss = sOpen;
        } else {
            if (tCompact == null) { tCompact = TextUtils.ellipsize(t.getTitle(), titleP, tw, TextUtils.TruncateAt.END); sCompact = t.getArtist() == null ? "" : TextUtils.ellipsize(t.getArtist(), subP, tw, TextUtils.TruncateAt.END); }
            tt = tCompact; ss = sCompact;
        }
        float ty = a.top + dp(20f + 14f * o);
        c.drawText(tt, 0, tt.length(), tx, ty, titleP);
        if (ss.length() > 0) c.drawText(ss, 0, ss.length(), tx, ty + dp(18f + 4f * o), subP);

        // progress (only when the player reports a duration and a position)
        long dur = t.getDurationMs(), pos = t.position();
        boolean prog = dur > 0 && pos >= 0;
        if (prog) {
            float frac = Math.min(1f, pos / (float) dur);
            float lx = o < 0.5f ? x0 + dp(16) : tx, rx = r.right - dp(16f + 4f * o);
            float ly = o < 0.5f ? r.bottom - dp(7) : a.bottom - dp(30f);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(dp(o < 0.5f ? 2f : 3f));
            stroke.setColor(0x33FFFFFF);
            c.drawLine(lx, ly, rx, ly, stroke);
            stroke.setColor(accent);
            c.drawLine(lx, ly, lx + (rx - lx) * frac, ly, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
            if (big) {
                timeP.setTextSize(dp(11f)); timeP.setColor(0xFF9A9AA0);
                String l = clock(pos), rr = clock(dur);
                c.drawText(l, lx, ly + dp(16), timeP);
                c.drawText(rr, rx - timeP.measureText(rr), ly + dp(16), timeP);
            }
        }

        if (!big) {
            // a quiet state glyph at the right: playing bars or paused (read-only; controls live in the larger view)
            float gx = r.right - dp(26), gy = r.top + dp(31) - (prog ? dp(2) : 0);
            fill.setColor(t.getPlaying() ? accent : 0xFF8E8E93);
            fill.setAlpha(255);
            if (t.getPlaying()) { c.drawRoundRect(gx - dp(7), gy - dp(4), gx - dp(4), gy + dp(6), dp(1.5f), dp(1.5f), fill); c.drawRoundRect(gx - dp(1.5f), gy - dp(9), gx + dp(1.5f), gy + dp(6), dp(1.5f), dp(1.5f), fill); c.drawRoundRect(gx + dp(4), gy - dp(1), gx + dp(7), gy + dp(6), dp(1.5f), dp(1.5f), fill); }
            else { c.drawRoundRect(gx - dp(6), gy - dp(7), gx - dp(2.5f), gy + dp(7), dp(1.5f), dp(1.5f), fill); c.drawRoundRect(gx + dp(2.5f), gy - dp(7), gx + dp(6), gy + dp(7), dp(1.5f), dp(1.5f), fill); }
        }

        if (o > 0.4f) drawControls(c, t, a.right + dp(18), r.bottom - dp(60), (r.right - dp(18)) - (a.right + dp(18)), (int) (255 * Math.min(1f, (o - 0.4f) / 0.6f)));
    }

    private void drawArt(Canvas c, RectF dst, float rad) {
        int s = c.save();
        clip.reset(); clip.addRoundRect(dst, rad, rad, Path.Direction.CW);
        c.clipPath(clip);
        if (art == null) { fill.setColor(0xFF2A2A2E); fill.setAlpha(255); c.drawRect(dst, fill); }
        else {
            if (prevArt != null && artMix < 1f) { bmp.setAlpha(255); c.drawBitmap(prevArt, null, dst, bmp); }
            bmp.setAlpha((int) (255 * artMix));
            c.drawBitmap(art, null, dst, bmp);
            bmp.setAlpha(255);
        }
        c.restoreToCount(s);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(0x1AFFFFFF);
        c.drawRoundRect(dst, rad, rad, stroke);
    }

    private void drawControls(Canvas c, Track t, float x, float y, float w, int al) {
        float cx = x + w / 2f, gap = dp(76);
        boolean focus = hasFocus();
        for (int i = 0; i < 3; i++) {
            if (!has(i)) continue;
            float px = cx + (i - 1) * gap, rr = dp(i == 1 ? 24 : 19);
            boolean on = focus && ctl == i;
            fill.setColor(on ? 0xFFFFFFFF : 0x2EFFFFFF);
            fill.setAlpha(on ? al : (int) (al * 0.18f));
            c.drawCircle(px, y + dp(24), rr, fill);
            fill.setColor(on ? 0xFF111113 : 0xFFFFFFFF);
            fill.setAlpha(al);
            glyph(c, i, t.getPlaying(), px, y + dp(24));
        }
    }

    private final Path p = new Path();

    private void glyph(Canvas c, int which, boolean playing, float cx, float cy) {
        float s = dp(8);
        p.reset();
        if (which == 1) {
            if (playing) { c.drawRoundRect(cx - s * 0.8f, cy - s, cx - s * 0.15f, cy + s, dp(1.5f), dp(1.5f), fill); c.drawRoundRect(cx + s * 0.15f, cy - s, cx + s * 0.8f, cy + s, dp(1.5f), dp(1.5f), fill); return; }
            p.moveTo(cx - s * 0.7f, cy - s); p.lineTo(cx + s, cy); p.lineTo(cx - s * 0.7f, cy + s);
        } else if (which == 2) {
            p.moveTo(cx - s * 0.9f, cy - s * 0.8f); p.lineTo(cx + s * 0.1f, cy); p.lineTo(cx - s * 0.9f, cy + s * 0.8f);
            p.moveTo(cx + s * 0.2f, cy - s * 0.8f); p.lineTo(cx + s * 1.2f, cy); p.lineTo(cx + s * 0.2f, cy + s * 0.8f);
        } else {
            p.moveTo(cx + s * 0.9f, cy - s * 0.8f); p.lineTo(cx - s * 0.1f, cy); p.lineTo(cx + s * 0.9f, cy + s * 0.8f);
            p.moveTo(cx - s * 0.2f, cy - s * 0.8f); p.lineTo(cx - s * 1.2f, cy); p.lineTo(cx - s * 0.2f, cy + s * 0.8f);
        }
        p.close();
        c.drawPath(p, fill);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    @Override protected void onMeasure(int wSpec, int hSpec) { setMeasuredDimension(dp(W_OPEN), dp(H_OPEN)); }

    static int gravity() { return Gravity.TOP | Gravity.END; }
}
