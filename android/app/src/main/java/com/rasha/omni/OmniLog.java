package com.rasha.omni;

import android.util.Log;

/**
 * Opt-in diagnostics for the regression and latency scripts. Silent unless enabled on the device:
 * `adb shell setprop log.tag.OmniState DEBUG` (state events) / `log.tag.OmniLat DEBUG` (input to first draw).
 */
final class OmniLog {
    private OmniLog() { }

    static void state(String s) {
        if (Log.isLoggable("OmniState", Log.DEBUG)) Log.d("OmniState", s);
    }

    static boolean latency() { return Log.isLoggable("OmniLat", Log.DEBUG); }

    /** Test switches (never set in normal use): `setprop log.tag.OmniOffline DEBUG` fakes a dead network, `OmniEmpty` fakes no apps. */
    static boolean forceOffline() { return Log.isLoggable("OmniOffline", Log.DEBUG); }

    static boolean forceEmpty() { return Log.isLoggable("OmniEmpty", Log.DEBUG); }

    /** Developer switch for profiling: `setprop log.tag.<name> DEBUG` then restart the app. Never set in normal use. */
    static boolean flag(String name) { return Log.isLoggable(name, Log.DEBUG); }

    static void lat(String s) { Log.d("OmniLat", s); }
}
