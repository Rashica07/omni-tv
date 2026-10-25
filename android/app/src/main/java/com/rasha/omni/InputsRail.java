package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
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
 * The Inputs rail on the right of the TV page: HDMI ports and other inputs of the TV, visually separate from channels. Names and states come
 * from the TV through the public input API; a device type is marked "set by you", "from your TV's name" or not claimed at all. One focusable
 * view: Up/Down choose, Left goes back to the channels, OK asks the system to show the input, hold OK for options.
 */
final class InputsRail extends View {
    interface Listener { void onOpen(Inputs.Entry e); void onMenu(Inputs.Entry e); void onLeft(); void onUp(); }

    private static final float HEAD = 40f, ROW = 52f, PAD = 10f;

    private final Listener listener;
    private final TextPaint head = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), name = new TextPaint(TextPaint.ANTI_ALIAS_FLAG), sub = new TextPaint(TextPaint.ANTI_ALIAS_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private List<Inputs.Entry> entries = new ArrayList<>();
    private int sel;
    private int top;                       // first visible row
    private float filled;
    private boolean longPressed;
    private final Spring fillSpring = new Spring(420f, 0.9f, 0f, 0.002f, v -> { filled = v; invalidate(); });
    private final Spring selSpring = new Spring(420f, 0.88f, 0f, 0.01f, v -> invalidate());
    private Ambient ambient;
    private String note = "";

    InputsRail(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        for (TextPaint t : new TextPaint[]{head, name, sub}) t.setTypeface(Theme.geist);
        head.setTextSize(dp(15));
        name.setTextSize(dp(16));
        sub.setTextSize(dp(12));
        if (Build.VERSION.SDK_INT >= 26) {
            head.setFontVariationSettings("'wght' 700");
            name.setFontVariationSettings("'wght' 600");
            sub.setFontVariationSettings("'wght' 450");
        }
        head.setColor(Theme.TEXT2);
        sub.setColor(Theme.TEXT2);
    }

    void setAmbient(Ambient a) { ambient = a; }

    void setEntries(List<Inputs.Entry> e, String footnote) {
        String keep = sel < entries.size() ? entries.get(sel).id : null;
        entries = e;
        note = footnote == null ? "" : footnote;
        sel = 0;
        for (int i = 0; i < e.size(); i++) if (e.get(i).id.equals(keep)) sel = i;     // keep the same input selected across refreshes
        selSpring.snap(sel);
        invalidate();
    }

    Inputs.Entry current() { return sel < entries.size() ? entries.get(sel) : null; }

    private int visibleRows() { return Math.max(1, (int) ((getHeight() - dp(HEAD) - dp(PAD) - (note.isEmpty() ? 0 : dp(34))) / dp(ROW))); }

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        fillSpring.to(gained ? 1f : 0f);
    }

    private void move(int d) {
        int n = sel + d;
        if (n < 0 || n >= entries.size()) return;
        sel = n;
        int rows = visibleRows();
        if (sel < top) top = sel;
        if (sel >= top + rows) top = sel - rows + 1;
        selSpring.to(sel);
        invalidate();
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP: if (sel == 0) listener.onUp(); else move(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: move(1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: listener.onLeft(); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return true;
            case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) { longPressed = false; e.startTracking(); }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (current() != null) listener.onMenu(current());
                return true;
            default: return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            longPressed = true;
            if (current() != null) listener.onMenu(current());
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (!longPressed && current() != null) listener.onOpen(current());
            longPressed = false;
            return true;
        }
        return super.onKeyUp(code, e);
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        Glass.draw(c, r, dp(22), ambient, getLeft(), getTop(), 1f, false);
        c.drawText("INPUTS", dp(18), dp(27), head);
        if (entries.isEmpty()) {
            sub.setColor(Theme.TEXT2);
            c.drawText("No inputs reported by this TV", dp(18), dp(HEAD) + dp(30), sub);
            return;
        }
        int rows = visibleRows();
        float y0 = dp(HEAD);
        for (int k = 0; k < rows && top + k < entries.size(); k++) {
            int i = top + k;
            Inputs.Entry e = entries.get(i);
            float y = y0 + k * dp(ROW);
            boolean on = i == sel;
            float al = e.state == android.media.tv.TvInputManager.INPUT_STATE_DISCONNECTED ? 0.55f : 1f;
            if (on) {                                         // white pill while the rail has focus, a faint one while it does not
                p.setStyle(Paint.Style.FILL);
                p.setColor(Theme.alpha(Theme.WHITE, (int) (255 * (0.14f * (1f - filled) + filled))));
                r.set(dp(PAD), y + dp(2), w - dp(PAD), y + dp(ROW) - dp(2));
                c.drawRoundRect(r, dp(14), dp(14), p);
            }
            int fg = on ? Theme.lerp(Theme.WHITE, Theme.BLACK, filled) : Theme.WHITE;
            int fg2 = on ? Theme.lerp(Theme.TEXT2, 0xFF3A3A3A, filled) : Theme.TEXT2;
            DeviceArt.icon(c, e.type, dp(PAD) + dp(26), y + dp(ROW) / 2f, dp(24), Theme.alpha(fg, (int) (255 * al)));
            float tx = dp(PAD) + dp(48), maxw = w - tx - dp(34);
            name.setColor(Theme.alpha(fg, (int) (255 * al)));
            CharSequence n = TextUtils.ellipsize(e.displayName(), name, maxw, TextUtils.TruncateAt.END);
            c.drawText(n, 0, n.length(), tx, y + dp(22), name);
            sub.setColor(Theme.alpha(fg2, (int) (255 * al)));
            String how = e.typeFromUser ? "set by you" : e.cecName != null ? "from TV" : "";
            CharSequence s = TextUtils.ellipsize(e.stateText() + (how.isEmpty() ? "" : " · " + how), sub, maxw, TextUtils.TruncateAt.END);
            c.drawText(s, 0, s.length(), tx, y + dp(39), sub);
            // connection mark: filled = signal, ring with dot = connected/standby, empty ring = nothing connected
            float dx = w - dp(PAD) - dp(16), dy = y + dp(ROW) / 2f;
            p.setStyle(e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED ? Paint.Style.FILL : Paint.Style.STROKE);
            p.setStrokeWidth(dp(1.8f));
            p.setColor(Theme.alpha(fg, (int) (255 * al)));
            c.drawCircle(dx, dy, dp(5), p);
            if (e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED_STANDBY) {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(dx, dy, dp(1.8f), p);
            }
        }
        if (entries.size() > rows) {                          // scroll hint
            sub.setColor(Theme.TEXT3);
            c.drawText((top + 1) + "-" + Math.min(entries.size(), top + rows) + " of " + entries.size(), dp(18), h - dp(note.isEmpty() ? 12 : 38), sub);
        }
        if (!note.isEmpty()) {
            sub.setColor(Theme.TEXT3);
            CharSequence nt = TextUtils.ellipsize(note, sub, w - dp(36), TextUtils.TruncateAt.END);
            c.drawText(nt, 0, nt.length(), dp(18), h - dp(14), sub);
        }
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        Inputs.Entry e = current();
        info.setContentDescription(e == null ? "Inputs" : "Input " + e.displayName() + ", " + e.stateText());
    }
}
