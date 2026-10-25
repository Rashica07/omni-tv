package com.rasha.omni;

/**
 * Picks an app's brand colour from a few hundred sampled pixels of its own artwork (pure, so it is tested). Backgrounds are ignored: near-white,
 * near-black and gray pixels carry no hue, and a hue needs both saturation and brightness to count. The result is the heaviest hue family, averaged,
 * then brought into a range that works as ambient light (never neon, never muddy). Returns 0 when the artwork has no real colour (a gray or
 * monochrome icon): the caller then uses no tint rather than inventing one.
 */
final class Dominant {
    private Dominant() { }

    private static final int BINS = 24;

    /** pixels are ARGB; typically a 16x16 downscale of the app's banner or icon. */
    static int pick(int[] px) {
        double[] weight = new double[BINS], r = new double[BINS], g = new double[BINS], b = new double[BINS];
        double total = 0;
        for (int p : px) {
            int a = p >>> 24;
            if (a < 128) continue;
            int rr = (p >> 16) & 255, gg = (p >> 8) & 255, bb = p & 255;
            int max = Math.max(rr, Math.max(gg, bb)), min = Math.min(rr, Math.min(gg, bb));
            float v = max / 255f, s = max == 0 ? 0f : (max - min) / (float) max;
            if (s < 0.28f || v < 0.22f) continue;                       // backgrounds: white, black, gray, muddy
            double w = s * s * Math.sqrt(v);                           // saturated, bright pixels define the brand
            int bin = (int) (hue(rr, gg, bb, max, min) / 360f * BINS) % BINS;
            weight[bin] += w; r[bin] += w * rr; g[bin] += w * gg; b[bin] += w * bb;
            total += w;
        }
        if (total < px.length * 0.012) return 0;                        // too little real colour: no tint
        int best = 0;
        double bestW = -1;
        for (int i = 0; i < BINS; i++) {                                // a hue family is a bin plus its neighbours
            double w = weight[i] + weight[(i + 1) % BINS] + weight[(i + BINS - 1) % BINS];
            if (w > bestW) { bestW = w; best = i; }
        }
        double w = 0, sr = 0, sg = 0, sb = 0;
        for (int d = -1; d <= 1; d++) {
            int i = (best + d + BINS) % BINS;
            w += weight[i]; sr += r[i]; sg += g[i]; sb += b[i];
        }
        if (w <= 0) return 0;
        return ambient((int) (sr / w), (int) (sg / w), (int) (sb / w));
    }

    private static float hue(int r, int g, int b, int max, int min) {
        float d = max - min;
        float h;
        if (d == 0) return 0f;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h *= 60f;
        return h < 0 ? h + 360f : h;
    }

    /** Keeps the hue, limits saturation and brightness so the colour reads as light on a photograph, not as a flat neon block. */
    static int ambient(int r, int g, int b) {
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float v = max / 255f, s = max == 0 ? 0f : (max - min) / (float) max;
        float nv = Math.max(0.55f, Math.min(0.92f, v));
        float ns = Math.max(0.45f, Math.min(0.80f, s));
        float h = hue(r, g, b, max, min);
        return 0xFF000000 | hsv(h, ns, nv);
    }

    private static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float rr, gg, bb;
        int k = (int) (h / 60f) % 6;
        switch (k) {
            case 0: rr = c; gg = x; bb = 0; break;
            case 1: rr = x; gg = c; bb = 0; break;
            case 2: rr = 0; gg = c; bb = x; break;
            case 3: rr = 0; gg = x; bb = c; break;
            case 4: rr = x; gg = 0; bb = c; break;
            default: rr = c; gg = 0; bb = x; break;
        }
        return (Math.round((rr + m) * 255) << 16) | (Math.round((gg + m) * 255) << 8) | Math.round((bb + m) * 255);
    }
}
