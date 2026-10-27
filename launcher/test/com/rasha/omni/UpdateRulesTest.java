package com.rasha.omni;

import java.util.ArrayList;
import java.util.List;

final class UpdateRulesTest {
    static UpdateRules.Release r(String tag, boolean pre, String url) { return new UpdateRules.Release(tag, pre, url, 2_000_000, "SHA-256: `" + "ab".repeat(32) + "`"); }

    static void run() {
        T.ok(UpdateRules.compare("v1.0.0-rc.10", "1.0.0-rc.9") > 0, "rc.10 is newer than rc.9, not older (numbers, not text)");
        T.ok(UpdateRules.compare("v1.0.0", "1.0.0-rc.9") > 0, "the final release is newer than any rc");
        T.ok(UpdateRules.compare("1.0.0-rc.1", "1.0.0-beta.9") > 0, "rc is newer than beta");
        T.ok(UpdateRules.compare("1.0.1-beta.1", "1.0.0") > 0, "a higher patch wins over a stage");
        T.ok(UpdateRules.compare("1.0.0-rc.9", "v1.0.0-rc.9") == 0, "the v prefix does not matter");
        T.ok(UpdateRules.compare("nightly", "1.0.0") == 0 && UpdateRules.compare("1.0.0-foo.1", "1.0.0") == 0 && UpdateRules.compare(null, "1.0.0") == 0, "anything that is not a version is never newer");

        List<UpdateRules.Release> l = new ArrayList<>();
        l.add(r("v1.0.0-rc.2", true, "https://github.com/a/b/releases/download/x/omni.apk"));
        l.add(r("v1.0.0-rc.10", true, "https://github.com/a/b/releases/download/y/omni.apk"));
        l.add(r("v1.0.0-rc.9", true, "https://github.com/a/b/releases/download/z/omni.apk"));
        l.add(r("v1.0.0-rc.11", true, ""));
        T.ok("v1.0.0-rc.10".equals(UpdateRules.pick(l, "1.0.0-rc.8", true).tag), "the newest release with an APK is picked");
        T.ok(UpdateRules.pick(l, "1.0.0-rc.10", true) == null, "nothing newer: no update");
        T.ok(UpdateRules.pick(l, "1.0.0-rc.1", false) == null, "pre-releases are skipped when the person chose stable only");
        l.add(r("v1.0.0", false, "https://github.com/a/b/releases/download/f/omni.apk"));
        T.ok("v1.0.0".equals(UpdateRules.pick(l, "1.0.0-rc.9", false).tag), "stable only still finds the final release");

        String hex = "0123456789abcdef".repeat(4);
        T.ok(hex.equals(UpdateRules.sha256("Notes\n**SHA-256**\n```\n" + hex + "\n```")), "checksum after a heading and a code fence");
        T.ok(hex.equals(UpdateRules.sha256("SHA-256: `" + hex.toUpperCase() + "`")), "checksum is lower-cased");
        T.ok(UpdateRules.sha256("no checksum here") == null && UpdateRules.sha256(null) == null, "no checksum, none returned");
        T.ok(UpdateRules.sha256("sha256 " + hex + "00") == null, "a longer hex run is not a checksum");

        T.ok(UpdateRules.hostAllowed("https://github.com/Rashica07/omni-tv/releases/download/v1/omni.apk"), "github.com is allowed");
        T.ok(UpdateRules.hostAllowed("https://objects.githubusercontent.com/x"), "the release CDN is allowed");
        T.ok(!UpdateRules.hostAllowed("http://github.com/x") && !UpdateRules.hostAllowed("https://github.com.evil.example/x") && !UpdateRules.hostAllowed("https://evil.example/github.com") && !UpdateRules.hostAllowed("file:///sdcard/x.apk") && !UpdateRules.hostAllowed("nonsense"), "plain http, look-alike hosts and other schemes are refused");

        long h = 3600_000L, now = 100 * h;
        T.ok(UpdateRules.due(now, 0, 0), "never checked: due");
        T.ok(!UpdateRules.due(now, now - 3 * h, now - 3 * h), "checked 3 hours ago: not due");
        T.ok(UpdateRules.due(now, now - 25 * h, now - 25 * h), "a day later: due");
        T.ok(!UpdateRules.due(now, now - 30 * h, now - 1 * h) && UpdateRules.due(now, now - 30 * h, now - 7 * h), "after a failure wait six hours");
        T.ok(UpdateRules.due(now, now + 5 * h, now + 5 * h), "a clock set back never blocks checking");

        T.ok(UpdateRules.installable("com.rasha.omni", 109, "com.rasha.omni", 110), "newer build of the same package installs");
        T.ok(!UpdateRules.installable("com.rasha.omni", 109, "com.rasha.omni", 109) && !UpdateRules.installable("com.rasha.omni", 109, "com.rasha.omni", 108), "same or older is never installed (no downgrade)");
        T.ok(!UpdateRules.installable("com.rasha.omni", 109, "com.evil.app", 999) && !UpdateRules.installable("com.rasha.omni", 109, null, 999), "a different package is never installed");

        T.ok("2.0 MB".equals(UpdateRules.size(2_097_152)) && "".equals(UpdateRules.size(0)), "sizes");
        String n = UpdateRules.notes("**OMNI TV 1.0.0 RC 3**\n\n- First line\n- Second `code`\n\nSHA-256: " + hex, 2, 400);
        T.ok(n.equals("OMNI TV 1.0.0 RC 3\nFirst line"), "notes are the first plain lines");
    }
}
