package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * Full-screen channel search. An on-screen keyboard made for a D-pad (letters, ë and ç, digits, Space, Delete, Clear) on the left and live
 * results on the right; a physical keyboard types directly. The whole catalogue is searched (Kosovo, Albania and anything else) on every
 * keystroke. Back leaves the results first, then closes the overlay and returns to where you were. Unavailable channels are listed and marked,
 * never hidden and never replaced by another channel.
 */
final class SearchOverlay extends View {
    interface Listener { void onOpen(SearchHit h); void onClose(); }

    private static final String[] ROWS = {"abcdefgh", "ijklmnop", "qrstuvwx", "yzëç" + "1234", "567890-'"};
    private static final float KEY = 46f, KGAP = 6f, RROW = 62f;

    private static final class Key {
        final String label;
        final int col, span;
        final int action;               // 0 char, 1 space, 2 delete, 3 clear
        Key(String l, int c, int s, int a) { label = l; col = c; span = s; action = a; }
    }

    private final Listener listener;
    private final List<SearchHit> all;
    private final List<SearchHit> results = new ArrayList<>();
    private final String hint, noun;
    private final List<Key[]> keys = new ArrayList<>();
    private final StringBuilder query = new StringBuilder();
    private final TextPaint big = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), mid = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), small = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF r = new RectF(), dst = new RectF();
    private boolean inResults;
    private int kr = 0, kc = 0;       // the keyboard starts on its first key
    private int rsel, rtop;
    private float shown;
    private final Spring in = new Spring(300f, 0.9f, 0f, 0.002f, v -> { shown = v; invalidate(); });
    private final java.util.Set<String> favorites;
    private Ambient ambient;
    private boolean closed;

    SearchOverlay(Context c, List<SearchHit> hits, java.util.Set<String> favs, String hint, String noun, Ambient amb, Listener l) {
        super(c);
        listener = l;
        all = hits;
        this.hint = hint;
        this.noun = noun;
        favorites = favs;
        ambient = amb;
        setFocusable(true);
        for (TextPaint t : new TextPaint[]{big, mid, small}) { t.setTypeface(Theme.geist); t.setColor(Theme.WHITE); }
        big.setTextSize(dp(26));
        mid.setTextSize(dp(18));
        small.setTextSize(dp(13));
        if (Build.VERSION.SDK_INT >= 26) { big.setFontVariationSettings("'wght' 700"); mid.setFontVariationSettings("'wght' 600"); small.setFontVariationSettings("'wght' 450"); }
        small.setColor(Theme.TEXT2);
        for (String row : ROWS) {
            Key[] k = new Key[row.length()];
            for (int i = 0; i < k.length; i++) k[i] = new Key(String.valueOf(row.charAt(i)), i, 1, 0);
            keys.add(k);
        }
        keys.add(new Key[]{new Key("Space", 0, 3, 1), new Key("Delete", 3, 2, 2), new Key("Clear", 5, 3, 3)});
        refilter();
    }

    void show(ViewGroup root) {
        root.addView(this, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        post(() -> { requestFocus(); in.snap(0f); in.to(1f); });
    }

    private void close() {
        if (closed) return;
        closed = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        listener.onClose();
    }

    // ------------------------------------------------------------ search

    private void refilter() {
        results.clear();
        results.addAll(SearchHit.search(query.toString(), all, 60));
        rsel = 0;
        rtop = 0;
        if (results.isEmpty()) inResults = false;
        invalidate();
    }

    private void type(String s) {
        if (query.length() < 40) query.append(s);
        refilter();
    }

    private void press(Key k) {
        if (k.action == 0) type(k.label);
        else if (k.action == 1) { if (query.length() > 0 && query.charAt(query.length() - 1) != ' ') type(" "); }
        else if (k.action == 2) { if (query.length() > 0) { query.deleteCharAt(query.length() - 1); refilter(); } }
        else { query.setLength(0); refilter(); }
    }

    // ------------------------------------------------------------ input

    private Key keyAt(int row, int col) {
        Key[] r = keys.get(row);
        for (Key k : r) if (col >= k.col && col < k.col + k.span) return k;
        return r[r.length - 1];
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK) {
            if (inResults) { inResults = false; invalidate(); } else close();
            return true;
        }
        if (inResults) return resultKey(code, e);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: kc = Math.max(0, keyAt(kr, kc).col - 1); kc = keyAt(kr, kc).col; invalidate(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: {
                Key k = keyAt(kr, kc);
                int next = k.col + k.span;
                if (next >= 8) { if (!results.isEmpty()) { inResults = true; invalidate(); } return true; }
                kc = next;
                invalidate();
                return true;
            }
            case KeyEvent.KEYCODE_DPAD_UP: if (kr > 0) { int c = keyAt(kr, kc).col; kr--; kc = keyAt(kr, c).col; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (kr < keys.size() - 1) { int c = keyAt(kr, kc).col; kr++; kc = keyAt(kr, c).col; invalidate(); } return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) press(keyAt(kr, kc));
                return true;
            case KeyEvent.KEYCODE_ENTER:
                if (e.getRepeatCount() == 0) { if (!results.isEmpty()) open(results.get(0)); else press(keyAt(kr, kc)); }
                return true;
            case KeyEvent.KEYCODE_DEL: press(keys.get(keys.size() - 1)[1]); return true;
            default: {
                int u = e.getUnicodeChar();                                   // physical keyboard
                if (u > 0 && (Character.isLetterOrDigit(u) || u == ' ' || u == '-' || u == '\'')) { type(new String(Character.toChars(u))); return true; }
                return super.onKeyDown(code, e);
            }
        }
    }

    private boolean resultKey(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: if (rsel > 0) { rsel--; scrollTo(); } return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: if (rsel < results.size() - 1) { rsel++; scrollTo(); } return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: inResults = false; invalidate(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0 && rsel < results.size()) open(results.get(rsel));
                return true;
            default: return true;
        }
    }

    private void scrollTo() {
        int rows = rows();
        if (rsel < rtop) rtop = rsel;
        if (rsel >= rtop + rows) rtop = rsel - rows + 1;
        invalidate();
    }

    private int rows() { return Math.max(1, (int) ((getHeight() - dp(150)) / dp(RROW))); }

    private void open(SearchHit h) {
        closed = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        listener.onOpen(h);
    }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float a = Math.max(0f, Math.min(1f, shown));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Theme.alpha(0xFF000000, (int) (252 * a)));
        c.drawRect(0, 0, w, h, p);
        c.save();
        c.translate(0, (1f - a) * dp(16));
        float left = dp(Theme.SAFE_SIDE), top = dp(Theme.SAFE_VERT + 6);

        // query field
        r.set(left, top, left + dp(8 * (KEY + KGAP) - KGAP), top + dp(56));
        Glass.draw(c, r, dp(28), ambient, 0, 0, a, false);
        String shownQ = query.length() == 0 ? hint : query.toString();
        big.setColor(query.length() == 0 ? Theme.TEXT3 : Theme.WHITE);
        CharSequence qt = TextUtils.ellipsize(shownQ, big, r.width() - dp(70), TextUtils.TruncateAt.START);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(2.2f));
        p.setColor(Theme.TEXT2);
        c.drawCircle(left + dp(30), top + dp(26), dp(8), p);
        c.drawLine(left + dp(36), top + dp(32), left + dp(43), top + dp(39), p);
        c.drawText(qt, 0, qt.length(), left + dp(58), top + dp(37), big);

        // keyboard
        float ky = top + dp(56 + 18);
        for (int row = 0; row < keys.size(); row++) {
            for (Key k : keys.get(row)) {
                float kx = left + k.col * dp(KEY + KGAP);
                float kw = k.span * dp(KEY) + (k.span - 1) * dp(KGAP);
                boolean foc = !inResults && row == kr && k == keyAt(kr, kc);
                r.set(kx, ky, kx + kw, ky + dp(KEY));
                p.setStyle(Paint.Style.FILL);
                p.setColor(foc ? Theme.WHITE : Theme.S2);
                c.drawRoundRect(r, dp(12), dp(12), p);
                mid.setColor(foc ? Theme.BLACK : Theme.WHITE);
                float tw = mid.measureText(k.label);
                c.drawText(k.label, kx + (kw - tw) / 2f, ky + dp(KEY) / 2f + dp(6), mid);
            }
            ky += dp(KEY + KGAP);
        }
        small.setColor(Theme.TEXT3);
        c.drawText("Back closes   physical keyboard works too", left, ky + dp(14), small);

        // results
        float rx = left + dp(8 * (KEY + KGAP)) + dp(20), rw = w - rx - dp(Theme.SAFE_SIDE);
        small.setColor(Theme.TEXT2);
        String head = query.toString().trim().isEmpty() ? all.size() + " " + noun + " to search" : results.isEmpty() ? "Nothing matches “" + query.toString().trim() + "”"
                : results.size() + (results.size() == 1 ? " result" : " results");
        c.drawText(head, rx, top + dp(20), small);
        float ry = top + dp(36);
        int rowsVisible = rows();
        for (int k = 0; k < rowsVisible && rtop + k < results.size(); k++) {
            SearchHit hit = results.get(rtop + k);
            PtvModel.Channel ch = hit.kind == SearchHit.CHANNEL ? (PtvModel.Channel) hit.payload : null;
            boolean on = inResults && rtop + k == rsel;
            float y = ry + k * dp(RROW);
            boolean ok = ch == null || ch.playable();
            if (on) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.WHITE);
                r.set(rx, y, rx + rw, y + dp(RROW - 6));
                c.drawRoundRect(r, dp(14), dp(14), p);
            } else if (rtop + k == rsel && !inResults) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.WHITE, 22));
                r.set(rx, y, rx + rw, y + dp(RROW - 6));
                c.drawRoundRect(r, dp(14), dp(14), p);
            }
            dst.set(rx + dp(8), y + dp(6), rx + dp(8) + dp(72), y + dp(6) + dp(40.5f));
            if (ch != null) {
                Bitmap art = ChannelArt.thumb(ch.id, ch.name, ch.country, ok, favorites.contains(ch.id));
                bmp.setAlpha(ok ? 255 : 120);
                c.drawBitmap(art, null, dst, bmp);
            } else if (hit.thumb instanceof Bitmap) {                      // an app: its own icon, square, centred in the slot
                float s = dp(40.5f);
                dst.set(dst.centerX() - s / 2f, dst.top, dst.centerX() + s / 2f, dst.bottom);
                bmp.setAlpha(255);
                c.drawBitmap((Bitmap) hit.thumb, null, dst, bmp);
            } else {                                                      // film, input, setting: a quiet tile that names the kind
                p.setStyle(Paint.Style.FILL);
                p.setColor(on ? 0x22000000 : Theme.S2);
                c.drawRoundRect(dst, dp(8), dp(8), p);
                small.setColor(on ? 0xFF3A3A3A : Theme.TEXT2);
                String kn = SearchHit.kindName(hit.kind).toUpperCase(java.util.Locale.ROOT);
                c.drawText(kn, dst.centerX() - small.measureText(kn) / 2f, dst.centerY() + dp(4), small);
            }
            mid.setColor(on ? Theme.BLACK : ok ? Theme.WHITE : Theme.TEXT2);
            CharSequence nm = TextUtils.ellipsize(hit.title, mid, rw - dp(100), TextUtils.TruncateAt.END);
            c.drawText(nm, 0, nm.length(), rx + dp(92), y + dp(24), mid);
            small.setColor(on ? 0xFF3A3A3A : Theme.TEXT2);
            String subline = hit.sub + (ok ? "" : " · not available here");
            CharSequence sl = TextUtils.ellipsize(subline, small, rw - dp(100), TextUtils.TruncateAt.END);
            c.drawText(sl, 0, sl.length(), rx + dp(92), y + dp(42), small);
        }
        c.restore();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setContentDescription("Search, " + (query.length() == 0 ? "empty" : query.toString()) + ", " + results.size() + " results");
    }
}
