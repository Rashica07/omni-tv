package com.rasha.omni;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Records OMNI's own crashes so a bug report can carry the real stack, and lets the launcher start in a plain "safe mode" (no setup card, no background
 * watchers, no idle timer) after repeated crashes, so a crash can never turn into a loop that locks the TV's Home screen. Nothing leaves the TV: the report
 * is a small text file shown in Settings, and the device model and Android version are the only identifying details in it.
 */
final class CrashGuard {
    private CrashGuard() { }

    static void install(Context c) {
        final Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try { record(app, t, e); } catch (Throwable ignored) { }
            if (prev != null) prev.uncaughtException(t, e);
        });
    }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("crashguard", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS); }

    private static void record(Context c, Thread t, Throwable e) throws Exception {
        long now = System.currentTimeMillis();
        String version = "?";
        try { version = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName; } catch (Exception ignored) { }
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] lines = sw.toString().split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append("OMNI ").append(version).append("\n")
          .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
          .append(new java.util.Date(now)).append(", thread ").append(t.getName()).append("\n");
        for (int i = 0; i < Math.min(lines.length, 28); i++) sb.append(lines[i]).append('\n');
        try (FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), "last-crash.txt"))) { out.write(sb.toString().getBytes("UTF-8")); }
        prefs(c).edit().putString("times", CrashRules.add(prefs(c).getString("times", ""), now)).commit();      // commit, not apply: the process is about to die
    }

    /** True when the last launches crashed repeatedly; the launcher then skips everything optional. */
    static boolean safeMode(Context c) { return CrashRules.safeMode(prefs(c).getString("times", ""), System.currentTimeMillis()); }

    /** The most recent crash report, or null. */
    static String report(Context c) {
        try {
            File f = new File(c.getFilesDir(), "last-crash.txt");
            if (!f.exists() || f.length() == 0) return null;
            byte[] b = new byte[(int) Math.min(f.length(), 6000)];
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) { int n = in.read(b); return new String(b, 0, Math.max(n, 0), "UTF-8"); }
        } catch (Exception e) { return null; }
    }

    static void clear(Context c) { new File(c.getFilesDir(), "last-crash.txt").delete(); prefs(c).edit().clear().apply(); }
}
