package com.rasha.omni;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.io.FileInputStream;

/**
 * Cinema, Weather and Server run in their own processes inside this app so the launcher stays small. When the last screen of one of them is closed its
 * process exits a few seconds later, handing the memory back (this TV has under 1 GB). The launcher's own process, the screensaver's and the
 * accessibility/notification services are never touched.
 */
public class OmniApp extends Application {
    private static final long GRACE_MS = 8000;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int alive;
    private final Runnable exit = () -> { if (alive == 0) android.os.Process.killProcess(android.os.Process.myPid()); };

    @Override public void onCreate() {
        super.onCreate();
        CrashGuard.install(this);                                      // every process records its own crash; see CrashGuard
        String p = processName();
        if (!(p.endsWith(":cinema") || p.endsWith(":weather") || p.endsWith(":server"))) return;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity a, Bundle b) { alive++; ui.removeCallbacks(exit); }
            @Override public void onActivityDestroyed(Activity a) { alive = Math.max(0, alive - 1); if (alive == 0 && a.isFinishing()) ui.postDelayed(exit, GRACE_MS); }
            @Override public void onActivityStarted(Activity a) { }
            @Override public void onActivityResumed(Activity a) { }
            @Override public void onActivityPaused(Activity a) { }
            @Override public void onActivityStopped(Activity a) { }
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) { }
        });
    }

    private static String processName() {
        try (FileInputStream in = new FileInputStream("/proc/self/cmdline")) {
            byte[] b = new byte[128];
            int n = in.read(b);
            int end = 0;
            while (end < n && b[end] != 0) end++;
            return new String(b, 0, end, "UTF-8");
        } catch (Exception e) { return ""; }
    }
}
