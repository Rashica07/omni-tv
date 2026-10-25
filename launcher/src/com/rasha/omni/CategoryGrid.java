package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * The Apps landing page: large glass category cards, each with a name, a count, a glyph and a collage of its real app icons. It is one
 * custom-drawn focusable view (like {@link ShelfStack}) with a continuous focus position, so a card lifts and its collage turns to colour
 * as focus arrives. Only categories that contain apps are ever added by the caller.
 */
final class CategoryGrid extends View {
    static final class Card {
        final String key;
        final String name;
        final int glyph;
        final List<Bitmap> icons = new ArrayList<>();
        final List<Boolean> self = new ArrayList<>();
        int count;

        Card(String key, String name, int glyph) {
            this.key = key;
            this.name = name;
            this.glyph = glyph;
        }
    }

    interface Listener {
        void onFocus(Card c, int index);
        void onOpen(Card c);
        void onLeaveUp();
    }

    static final int G_STAR = 0, G_CLOCK = 1, G_GAMES = 2, G_PLAY = 3, G_MUSIC = 4, G_TOOLS = 5, G_ALL = 6, G_HIDDEN = 7;
    private static final int COLS = 4;
    // the screen is 960 dp wide at this density; with the 64 dp safe margins that leaves 832 dp: 4 x 190 + 3 x 14
    private static final float CARD_W = 190f, CARD_H = 140f, GAP = 14f;

    private final Listener listener;
    private List<Card> cards = new ArrayList<>();
    private int index;
    private float fx, fy, ring, enter = 1f;
    private final Spring sx = new Spring(260f, 0.84f, 0f, 0.002f, v -> { fx = v; invalidate(); });
    private final Spring sy = new Spring(260f, 0.84f, 0f, 0.002f, v -> { fy = v; invalidate(); });
    private final Spring sring = new Spring(400f, 0.9f, 0f, 0.002f, v -> { ring = v; invalidate(); });
    private final Spring senter = new Spring(200f, 0.9f, 1f, 0.002f, v -> { enter = v; invalidate(); });
    private Ambient ambient;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint name = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint count = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final RectF q = new RectF();
    private final Path path = new Path();
    CategoryGrid(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        name.setTypeface(Theme.geist);
        name.setTextSize(dp(21));
        count.setTypeface(Theme.geist);
        count.setTextSize(dp(13));
        count.setColor(Theme.TEXT2);
        if (Build.VERSION.SDK_INT >= 26) {
            name.setFontVariationSettings("'wght' 700");
            count.setFontVariationSettings("'wght' 500");
        }
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
    }

    void setAmbient(Ambient a) { ambient = a; }

    int index() { return index; }

    Card current() { return index < cards.size() ? cards.get(index) : null; }

    void setCards(List<Card> list, String focusKey) {
        cards = list;
        index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).key.equals(focusKey)) index = i;
        sx.snap(index % COLS);
        sy.snap(index / COLS);
        invalidate();
        if (current() != null) listener.onFocus(current(), index);
    }

    void replay() {
        senter.snap(0f);
        senter.to(1f);
    }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        sring.to(gained ? 1f : 0f);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        int n = cards.size();
        if (n == 0) return super.onKeyDown(code, e);
        int row = index / COLS, colI = index % COLS, rows = (n + COLS - 1) / COLS;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (colI > 0) go(index - 1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (colI < COLS - 1 && index + 1 < n) go(index + 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (row < rows - 1) go(Math.min(n - 1, index + COLS)); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (row > 0) go(index - COLS); else listener.onLeaveUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) e.startTracking();
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (e.isTracking() && !e.isCanceled() && current() != null) listener.onOpen(current());
            return true;
        }
        return super.onKeyUp(code, e);
    }

    private void go(int i) {
        index = i;
        sx.to(i % COLS);
        sy.to(i / COLS);
        OmniLog.state("focus page=Apps landing=" + cards.get(i).key + " idx=" + i);
        listener.onFocus(cards.get(i), i);
    }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        float e = Math.max(0f, Math.min(1f, enter));
        for (int i = 0; i < cards.size(); i++) {
            int col = i % COLS, row = i / COLS;
            float hz = Accordion.bump((float) Math.hypot(col - fx, row - fy)) * Math.min(1f, ring + 0.0f);
            float l = dp(Theme.SAFE_SIDE) + col * dp(CARD_W + GAP);
            float t = dp(16) + row * dp(CARD_H + GAP) + (1f - e) * dp(24) * (1f + row * 0.4f);
            drawCard(c, cards.get(i), l, t, dp(CARD_W), dp(CARD_H), hz, (int) (255 * e));
        }
    }

    private void drawCard(Canvas c, Card card, float l, float t, float w, float h, float hz, int al) {
        int sv = c.save();
        c.translate(0, -dp(Theme.FOCUS_LIFT) * hz);
        float sc = 1f + Theme.FOCUS_SCALE * hz * 0.7f;
        c.scale(sc, sc, l + w / 2f, t + h / 2f);
        r.set(l, t, l + w, t + h);
        float rad = dp(18);
        Glass.draw(c, r, rad, ambient, getLeft(), getTop(), (0.85f + 0.15f * hz) * al / 255f, hz > 0.3f);

        // glyph and text
        int fg = Theme.lerp(Theme.TEXT2, Theme.WHITE, hz);
        drawGlyph(c, card.glyph, l + dp(18), t + dp(16), dp(26), Theme.alpha(fg, al));
        name.setColor(Theme.alpha(Theme.WHITE, al));
        CharSequence nm = TextUtils.ellipsize(card.name, name, w - dp(36), TextUtils.TruncateAt.END);
        c.drawText(nm, 0, nm.length(), l + dp(18), t + h - dp(34), name);
        count.setColor(Theme.alpha(Theme.TEXT2, al));
        String cs = card.count == 1 ? "1 app" : card.count + " apps";
        c.drawText(cs, l + dp(18), t + h - dp(14), count);

        // collage of real icons, grayscale until the card is focused
        int n = Math.min(4, card.icons.size());
        float cell = dp(36), pad = dp(6), gx = l + w - dp(16) - cell * 2 - pad, gy = t + dp(16);
        for (int i = 0; i < n; i++) {
            int cx = i % 2, cy = i / 2;
            float x = gx + cx * (cell + pad), y = gy + cy * (cell + pad);
            boolean self = card.self.get(i);
            float inset = self ? 0f : dp(5);
            q.set(x + inset, y + inset, x + cell - inset, y + cell - inset);
            if (!self) {
                p.setShader(null);
                p.setColor(Theme.alpha(Theme.S3, al));
                c.drawRoundRect(x, y, x + cell, y + cell, dp(8), dp(8), p);
            }
            Bitmap b = card.icons.get(i);
            bmp.setColorFilter(null);
            bmp.setAlpha((int) (al * (0.86f + 0.14f * hz)));      // real colours always; focus only brightens
            c.drawBitmap(b, null, q, bmp);
        }
        bmp.setColorFilter(null);
        bmp.setAlpha(255);

        if (hz > 0.02f && ring > 0.02f) {
            float gap = dp(Theme.RING_GAP), rw = dp(Theme.RING);
            int a = (int) (al * Math.min(1f, hz * 1.4f));
            p.setStyle(Paint.Style.STROKE);
            p.setShader(null);
            r.set(l - gap - rw / 2f, t - gap - rw / 2f, l + w + gap + rw / 2f, t + h + gap + rw / 2f);
            p.setStrokeWidth(gap + rw);
            p.setColor(Theme.alpha(Theme.BLACK, a));
            q.set(r);
            q.inset(rw / 2f, rw / 2f);
            c.drawRoundRect(q, rad + gap, rad + gap, p);
            p.setStrokeWidth(rw);
            p.setColor(Theme.alpha(Theme.WHITE, a));
            c.drawRoundRect(r, rad + gap + rw / 2f, rad + gap + rw / 2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        c.restoreToCount(sv);
    }

    /** Category glyphs on a 24-unit grid. */
    private void drawGlyph(Canvas c, int g, float x, float y, float size, int color) {
        float u = size / 24f;
        ink.setColor(color);
        ink.setStrokeWidth(2f * u);
        int sv = c.save();
        c.translate(x, y);        // (x, y) is the top-left of a size x size box; glyph coordinates are on a 24-unit grid
        switch (g) {
            case G_STAR:
                path.reset();
                for (int i = 0; i < 10; i++) {
                    double a = -Math.PI / 2 + i * Math.PI / 5;
                    float rr = (i % 2 == 0 ? 10f : 4.4f) * u;
                    float px = 12f * u + (float) Math.cos(a) * rr, py = 12.6f * u + (float) Math.sin(a) * rr;
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                path.close();
                c.drawPath(path, ink);
                break;
            case G_CLOCK:
                c.drawCircle(12f * u, 12f * u, 9f * u, ink);
                c.drawLine(12f * u, 6.5f * u, 12f * u, 12f * u, ink);
                c.drawLine(12f * u, 12f * u, 16f * u, 14.5f * u, ink);
                break;
            case G_GAMES:
                DeviceArt.icon(c, Inputs.T_CONSOLE, 12f * u, 12f * u, 24f * u, color);
                break;
            case G_PLAY:
                r.set(3f * u, 5f * u, 21f * u, 19f * u);
                c.drawRoundRect(r, 3.5f * u, 3.5f * u, ink);
                p.setStyle(Paint.Style.FILL); p.setShader(null); p.setColor(color);
                path.reset(); path.moveTo(10f * u, 9f * u); path.lineTo(15.5f * u, 12f * u); path.lineTo(10f * u, 15f * u); path.close();
                c.drawPath(path, p);
                break;
            case G_MUSIC:
                c.drawCircle(8f * u, 17.5f * u, 3f * u, ink);
                c.drawLine(11f * u, 17.5f * u, 11f * u, 5f * u, ink);
                c.drawLine(11f * u, 5f * u, 19f * u, 7.5f * u, ink);
                c.drawLine(19f * u, 7.5f * u, 19f * u, 15f * u, ink);
                c.drawCircle(16f * u, 15.5f * u, 3f * u, ink);
                break;
            case G_TOOLS:
                c.drawLine(4f * u, 8f * u, 20f * u, 8f * u, ink);
                c.drawLine(4f * u, 16f * u, 20f * u, 16f * u, ink);
                c.drawCircle(9f * u, 8f * u, 2.4f * u, ink);
                c.drawCircle(15f * u, 16f * u, 2.4f * u, ink);
                break;
            case G_HIDDEN:
                c.drawCircle(12f * u, 12f * u, 4f * u, ink);
                r.set(2.5f * u, 6f * u, 21.5f * u, 18f * u);
                c.drawOval(r, ink);
                c.drawLine(4f * u, 20f * u, 20f * u, 4f * u, ink);
                break;
            default:   // all apps
                for (int gx = 0; gx < 2; gx++) for (int gy = 0; gy < 2; gy++) {
                    r.set((3.5f + gx * 9.5f) * u, (3.5f + gy * 9.5f) * u, (11f + gx * 9.5f) * u, (11f + gy * 9.5f) * u);
                    c.drawRoundRect(r, 2.2f * u, 2.2f * u, ink);
                }
        }
        c.restoreToCount(sv);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        Card cur = current();
        if (cur != null) info.setContentDescription(cur.name + ", " + cur.count + " apps, " + (index + 1) + " of " + cards.size());
    }
}
