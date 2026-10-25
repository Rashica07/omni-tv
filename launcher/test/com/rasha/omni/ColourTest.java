package com.rasha.omni;

final class ColourTest {
    private static int[] fill(int bg, int fg, double fgShare) {
        int[] px = new int[256];
        for (int i = 0; i < px.length; i++) px[i] = i < px.length * fgShare ? fg : bg;
        return px;
    }

    private static boolean near(int c, int rgb, int tol) {
        return Math.abs(((c >> 16) & 255) - ((rgb >> 16) & 255)) <= tol && Math.abs(((c >> 8) & 255) - ((rgb >> 8) & 255)) <= tol
                && Math.abs((c & 255) - (rgb & 255)) <= tol;
    }

    private static int hueOf(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min, h;
        if (d == 0) return -1;
        if (max == r) h = ((g - b) / d) % 6f; else if (max == g) h = (b - r) / d + 2f; else h = (r - g) / d + 4f;
        h *= 60f;
        return Math.round(h < 0 ? h + 360f : h);
    }

    static void run() {
        int white = 0xFFF2F2F2, red = 0xFFE62117, blue = 0xFF1877F2, orange = 0xFFFF6A00, black = 0xFF0A0A0A;
        int c = Dominant.pick(fill(white, red, 0.18));
        T.ok(c != 0 && Math.abs(hueOf(c) - hueOf(red)) <= 8, "a red logo on a white banner is red, not white (YouTube-like)");
        c = Dominant.pick(fill(white, blue, 0.20));
        T.ok(c != 0 && Math.abs(hueOf(c) - hueOf(blue)) <= 8, "a blue brand is blue");
        c = Dominant.pick(fill(black, orange, 0.12));
        T.ok(c != 0 && Math.abs(hueOf(c) - hueOf(orange)) <= 8, "an orange logo on black is orange");
        T.ok(Dominant.pick(fill(white, 0xFF888888, 0.5)) == 0, "a gray icon has no brand colour: no tint is invented");
        T.ok(Dominant.pick(fill(black, white, 0.5)) == 0, "black and white artwork: no tint");
        int[] clear = new int[256];
        T.ok(Dominant.pick(clear) == 0, "a fully transparent image: no tint");
        // two colours: the heavier one wins
        int[] two = new int[256];
        for (int i = 0; i < 256; i++) two[i] = i < 90 ? red : i < 130 ? blue : white;
        c = Dominant.pick(two);
        T.ok(Math.abs(hueOf(c) - hueOf(red)) <= 8, "the larger colour area wins");
        // the result is usable as ambient light: not neon, not dark
        int e = Dominant.ambient(255, 0, 0);
        T.ok(((e >> 16) & 255) <= 235 && ((e >> 8) & 255) >= 40, "pure red is softened into ambient light");
        int dark = Dominant.ambient(40, 0, 0);
        T.ok(((dark >> 16) & 255) >= 130, "a very dark red is lifted so it can glow");
        T.ok(Dominant.pick(fill(white, red, 0.18)) == Dominant.pick(fill(white, red, 0.18)), "deterministic");

        // contrast veil
        T.ok(LumaVeil.luma(new int[]{0xFF000000, 0xFF000000}) < 0.001f && LumaVeil.luma(new int[]{0xFFFFFFFF}) > 0.99f, "luminance extremes");
        T.ok(LumaVeil.alpha(0f) == 70 && LumaVeil.alpha(1f) == 175, "veil range");
        boolean mono = true;
        for (float l = 0; l < 1f; l += 0.05f) if (LumaVeil.alpha(l + 0.05f) < LumaVeil.alpha(l)) mono = false;
        T.ok(mono, "brighter wallpaper never gets a lighter veil");
        T.ok(LumaVeil.scrim(0.5f) < LumaVeil.alpha(0.5f), "page scrim is lighter than a glass veil");
        T.ok(LumaVeil.alpha(-1f) == 70 && LumaVeil.alpha(9f) == 175, "out-of-range input is clamped");
    }
}
