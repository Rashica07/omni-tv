package com.rasha.omni;

import java.util.Locale;

/** Device kinds an input can be, and the one honest way to infer a kind: from a name the TV itself supplied. Pure Java (unit tested). */
final class DeviceTypes {
    static final int T_UNKNOWN = 0, T_CONSOLE = 1, T_PC = 2, T_STREAMER = 3, T_BOX = 4, T_AUDIO = 5, T_OTHER = 6, T_TUNER = 7, T_AV = 8;
    static final String[] TYPE_NAMES = {"Not set", "Game console", "Computer", "Streaming device", "Set-top box", "Sound system", "Other device", "TV tuner", "AV input"};

    /** Guess a device type from a name the TV gave us (a CEC name or a custom label). Returns T_UNKNOWN when nothing matches. */
    static int guessType(String name) {
        if (name == null) return T_UNKNOWN;
        String n = name.toLowerCase(Locale.ROOT);
        String[] consoles = {"playstation", "ps5", "ps4", "ps3", "xbox", "nintendo", "switch", "console", "game"};
        String[] streamers = {"apple tv", "chromecast", "fire tv", "firetv", "roku", "shield", "mi box", "mi tv stick", "google tv streamer", "tv stick", "streamer"};
        String[] boxes = {"sky", "set-top", "set top", "decoder", "tivusat", "vodafone tv", "tim box", "recorder", "dvd", "blu-ray", "bluray"};
        String[] audio = {"soundbar", "sound bar", "receiver", "avr", "denon", "yamaha", "marantz", "sonos", "home theater", "hdmi arc", "audio"};
        String[] pcs = {"pc", "laptop", "computer", "mac", "desktop", "raspberry"};
        for (String s : consoles) if (n.contains(s)) return T_CONSOLE;
        for (String s : streamers) if (n.contains(s)) return T_STREAMER;
        for (String s : boxes) if (n.contains(s)) return T_BOX;
        for (String s : audio) if (n.contains(s)) return T_AUDIO;
        for (String s : pcs) if (n.matches(".*\\b" + s + "\\b.*")) return T_PC;
        return T_UNKNOWN;
    }

    private DeviceTypes() { }
}
