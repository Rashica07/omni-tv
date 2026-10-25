package com.rasha.omni;

/** WMO weather interpretation codes (as returned by Open-Meteo) reduced to the few glyphs the status bar can draw. */
final class WeatherCodes {
    private WeatherCodes() { }

    static final int CLEAR = 0, PARTLY = 1, CLOUD = 2, FOG = 3, RAIN = 4, SNOW = 5, STORM = 6, UNKNOWN = 7;

    static int kind(int wmo) {
        if (wmo == 0) return CLEAR;
        if (wmo == 1 || wmo == 2) return PARTLY;
        if (wmo == 3) return CLOUD;
        if (wmo == 45 || wmo == 48) return FOG;
        if ((wmo >= 51 && wmo <= 67) || (wmo >= 80 && wmo <= 82)) return RAIN;
        if ((wmo >= 71 && wmo <= 77) || wmo == 85 || wmo == 86) return SNOW;
        if (wmo >= 95 && wmo <= 99) return STORM;
        return UNKNOWN;
    }

    static String label(int kind) {
        switch (kind) {
            case CLEAR: return "Clear";
            case PARTLY: return "Partly cloudy";
            case CLOUD: return "Cloudy";
            case FOG: return "Fog";
            case RAIN: return "Rain";
            case SNOW: return "Snow";
            case STORM: return "Thunderstorm";
            default: return "Weather";
        }
    }

    /** "18°" for a plausible reading, "" otherwise: an implausible or missing number is never shown. */
    static String temp(double c) {
        if (Double.isNaN(c) || c < -80 || c > 70) return "";
        long r = Math.round(c);
        return (r == 0 ? 0 : r) + "°";
    }

    /** How old a reading is, for the "updated" line: "just now", "12 min ago", "3 h ago", "yesterday or older". */
    static String age(long ms) {
        if (ms < 0) return "";
        long m = ms / 60000;
        if (m < 2) return "just now";
        if (m < 60) return m + " min ago";
        long h = m / 60;
        return h < 24 ? h + " h ago" : "a day or more ago";
    }
}
