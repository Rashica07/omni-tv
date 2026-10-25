package com.rasha.omni;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import static com.rasha.omni.Theme.dp;

/** A tab's content. Shelf pages fit the viewport; Settings scrolls with a spring. */
abstract class Page extends ScrollView {
    final Host host;
    final LinearLayout col;
    private final Spring sy = new Spring(190f, 0.95f, 0f, 0.5f, v -> scrollTo(0, (int) v));

    Page(Host h) {
        super(h.context());
        host = h;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        setClipToPadding(false);
        col = new LinearLayout(h.context());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) { return 0; }

    /** Cards never overlap, so fading the page can fade each child directly with no off-screen pass. */
    @Override public boolean hasOverlappingRendering() { return false; }

    /** Rebuilds the content, restoring focus to the given item in the given shelf when present. */
    abstract void refresh(String focusId, String focusShelf);

    abstract void focusFirst();

    /** One short line telling the person what OK does here (shown bottom-right, inside the safe area). */
    String hint() { return ""; }

    void onShown() { host.ambient().setGlow(null); }

    void onHidden() { }

    /** The activity came back to the foreground (for example from full-screen playback) while this page is the one showing. */
    void onResume() { }

    /** Back pressed while this page is showing and focus is on it. Return true when the page handled it (e.g. closing a sub-view). */
    boolean handleBack() { return false; }

    /** Lights the screen in the focused app's brand colour, centred on its tile; items without a colour clear it. */
    void bloom(ShelfStack st, ShelfStack.Item it) {
        if (it == null || it.color == 0) { host.ambient().clearBloom(); return; }
        android.graphics.RectF b = st.focusedBounds();
        int[] loc = new int[2];
        st.getLocationInWindow(loc);
        float w = host.ambient().getWidth() > 0 ? host.ambient().getWidth() : 1280f;
        float cx = b == null ? w * 0.25f : loc[0] + b.centerX(), cy = b == null ? 420f : loc[1] + b.centerY();
        host.ambient().setBloom(it.color, Math.max(w * 0.15f, Math.min(w * 0.6f, cx)), cy);
    }

    /** Takes focus once the view has been laid out (focus requests on an unsized view are ignored). */
    static void focusSoon(final View v) {
        if (v.isLaidOut() && !v.isLayoutRequested()) {
            v.requestFocus();
            return;
        }
        v.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View x, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                x.removeOnLayoutChangeListener(this);
                x.post(x::requestFocus);
            }
        });
        v.requestLayout();
    }

    /** Springs the scroll so the given descendant sits in the middle of the viewport. */
    void revealCenter(View v) {
        int y = 0;
        View c = v;
        while (c != null && c != col) {
            y += c.getTop();
            c = (View) c.getParent();
        }
        int max = Math.max(0, col.getHeight() - getHeight());
        int t = Math.min(max, Math.max(0, y - (getHeight() - v.getHeight()) / 2));
        if (!sy.isRunning()) sy.snap(getScrollY());
        sy.to(t);
    }

    void resetScroll() {
        sy.snap(0);
        scrollTo(0, 0);
    }

    /** Shown instead of a page when the TV has no launchable apps. */
    TextView emptyMessage() {
        TextView t = Theme.text(new TextView(host.context()), 20, Theme.TEXT2, 500);
        t.setSingleLine(false);
        t.setGravity(android.view.Gravity.CENTER);
        t.setText("No apps found\nInstall apps from the Play Store, or sideload them with adb.");
        t.setPadding(dp(120), dp(150), dp(120), 0);
        return t;
    }
}
