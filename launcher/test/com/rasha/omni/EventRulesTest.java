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
        T.ok(EventRules.audio("JBL Bar", EventRules.A_BT_A2DP, true, true) == null, "devices already there at start are not news");
        String[] bt = EventRules.audio("JBL Bar", EventRules.A_BT_A2DP, true, false);
        T.ok(bt != null && "JBL Bar connected".equals(bt[0]) && bt[1].contains("Bluetooth"), "a Bluetooth speaker connecting");
        T.ok(EventRules.audio("", EventRules.A_HDMI_ARC, true, false)[0].equals("HDMI ARC sound connected"), "a nameless soundbar on ARC");
        T.ok(EventRules.audio("JBL Bar", EventRules.A_BT_A2DP, false, false)[1].contains("TV speakers"), "disconnecting says where sound went");
        T.ok(EventRules.audio("Speaker", EventRules.A_SPEAKER, true, false) == null && EventRules.audio("HDMI", EventRules.A_HDMI, true, false) == null, "the TV's own speakers and HDMI out are never news");
        java.util.Set<String> known = new java.util.HashSet<>();
        T.ok(EventRules.found("Living Room", "_googlecast._tcp", known, true) == null && known.contains("Living Room"), "the first network scan only learns");
        String[] cc = EventRules.found("Bedroom TV", "_googlecast._tcp", known, false);
        T.ok(cc != null && cc[0].equals("Chromecast found: Bedroom TV"), "a new Chromecast is news once");
        T.ok(EventRules.found("Bedroom TV", "_googlecast._tcp", known, false) == null && EventRules.found(null, "x", known, false) == null, "and only once");
        T.ok(EventRules.casting("Lo-fi beats", "YouTube")[1].equals("Lo-fi beats · YouTube") && EventRules.casting(null, null)[1].length() > 0, "casting says what, when it knows");
        T.ok(EventRules.peripheral("DualSense Wireless Controller", false, false) != null && EventRules.peripheral("Smart Remote", false, false) == null && EventRules.peripheral("RC", false, false) == null, "controllers yes, remotes no");
        T.ok(EventRules.peripheral("Logi K380", false, true)[1].equals("Keyboard ready") && EventRules.peripheral("Phone", false, false) == null && EventRules.peripheral(null, true, false) == null, "keyboards yes, other devices no");
        T.ok(ControlRows.soundOutput(new int[]{EventRules.A_SPEAKER}, new String[]{"x"}).equals("TV speakers"), "speakers only");
        T.ok(ControlRows.soundOutput(new int[]{EventRules.A_SPEAKER, EventRules.A_HDMI_ARC, EventRules.A_BT_A2DP}, new String[]{"", "Bar", "JBL"}).equals("JBL (Bluetooth)"), "Bluetooth wins, with its name");
        T.ok(ControlRows.soundOutput(new int[]{EventRules.A_HDMI_ARC}, new String[]{""}).equals("HDMI ARC"), "nameless ARC");
    }
}
