package com.rasha.omni;

import java.util.HashMap;
import java.util.Map;

/**
 * Local viewing statistics. Only real playback counts: a session shorter than {@link #MIN_SESSION_MS} (an accidental OK, a quick zap past a
 * channel) is ignored entirely, so it cannot move "recently watched" or distort "most watched". Highlighting or previewing never records anything.
 * These numbers describe this TV only and are never presented as popularity.
 */
final class PtvUsage {
    static final long MIN_SESSION_MS = 20_000L;

    static final class Stat {
        int sessions;
        long ms;
        long last;
    }

    private final Map<String, Stat> stats = new HashMap<>();

    /** Records a finished stretch of playback. Returns true if it counted. */
    boolean record(String id, long watchedMs, long now) {
        if (id == null || watchedMs < MIN_SESSION_MS) return false;
        Stat s = stats.get(id);
        if (s == null) { s = new Stat(); stats.put(id, s); }
        s.sessions++;
        s.ms += watchedMs;
        s.last = Math.max(s.last, now);
        return true;
    }

    Stat get(String id) {
        Stat s = stats.get(id);
        return s != null ? s : EMPTY;
    }

    boolean hasHistory() { return !stats.isEmpty(); }

    private static final Stat EMPTY = new Stat();

    /** One line per channel: id TAB sessions TAB ms TAB last. Ids never contain tabs or newlines (see PtvModel.fromRows). */
    String serialize() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, Stat> e : stats.entrySet()) {
            Stat s = e.getValue();
            b.append(e.getKey()).append('\t').append(s.sessions).append('\t').append(s.ms).append('\t').append(s.last).append('\n');
        }
        return b.toString();
    }

    static PtvUsage parse(String text) {
        PtvUsage u = new PtvUsage();
        if (text == null) return u;
        for (String line : text.split("\n")) {
            String[] f = line.split("\t");
            if (f.length != 4 || f[0].isEmpty()) continue;
            try {
                Stat s = new Stat();
                s.sessions = Integer.parseInt(f[1]);
                s.ms = Long.parseLong(f[2]);
                s.last = Long.parseLong(f[3]);
                if (s.sessions < 0 || s.ms < 0) continue;
                u.stats.put(f[0], s);
            } catch (NumberFormatException ignored) { }
        }
        return u;
    }
}
