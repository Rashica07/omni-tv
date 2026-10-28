package com.rasha.omni;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Recognises which country a channel found by the TV's own antenna scan belongs to, from its name alone, so the Antenna list can be grouped
 * (Italy, Switzerland, Albania, Kosovo) without the person doing anything. Only well-known names are matched; anything else stays "other".
 * Nothing is scanned or decoded here: the TV's tuner already did the scan. Pure, so it is tested on the JVM.
 */
final class AntennaRules {
    private AntennaRules() { }

    /** Lower case, accents removed, anything but letters and digits becomes one space. */
    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return n.replaceAll("[^a-z0-9+]+", " ").trim();
    }

    // Kosovo first (its names often contain Albanian ones), then Albania, Switzerland, Italy. A name matches when it equals an entry or starts with it plus a space.
    private static final String[] XK = {"rtk", "kohavision", "ktv", "klan kosova", "rtv21", "rtv 21", "dukagjini", "tv dukagjini", "t7", "kanal 10", "tv dardania", "dardania", "tv arta", "arta", "tv dielli", "dielli", "tv opoja", "opoja", "tv puls", "dtv", "tv mitrovica", "tringa", "rtv tema", "tema", "tv genc", "channel 10", "klan plus", "bot tv"};
    private static final String[] AL = {"rtsh", "top channel", "top story", "klan", "tv klan", "vizion plus", "news 24", "a2 cnn", "abc news", "ora news", "report tv", "euronews albania", "tring", "scan tv", "tv scan", "alsat", "agon channel", "bunker tv", "cufo", "kanal 7", "balkan web", "rtv ora", "tv 21", "fax news", "arta tv", "tv shijak", "supersport"};
    private static final String[] CH = {"srf", "rts un", "rts deux", "rts info", "rsi la 1", "rsi la 2", "rsi", "tele zueri", "tele m1", "tele bern", "telebasel", "tele ticino", "teleticino", "tvm3", "tele 1", "3+", "4+", "5+", "6+", "pulse", "puls 8", "blue zoom", "s1", "sat 1 schweiz", "3 plus", "tele bärn", "star tv", "rts"};
    private static final String[] IT = {"rai", "canale 5", "rete 4", "italia 1", "la7", "la 7", "la5", "tv8", "tv 8", "nove", "mediaset", "iris", "cine34", "cine 34", "top crime", "tgcom24", "focus", "boing", "cartoonito", "dmax", "real time", "giallo", "k2", "frisbee", "cielo", "sportitalia", "twenty seven", "27", "paramount network", "super", "rete", "italia 2", "la 5", "20 mediaset", "20", "tv2000", "tv 2000", "radio italia", "rai news", "food network", "warner tv", "spike", "tgr"};

    private static boolean matches(String f, String[] names) {
        for (String n : names) if (f.equals(n) || f.startsWith(n + " ")) return true;
        return false;
    }

    /** "XK", "AL", "CH", "IT", or "" when the name is not recognised. */
    static String country(String name) {
        String f = fold(name);
        if (f.isEmpty()) return "";
        if (matches(f, XK)) return "XK";
        if (matches(f, AL)) return "AL";
        if (matches(f, CH)) return "CH";
        if (matches(f, IT)) return "IT";
        return "";
    }

    /** The majority country of a scan, or "" when nothing is recognised or the best two are tied: used to suggest the language and the default group. */
    static String dominant(java.util.List<String> names) {
        int xk = 0, al = 0, ch = 0, it = 0;
        for (String n : names) {
            switch (country(n)) { case "XK": xk++; break; case "AL": al++; break; case "CH": ch++; break; case "IT": it++; break; default: }
        }
        int best = Math.max(Math.max(xk, al), Math.max(ch, it));
        if (best == 0) return "";
        int ties = (xk == best ? 1 : 0) + (al == best ? 1 : 0) + (ch == best ? 1 : 0) + (it == best ? 1 : 0);
        if (ties > 1) return "";
        return xk == best ? "XK" : al == best ? "AL" : ch == best ? "CH" : "IT";
    }
}
