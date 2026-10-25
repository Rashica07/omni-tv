package com.rasha.omni;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import static com.rasha.omni.Theme.dp;

/** iOS-style bottom sheet: dims the screen and springs up with focusable options. */
final class Sheet extends FrameLayout {
    private final View scrim;
    private final LinearLayout panel;
    private final Spring in;
    private final Runnable onGone;
    private boolean closing;
    private boolean gone;          // the close callback runs exactly once: the spring keeps ticking after the sheet is removed
    private final ScrollView list;
    private final LinearLayout options;
    private final View header;

    Sheet(Context c, String title, String sub, List<String> labels, List<Runnable> actions, Runnable onGone) {
        super(c);
        this.onGone = onGone;
        setClickable(true);
        scrim = new View(c);
        scrim.setBackgroundColor(0xB8000000);
        scrim.setAlpha(0f);
        addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(18), dp(14), dp(18));
        GradientDrawable bg = new GradientDrawable();
        float r = dp(24);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        bg.setColor(Theme.S1);
        bg.setStroke(Math.max(1, dp(1)), Theme.HAIR);
        panel.setBackground(bg);
        panel.setTranslationY(dp(600));

        TextView t = Theme.text(new TextView(c), 22, Theme.TEXT, 700);
        t.setText(title);
        t.setPadding(dp(14), 0, dp(14), 0);
        panel.addView(t);
        if (sub != null) {
            TextView s = Theme.text(new TextView(c), 13, Theme.TEXT3, 400);
            s.setText(sub);
            if (sub.length() > 60) {
                s.setSingleLine(false);
                s.setMaxLines(7);
                s.setLineSpacing(0, 1.15f);
                s.setTextColor(Theme.TEXT2);
            }
            s.setPadding(dp(14), dp(4), dp(14), dp(10));
            panel.addView(s);
        }
        header = panel.getChildAt(panel.getChildCount() - 1);
        options = new LinearLayout(c);
        options.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < labels.size(); i++) options.addView(option(c, labels.get(i), actions.get(i)));
        list = new ScrollView(c);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setFillViewport(false);
        list.addView(options, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(panel, new LayoutParams(dp(440), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));

        in = new Spring(260f, 0.86f, 0f, 0.002f, v -> {
            panel.setTranslationY(panel.getHeight() * (1f - v));
            scrim.setAlpha(Math.max(0f, Math.min(1f, v)));
            if (closing && v < 0.01f) finish();
        });
    }

    private View option(Context c, String text, final Runnable action) {
        TextView o = Theme.text(new TextView(c), 17, Theme.TEXT, 500);
        o.setText(text);
        o.setGravity(Gravity.CENTER_VERTICAL);
        o.setPadding(dp(14), 0, dp(14), 0);
        o.setFocusable(true);
        o.setClickable(true);
        final GradientDrawable hl = new GradientDrawable();
        hl.setCornerRadius(dp(Theme.RADIUS));
        hl.setColor(0);
        o.setBackground(hl);
        o.setOnFocusChangeListener((v, has) -> {
            hl.setColor(has ? Theme.WHITE : 0);
            o.setTextColor(has ? Theme.BLACK : Theme.TEXT);
        });
        o.setOnClickListener(v -> action.run());
        o.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        return o;
    }

    void show(ViewGroup root) {
        root.addView(this, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        post(() -> {
            capHeight();
            in.snap(0f);
            in.to(1f);
            ensureFocus(0);
        });
    }

    /** A sheet is never taller than the safe area: the header stays, the options scroll. */
    private void capHeight() {
        int maxPanel = getHeight() - dp(Theme.SAFE_VERT) * 2;
        if (maxPanel <= 0) return;
        panel.measure(MeasureSpec.makeMeasureSpec(dp(440), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int excess = panel.getMeasuredHeight() - maxPanel;
        if (excess > 0) {
            int h = Math.max(dp(100), options.getChildCount() * dp(50) - excess);
            ViewGroup.LayoutParams lp = list.getLayoutParams();
            lp.height = h;
            list.setLayoutParams(lp);
        }
    }

    private View firstOption() {
        for (int i = 0; i < options.getChildCount(); i++) if (options.getChildAt(i).isFocusable()) return options.getChildAt(i);
        return null;
    }

    /** Focus is asked for until it actually sticks (it can be lost while the page behind hands its own focus away), and what happened is logged. */
    private void ensureFocus(final int attempt) {
        if (closing || !isAttachedToWindow()) return;
        View f = firstOption();
        if (f == null) return;
        View cur = findFocus();
        if (cur != null && cur.getParent() == options) {
            OmniLog.state("sheet focus ok attempt=" + attempt + " on=" + (cur instanceof android.widget.TextView ? ((android.widget.TextView) cur).getText() : "?"));
            return;
        }
        f.requestFocus();
        if (attempt >= 6) { OmniLog.state("sheet focus FAILED"); return; }
        postDelayed(() -> ensureFocus(attempt + 1), attempt < 2 ? 60 : 160);
    }

    /** If a key arrives while nothing in the sheet has focus, the sheet takes it: the first press selects the first option instead of being lost. */
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (!closing && e.getAction() == android.view.KeyEvent.ACTION_DOWN) {
            int k = e.getKeyCode();
            boolean nav = k == android.view.KeyEvent.KEYCODE_DPAD_DOWN || k == android.view.KeyEvent.KEYCODE_DPAD_UP || k == android.view.KeyEvent.KEYCODE_DPAD_CENTER || k == android.view.KeyEvent.KEYCODE_ENTER;
            View cur = findFocus();
            if (nav && (cur == null || cur.getParent() != options)) {
                View f = firstOption();
                if (f != null) { f.requestFocus(); OmniLog.state("sheet focus recovered on key"); return true; }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    void dismiss() {
        if (closing) return;
        closing = true;
        in.to(0f);
    }

    private void finish() {
        if (gone) return;
        gone = true;
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        onGone.run();
    }
}
