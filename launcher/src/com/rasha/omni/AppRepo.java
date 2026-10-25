package com.rasha.omni;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Installed apps plus the user's layout: order, pins, hidden apps, recents and launch counts. */
final class AppRepo {
    static final class App {
        ComponentName cn;
        String pkg;
        String label;
        /** Pre-rounded, pre-cropped artwork for every tile shape (see IconArt). */
        IconArt art;
        boolean system;
        /** ApplicationInfo.category (API 26+), -1 when the app does not declare one. */
        int category = -1;
        /** PackageInfo.firstInstallTime (ms). */
        long installed;
        /** CategoryRules.* from the evidence the platform gives (NONE when there is none). */
        int cat = CategoryRules.NONE;
        /** The app's brand colour as ambient light (ARGB), or 0 when its artwork has no real colour. Cached per app version. */
        int color;
    }

    private final Context ctx;
    private final SharedPreferences sp;
    private final float density;
    private volatile Map<String, App> apps = new LinkedHashMap<>();
    final List<String> order = new ArrayList<>();
    final List<String> pins = new ArrayList<>();
    final List<String> recents = new ArrayList<>();
    final Set<String> hidden = new HashSet<>();
    /** Set while the user is dragging an app to a new place (hold OK, Move). */
    String movingPkg;
    String movingList;

    AppRepo(Context c) {
        ctx = c;
        density = c.getResources().getDisplayMetrics().density;
        sp = c.getSharedPreferences("omni", Context.MODE_PRIVATE);
        read("order", order);
        read("pins", pins);
        read("recents", recents);
        List<String> h = new ArrayList<>();
        read("hidden", h);
        hidden.addAll(h);
    }

    private void read(String key, List<String> out) {
        String s = sp.getString(key, "");
        if (s.isEmpty()) return;
        Collections.addAll(out, s.split(","));
    }

    private static String join(java.util.Collection<String> l) {
        StringBuilder b = new StringBuilder();
        for (String s : l) b.append(b.length() == 0 ? "" : ",").append(s);
        return b.toString();
    }

    void save() {
        sp.edit().putString("order", join(order)).putString("pins", join(pins))
                .putString("recents", join(recents)).putString("hidden", join(hidden)).apply();
    }

    // ------------------------------------------------------------ scanning

    /** Scans on a background thread, then calls done on that thread. */
    void scan(final Runnable done) {
        new Thread(() -> {
            PackageManager pm = ctx.getPackageManager();
            Map<String, App> map = new LinkedHashMap<>();
            evidencePublishers = TvPublishers.query(ctx);
            evidenceVideo = handlers(pm, "video/mp4");
            evidenceAudio = handlers(pm, "audio/mpeg");
            collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, map);
            collect(pm, Intent.CATEGORY_LAUNCHER, false, map);
            apps = map;
            done.run();
        }).start();
    }

    private java.util.Set<String> evidencePublishers = new HashSet<>();
    private java.util.Set<String> evidenceVideo = new HashSet<>();
    private java.util.Set<String> evidenceAudio = new HashSet<>();

    /** Packages that offer to open a file of this type (requires the matching <queries> entry in the manifest). */
    private static java.util.Set<String> handlers(PackageManager pm, String mime) {
        java.util.Set<String> out = new HashSet<>();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse("content://omni.probe/file"), mime);
            for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) out.add(ri.activityInfo.packageName);
        } catch (Exception ignored) { }
        return out;
    }

    private void collect(PackageManager pm, String category, boolean tv, Map<String, App> out) {
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
            try {
                ActivityInfo ai = ri.activityInfo;
                String pkg = ai.packageName;
                if (out.containsKey(pkg) || AppFilter.isHidden(pkg)) continue;
                int fl = ai.applicationInfo.flags;
                boolean system = (fl & ApplicationInfo.FLAG_SYSTEM) != 0
                        && (fl & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
                if (!tv && system) continue;
                App a = new App();
                a.cn = new ComponentName(pkg, ai.name);
                a.pkg = pkg;
                a.system = system;
                a.label = String.valueOf(ri.loadLabel(pm));
                if (android.os.Build.VERSION.SDK_INT >= 26) a.category = ai.applicationInfo.category;
                try { a.installed = pm.getPackageInfo(pkg, 0).firstInstallTime; } catch (Exception ignored) { }
                a.art = IconArt.build(ri.loadIcon(pm), ai.loadBanner(pm), density);
                long upd = 0;
                try { upd = pm.getPackageInfo(pkg, 0).lastUpdateTime; } catch (Exception ignored) { }
                a.color = brandColor(pkg, upd, a.art);
                boolean game = (fl & ApplicationInfo.FLAG_IS_GAME) != 0;
                a.cat = CategoryRules.classify(a.category, game, evidencePublishers.contains(pkg), evidenceVideo.contains(pkg), evidenceAudio.contains(pkg));
                if (android.util.Log.isLoggable("OmniState", android.util.Log.DEBUG)) {
                    OmniLog.state("icon pkg=" + pkg + " kind=" + a.art.kind + " " + a.art.info);
                }
                out.put(pkg, a);
            } catch (Exception ignored) { }
        }
    }


    /** Brand colour from the app's own artwork, analysed once per app version and then read from the cache. */
    private int brandColor(String pkg, long updated, IconArt art) {
        String key = "ac_" + pkg;
        String saved = sp.getString(key, "");
        int cut = saved.indexOf(':');
        if (cut > 0) {
            try { if (Long.parseLong(saved.substring(0, cut)) == updated) return Integer.parseInt(saved.substring(cut + 1)); } catch (NumberFormatException ignored) { }
        }
        int color = 0;
        try {
            android.graphics.Bitmap src = art.kind == IconArt.Kind.BANNER && art.tile != null ? art.tile : art.icon;
            android.graphics.Bitmap small = android.graphics.Bitmap.createScaledBitmap(src, 16, 16, true);
            int[] px = new int[256];
            small.getPixels(px, 0, 16, 0, 0, 16, 16);
            color = Dominant.pick(px);
        } catch (RuntimeException ignored) { }
        sp.edit().putString(key, updated + ":" + color).apply();
        return color;
    }

    // ------------------------------------------------------------ lists

    App get(String pkg) { return apps.get(pkg); }

    int count(String pkg) { return sp.getInt("n_" + pkg, 0); }

    /** Every app: user order first, then the rest alphabetically. */
    List<App> ordered() {
        Map<String, App> m = OmniLog.forceEmpty() ? new LinkedHashMap<String, App>() : apps;
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String p : order) {
            App a = m.get(p);
            if (a != null && seen.add(p)) out.add(a);
        }
        List<App> rest = new ArrayList<>();
        for (App a : m.values()) if (!seen.contains(a.pkg)) rest.add(a);
        Collections.sort(rest, (x, y) -> x.label.compareToIgnoreCase(y.label));
        out.addAll(rest);
        return out;
    }

    /** Apps installed in the last 45 days, newest first (real install times). */
    List<App> recentlyInstalled() {
        long cutoff = System.currentTimeMillis() - 45L * 24 * 3600 * 1000;
        List<App> out = new ArrayList<>();
        for (App a : visible()) if (a.installed >= cutoff) out.add(a);
        Collections.sort(out, (x, y) -> Long.compare(y.installed, x.installed));
        return new ArrayList<>(out.subList(0, Math.min(10, out.size())));
    }

    List<App> hiddenApps() {
        List<App> out = new ArrayList<>();
        for (App a : ordered()) if (hidden.contains(a.pkg)) out.add(a);
        return out;
    }

    /** Alphabetical, so the "All apps" shelf is predictable and findable. */
    List<App> alphabetical() {
        List<App> out = visible();
        Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
        return out;
    }

    List<App> byCategory(int cat) {
        List<App> out = new ArrayList<>();
        for (App a : alphabetical()) if (a.cat == cat) out.add(a);
        return out;
    }

    List<App> visible() {
        List<App> out = new ArrayList<>();
        for (App a : ordered()) if (!hidden.contains(a.pkg)) out.add(a);
        return out;
    }

    /** Pinned apps; before anything is pinned, the most used (or first) eight. */
    List<App> favorites() {
        List<App> out = new ArrayList<>();
        if (!pins.isEmpty()) {
            for (String p : pins) {
                App a = apps.get(p);
                if (a != null && !hidden.contains(p)) out.add(a);
            }
            return out;
        }
        List<App> v = visible();
        Collections.sort(v, (x, y) -> Integer.compare(count(y.pkg), count(x.pkg)));
        return new ArrayList<>(v.subList(0, Math.min(8, v.size())));
    }

    List<App> continueRow() {
        List<App> out = new ArrayList<>();
        for (String p : recents) {
            App a = apps.get(p);
            if (a != null && !hidden.contains(p)) out.add(a);
        }
        return out;
    }

    List<App> mostUsed() {
        List<App> v = new ArrayList<>();
        for (App a : visible()) if (count(a.pkg) > 0) v.add(a);
        Collections.sort(v, (x, y) -> Integer.compare(count(y.pkg), count(x.pkg)));
        return new ArrayList<>(v.subList(0, Math.min(10, v.size())));
    }

    // ------------------------------------------------------------ edits

    /** Last launch from OMNI, kept so focus can be restored even if the system kills this process while the app runs. */
    private static final long RESTORE_WINDOW_MS = 10L * 60 * 1000;

    /** {pkg, shelfId} when an app was launched from OMNI within the last ten minutes, else null. */
    String[] recentLaunch() {
        String pkg = sp.getString("lp", null), list = sp.getString("ll", null);
        long at = sp.getLong("lt", 0);
        if (pkg == null || list == null || System.currentTimeMillis() - at > RESTORE_WINDOW_MS) return null;
        return new String[]{pkg, list};
    }

    void clearRecentLaunch() { sp.edit().remove("lp").remove("ll").remove("lt").apply(); }

    void launched(String pkg, String shelfId) {
        sp.edit().putInt("n_" + pkg, count(pkg) + 1).putString("lp", pkg).putString("ll", shelfId)
                .putLong("lt", System.currentTimeMillis()).apply();
        recents.remove(pkg);
        recents.add(0, pkg);
        while (recents.size() > 12) recents.remove(recents.size() - 1);
        save();
    }

    boolean togglePin(String pkg) {
        boolean now = !pins.contains(pkg);
        if (now) {
            if (pins.isEmpty()) for (App a : favorites()) pins.add(a.pkg);
            if (!pins.contains(pkg)) pins.add(pkg);
        } else {
            pins.remove(pkg);
        }
        save();
        return now;
    }

    boolean toggleHidden(String pkg) {
        boolean now = hidden.add(pkg);
        if (!now) hidden.remove(pkg);
        save();
        return now;
    }

    /** Makes the implicit list explicit so it can be reordered. */
    void ensureList(String listId) {
        if (listId.equals("fav") && pins.isEmpty()) {
            for (App a : favorites()) pins.add(a.pkg);
        } else if (listId.equals("all") && order.isEmpty()) {
            for (App a : ordered()) order.add(a.pkg);
        }
    }

    void move(String listId, String pkg, int delta) {
        List<String> l = listId.equals("fav") ? pins : order;
        if (ListOps.move(l, pkg, delta) >= 0) save();
    }

    /** Stores the final order of the Favorites shelf after a move. */
    void setFavoritesOrder(List<String> ids) {
        pins.clear();
        pins.addAll(ids);
        save();
    }

    void resetLayout() {
        order.clear();
        pins.clear();
        recents.clear();
        hidden.clear();
        movingPkg = null;
        save();
    }
}
