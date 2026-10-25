package com.rasha.omni;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Client for the home server (omniptvd). The address is configured by the user and nothing is assumed about it. Reachability is measured
 * with a real request to /health and the answer must look like omniptvd; "the TV has Wi-Fi" says nothing about the server.
 * The server is optional: playback of the bundled/cached catalogue never depends on it.
 */
final class PtvServer {
    static final int UNSET = 0, CHECKING = 1, REACHABLE = 2, UNREACHABLE = 3;

    static final class Status {
        int state = UNSET;
        String detail = "";          // honest one-liner for the UI
        int channels = -1;
        int playing = -1;
        boolean epg;
        long when;                   // wall clock of the last check
    }

    interface Callback { void done(Status s); }

    private PtvServer() { }

    static String base(Context c) {
        return PtvRules.serverBase(c.getSharedPreferences("omniptv", Context.MODE_PRIVATE).getString("server", ""));
    }

    static void setBase(Context c, String input) {
        String b = PtvRules.serverBase(input);
        c.getSharedPreferences("omniptv", Context.MODE_PRIVATE).edit().putString("server", b == null ? "" : b).apply();
    }

    /** Runs on a background thread, calls back on it. Short timeouts: this runs when asked, never in a loop. */
    static void check(final Context ctx, final Callback cb) {
        final Context app = ctx.getApplicationContext();
        new Thread(() -> {
            Status s = new Status();
            s.when = System.currentTimeMillis();
            String base = base(app);
            if (base == null) {
                s.detail = "No server set up. OMNIPTV is using its built-in channel list.";
                cb.done(s);
                return;
            }
            try {
                JSONObject h = new JSONObject(get(base + "/health", 4096));
                if (!"omniptvd".equals(h.optString("service"))) throw new IllegalStateException("not an OMNIPTV server");
                s.state = PtvServer.REACHABLE;
                s.channels = h.optInt("channels", -1);
                s.playing = h.optInt("playing", -1);
                s.epg = h.optBoolean("epg");
                s.detail = "Server reachable" + (s.playing >= 0 ? ", " + s.playing + " channels checked and playing" : "");
                try {
                    PtvCatalog.saveFromServer(app, get(base + "/catalog.json", 1 << 20));
                } catch (Exception ignored) { /* catalogue refresh is best effort; the last good copy stays */ }
            } catch (java.net.SocketTimeoutException | java.net.ConnectException | java.net.UnknownHostException e) {
                s.state = PtvServer.UNREACHABLE;
                s.detail = "Server not reachable. Using the built-in channel list.";
            } catch (Exception e) {
                s.state = PtvServer.UNREACHABLE;
                s.detail = "That address answered, but not like an OMNIPTV server.";
            }
            cb.done(s);
        }, "ptv-server").start();
    }

    private static String get(String url, int max) throws Exception {
        if (!PtvRules.safeUrl(url)) throw new IllegalArgumentException("unsafe url");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(4000);
        c.setInstanceFollowRedirects(false);
        try (InputStream in = c.getInputStream()) {
            if (c.getResponseCode() != 200) throw new IllegalStateException("http " + c.getResponseCode());
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0 && b.size() < max) b.write(buf, 0, n);
            return b.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
