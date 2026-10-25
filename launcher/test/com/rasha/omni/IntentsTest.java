package com.rasha.omni;

import java.util.Arrays;
import java.util.List;

final class IntentsTest {
    static final List<String> APPS = Arrays.asList("YouTube", "Netflix", "Prime Video", "1001 TVs", "OMNI Cinema", "Spotify", "Disney+");
    static final List<String> INPUTS = Arrays.asList("PlayStation 5", "HDMI 1", "HDMI 2", "Antenna");

    static Intents.Action first(String s) { List<Intents.Action> l = Intents.parse(s, APPS, INPUTS); return l.isEmpty() ? null : l.get(0); }

    static void run() {
        T.ok(Intents.parse("", APPS, INPUTS).isEmpty() && Intents.parse("   ", APPS, INPUTS).isEmpty(), "nothing said, nothing done");
        T.ok(first("open youtube").kind == Intents.OPEN_APP && "YouTube".equals(first("open youtube").arg), "open youtube");
        T.ok(first("Apri Netflix").kind == Intents.OPEN_APP && "Netflix".equals(first("Apri Netflix").arg), "Italian open");
        T.ok(first("hap youtube").kind == Intents.OPEN_APP, "Albanian open");
        T.ok(first("prime").kind == Intents.OPEN_APP && "Prime Video".equals(first("prime").arg), "a name's first word is enough");
        T.ok(first("disney plus") != null, "an unknown phrase still gives actions");
        T.ok(first("playstation").kind == Intents.INPUT && "PlayStation 5".equals(first("playstation").arg), "an input by its device name");
        T.ok(first("switch to hdmi 2").kind == Intents.INPUT && "HDMI 2".equals(first("switch to hdmi 2").arg) && "HDMI 2".equals(first("passa a hdmi 2").arg), "switch to an input");
        T.ok(first("open hdmi 1").kind == Intents.INPUT && "HDMI 1".equals(first("open hdmi 1").arg), "open an input");
        T.ok(first("antenna").kind == Intents.INPUT, "antenna is an input");
        T.ok(Intents.parse("play his girl friday", java.util.Arrays.asList("Play", "YouTube"), INPUTS).get(0).kind == Intents.PLAY, "a sentence starting with an app's name is not that app");
        T.ok(Intents.parse("youtube kids", java.util.Arrays.asList("YouTube"), INPUTS).get(0).kind == Intents.OPEN_APP, "a short extra word still opens the app");
        T.ok(first("play") == null || first("play").kind != Intents.INPUT, "a lone \"play\" never switches to the PlayStation");
        T.ok(first("playstation").kind == Intents.INPUT && first("netf").kind == Intents.OPEN_APP, "a long enough start still matches");
        T.ok(first("volume up").kind == Intents.VOLUME_UP && first("louder").kind == Intents.VOLUME_UP && first("alza il volume").kind == Intents.VOLUME_UP, "volume up in three ways");
        T.ok(first("volume down").kind == Intents.VOLUME_DOWN && first("abbassa il volume").kind == Intents.VOLUME_DOWN, "volume down");
        T.ok(first("mute").kind == Intents.MUTE && first("muto").kind == Intents.MUTE, "mute");
        Intents.Action v = first("volume to 30");
        T.ok(v.kind == Intents.VOLUME_SET && v.number == 30, "volume to a level");
        T.ok(first("volume 250").number == 100 && first("zeri 20").number == 20, "level clamps; Albanian level");
        T.ok(Intents.parse("volume up", APPS, INPUTS).size() == 1, "volume is never also an app search");
        T.ok(first("weather").kind == Intents.WEATHER && first("che tempo fa domani").kind == Intents.WEATHER && first("moti sot").kind == Intents.WEATHER, "weather three ways");
        T.ok(first("settings").kind == Intents.SETTINGS && first("open settings").kind == Intents.SETTINGS && first("impostazioni").kind == Intents.SETTINGS, "settings");
        T.ok(first("home").kind == Intents.HOME && first("go home").kind == Intents.HOME, "home");
        T.ok(first("control center").kind == Intents.CONTROL, "control center");
        List<Intents.Action> p = Intents.parse("play his girl friday", APPS, INPUTS);
        T.ok(p.get(0).kind == Intents.PLAY && "his girl friday".equals(p.get(0).arg), "play a film");
        T.ok(p.get(p.size() - 1).kind == Intents.ASK, "every free-form request can go to Google");
        List<Intents.Action> s = Intents.parse("search jungle book", APPS, INPUTS);
        T.ok(s.get(0).kind == Intents.SEARCH && "jungle book".equals(s.get(0).arg), "search");
        List<Intents.Action> q = Intents.parse("who won the match yesterday", APPS, INPUTS);
        T.ok(q.get(0).kind == Intents.ASK && "who won the match yesterday".equals(q.get(0).arg), "a question goes to the assistant first");
        T.ok(Intents.parse("chi ha vinto ieri", APPS, INPUTS).get(0).kind == Intents.ASK && Intents.parse("kush fitoi dje", APPS, INPUTS).get(0).kind == Intents.ASK, "questions in Italian and Albanian");
        T.ok(Intents.score("netflix", "Netflix") == 100 && Intents.score("1001 tvs", "1001 TVs") == 100 && Intents.score("you tube", "YouTube") == 100, "spacing and case do not matter");
        T.ok(Intents.best("zzzz", APPS) == null && Intents.best("ne", APPS) == null, "weak matches are not guessed");
        T.ok("amelie".equals(Intents.norm("Amélie!")), "accents and punctuation are ignored");
    }
}
