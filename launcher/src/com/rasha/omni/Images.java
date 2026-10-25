package com.rasha.omni;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** TMDB artwork loader: disk cache plus a small RGB_565 memory cache, sized for a 900 MB TV. */
final class Images {
    interface Callback { void done(Bitmap b); }

    private final File dir;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final LruCache<String, Bitmap> mem = new LruCache<String, Bitmap>(10 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private final LruCache<String, Bitmap> backdrops = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    /** Loads a w780 backdrop and bakes the OMNI scrims into it, off the main thread. */
    void loadBackdrop(String path, final Callback cb) {
        final String url = "https://image.tmdb.org/t/p/w780" + path;
        Bitmap hit = backdrops.get(url);
        if (hit != null) {
            cb.done(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap raw = fetch(url);
            Bitmap out = null;
            if (raw != null) {
                out = Ambient.compose(raw);
                raw.recycle();
                backdrops.put(url, out);
            }
            final Bitmap r = out;
            ui.post(() -> cb.done(r));
        });
    }

    Images(Context c) {
        dir = new File(c.getCacheDir(), "img");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** size is a TMDB image size such as "w185" or "w780"; the callback gets null on failure. */
    void load(String size, String path, final Callback cb) {
        final String url = "https://image.tmdb.org/t/p/" + size + path;
        Bitmap hit = mem.get(url);
        if (hit != null) {
            cb.done(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap b = fetch(url);
            if (b != null) mem.put(url, b);
            final Bitmap r = b;
            ui.post(() -> cb.done(r));
        });
    }

    /** Artwork from a publisher URI. Only http(s) and content: are accepted (never file:). The callback gets null on failure. */
    void loadUri(final Context ctx, final String uri, final Callback cb) {
        Bitmap hit = mem.get(uri);
        if (hit != null) {
            cb.done(hit);
            return;
        }
        pool.execute(() -> {
            Bitmap b = null;
            try {
                if (uri.startsWith("http://") || uri.startsWith("https://")) {
                    b = fetch(uri);
                } else if (uri.startsWith("content://")) {
                    BitmapFactory.Options o = new BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    try (InputStream in = ctx.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
                        if (in != null) BitmapFactory.decodeStream(in, null, o);
                    }
                    o.inJustDecodeBounds = false;
                    o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, o.outWidth / 480)));
                    o.inPreferredConfig = Bitmap.Config.RGB_565;
                    try (InputStream in = ctx.getContentResolver().openInputStream(android.net.Uri.parse(uri))) {
                        if (in != null) b = BitmapFactory.decodeStream(in, null, o);
                    }
                }
            } catch (Exception ignored) { }
            if (b != null) mem.put(uri, b);
            final Bitmap r = b;
            ui.post(() -> cb.done(r));
        });
    }

    private Bitmap fetch(String url) {
        try {
            File f = new File(dir, Integer.toHexString(url.hashCode()));
            if (!f.exists()) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(10000);
                if (c.getResponseCode() != 200) return null;
                File tmp = new File(dir, f.getName() + ".tmp");
                try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                } finally {
                    c.disconnect();
                }
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(f);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            o.inJustDecodeBounds = false;
            o.inSampleSize = Math.max(1, Integer.highestOneBit(Math.max(1, o.outWidth / 960)));   // never decode more than ~960 px wide
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getPath(), o);
        } catch (Exception e) {
            return null;
        }
    }
}
