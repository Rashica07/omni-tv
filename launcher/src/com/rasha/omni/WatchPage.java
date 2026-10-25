package com.rasha.omni;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.rasha.omni.Theme.dp;

/**
 * Watch: the shelf grammar applied to TMDB discovery. Rows are "included with a subscription in Italy" only
 * (flatrate), per-title availability comes from TMDB watch-provider data (JustWatch) and is shown only when
 * the data says so. Loading, offline and empty states are explicit.
 */
final class WatchPage extends Page implements ShelfStack.Listener {
    private static final String IT = "&watch_region=IT&sort_by=popularity.desc&with_watch_monetization_types=flatrate";
    /** id, heading, endpoint, streaming-app package for provider rows (or null), app name */
    private static final String[][] ROWS = {
            {"trend", "Trending this week", "/trending/all/week", null, null},
            {"nflx", "On Netflix", "/discover/tv?with_watch_providers=8" + IT, "com.netflix.ninja", "Netflix"},
            {"dis", "On Disney+", "/discover/movie?with_watch_providers=337" + IT, "com.disney.disneyplus", "Disney+"},
            {"prime", "On Prime Video", "/discover/movie?with_watch_providers=119" + IT, "com.amazon.amazonvideo.livingroom", "Prime Video"},
            {"series", "Popular series", "/tv/popular", null, null}};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final FrameLayout stage;
    private final TextView title;
    private final TextView meta;
    private final TextView overview;
    private final TextView avail;
    private final TextView status;
    private final TextView retry;
    private final ShelfStack stack;
    private final Map<String, Tmdb.Providers> providers = new HashMap<>();
    private final Map<String, ShelfStack.Shelf> byId = new HashMap<>();
    private ShelfStack.Item focused;
    private boolean built;
    private boolean visible;
    private int pending;
    private int populated;
    private long lastLoad;
    private final Runnable settle = this::settled;
    private final List<ShelfStack.Shelf> cinemaShelves = new ArrayList<>();
    private final List<ShelfStack.Shelf> tmdbShelves = new ArrayList<>();

    WatchPage(Host h) {
        super(h);
        stage = new FrameLayout(h.context());
        stage.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(454)));

        LinearLayout text = new LinearLayout(h.context());
        text.setOrientation(LinearLayout.VERTICAL);
        title = Theme.text(new TextView(h.context()), 40, Theme.TEXT, 800);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setLetterSpacing(-0.02f);
        text.addView(title);
        meta = Theme.text(new TextView(h.context()), 17, Theme.TEXT2, 500);
        text.addView(meta, lp(4));
        overview = Theme.text(new TextView(h.context()), 15, Theme.TEXT2, 400);
        overview.setSingleLine(false);
        overview.setMaxLines(2);
        overview.setEllipsize(TextUtils.TruncateAt.END);
        overview.setLineSpacing(0, 1.12f);
        text.addView(overview, lp(6));
        avail = Theme.text(new TextView(h.context()), 14, Theme.TEXT2, 500);
        text.addView(avail, lp(6));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(700),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = dp(Theme.SAFE_SIDE);
        tp.topMargin = dp(2);
        stage.addView(text, tp);

        status = Theme.text(new TextView(h.context()), 22, Theme.TEXT2, 500);
        status.setSingleLine(false);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(620),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        sp.leftMargin = dp(Theme.SAFE_SIDE);
        sp.topMargin = dp(70);
        stage.addView(status, sp);

        retry = Theme.text(new TextView(h.context()), 18, Theme.TEXT, 650);
        retry.setText("Try again");
        retry.setGravity(Gravity.CENTER);
        retry.setPadding(dp(22), 0, dp(22), 0);
        retry.setFocusable(true);
        retry.setClickable(true);
        android.graphics.drawable.GradientDrawable idle = new android.graphics.drawable.GradientDrawable();
        idle.setCornerRadius(dp(Theme.RADIUS));
        idle.setColor(Theme.S3);
        retry.setBackground(idle);
        retry.setOnFocusChangeListener((v, has) -> {
            android.graphics.drawable.GradientDrawable g = (android.graphics.drawable.GradientDrawable) v.getBackground();
            g.setCornerRadius(dp(Theme.RADIUS));
            g.setColor(has ? Theme.WHITE : Theme.S3);
            ((TextView) v).setTextColor(has ? Theme.BLACK : Theme.TEXT);
        });
        retry.setOnClickListener(v -> reload());
        retry.setVisibility(View.GONE);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46),
                Gravity.TOP | Gravity.START);
        rp.leftMargin = dp(Theme.SAFE_SIDE);
        rp.topMargin = dp(150);
        stage.addView(retry, rp);

        stack = new ShelfStack(h.context(), this);
        stack.logName = "Watch";
        FrameLayout.LayoutParams stp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(288), Gravity.TOP);
        stp.topMargin = dp(160);
        stage.addView(stack, stp);
        col.addView(stage);
    }

    private static LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        l.topMargin = dp(topDp);
        return l;
    }

    @Override String hint() { return stack.getVisibility() == View.VISIBLE && !stack.shelves().isEmpty() ? "OK to open   Hold OK for details" : ""; }

    @Override void refresh(String fp, String fl) { }

    @Override void focusFirst() { focusSoon(stack.getVisibility() == View.VISIBLE ? stack : retry); }

    @Override void onShown() {
        visible = true;
        host.ambient().setGlow(null);
        OmniLog.state("watch shown built=" + built + " age_ms=" + (android.os.SystemClock.uptimeMillis() - lastLoad));
        if (!built || android.os.SystemClock.uptimeMillis() - lastLoad > 30000) {       // new films and changed sources show up without a restart
            built = true;
            reload();
        } else if (focused != null) {
            environment(focused);
        }
    }

    @Override void onHidden() {
        visible = false;
        ui.removeCallbacks(settle);
        host.ambient().setBackdrop(null);
    }

    // ------------------------------------------------------------ loading

    private void reload() {
        lastLoad = android.os.SystemClock.uptimeMillis();
        title.setText("");
        meta.setText("");
        overview.setText("");
        avail.setText("");
        retry.setVisibility(View.GONE);
        stack.setVisibility(View.VISIBLE);
        byId.clear();
        cinemaShelves.clear();
        tmdbShelves.clear();
        final boolean tmdb = Tmdb.enabled();
        OmniLog.state("watch state=loading tmdb=" + tmdb);
        status.setText("Loading what's on…");
        pending = (tmdb ? ROWS.length : 0) + 1;      // + OMNI Cinema
        populated = 0;
        if (tmdb) for (int i = 0; i < ROWS.length; i++) {
            final String[] def = ROWS[i];
            ShelfStack.Shelf s = new ShelfStack.Shelf(def[0], def[1], ShelfStack.KIND_POSTER, new ArrayList<>());
            tmdbShelves.add(s);
            byId.put(def[0], s);
        }
        stack.setShelves(new ArrayList<>(), null, null);
        final android.content.Context ctx = host.context();
        new Thread(() -> {                           // Cinema's provider is read off the main thread; the launcher never waits for it
            final CinemaBridge.Result r = CinemaBridge.load(ctx);
            ui.post(() -> cinemaLoaded(r));
        }, "watch-cinema").start();
        if (tmdb) for (final String[] def : ROWS) host.tmdb().load(def[2], list -> loaded(def, list, tmdbShelves));
    }

    private void cinemaLoaded(CinemaBridge.Result r) {
        rebuildCinema(r);
        pending--;
        finishLoading();
    }

    private void rebuildCinema(CinemaBridge.Result r) {
        OmniLog.state("watch cinema state=" + r.state + " shelves=" + r.shelves.size());
        cinemaShelves.clear();
        populated = 0;
        for (CinemaBridge.Shelf sh : r.shelves) {
            ShelfStack.Shelf s = new ShelfStack.Shelf("cin:" + sh.id, sh.name, ShelfStack.KIND_POSTER, new ArrayList<>());
            for (CinemaBridge.Title t : sh.items) {
                ShelfStack.Item it = new ShelfStack.Item("c:" + sh.id + ":" + t.id, t.title);
                it.data = t;
                it.shape = ShelfStack.SHAPE_POSTER;
                s.items.add(it);
            }
            cinemaShelves.add(s);
            populated++;
        }
        // one honest card says what Cinema is doing: not installed, not answering, empty, or the way in
        CinemaBridge.Title c = new CinemaBridge.Title();
        c.id = "omni:app:cinema"; c.kind = "app"; c.title = "OMNI Cinema";
        if (r.state == CinemaBridge.NOT_INSTALLED) { c.sourceLabel = "Not installed"; c.overview = r.detail + " It adds your own films, USB drives and the OMNI server to this page."; }
        else if (r.state != CinemaBridge.OK) { c.sourceLabel = "Unavailable"; c.overview = r.detail; }
        else if (r.shelves.isEmpty()) { c.sourceLabel = "No films yet"; c.overview = "Open Cinema and add a source: the OMNI server, a USB drive, or free public-domain films."; }
        else { c.sourceLabel = "Open Cinema"; c.overview = "Your whole library, sources and settings."; }
        c.availability = r.state == CinemaBridge.OK ? "PLAYABLE" : "UNAVAILABLE";
        ShelfStack.Shelf ex = new ShelfStack.Shelf("cin:explore", "Explore Cinema", ShelfStack.KIND_POSTER, new ArrayList<>());
        ShelfStack.Item it = new ShelfStack.Item("c:explore", c.title);
        it.data = c;
        it.shape = ShelfStack.SHAPE_POSTER;
        ex.items.add(it);
        cinemaShelves.add(ex);
    }

    /** Coming back from Cinema (or any app): re-read only Cinema's shelves, so progress and new films show, and keep the focus where it was. */
    @Override void onResume() {
        if (!visible || !built || pending > 0) return;
        if (android.os.SystemClock.uptimeMillis() - lastLoad < 3000) return;
        lastLoad = android.os.SystemClock.uptimeMillis();
        final android.content.Context ctx = host.context();
        new Thread(() -> {
            final CinemaBridge.Result r = CinemaBridge.load(ctx);
            ui.post(() -> { if (!visible) return; rebuildCinema(r); publish(); });
        }, "watch-cinema-refresh").start();
    }

    private void loaded(String[] def, List<Tmdb.Title> list, List<ShelfStack.Shelf> all) {
        pending--;
        ShelfStack.Shelf s = byId.get(def[0]);
        if (s != null) {
            for (Tmdb.Title t : list) {
                String id = (t.tv ? "t" : "m") + t.id;
                ShelfStack.Item it = new ShelfStack.Item(id, t.title);
                it.data = new Row2(t, def[3], def[4]);
                it.shape = ShelfStack.SHAPE_POSTER;
                s.items.add(it);
            }
            if (!list.isEmpty()) populated++;
        }
        finishLoading();
    }

    private void finishLoading() {
        if (pending > 0) return;
        boolean any = false;
        for (ShelfStack.Shelf x : tmdbShelves) if (!x.items.isEmpty()) any = true;
        for (ShelfStack.Shelf x : cinemaShelves) if (x.items.size() > 0 && !"cin:explore".equals(x.id)) any = true;
        if (!any && Tmdb.enabled()) {
            OmniLog.state("watch state=offline");
            showMessage("Can't reach TMDB", "Check the TV's internet connection, then try again.", true);
            return;
        }
        OmniLog.state("watch state=loaded rows=" + populated);
        status.setText("");
        publish();
    }

    private void publish() {
        List<ShelfStack.Shelf> shown = new ArrayList<>();
        for (ShelfStack.Shelf x : cinemaShelves) if (!"cin:explore".equals(x.id)) shown.add(x);      // the films you own come first
        for (ShelfStack.Shelf x : tmdbShelves) if (!x.items.isEmpty()) shown.add(x);
        for (ShelfStack.Shelf x : cinemaShelves) if ("cin:explore".equals(x.id)) shown.add(x);
        stack.setShelves(shown, null, null);
        host.hintChanged();
        if (visible && !host.navHasFocus()) focusSoon(stack);
    }

    private void showMessage(String head, String body, boolean canRetry) {
        stack.setVisibility(View.GONE);
        title.setText(head);
        meta.setText("");
        overview.setText("");
        avail.setText("");
        status.setText(body);
        retry.setVisibility(canRetry ? View.VISIBLE : View.GONE);
        host.hintChanged();
        if (canRetry && visible && !host.navHasFocus()) focusSoon(retry);
    }

    /** Pairs a TMDB title with the streaming app of the row it appeared in (null for open lists). */
    private static final class Row2 {
        final Tmdb.Title t;
        final String pkg;
        final String appName;

        Row2(Tmdb.Title t, String pkg, String appName) {
            this.t = t;
            this.pkg = pkg;
            this.appName = appName;
        }
    }

    // ------------------------------------------------------------ ShelfStack.Listener

    @Override public void want(final ShelfStack.Item it, ShelfStack.Shelf shelf) {
        if (it.data instanceof CinemaBridge.Title) { wantCinema(it, (CinemaBridge.Title) it.data); return; }
        final Tmdb.Title t = ((Row2) it.data).t;
        host.images().load("w185", t.poster, (Bitmap b) -> {
            if (b == null) { it.requested &= ~1; return; }
            it.art = IconArt.rounded(b, 12f * Theme.density * (b.getWidth() / (120f * Theme.density)));
            stack.invalidate();
        });
    }

    @Override public void onFocus(ShelfStack.Item it, ShelfStack.Shelf shelf, int index) {
        focused = it;
        if (it.data instanceof CinemaBridge.Title) { focusCinema(it, (CinemaBridge.Title) it.data); return; }
        Tmdb.Title t = ((Row2) it.data).t;
        title.setTextSize(t.title.length() <= 22 ? 40 : 32);
        title.animate().cancel();
        title.setText(t.title);
        title.setAlpha(0f);
        title.setTranslationY(dp(16));
        title.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.EXPO).start();
        meta.setText((t.year.isEmpty() ? "" : t.year + "    ") + String.format(Locale.US, "★ %.1f", t.rating)
                + "    " + (t.tv ? "Series" : "Movie"));
        overview.setText(t.overview);
        avail.setText("");
        // heavy or networked work waits for the focus to settle, so a held key never queues requests
        ui.removeCallbacks(settle);
        ui.postDelayed(settle, 160);
    }

    private void settled() {
        final ShelfStack.Item it = focused;
        if (it == null || !visible) return;
        environment(it);
        if (it.data instanceof CinemaBridge.Title) return;       // availability for these comes from Cinema itself, not from TMDB
        final Row2 r = (Row2) it.data;
        Tmdb.Providers known = providers.get(it.id);
        if (known != null) { showAvailability(it, known); return; }
        host.tmdb().loadProviders(r.t.tv, r.t.id, p -> {
            providers.put(it.id, p);
            if (focused == it) showAvailability(it, p);
        });
    }

    private void environment(ShelfStack.Item it) {
        final String bd = it.data instanceof CinemaBridge.Title ? ((CinemaBridge.Title) it.data).backdrop : ((Row2) it.data).t.backdrop;
        if (bd == null) { if (it.data instanceof CinemaBridge.Title) host.ambient().setBackdrop(null); return; }
        host.images().loadBackdrop(bd, (Bitmap b) -> {
            if (b != null && focused == it && visible) host.ambient().setBackdrop(b);
        });
    }

    private void showAvailability(ShelfStack.Item it, Tmdb.Providers p) {
        if (!p.known) { avail.setText(""); return; }   // no data: say nothing rather than guess
        String main = p.flatrate.isEmpty() ? "No subscription streaming listed in Italy"
                : "Included with " + TextUtils.join(", ", p.flatrate);
        // TMDB's terms require JustWatch attribution next to this data; it is set quieter than the data itself
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder(main + "    JustWatch");
        int st = main.length() + 4;
        sb.setSpan(new android.text.style.ForegroundColorSpan(Theme.TEXT3), st, sb.length(), 0);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.86f), st, sb.length(), 0);
        avail.setText(sb);
    }

    /** OK launches the title in the app that has it; hold OK opens the details sheet. Falls back to the sheet when no app is known. */
    @Override public void onOpen(ShelfStack.Item it, ShelfStack.Shelf shelf, int x, int y, int w, int h) {
        if (it.data instanceof CinemaBridge.Title) { openCinema((CinemaBridge.Title) it.data, true); return; }
        if (!launchTitle(it)) detail(it);
    }

    private boolean launchTitle(ShelfStack.Item it) {
        final Row2 r = (Row2) it.data;
        final android.content.Context ctx = host.context();
        Tmdb.Providers p = providers.get(it.id);
        boolean netflix = Deeplinks.NETFLIX_PKG.equals(r.pkg) || (p != null && p.known && p.flatrate.contains("Netflix"));
        if (!netflix || ctx.getPackageManager().getLaunchIntentForPackage(Deeplinks.NETFLIX_PKG) == null) return false;
        final String title = r.t.title;
        new Thread(() -> {
            // a direct title link when Netflix's own rows tell us the id, otherwise Netflix opens on this title's search results
            String uri = Deeplinks.netflixSearch(title);
            try {
                if (WatchNext.granted(ctx)) for (WatchNext.Entry e : WatchNext.query(ctx)) {
                    if (Deeplinks.NETFLIX_PKG.equals(e.pkg) && e.title != null && e.title.equalsIgnoreCase(title)) {
                        long id = Deeplinks.netflixIdFrom(e.intentUri);
                        if (id > 0) { uri = Deeplinks.netflixTitle(id); break; }
                    }
                }
            } catch (RuntimeException ignored) { }
            final String target = uri;
            ui.post(() -> {
                try {
                    OmniLog.state("watch open netflix target=" + (target.startsWith("nflx") ? "search" : "title"));
                    ctx.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target)).setPackage(Deeplinks.NETFLIX_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception e) {
                    Toast.makeText(ctx, "Can't open Netflix", Toast.LENGTH_SHORT).show();
                }
            });
        }, "watch-open").start();
        return true;
    }

    @Override public void onMenu(ShelfStack.Item it, ShelfStack.Shelf shelf) {
        if (it.data instanceof CinemaBridge.Title) { cinemaSheet((CinemaBridge.Title) it.data); return; }
        detail(it);
    }

    @Override public void onMoveEnd(ShelfStack.Shelf shelf, ShelfStack.Item it, boolean placed, java.util.List<String> order) { }

    @Override public void onLeaveUp() { host.focusNav(); }

    private void detail(ShelfStack.Item it) {
        final Row2 r = (Row2) it.data;
        final Tmdb.Title t = r.t;
        final android.content.Context ctx = host.context();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        Tmdb.Providers p = providers.get(it.id);
        // an "Open" action only when the data supports it: the row's own filter, or the provider lookup
        String pkg = r.pkg, name = r.appName;
        if (pkg == null && p != null && p.known) {
            if (p.flatrate.contains("Netflix")) { pkg = "com.netflix.ninja"; name = "Netflix"; }
            else if (p.flatrate.contains("Disney Plus")) { pkg = "com.disney.disneyplus"; name = "Disney+"; }
            else if (p.flatrate.contains("Amazon Prime Video")) { pkg = "com.amazon.amazonvideo.livingroom"; name = "Prime Video"; }
        }
        final Intent launch = pkg == null ? null : ctx.getPackageManager().getLaunchIntentForPackage(pkg);
        final String fpkg = pkg;
        if (launch != null) {
            labels.add("Open " + name);
            acts.add(() -> host.closeSheet(() -> {
                try { ctx.startActivity(launch); }
                catch (Exception e) { Toast.makeText(ctx, "Can't open " + fpkg, Toast.LENGTH_SHORT).show(); }
            }));
        }
        labels.add("Close");
        acts.add(() -> host.closeSheet(null));
        String head = (t.year.isEmpty() ? "" : t.year + "   ") + String.format(Locale.US, "★ %.1f", t.rating)
                + "   " + (t.tv ? "Series" : "Movie");
        host.showSheet(t.title, head + (t.overview.isEmpty() ? "" : "\n" + t.overview), labels, acts);
    }
    // ------------------------------------------------------------ OMNI Cinema titles

    private void wantCinema(final ShelfStack.Item it, final CinemaBridge.Title t) {
        if (t.poster == null) { it.art = cinemaCard(t); stack.invalidate(); return; }
        host.images().load("w185", t.poster, (Bitmap b) -> {
            if (b == null) { it.requested &= ~1; return; }
            Bitmap base = IconArt.rounded(b, 12f * Theme.density * (b.getWidth() / (120f * Theme.density)));
            it.art = t.durationMs > 0 && t.positionMs > 0 && !t.watched ? withProgress(base, t.positionMs / (float) t.durationMs) : base;
            stack.invalidate();
        });
    }

    /** A thin white resume bar baked into the poster, so drawing stays as cheap as any other poster. */
    private static Bitmap withProgress(Bitmap src, float f) {
        Bitmap b = src.copy(Bitmap.Config.ARGB_8888, true);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        float h = Math.max(4f, b.getHeight() * 0.025f), m = b.getWidth() * 0.08f, y = b.getHeight() - m - h;
        p.setColor(0x99000000);
        c.drawRoundRect(m, y, b.getWidth() - m, y + h, h / 2f, h / 2f, p);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(m, y, m + (b.getWidth() - 2 * m) * Math.max(0.03f, Math.min(1f, f)), y + h, h / 2f, h / 2f, p);
        return b;
    }

    /** A plain card for titles that have no poster and for the Cinema entry itself: name and state, nothing invented. */
    private static Bitmap cinemaCard(CinemaBridge.Title t) {
        int w = 185, h = 278;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(Theme.S2);
        android.text.TextPaint tp = new android.text.TextPaint(android.text.TextPaint.ANTI_ALIAS_FLAG);
        tp.setTypeface(Theme.geist);
        tp.setColor(Theme.WHITE);
        tp.setTextSize(26);
        if (android.os.Build.VERSION.SDK_INT >= 26) tp.setFontVariationSettings("'wght' 750");
        android.text.StaticLayout nl = new android.text.StaticLayout(t.title, tp, w - 28, android.text.Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        c.save(); c.translate(14, h - 34 - nl.getHeight() - 22); nl.draw(c); c.restore();
        tp.setTextSize(15); tp.setColor(Theme.TEXT2);
        if (android.os.Build.VERSION.SDK_INT >= 26) tp.setFontVariationSettings("'wght' 500");
        String sub = t.sourceLabel == null ? "" : t.sourceLabel;
        c.drawText(sub, 14, h - 20, tp);
        return IconArt.rounded(b, 12f * Theme.density * (b.getWidth() / (120f * Theme.density)));
    }

    private void focusCinema(ShelfStack.Item it, CinemaBridge.Title t) {
        title.setTextSize(t.title.length() <= 22 ? 40 : 32);
        title.animate().cancel();
        title.setText(t.title);
        title.setAlpha(0f);
        title.setTranslationY(dp(16));
        title.animate().alpha(1f).translationY(0).setDuration(420).setInterpolator(Theme.EXPO).start();
        StringBuilder m = new StringBuilder();
        if ("app".equals(t.kind)) m.append(t.sourceLabel);
        else {
            if (t.year > 0) m.append(t.year).append("    ");
            if (t.rating > 0) m.append(String.format(Locale.US, "★ %.1f    ", t.rating));
            m.append("show".equals(t.kind) ? "Series" : "Movie").append("    ").append(t.sourceLabel);
        }
        meta.setText(m);
        overview.setText(t.overview == null ? "" : t.overview);
        avail.setText(cinemaStatus(t));
        ui.removeCallbacks(settle);
        ui.postDelayed(settle, 160);
    }

    /** What is true right now about this title, in words; never "available" unless Cinema said it is. */
    private static String cinemaStatus(CinemaBridge.Title t) {
        if ("app".equals(t.kind)) return "";
        String base;
        if (t.watched) base = "Watched";
        else if (t.positionMs > 0 && t.durationMs > 0) base = "Resume at " + CinemaBridge.clock(t.positionMs) + " of " + CinemaBridge.clock(t.durationMs);
        else base = "";
        String a = t.playable() ? "Ready to play" : "OFFLINE".equals(t.availability) ? "Source not reachable right now" : "ACCOUNT".equals(t.availability) ? "Needs a sign-in" : "Catalogue entry, no playable file";
        return base.isEmpty() ? a : a + "    " + base;
    }

    /** OK on a title: play it if Cinema says it is playable, otherwise open its page in Cinema, which explains why not. */
    private void openCinema(CinemaBridge.Title t, boolean play) {
        final android.content.Context ctx = host.context();
        String link;
        if ("app".equals(t.kind)) {
            if (!CinemaBridge.installed(ctx)) { cinemaSheet(t); return; }
            link = "omni-cinema://home";
        } else link = play && t.playable() && !"show".equals(t.kind) ? CinemaBridge.playLink(t) : CinemaBridge.titleLink(t);
        OmniLog.state("watch cinema open " + link.substring(0, link.indexOf('/', 14) > 0 ? link.indexOf('/', 14) : link.length()));
        if (!CinemaBridge.open(ctx, link)) Toast.makeText(ctx, "Can't open OMNI Cinema", Toast.LENGTH_SHORT).show();
    }

    private void cinemaSheet(final CinemaBridge.Title t) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        final android.content.Context ctx = host.context();
        if (!"app".equals(t.kind) && t.playable() && !"show".equals(t.kind)) {
            labels.add(t.positionMs > 0 && !t.watched ? "Resume" : "Play");
            acts.add(() -> host.closeSheet(() -> openCinema(t, true)));
        }
        if (!"app".equals(t.kind) || CinemaBridge.installed(ctx)) {
            labels.add("Open in Cinema");
            acts.add(() -> host.closeSheet(() -> openCinema(t, false)));
        }
        labels.add("Close");
        acts.add(() -> host.closeSheet(null));
        String head = "app".equals(t.kind) ? (t.overview == null ? "" : t.overview) : cinemaStatus(t) + (t.overview == null || t.overview.isEmpty() ? "" : "\n" + t.overview);
        host.showSheet(t.title, head, labels, acts);
    }
}
