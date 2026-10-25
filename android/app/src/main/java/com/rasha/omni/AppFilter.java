package com.rasha.omni;

/** Which packages never appear as launchable apps. Own package is an exact match: sibling apps stay visible. */
final class AppFilter {
    static final String SELF = "com.rasha.omni";
    private static final String[] PREFIXES = {
            "com.mediatek.", "com.vestel.", "com.mstar.", "mediatek.", "fusion.android",
            "com.android.tv.settings", "com.android.settings",
            "me.efesser.flauncher", "com.google.android.leanbacklauncher"};

    private AppFilter() { }

    static boolean isHidden(String pkg) {
        if (pkg.equals(SELF)) return true;
        for (String p : PREFIXES) if (pkg.startsWith(p)) return true;
        return false;
    }
}
