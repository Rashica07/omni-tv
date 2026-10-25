package com.rasha.omni;

/**
 * Pure rules for the wallpaper system: which cached image is current, when the cache needs topping up, and which remote photos are acceptable.
 * Selection is deterministic (the same inputs always give the same image), so navigating never changes the picture: it changes only when the
 * chosen schedule slot changes (a new day, a new six-hour slot) or, in "every launch" mode, when the app starts.
 */
final class WallpaperRules {
    private WallpaperRules() { }

    static final int OFF = 0, EVERY_LAUNCH = 1, DAILY = 2, SIX_HOURS = 3;
    static final String[] MODE_LABELS = {"Off", "Every launch", "Daily", "Every 6 hours"};
    static final int MAX_CACHED = 12, MIN_CACHED = 6;
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR;

    /** Index into a list of n cached images, or -1 for "no wallpaper" (mode off or nothing cached). tzOffsetMs is the device's current UTC offset. */
    static int index(int n, int mode, long nowMs, long tzOffsetMs, int launchCount) {
        if (n <= 0 || mode == OFF) return -1;
        long slot;
        switch (mode) {
            case EVERY_LAUNCH: slot = launchCount; break;
            case SIX_HOURS: slot = Math.floorDiv(nowMs + tzOffsetMs, 6 * HOUR); break;
            default: slot = Math.floorDiv(nowMs + tzOffsetMs, DAY); break;
        }
        return (int) Math.floorMod(slot, (long) n);
    }

    /** The schedule slot a moment belongs to (the same value for the whole day / six hours / launch). Used to lock the chosen picture. */
    static long slot(int mode, long nowMs, long tzOffsetMs, int launchCount) {
        switch (mode) {
            case EVERY_LAUNCH: return launchCount;
            case SIX_HOURS: return Math.floorDiv(nowMs + tzOffsetMs, 6 * HOUR);
            case OFF: return 0;
            default: return Math.floorDiv(nowMs + tzOffsetMs, DAY);
        }
    }

    /** True when the cache should be refreshed from the network: too few images, or the last refresh is older than a day. */
    static boolean needsRefresh(int cached, long lastRefreshMs, long nowMs) {
        return cached < MIN_CACHED || lastRefreshMs <= 0 || nowMs - lastRefreshMs >= DAY || lastRefreshMs > nowMs;
    }

    /** A photo is wanted if it is a landscape-format picture of usable size: 1.4 to 2.0 wide, at least 1600 px across. Panoramas and portraits are skipped. */
    static boolean acceptable(int width, int height) {
        if (width < 1600 || height <= 0) return false;
        float a = width / (float) height;
        return a >= 1.4f && a <= 2.0f;
    }

    /** Metadata from Commons arrives as HTML fragments; keep readable text only, bounded. */
    static String plain(String html, int max) {
        if (html == null) return "";
        String s = html.replaceAll("(?s)<[^>]*>", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">");
        s = s.replaceAll("[\\x00-\\x1f\\x7f]", " ").replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Only these licences are used from the open source: attribution is shown in Settings. */
    static boolean licenceAllowed(String lic) {
        if (lic == null) return false;
        String l = lic.toLowerCase(java.util.Locale.ROOT);
        return l.startsWith("cc by") || l.startsWith("cc0") || l.contains("public domain") || l.equals("pd") || l.startsWith("pdm");
    }

    /** A stable, safe file name for a cached image: hash of the source URL, never a name from the network. */
    static String fileFor(String url) {
        long h = 1125899906842597L;
        for (int i = 0; i < url.length(); i++) h = 31 * h + url.charAt(i);
        return "wp_" + Long.toHexString(h) + ".jpg";
    }

    /** JPEG magic bytes and a sane size. A download that is not a JPEG of reasonable size is discarded. */
    static boolean looksLikeJpeg(byte[] head, long length) {
        return head != null && head.length >= 3 && (head[0] & 255) == 0xFF && (head[1] & 255) == 0xD8 && (head[2] & 255) == 0xFF && length > 20_000 && length <= 4_000_000;
    }
}
