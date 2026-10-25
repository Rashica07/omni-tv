package com.rasha.omni;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The wallpaper engine. Photographs are cached on disk (up to 12, newest kept); one is chosen deterministically by the schedule (see
 * {@link WallpaperRules}), decoded and composed off the UI thread, and handed to {@link Ambient} to cross-fade in. Nothing waits on the network: a
 * bundled public-domain photograph covers a first or offline start. Sources are configurable: Wikimedia Commons featured landscapes (open
 * licences, credit shown in Settings) by default, or Bing's daily photograph (optional; copyright belongs to the photographers, personal use).
 */
final class Wallpaper {
    static final int SRC_WIKIMEDIA = 0, SRC_BING = 1;
    private static final int W = 1280, H = 720;
    private static final String UA = "OMNI-TV/1.0 (personal Android TV launcher)";

    static final class Entry {
        String file, title = "", artist = "", license = "", source = "", page = "";
    }

    private final Context ctx;
    private final Ambient amb;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final File dir;
    private String shownFile = "";
    private Entry shownEntry;
    private boolean refreshing;

    Wallpaper(Context c, Ambient a) {
        ctx = c.getApplicationContext();
        amb = a;
        prefs = ctx.getSharedPreferences("wallpaper", Context.MODE_PRIVATE);
        dir = new File(ctx.getFilesDir(), "wallpapers");
        dir.mkdirs();
        prefs.edit().putInt("launches", prefs.getInt("launches", 0) + 1).apply();
    }

    int mode() { return prefs.getInt("mode", WallpaperRules.DAILY); }

    void setMode(int m) { prefs.edit().putInt("mode", m).apply(); shownFile = ""; apply(); }

    int source() { return prefs.getInt("source", SRC_WIKIMEDIA); }

    void setSource(int s) { prefs.edit().putInt("source", s).putLong("last_refresh", 0).apply(); refreshIfDue(true); }

    Entry shown() { return shownEntry; }

    // ------------------------------------------------------------ choosing and showing

    /** Chooses the image for now (deterministic), composes it in the background and cross-fades it in. Cheap when nothing changed. */
    void apply() {
        io.execute(() -> {
            if (mode() == WallpaperRules.OFF || OmniLog.flag("OmniNoWall")) { ui.post(() -> amb.setWallpaper(null, null, null, null)); shownFile = ""; shownEntry = null; return; }
            List<Entry> list = entries();
            Entry pick = null;
            long nowMs = System.currentTimeMillis();
            int i = WallpaperRules.index(list.size(), mode(), nowMs, TimeZone.getDefault().getOffset(nowMs), prefs.getInt("launches", 0));
            // the choice is locked for its schedule slot: caching more photos mid-day must not change the picture on screen
            String slot = mode() + ":" + WallpaperRules.slot(mode(), nowMs, TimeZone.getDefault().getOffset(nowMs), prefs.getInt("launches", 0));
            String lockedFile = prefs.getString("slot_key", "").equals(slot) ? prefs.getString("slot_file", "") : "";
            for (Entry e : list) if (e.file.equals(lockedFile)) pick = e;
            if (pick == null && i >= 0) { pick = list.get(i); prefs.edit().putString("slot_key", slot).putString("slot_file", pick.file).apply(); }
            String key = pick == null ? "asset" : pick.file;
            if (key.equals(shownFile)) return;
            Bitmap src = pick == null ? decodeAsset() : decodeFile(new File(dir, pick.file));
            if (src == null && pick != null) { src = decodeAsset(); key = "asset"; pick = null; }       // an unreadable cached file: fall back, never blank
            if (src == null) return;
            final Composed comp = compose(src);
            src.recycle();
            shownFile = key;
            shownEntry = pick != null ? pick : fallbackEntry();
            OmniLog.state("wallpaper shown=" + key + " mode=" + mode());
            ui.post(() -> amb.setWallpaper(comp.wall, comp.soft, comp.mid, comp.band));
        });
    }

    private Entry fallbackEntry() {
        Entry e = new Entry();
        e.file = "asset";
        try (InputStream in = ctx.getAssets().open("wallpaper-fallback.json")) {
            JSONObject j = new JSONObject(new String(readAll(in, 20000), "UTF-8"));
            e.title = j.optString("title");
            e.license = j.optString("license");
            e.source = j.optString("source");
            e.page = j.optString("page");
        } catch (Exception ignored) { }
        return e;
    }

    private Bitmap decodeAsset() {
        try (InputStream in = ctx.getAssets().open("wallpaper-fallback.jpg")) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap decodeFile(File f) {
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth < 800 || o.outHeight < 400) return null;
        int s = 1;
        while (o.outWidth / (s * 2) >= W) s *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = s;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    // ------------------------------------------------------------ composing

    static final class Composed {
        Bitmap wall, mid, soft;      // soft = the picture already blended 70% toward its blurred copy
        float[] band = new float[8];
    }

    /** Cover-fits the photo to 1280x720, bakes the readability scrims (strength follows the photo's own brightness), and makes the small blurred copy glass samples. */
    static Composed compose(Bitmap src) {
        Bitmap cover = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(cover);
        float s = Math.max(W / (float) src.getWidth(), H / (float) src.getHeight());
        Matrix m = new Matrix();
        m.setScale(s, s);
        m.postTranslate((W - src.getWidth() * s) / 2f, (H - src.getHeight() * s) * 0.55f);
        Paint f = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, m, f);
        float[] raw = bands(cover);
        float all = 0;
        for (float v : raw) all += v / 8f;
        Paint p = new Paint();
        p.setColor(0xFF000000);
        p.setAlpha((int) (LumaVeil.scrim(all) * 0.40f));
        c.drawRect(0, 0, W, H, p);
        int topA = Math.min(235, LumaVeil.scrim((raw[0] + raw[1]) / 2f) + 60);
        p.setShader(new LinearGradient(0, 0, 0, 230, Theme.alpha(0xFF000000, topA), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 230, p);
        int leftA = Math.min(235, LumaVeil.scrim((raw[1] + raw[2] + raw[3]) / 3f) + 85);
        p.setShader(new RadialGradient(200, 250, 720, Theme.alpha(0xFF000000, leftA), 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, 900, 520, p);
        int botA = Math.min(235, LumaVeil.alpha((raw[5] + raw[6] + raw[7]) / 3f) + 70);
        p.setShader(new LinearGradient(0, 360, 0, H, 0x00000000, Theme.alpha(0xFF000000, botA), Shader.TileMode.CLAMP));
        c.drawRect(0, 360, W, H, p);
        Composed out = new Composed();
        out.band = bands(cover);                      // brightness AFTER the scrims: that is what glass sits on
        out.wall = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c2 = new Canvas(out.wall);
        c2.drawBitmap(cover, 0, 0, new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        out.mid = blur(Bitmap.createScaledBitmap(cover, 160, 90, true));
        // the 70%-blurred look is baked once, so showing it later is a single texture draw (an extra full-screen layer cost 34% janky frames)
        out.soft = Bitmap.createBitmap(W, H, Bitmap.Config.RGB_565);
        Canvas c3 = new Canvas(out.soft);
        c3.drawBitmap(out.wall, 0, 0, null);
        Paint bl = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        bl.setAlpha((int) (255 * 0.70f));
        c3.drawBitmap(out.mid, null, new android.graphics.Rect(0, 0, W, H), bl);
        cover.recycle();
        return out;
    }

    private static float[] bands(Bitmap b) {
        float[] out = new float[8];
        int[] px = new int[W];
        for (int i = 0; i < 8; i++) {
            int[] sample = new int[(W / 8) * (H / 8 / 8 + 1)];
            int n = 0;
            for (int y = i * H / 8; y < (i + 1) * H / 8 && n < sample.length; y += 8) {
                b.getPixels(px, 0, W, 0, y, W, 1);
                for (int x = 0; x < W && n < sample.length; x += 8) sample[n++] = px[x];
            }
            out[i] = LumaVeil.luma(java.util.Arrays.copyOf(sample, n));
        }
        return out;
    }

    /** Two box-blur passes over a tiny bitmap: frosted, not pixelated, and trivial to draw stretched. */
    private static Bitmap blur(Bitmap small) {
        int w = small.getWidth(), h = small.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        small.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b[y * w + x] = avg(a, w, h, x, y, true);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) a[y * w + x] = avg(b, w, h, x, y, false);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static int avg(int[] px, int w, int h, int x, int y, boolean horizontal) {
        int r = 0, g = 0, bl = 0, n = 0;
        for (int d = -3; d <= 3; d++) {
            int xx = horizontal ? Math.max(0, Math.min(w - 1, x + d)) : x, yy = horizontal ? y : Math.max(0, Math.min(h - 1, y + d));
            int v = px[yy * w + xx];
            r += (v >> 16) & 255; g += (v >> 8) & 255; bl += v & 255; n++;
        }
        return 0xFF000000 | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
    }

    // ------------------------------------------------------------ the cache

    List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        try {
            File idx = new File(dir, "index.json");
            if (!idx.exists()) return out;
            JSONArray a = new JSONArray(new String(readAll(new java.io.FileInputStream(idx), 200000), "UTF-8"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.file = o.getString("file");
                if (!e.file.matches("wp_[0-9a-f]+\\.jpg") || !new File(dir, e.file).exists()) continue;
                e.title = o.optString("title");
                e.artist = o.optString("artist");
                e.license = o.optString("license");
                e.source = o.optString("source");
                e.page = o.optString("page");
                out.add(e);
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void saveIndex(List<Entry> list) {
        try {
            JSONArray a = new JSONArray();
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("file", e.file).put("title", e.title).put("artist", e.artist).put("license", e.license).put("source", e.source).put("page", e.page);
                a.put(o);
            }
            try (FileOutputStream f = new FileOutputStream(new File(dir, "index.json.tmp"))) { f.write(a.toString().getBytes("UTF-8")); }
            new File(dir, "index.json.tmp").renameTo(new File(dir, "index.json"));
        } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ the network (background only)

    void refreshIfDue(final boolean force) {
        io.execute(() -> {
            long now = System.currentTimeMillis();
            List<Entry> have = entries();
            if (refreshing || (!force && !WallpaperRules.needsRefresh(have.size(), prefs.getLong("last_refresh", 0), now))) return;
            refreshing = true;
            try {
                List<Entry> add = source() == SRC_BING ? fetchBing() : fetchWikimedia(have);
                int added = 0;
                for (Entry e : add) {
                    if (have.size() >= WallpaperRules.MAX_CACHED) {              // drop the oldest, never the one on screen
                        for (int k = 0; k < have.size(); k++) if (!have.get(k).file.equals(shownFile) && !have.get(k).file.equals(prefs.getString("slot_file", ""))) { new File(dir, have.get(k).file).delete(); have.remove(k); break; }
                    }
                    have.add(e);
                    added++;
                }
                if (added > 0) saveIndex(have);
                prefs.edit().putLong("last_refresh", now).apply();
                OmniLog.state("wallpaper refresh added=" + added + " cached=" + have.size());
                if (added > 0) apply();
            } catch (Exception e) {
                OmniLog.state("wallpaper refresh failed " + e.getClass().getSimpleName());
            } finally {
                refreshing = false;
            }
        });
    }

    private List<Entry> fetchWikimedia(List<Entry> have) throws Exception {
        List<Entry> out = new ArrayList<>();
        String list = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&list=categorymembers&cmtitle=Category:Featured_pictures_of_landscapes&cmtype=file&cmlimit=500&cmprop=title", 200000);
        JSONArray members = new JSONObject(list).getJSONObject("query").getJSONArray("categorymembers");
        int n = members.length();
        if (n == 0) return out;
        long day = Math.floorDiv(System.currentTimeMillis(), 86_400_000L);
        StringBuilder titles = new StringBuilder();
        for (int k = 0; k < 14; k++) {                                          // a different window of the category each day
            String t = members.getJSONObject((int) Math.floorMod(day * 14 + k * 7, (long) n)).getString("title");
            if (titles.length() > 0) titles.append('|');
            titles.append(t);
        }
        String info = get("https://commons.wikimedia.org/w/api.php?action=query&format=json&prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=1280&iiextmetadatafilter=LicenseShortName|Artist&titles="
                + URLEncoder.encode(titles.toString(), "UTF-8"), 300000);
        JSONObject pages = new JSONObject(info).getJSONObject("query").getJSONObject("pages");
        java.util.Iterator<String> it = pages.keys();
        while (it.hasNext() && out.size() < 4) {
            JSONObject pg = pages.getJSONObject(it.next());
            JSONArray ii = pg.optJSONArray("imageinfo");
            if (ii == null || ii.length() == 0) continue;
            JSONObject i0 = ii.getJSONObject(0);
            JSONObject meta = i0.optJSONObject("extmetadata");
            String lic = meta == null || meta.optJSONObject("LicenseShortName") == null ? "" : meta.getJSONObject("LicenseShortName").optString("value");
            if (!WallpaperRules.acceptable(i0.optInt("width"), i0.optInt("height")) || !WallpaperRules.licenceAllowed(lic)) continue;
            String url = i0.optString("thumburl");
            if (!url.startsWith("https://upload.wikimedia.org/") && !url.startsWith("https://thumb.wikimedia.org/")) continue;
            Entry e = download(url);
            if (e == null) continue;
            e.title = WallpaperRules.plain(pg.optString("title").replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z]+$", ""), 80);
            e.artist = WallpaperRules.plain(meta != null && meta.optJSONObject("Artist") != null ? meta.getJSONObject("Artist").optString("value") : "", 60);
            e.license = lic;
            e.source = "Wikimedia Commons";
            e.page = "https://commons.wikimedia.org/wiki/" + pg.optString("title").replace(' ', '_');
            if (!has(have, e.file)) out.add(e); else new File(dir, e.file).delete();
        }
        return out;
    }

    private List<Entry> fetchBing() throws Exception {
        List<Entry> out = new ArrayList<>();
        String loc = Locale.getDefault().toLanguageTag();
        String json = get("https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=4&mkt=" + URLEncoder.encode(loc.isEmpty() ? "en-US" : loc, "UTF-8"), 60000);
        JSONArray imgs = new JSONObject(json).getJSONArray("images");
        for (int i = 0; i < imgs.length() && out.size() < 4; i++) {
            JSONObject o = imgs.getJSONObject(i);
            String base = o.optString("urlbase");
            if (!base.startsWith("/th?id=OHR.")) continue;
            Entry e = download("https://www.bing.com" + base + "_1920x1080.jpg");
            if (e == null) continue;
            e.title = WallpaperRules.plain(o.optString("title"), 80);
            e.artist = WallpaperRules.plain(o.optString("copyright"), 90);
            e.license = "© photographer, via Bing (personal use)";
            e.source = "Bing daily photo";
            e.page = "https://www.bing.com";
            out.add(e);
        }
        return out;
    }

    private static boolean has(List<Entry> l, String f) { for (Entry e : l) if (e.file.equals(f)) return true; return false; }

    /** Downloads, validates (magic bytes, size, decodable dimensions) and stores one image under a name derived from its URL. */
    private Entry download(String url) {
        try {
            byte[] data = getBytes(url, 4_000_000);
            if (!WallpaperRules.looksLikeJpeg(java.util.Arrays.copyOf(data, 4), data.length)) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            if (o.outWidth < 1000 || o.outHeight < 560) return null;
            Entry e = new Entry();
            e.file = WallpaperRules.fileFor(url);
            try (FileOutputStream f = new FileOutputStream(new File(dir, e.file))) { f.write(data); }
            return e;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String get(String url, int max) throws Exception { return new String(getBytes(url, max), "UTF-8"); }

    private static byte[] getBytes(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", UA);
        c.setInstanceFollowRedirects(true);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("http " + c.getResponseCode());
            return readAll(in, max);
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int max) throws java.io.IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) {
            b.write(buf, 0, n);
            if (b.size() > max) throw new java.io.IOException("too large");
        }
        in.close();
        return b.toByteArray();
    }
}
