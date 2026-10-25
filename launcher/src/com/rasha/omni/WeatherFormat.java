package com.rasha.omni;

/** Pure presentation rules for the weather app (compass points, UV words, sky colours, range bars), so they are tested without a screen. */
final class WeatherFormat {
    private WeatherFormat() { }

    private static final String[] COMPASS = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    static String compass(double deg) {
        int i = (int) Math.round((((deg % 360) + 360) % 360) / 22.5) % 16;
        return COMPASS[i];
    }

    /** WHO UV index bands. */
    static String uvLabel(double uv) {
        if (uv < 0) return "";
        if (uv < 3) return "Low";
        if (uv < 6) return "Moderate";
        if (uv < 8) return "High";
        if (uv < 11) return "Very high";
        return "Extreme";
    }

    /** Beaufort-style plain words for a wind speed in km/h. */
    static String windWord(double kmh) {
        if (kmh < 2) return "Calm";
        if (kmh < 12) return "Light breeze";
        if (kmh < 29) return "Breezy";
        if (kmh < 50) return "Windy";
        if (kmh < 75) return "Strong wind";
        return "Storm force";
    }

    /** Top and bottom sky colours (ARGB) for a condition kind and day/night; the lower panels sit on a dark fade, so these only paint the upper sky. */
    static int[] sky(int kind, boolean day) {
        if (!day) {
            switch (kind) {
                case WeatherCodes.RAIN: case WeatherCodes.STORM: return new int[]{0xFF1B2638, 0xFF090C14};
                case WeatherCodes.CLOUD: case WeatherCodes.FOG: return new int[]{0xFF242B36, 0xFF0A0C10};
                case WeatherCodes.SNOW: return new int[]{0xFF2B3647, 0xFF0B0E14};
                default: return new int[]{0xFF101A3A, 0xFF070914};
            }
        }
        switch (kind) {
            case WeatherCodes.CLEAR: return new int[]{0xFF2F7FD0, 0xFF0D2745};
            case WeatherCodes.PARTLY: return new int[]{0xFF4B88B8, 0xFF14293D};
            case WeatherCodes.CLOUD: return new int[]{0xFF5B6773, 0xFF1A1E24};
            case WeatherCodes.FOG: return new int[]{0xFF6D7780, 0xFF20252A};
            case WeatherCodes.RAIN: return new int[]{0xFF35566A, 0xFF101C24};
            case WeatherCodes.SNOW: return new int[]{0xFF8FA6BA, 0xFF27323D};
            case WeatherCodes.STORM: return new int[]{0xFF45356B, 0xFF110C1E};
            default: return new int[]{0xFF3C4650, 0xFF15181C};
        }
    }

    /** Start and end (0..1) of a day's low-high bar inside the week's overall range. Always at least a small visible bar. */
    static float[] range(double lo, double hi, double weekLo, double weekHi) {
        double span = Math.max(1.0, weekHi - weekLo);
        float a = (float) ((lo - weekLo) / span), b = (float) ((hi - weekLo) / span);
        a = Math.max(0f, Math.min(1f, a));
        b = Math.max(0f, Math.min(1f, b));
        if (b - a < 0.06f) b = Math.min(1f, a + 0.06f);
        return new float[]{a, b};
    }

    /** Hour label for the strip: "Now" for the first column, else the 24-hour or 12-hour hour. */
    static String hour(int hourOfDay, boolean first, boolean h24) {
        if (first) return "Now";
        if (h24) return String.format(java.util.Locale.US, "%02d", hourOfDay);
        int h = hourOfDay % 12 == 0 ? 12 : hourOfDay % 12;
        return h + (hourOfDay < 12 ? "am" : "pm");
    }

    /** "H:mm" out of an ISO local time like 2026-10-24T07:51; empty when malformed. */
    static String clock(String iso) {
        if (iso == null || iso.length() < 16 || iso.charAt(10) != 'T') return "";
        return iso.substring(11, 16);
    }

    static String percent(int p) { return p <= 0 ? "" : p + "%"; }

    static String speed(double kmh) { return Math.round(kmh) + " km/h"; }
}
