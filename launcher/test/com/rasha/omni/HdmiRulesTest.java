package com.rasha.omni;

final class HdmiRulesTest {
    static void run() {
        T.ok(HdmiRules.autoStopMs(true, 600) == 0 && HdmiRules.autoStopMs(true, 0) == 0 && HdmiRules.autoStopMs(true, 15) == 0, "a full-screen picture never has a timer");
        T.ok(HdmiRules.autoStopMs(false, 600) == 600_000L, "a card preview keeps its limit");
        T.ok(HdmiRules.autoStopMs(false, 15) == 15_000L, "a short probe preview keeps its short limit");
        T.ok(HdmiRules.autoStopMs(false, 0) == 600_000L && HdmiRules.autoStopMs(false, -5) == 600_000L, "a missing or bad value falls back to ten minutes");
        T.ok(HdmiRules.autoStopMs(false, 999_999) == 3_600_000L, "a preview is capped at an hour");
    }
}
