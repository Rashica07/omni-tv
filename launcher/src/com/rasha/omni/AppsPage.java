package com.rasha.omni;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/**
 * Apps: a landing page of large category cards, then the same shelves as everywhere else inside a category. Categories come only from
 * evidence the platform gives (see {@link CategoryRules}); an app that has none is still under All apps. Every installed app is at most
 * three presses from the landing page.
 */
final class AppsPage extends Page implements ShelfStack.Listener, CategoryGrid.Listener {
    private static final int CHUNK = 8;

    private final FrameLayout stage;
    private final TextView empty;
    private final TextView title;
    private final TextView meta;
    private final ShelfStack stack;
    private final CategoryGrid grid;
    private boolean shown;
    private String openKey;          // null = landing page
    private String lastKey;          // remembered so Back lands on the card we came from

    AppsPage(Host h) {
        super(h);
        stage = new FrameLayout(h.context());
        stage.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(454)));
        LinearLayout text = new LinearLayout(h.context());
        text.setOrientation(LinearLayout.VERTICAL);
        title = Theme.text(new TextView(h.context()), 48, Theme.TEXT, 800);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.02f);
        text.addView(title);
        meta = Theme.text(new TextView(h.context()), 18, Theme.TEXT2, 500);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = dp(8);
        text.addView(meta, ml);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(900), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = dp(Theme.SAFE_SIDE);
        tp.topMargin = dp(2);
        stage.addView(text, tp);

        grid = new CategoryGrid(h.context(), this);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330), Gravity.TOP);
        gp.topMargin = dp(112);
        stage.addView(grid, gp);

        stack = new ShelfStack(h.context(), this);
        stack.logName = "Apps";
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300), Gravity.TOP);
        sp.topMargin = dp(142);
        stage.addView(stack, sp);
        stack.setVisibility(View.GONE);

        empty = emptyMessage();
        empty.setVisibility(View.GONE);
        col.addView(stage);
        col.addView(empty);
    }

    @Override String hint() {
        if (host.repo().ordered().isEmpty()) return "";
        return openKey == null ? "OK to open   Back to leave" : "Hold OK for options   Back to categories";
    }

    @Override boolean handleBack() {
        if (openKey == null) return false;
        showLanding(true);
        return true;
    }

    @Override void refresh(String fp, String fl) {
        AppRepo repo = host.repo();
        if (repo.ordered().isEmpty()) {
            stage.setVisibility(View.GONE);
            empty.setVisibility(View.VISIBLE);
            host.hintChanged();
            return;
        }
        stage.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        List<CategoryGrid.Card> cards = buildCards();
        // a category that emptied (last app hidden, uninstalled...) sends us back to the landing page
        boolean stillThere = false;
        for (CategoryGrid.Card c : cards) if (c.key.equals(openKey)) stillThere = true;
        if (openKey != null && !stillThere) openKey = null;
        grid.setCards(cards, openKey != null ? openKey : lastKey);
        if (openKey == null) {
            grid.setVisibility(View.VISIBLE);
            stack.setVisibility(View.GONE);
            summary(cards);
            if (fp != null) focusSoon(grid);
        } else {
            fillCategory(openKey, fp, fl);
        }
        host.hintChanged();
    }

    @Override void focusFirst() { focusSoon(openKey == null ? grid : stack); }

    @Override void onShown() {
        shown = true;
        grid.replay();
        if (openKey == null) host.ambient().setGlow(null);
    }

    @Override void onHidden() { shown = false; }

    // ------------------------------------------------------------ data

    private List<AppRepo.App> appsOf(String key) {
        AppRepo repo = host.repo();
        switch (key) {
            case "fav": return repo.favorites();
            case "rec": return repo.continueRow();
            case "games": return repo.byCategory(CategoryRules.GAMES);
            case "stream": return repo.byCategory(CategoryRules.STREAMING);
            case "music": return repo.byCategory(CategoryRules.MUSIC);
            case "tools": return repo.byCategory(CategoryRules.TOOLS);
            case "hid": return repo.hiddenApps();
            default: return repo.alphabetical();
        }
    }

    private List<CategoryGrid.Card> buildCards() {
        List<CategoryGrid.Card> out = new ArrayList<>();
        add(out, "fav", "Favorites", CategoryGrid.G_STAR);
        add(out, "rec", "Recently opened", CategoryGrid.G_CLOCK);
        add(out, "games", CategoryRules.name(CategoryRules.GAMES), CategoryGrid.G_GAMES);
        add(out, "stream", CategoryRules.name(CategoryRules.STREAMING), CategoryGrid.G_PLAY);
        add(out, "music", CategoryRules.name(CategoryRules.MUSIC), CategoryGrid.G_MUSIC);
        add(out, "tools", CategoryRules.name(CategoryRules.TOOLS), CategoryGrid.G_TOOLS);
        add(out, "all", "All apps", CategoryGrid.G_ALL);
        add(out, "hid", "Hidden", CategoryGrid.G_HIDDEN);
        return out;
    }

    private void add(List<CategoryGrid.Card> out, String key, String name, int glyph) {
        List<AppRepo.App> apps = appsOf(key);
        if (apps.isEmpty()) return;
        CategoryGrid.Card c = new CategoryGrid.Card(key, name, glyph);
        c.count = apps.size();
        for (AppRepo.App a : apps) {
            if (c.icons.size() == 4) break;
            c.icons.add(a.art.icon);
            c.self.add(a.art.iconSelf);
        }
        out.add(c);
    }

    private void summary(List<CategoryGrid.Card> cards) {
        title.setText("Apps");
        int total = host.repo().visible().size();
        meta.setText(total + (total == 1 ? " app" : " apps") + " installed");
    }

    // ------------------------------------------------------------ landing <-> category

    private void showLanding(boolean animate) {
        lastKey = openKey;
        openKey = null;
        List<CategoryGrid.Card> cards = buildCards();
        grid.setCards(cards, lastKey);
        summary(cards);
        stack.setVisibility(View.GONE);
        grid.setVisibility(View.VISIBLE);
        if (animate) grid.replay();
        focusSoon(grid);
        host.hintChanged();
    }

    private void fillCategory(String key, String fp, String fl) {
        List<ShelfStack.Shelf> shelves = new ArrayList<>();
        String name = key;
        for (CategoryGrid.Card c : buildCards()) if (c.key.equals(key)) name = c.name;
        List<AppRepo.App> apps = appsOf(key);
        // short categories are one shelf; long ones are cut into A to Z shelves of eight so any app is a few presses away
        if (apps.size() <= CHUNK || key.equals("fav") || key.equals("rec")) {
            shelves.add(new ShelfStack.Shelf("c-" + key, name, ShelfStack.KIND_APP, AppItems.list(apps)));
        } else {
            List<AppRepo.App> sorted = new ArrayList<>(apps);
            java.util.Collections.sort(sorted, (x, y) -> x.label.compareToIgnoreCase(y.label));
            for (int i = 0, k = 0; i < sorted.size(); i += CHUNK, k++) {
                List<AppRepo.App> part = sorted.subList(i, Math.min(sorted.size(), i + CHUNK));
                shelves.add(new ShelfStack.Shelf("c-" + key + k, name + " " + range(part), ShelfStack.KIND_APP, AppItems.list(new ArrayList<>(part))));
            }
        }
        stack.setShelves(shelves, fp, fl);
    }

    private static String range(List<AppRepo.App> part) {
        char a = initial(part.get(0)), z = initial(part.get(part.size() - 1));
        return a == z ? String.valueOf(a) : a + " to " + z;
    }

    private static char initial(AppRepo.App app) {
        char c = app.label.isEmpty() ? '#' : Character.toUpperCase(app.label.charAt(0));
        return Character.isLetterOrDigit(c) ? c : '#';
    }

    // ------------------------------------------------------------ CategoryGrid.Listener

    @Override public void onFocus(CategoryGrid.Card c, int index) {
        if (!shown || openKey != null) return;
        title.animate().cancel();
        title.setText(c.name);
        title.setAlpha(0f);
        title.setTranslationY(dp(16));
        title.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.EXPO).start();
        meta.setText(c.count + (c.count == 1 ? " app" : " apps"));
    }

    @Override public void onOpen(CategoryGrid.Card c) {
        openKey = c.key;
        lastKey = c.key;
        OmniLog.state("category open=" + c.key);
        grid.setVisibility(View.GONE);
        stack.setVisibility(View.VISIBLE);
        fillCategory(c.key, null, null);
        title.setText(c.name);
        meta.setText(c.count + (c.count == 1 ? " app" : " apps"));
        focusSoon(stack);
        host.hintChanged();
    }

    // ------------------------------------------------------------ ShelfStack.Listener

    @Override public void onFocus(ShelfStack.Item it, ShelfStack.Shelf shelf, int index) {
        if (!shown || openKey == null) return;
        bloom(stack, it);
        AppRepo.App a = AppItems.app(it);
        int n = host.repo().count(a.pkg);
        meta.setText(n == 0 ? "Not opened yet" : "Opened " + n + (n == 1 ? " time" : " times"));
        int len = a.label.length();
        title.setTextSize(len <= 10 ? 48 : len <= 18 ? 42 : 34);
        title.animate().cancel();
        title.setText(a.label);
        title.setAlpha(0f);
        title.setTranslationY(dp(16));
        title.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.EXPO).start();
    }

    @Override public void onOpen(ShelfStack.Item it, ShelfStack.Shelf shelf, int x, int y, int w, int h) {
        host.launchAt(stack, x, y, w, h, AppItems.app(it), shelf.id);
    }

    @Override public void onMenu(ShelfStack.Item it, ShelfStack.Shelf shelf) { host.appMenu(AppItems.app(it), shelf.id); }

    @Override public void onMoveEnd(ShelfStack.Shelf shelf, ShelfStack.Item it, boolean placed, List<String> order) { }

    @Override public void onLeaveUp() { host.focusNav(); }

    @Override public void want(ShelfStack.Item it, ShelfStack.Shelf shelf) { }
}
