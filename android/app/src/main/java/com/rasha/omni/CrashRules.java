package com.rasha.omni;

/** Pure rules for the crash guard: which recent crashes count, and when OMNI starts in safe mode. */
final class CrashRules {
    private CrashRules() { }

    static final long WINDOW_MS = 120_000;
    static final int LIMIT = 2;
    static final int KEEP = 6;

    /** Appends a crash time to a comma-separated list, keeping the newest [KEEP]. Garbage in the stored text is ignored. */
    static String add(String csv, long now) {
        java.util.List<Long> l = parse(csv);
        l.add(now);
        StringBuilder sb = new StringBuilder();
        for (int i = Math.max(0, l.size() - KEEP); i < l.size(); i++) sb.append(sb.length() == 0 ? "" : ",").append(l.get(i));
        return sb.toString();
    }

    /** Safe mode: at least [LIMIT] crashes inside the last [WINDOW_MS]. A clock that went backwards never counts a crash from the "future". */
    static boolean safeMode(String csv, long now) {
        int n = 0;
        for (long t : parse(csv)) if (t <= now && now - t <= WINDOW_MS) n++;
        return n >= LIMIT;
    }

    private static java.util.List<Long> parse(String csv) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        if (csv == null) return out;
        for (String p : csv.split(",")) {
            try { if (!p.trim().isEmpty()) out.add(Long.parseLong(p.trim())); } catch (NumberFormatException ignored) { }
        }
        return out;
    }
}
