package com.rasha.omni;

import java.util.Arrays;

final class AntennaRulesTest {
    static void run() {
        T.ok("IT".equals(AntennaRules.country("Rai 1")) && "IT".equals(AntennaRules.country("RAI 4K")) && "IT".equals(AntennaRules.country("Canale 5 HD")) && "IT".equals(AntennaRules.country("La7")), "Italian channels");
        T.ok("CH".equals(AntennaRules.country("SRF 1 HD")) && "CH".equals(AntennaRules.country("RTS Un")) && "CH".equals(AntennaRules.country("RSI LA 1")) && "CH".equals(AntennaRules.country("3+")), "Swiss channels");
        T.ok("AL".equals(AntennaRules.country("Top Channel")) && "AL".equals(AntennaRules.country("RTSH 1")) && "AL".equals(AntennaRules.country("Vizion Plus")) && "AL".equals(AntennaRules.country("Klan")), "Albanian channels");
        T.ok("XK".equals(AntennaRules.country("RTK 1")) && "XK".equals(AntennaRules.country("Klan Kosova")) && "XK".equals(AntennaRules.country("Kohavision")) && "XK".equals(AntennaRules.country("TV Dukagjini")), "Kosovo channels, Kosovo's Klan is not Albania's");
        T.ok("".equals(AntennaRules.country("Discovery Channel")) && "".equals(AntennaRules.country("")) && "".equals(AntennaRules.country(null)), "unknown names stay unknown");
        T.ok("".equals(AntennaRules.country("Rainbow Kids")) && "".equals(AntennaRules.country("Railway TV")), "a name that merely starts with the letters is not matched");
        T.ok("IT".equals(AntennaRules.country("  rai   news 24 ")) && "AL".equals(AntennaRules.country("Tëst")) == false, "spacing and accents are folded");
        T.ok("IT".equals(AntennaRules.dominant(Arrays.asList("Rai 1", "Rai 2", "Canale 5", "SRF 1", "Foo"))), "the most common country wins");
        T.ok("".equals(AntennaRules.dominant(Arrays.asList("Rai 1", "SRF 1"))) && "".equals(AntennaRules.dominant(Arrays.asList("Foo", "Bar"))), "a tie or nothing recognised gives no answer");
    }
}
