package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rasha.omni.Theme.dp;

/**
 * OMNI's single browsing primitive: a stack of shelves drawn by one custom view. Tiles keep a fixed pitch and only the focused tile
 * lifts; shelves open and close by relevance (the active shelf shows full tiles, the others collapse to a strip of small ones).
 * Everything is drawn from pre-rounded bitmaps (see {@link IconArt}): no child views, no layout per frame and no clip paths.
 *
 * Move mode: the held tile floats above a dashed slot that shows exactly where it will land, the other tiles slide aside, and
 * everything else is dimmed. OK places it, Back cancels and restores the original order.
 */
final class ShelfStack extends View {
    static final int KIND_APP = 0;
    static final int KIND_POSTER = 1;

    static final int SHAPE_BANNER = 0;       // widescreen tile (app banner)
    static final int SHAPE_SQUARE_SELF = 1;  // icon with its own silhouette: the icon IS the tile
    static final int SHAPE_SQUARE_PLATE = 2; // free-form icon on a neutral plate
    static final int SHAPE_POSTER = 3;       // portrait poster

    /** One tile. Art is attached by the page that owns the data. */
    static final class Item {
        final String id;
        String label;
        int shape = SHAPE_BANNER;
        /** The tile bitmap (banner, icon, or poster), already rounded where it needs to be. */
        Bitmap art;
        /** Small normalised icon for collapsed strips (null for posters: they use art). */
        Bitmap icon;
        Object data;
        int requested;
        CharSequence wideLabel;
        /** A few-pixel sample of the artwork for the colour wash behind the page (computed on first focus). */
        Bitmap glow;
        /** Ambient colour for this item (an app's brand colour), 0 for none. */
        int color;
        /** Animated offset that makes a reorder slide instead of jump; decays to zero. */
        float dx;

        Item(String id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    static final class Shelf {
        final String id;
        final String name;
        final int kind;
        final List<Item> items;
        boolean reorderable;

        Shelf(String id, String name, int kind, List<Item> items) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.items = items;
        }
    }

    interface Listener {
        void onFocus(Item it, Shelf shelf, int index);
        void onOpen(Item it, Shelf shelf, int x, int y, int w, int h);
        void onMenu(Item it, Shelf shelf);
        /** The move ended. placed=false means it was cancelled and the original order is back. orderedIds is the shelf's order now. */
        void onMoveEnd(Shelf shelf, Item it, boolean placed, List<String> orderedIds);
        /** Up was pressed on the first shelf: hand focus to the header. */
        void onLeaveUp();
        /** Called once per item when its art is first needed (near focus). */
        void want(Item it, Shelf shelf);
        /** Left (-1) or Right (+1) pressed at the end of the shelf. Return true to take it (e.g. move focus to a neighbouring panel). */
        default boolean onEdge(int dx) { return false; }
    }

    // geometry (dp). Tiles keep a fixed pitch; shelves open and close by relevance (docs/design spec D2).
    private static final float HEAD = 28f;
    private static final float PAD_TOP = 14f;       // room above tiles for the lift and the ring
    private static final float PAD_BOTTOM = 26f;    // room below for the ring and icon names
    private static final float V_GAP = 6f;
    private static final float COLL_W = 56f;
    private static final float COLL_H = 48f;
    private static final float GAP_ACTIVE = 14f;
    private static final float GAP_COLL = 8f;
    private static final int LEAD = 2;

    private final Listener listener;
    /** Page name for log lines, so tests can tell the stacks apart. */
    String logName = "";
    /** Right margin in dp that tiles keep clear (default: the safe area). A page with a neighbouring panel uses less so focus rings are not cut. */
    float rightInsetDp = Theme.SAFE_SIDE;
    private List<Shelf> shelves = new ArrayList<>();
    private final Map<String, String> lastItem = new HashMap<>();
    private int shelf;
    private int[] idxs = new int[0];
    private float fx;
    private float fy;
    private float enter = 1f;
    private float ring;
    private float drop;          // 1 right after placing a tile, springs to 0 with a small bounce
    private boolean initialised;
    private boolean longPressed;
    private String movingId;
    private String movingShelf;
    private List<String> moveStartOrder;
    private long lastFrame;
    private float[] lefts = new float[40];

    private static final ColorMatrixColorFilter[] DIM = filters();

    private static ColorMatrixColorFilter[] filters() {
        ColorMatrixColorFilter[] out = new ColorMatrixColorFilter[16];
        for (int i = 0; i < 16; i++) {
            float k = 1f - i / 15f;
            ColorMatrix m = new ColorMatrix(new float[]{k, 0, 0, 0, 0, 0, k, 0, 0, 0, 0, 0, k, 0, 0, 0, 0, 0, 1, 0});
            out[i] = new ColorMatrixColorFilter(m);
        }
        return out;
    }

    private final Spring fxSpring = new Spring(240f, 0.82f, 0f, 0.002f, v -> { fx = v; invalidate(); });
    private final Spring fySpring = new Spring(220f, 0.88f, 0f, 0.002f, v -> { fy = v; invalidate(); });
    private final Spring enterSpring = new Spring(200f, 0.9f, 1f, 0.002f, v -> { enter = v; invalidate(); });
    private final Spring ringSpring = new Spring(400f, 0.9f, 0f, 0.002f, v -> { ring = v; invalidate(); });
    private final Spring dropSpring = new Spring(320f, 0.42f, 0f, 0.002f, v -> { drop = v; invalidate(); });

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tileText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF tmp = new RectF();

    ShelfStack(Context c, Listener l) {
        super(c);
        listener = l;
        setFocusable(true);
        stroke.setStyle(Paint.Style.STROKE);
        tileText.setTypeface(Theme.geist);
        tileText.setTextSize(dp(13));
        tileText.setTextAlign(Paint.Align.CENTER);
        headText.setTypeface(Theme.geist);
        headText.setTextSize(dp(16));
        if (Build.VERSION.SDK_INT >= 26) {
            tileText.setFontVariationSettings("'wght' 600");
            headText.setFontVariationSettings("'wght' 700");
        }
    }

    // ------------------------------------------------------------ state

    Shelf shelf() { return shelf < shelves.size() ? shelves.get(shelf) : null; }

    Item current() {
        Shelf s = shelf();
        if (s == null || s.items.isEmpty()) return null;
        return s.items.get(Math.max(0, Math.min(idxs[shelf], s.items.size() - 1)));
    }

    List<Shelf> shelves() { return shelves; }

    int index() { return idxs.length > shelf ? idxs[shelf] : 0; }

    boolean isMoving() {
        Item it = current();
        Shelf s = shelf();
        return it != null && s != null && movingId != null && it.id.equals(movingId) && s.id.equals(movingShelf);
    }

    /** Starts (or, with null, ends) move mode for the given item. The shelf must already be showing it. */
    void setMoving(String itemId, String shelfId) {
        if (itemId != null && movingId == null) {
            Shelf s = shelf();
            moveStartOrder = new ArrayList<>();
            if (s != null) for (Item i : s.items) moveStartOrder.add(i.id);
        }
        movingId = itemId;
        movingShelf = shelfId;
        invalidate();
    }

    /** Replaces the data, keeping or restoring the focused item by identity. */
    void setShelves(List<Shelf> list, String focusItem, String focusShelf) {
        Shelf cur = shelf();
        String keepShelf = focusShelf != null ? focusShelf : cur != null ? cur.id : null;
        if (focusItem != null && focusShelf != null) lastItem.put(focusShelf, focusItem);
        Item ci = current();
        if (cur != null && ci != null && focusItem == null) lastItem.put(cur.id, ci.id);
        shelves = list;
        if (android.util.Log.isLoggable("OmniState", android.util.Log.DEBUG)) {
            for (Shelf sh : list) {
                StringBuilder sb = new StringBuilder("shelf page=" + logName + " " + sh.id + "=");
                for (int i = 0; i < Math.min(sh.items.size(), 12); i++) sb.append(sh.items.get(i).id).append(i < sh.items.size() - 1 ? "," : "");
                OmniLog.state(sb.toString());
            }
        }
        idxs = new int[list.size()];
        int active = 0;
        for (int s = 0; s < list.size(); s++) {
            Shelf sh = list.get(s);
            if (sh.id.equals(keepShelf)) active = s;
            String want = lastItem.get(sh.id);
            int i = 0;
            if (want != null) {
                for (int j = 0; j < sh.items.size(); j++) if (sh.items.get(j).id.equals(want)) i = j;
            }
            idxs[s] = Math.max(0, Math.min(i, Math.max(0, sh.items.size() - 1)));
        }
        shelf = active;
        if (!initialised) {
            fxSpring.snap(idxs.length > shelf ? idxs[shelf] : 0);
            fySpring.snap(shelf);
            initialised = true;
        } else {
            fxSpring.to(idxs.length > shelf ? idxs[shelf] : 0);
            fySpring.to(shelf);
        }
        invalidate();
        focusChanged();
    }

    /** Home button: first shelf, first item. */
    void toStart() {
        shelf = 0;
        if (idxs.length > 0) idxs[0] = 0;
        fxSpring.to(0f);
        fySpring.to(0f);
        enterSpring.snap(0f);
        enterSpring.to(1f);
        focusChanged();
    }

    private void setShelf(int n) {
        Item ci = current();
        if (ci != null) lastItem.put(shelf().id, ci.id);
        shelf = n;
        fxSpring.snap(idxs[n]);
        fySpring.to(n);
        focusChanged();
    }

    private void focusChanged() {
        Item it = current();
        Shelf s = shelf();
        if (it == null || s == null) return;
        lastItem.put(s.id, it.id);
        OmniLog.state("focus page=" + logName + " shelf=" + s.id + " item=" + it.id + " idx=" + idxs[shelf]);
        listener.onFocus(it, s, idxs[shelf]);
        announce(it, s);
        wantArt();
        setContentDescription(it.label + ", " + (idxs[shelf] + 1) + " of " + s.items.size() + ", " + s.name);
    }

    /** Asks the owner for art near the focus; each item is requested once. */
    private void wantArt() {
        Shelf s = shelf();
        if (s == null) return;
        int i = idxs[shelf];
        for (int j = Math.max(0, i - 2); j < Math.min(s.items.size(), i + 9); j++) request(s.items.get(j), s);
        if (shelf + 1 < shelves.size()) {
            Shelf n = shelves.get(shelf + 1);
            for (int j = 0; j < Math.min(n.items.size(), 12); j++) request(n.items.get(j), n);
        }
    }

    private void request(Item it, Shelf s) {
        if ((it.requested & 1) != 0) return;
        it.requested |= 1;
        listener.want(it, s);
    }

    // ------------------------------------------------------------ accessibility

    /**
     * The view draws every tile itself, so a screen reader would see one blank block. This exposes each tile of the active
     * shelf as a virtual node (pattern from Google's AccessibilityDemo, android/tv-samples, Apache-2.0): name, position in
     * the shelf, bounds, and a click action that opens the item.
     */
    private final AccessibilityNodeProvider a11y = new AccessibilityNodeProvider() {
        @Override public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            Shelf sh = shelf();
            if (id == HOST_VIEW_ID) {
                AccessibilityNodeInfo host = AccessibilityNodeInfo.obtain(ShelfStack.this);
                onInitializeAccessibilityNodeInfo(host);
                if (sh != null) for (int i = 0; i < sh.items.size(); i++) host.addChild(ShelfStack.this, i);
                return host;
            }
            if (sh == null || id < 0 || id >= sh.items.size()) return null;
            Item it = sh.items.get(id);
            AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain(ShelfStack.this, id);
            n.setPackageName(getContext().getPackageName());
            n.setClassName("android.widget.Button");
            n.setParent(ShelfStack.this);
            n.setText(it.label);
            n.setContentDescription(it.label + ", " + (id + 1) + " of " + sh.items.size() + ", " + sh.name);
            n.setEnabled(true);
            n.setFocusable(true);
            n.setClickable(true);
            n.setVisibleToUser(true);
            n.setFocused(isFocused() && id == idxs[shelf]);
            n.addAction(AccessibilityNodeInfo.ACTION_CLICK);
            n.addAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
            n.addAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
            RectF r = tileRect(shelf, id);
            Rect inParent = new Rect((int) r.left, (int) r.top, (int) r.right, (int) r.bottom);
            n.setBoundsInParent(inParent);
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            Rect onScreen = new Rect(inParent);
            onScreen.offset(loc[0], loc[1]);
            n.setBoundsInScreen(onScreen);
            return n;
        }

        @Override public boolean performAction(int id, int action, android.os.Bundle args) {
            if (id == HOST_VIEW_ID) return performAccessibilityAction(action, args);
            Shelf sh = shelf();
            if (sh == null || id < 0 || id >= sh.items.size()) return false;
            if (action == AccessibilityNodeInfo.ACTION_CLICK) {
                idxs[shelf] = id;
                fxSpring.to(id);
                focusChanged();
                open();
                return true;
            }
            return action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS
                    || action == AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS;
        }
    };

    @Override public AccessibilityNodeProvider getAccessibilityNodeProvider() { return a11y; }

    private void announce(Item it, Shelf s) {
        AccessibilityManager am = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am != null && am.isEnabled() && isFocused()) {
            announceForAccessibility(it.label + ", " + (idxs[shelf] + 1) + " of " + s.items.size() + ", " + s.name);
        }
    }

    // ------------------------------------------------------------ input

    @Override protected void onFocusChanged(boolean gained, int direction, Rect prev) {
        super.onFocusChanged(gained, direction, prev);
        ringSpring.to(gained ? 1f : 0f);
    }

    @Override public boolean onKeyDown(int code, KeyEvent e) {
        Shelf sh = shelf();
        if (sh == null || sh.items.isEmpty()) return super.onKeyDown(code, e);
        boolean moving = isMoving();
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT: if (!step(-1, moving) && !moving) listener.onEdge(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (!step(1, moving) && !moving) listener.onEdge(1); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (moving) return true;
                if (shelf > 0) setShelf(shelf - 1);
                else listener.onLeaveUp();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (!moving && shelf < shelves.size() - 1) setShelf(shelf + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (e.getRepeatCount() == 0) {
                    longPressed = false;
                    e.startTracking();
                }
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (e.getRepeatCount() == 0 && !moving) {
                    Item it = current();
                    if (it != null) listener.onMenu(it, sh);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (moving) {
                    endMove(false);
                    return true;
                }
                return super.onKeyDown(code, e);
            default:
                return super.onKeyDown(code, e);
        }
    }

    @Override public boolean onKeyLongPress(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            longPressed = true;
            Item it = current();
            if (it != null && !isMoving()) listener.onMenu(it, shelf());
            return true;
        }
        return super.onKeyLongPress(code, e);
    }

    @Override public boolean onKeyUp(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (longPressed) {
                longPressed = false;
                return true;
            }
            if (e.isTracking() && !e.isCanceled()) {
                if (isMoving()) endMove(true);
                else open();
            }
            return true;
        }
        return super.onKeyUp(code, e);
    }

    /** Moves focus by d tiles; false when already at that end of the shelf. */
    private boolean step(int d, boolean moving) {
        Shelf sh = shelf();
        int i = idxs[shelf], ni = i + d;
        if (ni < 0 || ni >= sh.items.size()) return false;
        if (moving && sh.reorderable) {
            // swap, and give both tiles an offset equal to the distance they just jumped, so they slide into place
            float li = leftAt(shelf, i), ln = leftAt(shelf, ni);
            Item a = sh.items.get(i), b = sh.items.get(ni);
            Collections.swap(sh.items, i, ni);
            float li2 = leftAt(shelf, i), ln2 = leftAt(shelf, ni);
            a.dx += li - ln2;     // a moved from slot i to slot ni
            b.dx += ln - li2;     // b moved from slot ni to slot i
            invalidate();
        }
        idxs[shelf] = ni;
        fxSpring.to(ni);
        focusChanged();
        return true;
    }

    /** Bounds of the focused tile in this view's coordinates, or null. The tile is where it rests once the focus animation settles. */
    RectF focusedBounds() {
        if (shelves.isEmpty() || shelf >= shelves.size() || shelves.get(shelf).items.isEmpty()) return null;
        return tileRect(shelf, Math.min(idxs[shelf], shelves.get(shelf).items.size() - 1));
    }

    int shelfIndex() { return shelf; }

    private void endMove(boolean placed) {
        Item it = current();
        Shelf sh = shelf();
        if (it == null || sh == null) return;
        if (!placed && moveStartOrder != null) restoreOrder(sh);
        List<String> order = new ArrayList<>();
        for (Item i : sh.items) order.add(i.id);
        movingId = null;
        moveStartOrder = null;
        if (placed) {
            dropSpring.snap(1f);
            dropSpring.to(0f);
        }
        invalidate();
        listener.onMoveEnd(sh, it, placed, order);
        focusChanged();
    }

    /** Puts the shelf back in the order it had when the move began, sliding tiles back and returning focus to the held item. */
    private void restoreOrder(Shelf sh) {
        Map<String, Item> byId = new HashMap<>();
        for (Item i : sh.items) byId.put(i.id, i);
        float[] before = new float[sh.items.size()];
        Map<String, Float> was = new HashMap<>();
        for (int k = 0; k < sh.items.size(); k++) was.put(sh.items.get(k).id, leftAt(shelf, k));
        sh.items.clear();
        for (String id : moveStartOrder) { Item it = byId.get(id); if (it != null) sh.items.add(it); }
        for (Item i : byId.values()) if (!sh.items.contains(i)) sh.items.add(i);   // anything added meanwhile goes last
        for (int k = 0; k < sh.items.size(); k++) {
            Item it = sh.items.get(k);
            Float w = was.get(it.id);
            if (w != null) it.dx += w - leftAt(shelf, k);
            if (it.id.equals(movingId)) { idxs[shelf] = k; fxSpring.to(k); }
        }
    }

    private void open() {
        Item it = current();
        Shelf sh = shelf();
        if (it == null || sh == null) return;
        RectF r = tileRect(shelf, idxs[shelf]);
        listener.onOpen(it, sh, (int) r.left, (int) r.top, (int) r.width(), (int) r.height());
    }

    // ------------------------------------------------------------ geometry

    private static float bump(float d) { return Accordion.bump(d); }

    private float tOfShelf(int s) { return bump(Math.abs(s - fy)); }

    private float fxOf(int s) { return s == shelf ? fx : idxs[s]; }

    private static float activeW(Item it) {
        switch (it.shape) {
            case SHAPE_SQUARE_SELF:
            case SHAPE_SQUARE_PLATE: return 112f;
            case SHAPE_POSTER: return 120f;
            default: return 200f;
        }
    }

    private static float activeH(Shelf s) { return s.kind == KIND_POSTER ? 180f : 112f; }

    private float tileW(Item it, float t) { return dp(COLL_W + (activeW(it) - COLL_W) * t); }

    private float tileH(Shelf s, float t) { return dp(COLL_H + (activeH(s) - COLL_H) * t); }

    private float gap(float t) { return dp(GAP_COLL + (GAP_ACTIVE - GAP_COLL) * t); }

    private float shelfHeight(int s) {
        Shelf sh = shelves.get(s);
        return dp(HEAD + PAD_TOP + PAD_BOTTOM) + tileH(sh, tOfShelf(s));
    }

    private float shelfTop(int s) {
        float y = 0, scrollA = 0, scrollB = 0;
        int a = Math.max(0, Math.min(shelves.size() - 1, (int) Math.floor(fy)));
        int b = Math.min(shelves.size() - 1, a + 1);
        float yy = 0;
        for (int i = 0; i < shelves.size(); i++) {
            if (i == a) scrollA = yy;
            if (i == b) scrollB = yy;
            if (i == s) y = yy;
            yy += shelfHeight(i) + dp(V_GAP);
        }
        float fr = Math.max(0f, Math.min(1f, fy - a));
        return y - (scrollA + (scrollB - scrollA) * fr);
    }

    /** Fills {@link #lefts} with the left edge of every tile of shelf s (before scrolling) and returns the right edge of the last. */
    private float computeLefts(int s) {
        Shelf sh = shelves.get(s);
        int n = sh.items.size();
        if (lefts.length < n + 1) lefts = new float[n + 8];
        float t = tOfShelf(s), g = gap(t), x = 0;
        for (int i = 0; i < n; i++) {
            lefts[i] = x;
            x += tileW(sh.items.get(i), t) + g;
        }
        return Math.max(0f, x - g);
    }

    private float scrollOf(int s, float total) {
        int n = shelves.get(s).items.size();
        return Accordion.scrollVar(fxOf(s), lefts, n, total, getWidth() - dp(Theme.SAFE_SIDE) - dp(rightInsetDp), LEAD);
    }

    /** Left edge (view coordinates) of tile i in shelf s, without the slide offset. */
    private float leftAt(int s, int i) {
        float total = computeLefts(s);
        return dp(Theme.SAFE_SIDE) - scrollOf(s, total) + lefts[i];
    }

    /** Where a tile sits right now (view coordinates), used for opening, accessibility and the drop target. */
    private RectF tileRect(int s, int i) {
        Shelf sh = shelves.get(s);
        float t = tOfShelf(s);
        float left = leftAt(s, i) + sh.items.get(i).dx;
        float top = shelfTop(s) + dp(HEAD + PAD_TOP);
        return new RectF(left, top, left + tileW(sh.items.get(i), t), top + tileH(sh, t));
    }

    // ------------------------------------------------------------ drawing

    @Override protected void onDraw(Canvas c) {
        if (shelves.isEmpty()) return;
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        boolean sliding = decayOffsets(dt);
        float e = Math.max(0f, Math.min(1f, enter));
        float rise = (1f - e) * dp(24);
        boolean moving = movingId != null;
        for (int s = 0; s < shelves.size(); s++) {
            Shelf sh = shelves.get(s);
            if (sh.items.isEmpty()) continue;
            float top = shelfTop(s) + rise;
            float t = tOfShelf(s);
            float vis = s < fy ? Math.max(0f, 1f - (fy - s)) : 1f;
            vis *= Math.max(0f, Math.min(1f, (getHeight() - top) / dp(36)));
            vis *= e;
            if (moving && s != shelf) vis *= 0.3f;     // everything but the shelf being edited steps back
            if (vis <= 0.01f || top > getHeight()) continue;
            drawShelf(c, sh, s, top, t, (int) (255 * vis));
        }
        if (sliding) invalidate();
        else lastFrame = 0;
    }

    /** Exponential decay of the slide offsets; returns true while any tile is still sliding. */
    private boolean decayOffsets(float dt) {
        boolean any = false;
        float k = Math.min(1f, dt * 16f);
        for (Shelf sh : shelves) {
            for (Item it : sh.items) {
                if (it.dx == 0f) continue;
                if (Theme.reduceMotion || Math.abs(it.dx) < 0.4f) it.dx = 0f;
                else { it.dx -= it.dx * k; any = true; }
            }
        }
        return any;
    }

    private void drawShelf(Canvas c, Shelf sh, int s, float top, float t, int al) {
        int n = sh.items.size();
        headText.setColor(Theme.alpha(Theme.lerp(Theme.TEXT3, Theme.WHITE, t), al));
        c.drawText(sh.name, dp(Theme.SAFE_SIDE), top + dp(HEAD - 8), headText);
        float total = computeLefts(s);
        float scroll = scrollOf(s, total);
        float h = tileH(sh, t);
        float y = top + dp(HEAD + PAD_TOP);
        float focus = fxOf(s);
        boolean moving = movingId != null && s == shelf;
        int heldIdx = moving ? idxs[shelf] : -1;
        for (int i = 0; i < n; i++) {
            Item it = sh.items.get(i);
            float w = tileW(it, t);
            float l = dp(Theme.SAFE_SIDE) - scroll + lefts[i] + it.dx;
            if (l + w < -dp(40) || l > getWidth() + dp(40)) continue;
            boolean focused = s == shelf && i == idxs[shelf];
            float hz = bump(Math.abs(i - focus)) * t;
            if (moving && i == heldIdx) continue;                 // the held tile is drawn last, on top
            drawTile(c, sh, it, l, y, w, h, t, hz, focused, al, moving);
        }
        if (moving && heldIdx >= 0 && heldIdx < n) {
            Item it = sh.items.get(heldIdx);
            float w = tileW(it, t);
            float l = dp(Theme.SAFE_SIDE) - scroll + lefts[heldIdx] + it.dx;
            drawSlot(c, l, y, w, h, it);
            drawTile(c, sh, it, l, y, w, h, t, 1f, true, al, true);
            drawArrows(c, l, y, w, h, heldIdx > 0, heldIdx < n - 1);
        }
    }

    private float radiusFor(Item it, float w, float h) {
        if (it.shape == SHAPE_SQUARE_SELF) return Math.min(w, h) * 0.2237f;      // the silhouette most icons already have
        return Math.min(dp(Theme.RADIUS), h * 0.3f);
    }

    private void drawTile(Canvas c, Shelf sh, Item it, float l, float y, float w, float h, float t, float hz,
                          boolean focused, int al, boolean editing) {
        boolean held = editing && focused;
        float pop = held ? 1f + 0.25f * drop : 0f;                // brief overshoot when a tile is dropped
        float lift = dp(Theme.FOCUS_LIFT) * (held ? 2.2f + drop : hz);
        float sc = 1f + Theme.FOCUS_SCALE * (held ? 1.5f + drop * 0.6f : hz);
        int sv = c.save();
        c.translate(0f, -lift);
        c.scale(sc, sc, l + w / 2f, y + h / 2f);
        rect.set(l, y, l + w, y + h);
        float r = radiusFor(it, w, h);

        if (held) {
            // soft contact shadow so the tile reads as floating above its slot
            fill.setColor(Theme.alpha(Theme.BLACK, mul(150, al)));
            tmp.set(rect.left + dp(4), rect.bottom - dp(2), rect.right - dp(4), rect.bottom + dp(10));
            c.drawRoundRect(tmp, dp(8), dp(8), fill);
        }

        // how much of the "full tile" look versus the "small strip" look to show while the shelf opens or closes
        float full = Theme.smooth(0.3f, 0.8f, t);
        float dimBase = editing && !held ? 0.62f : 0.16f * (1f - hz);
        float dim = Math.min(0.95f, dimBase + 0.42f * (1f - t));
        if (full < 0.99f) drawSmall(c, it, rect, r, mul((int) (255 * (1f - full)), al), hz, dim);
        if (full > 0.01f) drawFull(c, sh, it, rect, r, mul((int) (255 * full), al), hz, dim);

        float ra = held ? 1f : ring * (focused ? 1f : 0f) * t * hz;
        if (ra > 0.02f) {
            // white ring OUTSIDE the tile with a black gap, so it stays visible over white banners (docs/design spec D3)
            float gap = dp(Theme.RING_GAP), rw = dp(Theme.RING);
            int a = mul((int) (255 * Math.min(1f, ra)), al);
            tmp.set(rect.left - gap - rw / 2f, rect.top - gap - rw / 2f, rect.right + gap + rw / 2f, rect.bottom + gap + rw / 2f);
            float rr = r + gap + rw / 2f;
            stroke.setPathEffect(null);
            stroke.setStrokeWidth(gap + rw);
            stroke.setColor(Theme.alpha(Theme.BLACK, a));
            tmp.inset(rw / 2f, rw / 2f);
            c.drawRoundRect(tmp, rr - rw / 2f, rr - rw / 2f, stroke);
            tmp.set(rect.left - gap - rw / 2f, rect.top - gap - rw / 2f, rect.right + gap + rw / 2f, rect.bottom + gap + rw / 2f);
            stroke.setStrokeWidth(rw);
            stroke.setColor(Theme.alpha(Theme.WHITE, a));
            c.drawRoundRect(tmp, rr, rr, stroke);
        }
        c.restoreToCount(sv);
        // icon names sit under square tiles of the active shelf (banner and poster art already carry the name)
        if (full > 0.6f && (it.shape == SHAPE_SQUARE_SELF || it.shape == SHAPE_SQUARE_PLATE) && !held) {
            if (it.wideLabel == null) it.wideLabel = TextUtils.ellipsize(it.label, tileText, w + dp(24), TextUtils.TruncateAt.END);
            tileText.setColor(Theme.alpha(Theme.lerp(Theme.TEXT2, Theme.WHITE, hz), mul((int) (255 * Theme.smooth(0.6f, 1f, full)), al)));
            c.drawText(it.wideLabel, 0, it.wideLabel.length(), l + w / 2f, y + h + dp(22), tileText);
        }
    }

    /** The collapsed look: a quiet plate with the app's icon. */
    private void drawSmall(Canvas c, Item it, RectF r, float rad, int al, float hz, float dim) {
        fill.setColor(Theme.alpha(Theme.S2, al));
        c.drawRoundRect(r, rad, rad, fill);
        Bitmap icon = it.icon != null ? it.icon : it.art;
        if (icon == null) return;
        float sz = Math.min(r.width(), r.height()) * 0.66f;
        tmp.set(r.centerX() - sz / 2f, r.centerY() - sz / 2f, r.centerX() + sz / 2f, r.centerY() + sz / 2f);
        if (it.shape == SHAPE_POSTER) tmp.set(r);
        blit(c, icon, tmp, al, hz, dim);
    }

    /** The open look, by tile shape. Content always keeps its own colours. */
    private void drawFull(Canvas c, Shelf sh, Item it, RectF r, float rad, int al, float hz, float dim) {
        switch (it.shape) {
            case SHAPE_SQUARE_PLATE: {
                fill.setColor(Theme.alpha(Theme.S2, al));
                c.drawRoundRect(r, rad, rad, fill);
                if (it.art != null) {
                    float sz = Math.min(r.width(), r.height()) * 0.62f;
                    tmp.set(r.centerX() - sz / 2f, r.centerY() - sz / 2f, r.centerX() + sz / 2f, r.centerY() + sz / 2f);
                    blit(c, it.art, tmp, al, hz, dim);
                }
                break;
            }
            default:
                if (it.art != null) blit(c, it.art, r, al, hz, dim);
                else {
                    fill.setColor(Theme.alpha(Theme.S2, al));
                    c.drawRoundRect(r, rad, rad, fill);
                }
        }
    }

    /** Draws content in its own colours, darkened by dim (hierarchy, never desaturation: docs/design spec D4). */
    private void blit(Canvas c, Bitmap b, RectF dst, int al, float hz, float dim) {
        int level = Math.max(0, Math.min(15, Math.round(dim * 15f)));
        bmp.setColorFilter(level == 0 ? null : DIM[level]);
        bmp.setAlpha(al);
        c.drawBitmap(b, null, dst, bmp);
        bmp.setColorFilter(null);
        bmp.setAlpha(255);
    }

    /** Dashed outline of the slot the held tile will land in. */
    private void drawSlot(Canvas c, float l, float y, float w, float h, Item it) {
        float gap = dp(4);
        tmp.set(l - gap, y - gap, l + w + gap, y + h + gap);
        float r = radiusFor(it, w, h) + gap;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Theme.alpha(Theme.WHITE, 200));
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(10), dp(8)}, 0));
        c.drawRoundRect(tmp, r, r, stroke);
        stroke.setPathEffect(null);
    }

    /** Chevrons beside the held tile showing which ways it can still move. */
    private void drawArrows(Canvas c, float l, float y, float w, float h, boolean left, boolean right) {
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(3));
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setColor(Theme.WHITE);
        float cy = y + h / 2f - dp(Theme.FOCUS_LIFT) * 2f, s = dp(9);
        if (left) {
            float x = l - dp(26);
            c.drawLine(x + s, cy - s, x, cy, stroke);
            c.drawLine(x, cy, x + s, cy + s, stroke);
        }
        if (right) {
            float x = l + w + dp(26);
            c.drawLine(x - s, cy - s, x, cy, stroke);
            c.drawLine(x, cy, x - s, cy + s, stroke);
        }
        stroke.setStrokeCap(Paint.Cap.BUTT);
    }

    private static int mul(int a, int al) { return a * al / 255; }
}
