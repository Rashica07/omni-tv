package com.rasha.omni;

final class TrTest {
    static void run() {
        for (String k : Tr.keys()) {
            String[] v = Tr.all(k);
            T.ok(v.length == 3, "three translations: " + k);
            for (int i = 0; i < 3; i++) T.ok(v[i] != null && !v[i].trim().isEmpty(), "non-empty translation " + i + " of: " + k);
            T.ok(placeholders(k) == placeholders(v[0]) && placeholders(k) == placeholders(v[1]) && placeholders(k) == placeholders(v[2]), "same number of %-placeholders in: " + k);
            T.ok(v[2].indexOf('ß') < 0, "Swiss spelling has no sharp s: " + k);
        }
        Tr.lang = 0;
        T.ok("Home".equals(Tr.t("Home")) && "Anything not translated".equals(Tr.t("Anything not translated")), "English is the key and the fallback");
        Tr.lang = 1;
        T.ok("Cilësimet".equals(Tr.t("Settings")) && "Anything not translated".equals(Tr.t("Anything not translated")), "Albanian, with English for a missing key");
        Tr.lang = 2;
        T.ok("Impostazioni".equals(Tr.t("Settings")), "Italian");
        Tr.lang = 3;
        T.ok("Einstellungen".equals(Tr.t("Settings")) && "Strasse".equals("Strasse"), "Swiss German");
        T.ok("2 Sender".equals(Tr.count(2, "channel", "channels")) && "1 Sender".equals(Tr.count(1, "channel", "channels")), "counts");
        Tr.lang = 2;
        T.ok("1 canale".equals(Tr.count(1, "channel", "channels")) && "3 canali".equals(Tr.count(3, "channel", "channels")), "Italian singular and plural");
        Tr.lang = 0;
        T.ok(Tr.fromSystem("sq") == 1 && Tr.fromSystem("sq-AL") == 1 && Tr.fromSystem("it-CH") == 2 && Tr.fromSystem("de-CH") == 3 && Tr.fromSystem("gsw") == 3 && Tr.fromSystem("fr") == 0 && Tr.fromSystem(null) == 0, "system language to ours");
        T.ok(Tr.initial(2, "sq", true) == 2 && Tr.initial(0, "it", false) == 0, "a saved choice always wins");
        T.ok(Tr.initial(-1, "it-IT", false) == 2 && Tr.initial(-1, "sq", false) == 1 && Tr.initial(-1, "fr", false) == 0, "a first run follows the TV's language");
        T.ok(Tr.initial(-1, "it-IT", true) == 0, "an install that already finished its setup is not switched by an update");
        for (String s : SetupFlow.TITLES) T.ok(Tr.has(s), "every setup title is translated: " + s);
        for (String s : new String[]{"Home", "Watch", "Apps", "TV", "Settings"}) T.ok(Tr.has(s), "navigation is translated: " + s);
        for (String s : TvToolbarKeys.FILTERS) T.ok(Tr.has(s), "filters are translated: " + s);
    }

    private static int placeholders(String s) { int n = 0; for (int i = 0; i + 1 < s.length(); i++) if (s.charAt(i) == '%' && s.charAt(i + 1) != '%') n++; return n; }
}
