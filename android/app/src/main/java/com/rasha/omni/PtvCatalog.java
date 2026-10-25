package com.rasha.omni;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Android I/O for the channel catalogue, kept apart from the UI and from the pure model ({@link PtvModel}). Order of trust: a catalogue fetched
 * from the home server and cached on disk, else the copy bundled in assets. Only schema-2 files (stable source ids) are accepted: an older cached
 * file is deleted, so a stale cache can never feed the wrong data to the new pipeline. User data (favourites, usage) is kept and migrated.
 */
final class PtvCatalog {
    private static final int MAX_BYTES = 2 << 20;
    private static final String CACHE = "ptv-catalog.json";

    private PtvCatalog() { }

    // ------------------------------------------------------------ parsing

    /** Parses schema-2 catalogue JSON into channels; anything else (including the old flat format) yields an empty list. */
    static List<PtvModel.Channel> parse(String json) {
        try {
            JSONObject root = new JSONObject(json.trim());
            if (root.optInt("schema", 0) != 2) return new ArrayList<>();
            JSONArray rows = root.getJSONArray("channels");
            List<Map<String, Object>> list = new ArrayList<>();
            for (int i = 0; i < rows.length() && i < 5000; i++) {
                JSONObject o = rows.optJSONObject(i);
                if (o != null) list.add(toMap(o));
            }
            return PtvModel.fromRows(list);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static Map<String, Object> toMap(JSONObject o) throws Exception {
        Map<String, Object> m = new HashMap<>();
        for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
            String k = it.next();
            m.put(k, toValue(o.get(k)));
        }
        return m;
    }

    private static Object toValue(Object v) throws Exception {
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> l = new ArrayList<>();
            for (int i = 0; i < a.length() && i < 200; i++) l.add(toValue(a.get(i)));
            return l;
        }
        return JSONObject.NULL.equals(v) ? null : v;
    }

    // ------------------------------------------------------------ loading

    /** Server cache if present and valid, else the bundled copy. A cache that is not schema 2 is deleted. */
    static List<PtvModel.Channel> load(Context c) {
        File f = new File(c.getFilesDir(), CACHE);
        if (f.exists()) {
            List<PtvModel.Channel> l = parse(read(f));
            if (!l.isEmpty()) return l;
            f.delete();
        }
        try (InputStream in = c.getAssets().open("ptv-catalog.json")) {
            return parse(new String(readAll(in), "UTF-8"));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Stores a server-supplied catalogue only if it parses to something; a bad download never replaces a good cache. */
    static boolean saveFromServer(Context c, String json) {
        if (json == null || json.length() > MAX_BYTES || parse(json).isEmpty()) return false;
        File tmp = new File(c.getFilesDir(), CACHE + ".tmp");
        try (FileOutputStream o = new FileOutputStream(tmp)) {
            o.write(json.getBytes("UTF-8"));
        } catch (Exception e) {
            return false;
        }
        return tmp.renameTo(new File(c.getFilesDir(), CACHE));
    }

    private static String read(File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            return new String(readAll(in), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && b.size() < MAX_BYTES) b.write(buf, 0, n);
        return b.toByteArray();
    }

    // ------------------------------------------------------------ user data

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("omniptv", Context.MODE_PRIVATE); }

    /** Favourites are channel ids, in the order they were pinned. */
    static Set<String> favorites(Context c) {
        Set<String> s = new LinkedHashSet<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("fav_ids", "[]"));
            for (int i = 0; i < a.length(); i++) s.add(a.getString(i));
        } catch (Exception ignored) { }
        return s;
    }

    static boolean toggleFavorite(Context c, PtvModel.Channel ch) {
        Set<String> s = favorites(c);
        boolean now = !s.remove(ch.id);
        if (now) s.add(ch.id);
        prefs(c).edit().putString("fav_ids", new JSONArray(s).toString()).apply();
        return now;
    }

    static PtvUsage usage(Context c) { return PtvUsage.parse(prefs(c).getString("usage", "")); }

    /** Records a finished stretch of playback (ignored below the session threshold). */
    static void recordWatch(Context c, String id, long watchedMs) {
        PtvUsage u = usage(c);
        if (u.record(id, watchedMs, System.currentTimeMillis())) prefs(c).edit().putString("usage", u.serialize()).apply();
    }

    /**
     * Older builds stored favourites and recents as "name|country". Map them to the new ids by exact name and country so nothing the user set is
     * lost; unmatched old entries are dropped (they cannot be attributed to a channel safely). Runs once.
     */
    static void migrate(Context c, List<PtvModel.Channel> channels) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("migrated_ids", false) || channels.isEmpty()) return;
        Map<String, String> byOld = new HashMap<>();
        for (PtvModel.Channel ch : channels) byOld.put(ch.name + "|" + ch.country, ch.id);
        Set<String> fav = favorites(c);
        try {
            JSONArray old = new JSONArray(p.getString("fav", "[]"));
            for (int i = 0; i < old.length(); i++) { String id = byOld.get(old.getString(i)); if (id != null) fav.add(id); }
        } catch (Exception ignored) { }
        p.edit().putString("fav_ids", new JSONArray(fav).toString()).remove("fav").remove("recent").putBoolean("migrated_ids", true).apply();
    }
}
