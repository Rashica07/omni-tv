package com.rasha.omni;

final class WallpaperTest {
    private static final long H = 3_600_000L, D = 24 * H;

    static void run() {
        T.ok(WallpaperRules.index(0, WallpaperRules.DAILY, 0, 0, 0) == -1, "nothing cached: no wallpaper");
        T.ok(WallpaperRules.index(5, WallpaperRules.OFF, 123456789L, 0, 3) == -1, "off means off");
        // daily: constant through a day, different the next day
        long day0 = 20_000L * D;
        int a = WallpaperRules.index(8, WallpaperRules.DAILY, day0 + 1 * H, 0, 0), b = WallpaperRules.index(8, WallpaperRules.DAILY, day0 + 23 * H, 0, 99);
        T.ok(a == b, "daily: the same image all day, whatever the launch count");
        T.ok(WallpaperRules.index(8, WallpaperRules.DAILY, day0 + 25 * H, 0, 0) == (a + 1) % 8, "daily: next day, next image");
        // time zone: the day changes at local midnight
        T.ok(WallpaperRules.index(8, WallpaperRules.DAILY, day0 + 23 * H, 2 * H, 0) != WallpaperRules.index(8, WallpaperRules.DAILY, day0 + 23 * H, 0, 0), "daily follows the device's local midnight");
        // six hours
        T.ok(WallpaperRules.index(10, WallpaperRules.SIX_HOURS, day0 + 1 * H, 0, 0) == WallpaperRules.index(10, WallpaperRules.SIX_HOURS, day0 + 5 * H, 0, 0), "six-hour slot is stable inside the slot");
        T.ok(WallpaperRules.index(10, WallpaperRules.SIX_HOURS, day0 + 7 * H, 0, 0) != WallpaperRules.index(10, WallpaperRules.SIX_HOURS, day0 + 5 * H, 0, 0), "and changes at the next slot");
        // every launch: depends only on the launch counter, never on time
        T.ok(WallpaperRules.index(4, WallpaperRules.EVERY_LAUNCH, 1, 0, 7) == WallpaperRules.index(4, WallpaperRules.EVERY_LAUNCH, day0 * 3, 0, 7), "launch mode ignores the clock");
        T.ok(WallpaperRules.index(4, WallpaperRules.EVERY_LAUNCH, 0, 0, 8) != WallpaperRules.index(4, WallpaperRules.EVERY_LAUNCH, 0, 0, 7), "a new launch picks the next image");
        T.ok(WallpaperRules.index(3, WallpaperRules.DAILY, -5 * D, 0, 0) >= 0, "dates before 1970 never give a negative index");
        for (int n = 1; n < 20; n++) T.ok(WallpaperRules.index(n, WallpaperRules.DAILY, day0 + 777 * D, 0, 0) < n, "index in range for n=" + n);

        T.ok(WallpaperRules.slot(WallpaperRules.DAILY, day0 + H, 0, 5) == WallpaperRules.slot(WallpaperRules.DAILY, day0 + 20 * H, 0, 9) && WallpaperRules.slot(WallpaperRules.DAILY, day0, 0, 0) != WallpaperRules.slot(WallpaperRules.DAILY, day0 + 25 * H, 0, 0), "a daily slot is the same all day");
        T.ok(WallpaperRules.slot(WallpaperRules.EVERY_LAUNCH, 0, 0, 4) == 4, "launch slot is the launch count");
        T.ok(WallpaperRules.needsRefresh(3, day0, day0), "too few cached images: refresh");
        T.ok(!WallpaperRules.needsRefresh(8, day0, day0 + 5 * H), "enough images, refreshed recently: leave the network alone");
        T.ok(WallpaperRules.needsRefresh(8, day0, day0 + D + 1), "a day old: refresh");
        T.ok(WallpaperRules.needsRefresh(8, 0, day0), "never refreshed: refresh");
        T.ok(WallpaperRules.needsRefresh(8, day0 + 10 * D, day0), "a refresh stamp in the future (clock set back) does not block refreshing");

        T.ok(WallpaperRules.acceptable(5184, 3456) && WallpaperRules.acceptable(1920, 1080), "landscape photographs accepted");
        T.ok(!WallpaperRules.acceptable(8916, 1273) && !WallpaperRules.acceptable(3000, 4500) && !WallpaperRules.acceptable(1200, 800), "panoramas, portraits and small images skipped");

        T.ok("Jane Doe".equals(WallpaperRules.plain("<a href=\"x\">Jane</a>  <b>Doe</b>", 40)), "html removed from credits");
        T.ok(WallpaperRules.plain("x".repeat(500), 60).length() == 60, "credits bounded");
        T.ok(WallpaperRules.licenceAllowed("CC BY-SA 4.0") && WallpaperRules.licenceAllowed("CC0") && WallpaperRules.licenceAllowed("Public domain") && !WallpaperRules.licenceAllowed("All rights reserved") && !WallpaperRules.licenceAllowed(null), "licence filter");
        T.ok(WallpaperRules.fileFor("https://a/b.jpg").equals(WallpaperRules.fileFor("https://a/b.jpg")) && !WallpaperRules.fileFor("https://a/b.jpg").equals(WallpaperRules.fileFor("https://a/c.jpg")), "file names are stable and distinct");
        T.ok(!WallpaperRules.fileFor("../../etc/passwd").contains("/"), "a hostile url cannot choose the file path");
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};
        T.ok(WallpaperRules.looksLikeJpeg(jpg, 300_000) && !WallpaperRules.looksLikeJpeg(new byte[]{'<', 'h', 't'}, 300_000) && !WallpaperRules.looksLikeJpeg(jpg, 9_000_000) && !WallpaperRules.looksLikeJpeg(jpg, 100), "download validation");
    }
}
