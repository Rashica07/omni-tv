package com.rasha.omni;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * The assistant: the screen dims, a wide liquid-glass sheet rises at the bottom, a listening wave answers your voice and your words appear as you speak.
 * TV things (apps, inputs, volume, films, weather, search, Home, Settings) are done by OMNI; anything else is handed, with your words, to the TV's own
 * Google assistant / Gemini, whose full interface answers. Without a speech service or microphone permission it opens straight into typing.
 * Left/Right move between the suggestion chips, OK runs one, Back closes, the mic key (or OK on "Listen again") listens again.
 */
final class Assistant extends FrameLayout {
    interface Actions {
        List<String> apps();
        List<String> inputs();
        boolean canAsk();
        boolean micAllowed();
        void run(Intents.Action a);
        void closed();
    }

    private static final int IDLE = 0, LISTENING = 1, THINKING = 2, RESULT = 3, ERROR = 4, TYPING = 5;
    private static final float SHEET_H = 188f, SIDE = 56f, BOTTOM = 34f;

    private final Actions actions;
    private final Ambient ambient;
    private final Sheet sheet;
    private final EditText field;
    private final Spring in;
    private SpeechRecognizer rec;
    private int state = IDLE;
    private String heard = "", partial = "";
    private final List<Intents.Action> chips = new ArrayList<>();
    private int focus;
    private float rms;                          // 0..1, smoothed microphone level
    private boolean closing, gone;
    private float shown, lift;
    private final Spring liftSpring = new Spring(240f, 0.86f, 0f, 0.002f, v -> { lift = v; place(); });

    /** Typing: the on-screen keyboard takes the bottom half, so the sheet rises to the top. */
    private void place() {
        float up = lift * Math.max(0, getHeight() - dp(SHEET_H) - dp(BOTTOM) - dp(28));
        sheet.setTranslationY(dp(SHEET_H + 60) * (1f - shown) - up);
        field.setTranslationY(-up);
    }

    Assistant(Context c, Ambient amb, Actions a) {
        super(c);
        actions = a;
        ambient = amb;
        setClickable(true);
        setWillNotDraw(false);
        sheet = new Sheet(c);
        LayoutParams sp = new LayoutParams(-1, dp(SHEET_H), Gravity.BOTTOM);
        sp.leftMargin = dp(SIDE); sp.rightMargin = dp(SIDE); sp.bottomMargin = dp(BOTTOM);
        addView(sheet, sp);
        field = new EditText(c);
        field.setSingleLine(true);
        field.setTextColor(Theme.WHITE);
        field.setHintTextColor(Theme.TEXT3);
        field.setHint("Type what you want: open YouTube, PlayStation, weather…");
        field.setTextSize(20);
        field.setTypeface(Theme.geist);
        field.setBackgroundColor(0x00000000);
        field.setImeOptions(EditorInfo.IME_ACTION_GO);
        field.setVisibility(View.GONE);
        field.setOnEditorActionListener((v, id, ev) -> { String t = field.getText().toString().trim(); if (!t.isEmpty()) understood(t); return true; });
        LayoutParams fp = new LayoutParams(-1, dp(56), Gravity.BOTTOM);
        fp.leftMargin = dp(SIDE + 100); fp.rightMargin = dp(SIDE + 30); fp.bottomMargin = dp(BOTTOM + 104);
        addView(field, fp);
        sheet.setTranslationY(dp(SHEET_H + 60));
        in = new Spring(260f, 0.84f, 0f, 0.002f, v -> {
            shown = Math.max(0f, Math.min(1f, v));
            place();
            invalidate();
            if (closing && v < 0.01f) finish();
        });
    }

    void show(ViewGroup root) {
        root.addView(this, new LayoutParams(-1, -1));
        post(() -> {
            in.snap(0f); in.to(1f);
            sheet.requestFocus();
            if (actions.micAllowed() && SpeechRecognizer.isRecognitionAvailable(getContext())) listen(); else typing();
        });
    }

    // ------------------------------------------------------------ listening

    private void listen() {
        stopRecognizer();
        try {
            rec = SpeechRecognizer.createSpeechRecognizer(getContext());
            rec.setRecognitionListener(listener);
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getContext().getPackageName());
            heard = ""; partial = ""; chips.clear();
            setState(LISTENING);
            rec.startListening(i);
            OmniLog.state("assistant listen");
        } catch (RuntimeException e) {
            OmniLog.state("assistant no recognizer " + e.getClass().getSimpleName());
            typing();
        }
    }

    private final RecognitionListener listener = new RecognitionListener() {
        @Override public void onReadyForSpeech(Bundle b) { }
        @Override public void onBeginningOfSpeech() { }
        @Override public void onRmsChanged(float db) { float v = Math.max(0f, Math.min(1f, (db + 2f) / 12f)); rms = rms * 0.6f + v * 0.4f; sheet.invalidate(); }
        @Override public void onBufferReceived(byte[] b) { }
        @Override public void onEndOfSpeech() { setState(THINKING); }
        @Override public void onError(int code) {
            OmniLog.state("assistant error=" + code);
            if (state == RESULT || closing) return;
            heard = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? "I didn't catch that." : code == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ? "The microphone is not allowed." : "Listening isn't available right now.";
            chips.clear();
            chips.add(new Intents.Action(-1, null, 0, "Listen again"));
            chips.add(new Intents.Action(-2, null, 0, "Type instead"));
            focus = 0;
            setState(ERROR);
        }
        @Override public void onResults(Bundle b) { List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); String t = r == null || r.isEmpty() ? partial : r.get(0); if (t == null || t.trim().isEmpty()) onError(SpeechRecognizer.ERROR_NO_MATCH); else understood(t); }
        @Override public void onPartialResults(Bundle b) { List<String> r = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if (r != null && !r.isEmpty() && !r.get(0).isEmpty()) { partial = r.get(0); sheet.invalidate(); } }
        @Override public void onEvent(int t, Bundle b) { }
    };

    private void typing() {
        stopRecognizer();
        setState(TYPING);
        chips.clear();
        liftSpring.to(1f);
        field.setVisibility(View.VISIBLE);
        field.setText("");
        field.requestFocus();
    }

    /** Words are in: rank the actions. A single clear TV command runs at once (with its chip still shown for a moment); otherwise you choose. */
    private void understood(String text) {
        heard = text.trim();
        partial = "";
        field.setVisibility(View.GONE);
        hideKeyboard();
        liftSpring.to(0f);
        stopRecognizer();
        chips.clear();
        for (Intents.Action a : Intents.parse(heard, actions.apps(), actions.inputs())) if (a.kind != Intents.ASK || actions.canAsk()) chips.add(a);
        chips.add(new Intents.Action(-1, null, 0, "Listen again"));
        focus = 0;
        setState(RESULT);
        sheet.requestFocus();
        OmniLog.state("assistant heard chips=" + chips.size() + " first=" + (chips.isEmpty() ? "-" : chips.get(0).kind));
        Intents.Action first = chips.get(0);
        boolean direct = first.kind >= Intents.OPEN_APP && first.kind <= Intents.CONTROL && first.kind != Intents.SEARCH && first.kind != Intents.PLAY
                && (chips.size() <= 2 || first.kind == Intents.INPUT || first.kind == Intents.OPEN_APP && isOpenCommand(heard));
        if (direct) postDelayed(() -> { if (!closing && state == RESULT && focus == 0) run(first); }, 650);
    }

    private static boolean isOpenCommand(String t) { String n = Intents.norm(t); return n.startsWith("open ") || n.startsWith("apri ") || n.startsWith("hap ") || n.startsWith("launch ") || n.startsWith("switch to "); }

    private void run(Intents.Action a) {
        if (a.kind == -1) { listen(); return; }
        if (a.kind == -2) { typing(); return; }
        OmniLog.state("assistant run kind=" + a.kind);
        boolean stays = a.kind == Intents.VOLUME_UP || a.kind == Intents.VOLUME_DOWN || a.kind == Intents.MUTE || a.kind == Intents.VOLUME_SET;
        actions.run(a);
        if (!stays) dismiss();
        else { heard = a.label; sheet.invalidate(); }
    }

    private void setState(int s) { state = s; sheet.invalidate(); if (s == LISTENING) sheet.postInvalidateOnAnimation(); }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager im = (android.view.inputmethod.InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(field.getWindowToken(), 0);
    }

    private void stopRecognizer() {
        if (rec != null) { try { rec.cancel(); rec.destroy(); } catch (RuntimeException ignored) { } rec = null; }
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        stopRecognizer();
        hideKeyboard();
        field.setVisibility(View.GONE);
        in.to(0f);
    }

    private void finish() {
        if (gone) return;
        gone = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        actions.closed();
    }

    /** The dim behind the sheet: darker toward the bottom, where the sheet is. One gradient rect, only while shown. */
    private final Paint dim = new Paint();
    private LinearGradient dimShader;

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        dimShader = new LinearGradient(0, 0, 0, h, new int[]{0x55000000, 0x99000000, 0xDD000000}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas c) {
        if (dimShader == null) return;
        dim.setShader(dimShader);
        dim.setAlpha((int) (255 * shown));
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && !closing) {
            int k = e.getKeyCode();
            if (k == KeyEvent.KEYCODE_BACK) { if (state == TYPING && field.hasFocus() && field.getText().length() > 0) return super.dispatchKeyEvent(e); dismiss(); return true; }
            if (isMicKey(k) && e.getRepeatCount() == 0) { listen(); return true; }
            if (state == TYPING && field.hasFocus()) return super.dispatchKeyEvent(e);
            if (!sheet.hasFocus()) { sheet.requestFocus(); return true; }
        }
        return super.dispatchKeyEvent(e);
    }

    static boolean isMicKey(int k) { return k == KeyEvent.KEYCODE_SEARCH || k == KeyEvent.KEYCODE_VOICE_ASSIST || k == KeyEvent.KEYCODE_ASSIST; }

    /** Developer check: behave as if these words had been heard (debug entry only). */
    void debugSay(String words) { stopRecognizer(); understood(words); }

    // ------------------------------------------------------------ the sheet

    private final class Sheet extends View {
        private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG), small = new TextPaint(Paint.ANTI_ALIAS_FLAG), chip = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final float[] bars = new float[5];
        private float hx, hw, hy, hh;
        private final Spring sx = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hx = v; invalidate(); });
        private final Spring sw = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hw = v; invalidate(); });
        private final RectF[] chipRects = new RectF[8];

        Sheet(Context c) {
            super(c);
            setFocusable(true);
            for (TextPaint t : new TextPaint[]{big, small, chip}) t.setTypeface(Theme.geist);
            big.setTextSize(dp(26)); big.setColor(Theme.WHITE);
            small.setTextSize(dp(13)); small.setColor(Theme.TEXT3);
            chip.setTextSize(dp(15)); chip.setColor(Theme.WHITE); chip.setTextAlign(Paint.Align.CENTER);
            if (Build.VERSION.SDK_INT >= 26) { big.setFontVariationSettings("'wght' 650"); small.setFontVariationSettings("'wght' 600"); chip.setFontVariationSettings("'wght' 600"); }
            for (int i = 0; i < chipRects.length; i++) chipRects[i] = new RectF();
        }

        @Override protected void onDraw(Canvas c) {
            r.set(0, 0, getWidth(), getHeight());
            Glass.draw(c, r, dp(34), ambient, getLeft(), getTop() + getTranslationY(), 1f, true);
            float wx = dp(56), wy = dp(62);
            drawWave(c, wx, wy);
            String label = state == LISTENING ? "Listening…" : state == THINKING ? "One moment…" : state == TYPING ? "Typing" : state == ERROR ? "" : "You said";
            c.drawText(label.toUpperCase(java.util.Locale.ROOT), dp(112), dp(40), small);
            String line = state == LISTENING ? (partial.isEmpty() ? "Say something like “open YouTube” or “PlayStation”" : partial) : state == THINKING ? partial : state == TYPING ? "" : heard;
            big.setColor(state == LISTENING && partial.isEmpty() ? Theme.TEXT3 : Theme.WHITE);
            CharSequence shown = TextUtils.ellipsize(line, big, getWidth() - dp(112 + 36), TextUtils.TruncateAt.START);
            c.drawText(shown, 0, shown.length(), dp(112), dp(76), big);
            drawChips(c);
            if (state == LISTENING) postInvalidateOnAnimation();
        }

        /** Five rounded bars that follow the microphone level; resting, they breathe slowly. Nothing is allocated per frame. */
        private void drawWave(Canvas c, float cx, float cy) {
            long t = android.os.SystemClock.uptimeMillis();
            p.setColor(Theme.WHITE);
            for (int i = 0; i < bars.length; i++) {
                float target;
                if (state == LISTENING) target = 0.25f + rms * (0.55f + 0.45f * (float) Math.abs(Math.sin(t / 140.0 + i * 1.3)));
                else if (state == THINKING) target = 0.25f + 0.25f * (float) Math.abs(Math.sin(t / 220.0 + i * 0.9));
                else target = 0.22f;
                bars[i] += (target - bars[i]) * 0.35f;
                float h = dp(46) * bars[i], x = cx - dp(24) + i * dp(12);
                r.set(x - dp(3.5f), cy - h / 2f, x + dp(3.5f), cy + h / 2f);
                c.drawRoundRect(r, dp(3.5f), dp(3.5f), p);
            }
            if (state == THINKING) postInvalidateOnAnimation();
        }

        private void drawChips(Canvas c) {
            if (chips.isEmpty()) return;
            float x = dp(112), y = getHeight() - dp(30) - dp(46);
            int n = Math.min(chips.size(), chipRects.length);
            for (int i = 0; i < n; i++) {
                String l = chips.get(i).label;
                float w = Math.min(getWidth() - x - dp(30), chip.measureText(l) + dp(36));
                if (w < dp(80)) { n = i; break; }
                chipRects[i].set(x, y, x + w, y + dp(46));
                x += w + dp(10);
            }
            focus = Math.max(0, Math.min(n - 1, focus));
            for (int i = 0; i < n; i++) {
                RectF cr = chipRects[i];
                p.setColor(0x26FFFFFF);
                c.drawRoundRect(cr, cr.height() / 2f, cr.height() / 2f, p);
                chip.setColor(Theme.WHITE);
                CharSequence l = TextUtils.ellipsize(chips.get(i).label, chip, cr.width() - dp(24), TextUtils.TruncateAt.END);
                c.drawText(l, 0, l.length(), cr.centerX(), cr.centerY() + dp(5), chip);
            }
            if (n > 0 && hasFocus()) {
                RectF f = chipRects[focus];
                if (hw == 0) { sx.snap(f.left); sw.snap(f.width()); } else { sx.to(f.left); sw.to(f.width()); }
                hy = f.top; hh = f.height();
                p.setColor(Theme.WHITE);
                r.set(hx, hy, hx + hw, hy + hh);
                c.drawRoundRect(r, hh / 2f, hh / 2f, p);
                c.save(); c.clipRect(r);
                chip.setColor(0xFF000000);
                CharSequence l = TextUtils.ellipsize(chips.get(focus).label, chip, f.width() - dp(24), TextUtils.TruncateAt.END);
                c.drawText(l, 0, l.length(), f.centerX(), f.centerY() + dp(5), chip);
                c.restore();
            }
            visibleChips = n;
        }

        private int visibleChips;

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (visibleChips > 0) { focus = Math.max(0, Math.min(visibleChips - 1, focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1))); invalidate(); }
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: return true;
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() == 0) { if (!chips.isEmpty() && focus < chips.size()) run(chips.get(focus)); else if (state != LISTENING) listen(); }
                    return true;
                default: return super.onKeyDown(code, e);
            }
        }

        @Override protected void onFocusChanged(boolean g, int d, android.graphics.Rect p) { super.onFocusChanged(g, d, p); invalidate(); }
    }
}
