package com.rasha.omni;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure channel model (no Android): one {@link Channel} per real television service, identified by the source's own stable id (for example
 * "RTK3.xk"). The id, name, logo/card, streams, country, categories and languages all live on the one object, so nothing can drift apart
 * the way a name-keyed or index-keyed pipeline can. Rows are untrusted data: bad rows are skipped, never repaired by guessing.
 */
final class PtvModel {
    private PtvModel() { }

    static final class Stream {
        String url;
        String probe = "unchecked";
        String tier = "C";
        boolean needsReferrer;
        boolean enabled;

        boolean playable() { return enabled && !needsReferrer && "plays".equals(probe) && "B".equals(tier); }
    }

    static final class Channel {
        String id;
        String name;
        String country = "";
        String website = "";
        final List<String> alt = new ArrayList<>();
        final List<String> languages = new ArrayList<>();
        final List<String> categories = new ArrayList<>();
        final List<Stream> streams = new ArrayList<>();
        /** Where it comes from: an internet stream ("iptv") or the TV's own tuner ("tuner", played through the system input service). */
        String source = "iptv";
        String tunerInput = "";
        long tunerId;
        String number = "";

        boolean isTuner() { return "tuner".equals(source); }

        /** An internet URL exists for this channel (previews use it; tuner channels have none). */
        boolean hasStream() { return !urls().isEmpty(); }

        /** Playable URLs only, primary first. A failed or refused stream is never replaced by another channel's. */
        List<String> urls() {
            List<String> u = new ArrayList<>();
            for (Stream s : streams) if (s.playable()) u.add(s.url);
            return u;
        }

        boolean playable() { return isTuner() ? tunerId > 0 && !tunerInput.isEmpty() : !urls().isEmpty(); }

        /** Why nothing is offered, in words for the screen. */
        String reason() {
            boolean blocked = false, referrer = false, third = false;
            for (Stream s : streams) {
                if (s.needsReferrer) referrer = true;
                else if (s.probe.startsWith("blocked")) blocked = true;
                else if ("plays".equals(s.probe) && !"B".equals(s.tier)) third = true;
            }
            if (referrer) return "Its stream is only offered to another website's player, so OMNI does not use it. Open the broadcaster's own site or app.";
            if (blocked) return "The broadcaster's server refuses this app (access protection). Use its own app or website.";
            if (third) return "Only a third-party copy was found, which OMNI does not enable by default.";
            return "No working stream was found from here.";
        }

        /** Folded searchable text, built once per channel (a large catalogue is searched on every keystroke). */
        String hay;
        String[] hayWords;
        String hayFlat;

        String section() { return isTuner() ? "TUNER" : "AL".equals(country) ? "AL" : "XK".equals(country) ? "XK" : "OTHER"; }

        String category() { return categories.isEmpty() ? "" : categories.get(0); }
    }

    private static String s(Object o) { return o == null ? "" : String.valueOf(o); }

    @SuppressWarnings("unchecked")
    private static void strings(Object o, List<String> out) {
        if (o instanceof List) for (Object x : (List<Object>) o) { String v = PtvRules.cleanName(s(x)); if (!v.isEmpty()) out.add(v); }
    }

    /** Builds channels from schema-2 rows. Duplicate ids are merged (streams appended), never duplicated as two channels. */
    @SuppressWarnings("unchecked")
    static List<Channel> fromRows(List<Map<String, Object>> rows) {
        Map<String, Channel> by = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            String id = s(r.get("id")).trim();
            String name = PtvRules.cleanName(s(r.get("name")));
            if (id.isEmpty() || id.length() > 80 || name.isEmpty()) continue;
            Channel c = by.get(id);
            if (c == null) {
                c = new Channel();
                c.id = id;
                c.name = name;
                String cc = s(r.get("country"));
                c.country = cc.length() <= 3 ? cc : "";
                String w = s(r.get("website"));
                c.website = PtvRules.safeUrl(w) ? w : "";
                strings(r.get("alt"), c.alt);
                strings(r.get("languages"), c.languages);
                strings(r.get("categories"), c.categories);
                by.put(id, c);
            }
            Object ss = r.get("streams");
            if (!(ss instanceof List)) continue;
            for (Object o : (List<Object>) ss) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = (Map<String, Object>) o;
                String url = s(m.get("url"));
                if (!PtvRules.safeUrl(url)) continue;
                boolean dup = false;
                for (Stream e : c.streams) if (e.url.equals(url)) dup = true;
                if (dup) continue;
                Stream st = new Stream();
                st.url = url;
                st.probe = s(m.get("probe")).isEmpty() ? "unchecked" : s(m.get("probe"));
                st.tier = s(m.get("tier")).isEmpty() ? "C" : s(m.get("tier")).substring(0, 1);
                st.needsReferrer = Boolean.TRUE.equals(m.get("needs_referrer"));
                st.enabled = Boolean.TRUE.equals(m.get("enabled"));
                c.streams.add(st);
            }
        }
        List<Channel> out = new ArrayList<>();
        for (Channel c : by.values()) if (!c.streams.isEmpty()) out.add(c);
        return out;
    }
}
