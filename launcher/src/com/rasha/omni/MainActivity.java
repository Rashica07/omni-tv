package com.rasha.omni;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/** OMNI: an ad-free, spring-animated TV home screen. Owns the tab bar and page transitions. */
public class MainActivity extends Activity implements Host {
    private static final String[] TABS = {"Home", "Watch", "Apps", "TV", "Settings"};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Page[] pages = new Page[TABS.length];
    private AppRepo repo;
    private Ambient ambient;
    private Wallpaper wallpaper;
    private Images images;
    private Tmdb tmdb;
    private FrameLayout pageHost;
    private NavBar nav;
    private StatusBar status;
    private NetState net;
    private PtvServer.Status serverStatus = new PtvServer.Status();
    private boolean serverChecked;
    private Banner banner;
    private TextView hintView;
    private int cur = -1;
    private Sheet sheet;
    private Runnable afterSheet;
    private View incoming;
    private View outgoing;
    private int dirSign = 1;
    private Spring trans;
    private Spring returnSpring;
    private String lastPkg;
    private String lastList;
    private boolean launched;
    private boolean loaded;
    private boolean prewarming;
    /** Index of the page currently attached as the real, visible page (-1 before the first). */
    private int shown = -1;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { rescan(); }
    };

    // ------------------------------------------------------------ Host

    @Override public Context context() { return this; }

    @Override public AppRepo repo() { return repo; }

    @Override public Ambient ambient() { return ambient; }

    @Override public Wallpaper wallpaper() { return wallpaper; }

    @Override public Images images() { return images; }

    @Override public Tmdb tmdb() { return tmdb; }

    @Override public void launchIntent(View src, int x, int y, int w, int h, Intent intent, String pkg) {
        if (sheet != null) return;
        repo.launched(pkg, "wn");
        lastPkg = pkg;
        lastList = "wn";
        launched = true;
        OmniLog.state("launch pkg=" + pkg + " via=watchnext");
        int[] loc = new int[2];
        src.getLocationInWindow(loc);
        Ripple.play(ambient, loc[0] + x + w / 2f, loc[1] + y + h / 2f, Theme.WHITE);
        final ActivityOptions o = ActivityOptions.makeScaleUpAnimation(src, x, y, w, h);
        final Intent it = intent;
        ui.postDelayed(() -> {
            try { startActivity(it, o.toBundle()); }
            catch (Exception e) { Toast.makeText(this, "Can't open that item", Toast.LENGTH_SHORT).show(); }
        }, 140);
    }

    @Override public void launchAt(View src, int x, int y, int w, int h, AppRepo.App a, String listId) {
        if (sheet != null) return;
        repo.launched(a.pkg, listId);
        lastPkg = a.pkg;
        lastList = listId;
        launched = true;
        OmniLog.state("launch pkg=" + a.pkg);
        int[] loc = new int[2];
        src.getLocationInWindow(loc);
        Ripple.play(ambient, loc[0] + x + w / 2f, loc[1] + y + h / 2f, Theme.WHITE);
        ambient.boost(true);                                           // the app's colour intensifies as it opens
        final Intent it = Intent.makeMainActivity(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        final ActivityOptions o = ActivityOptions.makeScaleUpAnimation(src, x, y, w, h);
        ui.postDelayed(() -> {
            try { startActivity(it, o.toBundle()); }
            catch (Exception e) { Toast.makeText(this, "Can't open " + a.label, Toast.LENGTH_SHORT).show(); }
        }, 140);
    }

    @Override public void appMenu(final AppRepo.App a, final String listId) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        if (listId.equals("fav")) {   // only the Favorites order is user-editable
            labels.add("Move");
            acts.add(() -> closeSheet(() -> {
                repo.movingPkg = a.pkg;       // the order is only saved when the item is placed
                repo.movingList = listId;
                refreshAll(a.pkg, listId);
            }));
        }
        labels.add(repo.pins.contains(a.pkg) ? "Remove from Favorites" : "Pin to Favorites");
        acts.add(() -> closeSheet(() -> {
            boolean now = repo.togglePin(a.pkg);
            refreshAll(a.pkg, listId);
            flash(now ? "Pinned to Favorites" : "Removed from Favorites", a.label);
        }));
        labels.add(repo.hidden.contains(a.pkg) ? "Show on Home" : "Hide from Home");
        acts.add(() -> closeSheet(() -> {
            boolean now = repo.toggleHidden(a.pkg);
            refreshAll(a.pkg, listId);
            flash(now ? "Hidden from Home" : "Shown on Home", now ? a.label + " is under Apps, Hidden" : a.label);
        }));
        labels.add("App info");
        acts.add(() -> closeSheet(() -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", a.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        if (!a.system) {
            labels.add("Uninstall");
            acts.add(() -> closeSheet(() -> startActivity(new Intent(Intent.ACTION_DELETE,
                    Uri.fromParts("package", a.pkg, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))));
        }
        int opened = repo.count(a.pkg);
        showSheet(a.label, opened == 0 ? "Not opened yet" : "Opened " + opened + (opened == 1 ? " time" : " times"), labels, acts);
    }

    @Override public void showSheet(String title, String sub, List<String> labels, List<Runnable> acts) {
        if (sheet != null) return;
        final View before = getCurrentFocus();
        setFocusBlocked(true);
        sheet = new Sheet(this, title, sub, labels, acts, () -> {
            sheet = null;
            OmniLog.state("sheet=closed");
            setFocusBlocked(false);
            // Put focus back where it was, unless the action rebuilt the page (it restores its own focus).
            if (before != null && before.isAttachedToWindow()) before.requestFocus();
            Runnable r = afterSheet;
            afterSheet = null;
            if (r != null) r.run();
        });
        sheet.show(ambient);
        OmniLog.state("sheet=open title=" + title);
    }

    // ------------------------------------------------------------ Control Center

    private ControlCenter cc;

    private String weatherLine() {
        Weather.Place pl = Weather.place(this);
        Weather.Reading r = Weather.cached(this);
        return pl == null ? "No location set" : !r.valid() ? pl.name + ": no reading yet" : pl.name + ", " + WeatherCodes.temp(r.temp) + " " + WeatherCodes.label(r.kind);
    }

    private String serverLine() {
        return ControlRows.server(PtvServer.base(this) != null, serverChecked, serverStatus.state == PtvServer.REACHABLE, serverStatus.playing >= 0 ? serverStatus.playing + " channels playing" : "");
    }

    @Override public void openControlCenter() {
        if (sheet != null || cc != null || search != null) return;
        final View before = getCurrentFocus();
        setFocusBlocked(true);
        cc = new ControlCenter(this, ambient, net.describe(), serverLine(), weatherLine(), CinemaBridge.installed(this), new ControlCenter.Actions() {
            @Override public void checkServer() { MainActivity.this.checkServer(false); }                       // the answer lands in the row itself (see checkServer)
            @Override public void refreshWeather() {
                Weather.refresh(MainActivity.this, true, (r, failed) -> ui.post(() -> { pushStatus(); if (cc != null) cc.setWeather(failed && !r.valid() ? "Could not update" : weatherLine() + (failed ? " (offline)" : "")); }));
            }
            @Override public void openNetwork() { closeControl(() -> openSettings(Settings.ACTION_WIFI_SETTINGS)); }
            @Override public void openInput(Inputs.Entry e) {
                if (e.state != android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) { flash(e.displayName() + " has no signal", "Switch the device on first"); return; }
                closeControl(() -> HdmiViewerActivity.open(MainActivity.this, e));
            }
            @Override public void run(String id) {
                switch (id) {
                    case "search": closeControl(MainActivity.this::openUniversalSearch); break;
                    case "tv": closeControl(() -> { nav.select(3, true); selectTab(3); }); break;
                    case "weather": closeControl(MainActivity.this::openWeather); break;
                    case "play": closeControl(() -> { if (!CinemaBridge.open(MainActivity.this, "omni-cinema://home")) flash("Can't open OMNI Cinema", "It did not start"); }); break;
                    default: closeControl(() -> openSettings(Settings.ACTION_SETTINGS));
                }
            }
            @Override public void closed() {
                cc = null;
                OmniLog.state("control=closed");
                setFocusBlocked(false);
                if (before != null && before.isAttachedToWindow()) before.requestFocus();
                Runnable a = afterSheet;
                afterSheet = null;
                if (a != null) a.run();
            }
        });
        cc.show(ambient);
        OmniLog.state("control=open");
        final ControlCenter shown = cc;
        new Thread(() -> {                                                   // the input list is an IPC call: never on the main thread
            final List<Inputs.Entry> hdmi = new ArrayList<>();
            for (Inputs.Entry e : Inputs.query(this)) if (e.hdmi) hdmi.add(e);
            ui.post(() -> { if (cc == shown) shown.setInputs(hdmi); });
        }, "control-inputs").start();
    }

    // ------------------------------------------------------------ universal search

    private SearchOverlay search;

    /** One search across apps, TV channels, films (from OMNI Cinema), inputs and settings. The list is gathered off the main thread, then the overlay opens. */
    void openUniversalSearch() {
        if (sheet != null || cc != null || search != null) return;
        flash("Searching OMNI", "Gathering apps, channels, films and inputs");
        new Thread(() -> {
            final List<SearchHit> hits = new ArrayList<>();
            for (AppRepo.App a : repo.alphabetical()) {
                SearchHit h = new SearchHit(SearchHit.APP, "app:" + a.pkg, a.label, "App", a.pkg.replace('.', ' '));
                h.payload = a;
                if (a.art != null) h.thumb = a.art.icon;
                hits.add(h);
            }
            try {
                List<PtvModel.Channel> chans = new ArrayList<>(PtvCatalog.load(this));
                chans.addAll(TunerSource.load(this));
                for (PtvModel.Channel ch : chans) {
                    String cat = ch.category();
                    StringBuilder extra = new StringBuilder();
                    for (String a : ch.alt) extra.append(a).append(' ');
                    extra.append(PtvSearch.countryWords(ch.country)).append(' ');
                    if (ch.isTuner()) extra.append("tuner antenna terrestrial dvb live tv ").append(ch.number).append(' ');
                    for (String k : ch.categories) extra.append(k).append(' ');
                    SearchHit h = new SearchHit(SearchHit.CHANNEL, ch.id, ch.name, (ch.isTuner() ? "Antenna · channel " + ch.number : PtvRules.group(ch.country)) + (cat.isEmpty() ? "" : " · " + Character.toUpperCase(cat.charAt(0)) + cat.substring(1)), extra.toString());
                    h.payload = ch;
                    hits.add(h);
                }
            } catch (RuntimeException e) { OmniLog.state("search channels unavailable " + e.getClass().getSimpleName()); }
            CinemaBridge.Result films = CinemaBridge.load(this);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (CinemaBridge.Shelf sh : films.shelves) for (CinemaBridge.Title t : sh.items) {
                if (!seen.add(t.id)) continue;
                SearchHit h = new SearchHit(SearchHit.FILM, t.id, t.title, (t.year > 0 ? t.year + " · " : "") + (t.sourceLabel == null ? "" : t.sourceLabel) + (t.playable() ? "" : " · not reachable now"), t.overview);
                h.payload = t;
                hits.add(h);
            }
            for (Inputs.Entry e : Inputs.query(this)) {
                SearchHit h = new SearchHit(SearchHit.INPUT, "in:" + e.id, e.displayName(), e.portLabel + " · " + e.stateText(), e.portLabel + " input source hdmi");
                h.payload = e;
                hits.add(h);
            }
            for (int i = 0; i < SettingsPage.NAMES.length; i++) {
                SearchHit h = new SearchHit(SearchHit.SETTING, "set:" + i, SettingsPage.NAMES[i], SettingsPage.DESC[i], "settings");
                h.payload = SettingsPage.ACTIONS[i];
                hits.add(h);
            }
            ui.post(() -> showUniversalSearch(hits));
        }, "search-gather").start();
    }

    private void showUniversalSearch(List<SearchHit> hits) {
        if (search != null || sheet != null || cc != null) return;
        final View before = getCurrentFocus();
        setFocusBlocked(true);
        search = new SearchOverlay(this, hits, new java.util.HashSet<>(), "Search OMNI", "things", ambient, new SearchOverlay.Listener() {
            @Override public void onOpen(SearchHit h) { endSearch(before); openHit(h); }
            @Override public void onClose() { endSearch(before); }
        });
        search.show(ambient);
        OmniLog.state("search=open items=" + hits.size());
    }

    private void endSearch(View before) {
        search = null;
        setFocusBlocked(false);
        if (before != null && before.isAttachedToWindow()) before.requestFocus();
    }

    /** Opens what a search result is: the app, the channel, the film, the input, the setting. */
    private void openHit(SearchHit h) {
        OmniLog.state("search open kind=" + SearchHit.kindName(h.kind) + " id=" + h.id);
        switch (h.kind) {
            case SearchHit.APP: {
                AppRepo.App a = (AppRepo.App) h.payload;
                repo.launched(a.pkg, "search");
                try { startActivity(Intent.makeMainActivity(a.cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)); }
                catch (Exception e) { flash("Can't open " + a.label, "It did not start"); }
                break;
            }
            case SearchHit.CHANNEL: {
                PtvModel.Channel c = (PtvModel.Channel) h.payload;
                if (!c.playable()) { flash(c.name + " is not available", c.reason()); break; }
                if (c.isTuner()) startActivity(new Intent(this, TunerActivity.class).putExtra(TunerActivity.EXTRA_IDS, new String[]{c.id}).putExtra(TunerActivity.EXTRA_INDEX, 0));
                else startActivity(new Intent(this, PlayerActivity.class).putExtra(PlayerActivity.EXTRA_KEYS, new String[]{c.id}).putExtra(PlayerActivity.EXTRA_INDEX, 0));
                break;
            }
            case SearchHit.FILM: {
                CinemaBridge.Title t = (CinemaBridge.Title) h.payload;
                if (!CinemaBridge.open(this, t.playable() && !"show".equals(t.kind) ? CinemaBridge.playLink(t) : CinemaBridge.titleLink(t))) flash("Can't open OMNI Cinema", "It did not start");
                break;
            }
            case SearchHit.INPUT: {
                Inputs.Entry e = (Inputs.Entry) h.payload;
                if (e.hdmi && e.state == android.media.tv.TvInputManager.INPUT_STATE_CONNECTED) HdmiViewerActivity.open(this, e);
                else if (e.hdmi) flash(e.displayName() + " has no signal", "Switch the device on first");
                else openSettingsOrInput(e);
                break;
            }
            default: openSettings((String) h.payload);
        }
    }

    private void openSettingsOrInput(Inputs.Entry e) {
        try { startActivity(Inputs.switchIntent(e.id)); } catch (RuntimeException ex) { flash("This TV did not accept the switch", "Use the Input button on your remote"); }
    }

    // ------------------------------------------------------------ system notifications (real changes only, through the one Banner)

    private Boolean netWasOnline;
    private final Runnable netSettled = () -> {
        boolean now = net.transport != NetKind.NONE && net.validated;
        String[] e = EventRules.network(netWasOnline, now, net.describe());
        netWasOnline = now;
        if (e != null) { OmniLog.state("event network " + e[0]); flash(e[0], e[1]); }
    };

    /** A flapping link is one event, not five: the change must hold for 3 seconds before it is announced. */
    private void netEvent() { ui.removeCallbacks(netSettled); ui.postDelayed(netSettled, 3000); }

    private final java.util.Map<String, Integer> inputStates = new java.util.HashMap<>();
    private android.media.tv.TvInputManager.TvInputCallback inputCb;
    private final Runnable inputsChanged = () -> new Thread(() -> {
        final java.util.List<String[]> events = new ArrayList<>();
        synchronized (inputStates) {
            boolean first = inputStates.isEmpty();
            for (Inputs.Entry e : Inputs.query(this)) {
                if (!e.hdmi) continue;
                String[] ev = EventRules.input(e.displayName(), e.portLabel, inputStates.get(e.id), e.state);
                inputStates.put(e.id, e.state);
                if (ev != null && !first) events.add(ev);
            }
        }
        for (String[] ev : events) ui.post(() -> { OmniLog.state("event input " + ev[0]); flash(ev[0], ev[1]); });
    }, "input-events").start();

    /** Tells you when a device on HDMI is switched on or off. The first reading is taken silently; later changes arrive from the TV's own callback, with no polling. */
    private void watchInputs() {
        try {
            android.media.tv.TvInputManager m = (android.media.tv.TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
            if (m == null) return;
            inputCb = new android.media.tv.TvInputManager.TvInputCallback() {
                @Override public void onInputStateChanged(String inputId, int state) { ui.removeCallbacks(inputsChanged); ui.postDelayed(inputsChanged, 800); }
            };
            m.registerCallback(inputCb, ui);
            ui.post(inputsChanged);                                                  // the silent first snapshot
        } catch (RuntimeException e) { OmniLog.state("input events unavailable " + e.getClass().getSimpleName()); }
    }

    private void unwatchInputs() {
        try { android.media.tv.TvInputManager m = (android.media.tv.TvInputManager) getSystemService(Context.TV_INPUT_SERVICE); if (m != null && inputCb != null) m.unregisterCallback(inputCb); } catch (RuntimeException ignored) { }
    }

    private void closeControl(Runnable after) {
        afterSheet = after;
        if (cc != null) cc.dismiss();
    }

    @Override public void closeSheet(Runnable after) {
        afterSheet = after;
        if (sheet != null) sheet.dismiss();
    }

    private void setFocusBlocked(boolean b) {
        int f = b ? ViewGroup.FOCUS_BLOCK_DESCENDANTS : ViewGroup.FOCUS_AFTER_DESCENDANTS;
        pageHost.setDescendantFocusability(f);
        nav.setFocusable(!b);
        status.setFocusable(!b);
    }

    @Override public void moveFinished(String label, boolean placed) {
        banner.flash(placed ? Banner.MODE_DONE : Banner.MODE_CANCEL, placed ? label + " placed" : "Move cancelled",
                placed ? "Saved in Favorites" : "Back where it was");
    }

    @Override public void flash(String heading, String detail) { banner.flash(Banner.MODE_DONE, heading, detail); }

    /** Keeps the hint line and the editing banner in step with what the page and the repository say. */
    @Override public void hintChanged() {
        if (hintView == null || cur < 0) return;
        boolean moving = repo.movingPkg != null;
        if (moving) {
            AppRepo.App a = repo.get(repo.movingPkg);
            banner.show(Banner.MODE_MOVE, "Move " + (a != null ? a.label : "app"), "Left and Right to move   OK to place   Back to cancel");
            hintView.setText("");
        } else {
            if (banner.isEditing()) banner.hide();      // an editing mode ended; confirmations dismiss themselves
            hintView.setText(pages[cur].hint());
        }
        float dim = moving ? 0.12f : 1f;
        nav.animate().alpha(dim).setDuration(180).start();
        status.animate().alpha(dim).setDuration(180).start();
    }

    @Override public void focusNav() { nav.requestFocus(); }

    @Override public boolean navHasFocus() { return nav.isFocused(); }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (OmniLog.flag("OmniKeys") && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) OmniLog.state("key code=" + e.getKeyCode() + " " + KeyEvent.keyCodeToString(e.getKeyCode()) + " scan=" + e.getScanCode());
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && ControlCenter.isInfo(e) && cc == null && sheet == null && search == null) { openControlCenter(); return true; }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0 && OmniLog.latency()) probeLatency(e);
        return super.dispatchKeyEvent(e);
    }

    /** Logs input-to-first-draw time (app-side responsiveness; excludes display latency). Only when OmniLat is enabled. */
    private void probeLatency(final KeyEvent e) {
        final long t0 = e.getEventTime();
        final int code = e.getKeyCode();
        final View v = getWindow().getDecorView();
        v.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
            boolean done;
            @Override public void onDraw() {
                if (done) return;
                done = true;
                OmniLog.lat("key=" + KeyEvent.keyCodeToString(code) + " ms=" + (android.os.SystemClock.uptimeMillis() - t0));
                v.post(() -> v.getViewTreeObserver().removeOnDrawListener(this));
            }
        });
    }

    @Override public void refreshAll(String fp, String fl) {
        for (Page p : pages) if (p != null) p.refresh(fp, fl);
        hintChanged();
    }

    // ------------------------------------------------------------ status area

    private void pushStatus() {
        if (status == null || net == null) return;
        status.setNetwork(net.transport, serverChecked, serverStatus.state);
        Weather.Reading r = Weather.cached(this);
        boolean have = Weather.place(this) != null;
        status.setWeather(have, r.temp, r.kind, r.valid() && System.currentTimeMillis() - r.fetched > 3 * 3600_000L);
    }

    private void checkServer(final boolean announce) {
        serverStatus.state = PtvServer.CHECKING;
        PtvServer.check(this, s -> ui.post(() -> {
            serverStatus = s;
            serverChecked = s.state == PtvServer.REACHABLE || s.state == PtvServer.UNREACHABLE;
            pushStatus();
            OmniLog.state("server state=" + s.state);
            if (cc != null) cc.setServer(serverLine());
            if (announce) flash(s.state == PtvServer.REACHABLE ? "Server reachable" : s.state == PtvServer.UNSET ? "No server set up" : "Server not reachable", s.detail);
        }));
    }

    private void refreshWeather(boolean force) {
        Weather.refresh(this, force, (r, failed) -> ui.post(() -> {
            pushStatus();
            if (force) flash(failed ? "Weather could not be updated" : "Weather updated", failed && r.valid() ? "Showing the reading from " + WeatherCodes.age(System.currentTimeMillis() - r.fetched) : r.place);
        }));
    }

    private void statusActivated(int zone) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        String title, sub;
        if (zone == StatusBar.NET) {
            title = "Network";
            String srv = serverStatus.state == PtvServer.UNSET || !serverChecked ? (PtvServer.base(this) == null ? "OMNIPTV server: not set up" : "OMNIPTV server: not checked yet") : serverStatus.detail;
            sub = net.describe() + ". " + srv;
            labels.add("Check OMNIPTV server");
            acts.add(() -> closeSheet(() -> checkServer(true)));
            labels.add("Set server address");
            acts.add(() -> closeSheet(this::askServerAddress));
            labels.add("Network settings");
            acts.add(() -> closeSheet(() -> openSettings(Settings.ACTION_WIFI_SETTINGS)));
        } else if (zone == StatusBar.WEATHER) {
            openWeather();
            return;
        } else if (zone == -1) {
            Weather.Place pl = Weather.place(this);
            Weather.Reading r = Weather.cached(this);
            title = "Weather";
            if (pl == null) sub = "Choose where you are. No location permission is used. Data: Open-Meteo.com";
            else if (!r.valid()) sub = pl.name + ": no reading yet. Data: Open-Meteo.com";
            else sub = pl.name + ", " + WeatherCodes.temp(r.temp) + " " + WeatherCodes.label(r.kind) + ", updated " + WeatherCodes.age(System.currentTimeMillis() - r.fetched) + ". Data: Open-Meteo.com (CC BY 4.0)";
            if (pl != null) {
                labels.add("Refresh now");
                acts.add(() -> closeSheet(() -> refreshWeather(true)));
            }
            labels.add(pl == null ? "Choose location" : "Change location");
            acts.add(() -> closeSheet(this::placeMenu));
        } else {
            title = "Account";
            sub = "OMNI cannot see which Google account this TV uses, so none is shown.";
            labels.add("Account settings");
            acts.add(() -> closeSheet(() -> openSettings(Settings.ACTION_SYNC_SETTINGS)));
            labels.add("Add an account");
            acts.add(() -> closeSheet(() -> openSettings(Settings.ACTION_ADD_ACCOUNT)));
        }
        showSheet(title, sub, labels, acts);
    }

    /** The Kotlin + Compose weather app when it is installed; the built-in screen otherwise. */
    private void openWeather() {
        Intent wx = getPackageManager().getLaunchIntentForPackage("com.rasha.omni.weather");
        startActivity(wx != null ? wx : new Intent(this, WeatherActivity.class));
    }

    private void openSettings(String action) {
        try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e2) { flash("Settings could not be opened", "This TV has no settings screen for that"); }
        }
    }

    /** Cities are grouped so each list fits on screen. Indexes follow Weather.PLACES: 0-6 Italy, 7-8 Albania, 9-10 Kosovo. */
    private void placeMenu() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        final String[] regions = {"Italy", "Albania", "Kosovo"};
        final int[][] ranges = {{0, 7}, {7, 9}, {9, 11}};
        for (int g = 0; g < regions.length; g++) {
            final int from = ranges[g][0], to = ranges[g][1];
            labels.add(regions[g]);
            acts.add(() -> closeSheet(() -> cityMenu(from, to)));
        }
        if (Weather.place(this) != null) {
            labels.add("Remove location");
            acts.add(() -> closeSheet(() -> { Weather.setPlace(this, null); pushStatus(); }));
        }
        showSheet("Weather location", "Pick a region, then the nearest city. It is stored on this TV only.", labels, acts);
    }

    private void cityMenu(int from, int to) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            final Weather.Place p = Weather.PLACES[i];
            labels.add(p.name);
            acts.add(() -> closeSheet(() -> { Weather.setPlace(this, p); pushStatus(); refreshWeather(true); }));
        }
        showSheet("Weather location", "Pick the nearest city.", labels, acts);
    }

    /** Server address entry uses the system text field (and the TV's own on-screen keyboard); the value is validated before it is stored. */
    private void askServerAddress() {
        final android.widget.EditText in = new android.widget.EditText(this);
        in.setSingleLine(true);
        in.setHint("192.168.1.146");
        String cur = PtvServer.base(this);
        if (cur != null) in.setText(cur.replace("http://", "").replace(":8765", ""));
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog)
                .setTitle("OMNIPTV server address")
                .setMessage("Address of the home server on this network. Leave empty to forget it.")
                .setView(in)
                .setPositiveButton("Save", (d, w) -> {
                    String v = in.getText().toString().trim();
                    if (v.isEmpty()) { PtvServer.setBase(this, ""); serverChecked = false; serverStatus = new PtvServer.Status(); pushStatus(); flash("Server forgotten", "Using the built-in channel list"); return; }
                    if (PtvRules.serverBase(v) == null) { flash("That is not a valid address", "Example: 192.168.1.146"); return; }
                    PtvServer.setBase(this, v);
                    checkServer(true);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ------------------------------------------------------------ lifecycle

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Theme.BG));
        repo = new AppRepo(this);
        images = new Images(this);
        tmdb = new Tmdb(this);
        buildUi();
        // an app tile with focus keeps the wallpaper crisp (and lit in the app's colour); anywhere else it sits at 70% blur
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> ambient.setSharp(newF instanceof ShelfStack));
        wallpaper = new Wallpaper(this, ambient);
        wallpaper.apply();                                             // off the UI thread; the ground is near-black until it fades in
        ui.postDelayed(() -> wallpaper.refreshIfDue(false), 6000);     // the network never delays startup
        net = new NetState(this);
        net.start(() -> ui.post(() -> { pushStatus(); netEvent(); }));
        netWasOnline = net.transport != NetKind.NONE && net.validated ? Boolean.TRUE : (net.transport == NetKind.NONE ? Boolean.FALSE : null);      // an unvalidated link at start is unknown, not news
        watchInputs();
        pushStatus();
        refreshWeather(false);
        if (PtvServer.base(this) != null) checkServer(false);
        pages[0] = new HomePage(this);
        pages[1] = new WatchPage(this);
        pages[2] = new AppsPage(this);
        pages[3] = new TvPage(this);
        pages[4] = new SettingsPage(this);
        selectTab(0);
        rescan();
    }

    /** Asks for READ_TV_LISTINGS once (a runtime permission); declining is respected and never nagged. */
    private void askWatchNextOnce() {
        if (WatchNext.granted(this)) return;
        android.content.SharedPreferences sp = getSharedPreferences("omni", MODE_PRIVATE);
        if (sp.getBoolean("asked_wn", false)) return;
        sp.edit().putBoolean("asked_wn", true).apply();
        requestPermissions(new String[]{WatchNext.PERMISSION}, 7);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        OmniLog.state("permission watchnext=" + (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED));
        refreshAll(null, null);
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (net != null) net.stop();
        unwatchInputs();
    }

    @Override protected void onStop() {
        super.onStop();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
    }

    @Override protected void onPause() { HdmiOverlay.launcherResumed = false; super.onPause(); }

    @Override protected void onResume() {
        super.onResume();
        HdmiOverlay.launcherResumed = true;
        if (cur >= 0 && pages[cur] != null) pages[cur].onResume();
        if (wallpaper != null) wallpaper.apply();                      // a new schedule slot while we were away
        if (ambient != null) ambient.boost(false);                     // the bloom relaxes when OMNI is back
        if (launched) {
            launched = false;
            refreshAll(lastPkg, lastList);
            returnAnim();
        }
    }

    /**
     * Home pressed. Coming back from an app we launched restores the place you left (the same tile on
     * the same shelf); pressing Home while already on OMNI resets to the first tile of the first shelf.
     */
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra(HdmiOverlay.EXTRA_KEEP, false)) return;      // the HDMI preview bringing OMNI back in front: stay exactly where you were
        if (OmniLog.flag("OmniDebugOpen") && "weather".equals(intent.getStringExtra("open"))) { openWeather(); return; }
        if (OmniLog.flag("OmniDebugOpen") && intent.getStringExtra("server") != null) { PtvServer.setBase(this, intent.getStringExtra("server")); serverChecked = false; checkServer(true); return; }   // developer setup only
        if (OmniLog.flag("OmniDebugOpen") && "control".equals(intent.getStringExtra("open"))) { openControlCenter(); return; }
        if (OmniLog.flag("OmniDebugOpen") && "search".equals(intent.getStringExtra("open"))) { openUniversalSearch(); return; }
        if (OmniLog.flag("OmniDebugOpen") && "wallpaper".equals(intent.getStringExtra("open"))) { nav.select(4, false); selectTab(4); ui.postDelayed(() -> ((SettingsPage) pages[4]).openWallpaperMenu(), 700); return; }   // developer profiling only
        boolean fromApp = launched;
        launched = false;
        if (fromApp) repo.clearRecentLaunch();
        if (sheet != null) { afterSheet = null; sheet.dismiss(); }
        repo.movingPkg = null;
        nav.select(0, true);
        selectTab(0);
        if (fromApp) {
            refreshAll(lastPkg, lastList);
        } else {
            refreshAll(null, null);
            pages[0].resetScroll();
            pages[0].focusFirst();
        }
        returnAnim();
    }

    /**
     * Back: sheet, then a page's own sub-view (a category), then the header, then the Home tab, then nothing
     * (a launcher has nowhere to go back to). Never exits and never loops.
     */
    /** Menu opens the Control Center wherever no page used the key itself (the channel grid keeps Menu for its own options). */
    @Override public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU && e.getRepeatCount() == 0 && cc == null && sheet == null) { openControlCenter(); return true; }
        return super.onKeyDown(code, e);
    }

    @Override public void onBackPressed() {
        if (cc != null) { cc.dismiss(); return; }
        if (sheet != null) { closeSheet(null); return; }
        if (status.isFocused()) { OmniLog.state("back to=nav"); focusNav(); return; }
        if (!nav.isFocused()) {
            if (pages[cur].handleBack()) { OmniLog.state("back to=parent"); return; }
            OmniLog.state("back to=header");
            focusNav();
            return;
        }
        if (cur != 0) {
            OmniLog.state("back to=home");
            nav.select(0, true);
            selectTab(0);
        } else {
            OmniLog.state("back to=none");
        }
    }

    private void rescan() {
        repo.scan(() -> ui.post(() -> {
            // a fresh process shortly after launching an app means the system recycled us: put focus back where it was
            String[] back = loaded ? null : repo.recentLaunch();
            if (back != null) {
                OmniLog.state("restore after process recycle pkg=" + back[0]);
                repo.clearRecentLaunch();
                refreshAll(back[0], back[1]);
            } else {
                refreshAll(null, null);
            }
            if (!loaded) {
                loaded = true;
                if (back == null) pages[cur].focusFirst();
                prewarm();
                askWatchNextOnce();
            }
        }));
    }

    /**
     * Draws the other tabs once, at near-zero alpha behind the current page, so their artwork,
     * shadows and glyphs are uploaded to the GPU now instead of janking the first tab switch.
     */
    private void prewarm() {
        prewarming = true;
        for (int i = 0; i < pages.length; i++) {
            if (i == shown) continue;
            Page p = pages[i];
            p.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            p.setAlpha(0.004f);
            pageHost.addView(p, 0, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        ui.postDelayed(this::endPrewarm, 900);
    }

    private void endPrewarm() {
        if (!prewarming) return;
        prewarming = false;
        for (int i = 0; i < pages.length; i++) {
            if (i == shown) continue;
            Page p = pages[i];
            pageHost.removeView(p);
            p.setAlpha(1f);
            p.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        }
    }

    /** iOS-style "app closes back to its icon": the page settles from slightly small and dim. */
    private void returnAnim() {
        if (returnSpring == null) {
            returnSpring = new Spring(240f, 0.8f, 1f, 0.002f, v -> {
                float s = 0.94f + 0.06f * v;
                pageHost.setScaleX(s);
                pageHost.setScaleY(s);
                pageHost.setAlpha(Math.max(0f, Math.min(1f, 0.3f + 0.7f * v)));
            });
        }
        returnSpring.snap(0f);
        returnSpring.to(1f);
    }

    // ------------------------------------------------------------ UI

    private void buildUi() {
        ambient = new Ambient(this);

        pageHost = new FrameLayout(this);
        pageHost.setClipChildren(false);
        FrameLayout.LayoutParams ph = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        ph.topMargin = dp(86);
        ambient.addView(pageHost, ph);

        nav = new NavBar(this, this::navSelected);
        nav.setAmbient(ambient);
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(NavBar.widthPx(), NavBar.heightPx(), Gravity.TOP | Gravity.START);
        np.leftMargin = dp(Theme.SAFE_SIDE);
        np.topMargin = dp(Theme.SAFE_VERT - 2);
        ambient.addView(nav, np);

        status = new StatusBar(this, this::statusActivated);
        status.setAmbient(ambient);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(StatusBar.widthPx(), StatusBar.heightPx(), Gravity.TOP | Gravity.END);
        cp.rightMargin = dp(Theme.SAFE_SIDE);
        cp.topMargin = dp(Theme.SAFE_VERT - 2);
        ambient.addView(status, cp);

        banner = new Banner(this);
        banner.setAmbient(ambient);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(dp(832), dp(Banner.H), Gravity.TOP | Gravity.START);
        bp.leftMargin = dp(Theme.SAFE_SIDE);
        bp.topMargin = dp(Theme.SAFE_VERT - 2);
        ambient.addView(banner, bp);

        hintView = Theme.text(new TextView(this), 13, Theme.TEXT3, 500);
        FrameLayout.LayoutParams hv = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        hv.rightMargin = dp(Theme.SAFE_SIDE);
        hv.bottomMargin = dp(Theme.SAFE_VERT - 6);
        ambient.addView(hintView, hv);

        trans = new Spring(330f, 0.86f, 1f, 0.002f, v -> {
            float p = Math.max(0f, Math.min(1f, v));
            if (incoming != null) {
                incoming.setTranslationX(dirSign * dp(90) * (1f - v));
                incoming.setAlpha(p);
            }
            if (outgoing != null) {
                outgoing.setTranslationX(-dirSign * dp(50) * p);
                outgoing.setAlpha(1f - p);
            }
            if (v == 1f) {
                if (outgoing != null) {
                    pageHost.removeView(outgoing);
                    outgoing.setTranslationX(0f);
                    outgoing.setAlpha(1f);
                }
                if (incoming != null) {
                    incoming.setTranslationX(0f);
                    incoming.setAlpha(1f);
                }
                incoming = null;
                outgoing = null;
            }
        });

        setContentView(ambient);
    }

    /** The navigation bar moved its selection. */
    private void navSelected(int i) { selectTab(i); }

    private void selectTab(int i) {
        if (i == cur) return;
        int prev = cur;
        cur = i;
        showPage(prev, i);
        OmniLog.state("tab=" + TABS[i]);
        hintChanged();
    }

    private void showPage(int prev, int to) {
        Page in = pages[to];
        endPrewarm();
        if (trans.isRunning()) trans.snap(1f);
        if (prev >= 0) pages[prev].onHidden();
        in.resetScroll();
        if (prev < 0) {
            pageHost.addView(in, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            in.onShown();
            shown = to;
            return;
        }
        outgoing = pages[prev];
        incoming = in;
        dirSign = to > prev ? 1 : -1;
        pageHost.addView(in, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        trans.snap(0f);
        trans.to(1f);
        in.onShown();
        shown = to;
    }
}
