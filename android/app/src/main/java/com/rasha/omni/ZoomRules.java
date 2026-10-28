package com.rasha.omni;

/**
 * The pure rules of the zoom launch (see docs/plans/ZOOM-LAUNCH.md): when it is used, how the rectangle and corners move, and when the shell
 * that covers the app's loading dissolves. The numbers are the motion tokens in docs/design/MOTION.md. Tested on the JVM.
 */
final class ZoomRules {
    private ZoomRules() { }

    static final int GROW_MS = 320, DISSOLVE_MS = 160, MIN_TOTAL_MS = 420, MAX_TOTAL_MS = 2500, PROGRESS_AFTER_MS = 800;
    static final float TILE_RADIUS_DP = 12f, END_ICON_DP = 96f;

    /** The zoom is used only when the person switched it on, the "draw over other apps" permission is granted and the system's animations are on. */
    static boolean usable(boolean setting, boolean overlayAllowed, boolean reducedMotion) { return setting && overlayAllowed && !reducedMotion; }

    static float lerp(float a, float b, float t) { return a + (b - a) * Math.max(0f, Math.min(1f, t)); }

    /** Corner radius while growing: the tile's radius at the start, square at full screen. */
    static float radius(float t, float tileRadius) { return tileRadius * (1f - Math.max(0f, Math.min(1f, t))); }

    /** How visible the tile's artwork is as the shell grows: all of it while small, gone by the end so the stretched picture is never seen full screen. */
    static float artAlpha(float t) { return 1f - smooth(0.3f, 0.9f, t); }

    /** A banner's small icon appears at the centre as the artwork leaves. */
    static float iconAlpha(float t) { return smooth(0.4f, 1f, t); }

    static float smooth(float e0, float e1, float x) { float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0))); return t * t * (3f - 2f * t); }

    /**
     * Whether the shell should start dissolving now. Never before it has finished growing and shown for a minimum time (no flicker for an app that is
     * already running); once the app is on screen it goes; and a hard limit means it can never get stuck if the app never reports.
     */
    static boolean shouldDissolve(long elapsedMs, boolean appReady) {
        if (elapsedMs >= MAX_TOTAL_MS) return true;
        return appReady && elapsedMs >= MIN_TOTAL_MS;
    }

    /** A thin progress line only appears when the app is slow (the zoom itself already says "opening"). */
    static boolean showProgress(long elapsedMs, boolean appReady) { return !appReady && elapsedMs >= PROGRESS_AFTER_MS; }
}
