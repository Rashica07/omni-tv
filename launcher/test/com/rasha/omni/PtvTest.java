package com.rasha.omni;

final class PtvTest {
    static void run() {
        T.ok(PtvRules.safeUrl("https://tv.example.al/live/index.m3u8"), "https url ok");
        T.ok(PtvRules.safeUrl("http://192.168.1.146:8765/health"), "http lan ok");
        T.ok(!PtvRules.safeUrl("file:///etc/passwd"), "file refused");
        T.ok(!PtvRules.safeUrl("intent://x#Intent;end"), "intent refused");
        T.ok(!PtvRules.safeUrl("javascript:alert(1)"), "javascript refused");
        T.ok(!PtvRules.safeUrl("http://user:pw@host/x.m3u8"), "credentials in url refused");
        T.ok(!PtvRules.safeUrl("http://a/b c.m3u8"), "whitespace refused");
        T.ok(!PtvRules.safeUrl("http://"), "no host refused");
        T.ok(!PtvRules.safeUrl(null) && !PtvRules.safeUrl(""), "null/empty refused");
        StringBuilder big = new StringBuilder("http://a/");
        for (int i = 0; i < 700; i++) big.append('x');
        T.ok(!PtvRules.safeUrl(big.toString()), "oversized url refused");
        T.ok("A B".equals(PtvRules.cleanName("A\nB")), "newline removed from names");
        T.ok(PtvRules.cleanName("x".repeat(200)).length() == 60, "names bounded");

        T.ok("http://192.168.1.146:8765".equals(PtvRules.serverBase("192.168.1.146")), "bare host gets default port");
        T.ok("http://192.168.1.146:9000".equals(PtvRules.serverBase(" 192.168.1.146:9000 ")), "explicit port kept");
        T.ok("http://box.local:8765".equals(PtvRules.serverBase("http://box.local/")), "trailing slash ok");
        T.ok("https://box.example:8765".equals(PtvRules.serverBase("https://box.example")), "https only when typed");
        T.ok(PtvRules.serverBase("http://box/health") == null, "a path is not a base address");
        T.ok(PtvRules.serverBase("ftp://box") == null && PtvRules.serverBase("") == null && PtvRules.serverBase(null) == null, "bad bases refused");

        T.ok("Albania".equals(PtvRules.group("AL")) && "Kosovo".equals(PtvRules.group("XK")) && "Other".equals(PtvRules.group("IT")), "groups");
        T.ok(PtvRules.offered(true, "plays") && !PtvRules.offered(true, "blocked-403") && !PtvRules.offered(false, "plays") && !PtvRules.offered(true, "fails"),
                "only enabled + playing channels are offered");

        T.ok(WeatherCodes.kind(0) == WeatherCodes.CLEAR && WeatherCodes.kind(2) == WeatherCodes.PARTLY && WeatherCodes.kind(63) == WeatherCodes.RAIN
                && WeatherCodes.kind(73) == WeatherCodes.SNOW && WeatherCodes.kind(96) == WeatherCodes.STORM && WeatherCodes.kind(45) == WeatherCodes.FOG
                && WeatherCodes.kind(999) == WeatherCodes.UNKNOWN, "wmo mapping");
        T.ok("18°".equals(WeatherCodes.temp(18.4)) && "-3°".equals(WeatherCodes.temp(-2.6)) && "0°".equals(WeatherCodes.temp(-0.2)), "temperature rounding");
        T.ok("".equals(WeatherCodes.temp(Double.NaN)) && "".equals(WeatherCodes.temp(999)), "implausible temperature never shown");
        T.ok("just now".equals(WeatherCodes.age(30000)) && "12 min ago".equals(WeatherCodes.age(12 * 60000L)) && "3 h ago".equals(WeatherCodes.age(3 * 3600000L))
                && "a day or more ago".equals(WeatherCodes.age(30 * 3600000L)), "age text");

        T.ok(NetKind.transport(true, true, false) == NetKind.ETHERNET && NetKind.transport(true, false, false) == NetKind.WIFI
                && NetKind.transport(false, false, false) == NetKind.NONE && NetKind.transport(false, false, true) == NetKind.OTHER, "transport priority");
        T.ok(NetKind.describe(NetKind.WIFI, false).contains("could not confirm"), "wifi without validation is not called internet");
        T.ok(NetKind.describe(NetKind.ETHERNET, true).equals("Ethernet, internet reachable") && "Not connected".equals(NetKind.describe(NetKind.NONE, true)), "descriptions");
    }
}
