package com.rasha.omni;

/**
 * The order of the first-run setup and what moving through it means. Pure, so it is tested on the JVM.
 * Every step can be skipped; Back never leaves the first step (setup is left with "Skip setup" or by finishing); the dots count only the real steps.
 * Simple mode (no home server) leaves out the server step; the antenna step appears only on a TV that has a tuner.
 */
final class SetupFlow {
    static final int WELCOME = 0, LANGUAGE = 1, MODE = 2, HOME = 3, REMOTE = 4, SERVER = 5, WEATHER = 6, ANTENNA = 7, QUICK = 8, PERMISSIONS = 9, DONE = 10;
    static final int COUNT = 11;
    /** English titles; shown through {@link Tr}. */
    static final String[] TITLES = {"WELCOME TO OMNI UI", "Language", "Simple or full?", "Make OMNI your Home", "Your remote", "Your OMNI server", "Weather", "Antenna channels", "Quick access", "Permissions", "You're all set"};

    private SetupFlow() { }

    /** The steps shown, in order, for this choice of mode and TV. */
    static int[] order(boolean simple, boolean tuner) {
        int[] all = {WELCOME, LANGUAGE, MODE, HOME, REMOTE, SERVER, WEATHER, ANTENNA, QUICK, PERMISSIONS, DONE};
        int n = 0;
        int[] out = new int[all.length];
        for (int s : all) {
            if (s == SERVER && simple) continue;
            if (s == ANTENNA && !tuner) continue;
            out[n++] = s;
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private static int indexOf(int[] o, int step) { for (int i = 0; i < o.length; i++) if (o[i] == step) return i; return -1; }

    static int next(int step, boolean simple, boolean tuner) {
        int[] o = order(simple, tuner);
        int i = indexOf(o, step);
        if (i < 0) { for (int s : o) if (s > step) return s; return DONE; }      // a step this mode does not show: continue from where it would have been
        return o[Math.min(o.length - 1, i + 1)];
    }

    static int back(int step, boolean simple, boolean tuner) {
        int[] o = order(simple, tuner);
        int i = indexOf(o, step);
        if (i < 0) { int prev = WELCOME; for (int s : o) if (s < step) prev = s; return prev; }
        return o[Math.max(0, i - 1)];
    }

    /** The dots under the card: every step between the welcome and the last card is one dot. Returns {index, total} or null. */
    static int[] dots(int step, boolean simple, boolean tuner) {
        int[] o = order(simple, tuner);
        int i = indexOf(o, step);
        if (i <= 0 || i >= o.length - 1) return null;
        return new int[]{i - 1, o.length - 2};
    }

    /** A remembered step that no longer exists (an older build) restarts at the welcome. */
    static int resume(int saved) { return saved < WELCOME || saved >= COUNT ? WELCOME : saved; }

    /** Keys that move through the card and therefore must not count as "the button you pressed" on the remote step. */
    static boolean isNavigation(int keyCode) {
        switch (keyCode) {
            case 19: case 20: case 21: case 22: case 23: case 66: case 160: case 4: case 3: return true;     // D-pad, OK/Enter, Back, Home
            default: return false;
        }
    }
}
