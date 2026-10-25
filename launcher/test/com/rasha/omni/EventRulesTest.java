package com.rasha.omni;

final class EventRulesTest {
    static void run() {
        T.ok(EventRules.network(null, true, "Ethernet") == null && EventRules.network(null, false, "x") == null, "the first network reading is never news");
        T.ok(EventRules.network(true, true, "Ethernet") == null && EventRules.network(false, false, "x") == null, "no change, no news");
        T.ok("Network lost".equals(EventRules.network(true, false, "x")[0]) && "Network is back".equals(EventRules.network(false, true, "Wi-Fi")[0]) && "Wi-Fi".equals(EventRules.network(false, true, "Wi-Fi")[1]), "online/offline transitions");
        T.ok(EventRules.input("PlayStation 5", "HDMI 3", null, 0) == null, "the first input reading is never news");
        String[] on = EventRules.input("PlayStation 5", "HDMI 3", EventRules.STANDBY, EventRules.CONNECTED);
        T.ok(on != null && "PlayStation 5 is on".equals(on[0]) && "HDMI 3 now has a picture".equals(on[1]), "standby -> signal says the device is on");
        T.ok(EventRules.input("PC", "HDMI 1", EventRules.DISCONNECTED, EventRules.CONNECTED) != null, "unplugged -> signal also counts");
        String[] off = EventRules.input("PlayStation 5", "HDMI 3", EventRules.CONNECTED, EventRules.STANDBY);
        T.ok(off != null && "PlayStation 5 switched off".equals(off[0]), "signal -> standby says switched off");
        T.ok(EventRules.input("PC", "HDMI 1", EventRules.CONNECTED, EventRules.DISCONNECTED) != null, "signal -> unplugged says it too");
        T.ok(EventRules.input("PC", "HDMI 1", EventRules.STANDBY, EventRules.DISCONNECTED) == null && EventRules.input("PC", "HDMI 1", EventRules.DISCONNECTED, EventRules.STANDBY) == null, "standby <-> unplugged is not news");
        T.ok(EventRules.input("PC", "HDMI 1", 0, 0) == null, "no change, no news");
    }
}
