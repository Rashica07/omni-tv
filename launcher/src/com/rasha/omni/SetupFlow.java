package com.rasha.omni;

/**
 * The order of the first-run setup and what moving through it means. Pure, so it is tested on the JVM.
 * Every step can be skipped; Back never leaves the first step (setup is left with "Skip setup" or by finishing); the dots count only the real steps.
 */
final class SetupFlow {
    static final int WELCOME = 0, HOME = 1, REMOTE = 2, SERVER = 3, WEATHER = 4, PERMISSIONS = 5, DONE = 6;
    static final int COUNT = 7;
    static final String[] TITLES = {"WELCOME TO OMNI UI", "Make OMNI your Home", "Your remote", "Your OMNI server", "Weather", "Permissions", "You're all set"};

    private SetupFlow() { }

    static int next(int step) { return Math.min(DONE, step + 1); }

    static int back(int step) { return Math.max(WELCOME, step - 1); }

    /** The dots under the card: steps 1..5 are numbered, the welcome and the last card have none. Returns {index, total} or null. */
    static int[] dots(int step) { return step <= WELCOME || step >= DONE ? null : new int[]{step - 1, DONE - 1}; }

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
