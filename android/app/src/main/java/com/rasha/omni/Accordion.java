package com.rasha.omni;

/** Pure shelf geometry (no Android types; unit tested in test/). */
final class Accordion {
    private Accordion() { }

    /** Smooth bump: 1 at distance 0, 0 at distance >= 1. Used for how "focused" a tile is as focus glides past it. */
    static float bump(float d) {
        float t = Math.max(0f, 1f - d);
        return t * t * (3f - 2f * t);
    }

    /**
     * Horizontal scroll of a fixed-pitch shelf. The first {@code lead} tiles never scroll; after that the shelf follows focus so
     * the focused tile stays in the same slot, and it stops when the last tile reaches the right safe edge.
     * @param focus continuous focus index
     * @param pitch distance between tile left edges
     * @param tileW width of one tile
     * @param visibleW width available for tiles (view width minus both safe margins)
     */
    static float scroll(float focus, int n, float pitch, float tileW, float visibleW, float lead) {
        if (n <= 0) return 0f;
        float total = (n - 1) * pitch + tileW;
        float max = Math.max(0f, total - visibleW);
        float want = Math.max(0f, (focus - lead) * pitch);
        return Math.min(want, max);
    }

    /**
     * Horizontal scroll of a shelf whose tiles have different widths. {@code lefts[i]} is the left edge of tile i (relative to the first),
     * {@code total} the right edge of the last tile. The first {@code lead} tiles never scroll; after that the shelf follows the
     * (continuous) focus so the focused tile stays in the same slot; it stops when the last tile reaches the right safe edge.
     */
    static float scrollVar(float focus, float[] lefts, int n, float total, float visibleW, int lead) {
        if (n <= 0) return 0f;
        int a = Math.max(0, Math.min(n - 1, (int) Math.floor(focus)));
        int b = Math.min(n - 1, a + 1);
        float fr = Math.max(0f, Math.min(1f, focus - a));
        float at = lefts[a] + (lefts[b] - lefts[a]) * fr;
        float want = Math.max(0f, at - lefts[Math.min(lead, n - 1)]);
        float max = Math.max(0f, total - visibleW);
        return Math.min(want, max);
    }
}
