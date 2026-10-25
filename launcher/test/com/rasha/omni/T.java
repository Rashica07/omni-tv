package com.rasha.omni;

/** Minimal assertions: failures throw, the runner prints and exits non-zero. */
final class T {
    static int checks;

    static void ok(boolean c, String msg) {
        checks++;
        if (!c) throw new AssertionError(msg);
    }

    static void eq(float a, float b, float eps, String msg) { ok(Math.abs(a - b) <= eps, msg + " (expected " + b + ", got " + a + ")"); }
}
