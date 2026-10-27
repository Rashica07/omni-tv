package com.rasha.omni;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OMNI's updater. Android does not let an ordinary app install itself silently, so this does everything up to that point on its own and asks for one press:
 * it checks GitHub's public releases (once a day, only while OMNI is on screen, nothing identifying is sent), downloads a newer APK, verifies it (size cap,
 * SHA-256 from the release notes, same package, newer version, the same signing certificate as the installed OMNI) and hands it to the system installer,
 * which shows its own confirmation. A build that carries its own channel list (a personal build) is never replaced by the public one.
 */
final class Updater {
    /** The feed. Replaced only by a developer flag (a local test server). */
    private static final String FEED = "https://api.github.com/repos/Rashica07/omni-tv/releases?per_page=12";
    private static final long MAX_APK = 60L << 20;
    private static Updater instance;

    private final Context ctx;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private volatile boolean busy;

    interface Callback { void done(String message); }

    private Updater(Context c) { ctx = c.getApplicationContext(); }

    static synchronized Updater get(Context c) { if (instance == null) instance = new Updater(c); return instance; }

    private SharedPreferences p() { return ctx.getSharedPreferences("updates", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS); }

    // ------------------------------------------------------------ settings and state

    boolean auto() { return p().getBoolean("auto", true); }
    void setAuto(boolean v) { p().edit().putBoolean("auto", v).apply(); }
    /** Every release so far is a pre-release; stable-only becomes the better choice after 1.0.0. */
    boolean pre() { return p().getBoolean("pre", true); }
    void setPre(boolean v) { p().edit().putBoolean("pre", v).apply(); }
    boolean busy() { return busy; }

    String installed() {
        try { return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionName; } catch (Exception e) { return "?"; }
    }

    private long installedCode() { return code(info(ctx.getPackageName(), false)); }

    private static long code(PackageInfo i) { return i == null ? 0 : Build.VERSION.SDK_INT >= 28 ? i.getLongVersionCode() : i.versionCode; }

    private PackageInfo info(String pkg, boolean signatures) {
        try {
            int flags = signatures ? (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES) : 0;
            return ctx.getPackageManager().getPackageInfo(pkg, flags);
        } catch (Exception e) { return null; }
    }

    /** A build that bundles its own channel list is a personal build: replacing it with the public release would lose that list and its logos. */
    boolean personalBuild() {
        try (InputStream in = ctx.getAssets().open("ptv-catalog.json")) { return in.available() > 200; } catch (Exception e) { return false; }
    }

    /** The newest release found by the last check, or null. */
    UpdateRules.Release available() {
        String tag = p().getString("tag", null);
        if (tag == null || UpdateRules.compare(tag, installed()) <= 0) return null;
        return new UpdateRules.Release(tag, true, p().getString("url", ""), p().getLong("size", 0), p().getString("body", ""));
    }

    File downloaded() {
        String tag = p().getString("file_tag", null);
        File f = new File(new File(ctx.getCacheDir(), "update"), "omni.apk");
        return tag != null && tag.equals(p().getString("tag", "")) && f.exists() ? f : null;
    }

    long lastCheck() { return p().getLong("ok", 0); }
    String lastError() { return p().getString("err", null); }

    // ------------------------------------------------------------ check

    /** Checks if it is time (once a day, six hours after a failure). Never on a device whose launcher is not on screen: the caller decides. */
    void checkIfDue(Callback cb) {
        if (!auto() || busy) return;
        if (!UpdateRules.due(System.currentTimeMillis(), p().getLong("ok", 0), p().getLong("try", 0))) { maybeDownload(cb); return; }
        check(cb);
    }

    void check(Callback cb) {
        if (busy) return;
        busy = true;
        io.execute(() -> {
            String msg;
            try {
                long now = System.currentTimeMillis();
                p().edit().putLong("try", now).apply();
                UpdateRules.Release r = UpdateRules.pick(fetch(), installed(), pre());
                SharedPreferences.Editor e = p().edit().putLong("ok", now).remove("err");
                if (r == null) { e.remove("tag").apply(); msg = "OMNI " + installed() + " is up to date"; }
                else {
                    e.putString("tag", r.tag).putString("url", r.apkUrl).putLong("size", r.size).putString("body", r.body).apply();
                    msg = "OMNI " + r.tag.replaceFirst("^v", "") + " is available";
                }
            } catch (Exception ex) {
                p().edit().putString("err", "Could not reach GitHub").apply();
                msg = "Could not check for updates";
            }
            busy = false;
            final String m = msg;
            ui.post(() -> { if (cb != null) cb.done(m); });
            if (available() != null) ui.post(() -> maybeDownload(cb));
        });
    }

    private List<UpdateRules.Release> fetch() throws Exception {
        String feed = FEED;
        if (OmniLog.flag("OmniDebugOpen") && p().getString("debug_feed", null) != null) feed = p().getString("debug_feed", FEED);   // developer test server only
        JSONArray a = new JSONArray(new String(get(feed, 400_000), "UTF-8"));
        List<UpdateRules.Release> out = new ArrayList<>();
        for (int i = 0; i < Math.min(a.length(), 12); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null || o.optBoolean("draft")) continue;
            JSONArray assets = o.optJSONArray("assets");
            String url = ""; long size = 0;
            if (assets != null) for (int k = 0; k < assets.length(); k++) {
                JSONObject s = assets.optJSONObject(k);
                if (s != null && s.optString("name").endsWith(".apk") && !s.optString("name").contains("personal")) { url = s.optString("browser_download_url"); size = s.optLong("size"); break; }
            }
            out.add(new UpdateRules.Release(o.optString("tag_name"), o.optBoolean("prerelease"), allowed(url) ? url : "", size, o.optString("body")));
        }
        return out;
    }

    private boolean allowed(String url) { return UpdateRules.hostAllowed(url) || (OmniLog.flag("OmniDebugOpen") && url.startsWith("http://10.0.2.2")); }

    private byte[] get(String url, int max) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", "OMNI-TV/" + installed());
        c.setRequestProperty("Accept", "application/vnd.github+json");
        try {
            if (c.getResponseCode() != 200) throw new java.io.IOException("http " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[16384];
                for (int n; (n = in.read(b)) >= 0; ) { out.write(b, 0, n); if (out.size() > max) throw new java.io.IOException("too large"); }
                return out.toByteArray();
            }
        } finally { c.disconnect(); }
    }

    // ------------------------------------------------------------ download and verify

    private void maybeDownload(Callback cb) {
        if (available() != null && downloaded() == null && !busy && !personalBuild()) download(cb);
    }

    /** Downloads the available APK and verifies it. The result message says what happened; a file that fails any check is deleted. */
    void download(Callback cb) {
        final UpdateRules.Release r = available();
        if (r == null || busy) return;
        busy = true;
        io.execute(() -> {
            String msg;
            File dir = new File(ctx.getCacheDir(), "update"); dir.mkdirs();
            File f = new File(dir, "omni.apk");
            try {
                String want = UpdateRules.sha256(r.body);
                if (want == null) throw new SecurityException("The release has no checksum");
                if (!allowed(r.apkUrl)) throw new SecurityException("The download address is not allowed");
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                HttpURLConnection c = (HttpURLConnection) new URL(r.apkUrl).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(20000); c.setRequestProperty("User-Agent", "OMNI-TV/" + installed());
                try {
                    if (c.getResponseCode() != 200) throw new java.io.IOException("http " + c.getResponseCode());
                    long total = 0;
                    try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(f)) {
                        byte[] b = new byte[32768];
                        for (int n; (n = in.read(b)) >= 0; ) { total += n; if (total > MAX_APK) throw new SecurityException("The download is too large"); md.update(b, 0, n); out.write(b, 0, n); }
                    }
                } finally { c.disconnect(); }
                if (!hex(md.digest()).equals(want)) throw new SecurityException("The checksum does not match");
                String bad = verifyFile(f);
                if (bad != null) throw new SecurityException(bad);
                p().edit().putString("file_tag", r.tag).remove("err").apply();
                msg = "OMNI " + r.tag.replaceFirst("^v", "") + " is ready to install";
            } catch (SecurityException e) {
                f.delete(); p().edit().putString("err", e.getMessage()).remove("file_tag").apply();
                msg = "Update refused: " + e.getMessage();
            } catch (Exception e) {
                f.delete(); p().edit().putString("err", "The download failed").remove("file_tag").apply();
                msg = "The update download failed";
            }
            busy = false;
            final String m = msg;
            ui.post(() -> { if (cb != null) cb.done(m); });
        });
    }

    /** Same package, a higher version code, and the signing certificate of the OMNI that is installed. Returns the reason it is refused, or null. */
    private String verifyFile(File f) {
        PackageManager pm = ctx.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo n = pm.getPackageArchiveInfo(f.getPath(), flags);
        if (n == null) return "The file is not an app";
        if (!UpdateRules.installable(ctx.getPackageName(), installedCode(), n.packageName, code(n))) return "The file is not a newer OMNI";
        Signature[] mine = sigs(info(ctx.getPackageName(), true)), theirs = sigs(n);
        if (mine == null || theirs == null || mine.length == 0 || theirs.length == 0) return "The signature could not be read";
        for (Signature a : theirs) for (Signature b : mine) if (a.equals(b)) return null;
        return "It is signed with a different key";
    }

    private static Signature[] sigs(PackageInfo i) {
        if (i == null) return null;
        if (Build.VERSION.SDK_INT >= 28) return i.signingInfo == null ? null : i.signingInfo.getApkContentsSigners();
        return i.signatures;
    }

    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }

    // ------------------------------------------------------------ install

    /** Hands the verified file to the system installer. The system asks the person to confirm; OMNI restarts when it finishes. */
    void install(Callback cb) {
        final File f = downloaded();
        if (f == null || busy || personalBuild()) { if (cb != null) cb.done("Nothing verified to install"); return; }
        busy = true;
        io.execute(() -> {
            String msg = "Waiting for you to confirm the install";
            try {
                if (verifyFile(f) != null) throw new SecurityException("Verification failed");
                PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                sp.setAppPackageName(ctx.getPackageName());
                int id = pi.createSession(sp);
                try (PackageInstaller.Session s = pi.openSession(id)) {
                    try (InputStream in = new FileInputStream(f); OutputStream out = s.openWrite("omni", 0, f.length())) {
                        byte[] b = new byte[65536];
                        for (int n; (n = in.read(b)) >= 0; ) out.write(b, 0, n);
                        s.fsync(out);
                    }
                    Intent result = new Intent(ctx, UpdateReceiver.class).setAction("com.rasha.omni.UPDATE_RESULT").setPackage(ctx.getPackageName());
                    PendingIntent pend = PendingIntent.getBroadcast(ctx, id, result, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0));
                    s.commit(pend.getIntentSender());
                }
            } catch (Exception e) {
                p().edit().putString("err", "The installer refused the update").apply();
                msg = "The installer refused the update";
            }
            busy = false;
            final String m = msg;
            ui.post(() -> { if (cb != null) cb.done(m); });
        });
    }

    /** Called by the receiver with the installer's verdict. */
    void installResult(int status, String message) {
        if (status == PackageInstaller.STATUS_SUCCESS) { p().edit().remove("err").remove("file_tag").apply(); return; }
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) p().edit().putString("err", message == null || message.isEmpty() ? "The install did not finish" : message).apply();
    }

    /** Developer only: points the feed at a local test server. Ignored unless the developer flag is set. */
    void debugFeed(String url) { p().edit().putString("debug_feed", url).remove("ok").remove("try").apply(); }
}
