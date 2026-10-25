package com.rasha.omni;

import java.util.Locale;

/** Pure input rules for OMNIPTV. Everything that arrives from a playlist, a catalogue or the user is untrusted: it passes through here first. */
final class PtvRules {
    private PtvRules() { }

    static final int MAX_URL = 600;

    /** Only plain http(s) with a host, no whitespace or markup, bounded length. file:, javascript:, intent: and friends are refused. */
    static boolean safeUrl(String u) {
        if (u == null || u.length() == 0 || u.length() > MAX_URL) return false;
        String l = u.toLowerCase(Locale.ROOT);
        if (!(l.startsWith("http://") || l.startsWith("https://"))) return false;
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (c <= ' ' || c == '<' || c == '>' || c == '"' || c == '\'' || c == '\\') return false;
        }
        int s = l.indexOf("://") + 3;
        int e = s;
        while (e < l.length() && "/?#".indexOf(l.charAt(e)) < 0) e++;
        String authority = l.substring(s, e);
        if (authority.indexOf('@') >= 0) return false;             // no user:pass@ tricks
        String host = authority.startsWith("[") ? authority : authority.split(":")[0];
        return host.length() > 0;
    }

    /** Display names: single line, bounded, no control characters. */
    static String cleanName(String n) {
        if (n == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n.length() && b.length() < 60; i++) {
            char c = n.charAt(i);
            b.append(c < ' ' || c == 0x7f ? ' ' : c);
        }
        return b.toString().trim();
    }

    /**
     * Turns what a person types for the server address ("192.168.1.146", "192.168.1.146:8765", "http://host:8765/") into a base URL,
     * or null. The default port is 8765. HTTPS is accepted only when typed; typing a bare host means plain http on the home LAN.
     */
    static String serverBase(String in) {
        if (in == null) return null;
        String s = in.trim();
        if (s.length() == 0 || s.length() > 120) return null;
        if (!s.contains("://")) s = "http://" + s;
        if (!safeUrl(s)) return null;
        String rest = s.substring(s.indexOf("://") + 3);
        int slash = rest.indexOf('/');
        String auth = slash >= 0 ? rest.substring(0, slash) : rest;
        if (slash >= 0 && rest.substring(slash).replace("/", "").length() > 0) return null;   // a base address has no path
        String scheme = s.substring(0, s.indexOf("://"));
        boolean hasPort = auth.startsWith("[") ? auth.contains("]:") : auth.contains(":");
        return scheme.toLowerCase(Locale.ROOT) + "://" + auth + (hasPort ? "" : ":8765");
    }

    /** Group label for shelves. */
    static String group(String country) {
        if ("AL".equals(country)) return "Albania";
        if ("XK".equals(country)) return "Kosovo";
        return "Other";
    }

    /**
     * Only tier B (host consistent with the broadcaster) streams that were seen playing are enabled by default. Everything else needs the
     * user to turn it on, and a blocked or failing stream is never offered as playable.
     */
    static boolean offered(boolean enabled, String probe) { return enabled && "plays".equals(probe); }
}
