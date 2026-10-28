package com.rasha.omni;

/** Pure rules for the HDMI picture window. */
final class HdmiRules {
    private HdmiRules() { }

    static final int DEFAULT_PREVIEW_SECONDS = 600, MAX_PREVIEW_SECONDS = 3600;

    /**
     * How long the window may stay up before it closes itself, in milliseconds; 0 means never. Only a small preview on a card has a limit (so a forgotten
     * preview cannot hold the input for ever). A full-screen picture belongs to the person watching it: it ends when they leave, never on a timer.
     * (The bug this prevents: a 10-minute preview timer kept running after the picture went full screen and ended the HDMI view, and the empty viewer
     * left on top of the launcher looked like a crash until Home was pressed.)
     */
    static long autoStopMs(boolean fullscreen, int requestedSeconds) {
        if (fullscreen) return 0;
        int s = requestedSeconds <= 0 ? DEFAULT_PREVIEW_SECONDS : Math.min(requestedSeconds, MAX_PREVIEW_SECONDS);
        return s * 1000L;
    }
}
