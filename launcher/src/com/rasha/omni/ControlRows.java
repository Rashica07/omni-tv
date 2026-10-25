package com.rasha.omni;

import java.util.List;
import java.util.Locale;

/** The words the Control Center shows, as pure functions (so they are tested without a TV). */
final class ControlRows {
    private ControlRows() { }

    /** "12.3 GB free of 24 GB", honest about unknown sizes. */
    static String storage(long free, long total) {
        if (total <= 0 || free < 0) return "Unknown";
        return size(free) + " free of " + size(total);
    }

    static String size(long bytes) {
        double gb = bytes / 1073741824.0;
        if (gb >= 100) return String.format(Locale.US, "%.0f GB", gb);
        if (gb >= 1) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%.0f MB", bytes / 1048576.0);
    }

    /** The inputs that currently have a picture, by name. */
    static String inputs(List<String> activeNames) {
        if (activeNames == null) return "Checking…";
        if (activeNames.isEmpty()) return "None with a signal";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < activeNames.size() && i < 3; i++) { if (i > 0) b.append(", "); b.append(activeNames.get(i)); }
        if (activeNames.size() > 3) b.append(" +").append(activeNames.size() - 3);
        return b.toString();
    }

    /** The server line: unset, unchecked, or the checked result. Never claims reachable without a check. */
    static String server(boolean configured, boolean checked, boolean reachable, String detail) {
        if (!configured) return "Not set up";
        if (!checked) return "Not checked yet";
        if (reachable) return detail == null || detail.isEmpty() ? "Reachable" : detail;
        return "Not reachable";
    }

    /** One Left/Right step of the volume slider (5% of the range, at least 1), clamped. */
    static int volumeStep(int cur, int max, int dir) {
        if (max <= 0) return 0;
        int step = Math.max(1, Math.round(max / 20f));
        return Math.max(0, Math.min(max, cur + dir * step));
    }

    static int volumePercent(int cur, int max) { return max <= 0 ? 0 : Math.round(100f * Math.max(0, Math.min(max, cur)) / max); }

    /** The text of one input row: its name and what the TV says about it. */
    static String inputState(boolean signal, boolean standby) { return signal ? "Signal" : standby ? "Standby" : "Nothing connected"; }

    /** The Control Center's "Sound" row: where sound plays now. Preference follows Android's own routing: Bluetooth, then HDMI ARC/eARC, USB, headphones, else the TV. types/names are parallel arrays. */
    static String soundOutput(int[] types, String[] names) {
        int[] order = {EventRules.A_BT_A2DP, EventRules.A_HDMI_EARC, EventRules.A_HDMI_ARC, EventRules.A_USB_HEADSET, EventRules.A_USB_DEVICE, EventRules.A_WIRED_HEADPHONES, EventRules.A_WIRED_HEADSET};
        for (int want : order) for (int i = 0; i < types.length; i++) if (types[i] == want) {
            String kind = EventRules.audioKind(want), n = names[i] == null ? "" : names[i].trim();
            return n.isEmpty() || n.equalsIgnoreCase(kind) ? kind : n + " (" + kind + ")";
        }
        return "TV speakers";
    }
}
