package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.view.KeyEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * Channels as a grid of large, readable cards in sections (Kosovo, Albania, Antenna), custom drawn in one view. Navigation is geometric (Up/Down
 * pick the nearest card in the next row, Right at the end of a row asks the page for the Inputs rail). One white ring glides between cards; the grid
 * scrolls vertically so the focused row stays comfortably in view. Channels are tracked by their stable id, so a rebuild keeps the same channel focused.
 */
final class ChannelGrid extends View {
    interface Listener {
        void onFocus(PtvModel.Channel c);
        void onOpen(PtvModel.Channel c);
        void onMenu(PtvModel.Channel c);
        void onLeaveUp();
        /** Right at the end of a row; return true if the page took it (moves focus to the rail). */
        boolean onEdgeRight();
        void onFocusInput(Inputs.Entry e);
        void onOpenInput(Inputs.Entry e);
        void onMenuInput(Inputs.Entry e);
    }

    static final class Section {
        final String title;
        final List<PtvModel.Channel> channels = new ArrayList<>();
        final List<Inputs.Entry> inputs = new ArrayList<>();
        Section(String t) { title = t; }
    }

    private static final int COLS = 3;
    private static final float GAP = 14f, HEAD = 34f, SIDE = 64f;

    private static final class Cell {
        PtvModel.Channel ch;
        Inputs.Entry input;          // set instead of ch for an input card
        Bitmap art;
        float x, y, w, h;            // dp, content coordinates
        boolean favorite;
    }

    private final Listener listener;
    private final List<Cell> cells = new ArrayList<>();
    private final List<float[]> heads = new ArrayList<>();      // {y} per section header
    private final List<String> headText = new ArrayList<>();
    private int sel;
    private float contentH;
    private String empty = "";
    private boolean longPressed;
    private final TextPaint tp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), hp = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final ColorMatrixColorFilter dim;
    private float fx, fy, scroll;
    private final Spring sx = new Spring(520f, 0.82f, 0f, 0.05f, v -> { fx = v; invalidate(); });
    private final Spring sy = new Spring(520f, 0.82f, 0f, 0.05f, v -> { fy = v; invalidate(); });
    private final Spring ss = new Spring(300f, 0.9f, 0f, 0.5f, v -> { scroll = v; invalidate(); });
    private float focusAmt;
    private final Spring fs = new Spring(420f, 0.9f, 0f, 0.002f, v -> { focusAmt = v; invalidate(); });

    ChannelGrid(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        tp.setTypeface(Theme.geist);
        hp.setTypeface(Theme.geist);
        hp.setTextSize(dp(16));
        hp.setColor(Theme.TEXT2);
        if (Build.VERSION.SDK_INT >= 26) hp.setFontVariationSettings("'wght' 700");
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(dp(Theme.RING));
        ColorMatrix m = new ColorMatrix(new float[]{0.55f, 0, 0, 0, 0, 0, 0.55f, 0, 0, 0, 0, 0, 0.55f, 0, 0, 0, 0, 0, 1, 0});
        dim = new ColorMatrixColorFilter(m);
    }

    void setEmptyText(String t) { empty = t == null ? "" : t; invalidate(); }

    PtvModel.Channel current() { return sel < cells.size() ? cells.get(sel).ch : null; }

    Inputs.Entry currentInput() { return sel < cells.size() ? cells.get(sel).input : null; }

    /** Where the focused input card is on the screen, in pixels, or null when an input card is not focused or the grid is still moving to it. Used to place the live preview. */
    int[] focusedInputRect() {
        if (currentInput() == null) return null;
        Cell k = cells.get(sel);
        if (Math.abs(fx - k.x) > 0.6f || Math.abs(fy - k.y) > 0.6f || Math.abs(scroll - targetScroll()) > 0.6f) return null;
        int[] at = new int[2];
        getLocationOnScreen(at);
        float d = Theme.density;
        return new int[]{(int) (at[0] + k.x * d), (int) (at[1] + (k.y - scroll) * d), (int) (k.w * d), (int) (k.h * d)};
    }

    /** Rebuilds the grid, keeping focus on the same channel id when it is still present. */
    void setData(List<Section> sections, java.util.Set<String> favs) {
        String keep = current() != null ? current().id : currentInput() != null ? "input:" + currentInput().id : null;
        cells.clear();
        heads.clear();
        headText.clear();
        float cw = cardW(), ch = cw * 9f / 16f, y = 0;
        for (Section s : sections) {
            if (s.channels.isEmpty() && s.inputs.isEmpty()) continue;
            int count = s.channels.size() + s.inputs.size();
            heads.add(new float[]{y});
            headText.add(s.title + "   ·   " + count + (s.inputs.isEmpty() ? (count == 1 ? " channel" : " channels") : (count == 1 ? " input" : " inputs")));
            y += HEAD;
            int i = 0;
            for (Inputs.Entry in : s.inputs) {
                Cell cell = new Cell();
                cell.input = in;
                cell.art = InputArt.cached(in);
                cell.x = SIDE + (i % COLS) * (cw + GAP);
                cell.y = y + (i / COLS) * (ch + GAP);
                cell.w = cw;
                cell.h = ch;
                cells.add(cell);
                i++;
            }
            for (PtvModel.Channel c : s.channels) {
                Cell cell = new Cell();
                cell.ch = c;
                cell.favorite = favs.contains(c.id);
                cell.art = ChannelArt.cached(c.id, c.name, c.country, c.playable(), cell.favorite);
                cell.x = SIDE + (i % COLS) * (cw + GAP);
                cell.y = y + (i / COLS) * (ch + GAP);
                cell.w = cw;
                cell.h = ch;
                cells.add(cell);
                i++;
            }
            y += ((i + COLS - 1) / COLS) * (ch + GAP) + 6;
        }
        contentH = y;
        sel = 0;
        if (keep != null) for (int i = 0; i < cells.size(); i++) { Cell k = cells.get(i); String id = k.ch != null ? k.ch.id : "input:" + k.input.id; if (id.equals(keep)) { sel = i; break; } }
        if (!cells.isEmpty()) { sx.snap(cells.get(sel).x); sy.snap(cells.get(sel).y); ss.snap(targetScroll()); }
        else { sx.snap(0); sy.snap(0); ss.snap(0); }
        invalidate();
        if (hasFocus()) notifyFocus();
    }

    private float cardW() { return (getWidth() > 0 ? getWidth() / Theme.density - SIDE - 14f : 574f) / COLS - GAP * (COLS - 1) / COLS; }

    private float targetScroll() {
        if (cells.isEmpty()) return 0;
        Cell c = cells.get(sel);
        float viewH = getHeight() / Theme.density;
        float t = c.y - HEAD - (viewH - c.h) * 0.18f;                 // keep the focused row near the top, with its section header in view
        float max = Math.max(0, contentH - viewH + 10);
        return Math.max(0, Math.min(max, t));
    }

    @Override protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        fs.to(gained ? 1f : 0f);
        if (gained) notifyFocus();
    }

    private void notifyFocus() {
        if (current() != null) listener.onFocus(current());
        else if (currentInput() != null) listener.onFocusInput(currentInput());
    }

    private void moveTo(int i) {
        if (i < 0 || i >= cells.size() || i == sel) return;
        sel = i;
        sx.to(cells.get(i).x);
        sy.to(cells.get(i).y);
        ss.to(targetScroll());
        notifyFocus();
    }

    private int nearest(int dx, int dy) {
        Cell c = cells.get(sel);
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            Cell o = cells.get(i);
            float ddx = o.x - c.x, ddy = o.y - c.y;
            if (dx != 0 && (Math.abs(ddy) > 1f || ddx * dx <= 1f)) continue;
            if (dy != 0 && ddy * dy <= 1f) continue;
            float d = dy != 0 ? Math.abs(ddy) * 1000 + Math.abs(ddx) : Math.abs(ddx);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (cells.isEmpty()) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) { listener.onLeaveUp(); return true; }
            return super.onKeyDown(code, e);
        }
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: { int n = nearest(-1, 0); if (n >= 0) moveTo(n); return true; }
            case KeyEvent.KEYCODE_DPAD_RIGHT: { int n = nearest(1, 0); if (n >= 0) moveTo(n); else listener.onEdgeRight(); return true; }
            case KeyEvent.KEYCODE_DPAD_DOWN: { int n = nearest(0, 1); if (n >= 0) moveTo(n); return true; }
            case KeyEvent.KEYCODE_DPAD_UP: { int n = nearest(0, -1); if (n >= 0) moveTo(n); else listener.onLeaveUp(); return true; }
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) { longPressed = false; e.startTracking(); }
                return true;
            case KeyEvent.KEYCODE_MENU: menu(); return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            longPressed = true;
            menu();
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    private void menu() { if (current() != null) listener.onMenu(current()); else if (currentInput() != null) listener.onMenuInput(currentInput()); }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!longPressed) { if (current() != null) listener.onOpen(current()); else if (currentInput() != null) listener.onOpenInput(currentInput()); }
            longPressed = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override protected void onDraw(Canvas c) {
        float d = Theme.density;
        if (cells.isEmpty()) {
            tp.setTextSize(dp(18));
            tp.setColor(Theme.TEXT2);
            c.drawText(empty, dp(SIDE), dp(40), tp);
            return;
        }
        c.save();
        c.translate(0, -scroll * d);
        for (int i = 0; i < heads.size(); i++) c.drawText(headText.get(i), dp(SIDE), (heads.get(i)[0] + HEAD - 12) * d, hp);
        for (int i = 0; i < cells.size(); i++) {
            Cell k = cells.get(i);
            float top = (k.y - scroll) * d, bot = (k.y + k.h - scroll) * d;
            if (bot < -dp(20) || top > getHeight() + dp(20)) continue;
            rect.set(k.x * d, k.y * d, (k.x + k.w) * d, (k.y + k.h) * d);
            boolean ok = k.ch == null || k.ch.playable();
            bmp.setColorFilter(ok ? null : dim);
            bmp.setAlpha(ok ? 255 : 150);
            c.drawBitmap(k.art, null, rect, bmp);
        }
        bmp.setColorFilter(null);
        bmp.setAlpha(255);
        // the one ring, gliding between cards
        Cell k = cells.get(sel);
        float a = focusAmt;
        float gx = fx * d, gy = fy * d, gw = k.w * d, gh = k.h * d, pad = dp(Theme.RING + Theme.RING_GAP);
        ring.setColor(Theme.alpha(Theme.BLACK, (int) (255 * a)));
        ring.setStrokeWidth(dp(Theme.RING_GAP + 1));
        rect.set(gx - dp(Theme.RING_GAP) / 2f, gy - dp(Theme.RING_GAP) / 2f, gx + gw + dp(Theme.RING_GAP) / 2f, gy + gh + dp(Theme.RING_GAP) / 2f);
        c.drawRoundRect(rect, dp(Theme.RADIUS), dp(Theme.RADIUS), ring);
        ring.setColor(Theme.alpha(Theme.WHITE, (int) (255 * (0.35f + 0.65f * a))));
        ring.setStrokeWidth(dp(Theme.RING));
        rect.set(gx - pad + dp(Theme.RING) / 2f, gy - pad + dp(Theme.RING) / 2f, gx + gw + pad - dp(Theme.RING) / 2f, gy + gh + pad - dp(Theme.RING) / 2f);
        c.drawRoundRect(rect, dp(Theme.RADIUS + 3), dp(Theme.RADIUS + 3), ring);
        c.restore();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        PtvModel.Channel ch = current();
        Inputs.Entry in = currentInput();
        info.setContentDescription(in != null ? in.displayName() + ", " + in.stateText() : ch == null ? "Channels" : ch.name + (ch.playable() ? "" : ", not available"));
    }
}
