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
}
