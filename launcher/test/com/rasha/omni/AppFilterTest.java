package com.rasha.omni;

final class AppFilterTest {
    static void run() {
        T.ok(AppFilter.isHidden("com.rasha.omni"), "OMNI hides itself");
        // regression: v2 matched our own package by prefix and hid sibling apps such as com.rasha.omnitest
        T.ok(!AppFilter.isHidden("com.rasha.omnitest"), "a sibling app whose name starts with OMNI's stays visible");
        T.ok(AppFilter.isHidden("com.mediatek.tv.factory"), "MediaTek service hidden");
        T.ok(AppFilter.isHidden("com.vestel.tv.service"), "Vestel service hidden");
        T.ok(AppFilter.isHidden("com.android.tv.settings"), "system settings hidden (reachable from the Settings tab)");
        T.ok(!AppFilter.isHidden("com.netflix.ninja"), "Netflix visible");
        T.ok(!AppFilter.isHidden("com.google.android.youtube.tv"), "YouTube visible");
    }
}
