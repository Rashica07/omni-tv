package com.rasha.omni;

final class InputsTest {
    static void run() {
        T.ok(DeviceTypes.guessType("PlayStation 5") == DeviceTypes.T_CONSOLE, "PlayStation name");
        T.ok(DeviceTypes.guessType("Xbox Series X") == DeviceTypes.T_CONSOLE, "Xbox name");
        T.ok(DeviceTypes.guessType("Nintendo Switch") == DeviceTypes.T_CONSOLE, "Switch name");
        T.ok(DeviceTypes.guessType("Apple TV") == DeviceTypes.T_STREAMER, "Apple TV is a streaming device");
        T.ok(DeviceTypes.guessType("Chromecast") == DeviceTypes.T_STREAMER, "Chromecast");
        T.ok(DeviceTypes.guessType("Sky Q") == DeviceTypes.T_BOX, "Sky box");
        T.ok(DeviceTypes.guessType("Denon AVR-X") == DeviceTypes.T_AUDIO, "receiver");
        T.ok(DeviceTypes.guessType("Gaming PC") == DeviceTypes.T_PC, "PC as a whole word");
        // no name, no guess: a port label is never turned into a device
        T.ok(DeviceTypes.guessType(null) == DeviceTypes.T_UNKNOWN, "null name");
        T.ok(DeviceTypes.guessType("HDMI 2") == DeviceTypes.T_UNKNOWN, "a port label is not a device");
        T.ok(DeviceTypes.guessType("Playground") == DeviceTypes.T_UNKNOWN, "no accidental substring: 'pc' must be a whole word");
        T.ok(DeviceTypes.guessType("") == DeviceTypes.T_UNKNOWN, "empty");
    }
}
