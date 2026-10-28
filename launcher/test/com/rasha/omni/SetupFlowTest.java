package com.rasha.omni;

final class SetupFlowTest {
    static void run() {
        T.ok(SetupFlow.next(SetupFlow.WELCOME, false, true) == SetupFlow.LANGUAGE && SetupFlow.next(SetupFlow.LANGUAGE, false, true) == SetupFlow.MODE, "language and mode come right after the welcome");
        T.ok(SetupFlow.next(SetupFlow.REMOTE, false, true) == SetupFlow.SERVER, "the full setup asks about a server");
        T.ok(SetupFlow.next(SetupFlow.REMOTE, true, true) == SetupFlow.WEATHER, "simple mode never shows the server step");
        T.ok(SetupFlow.next(SetupFlow.WEATHER, true, true) == SetupFlow.ANTENNA && SetupFlow.next(SetupFlow.WEATHER, true, false) == SetupFlow.QUICK, "the antenna step only exists on a TV with a tuner");
        T.ok(SetupFlow.next(SetupFlow.PERMISSIONS, false, false) == SetupFlow.DONE && SetupFlow.next(SetupFlow.DONE, true, true) == SetupFlow.DONE, "next ends at done and never runs past it");
        T.ok(SetupFlow.back(SetupFlow.SERVER, false, true) == SetupFlow.REMOTE && SetupFlow.back(SetupFlow.WEATHER, true, true) == SetupFlow.REMOTE, "back skips what is not shown");
        T.ok(SetupFlow.back(SetupFlow.WELCOME, false, true) == SetupFlow.WELCOME, "back stops at the welcome");
        T.ok(SetupFlow.next(SetupFlow.SERVER, true, true) == SetupFlow.WEATHER && SetupFlow.back(SetupFlow.SERVER, true, true) == SetupFlow.REMOTE, "a step the mode does not show is walked past, not stuck on");
        T.ok(SetupFlow.dots(SetupFlow.WELCOME, false, true) == null && SetupFlow.dots(SetupFlow.DONE, false, true) == null, "no dots on the first and last card");
        int[] d = SetupFlow.dots(SetupFlow.REMOTE, true, false);
        T.ok(d != null && d[0] == 3 && d[1] == 7, "simple mode without a tuner: remote is dot 4 of 7");
        int[] f = SetupFlow.dots(SetupFlow.SERVER, false, true);
        T.ok(f != null && f[0] == 4 && f[1] == 9, "full setup with a tuner: server is dot 5 of 9");
        T.ok(SetupFlow.resume(99) == SetupFlow.WELCOME && SetupFlow.resume(-1) == SetupFlow.WELCOME && SetupFlow.resume(SetupFlow.SERVER) == SetupFlow.SERVER, "resume clamps unknown steps");
        T.ok(SetupFlow.TITLES.length == SetupFlow.COUNT, "a title per step");
        T.ok(SetupFlow.isNavigation(23) && SetupFlow.isNavigation(4) && !SetupFlow.isNavigation(165) && !SetupFlow.isNavigation(82), "D-pad/OK/Back are navigation; INFO and Menu are not");
    }
}
