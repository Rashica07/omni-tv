package com.rasha.omni;

import android.content.Context;
import android.content.SharedPreferences;

/** The person's two broad choices, kept in one place: the language, and Simple mode (no home server, only what they use). */
final class UserMode {
    private UserMode() { }

    private static SharedPreferences p(Context c) { return c.getSharedPreferences("omni", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS); }

    /** Simple mode hides everything that needs a server: the Server screen and rows, pairing, polling. */
    static boolean simple(Context c) {
        SharedPreferences p = p(c);
        if (p.contains("simple")) return p.getBoolean("simple", false);
        return !p.getBoolean("setup_done", false);          // a first run starts Simple; an install that already finished its setup keeps everything it had
    }

    static void setSimple(Context c, boolean on) { p(c).edit().putBoolean("simple", on).apply(); }

    /** The saved language, or the TV's own language when none was chosen. Sets {@link Tr#lang}. */
    static void applyLanguage(Context c) {
        int saved = p(c).getInt("lang", -1);
        Tr.lang = saved >= 0 && saved < Tr.CODES.length ? saved : Tr.fromSystem(java.util.Locale.getDefault().toLanguageTag());
    }

    static void setLanguage(Context c, int index) { p(c).edit().putInt("lang", Math.max(0, Math.min(Tr.CODES.length - 1, index))).apply(); Tr.lang = Math.max(0, Math.min(Tr.CODES.length - 1, index)); }
}
