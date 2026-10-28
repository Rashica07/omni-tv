package com.rasha.omni;

final class ZoomRulesTest {
    static void run() {
        T.ok(ZoomRules.usable(true, true, false), "on, allowed, animations on: used");
        T.ok(!ZoomRules.usable(false, true, false) && !ZoomRules.usable(true, false, false) && !ZoomRules.usable(true, true, true), "off, no overlay permission or reduced motion: not used");
        T.ok(ZoomRules.lerp(10f, 20f, 0f) == 10f && ZoomRules.lerp(10f, 20f, 1f) == 20f && ZoomRules.lerp(10f, 20f, 0.5f) == 15f && ZoomRules.lerp(10f, 20f, 2f) == 20f && ZoomRules.lerp(10f, 20f, -1f) == 10f, "lerp is clamped");
        T.ok(ZoomRules.radius(0f, 12f) == 12f && ZoomRules.radius(1f, 12f) == 0f && ZoomRules.radius(0.5f, 12f) == 6f, "corners go from the tile's radius to square");
        T.ok(ZoomRules.artAlpha(0f) == 1f && ZoomRules.artAlpha(0.3f) == 1f && ZoomRules.artAlpha(1f) == 0f, "the tile artwork is fully visible while small and gone at full screen (never shown stretched)");
        T.ok(ZoomRules.iconAlpha(0f) == 0f && ZoomRules.iconAlpha(0.4f) == 0f && ZoomRules.iconAlpha(1f) == 1f, "the centred icon fades in as the artwork leaves");
        T.ok(!ZoomRules.shouldDissolve(100, true) && !ZoomRules.shouldDissolve(400, true), "never dissolves before the minimum time, even if the app is ready");
        T.ok(ZoomRules.shouldDissolve(420, true) && ZoomRules.shouldDissolve(1000, true), "dissolves once the app is ready and the minimum time has passed");
        T.ok(!ZoomRules.shouldDissolve(1500, false) && ZoomRules.shouldDissolve(2500, false), "a slow app is waited for, but never longer than the limit");
        T.ok(!ZoomRules.showProgress(500, false) && ZoomRules.showProgress(900, false) && !ZoomRules.showProgress(900, true), "a progress line only for a slow app");
        T.ok(ZoomRules.GROW_MS == 320 && ZoomRules.DISSOLVE_MS == 160, "the motion tokens are the documented ones");
    }
}
