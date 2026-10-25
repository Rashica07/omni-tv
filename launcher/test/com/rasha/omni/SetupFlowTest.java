package com.rasha.omni;

final class SetupFlowTest {
    static void run() {
        T.ok(SetupFlow.next(SetupFlow.WELCOME) == SetupFlow.HOME && SetupFlow.next(SetupFlow.PERMISSIONS) == SetupFlow.DONE, "next walks the steps in order");
        T.ok(SetupFlow.next(SetupFlow.DONE) == SetupFlow.DONE, "next never runs past the end");
        T.ok(SetupFlow.back(SetupFlow.HOME) == SetupFlow.WELCOME && SetupFlow.back(SetupFlow.WELCOME) == SetupFlow.WELCOME, "back stops at the welcome");
        T.ok(SetupFlow.dots(SetupFlow.WELCOME) == null && SetupFlow.dots(SetupFlow.DONE) == null, "no dots on the first and last card");
        int[] d = SetupFlow.dots(SetupFlow.REMOTE);
        T.ok(d != null && d[0] == 1 && d[1] == 5, "remote is dot 2 of 5");
        T.ok(SetupFlow.resume(99) == SetupFlow.WELCOME && SetupFlow.resume(-1) == SetupFlow.WELCOME && SetupFlow.resume(SetupFlow.SERVER) == SetupFlow.SERVER, "resume clamps unknown steps");
        T.ok(SetupFlow.TITLES.length == SetupFlow.COUNT, "a title per step");
        T.ok(SetupFlow.isNavigation(23) && SetupFlow.isNavigation(4) && !SetupFlow.isNavigation(165) && !SetupFlow.isNavigation(82), "D-pad/OK/Back are navigation; INFO and Menu are not");
    }
}
