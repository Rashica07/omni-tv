package com.rasha.omni;

final class WeatherFormatTest {
    static void run() {
        T.ok("N".equals(WeatherFormat.compass(0)) && "N".equals(WeatherFormat.compass(359)) && "E".equals(WeatherFormat.compass(90)) && "NW".equals(WeatherFormat.compass(315))
                && "SSW".equals(WeatherFormat.compass(202)) && "N".equals(WeatherFormat.compass(-10)) && "N".equals(WeatherFormat.compass(720)), "compass points");
        T.ok("Low".equals(WeatherFormat.uvLabel(2.9)) && "Moderate".equals(WeatherFormat.uvLabel(3)) && "High".equals(WeatherFormat.uvLabel(6.5)) && "Very high".equals(WeatherFormat.uvLabel(9))
                && "Extreme".equals(WeatherFormat.uvLabel(12)) && "".equals(WeatherFormat.uvLabel(-1)), "uv bands");
        T.ok("Calm".equals(WeatherFormat.windWord(1)) && "Breezy".equals(WeatherFormat.windWord(20)) && "Storm force".equals(WeatherFormat.windWord(90)), "wind words");
        for (int k = 0; k <= WeatherCodes.UNKNOWN; k++) {
            T.ok(WeatherFormat.sky(k, true).length == 2 && WeatherFormat.sky(k, false).length == 2, "sky defined for kind " + k);
        }
        T.ok(WeatherFormat.sky(WeatherCodes.CLEAR, true)[0] != WeatherFormat.sky(WeatherCodes.CLEAR, false)[0], "day and night skies differ");
        float[] r = WeatherFormat.range(8, 14, 5, 20);
        T.ok(Math.abs(r[0] - 0.2f) < 1e-4 && Math.abs(r[1] - 0.6f) < 1e-4, "range bar position");
        float[] flat = WeatherFormat.range(10, 10, 10, 10);
        T.ok(flat[1] - flat[0] >= 0.06f && flat[0] >= 0f && flat[1] <= 1f, "a flat day still has a visible bar");
        T.ok("Now".equals(WeatherFormat.hour(21, true, true)) && "21".equals(WeatherFormat.hour(21, false, true)) && "9pm".equals(WeatherFormat.hour(21, false, false)) && "12am".equals(WeatherFormat.hour(0, false, false)) && "12pm".equals(WeatherFormat.hour(12, false, false)), "hour labels");
        T.ok("07:51".equals(WeatherFormat.clock("2026-10-24T07:51")) && "".equals(WeatherFormat.clock("garbage")) && "".equals(WeatherFormat.clock(null)), "iso clock");
        T.ok("".equals(WeatherFormat.percent(0)) && "30%".equals(WeatherFormat.percent(30)) && "12 km/h".equals(WeatherFormat.speed(11.6)), "small formats");
    }
}
