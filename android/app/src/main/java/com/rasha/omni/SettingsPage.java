package com.rasha.omni;

import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import static com.rasha.omni.Theme.dp;

/** Settings as an editorial list: a big title on the left, one quiet line per setting on the right. */
final class SettingsPage extends Page {
    static final String[] NAMES = {"Network", "Display", "Sound", "Apps", "Storage", "Date & time", "About", "All settings"};
    static final String[] DESC = {"Wi-Fi and Ethernet", "Resolution and picture", "Volume and outputs",
            "Permissions and defaults", "Space and drives", "Clock and time zone", "Model and software", "The full Android TV settings"};
    static final String[] ACTIONS = {
            Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SOUND_SETTINGS,
            Settings.ACTION_APPLICATION_SETTINGS, Settings.ACTION_INTERNAL_STORAGE_SETTINGS,
            Settings.ACTION_DATE_SETTINGS, Settings.ACTION_DEVICE_INFO_SETTINGS, Settings.ACTION_SETTINGS};

    SettingsPage(Host h) { super(h); }

    @Override void refresh(String fp, String fl) {
        col.removeAllViews();
        LinearLayout row = new LinearLayout(host.context());
        row.setClipChildren(false);

        LinearLayout left = new LinearLayout(host.context());
        left.setOrientation(LinearLayout.VERTICAL);
        TextView t = Theme.text(new TextView(host.context()), 60, Theme.TEXT, 800);
        t.setText(Tr.t("Settings"));
        t.setLetterSpacing(-0.02f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(0);
        left.addView(t, tl);
        TextView s = Theme.text(new TextView(host.context()), 14, Theme.TEXT2, 450);
        s.setSingleLine(false);
        s.setText(Tr.t("Shortcuts into the TV's system settings, and OMNI's own options."));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = dp(12);
        left.addView(s, sl);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT);
        ll.leftMargin = dp(64);
        ll.topMargin = dp(40);
        row.addView(left, ll);

        LinearLayout list = new LinearLayout(host.context());
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.addView(item("Control Center", "Status and shortcuts", false, host::openControlCenter));
        list.addView(item("Welcome & setup", "Run the setup again", false, host::openWelcome));
        list.addView(item("Language", Tr.NAMES[Tr.lang], false, host::chooseLanguage));
        list.addView(item("Simple mode", UserMode.simple(host.context()) ? "On" : "Off", false, host::toggleSimple));
        list.addView(item("Zoom when opening", UserMode.zoom(host.context()) ? "On" : "Off", false, host::toggleZoom));
        list.addView(item("Quick access", "Choose what is on your Home row", false, host::openQuickAccess));
        list.addView(item("Remote buttons", "INFO, colour keys, mic", false, host::openRemoteButtons));
        if (!UserMode.simple(host.context())) list.addView(item("Server", "Home server status", false, () -> openOwn("com.rasha.omni.server.ServerActivity")));
        list.addView(item("Gallery", "Photos, videos, ambient display settings", false, () -> openOwn("com.rasha.omni.gallery.GalleryActivity")));
        list.addView(item("Ambient display", "Start the photograph and clock now", false, () -> openOwn("com.rasha.omni.gallery.SlideshowActivity")));
        list.addView(item("Updates", updatesLine(), false, this::updatesMenu));
        if (CrashGuard.report(host.context()) != null) list.addView(item("Crash report", "What OMNI recorded when it last crashed", false, this::showCrash));
        for (int i = 0; i < NAMES.length; i++) {
            final String action = ACTIONS[i];
            list.addView(item(NAMES[i], DESC[i], false, () -> openSetting(action)));
        }
        View gap = new View(host.context());
        list.addView(gap, new LinearLayout.LayoutParams(1, dp(10)));
        list.addView(item("Wallpaper", "Photographs behind OMNI", false, this::wallpaperMenu));
        list.addView(item("Reset layout", "Restore OMNI's default order", true, this::confirmReset));
        // the rows sit on a dark glass panel so they stay readable over any photograph
        android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
        panel.setColor(0xB30A0A0A);
        panel.setCornerRadius(dp(24));
        panel.setStroke(Math.max(1, dp(1)), 0x30FFFFFF);
        list.setBackground(panel);
        list.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(dp(500), ViewGroup.LayoutParams.WRAP_CONTENT);
        rl.topMargin = dp(8);
        row.addView(list, rl);
        col.addView(row);
    }

    private SettingRow item(String name, String desc, boolean destructive, final Runnable act) {
        final SettingRow r = new SettingRow(host.context(), Tr.t(name), Tr.t(desc));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        r.setOnFocusChangeListener((v, has) -> {
            if (!has) return;
            revealCenter(r);
        });
        r.setOnClickListener(v -> act.run());
        return r;
    }

    void openWallpaperMenu() { wallpaperMenu(); }

    private void wallpaperMenu() {
        final Wallpaper w = host.wallpaper();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        labels.add("Change: " + WallpaperRules.MODE_LABELS[w.mode()]);
        acts.add(() -> host.closeSheet(this::scheduleMenu));
        labels.add("Source: " + (w.source() == Wallpaper.SRC_BING ? "Bing daily photo" : "Wikimedia Commons"));
        acts.add(() -> host.closeSheet(() -> {
            w.setSource(w.source() == Wallpaper.SRC_BING ? Wallpaper.SRC_WIKIMEDIA : Wallpaper.SRC_BING);
            host.flash("Wallpaper source changed", w.source() == Wallpaper.SRC_BING ? "Bing daily photo (personal use)" : "Wikimedia Commons (open licences)");
        }));
        labels.add("Get new photos now");
        acts.add(() -> host.closeSheet(() -> { w.refreshIfDue(true); host.flash("Looking for new photos", "They appear on the next change"); }));
        Wallpaper.Entry e = w.shown();
        String credit = e == null ? "No photograph is showing." : (e.title.isEmpty() ? "Photograph" : e.title) + (e.artist.isEmpty() ? "" : " by " + e.artist) + ". " + e.license + (e.source.isEmpty() ? "" : ", " + e.source) + ".";
        host.showSheet("Wallpaper", credit, labels, acts);
    }

    private void scheduleMenu() {
        final Wallpaper w = host.wallpaper();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        for (int m = 0; m < WallpaperRules.MODE_LABELS.length; m++) {
            final int mode = m;
            labels.add((m == w.mode() ? "\u2713  " : "") + WallpaperRules.MODE_LABELS[m]);
            acts.add(() -> host.closeSheet(() -> { w.setMode(mode); host.flash("Wallpaper: " + WallpaperRules.MODE_LABELS[mode], mode == WallpaperRules.OFF ? "Plain black background" : "The picture changes on this schedule only"); }));
        }
        host.showSheet("Change wallpaper", "The picture never changes while you navigate.", labels, acts);
    }

    /** Destructive: confirm first, with Cancel as the focused default (Android TV guidance). */
    private void confirmReset() {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        labels.add("Cancel");
        acts.add(() -> host.closeSheet(null));
        labels.add("Reset layout");
        acts.add(() -> host.closeSheet(() -> {
            host.repo().resetLayout();
            host.refreshAll(null, null);
            Toast.makeText(host.context(), "Layout reset", Toast.LENGTH_SHORT).show();
        }));
        host.showSheet("Reset layout?", "Favorites order, pins, hidden apps and recents will be cleared. Apps stay installed.", labels, acts);
    }

    /** A screen that is part of OMNI (it runs in its own process; there is no separate app). */
    private void openOwn(String className) {
        try { host.context().startActivity(new Intent().setClassName(host.context().getPackageName(), className)); }
        catch (Exception e) { Toast.makeText(host.context(), "Not available", Toast.LENGTH_SHORT).show(); }
    }

    private String updatesLine() {
        Updater u = Updater.get(host.context());
        UpdateRules.Release r = u.available();
        if (r != null) return "OMNI " + r.tag.replaceFirst("^v", "") + " " + Tr.t(u.downloaded() != null && !u.personalBuild() ? "is ready to install" : "is available");
        return Tr.t("Installed") + " " + u.installed() + (u.lastCheck() > 0 ? ", " + Tr.t("up to date") : "");
    }

    private static String ago(long then) {
        long m = Math.max(0, (System.currentTimeMillis() - then) / 60000);
        return m < 2 ? "just now" : m < 120 ? m + " min ago" : m < 2880 ? (m / 60) + " h ago" : (m / 1440) + " days ago";
    }

    /** Updates: what is installed, what was found, and one press to install it. The system installer still asks you to confirm. */
    private void updatesMenu() {
        final Updater u = Updater.get(host.context());
        final UpdateRules.Release r = u.available();
        final boolean personal = u.personalBuild();
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        StringBuilder sub = new StringBuilder("Installed " + u.installed() + (u.lastCheck() > 0 ? ". Checked " + ago(u.lastCheck()) + "." : ". Not checked yet."));
        if (r != null) {
            sub.append("\nNew: ").append(r.tag.replaceFirst("^v", "")).append(r.size > 0 ? " (" + UpdateRules.size(r.size) + ")" : "");
            String n = UpdateRules.notes(r.body, 3, 260);
            if (!n.isEmpty()) sub.append("\n").append(n);
            if (personal) sub.append("\nThis is a personal build: install the new one yourself so your channel list is kept.");
        }
        if (u.lastError() != null && !u.busy()) sub.append("\n").append(u.lastError());
        if (r != null && !personal) {
            if (u.downloaded() != null) {
                labels.add("Install now");
                acts.add(() -> host.closeSheet(() -> startInstall(u)));
            } else {
                labels.add("Download now");
                acts.add(() -> host.closeSheet(() -> { host.flash("Downloading", "OMNI " + r.tag.replaceFirst("^v", "")); u.download(m -> { host.flash("Update", m); host.refreshAll(null, null); }); }));
            }
        }
        labels.add("Check for updates now");
        acts.add(() -> host.closeSheet(() -> { host.flash("Checking for updates", "Asking GitHub's public releases"); u.check(m -> { host.flash("Updates", m); host.refreshAll(null, null); }); }));
        labels.add("Check automatically: " + (u.auto() ? "On" : "Off"));
        acts.add(() -> host.closeSheet(() -> { u.setAuto(!u.auto()); host.refreshAll(null, null); updatesMenu(); }));
        labels.add("Include pre-releases: " + (u.pre() ? "On" : "Off"));
        acts.add(() -> host.closeSheet(() -> { u.setPre(!u.pre()); host.refreshAll(null, null); updatesMenu(); }));
        labels.add("Close");
        acts.add(() -> host.closeSheet(null));
        host.showSheet("Updates", sub.toString(), labels, acts);
    }

    private void startInstall(Updater u) {
        android.content.Context c = host.context();
        if (Build.VERSION.SDK_INT >= 26 && !c.getPackageManager().canRequestPackageInstalls()) {       // the one-time "install unknown apps" switch for OMNI
            try {
                c.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + c.getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                host.flash("Allow OMNI to install updates", "Then press Install now again");
            } catch (Exception e) { host.flash("Cannot install updates", "This TV has no 'install unknown apps' screen"); }
            return;
        }
        u.install(m -> host.flash("Update", m));
    }

    private void showCrash() {
        String r = CrashGuard.report(host.context());
        if (r == null) return;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> acts = new ArrayList<>();
        labels.add("Close");
        acts.add(() -> host.closeSheet(null));
        labels.add("Clear the report");
        acts.add(() -> host.closeSheet(() -> { CrashGuard.clear(host.context()); host.refreshAll(null, null); }));
        String[] ls = r.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(ls.length, 9); i++) sb.append(ls[i].length() > 110 ? ls[i].substring(0, 110) : ls[i]).append('\n');
        host.showSheet("Last crash", sb + "Photograph this or paste it into a GitHub issue.", labels, acts);
    }

    private void openSetting(String action) {
        try {
            host.context().startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(host.context(), "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    @Override void focusFirst() { focusSoon(col); }
}
