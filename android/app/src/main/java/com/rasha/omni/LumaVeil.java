package com.rasha.omni;

/**
 * Contrast protection for text and icons over photography. The glass surfaces and the page scrims darken in proportion to how bright the
 * wallpaper is where they sit: a dark photograph needs almost nothing, a bright sky needs a firm veil. Pure, so the rule is tested.
 */
final class LumaVeil {
    private LumaVeil() { }

    /** Relative luminance (0..1) of an ARGB pixel set, gamma-aware enough for a veil decision. */
    static float luma(int[] px) {
        if (px.length == 0) return 0f;
        double t = 0;
        for (int p : px) {
            double r = lin((p >> 16) & 255), g = lin((p >> 8) & 255), b = lin(p & 255);
            t += 0.2126 * r + 0.7152 * g + 0.0722 * b;
        }
        return (float) (t / px.length);
    }

    private static double lin(int c) {
        double s = c / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** Veil alpha (0..255) for a surface over a region of the given luminance: 70 over dark, up to 175 over bright; monotone. */
    static int alpha(float luma) {
        float l = Math.max(0f, Math.min(1f, luma));
        return Math.round(70f + 105f * (float) Math.sqrt(l));
    }

    /** Page scrim alpha behind headings and shelf labels (lighter than a glass veil: the photograph must stay visible). */
    static int scrim(float luma) {
        float l = Math.max(0f, Math.min(1f, luma));
        return Math.round(40f + 90f * (float) Math.sqrt(l));
    }
}
