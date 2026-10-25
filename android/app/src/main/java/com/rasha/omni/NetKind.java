package com.rasha.omni;

/** What the status bar may claim about the connection. "Connected" and "has internet" are different facts and are never merged. */
final class NetKind {
    private NetKind() { }

    static final int NONE = 0, WIFI = 1, ETHERNET = 2, OTHER = 3;

    static int transport(boolean wifi, boolean ethernet, boolean other) {
        if (ethernet) return ETHERNET;          // wired wins when both exist
        if (wifi) return WIFI;
        return other ? OTHER : NONE;
    }

    /** Text for the sheet. internet is the platform's own validation result (NET_CAPABILITY_VALIDATED), not a guess. */
    static String describe(int transport, boolean validated) {
        if (transport == NONE) return "Not connected";
        String how = transport == ETHERNET ? "Ethernet" : transport == WIFI ? "Wi-Fi" : "Connected";
        return validated ? how + ", internet reachable" : how + ", but the system could not confirm internet access";
    }
}
