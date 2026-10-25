package com.rasha.omni;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the assistant does with a sentence. Pure (tested on the JVM): words in, a ranked list of actions out. The first action is what OMNI does
 * on its own; the rest become chips. Anything OMNI cannot do itself ends in an ASK action, which hands the words to the TV's own assistant.
 * Understands short English, Italian and Albanian commands ("open youtube", "apri netflix", "hap youtube", "volume up", "alza il volume", "playstation", "weather").
 */
final class Intents {
    static final int OPEN_APP = 1, INPUT = 2, VOLUME_UP = 3, VOLUME_DOWN = 4, MUTE = 5, VOLUME_SET = 6, PLAY = 7, WEATHER = 8, SEARCH = 9, HOME = 10, SETTINGS = 11, CONTROL = 12, ASK = 13;

    static final class Action {
        final int kind; final String arg; final String label; final int number;
        Action(int kind, String arg, int number, String label) { this.kind = kind; this.arg = arg; this.number = number; this.label = label; }
        @Override public String toString() { return kind + ":" + arg + ":" + number; }
    }

    private Intents() { }

    private static final String[] OPEN = {"open", "launch", "start", "go to", "switch to", "change to", "apri", "avvia", "vai su", "passa a", "hap", "nis", "kalo te", "kalo ne"};
    private static final String[] PLAYW = {"play", "watch", "put on", "guarda", "riproduci", "metti", "luaj", "shiko"};
    private static final String[] SEARCHW = {"search for", "search", "find", "look for", "cerca", "trova", "kerko", "gjej"};
    private static final String[] UP = {"volume up", "louder", "turn it up", "turn up", "alza il volume", "alza volume", "piu forte", "ngre zerin", "me fort", "zeri lart"};
    private static final String[] DOWN = {"volume down", "quieter", "turn it down", "turn down", "abbassa il volume", "abbassa volume", "piu piano", "ul zerin", "me ngadale", "zeri posht"};
    private static final String[] MUTEW = {"mute", "silence", "unmute", "muto", "silenzio", "pa ze", "hiq zerin"};
    private static final String[] WEATHERW = {"weather", "forecast", "temperature", "will it rain", "meteo", "che tempo", "previsioni", "moti", "parashikimi"};
    private static final String[] HOMEW = {"go home", "home screen", "home", "schermata home", "casa", "shtepi", "faqja kryesore"};
    private static final String[] SETTINGSW = {"settings", "impostazioni", "cilesimet", "parametrat"};
    private static final String[] CONTROLW = {"control center", "control centre", "centro di controllo", "qendra e kontrollit"};

    /** Lower case, no accents, no punctuation, single spaces. */
    static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * @param said     what the person said or typed
     * @param apps     installed app labels
     * @param inputs   input names as OMNI shows them ("PlayStation 5", "HDMI 3", "Antenna")
     */
    static List<Action> parse(String said, List<String> apps, List<String> inputs) {
        List<Action> out = new ArrayList<>();
        String t = norm(said);
        if (t.isEmpty()) return out;
        // volume first: "volume up" must never be read as an app called "volume"
        Integer level = volumeLevel(t);
        if (level != null) out.add(new Action(VOLUME_SET, null, level, "Volume " + level + "%"));
        else if (starts(t, UP) != null || t.equals("up")) out.add(new Action(VOLUME_UP, null, 0, "Volume up"));
        else if (starts(t, DOWN) != null || t.equals("down")) out.add(new Action(VOLUME_DOWN, null, 0, "Volume down"));
        else if (whole(t, MUTEW)) out.add(new Action(MUTE, null, 0, t.startsWith("un") ? "Unmute" : "Mute"));
        if (!out.isEmpty()) return out;

        if (whole(t, CONTROLW)) { out.add(new Action(CONTROL, null, 0, "Open Control Center")); return out; }
        if (whole(t, SETTINGSW) || startsThen(t, OPEN, SETTINGSW)) { out.add(new Action(SETTINGS, null, 0, "Open Settings")); return out; }
        if (whole(t, HOMEW)) { out.add(new Action(HOME, null, 0, "Go Home")); return out; }
        if (contains(t, WEATHERW)) { out.add(new Action(WEATHER, null, 0, "Show the weather")); return out; }

        String rest = starts(t, OPEN);
        String target = rest != null ? rest : t;
        String input = best(target, inputs);
        String app = best(target, apps);
        // an input wins when it matches at least as well (people say "playstation", not "open playstation 5")
        if (input != null && (app == null || score(target, input) >= score(target, app))) out.add(new Action(INPUT, input, 0, "Switch to " + input));
        if (app != null) out.add(new Action(OPEN_APP, app, 0, "Open " + app));
        if (!out.isEmpty() && rest != null) { out.add(new Action(ASK, said.trim(), 0, "Ask Google")); return out; }

        String play = starts(t, PLAYW);
        if (play != null && !play.isEmpty()) out.add(new Action(PLAY, play, 0, "Find “" + play + "” in Cinema"));
        String search = starts(t, SEARCHW);
        String q = search != null && !search.isEmpty() ? search : play != null && !play.isEmpty() ? play : t;
        out.add(new Action(SEARCH, q, 0, "Search OMNI for “" + q + "”"));
        out.add(new Action(ASK, said.trim(), 0, "Ask Google"));
        // a question goes to the assistant first
        if (isQuestion(t) && out.get(0).kind != INPUT && out.get(0).kind != OPEN_APP) {
            Action ask = out.remove(out.size() - 1);
            out.add(0, ask);
        }
        return out;
    }

    static boolean isQuestion(String t) {
        String[] q = {"who ", "what ", "when ", "where ", "why ", "how ", "is ", "are ", "can ", "do ", "does ", "chi ", "cosa ", "che ", "quando ", "dove ", "perche ", "come ", "kush ", "cfare ", "kur ", "ku ", "pse ", "si "};
        for (String s : q) if (t.startsWith(s)) return true;
        return false;
    }

    /** "volume 30", "volume to 30", "volume al 30", "zeri 30" -> 30 (clamped 0..100); otherwise null. */
    static Integer volumeLevel(String t) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(?:set )?(?:the )?(?:volume|zeri|zerin)(?: to| at| al| a| ne)? (\\d{1,3})(?: percent| per cento| perqind)?$").matcher(t);
        if (!m.find()) return null;
        return Math.max(0, Math.min(100, Integer.parseInt(m.group(1))));
    }

    private static String starts(String t, String[] words) {
        for (String w : words) { if (t.equals(w)) return ""; if (t.startsWith(w + " ")) return t.substring(w.length() + 1).trim(); }
        return null;
    }

    private static boolean startsThen(String t, String[] first, String[] then) { String r = starts(t, first); return r != null && whole(r, then); }

    private static boolean whole(String t, String[] words) { for (String w : words) if (t.equals(w)) return true; return false; }

    private static boolean contains(String t, String[] words) { for (String w : words) if ((" " + t + " ").contains(" " + w + " ")) return true; return false; }

    /** The candidate that best matches what was said, or null when nothing is a convincing match. */
    static String best(String said, List<String> names) {
        if (names == null || said.isEmpty()) return null;
        String bestName = null; int bestScore = 0;
        for (String n : names) { int s = score(said, n); if (s > bestScore) { bestScore = s; bestName = n; } }
        return bestScore >= 60 ? bestName : null;
    }

    /** 100 exact, 90 the name starts with what was said (or the other way round), 75 a whole word of the name, 60 all said words appear; else 0. */
    static int score(String said, String name) {
        String a = norm(said), b = norm(name), bc = b.replace(" ", ""), ac = a.replace(" ", "");
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b) || ac.equals(bc)) return 100;
        if ((bc.startsWith(ac) && ac.length() >= 3 && ac.length() * 2 >= bc.length()) || (ac.startsWith(bc) && bc.length() >= 4)) return 90;     // "play" is not enough for "PlayStation 5"
        for (String w : b.split(" ")) if (w.length() >= 3 && w.equals(a)) return 75;
        boolean all = true;
        for (String w : a.split(" ")) if (!(" " + b + " ").contains(" " + w + " ")) { all = false; break; }
        return all ? 60 : 0;
    }
}
