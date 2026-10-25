package com.rasha.omni;

/**
 * Decides whether live previews inside channel cards are worth attempting on this TV. A preview is "bad" if it failed or took too long to show
 * its first picture; after {@link #LIMIT} bad previews in a row the TV switches to the lightweight artwork mode on its own (and says so).
 * The user can switch previews back on at any time, which also resets the count. Pure, so the rule is tested.
 */
final class PreviewPolicy {
    static final int LIMIT = 3;
    static final long SLOW_MS = 7000L;

    private int bad;
    private boolean userOff;
    private boolean autoOff;

    PreviewPolicy(boolean userOff, boolean autoOff) {
        this.userOff = userOff;
        this.autoOff = autoOff;
    }

    boolean allowed() { return !userOff && !autoOff; }

    boolean userOff() { return userOff; }

    boolean autoOff() { return autoOff; }

    /** Reports one finished preview attempt. Returns true when this one tipped the policy into artwork-only mode. */
    boolean record(boolean ok, long msToFirstFrame) {
        if (ok && msToFirstFrame <= SLOW_MS) {
            bad = 0;
            return false;
        }
        bad++;
        if (bad >= LIMIT && !autoOff) {
            autoOff = true;
            return true;
        }
        return false;
    }

    /** The device's player cannot show video in a card (detected, not guessed): artwork-only mode from now on, until the user turns previews on again. */
    boolean markUnsupported() {
        boolean was = autoOff;
        autoOff = true;
        bad = LIMIT;
        return !was;
    }

    void setUserOff(boolean off) {
        userOff = off;
        if (!off) {              // switching previews on again is a fresh start
            autoOff = false;
            bad = 0;
        }
    }
}
