package com.rasha.omni;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class SearchHitTest {
    private static List<String> titles(List<SearchHit> l) { List<String> o = new ArrayList<>(); for (SearchHit h : l) o.add(h.title); return o; }

    static void run() {
        List<SearchHit> all = Arrays.asList(
                new SearchHit(SearchHit.APP, "a:netflix", "Netflix", "App", ""),
                new SearchHit(SearchHit.APP, "a:youtube", "YouTube", "App", ""),
                new SearchHit(SearchHit.CHANNEL, "c:rai4", "Rai 4", "Antenna", "tv italia"),
                new SearchHit(SearchHit.CHANNEL, "c:rtk3", "RTK 3", "Kosovo", "Kosova"),
                new SearchHit(SearchHit.FILM, "f:heat", "Heat", "1995", "Michael Mann crime"),
                new SearchHit(SearchHit.FILM, "f:hisgirl", "His Girl Friday", "1940", "comedy"),
                new SearchHit(SearchHit.INPUT, "i:ps5", "PlayStation 5", "HDMI 3", "game console"),
                new SearchHit(SearchHit.SETTING, "s:net", "Network", "Wi-Fi and Ethernet", ""),
                new SearchHit(SearchHit.SETTING, "s:snd", "Sound", "Volume and outputs", ""));
        T.ok(SearchHit.search("", all, 10).isEmpty() && SearchHit.search("   ", all, 10).isEmpty(), "an empty query finds nothing");
        T.ok(titles(SearchHit.search("net", all, 10)).equals(Arrays.asList("Netflix", "Network")), "'net' finds the app before the setting");
        T.ok(titles(SearchHit.search("play", all, 10)).equals(Arrays.asList("PlayStation 5")), "'play' finds the input by its name");
        T.ok(titles(SearchHit.search("console", all, 10)).equals(Arrays.asList("PlayStation 5")), "extra text is searchable (game console)");
        T.ok(titles(SearchHit.search("YOUTUBE", all, 10)).equals(Arrays.asList("YouTube")), "case is ignored");
        T.ok(titles(SearchHit.search("kosova", all, 10)).equals(Arrays.asList("RTK 3")), "alternative text finds a channel");
        T.ok(titles(SearchHit.search("h", all, 10)).get(0).startsWith("H") && titles(SearchHit.search("h", all, 10)).size() >= 2, "single letter finds word starts");
        T.ok(titles(SearchHit.search("girl fri", all, 10)).equals(Arrays.asList("His Girl Friday")), "several words each start a word");
        T.ok(titles(SearchHit.search("rai4", all, 10)).equals(Arrays.asList("Rai 4")), "'rai4' finds 'Rai 4' (4+ letters/digits squashed)");
        T.ok(SearchHit.search("zzzz", all, 10).isEmpty(), "no match is empty, never a guess");
        // ranking: a title that starts with the query beats a match on extra text only
        List<SearchHit> r = Arrays.asList(new SearchHit(SearchHit.SETTING, "s", "Display", "Resolution", "sound picture"), new SearchHit(SearchHit.SETTING, "s2", "Sound", "Volume", ""));
        T.ok(titles(SearchHit.search("sound", r, 10)).equals(Arrays.asList("Sound", "Display")), "title match ranks above extra-text match");
        // equal rank: apps before channels before settings
        List<SearchHit> tie = Arrays.asList(new SearchHit(SearchHit.SETTING, "s", "Sky", "", ""), new SearchHit(SearchHit.CHANNEL, "c", "Sky", "", ""), new SearchHit(SearchHit.APP, "a", "Sky", "", ""));
        List<SearchHit> st = SearchHit.search("sky", tie, 10);
        T.ok(st.get(0).kind == SearchHit.APP && st.get(1).kind == SearchHit.CHANNEL && st.get(2).kind == SearchHit.SETTING, "ties: app, channel, setting");
        T.ok(SearchHit.search("n", all, 10).size() >= 2 && SearchHit.search("n", all, 1).size() == 1, "results are capped");
        T.ok("channel".equals(SearchHit.kindName(SearchHit.CHANNEL)) && "setting".equals(SearchHit.kindName(SearchHit.SETTING)), "kind names");
    }
}
