package com.rasha.omni;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.rasha.omni.Theme.dp;

/**
 * TV: live channels as a grid of cards (filters: All, Kosovo, Albania, Antenna, Favorites; sort: Recent, Most watched, A to Z; Search across
 * everything) with the TV's inputs in their own rail on the right. Everything about a channel is resolved by its stable id when it is used.
 * Playback statistics are local to this TV and only count real viewing. Live previews inside cards are not offered: this TV's player cannot
 * show video in an in-page surface (see docs), so cards keep their artwork.
 */
final class TvPage extends Page implements ChannelGrid.Listener {
    private static final int RAIL_W = 232;

    private final FrameLayout stage;
    private final TextView title, meta, note;
    private final TvToolbar toolbar;
    private final ChannelGrid grid;
    private final InputsRail rail;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;

    private List<PtvModel.Channel> channels = new ArrayList<>();
    private final Map<String, PtvModel.Channel> byId = new HashMap<>();
    private List<String> playOrder = new ArrayList<>();
    private List<Inputs.Entry> entries = new ArrayList<>();
    private PtvUsage usage = new PtvUsage();
    private Set<String> favorites = new java.util.LinkedHashSet<>();
    private int filter, sort, shownTotal;
    private boolean shown, loaded, overlayOpen;
    private long lastLoad;
    private String railSignature = "";

    TvPage(Host h) {
        super(h);
        ChannelLogos.init(h.context());
        prefs = h.context().getSharedPreferences("omniptv", Context.MODE_PRIVATE);
        filter = Math.max(0, Math.min(4, prefs.getInt("tv_filter", 0)));
        sort = Math.max(0, Math.min(2, prefs.getInt("tv_sort2", ChannelSort.RECENT)));

        stage = new FrameLayout(h.context());
        stage.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(454)));
        stage.setClipChildren(true);

        LinearLayout text = new LinearLayout(h.context());
        text.setOrientation(LinearLayout.VERTICAL);
        title = Theme.text(new TextView(h.context()), 34, Theme.TEXT, 800);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setSingleLine(true);
        title.setLetterSpacing(-0.02f);
        text.addView(title);
        meta = Theme.text(new TextView(h.context()), 16, Theme.TEXT2, 500);
        meta.setSingleLine(true);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ml.topMargin = dp(2);
        text.addView(meta, ml);
        note = Theme.text(new TextView(h.context()), 13, Theme.TEXT3, 450);
        note.setSingleLine(true);
        note.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = dp(2);
        text.addView(note, nl);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(dp(590), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        tp.leftMargin = dp(Theme.SAFE_SIDE);
        stage.addView(text, tp);

        toolbar = new TvToolbar(h.context(), new TvToolbar.Listener() {
            @Override public void onSearch() { openSearch(); }
            @Override public void onFilter(int f) { setFilter(f); }
            @Override public void onSortNext() { setSort((sort + 1) % 3); }
            @Override public void onUp() { host.focusNav(); }
            @Override public void onDown() { grid.requestFocus(); }
        });
        toolbar.setAmbient(h.ambient());
        toolbar.set(filter, sort);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP | Gravity.START);
        bp.leftMargin = dp(Theme.SAFE_SIDE);
        bp.topMargin = dp(94);
        stage.addView(toolbar, bp);
        toolbar.setOnFocusChangeListener((v, has) -> { if (has) OmniLog.state("focus page=TV toolbar"); });

        grid = new ChannelGrid(h.context(), this);
        FrameLayout.LayoutParams gp = new FrameLayout.LayoutParams(dp(960 - RAIL_W - 64 - 12), dp(300), Gravity.TOP | Gravity.START);
        gp.topMargin = dp(148);
        stage.addView(grid, gp);

        rail = new InputsRail(h.context(), new InputsRail.Listener() {
            @Override public void onOpen(Inputs.Entry e) { switchInput(e); }
            @Override public void onMenu(Inputs.Entry e) { inputMenu(e); }
            @Override public void onLeft() { grid.requestFocus(); }
            @Override public void onUp() { toolbar.requestFocus(); }
        });
        rail.setAmbient(h.ambient());
        rail.setOnFocusChangeListener((v, has) -> { if (has) OmniLog.state("focus page=TV rail=" + (rail.current() != null ? rail.current().id : "none")); });
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(RAIL_W), dp(398), Gravity.TOP | Gravity.END);
        rp.rightMargin = dp(Theme.SAFE_SIDE);
        rp.topMargin = dp(2);
        stage.addView(rail, rp);

        col.addView(stage);
    }

    @Override String hint() { return overlayOpen ? "" : "OK to watch   Hold OK for options   Right for inputs"; }

    @Override void focusFirst() { focusSoon(grid); }

    @Override void refresh(String fp, String fl) { if (shown || !loaded) reload(); }

    @Override void onShown() {
        shown = true;
        host.ambient().setGlow(null);
        if (!loaded || SystemClock.uptimeMillis() - lastLoad > 20000) reload();
    }

    @Override void onResume() { if (shown) reload(); }

    @Override void onHidden() { shown = false; stopPreview(); }

    // ------------------------------------------------------------ loading (off the UI thread)

    private static final class Snapshot {
        List<PtvModel.Channel> channels;
        List<Inputs.Entry> entries;
        PtvUsage usage;
        Set<String> favs;
        String railSig;
    }

    private void reload() {
        lastLoad = SystemClock.uptimeMillis();
        io.execute(() -> {
            Context ctx = host.context();
            Snapshot s = new Snapshot();
            s.entries = Inputs.query(ctx);
            s.channels = PtvCatalog.load(ctx);
            s.channels.addAll(TunerSource.load(ctx));
            PtvCatalog.migrate(ctx, s.channels);
            s.usage = PtvCatalog.usage(ctx);
            s.favs = PtvCatalog.favorites(ctx);
            for (PtvModel.Channel c : s.channels) {                                   // draw every card now, not while the user scrolls
                ChannelArt.cached(c.id, c.name, c.country, c.playable(), s.favs.contains(c.id));
                ChannelArt.cached(c.id, c.name, c.country, c.playable(), !s.favs.contains(c.id));
            }
            StringBuilder rs = new StringBuilder();
            for (Inputs.Entry e : s.entries) rs.append(e.id).append(e.state).append(e.type).append(e.displayName()).append(e.typeFromUser).append(';');
            s.railSig = rs.toString();
            if (!loaded) TvSources.log(ctx);
            ui.post(() -> apply(s));
        });
    }

    private void apply(Snapshot s) {
        channels = s.channels;
        byId.clear();
        for (PtvModel.Channel c : channels) byId.put(c.id, c);
        usage = s.usage;
        favorites = s.favs;
        entries = s.entries;
        if (!s.railSig.equals(railSignature)) {
            railSignature = s.railSig;
            List<Inputs.Entry> ordered = new ArrayList<>();
            for (Inputs.Entry e : entries) if (e.hdmi) ordered.add(e);
            for (Inputs.Entry e : entries) if (!e.hdmi) ordered.add(e);
            rail.setEntries(ordered, null);
        }
        loaded = true;
        rebuild();
        host.hintChanged();
    }

    /** Applies the current filter and sort to the loaded channels. Cheap: art is already cached. */
    private void rebuild() {
        String[] keys = {"XK", "AL", "TUNER", "OTHER"};
        String[] names = {"Kosovo", "Albania", "Antenna", "Other"};
        List<ChannelGrid.Section> sections = new ArrayList<>();
        playOrder = new ArrayList<>();
        if (filter == 0) {                                      // inputs that have a picture come first, like the source list on a TV
            ChannelGrid.Section in = new ChannelGrid.Section("Inputs");
            for (Inputs.Entry e : entries) if (e.hdmi && e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) in.inputs.add(e);
            if (!in.inputs.isEmpty()) sections.add(in);
        }
        int total = 0;
        for (int k = 0; k < keys.length; k++) {
            if (filter == 1 && !keys[k].equals("XK") || filter == 2 && !keys[k].equals("AL") || filter == 3 && !keys[k].equals("TUNER")) continue;
            List<PtvModel.Channel> in = new ArrayList<>();
            for (PtvModel.Channel c : channels) if (c.section().equals(keys[k]) && (filter != 4 || favorites.contains(c.id))) in.add(c);
            if (in.isEmpty()) continue;
            ChannelGrid.Section sec = new ChannelGrid.Section(names[k]);
            sec.channels.addAll(ChannelSort.order(in, sort, usage, favorites));
            for (PtvModel.Channel c : sec.channels) if (c.playable()) playOrder.add(c.id);
            sections.add(sec);
            total += in.size();
        }
        shownTotal = total;
        grid.setEmptyText(filter == 4 ? "No favorites yet. Hold OK on a channel and choose Add to favorites." : "No channels here.");
        grid.setData(sections, favorites);
        if (sections.isEmpty() == false && grid.currentInput() == null) stopPreview();
        if (!grid.hasFocus()) showDefaultTitle(total);
    }

    private void showDefaultTitle(int shownCount) {
        title.setText(filter == 0 ? "Live TV" : TvToolbar.FILTERS[filter]);
        int playable = 0;
        for (PtvModel.Channel c : channels) if (c.playable()) playable++;
        meta.setText(shownCount + (shownCount == 1 ? " channel" : " channels") + "   ·   sorted: " + TvToolbar.SORTS[sort]);
        note.setText(usage.hasHistory() ? "Order and counts reflect viewing on this TV only." : "No viewing history yet, so channels are alphabetical until you watch some.");
    }

    private void setFilter(int f) {
        filter = f;
        prefs.edit().putInt("tv_filter", f).apply();
        toolbar.set(filter, sort);
        rebuild();
        host.flash(TvToolbar.FILTERS[f] + (f == 0 ? ": all channels" : ""), shownTotal == 0 ? (f == 4 ? "No favorites yet" : "No channels here") : shownTotal + (shownTotal == 1 ? " channel" : " channels"));
    }

    private void setSort(int s) {
        sort = s;
        prefs.edit().putInt("tv_sort2", s).apply();
        toolbar.set(filter, sort);
        rebuild();
        host.flash("Sorted: " + TvToolbar.SORTS[sort], sort == 0 && !usage.hasHistory() ? "No history yet, so it matches A to Z" : "Per section");
    }

    // ------------------------------------------------------------ ChannelGrid.Listener

    @Override public void onFocus(PtvModel.Channel c) {
        if (!shown || c == null) return;
        PtvModel.Channel cur = byId.get(c.id) != null ? byId.get(c.id) : c;
        title.animate().cancel();
        title.setText(cur.name);
        title.setAlpha(0f);
        title.setTranslationY(dp(10));
        title.animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(Theme.EXPO).start();
        StringBuilder m = new StringBuilder(cur.isTuner() ? "Antenna   ·   channel " + cur.number : PtvRules.group(cur.country));
        if (!cur.category().isEmpty() && !cur.isTuner()) m.append("   ·   ").append(Character.toUpperCase(cur.category().charAt(0))).append(cur.category().substring(1));
        if (!cur.languages.isEmpty()) m.append("   ·   ").append("sqi".equals(cur.languages.get(0)) ? "Albanian" : cur.languages.get(0));
        meta.setText(m);
        PtvUsage.Stat st = usage.get(cur.id);
        if (!cur.playable()) note.setText(cur.reason());
        else if (st.sessions > 0) note.setText("Watched " + st.sessions + (st.sessions == 1 ? " time" : " times") + " on this TV, " + minutes(st.ms) + ". No programme guide for this channel.");
        else if (cur.isTuner()) note.setText("From the TV's own tuner. Opens full screen.");
        else note.setText("Not watched yet on this TV. No programme guide for this channel.");
    }

    private static String minutes(long ms) {
        long m = ms / 60000;
        return m < 1 ? "under a minute" : m < 60 ? m + " min" : (m / 60) + " h " + (m % 60) + " min";
    }

    @Override public void onOpen(PtvModel.Channel c) { play(byId.get(c.id) != null ? byId.get(c.id) : c); }

    @Override public void onMenu(PtvModel.Channel c) { channelMenu(byId.get(c.id) != null ? byId.get(c.id) : c); }

    @Override public void onLeaveUp() { stopPreview(); toolbar.requestFocus(); }

    @Override public boolean onEdgeRight() {
        stopPreview();
        if (rail.current() == null) return false;
        rail.requestFocus();
        return true;
    }

    // ------------------------------------------------------------ actions

    private void play(PtvModel.Channel c) {
        if (!c.playable()) { channelMenu(c); return; }
        List<String> ids = new ArrayList<>(playOrder);
        int at = ids.indexOf(c.id);
        if (at < 0) { ids.add(0, c.id); at = 0; }
        OmniLog.state("open id=" + c.id + " at=" + at + " of=" + ids.size());
        if (c.isTuner()) {
            List<String> tids = new ArrayList<>();
            for (String id : ids) if (id.startsWith("tuner:")) tids.add(id);
            host.context().startActivity(new Intent(host.context(), TunerActivity.class)
                    .putExtra(TunerActivity.EXTRA_IDS, tids.toArray(new String[0])).putExtra(TunerActivity.EXTRA_INDEX, Math.max(0, tids.indexOf(c.id))));
            return;
        }
        List<String> iptv = new ArrayList<>();
        for (String id : ids) if (!id.startsWith("tuner:")) iptv.add(id);
        host.context().startActivity(new Intent(host.context(), PlayerActivity.class)
                .putExtra(PlayerActivity.EXTRA_KEYS, iptv.toArray(new String[0])).putExtra(PlayerActivity.EXTRA_INDEX, Math.max(0, iptv.indexOf(c.id))));
    }

    private void channelMenu(final PtvModel.Channel c) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (c.playable()) {
            labels.add("Watch");
            acts.add(() -> host.closeSheet(() -> play(c)));
            final boolean fav = favorites.contains(c.id);
            labels.add(fav ? "Remove from favorites" : "Add to favorites");
            acts.add(() -> host.closeSheet(() -> {
                boolean now = PtvCatalog.toggleFavorite(host.context(), c);
                host.flash(now ? "Added to favorites" : "Removed from favorites", c.name);
                reload();
            }));
        }
        if (c.website.length() > 0 && !c.isTuner()) {
            labels.add("Open broadcaster website");
            acts.add(() -> host.closeSheet(() -> {
                try {
                    host.context().startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(c.website)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ex) {
                    host.flash("No browser available", "This TV has no app that can open that page");
                }
            }));
        }
        PtvUsage.Stat st = usage.get(c.id);
        String sub = c.playable() ? (c.isTuner() ? "Antenna, channel " + c.number : PtvRules.group(c.country) + ", live") + (st.sessions > 0 ? ". Watched " + st.sessions + (st.sessions == 1 ? " time" : " times") + " on this TV" : "") : c.reason();
        host.showSheet(c.name, sub, labels, acts);
    }

    private void openSearch() {
        overlayOpen = true;
        host.hintChanged();
        List<SearchHit> hits = new ArrayList<>();
        for (PtvModel.Channel ch : channels) {
            String cat = ch.category();
            String sub = (ch.isTuner() ? "Antenna · channel " + ch.number : PtvRules.group(ch.country)) + (cat.isEmpty() ? "" : " · " + Character.toUpperCase(cat.charAt(0)) + cat.substring(1));
            StringBuilder extra = new StringBuilder();
            for (String a : ch.alt) extra.append(a).append(' ');
            extra.append(PtvSearch.countryWords(ch.country)).append(' ');
            if (ch.isTuner()) extra.append("tuner antenna terrestrial dvb live tv ").append(ch.number).append(' ');
            for (String l : ch.languages) extra.append(l).append(' ');
            for (String k : ch.categories) extra.append(k).append(' ');
            SearchHit h = new SearchHit(SearchHit.CHANNEL, ch.id, ch.name, sub, extra.toString());
            h.payload = ch;
            hits.add(h);
        }
        new SearchOverlay(host.context(), hits, favorites, "Search all channels", "channels", host.ambient(), new SearchOverlay.Listener() {
            @Override public void onOpen(SearchHit hit) {
                overlayOpen = false;
                PtvModel.Channel c = (PtvModel.Channel) hit.payload;
                play(byId.get(c.id) != null ? byId.get(c.id) : c);
            }
            @Override public void onClose() {
                overlayOpen = false;
                host.hintChanged();
                toolbar.requestFocus();
            }
        }).show(host.ambient());
    }

    // ------------------------------------------------------------ inputs rail

    private void switchInput(Inputs.Entry e) {
        if (e.hdmi) {                                                // HDMI is shown inside OMNI, in the same panel as an antenna channel, with sound
            ui.removeCallbacks(previewStart);
            previewOn = false;                                       // the preview's window is handed to the viewer (it grows to full screen); it is not stopped
            HdmiViewerActivity.open(host.context(), e);
            return;
        }
        try {
            host.context().startActivity(Inputs.switchIntent(e.id));
            OmniLog.state("switch input=" + e.id);
        } catch (Exception ex) {
            host.flash("This TV did not accept the switch", "Use the Input or Source button on your remote");
        }
    }

    private void inputMenu(final Inputs.Entry e) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        labels.add("Switch to " + e.displayName());
        acts.add(() -> host.closeSheet(() -> switchInput(e)));
        labels.add("Set device type");
        acts.add(() -> host.closeSheet(() -> typeMenu(e)));
        if (e.typeFromUser) {
            labels.add("Clear device type");
            acts.add(() -> host.closeSheet(() -> { Inputs.setUserType(host.context(), e.id, Inputs.T_UNKNOWN); railSignature = ""; reload(); }));
        }
        host.showSheet(e.displayName(), e.portLabel + ", " + e.stateText(), labels, acts);
    }

    private void typeMenu(final Inputs.Entry e) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        final int[] types = {Inputs.T_CONSOLE, Inputs.T_PC, Inputs.T_STREAMER, Inputs.T_BOX, Inputs.T_AUDIO, Inputs.T_OTHER};
        for (final int t : types) {
            labels.add(DeviceTypes.TYPE_NAMES[t]);
            acts.add(() -> host.closeSheet(() -> { Inputs.setUserType(host.context(), e.id, t); railSignature = ""; reload(); }));
        }
        host.showSheet("Device type", "What is plugged into " + e.portLabel + "? This is saved on this TV only.", labels, acts);
    }
    // ------------------------------------------------------------ inputs in the grid and their live preview

    private final Runnable previewStart = this::startPreview;
    private boolean previewOn;
    private String previewInput;

    @Override public void onFocusInput(Inputs.Entry e) {
        if (!shown) return;
        title.animate().cancel();
        title.setText(e.displayName());
        title.setAlpha(1f);
        title.setTranslationY(0);
        meta.setText(e.portLabel + "   \u00b7   " + e.stateText() + (e.nameFromTv() ? "   \u00b7   name from the TV" : ""));
        boolean can = android.provider.Settings.canDrawOverlays(host.context());
        note.setText(can ? "OK to switch to this input. A live picture appears here after a moment." : "OK to switch to this input. (Live preview needs permission to draw over other apps.)");
        ui.removeCallbacks(previewStart);
        stopPreview();                                              // a moving focus never keeps a picture running
        if (can) ui.postDelayed(previewStart, 900);
    }

    @Override public void onOpenInput(Inputs.Entry e) { stopPreview(); switchInput(e); }

    @Override public void onMenuInput(Inputs.Entry e) { stopPreview(); inputMenu(e); }

    private void startPreview() {
        if (!shown || !grid.hasFocus()) return;
        Inputs.Entry e = grid.currentInput();
        int[] r = grid.focusedInputRect();
        if (e == null || r == null) return;
        float d = Theme.density;
        int inset = (int) (4 * d);
        previewInput = e.id;
        previewOn = true;
        host.context().startService(new Intent(host.context(), HdmiOverlay.class).putExtra(HdmiOverlay.EXTRA_INPUT, e.playId())
                .putExtra(HdmiOverlay.EXTRA_X, r[0] + inset).putExtra(HdmiOverlay.EXTRA_Y, r[1] + inset).putExtra(HdmiOverlay.EXTRA_W, r[2] - 2 * inset).putExtra(HdmiOverlay.EXTRA_H, r[3] - 2 * inset)
                .putExtra(HdmiOverlay.EXTRA_SECONDS, 600));
        OmniLog.state("preview start " + e.id);
    }

    private void stopPreview() {
        ui.removeCallbacks(previewStart);
        if (!previewOn) return;
        previewOn = false;
        host.context().startService(new Intent(host.context(), HdmiOverlay.class).setAction(HdmiOverlay.ACTION_STOP));
        OmniLog.state("preview stop");
    }
}
