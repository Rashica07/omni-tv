package com.rasha.omni;

import java.util.*;

final class PtvCatalogTest {
    private static Map<String, Object> stream(String url, String probe, String tier, boolean ref, boolean on) {
        Map<String, Object> m = new HashMap<>();
        m.put("url", url); m.put("probe", probe); m.put("tier", tier); m.put("needs_referrer", ref); m.put("enabled", on);
        return m;
    }

    private static Map<String, Object> row(String id, String name, String cc, String cat, Object... streams) {
        Map<String, Object> r = new HashMap<>();
        r.put("id", id); r.put("name", name); r.put("country", cc);
        r.put("categories", new ArrayList<Object>(Arrays.asList(cat)));
        r.put("languages", new ArrayList<Object>(Arrays.asList("sqi")));
        r.put("streams", new ArrayList<Object>(Arrays.asList(streams)));
        return r;
    }

    private static List<Map<String, Object>> sample() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("AlbKanaleMusicTV.al", "AlbKanale Music TV", "AL", "music", stream("https://a.example/music.m3u8", "plays", "B", false, true)));
        rows.add(row("RTK3.xk", "RTK 3", "XK", "", stream("https://b.example/rtk3.m3u8", "plays", "B", false, true)));
        rows.add(row("News24.al", "News 24", "AL", "news", stream("https://c.example/n24.m3u8", "plays", "B", false, true), stream("http://c.example/n24b.m3u8", "plays", "B", false, true)));
        rows.add(row("CNA.al", "CNA", "AL", "news", stream("https://d.example/cna.m3u8", "blocked-403", "B", true, false)));
        rows.add(row("Syri.al", "Syri", "AL", "news", stream("https://e.example/syri.m3u8", "plays", "B", false, true)));
        rows.add(row("TVSyri.xk", "TV Syri", "XK", "general", stream("https://f.example/tvsyri.m3u8", "fails", "B", false, false)));
        return rows;
    }

    private static PtvModel.Channel byId(List<PtvModel.Channel> l, String id) {
        for (PtvModel.Channel c : l) if (c.id.equals(id)) return c;
        return null;
    }

    static void run() {
        List<PtvModel.Channel> a = PtvModel.fromRows(sample());
        T.ok(a.size() == 6, "six channels, none merged by accident");
        // identity: every channel keeps its own name, country and url
        T.ok("https://b.example/rtk3.m3u8".equals(byId(a, "RTK3.xk").urls().get(0)) && "XK".equals(byId(a, "RTK3.xk").country), "RTK 3 keeps its own stream and country");
        T.ok("https://a.example/music.m3u8".equals(byId(a, "AlbKanaleMusicTV.al").urls().get(0)), "Music TV keeps its own stream");
        T.ok(!byId(a, "RTK3.xk").urls().contains("https://a.example/music.m3u8"), "no channel ever carries another channel's url");
        T.ok(byId(a, "Syri.al") != byId(a, "TVSyri.xk") && !"Syri".equals(byId(a, "TVSyri.xk").name), "Syri and TV Syri are two channels");
        T.ok(byId(a, "News24.al").urls().size() == 2, "alternate urls stay with their channel");

        // regression: a reload creates NEW objects; lookup must work by id, not by object identity
        List<PtvModel.Channel> b = PtvModel.fromRows(sample());
        PtvModel.Channel old = byId(a, "Syri.al");
        T.ok(old != byId(b, "Syri.al") && old.id.equals(byId(b, "Syri.al").id), "reloaded objects differ but ids match");
        T.ok("https://e.example/syri.m3u8".equals(byId(b, "Syri.al").urls().get(0)), "reloaded channel resolves to its own stream");

        // access conditions are respected, not bypassed
        T.ok(!byId(a, "CNA.al").playable() && byId(a, "CNA.al").reason().contains("another website"), "a stream needing a third-party referrer is not offered");
        T.ok(!byId(a, "TVSyri.xk").playable() && byId(a, "TVSyri.xk").reason().contains("No working"), "a failing stream is not offered and says why");

        List<Map<String, Object>> ref = new ArrayList<>();
        ref.add(row("Ref.al", "Ref", "AL", "", stream("https://r.example/r.m3u8", "plays", "B", true, true)));
        T.ok(!PtvModel.fromRows(ref).get(0).playable(), "a stream that plays but needs another site's referrer is still not offered");
        // untrusted rows
        List<Map<String, Object>> bad = new ArrayList<>(sample());
        bad.add(row("", "No id", "AL", "", stream("https://x/y.m3u8", "plays", "B", false, true)));
        bad.add(row("X.al", "Bad scheme", "AL", "", stream("file:///etc/passwd", "plays", "B", false, true)));
        bad.add(row("Y.al", "Y\nnewline", "AL", "", stream("https://y.example/y.m3u8", "plays", "B", false, true)));
        bad.add(row("RTK3.xk", "RTK 3 again", "XK", "", stream("https://b.example/rtk3-alt.m3u8", "plays", "B", false, true)));
        List<PtvModel.Channel> c = PtvModel.fromRows(bad);
        T.ok(byId(c, "X.al") == null, "channel with only an unsafe url is dropped");
        T.ok(byId(c, "Y.al").name.equals("Y newline"), "names are single line");
        T.ok(byId(c, "RTK3.xk").streams.size() == 2 && "RTK 3".equals(byId(c, "RTK3.xk").name), "duplicate id merges streams, not channels");
        T.ok(c.size() == 7, "bad rows dropped, valid ones kept");

        // sections come from metadata
        T.ok("AL".equals(byId(a, "News24.al").section()) && "XK".equals(byId(a, "RTK3.xk").section()), "sections from country metadata");

        // search
        T.ok(PtvSearch.matches("rtk", byId(a, "RTK3.xk")) && PtvSearch.matches("RTK 3", byId(a, "RTK3.xk")), "name search");
        T.ok(PtvSearch.matches("news24", byId(a, "News24.al")), "squashed word finds spaced name");
        T.ok(PtvSearch.matches("kosova", byId(a, "RTK3.xk")) && !PtvSearch.matches("kosova", byId(a, "Syri.al")), "country words");
        T.ok(PtvSearch.matches("shqip", byId(a, "Syri.al")) && PtvSearch.matches("albanian news", byId(a, "Syri.al")), "language and category words");
        T.ok(PtvSearch.matches("MUSIC", byId(a, "AlbKanaleMusicTV.al")), "case-insensitive");
        T.ok(PtvSearch.matches("alb", byId(a, "AlbKanaleMusicTV.al")), "prefix of a name word");
        T.ok(!PtvSearch.matches("zzz", byId(a, "Syri.al")), "no match");
        T.ok(PtvSearch.matches("", byId(a, "Syri.al")), "empty query matches all");
        T.ok("e c".equals(PtvSearch.fold("ë ç")), "accents folded");
        PtvModel.Channel e = new PtvModel.Channel(); e.id = "E.al"; e.name = "Top Channel Ë"; e.country = "AL";
        T.ok(PtvSearch.matches("top channel e", e), "accented letters match plain ones");
        // search spans the whole catalogue, both countries
        int hits = 0;
        for (PtvModel.Channel ch : a) if (PtvSearch.matches("syri", ch)) hits++;
        T.ok(hits == 2, "search finds channels in both Albania and Kosovo sections");

        // a large catalogue is searched on every keystroke: 20,000 channels must answer well inside a frame budget
        List<PtvModel.Channel> big = new ArrayList<>();
        for (int i = 0; i < 20000; i++) {
            PtvModel.Channel bc = new PtvModel.Channel(); bc.id = "C" + i + ".xx"; bc.name = "Channel " + i + " Televizion"; bc.country = i % 2 == 0 ? "AL" : "XK"; big.add(bc);
        }
        for (PtvModel.Channel bc : big) PtvSearch.matches("warm", bc);       // builds each channel's index once
        long t0 = System.nanoTime();
        int found = 0;
        String[] qw = PtvSearch.prepare("channel 19999");
        for (PtvModel.Channel bc : big) if (PtvSearch.matches(qw, bc)) found++;
        long ms = (System.nanoTime() - t0) / 1_000_000;
        T.ok(found == 1, "large catalogue: exactly one match for an exact number");
        T.ok(ms < 150, "large catalogue: a query over 20,000 channels takes " + ms + " ms (< 150)");

        // usage
        PtvUsage u = new PtvUsage();
        T.ok(!u.record("Syri.al", 5_000, 100), "a 5 s accidental open does not count");
        T.ok(!u.hasHistory(), "and leaves no history");
        T.ok(u.record("Syri.al", 60_000, 1_000), "a minute counts");
        T.ok(u.record("RTK3.xk", 30_000, 2_000), "30 s counts");
        T.ok(u.record("Syri.al", 600_000, 500), "an earlier, longer session counts");
        T.ok(u.get("Syri.al").sessions == 2 && u.get("Syri.al").ms == 660_000 && u.get("Syri.al").last == 1_000, "totals and last time");
        PtvUsage r = PtvUsage.parse(u.serialize());
        T.ok(r.get("Syri.al").ms == 660_000 && r.get("RTK3.xk").last == 2_000, "persists and restores");
        T.ok(PtvUsage.parse("garbage\n\t\t\t\nid\tx\ty\tz\nA\t1\t30000\t5\n").get("A").ms == 30_000, "malformed lines are skipped");

        // sorting
        Set<String> fav = new HashSet<>(Arrays.asList("News24.al"));
        List<PtvModel.Channel> rec = ChannelSort.order(a, ChannelSort.RECENT, u, fav);
        T.ok(rec.get(0).id.equals("RTK3.xk") && rec.get(1).id.equals("Syri.al"), "recent: most recent session first");
        List<PtvModel.Channel> most = ChannelSort.order(a, ChannelSort.MOST, u, fav);
        T.ok(most.get(0).id.equals("Syri.al") && most.get(1).id.equals("RTK3.xk"), "most watched: total time");
        List<PtvModel.Channel> az = ChannelSort.order(a, ChannelSort.AZ, u, fav);
        T.ok(az.get(0).id.equals("AlbKanaleMusicTV.al") && az.get(az.size() - 1).playable() == false, "A-Z, unavailable last");
        List<PtvModel.Channel> fv = ChannelSort.order(a, ChannelSort.FAVORITES, u, fav);
        T.ok(fv.get(0).id.equals("News24.al"), "favorites first");
        List<PtvModel.Channel> none = ChannelSort.order(a, ChannelSort.RECENT, new PtvUsage(), fav);
        T.ok(none.get(0).id.equals("AlbKanaleMusicTV.al"), "no history: alphabetical, not a pretend ranking");
    }
}
