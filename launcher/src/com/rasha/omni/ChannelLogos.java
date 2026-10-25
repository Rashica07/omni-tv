package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Map;

/**
 * Channel logos shipped as small local files (assets/logos, made by tools/fetch_logos.py from the iptv-org database; origins in SOURCES.json).
 * Catalogue channels are found by id. The TV's own antenna channels only have a name, so they are matched by exact normalised name against Italian
 * channels. No match means no logo: the card stays the generated one. Never guessed, never fuzzy.
 */
final class ChannelLogos {
    private ChannelLogos() { }

    private static Context ctx;
    private static JSONObject ids, names;
    private static final Map<String, Bitmap> BITMAPS = new HashMap<>();

    static synchronized void init(Context c) { if (ctx == null) ctx = c.getApplicationContext(); }

    static String norm(String s) {
        String d = Normalizer.normalize(s.toLowerCase(java.util.Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i++) { char ch = d.charAt(i); if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) b.append(ch); }
        return b.toString();
    }

    private static void load() {
        if (ids != null || ctx == null) return;
        try (InputStream in = ctx.getAssets().open("logos/index.json")) {
            byte[] buf = new byte[in.available()];
            int n = 0; while (n < buf.length) { int r = in.read(buf, n, buf.length - n); if (r < 0) break; n += r; }
            JSONObject o = new JSONObject(new String(buf, 0, n, "UTF-8"));
            ids = o.getJSONObject("ids"); names = o.getJSONObject("names");
        } catch (Exception e) { ids = new JSONObject(); names = new JSONObject(); }
    }

    /** The logo for a channel, or null. [tuner] channels (no country) are matched by name; all others by catalogue id only. */
    static synchronized Bitmap find(String id, String name, boolean tuner) {
        load();
        if (ids == null) return null;
        String file = tuner ? null : ids.optString(id, null);
        if (tuner && name != null) {
            String n = norm(name);
            file = names.optString(n, null);
            if (file == null && n.endsWith("hd")) file = names.optString(n.substring(0, n.length() - 2), null);
        }
        if (file == null || file.isEmpty() || file.contains("/")) return null;
        Bitmap b = BITMAPS.get(file);
        if (b == null && !BITMAPS.containsKey(file)) {
            try (InputStream in = ctx.getAssets().open("logos/" + file)) { b = BitmapFactory.decodeStream(in); } catch (Exception e) { b = null; }
            BITMAPS.put(file, b);
        }
        return b;
    }
}
