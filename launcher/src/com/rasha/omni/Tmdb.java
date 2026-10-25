package com.rasha.omni;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal TMDB client: list endpoints only, cached on disk for six hours. */
final class Tmdb {
    static final class Title {
        long id;
        String title;
        String overview;
        String year;
        String poster;
        String backdrop;
        double rating;
        boolean tv;
    }

    interface Callback { void done(List<Title> list); }

    /** Subscription ("flatrate") providers for one title in Italy, from TMDB's watch-provider data (JustWatch). */
    static final class Providers {
        final boolean known;
        final List<String> flatrate;

        Providers(boolean known, List<String> flatrate) {
            this.known = known;
            this.flatrate = flatrate;
        }
    }

    interface ProvidersCallback { void done(Providers p); }

    private static final String BASE = "https://api.themoviedb.org/3";
    private static final long TTL = 6L * 60 * 60 * 1000;
    private final File dir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    Tmdb(Context c) {
        dir = new File(c.getCacheDir(), "tmdb");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    static boolean enabled() { return !Secrets.TMDB.isEmpty(); }

    /** Fetches a list endpoint off the main thread; the callback runs on the main thread (empty on failure). */
    void load(final String path, final Callback cb) {
        pool.execute(() -> {
            List<Title> out = new ArrayList<>();
            if (!OmniLog.forceOffline()) {
                try { out = parse(get(path)); } catch (Exception ignored) { }
            }
            final List<Title> res = out;
            ui.post(() -> cb.done(res));
        });
    }

    /** Looks up where a title streams with a subscription in Italy. known=false means no data or a failed request. */
    void loadProviders(final boolean tv, final long id, final ProvidersCallback cb) {
        pool.execute(() -> {
            Providers out = new Providers(false, new ArrayList<>());
            try {
                JSONObject r = new JSONObject(get("/" + (tv ? "tv" : "movie") + "/" + id + "/watch/providers"))
                        .optJSONObject("results");
                JSONObject it = r == null ? null : r.optJSONObject("IT");
                if (it != null) {
                    List<String> names = new ArrayList<>();
                    JSONArray fr = it.optJSONArray("flatrate");
                    if (fr != null) for (int i = 0; i < fr.length(); i++) names.add(fr.getJSONObject(i).optString("provider_name"));
                    out = new Providers(true, names);
                }
            } catch (Exception ignored) { }
            final Providers res = out;
            ui.post(() -> cb.done(res));
        });
    }

    private String get(String path) throws IOException {
        File f = new File(dir, Integer.toHexString(path.hashCode()) + ".json");
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < TTL) return read(new FileInputStream(f));
        boolean bearer = Secrets.TMDB.length() > 40;
        String url = BASE + path + (path.contains("?") ? "&" : "?") + "language=en-US"
                + (bearer ? "" : "&api_key=" + Secrets.TMDB);
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("Accept", "application/json");
        if (bearer) c.setRequestProperty("Authorization", "Bearer " + Secrets.TMDB);
        try {
            if (c.getResponseCode() != 200) {
                if (f.exists()) return read(new FileInputStream(f));
                throw new IOException("TMDB http " + c.getResponseCode());
            }
            String body = read(c.getInputStream());
            try (FileOutputStream o = new FileOutputStream(f)) { o.write(body.getBytes("UTF-8")); }
            return body;
        } finally {
            c.disconnect();
        }
    }

    static String read(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) b.write(buf, 0, n);
            return b.toString("UTF-8");
        }
    }

    private static List<Title> parse(String body) throws Exception {
        List<Title> out = new ArrayList<>();
        JSONArray a = new JSONObject(body).optJSONArray("results");
        if (a == null) return out;
        for (int i = 0; i < a.length() && out.size() < 20; i++) {
            JSONObject o = a.getJSONObject(i);
            if (o.isNull("poster_path")) continue;
            Title t = new Title();
            t.id = o.optLong("id");
            t.tv = "tv".equals(o.optString("media_type")) || (!o.has("title") && o.has("name"));
            t.title = o.optString("title", o.optString("name", ""));
            t.overview = o.optString("overview", "");
            String d = o.optString("release_date", o.optString("first_air_date", ""));
            t.year = d.length() >= 4 ? d.substring(0, 4) : "";
            t.rating = o.optDouble("vote_average", 0);
            t.poster = o.optString("poster_path");
            t.backdrop = o.isNull("backdrop_path") ? null : o.optString("backdrop_path");
            out.add(t);
        }
        return out;
    }
}
