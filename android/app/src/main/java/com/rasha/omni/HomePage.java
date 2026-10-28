package com.rasha.omni;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.rasha.omni.Theme.dp;

/**
 * Home: a typographic stage over an environment made from the focused app's own art, and below it the
 * shelf stack (Favorites, Recent, Most used) where the featured art lives.
 */
final class HomePage extends Page implements ShelfStack.Listener {
    private final FrameLayout stage;
    private final TextView empty;
    private final TextView title;
    private final TextView meta;
    private final ShelfStack stack;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private List<WatchNext.Entry> watchNext = new ArrayList<>();
    /** True only while Home is the visible tab. */
    private boolean shown;

    HomePage(Host h) {
        super(h);
        stage = new FrameLayout(h.context());
        stage.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(454)));

        LinearLayout text = new LinearLayout(h.context());
        text.setOrientation(LinearLayout.VERTICAL);
        title = Theme.text(new TextView(h.context()), 44, Theme.TEXT, 800);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.02f);
        text.addView(title);
        meta = Theme.text(new TextView(h.context()), 18, Theme.TEXT2, 500);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = dp(8);
        text.addView(meta, ml);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(470),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = dp(Theme.SAFE_SIDE);
        tp.topMargin = dp(10);
        stage.addView(text, tp);

        stack = new ShelfStack(h.context(), this);
        stack.logName = "Home";
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(314), Gravity.TOP);
        sp.topMargin = dp(140);
        stage.addView(stack, sp);

        empty = emptyMessage();
        empty.setVisibility(View.GONE);
        col.addView(stage);
        col.addView(empty);
    }

    @Override String hint() { return host.repo().ordered().isEmpty() ? "" : Tr.t("Hold OK for options"); }

    @Override void refresh(String fp, String fl) {
        if (stack.isMoving() && fp == null) return;     // late data must not undo a move in progress
        AppRepo repo = host.repo();
        if (repo.ordered().isEmpty()) {
            OmniLog.state("home state=empty");
            stage.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            return;
        }
        stage.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        List<ShelfStack.Shelf> shelves = new ArrayList<>();
        add(shelves, "fav", UserMode.simple(host.context()) ? "Quick access" : "Favorites", repo.favorites(), true);
        if (!watchNext.isEmpty()) {
            List<ShelfStack.Item> items = new ArrayList<>();
            for (WatchNext.Entry e : watchNext) {
                ShelfStack.Item it = new ShelfStack.Item("wn" + e.id, e.title);
                it.data = e;
                items.add(it);
            }
            for (ShelfStack.Item wi : items) wi.shape = ShelfStack.SHAPE_POSTER;
            shelves.add(new ShelfStack.Shelf("wn", Tr.t("Continue watching"), ShelfStack.KIND_POSTER, items));
        }
        add(shelves, "rec", Tr.t("Recent"), repo.continueRow(), false);
        add(shelves, "top", Tr.t("Most used"), repo.mostUsed(), false);
        stack.setShelves(shelves, fp, fl);
        stack.setMoving(repo.movingPkg, repo.movingList);
        if (fp != null) focusSoon(stack);
        reloadWatchNext();
    }

    /** Provider I/O stays off the main thread (ANR rule); the shelf appears, changes or disappears when the data does. */
    private void reloadWatchNext() {
        if (!WatchNext.granted(host.context())) return;
        io.execute(() -> {
            final List<WatchNext.Entry> fresh = WatchNext.query(host.context());
            post(() -> {
                if (sameEntries(fresh, watchNext)) return;
                watchNext = fresh;
                refresh(null, null);
            });
        });
    }

    private static boolean sameEntries(List<WatchNext.Entry> a, List<WatchNext.Entry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (a.get(i).id != b.get(i).id) return false;
        return true;
    }

    private static void add(List<ShelfStack.Shelf> out, String id, String name, List<AppRepo.App> apps, boolean reorder) {
        if (apps.isEmpty()) return;
        ShelfStack.Shelf s = new ShelfStack.Shelf(id, Tr.t(name), ShelfStack.KIND_APP, AppItems.list(apps));
        s.reorderable = reorder;
        out.add(s);
    }

    @Override void focusFirst() {
        stack.toStart();
        focusSoon(stack);
    }

    @Override void onShown() {
        shown = true;
        ShelfStack.Item it = stack.current();
        if (it != null) present(it);   // re-sync the stage with the remembered focus
    }

    /** The page's colour wash follows the focused item's artwork. */
    private void wash(ShelfStack.Item it) {
        bloom(stack, it);
    }

    @Override void onHidden() { shown = false; }


    // ------------------------------------------------------------ ShelfStack.Listener

    @Override public boolean onEdge(int dx) { return dx > 0 && host.focusNowPlaying(); }

    @Override public void onFocus(ShelfStack.Item it, ShelfStack.Shelf shelf, int index) {
        if (shown) present(it);
    }

    private void present(ShelfStack.Item it) {
        wash(it);
        String label = it.label;
        if (it.data instanceof WatchNext.Entry) {
            meta.setText("From " + appName(((WatchNext.Entry) it.data).pkg));
        } else {
            int n = host.repo().count(((AppRepo.App) it.data).pkg);
            meta.setText(n == 0 ? Tr.t("Not opened yet") : Tr.t("Opened") + " " + n + " " + Tr.t(n == 1 ? "time" : "times"));
        }
        int len = label.length();
        title.setTextSize(len <= 10 ? 44 : len <= 18 ? 38 : 32);
        title.animate().cancel();
        title.setText(label);
        title.setAlpha(0f);
        title.setTranslationY(dp(20));
        title.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.EXPO).start();
    }

    private String appName(String pkg) {
        try {
            android.content.pm.PackageManager pm = host.context().getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return pkg;
        }
    }

    @Override public void onOpen(ShelfStack.Item it, ShelfStack.Shelf shelf, int x, int y, int w, int h) {
        if (it.data instanceof WatchNext.Entry) {
            WatchNext.Entry e = (WatchNext.Entry) it.data;
            android.content.Intent intent = WatchNext.safeIntent(e);
            if (intent == null) {
                android.widget.Toast.makeText(host.context(), "Can't open that item", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            host.launchIntent(stack, x, y, w, h, intent, e.pkg);
            return;
        }
        host.launchAt(stack, x, y, w, h, AppItems.app(it), shelf.id);
    }

    @Override public void onMenu(ShelfStack.Item it, ShelfStack.Shelf shelf) {
        if (it.data instanceof AppRepo.App) host.appMenu(AppItems.app(it), shelf.id);   // publishers own Watch Next items: nothing to edit
    }

    @Override public void onMoveEnd(ShelfStack.Shelf shelf, ShelfStack.Item it, boolean placed, java.util.List<String> order) {
        OmniLog.state("move-end shelf=" + shelf.id + " item=" + it.id + " placed=" + placed);
        host.repo().movingPkg = null;
        if (placed) host.repo().setFavoritesOrder(order);
        host.moveFinished(it.label, placed);
        host.refreshAll(it.id, shelf.id);
    }

    @Override public void onLeaveUp() { host.focusNav(); }

    @Override public void want(final ShelfStack.Item it, ShelfStack.Shelf shelf) {
        if (!(it.data instanceof WatchNext.Entry)) return;   // app art is already built
        String uri = ((WatchNext.Entry) it.data).poster;
        if (uri == null) return;
        host.images().loadUri(host.context(), uri, (Bitmap b) -> {
            if (b == null) return;
            it.art = IconArt.rounded(b, 12f * Theme.density * (b.getWidth() / (120f * Theme.density)));
            stack.invalidate();
        });
    }
}
