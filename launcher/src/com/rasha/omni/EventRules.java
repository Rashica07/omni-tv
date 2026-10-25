package com.rasha.omni;

/**
 * What is worth telling the user, as pure rules over real state changes (no polling, no guessing). Each returns {heading, detail} or null for "say nothing".
 * Input states use the platform's numbers (TvInputManager: 0 connected, 1 connected standby, 2 disconnected) so this stays free of Android types and testable.
 */
final class EventRules {
    private EventRules() { }

    static final int CONNECTED = 0, STANDBY = 1, DISCONNECTED = 2;

    /** Only a change between online and offline is news; the first reading is not. */
    static String[] network(Boolean wasOnline, boolean isOnline, String describe) {
        if (wasOnline == null || wasOnline == isOnline) return null;
        return isOnline ? new String[]{"Network is back", describe} : new String[]{"Network lost", "OMNI cannot reach the internet. Local things still work."};
    }

    /** A device with a picture appearing or disappearing. Standby and unplugged are the same to the user, so moving between them says nothing. */
    static String[] input(String name, String port, Integer was, int now) {
        if (was == null || was == now) return null;
        boolean wasOn = was == CONNECTED, isOn = now == CONNECTED;
        if (!wasOn && isOn) return new String[]{name + " is on", port + " now has a picture"};
        if (wasOn && !isOn) return new String[]{name + " switched off", port + " has no picture"};
        return null;
    }

    // ---- sound devices. Types are AudioDeviceInfo's numbers, kept here as constants so the rules stay free of Android.
    static final int A_SPEAKER = 2, A_WIRED_HEADSET = 3, A_WIRED_HEADPHONES = 4, A_BT_A2DP = 8, A_HDMI = 9, A_HDMI_ARC = 10, A_USB_DEVICE = 11, A_USB_HEADSET = 22, A_HDMI_EARC = 29;

    /** How a sound output is called on screen. */
    static String audioKind(int type) {
        switch (type) {
            case A_BT_A2DP: return "Bluetooth";
            case A_HDMI_ARC: return "HDMI ARC";
            case A_HDMI_EARC: return "HDMI eARC";
            case A_USB_DEVICE: case A_USB_HEADSET: return "USB";
            case A_WIRED_HEADSET: case A_WIRED_HEADPHONES: return "Headphones";
            default: return null;                                   // speakers, HDMI out, internal routes: never news
        }
    }

    /** A speaker, soundbar or headphones appearing or going away. The first batch (what was already there when OMNI started) is never news. */
    static String[] audio(String name, int type, boolean added, boolean firstBatch) {
        String kind = audioKind(type);
        if (firstBatch || kind == null) return null;
        String n = name == null || name.trim().isEmpty() || name.trim().equalsIgnoreCase(kind) ? kind + " sound" : name.trim();
        return added ? new String[]{n + " connected", "Sound now plays there (" + kind + ")"} : new String[]{n + " disconnected", "Sound is back on the TV speakers"};
    }

    /** A Chromecast or OMNI device seen on the network for the first time ever. Known names say nothing. */
    static String[] found(String name, String service, java.util.Set<String> known, boolean baseline) {
        if (name == null || known.contains(name)) return null;
        known.add(name);
        if (baseline) return null;
        boolean cast = service != null && service.contains("googlecast");
        return new String[]{(cast ? "Chromecast found: " : "Device found: ") + name, cast ? "You can cast from it, or to it, on this network" : service};
    }

    /** Something started playing on this TV from a phone (Chromecast built-in). */
    static String[] casting(String title, String app) {
        String t = title == null || title.trim().isEmpty() ? null : title.trim();
        return new String[]{"Casting to this TV", t == null ? (app == null ? "From a phone or computer" : app) : t + (app == null ? "" : " · " + app)};
    }

    /** A Bluetooth game controller or keyboard connecting. Remotes reconnect all the time and are never news. */
    static String[] peripheral(String name, boolean gamepad, boolean keyboard) {
        if (name == null || name.trim().isEmpty()) return null;
        String l = name.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("remote") || l.contains("rc") && l.length() <= 6) return null;
        if (gamepad || l.contains("controller") || l.contains("dualsense") || l.contains("dualshock") || l.contains("xbox") || l.contains("gamepad")) return new String[]{name.trim() + " connected", "Game controller ready"};
        if (keyboard) return new String[]{name.trim() + " connected", "Keyboard ready"};
        return null;
    }
}
