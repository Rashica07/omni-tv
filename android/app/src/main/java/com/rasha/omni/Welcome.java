package com.rasha.omni;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * First-run welcome and setup: one liquid-glass card over the wallpaper ("WELCOME TO OMNI UI", then Home, remote, server, weather, permissions, done).
 * The launcher's pages are hidden while it shows, so the glass sits on the photograph alone. One custom-drawn view, like the Control Center: the focused
 * button is a white pill that glides between buttons, text cross-fades between steps. Every step can be skipped; it can be run again from Settings.
 */
final class Welcome extends FrameLayout {
    interface Actions {
        boolean isHome();
        void chooseHome();
        String server();
        void findServer();
        void useServer(String hostPort);
        String weather();
        void pickWeather();
        boolean listings();
        void askListings();
        boolean overlay();
        void askOverlay();
        boolean mic();
        void askMic();
        boolean sessions();
        void askSessions();
        void finished(boolean completed, int step);
        boolean simple();
        void setSimple(boolean simple);
        boolean tuner();
        /** One line about the antenna: how many channels the TV has and from which countries, or that there are none yet. */
        String antenna();
        void scanAntenna();
        /** The language was changed on the Language step: the launcher restarts itself and comes back to the next step. */
        void languageChanged(int nextStep);
        /** Ends the setup and opens the quick-access app picker / the remote-button list. */
        void pickApps();
        void pickButtons();
    }

    private static final float CARD_W = 620f, CARD_H = 380f, PAD = 36f, BTN_H = 48f, BTN_GAP = 12f;

    private final Actions actions;
    private final Ambient ambient;
    private final Card card;
    private final View scrim;
    private final Spring in;
    private boolean closing, gone;
    private int step;
    // live state shown on the cards
    private String scanText = "";
    private List<String> found = new ArrayList<>();
    private boolean scanning;
    private String lastKey = "";
    private boolean infoSeen;

    Welcome(Context c, Ambient amb, int startStep, Actions a) {
        super(c);
        actions = a;
        ambient = amb;
        step = SetupFlow.resume(startStep);
        setClickable(true);
        scrim = new View(c);
        scrim.setBackgroundColor(0x33000000);
        scrim.setAlpha(0f);
        addView(scrim, new LayoutParams(-1, -1));
        card = new Card(c);
        addView(card, new LayoutParams(dp(CARD_W), dp(CARD_H), Gravity.CENTER));
        card.setAlpha(0f);
        in = new Spring(220f, 0.82f, 0f, 0.002f, v -> {
            float k = Math.max(0f, Math.min(1f, v));
            card.setAlpha(k);
            card.setTranslationY(dp(28) * (1f - v));
            card.setScaleX(0.96f + 0.04f * k);
            card.setScaleY(0.96f + 0.04f * k);
            scrim.setAlpha(k);
            if (closing && v < 0.01f) finish();
        });
    }

    int step() { return step; }

    void show(ViewGroup root) {
        root.addView(this, new LayoutParams(-1, -1));
        post(() -> { in.snap(0f); in.to(1f); card.rebuild(true); ensureFocus(0); });
        OmniLog.state("welcome step=" + step);
    }

    /** Re-reads Home/permissions/server/weather state (after returning from a system screen). */
    void refresh() { card.rebuild(false); }

    void setScan(boolean busy, List<String> hosts) {
        scanning = busy;
        if (hosts != null) found = hosts;
        scanText = busy ? "Looking on your network…" : found.isEmpty() ? "No OMNI server answered on this network." : "Found " + (found.size() == 1 ? "a server at " + found.get(0) : found.size() + " servers");
        card.rebuild(false);
    }

    private void ensureFocus(final int attempt) {
        if (closing || !isAttachedToWindow()) return;
        if (card.hasFocus()) { OmniLog.state("welcome focus ok attempt=" + attempt); return; }
        card.requestFocus();
        if (attempt < 6) postDelayed(() -> ensureFocus(attempt + 1), attempt < 2 ? 60 : 160);
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (!closing && e.getAction() == KeyEvent.ACTION_DOWN && !card.hasFocus()) { card.requestFocus(); return true; }
        return super.dispatchKeyEvent(e);
    }

    void dismiss(boolean completed) {
        if (closing) return;
        closing = true;
        actions.finished(completed, step);
        in.to(0f);
    }

    private void finish() {
        if (gone) return;
        gone = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    private void go(int to) {
        if (to == step) return;
        step = to;
        OmniLog.state("welcome step=" + step);
        card.rebuild(true);
    }

    // ------------------------------------------------------------------------------------------------ the card

    private static final class Btn { String id, label; boolean done; final RectF r = new RectF(); }

    final class Card extends View {
        private final TextPaint hero = new TextPaint(Paint.ANTI_ALIAS_FLAG), title = new TextPaint(Paint.ANTI_ALIAS_FLAG), body = new TextPaint(Paint.ANTI_ALIAS_FLAG),
                status = new TextPaint(Paint.ANTI_ALIAS_FLAG), btn = new TextPaint(Paint.ANTI_ALIAS_FLAG), hint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG), ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private final List<Btn> buttons = new ArrayList<>();
        private StaticLayout bodyLayout;
        private String heading = "", statusText = "";
        private boolean statusOk;
        private int focus;
        private boolean simpleNow, tunerNow;          // read once per step: onDraw must not ask the system each frame
        private float hx, hy, hw, hh;
        private final Spring sx = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hx = v; invalidate(); });
        private final Spring sy = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hy = v; invalidate(); });
        private final Spring sw = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hw = v; invalidate(); });
        private final Spring sh = new Spring(520f, 0.84f, 0f, 0.1f, v -> { hh = v; invalidate(); });
        private float content = 1f;
        private final Spring fade = new Spring(260f, 0.95f, 1f, 0.002f, v -> { content = v; invalidate(); });

        Card(Context c) {
            super(c);
            setFocusable(true);
            for (TextPaint t : new TextPaint[]{hero, title, body, status, btn, hint}) t.setTypeface(Theme.geist);
            hero.setTextSize(dp(40)); hero.setColor(Theme.WHITE); hero.setTextAlign(Paint.Align.CENTER); hero.setLetterSpacing(0.06f);
            title.setTextSize(dp(28)); title.setColor(Theme.WHITE);
            body.setTextSize(dp(15)); body.setColor(Theme.TEXT2);
            status.setTextSize(dp(15)); status.setColor(Theme.WHITE);
            btn.setTextSize(dp(15)); btn.setColor(Theme.WHITE); btn.setTextAlign(Paint.Align.CENTER);
            hint.setTextSize(dp(12)); hint.setColor(Theme.TEXT3);
            if (Build.VERSION.SDK_INT >= 26) {                       // weights are set once here, never per frame (project rule)
                hero.setFontVariationSettings("'wght' 800"); title.setFontVariationSettings("'wght' 750"); body.setFontVariationSettings("'wght' 450");
                status.setFontVariationSettings("'wght' 600"); btn.setFontVariationSettings("'wght' 650"); hint.setFontVariationSettings("'wght' 500");
            }
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeCap(Paint.Cap.ROUND);
        }

        /** Builds the step's text and buttons from the current state. animate = a new step (text fades in, focus goes to the first button). */
        void rebuild(boolean animate) {
            String keep = !animate && focus < buttons.size() ? buttons.get(focus).id : null;
            buttons.clear();
            simpleNow = actions.simple(); tunerNow = actions.tuner();
            heading = Tr.t(SetupFlow.TITLES[step]);
            String text;
            statusText = ""; statusOk = false;
            switch (step) {
                case SetupFlow.LANGUAGE:
                    text = "Choose your language.";
                    for (int i = 0; i < Tr.NAMES.length; i++) { Btn b = new Btn(); b.id = "lang:" + i; b.label = Tr.NAMES[i] + (i == Tr.lang ? "  ✓" : ""); b.done = i == Tr.lang; buttons.add(b); }
                    add("next", "Next");
                    break;
                case SetupFlow.MODE:
                    text = "Simple: no server, just your TV, your channels and your apps. Full: everything, including a home server for films and the Server screen. You can change this later in Settings.";
                    add("mode:s", actions.simple() ? "Simple  ✓" : "Simple"); add("mode:f", !actions.simple() ? "Full  ✓" : "Full");
                    break;
                case SetupFlow.WELCOME:
                    text = "Explore your TV to its maximum potential.";
                    add("start", "Let's start"); add("skip", "Skip setup");
                    break;
                case SetupFlow.HOME:
                    text = "With OMNI as your Home, the Home button on your remote always brings you here. You can switch back to the TV's own home screen at any time in Settings.";
                    statusOk = actions.isHome();
                    statusText = statusOk ? "OMNI is your Home" : "Another app is Home right now";
                    if (!statusOk) add("home", "Choose Home");
                    add("next", "Next");
                    break;
                case SetupFlow.REMOTE:
                    text = "Press INFO on your remote. It opens the Control Center from anywhere in OMNI: volume, inputs, server and weather in one place. Menu works too.";
                    statusOk = infoSeen;
                    statusText = infoSeen ? "INFO works" : lastKey.isEmpty() ? "Waiting for a button…" : "You pressed " + lastKey + ". Try INFO (or use Menu).";
                    add("next", "Next");
                    break;
                case SetupFlow.SERVER:
                    text = "A computer at home can keep your films and show them here, with posters and Continue watching. No server? Skip this; everything else works without one.";
                    String cur = actions.server();
                    if (scanning || !scanText.isEmpty()) statusText = scanText; else statusText = cur == null ? "Not set up" : "Using " + cur;
                    statusOk = !scanning && cur != null && scanText.isEmpty();
                    if (!scanning) add("find", "Find my server");
                    for (String h : found) if (buttons.size() < 3 && (cur == null || !cur.contains(h))) add("use:" + h, "Use " + h);
                    add("next", cur == null ? "Skip" : "Next");
                    break;
                case SetupFlow.WEATHER:
                    text = "Pick the place for the weather on Home and in the Control Center.";
                    String w = actions.weather();
                    statusOk = w != null;
                    statusText = w == null ? "No place chosen yet" : w;
                    add("place", w == null ? "Choose place" : "Change place"); add("next", "Next");
                    break;
                case SetupFlow.ANTENNA:
                    text = "Your TV has an antenna tuner. OMNI finds the channels the TV has scanned and sorts them by country. If there are none yet, the TV's own scan finds them.";
                    statusText = actions.antenna();
                    statusOk = !statusText.isEmpty() && !statusText.startsWith("No ");
                    add("scan", "Scan for antenna channels"); add("next", "Next");
                    break;
                case SetupFlow.QUICK:
                    text = "Choose what is in your quick access row on Home: apps, or channels and inputs on a remote button. You can change it any time in Settings.";
                    add("apps", "Choose apps"); add("buttons", "Set remote buttons"); add("next", "Next");
                    break;
                case SetupFlow.PERMISSIONS:
                    text = "Listings fill Continue watching from your apps. HDMI lets inputs play live inside OMNI and shows notices over other apps. Mic is for the assistant. Casting lets OMNI notice when a phone casts to this TV. Each is optional.";
                    addDone("listings", "Listings", actions.listings());
                    addDone("overlay", "HDMI", actions.overlay());
                    addDone("mic", "Mic", actions.mic());
                    addDone("sessions", "Casting", actions.sessions());
                    add("next", "Next");
                    break;
                default:
                    text = "INFO opens the Control Center.  Hold OK on anything for options.  The mic button talks to the assistant.  Settings has this setup again.";
                    add("finish", "Start using OMNI");
            }
            text = Tr.t(text); statusText = Tr.t(statusText);
            int bw = Math.max(1, getWidth() - dp(PAD) * 2);
            if (step == SetupFlow.WELCOME) { body.setTextSize(dp(18)); body.setColor(Theme.TEXT2); }
            else { body.setTextSize(dp(15)); body.setColor(Theme.TEXT2); }
            bodyLayout = new StaticLayout(text, body, bw, step == SetupFlow.WELCOME ? Layout.Alignment.ALIGN_CENTER : Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false);
            layoutButtons();
            focus = 0;
            if (keep != null) for (int i = 0; i < buttons.size(); i++) if (buttons.get(i).id.equals(keep)) focus = i;
            if (animate) { fade.snap(0f); fade.to(1f); }
            moveHighlight(animate || hw == 0);
            invalidate();
        }

        private void add(String id, String label) { Btn b = new Btn(); b.id = id; b.label = Tr.t(label.replace("  ✓", "")) + (label.endsWith("  ✓") ? "  ✓" : ""); buttons.add(b); }
        private void addDone(String id, String label, boolean done) { Btn b = new Btn(); b.id = id; b.label = done ? Tr.t(label) + "  ✓" : Tr.t(label); b.done = done; buttons.add(b); }

        private void layoutButtons() {
            float total = 0;
            float[] w = new float[buttons.size()];
            for (int i = 0; i < w.length; i++) { w[i] = Math.max(dp(buttons.size() > 4 ? 88 : 110), btn.measureText(buttons.get(i).label) + dp(buttons.size() > 4 ? 30 : 40)); total += w[i]; }
            total += dp(BTN_GAP) * Math.max(0, w.length - 1);
            float x = step == SetupFlow.WELCOME || step == SetupFlow.DONE ? (getWidth() - total) / 2f : dp(PAD);
            float y = getHeight() - dp(PAD) - dp(BTN_H) - dp(18);
            for (int i = 0; i < w.length; i++) { buttons.get(i).r.set(x, y, x + w[i], y + dp(BTN_H)); x += w[i] + dp(BTN_GAP); }
        }

        private void moveHighlight(boolean snap) {
            if (buttons.isEmpty()) return;
            RectF f = buttons.get(Math.min(focus, buttons.size() - 1)).r;
            if (snap) { sx.snap(f.left); sy.snap(f.top); sw.snap(f.width()); sh.snap(f.height()); }
            else { sx.to(f.left); sy.to(f.top); sw.to(f.width()); sh.to(f.height()); }
        }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) { super.onSizeChanged(w, h, ow, oh); rebuild(false); }

        @Override protected void onDraw(Canvas c) {
            r.set(0, 0, getWidth(), getHeight());
            Glass.draw(c, r, dp(36), ambient, getLeft() + getTranslationX(), getTop() + getTranslationY(), 1f, true);
            int a = (int) (255 * Math.max(0f, Math.min(1f, content)));
            float cx = getWidth() / 2f;
            if (step == SetupFlow.WELCOME) {
                // the OMNI mark: a ring and a dot, drawn, not an image
                ring.setStrokeWidth(dp(5)); ring.setColor(Theme.alpha(Theme.WHITE, a));
                c.drawCircle(cx, dp(84), dp(26), ring);
                p.setColor(Theme.alpha(Theme.WHITE, a)); c.drawCircle(cx, dp(84), dp(7), p);
                hero.setAlpha(a);
                c.drawText(heading, cx, dp(168), hero);
                drawBody(c, dp(PAD), dp(190), a);
            } else {
                title.setAlpha(a);
                c.drawText(heading, dp(PAD), dp(PAD) + dp(30), title);
                drawBody(c, dp(PAD), dp(PAD) + dp(50), a);
                if (!statusText.isEmpty()) {
                    float sy0 = dp(PAD) + dp(60) + (bodyLayout == null ? 0 : bodyLayout.getHeight()) + dp(10);
                    p.setColor(Theme.alpha(statusOk ? Theme.WHITE : Theme.TEXT3, a));
                    float dx = dp(PAD) + dp(7);
                    if (statusOk) { ring.setStrokeWidth(dp(2.5f)); ring.setColor(Theme.alpha(Theme.WHITE, a)); c.drawLine(dx - dp(5), sy0 - dp(5), dx - dp(1), sy0 - dp(1), ring); c.drawLine(dx - dp(1), sy0 - dp(1), dx + dp(6), sy0 - dp(10), ring); }
                    else c.drawCircle(dx, sy0 - dp(5), dp(4), p);
                    status.setColor(statusOk ? Theme.WHITE : Theme.TEXT2); status.setAlpha(a);
                    c.drawText(statusText, dp(PAD) + dp(22), sy0, status);
                }
            }
            // buttons: quiet glass pills; the focused one is the white pill that glides
            for (int i = 0; i < buttons.size(); i++) {
                Btn b = buttons.get(i);
                p.setColor(Theme.alpha(Theme.WHITE, b.done ? 0x30 : 0x22));
                c.drawRoundRect(b.r, b.r.height() / 2f, b.r.height() / 2f, p);
                btn.setColor(Theme.WHITE); btn.setAlpha(a);
                c.drawText(b.label, b.r.centerX(), b.r.centerY() + dp(5), btn);
            }
            if (hasFocus() && hw > 0 && !buttons.isEmpty()) {
                p.setColor(Theme.WHITE);
                r.set(hx, hy, hx + hw, hy + hh);
                c.drawRoundRect(r, hh / 2f, hh / 2f, p);
                Btn b = buttons.get(Math.min(focus, buttons.size() - 1));
                c.save(); c.clipRect(r);
                btn.setColor(0xFF000000);
                c.drawText(b.label, b.r.centerX(), b.r.centerY() + dp(5), btn);
                c.restore();
            }
            // progress dots and the quiet hint line
            int[] d = SetupFlow.dots(step, simpleNow, tunerNow);
            float by = getHeight() - dp(20);
            if (d != null) {
                float gap = dp(14), x0 = cx - gap * (d[1] - 1) / 2f;
                for (int i = 0; i < d[1]; i++) { p.setColor(i == d[0] ? Theme.WHITE : 0x55FFFFFF); c.drawCircle(x0 + gap * i, by, i == d[0] ? dp(3.5f) : dp(3), p); }
                hint.setTextAlign(Paint.Align.RIGHT); c.drawText(Tr.t("Back: previous step"), getWidth() - dp(PAD), by + dp(4), hint);
            }
        }

        private void drawBody(Canvas c, float x, float y, int a) {
            if (bodyLayout == null) return;
            body.setAlpha(a);
            c.save(); c.translate(x, y); bodyLayout.draw(c); c.restore();
        }

        @Override protected void onFocusChanged(boolean gained, int dir, android.graphics.Rect prev) { super.onFocusChanged(gained, dir, prev); invalidate(); }

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            if (step == SetupFlow.REMOTE && !SetupFlow.isNavigation(code)) {
                boolean info = ControlCenter.isInfo(e) || code == KeyEvent.KEYCODE_MENU;
                if (info) infoSeen = true;
                lastKey = KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
                OmniLog.state("welcome key=" + lastKey);
                rebuild(false);
                return true;
            }
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_LEFT: case KeyEvent.KEYCODE_DPAD_RIGHT: {
                    int n = Math.max(0, Math.min(buttons.size() - 1, focus + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1)));
                    if (n != focus) { focus = n; moveHighlight(false); invalidate(); }
                    return true;
                }
                case KeyEvent.KEYCODE_DPAD_UP: case KeyEvent.KEYCODE_DPAD_DOWN: return true;          // one row of buttons: focus never leaves the card
                case KeyEvent.KEYCODE_DPAD_CENTER: case KeyEvent.KEYCODE_ENTER: case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (e.getRepeatCount() == 0 && !buttons.isEmpty()) activate(buttons.get(Math.min(focus, buttons.size() - 1)).id);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (step != SetupFlow.WELCOME) go(SetupFlow.back(step, actions.simple(), actions.tuner()));
                    return true;
                default: return super.onKeyDown(code, e);
            }
        }

        private void activate(String id) {
            OmniLog.state("welcome activate=" + id + " step=" + step);
            switch (id) {
                case "start": case "next": go(SetupFlow.next(step, actions.simple(), actions.tuner())); break;
                case "mode:s": case "mode:f": actions.setSimple(id.equals("mode:s")); go(SetupFlow.next(step, actions.simple(), actions.tuner())); break;
                case "scan": actions.scanAntenna(); break;
                case "apps": actions.pickApps(); break;
                case "buttons": actions.pickButtons(); break;
                case "skip": dismiss(false); break;
                case "finish": dismiss(true); break;
                case "home": actions.chooseHome(); break;
                case "find": setScan(true, null); actions.findServer(); break;
                case "place": actions.pickWeather(); break;
                case "listings": if (!actions.listings()) actions.askListings(); break;
                case "overlay": if (!actions.overlay()) actions.askOverlay(); break;
                case "mic": if (!actions.mic()) actions.askMic(); break;
                case "sessions": if (!actions.sessions()) actions.askSessions(); break;
                default:
                    if (id.startsWith("lang:")) { try { Tr.lang = Integer.parseInt(id.substring(5)); actions.languageChanged(SetupFlow.MODE); } catch (NumberFormatException ignored) { } break; }
                    if (id.startsWith("use:")) { actions.useServer(id.substring(4)); scanText = ""; found = new ArrayList<>(); rebuild(false); }
            }
        }

        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setContentDescription(heading + ". " + (buttons.isEmpty() ? "" : buttons.get(Math.min(focus, buttons.size() - 1)).label));
        }
    }
}
