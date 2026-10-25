package com.rasha.omni;

import java.util.Arrays;
import java.util.Collections;

final class ControlRowsTest {
    static void run() {
        T.ok("Unknown".equals(ControlRows.storage(-1, 100)) && "Unknown".equals(ControlRows.storage(5, 0)), "unknown storage is said, not invented");
        T.ok("2.5 GB free of 8.0 GB".equals(ControlRows.storage(2684354560L, 8589934592L)), "storage in GB with one decimal");
        T.ok("512 MB".equals(ControlRows.size(536870912L)) && "120 GB".equals(ControlRows.size(128849018880L)), "sizes: MB below 1 GB, no decimals from 100 GB");
        T.ok("Checking\u2026".equals(ControlRows.inputs(null)) && "None with a signal".equals(ControlRows.inputs(Collections.<String>emptyList())), "inputs: loading and empty");
        T.ok("PlayStation 5".equals(ControlRows.inputs(Arrays.asList("PlayStation 5"))) && "A, B, C +2".equals(ControlRows.inputs(Arrays.asList("A", "B", "C", "D", "E"))), "inputs: list and overflow");
        T.ok(ControlRows.volumeStep(10, 100, 1) == 15 && ControlRows.volumeStep(10, 100, -1) == 5 && ControlRows.volumeStep(98, 100, 1) == 100 && ControlRows.volumeStep(2, 100, -1) == 0, "volume steps by 5% and clamps");
        T.ok(ControlRows.volumeStep(3, 15, 1) == 4 && ControlRows.volumeStep(0, 0, 1) == 0, "volume on a 15-step scale moves by one, and a missing scale is safe");
        T.ok(ControlRows.volumePercent(7, 15) == 47 && ControlRows.volumePercent(20, 15) == 100 && ControlRows.volumePercent(-1, 15) == 0 && ControlRows.volumePercent(1, 0) == 0, "volume percent is clamped");
        T.ok("Signal".equals(ControlRows.inputState(true, false)) && "Standby".equals(ControlRows.inputState(false, true)) && "Nothing connected".equals(ControlRows.inputState(false, false)), "input state words");
        T.ok("Not set up".equals(ControlRows.server(false, false, false, "x")) && "Not checked yet".equals(ControlRows.server(true, false, false, "x")), "server: unset and unchecked never claim anything");
        T.ok("Not reachable".equals(ControlRows.server(true, true, false, "x")) && "Reachable".equals(ControlRows.server(true, true, true, "")) && "Up".equals(ControlRows.server(true, true, true, "Up")), "server: checked results");
    }
}
