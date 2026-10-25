package com.rasha.omni;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which remote button does what inside OMNI. Pure (tested on the JVM), stored as "165=control;84=assistant".
 * A button is its key code, or 10000 + its scan code when the remote sends no key code (this TV's INFO arrives as scan 358).
 * Navigation, OK, Back, Home, volume and power can never be remapped: those must always work.
 */
final class KeyMap {
    static final String[] ACTIONS = {"control", "assistant", "search", "home", "tv", "cinema", "weather", "settings"};
    static final String[] ACTION_NAMES = {"Control Center", "Assistant", "Search", "Home", "Live TV", "OMNI Cinema", "Weather", "Settings"};
    static final String DEFAULTS = "165=control;10358=control;84=assistant;231=assistant;219=assistant";

    private final LinkedHashMap<Integer, String> map = new LinkedHashMap<>();

    KeyMap(String stored) { parse(stored == null || stored.isEmpty() ? DEFAULTS : stored); }

    static int keyOf(int keyCode, int scanCode) { return keyCode == 0 ? 10000 + scanCode : keyCode; }

    static boolean reserved(int key) {
        switch (key) {
            case 3: case 4: case 19: case 20: case 21: case 22: case 23: case 66: case 160: case 24: case 25: case 164: case 26: case 0: case 10000: return true;   // Home, Back, D-pad, OK, Enter, volume, mute, power, nothing
            default: return false;
        }
    }

    static boolean known(String action) { for (String a : ACTIONS) if (a.equals(action)) return true; return action != null && action.startsWith("app:") && action.length() > 4; }

    static String actionName(String action) {
        for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(action)) return ACTION_NAMES[i];
        return action != null && action.startsWith("app:") ? "Open " + action.substring(4) : "Nothing";
    }

    private void parse(String s) {
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            try {
                int k = Integer.parseInt(part.substring(0, eq).trim());
                String a = part.substring(eq + 1).trim();
                if (!reserved(k) && known(a)) map.put(k, a);
            } catch (NumberFormatException ignored) { }
        }
    }

    String get(int key) { return map.get(key); }

    /** Assigns a button; null or "none" clears it. Returns false for buttons that can never be remapped. */
    boolean set(int key, String action) {
        if (reserved(key)) return false;
        if (action == null || "none".equals(action)) { map.remove(key); return true; }
        if (!known(action)) return false;
        map.put(key, action);
        return true;
    }

    Map<Integer, String> all() { return new LinkedHashMap<>(map); }

    String serialize() {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<Integer, String> e : map.entrySet()) { if (b.length() > 0) b.append(';'); b.append(e.getKey()).append('=').append(e.getValue()); }
        return b.toString();
    }

    /** How a button is called on screen ("INFO", "Red", "Key 183"). Names come from Android's own key names. */
    static String buttonName(int key, String androidName) {
        if (key == 10358) return "INFO";
        if (key > 10000) return "Button " + (key - 10000);
        if (androidName == null || androidName.isEmpty()) return "Key " + key;
        String n = androidName.replace("KEYCODE_", "");
        switch (n) {
            case "PROG_RED": return "Red"; case "PROG_GREEN": return "Green"; case "PROG_YELLOW": return "Yellow"; case "PROG_BLUE": return "Blue";
            case "VOICE_ASSIST": case "ASSIST": return "Assistant button"; case "SEARCH": return "Mic / Search";
            default: return n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        }
    }
}
