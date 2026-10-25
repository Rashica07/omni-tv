package com.rasha.omni;

final class AccordionTest {
    static void run() {
        float pitch = 214, tileW = 200, visible = 1152;
        int n = 12;

        // the first tiles never scroll, so the stage and the dock stay put at the start
        for (float f = 0; f <= 2f; f += 0.25f) T.eq(Accordion.scroll(f, n, pitch, tileW, visible, 2f), 0f, 0.001f, "no scroll before the lead (focus " + f + ")");
        // the shelf then follows focus one pitch per step, so the focused tile stays in the same slot
        for (int f = 3; f < 8; f++) T.eq(Accordion.scroll(f, n, pitch, tileW, visible, 2f), (f - 2) * pitch, 0.001f, "follows focus at " + f);
        // and stops when the last tile reaches the right edge: never scroll past the end
        float max = (n - 1) * pitch + tileW - visible;
        for (int f = 0; f < n; f++) T.ok(Accordion.scroll(f, n, pitch, tileW, visible, 2f) <= max + 0.001f, "clamped at the end (focus " + f + ")");
        T.eq(Accordion.scroll(n - 1, n, pitch, tileW, visible, 2f), max, 0.001f, "last tile sits at the right edge");
        // a short shelf never scrolls
        T.eq(Accordion.scroll(2f, 3, pitch, tileW, visible, 2f), 0f, 0.001f, "short shelf does not scroll");
        T.eq(Accordion.scroll(0f, 0, pitch, tileW, visible, 2f), 0f, 0.001f, "empty shelf");

        // continuity: a tiny focus change never moves the shelf by more than a tiny amount (no jumps)
        float worst = 0, prev = Accordion.scroll(0, n, pitch, tileW, visible, 2f);
        for (float f = 0.01f; f < n; f += 0.01f) {
            float s = Accordion.scroll(f, n, pitch, tileW, visible, 2f);
            worst = Math.max(worst, Math.abs(s - prev));
            T.ok(s >= prev - 0.0001f, "scroll is monotonic in focus (" + f + ")");
            prev = s;
        }
        T.ok(worst <= pitch * 0.0101f + 0.001f, "scroll step is bounded by pitch * dFocus (worst " + worst + ")");

        // variable-width shelf: a banner (200), two square icons (112) and posters mixed; same guarantees
        float[] w = {200, 112, 200, 112, 200, 112, 112, 200, 200, 112};
        int m = w.length; float[] lefts = new float[m]; float x = 0, g = 14;
        for (int i = 0; i < m; i++) { lefts[i] = x; x += w[i] + g; }
        float total = x - g, vis = 1152;
        for (float f = 0; f <= 2f; f += 0.25f) T.eq(Accordion.scrollVar(f, lefts, m, total, vis, 2), 0f, 0.001f, "var: no scroll before the lead");
        float prevV = 0, worstV = 0;
        for (float f = 0; f <= m - 1; f += 0.01f) {
            float sv = Accordion.scrollVar(f, lefts, m, total, vis, 2);
            T.ok(sv >= prevV - 0.0001f, "var: monotonic (" + f + ")");
            T.ok(sv <= Math.max(0f, total - vis) + 0.001f, "var: clamped at the end (" + f + ")");
            worstV = Math.max(worstV, Math.abs(sv - prevV)); prevV = sv;
        }
        T.ok(worstV <= 214f * 0.0101f + 0.01f, "var: step bounded by the widest pitch (worst " + worstV + ")");
        T.eq(Accordion.scrollVar(5f, lefts, m, 300f, vis, 2), 0f, 0.001f, "var: a short shelf never scrolls");
        T.eq(Accordion.scrollVar(3f, lefts, 0, 0f, vis, 2), 0f, 0.001f, "var: empty shelf");

        // bump: 1 on the tile, 0 one tile away, symmetric, monotonic
        T.eq(Accordion.bump(0f), 1f, 0.0001f, "bump at 0");
        T.eq(Accordion.bump(1f), 0f, 0.0001f, "bump at 1");
        T.eq(Accordion.bump(1.7f), 0f, 0.0001f, "bump beyond 1");
        float last = 1f;
        for (float d = 0; d <= 1f; d += 0.05f) { float b = Accordion.bump(d); T.ok(b <= last + 0.0001f, "bump decreases"); last = b; }
    }
}
