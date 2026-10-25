package com.rasha.omni;

import java.util.List;

/** Pure list helpers for the user's ordering (unit tested in test/). */
final class ListOps {
    private ListOps() { }

    /** Moves id by delta places, clamped to the list; no-op when absent. Returns the new index or -1. */
    static int move(List<String> l, String id, int delta) {
        int i = l.indexOf(id);
        if (i < 0) return -1;
        int j = Math.max(0, Math.min(l.size() - 1, i + delta));
        l.remove(i);
        l.add(j, id);
        return j;
    }
}
