package com.rasha.omni;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Channel search matching. Case, accents and punctuation are ignored ("Dukagjini" finds "Dukagjini", "ë" matches "e", "ç" matches "c"), and every
 * word typed must start a word of the channel's name, alternative names, country, language or category, so partial typing works.
 */
final class PtvSearch {
    private PtvSearch() { }

    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) == Character.NON_SPACING_MARK) continue;
            b.append(Character.isLetterOrDigit(c) ? c : ' ');
        }
        return b.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * Every word typed must start a word of the channel's searchable text; a word of 4+ letters may also appear inside the text with the
     * spaces removed, so "news24" finds "News 24".
     */
    static boolean matches(String query, PtvModel.Channel c) { return matches(prepare(query), c); }

    /** Fold the typed text once per keystroke, not once per channel. */
    static String[] prepare(String query) {
        String q = fold(query);
        return q.isEmpty() ? new String[0] : q.split(" ");
    }

    static boolean matches(String[] queryWords, PtvModel.Channel c) {
        if (queryWords.length == 0) return true;
        if (c.hay == null) {
            StringBuilder hay = new StringBuilder();
            hay.append(c.name).append(' ');
            for (String a : c.alt) hay.append(a).append(' ');
            hay.append(countryWords(c.country)).append(' ');
            if (c.isTuner()) hay.append("tuner antenna terrestrial dvb live tv ").append(c.number).append(' ');
            for (String l : c.languages) hay.append(languageWords(l)).append(' ');
            for (String k : c.categories) hay.append(k).append(' ');
            c.hay = fold(hay.toString());
            c.hayWords = c.hay.split(" ");
            c.hayFlat = c.hay.replace(" ", "");
        }
        String[] words = c.hayWords;
        String squashed = c.hayFlat;
        for (String w : queryWords) {
            boolean ok = false;
            for (String hw : words) if (hw.startsWith(w)) { ok = true; break; }
            if (!ok && w.length() >= 4 && squashed.contains(w)) ok = true;
            if (!ok) return false;
        }
        return true;
    }

    static String countryWords(String cc) {
        if ("AL".equals(cc)) return "Albania Shqiperi";
        if ("XK".equals(cc)) return "Kosovo Kosova";
        return cc;
    }

    private static String languageWords(String l) {
        if ("sqi".equals(l) || "alb".equals(l)) return "Albanian Shqip";
        if ("srp".equals(l)) return "Serbian";
        return l;
    }
}
