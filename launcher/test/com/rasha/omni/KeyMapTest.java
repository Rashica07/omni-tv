package com.rasha.omni;

final class KeyMapTest {
    static void run() {
        KeyMap d = new KeyMap(null);
        T.ok("control".equals(d.get(165)) && "control".equals(d.get(10358)) && "assistant".equals(d.get(84)), "defaults: INFO opens the Control Center, the mic opens the assistant");
        T.ok(KeyMap.keyOf(0, 358) == 10358 && KeyMap.keyOf(165, 358) == 165, "scan-code-only buttons get their own number");
        KeyMap m = new KeyMap("183=cinema;19=control;oops;184=bogus;185=app:com.netflix.ninja");
        T.ok("cinema".equals(m.get(183)) && m.get(19) == null && m.get(184) == null && "app:com.netflix.ninja".equals(m.get(185)), "stored map: D-pad and unknown actions are dropped");
        T.ok(!m.set(23, "control") && !m.set(4, "home") && !m.set(26, "tv") && !m.set(24, "tv"), "OK, Back, power and volume can never be taken");
        T.ok(m.set(186, "weather") && "weather".equals(m.get(186)) && m.set(186, "none") && m.get(186) == null, "assign and clear");
        T.ok(!m.set(187, "rm -rf"), "unknown actions are refused");
        KeyMap back = new KeyMap(m.serialize());
        T.ok(back.all().equals(m.all()), "serialize round-trips");
        T.ok(KeyMap.buttonName(183, "KEYCODE_PROG_RED").equals("Red") && KeyMap.buttonName(10358, null).equals("INFO") && KeyMap.buttonName(165, "KEYCODE_INFO").equals("Info") && KeyMap.buttonName(84, "KEYCODE_SEARCH").equals("Mic / Search"), "button names");
        T.ok(KeyMap.actionName("cinema").equals("OMNI Cinema") && KeyMap.actionName("app:YouTube").equals("Open YouTube") && KeyMap.actionName(null).equals("Nothing"), "action names");
    }
}
