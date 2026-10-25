package com.rasha.omni;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Finds OMNI servers on the TV's own /24 by asking each address for /health on port 8765 (which reveals nothing private).
 * Nothing leaves the local network. Bounded: 254 short requests on 32 threads, about 3 s in total. Same rule as Cinema's Discovery.
 */
final class ServerScan {
    interface Callback { void done(List<String> found); }

    private ServerScan() { }

    static void find(final Context ctx, final Callback cb) {
        new Thread(() -> {
            List<String> found = Collections.synchronizedList(new ArrayList<>());
            try {
                Inet4Address mine = ownAddress(ctx);
                if (mine != null) {
                    byte[] b = mine.getAddress();
                    String prefix = (b[0] & 255) + "." + (b[1] & 255) + "." + (b[2] & 255) + ".";
                    ExecutorService pool = Executors.newFixedThreadPool(32);
                    for (int n = 1; n <= 254; n++) {
                        final String ip = prefix + n;
                        if (ip.equals(mine.getHostAddress())) continue;
                        pool.execute(() -> { if (isOmni(ip, 8765)) found.add(ip + ":8765"); });
                    }
                    pool.shutdown();
                    pool.awaitTermination(6, TimeUnit.SECONDS);
                    pool.shutdownNow();
                }
            } catch (Exception ignored) { }
            List<String> out = new ArrayList<>(found);
            Collections.sort(out);
            OmniLog.state("serverscan found=" + out.size());
            cb.done(out);
        }, "server-scan").start();
    }

    private static Inet4Address ownAddress(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return null;
        LinkProperties lp = cm.getLinkProperties(cm.getActiveNetwork());
        if (lp == null) return null;
        for (LinkAddress la : lp.getLinkAddresses()) { InetAddress a = la.getAddress(); if (a instanceof Inet4Address && !a.isLoopbackAddress()) return (Inet4Address) a; }
        return null;
    }

    private static boolean isOmni(String ip, int port) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + ip + ":" + port + "/health").openConnection();
            c.setConnectTimeout(400);
            c.setReadTimeout(800);
            if (c.getResponseCode() != 200) return false;
            byte[] buf = new byte[2000];
            int n = 0, r;
            try (InputStream in = c.getInputStream()) { while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r; }
            return new String(buf, 0, n, "UTF-8").contains("\"omniptvd\"");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }
}
