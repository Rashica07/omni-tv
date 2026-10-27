package com.rasha.omni;

final class CrashRulesTest {
    static void run() {
        long now = 1_000_000_000L;
        T.ok(!CrashRules.safeMode("", now) && !CrashRules.safeMode(null, now), "no crashes: normal start");
        T.ok(!CrashRules.safeMode(String.valueOf(now - 5_000), now), "one crash is not a loop");
        T.ok(CrashRules.safeMode((now - 30_000) + "," + (now - 5_000), now), "two crashes in two minutes: safe mode");
        T.ok(!CrashRules.safeMode((now - 600_000) + "," + (now - 5_000), now), "an old crash does not count");
        T.ok(!CrashRules.safeMode((now + 50_000) + "," + (now + 60_000), now), "a clock that went backwards never counts crashes from the future");
        T.ok(!CrashRules.safeMode("abc,,12x,", now), "garbage is ignored");
        String s = "";
        for (int i = 0; i < 20; i++) s = CrashRules.add(s, now + i);
        T.ok(s.split(",").length == CrashRules.KEEP && s.endsWith(String.valueOf(now + 19)), "only the newest few are kept");
        T.ok(CrashRules.add("junk", now).equals(String.valueOf(now)), "junk in the stored list is dropped when adding");
    }
}
