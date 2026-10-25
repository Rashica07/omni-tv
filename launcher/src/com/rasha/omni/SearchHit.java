package com.rasha.omni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One thing the search can find: an app, a TV channel, a film, an input or a setting. Matching ignores case, accents and punctuation and is word-prefix based
 * (see {@link PtvSearch}); results are ranked so a title that starts with what you typed comes first. Pure logic (no Android types), so it is tested without a TV.
 */
final class SearchHit {
    static final int APP = 0, CHANNEL = 1, FILM = 2, INPUT = 3, SETTING = 4;
    private static final int[] KIND_WEIGHT = {4, 3, 3, 2, 1};            // on equal rank, apps come before channels and films, then inputs, then settings

    final int kind;
    final String id, title, sub;
    /** What the overlay needs to open or draw it (a Channel, an app's artwork, an Inputs.Entry...). Not used by the matching. */
    Object payload, thumb;
    private final String foldedTitle;
    private final String[] titleWords, words;
    private final String flat;

    SearchHit(int kind, String id, String title, String sub, String extraText) {
        this.kind = kind; this.id = id; this.title = title; this.sub = sub == null ? "" : sub;
        foldedTitle = PtvSearch.fold(title);
        titleWords = foldedTitle.isEmpty() ? new String[0] : foldedTitle.split(" ");
        String hay = PtvSearch.fold(title + " " + (extraText == null ? "" : extraText));
        words = hay.isEmpty() ? new String[0] : hay.split(" ");
        flat = hay.replace(" ", "");
    }

    /** -1 when it does not match; otherwise higher is better. */
    int score(String[] q) {
        if (q.length == 0) return -1;
        for (String w : q) {
            boolean ok = false;
            for (String hw : words) if (hw.startsWith(w)) { ok = true; break; }
            if (!ok && w.length() >= 4 && flat.contains(w)) ok = true;
            if (!ok) return -1;
        }
        String joined = String.join(" ", q);
        int s;
        if (foldedTitle.startsWith(joined)) s = 300;                       // the title starts with exactly what was typed
        else {
            boolean inTitle = true;
            for (String w : q) { boolean f = false; for (String tw : titleWords) if (tw.startsWith(w)) { f = true; break; } if (!f) { inTitle = false; break; } }
            s = inTitle ? 200 : 100;                                       // every word starts a word of the title; or only the other text matched
        }
        return s + KIND_WEIGHT[kind];
    }

    /** Hits for [query], best first, at most [max]. An empty query finds nothing (the overlay says how much there is to search instead). */
    static List<SearchHit> search(String query, List<SearchHit> all, int max) {
        final String[] q = PtvSearch.prepare(query);
        List<SearchHit> out = new ArrayList<>();
        final java.util.Map<SearchHit, Integer> sc = new java.util.HashMap<>();
        for (SearchHit h : all) { int s = h.score(q); if (s >= 0) { sc.put(h, s); out.add(h); } }
        Collections.sort(out, (a, b) -> { int d = sc.get(b) - sc.get(a); return d != 0 ? d : a.foldedTitle.compareTo(b.foldedTitle); });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    static String kindName(int kind) { return kind == APP ? "app" : kind == CHANNEL ? "channel" : kind == FILM ? "film" : kind == INPUT ? "input" : "setting"; }
}
