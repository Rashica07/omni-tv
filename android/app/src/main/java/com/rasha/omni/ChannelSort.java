package com.rasha.omni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Orderings for a channel section. Pure, so the rules are tested. Playable channels always come before unavailable ones. */
final class ChannelSort {
    private ChannelSort() { }

    static final int RECENT = 0, MOST = 1, AZ = 2, FAVORITES = 3;
    static final String[] LABELS = {"Recently watched", "Most watched", "A to Z", "Favorites"};

    static List<PtvModel.Channel> order(List<PtvModel.Channel> in, final int mode, final PtvUsage usage, final Set<String> favorites) {
        List<PtvModel.Channel> out = new ArrayList<>(in);
        final boolean history = usage.hasHistory();
        Collections.sort(out, new Comparator<PtvModel.Channel>() {
            @Override public int compare(PtvModel.Channel a, PtvModel.Channel b) {
                boolean pa = a.playable(), pb = b.playable();
                if (pa != pb) return pa ? -1 : 1;
                int m = mode;
                if (m == RECENT && !history) m = AZ;                       // no history yet: a calm alphabetical order, no pretence of ranking
                switch (m) {
                    case RECENT: {
                        int c = Long.compare(usage.get(b.id).last, usage.get(a.id).last);
                        if (c != 0) return c;
                        break;
                    }
                    case MOST: {
                        PtvUsage.Stat x = usage.get(a.id), y = usage.get(b.id);
                        int c = Long.compare(y.ms, x.ms);
                        if (c != 0) return c;
                        c = Integer.compare(y.sessions, x.sessions);
                        if (c != 0) return c;
                        break;
                    }
                    case FAVORITES: {
                        boolean fa = favorites.contains(a.id), fb = favorites.contains(b.id);
                        if (fa != fb) return fa ? -1 : 1;
                        break;
                    }
                    default: break;
                }
                int n = PtvSearch.fold(a.name).compareTo(PtvSearch.fold(b.name));
                return n != 0 ? n : a.id.compareTo(b.id);
            }
        });
        return out;
    }
}
