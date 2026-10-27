package com.rasha.omni;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pure decisions of the updater: which release is newer, what its checksum is, which download hosts are allowed and when to ask again.
 * Android I/O lives in {@link Updater}; everything here is tested on the JVM.
 */
final class UpdateRules {
    private UpdateRules() { }

    /** One published release as the updater sees it. */
    static final class Release {
        final String tag, apkUrl, body;
        final boolean prerelease;
        final long size;
        Release(String tag, boolean prerelease, String apkUrl, long size, String body) { this.tag = tag; this.prerelease = prerelease; this.apkUrl = apkUrl; this.size = size; this.body = body == null ? "" : body; }
    }

    private static final Pattern VERSION = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]+)\\.?(\\d+)?)?$");

    /** [major, minor, patch, stage, stageNumber] with stage alpha=0, beta=1, rc=2, final=3; null when the text is not a version. */
    static int[] parse(String v) {
        if (v == null) return null;
        Matcher m = VERSION.matcher(v.trim());
        if (!m.matches()) return null;
        int stage = 3, num = 0;
        if (m.group(4) != null) {
            String s = m.group(4).toLowerCase(Locale.US);
            stage = s.equals("alpha") ? 0 : s.equals("beta") ? 1 : s.equals("rc") ? 2 : -1;
            if (stage < 0) return null;                                       // an unknown suffix is not trusted to be newer or older
            num = m.group(5) == null ? 0 : Integer.parseInt(m.group(5));
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), stage, num};
    }

    /** Positive when a is newer than b, negative when older, 0 when equal or when either is not a version. */
    static int compare(String a, String b) {
        int[] x = parse(a), y = parse(b);
        if (x == null || y == null) return 0;
        for (int i = 0; i < 5; i++) if (x[i] != y[i]) return Integer.compare(x[i], y[i]);
        return 0;
    }

    /** The newest release that is newer than [current], has an APK, and is allowed by the pre-release choice. Drafts never reach here. */
    static Release pick(List<Release> all, String current, boolean includePre) {
        Release best = null;
        for (Release r : all) {
            if (r.apkUrl == null || r.apkUrl.isEmpty() || (r.prerelease && !includePre)) continue;
            if (compare(r.tag, current) <= 0) continue;
            if (best == null || compare(r.tag, best.tag) > 0) best = r;
        }
        return best;
    }

    private static final Pattern SHA = Pattern.compile("(?i)sha-?256[^0-9a-fA-F]{0,12}([0-9a-fA-F]{64})(?![0-9a-fA-F])");

    /** The SHA-256 the release notes state for the APK, lower-case, or null. A release without one is never installed. */
    static String sha256(String body) {
        if (body == null) return null;
        Matcher m = SHA.matcher(body);
        return m.find() ? m.group(1).toLowerCase(Locale.US) : null;
    }

    /** Release assets are served from github.com and its CDN hosts, over https. Anything else is refused. */
    static boolean hostAllowed(String url) {
        try {
            java.net.URI u = new java.net.URI(url);
            if (!"https".equals(u.getScheme()) || u.getHost() == null) return false;
            String h = u.getHost().toLowerCase(Locale.US);
            return h.equals("github.com") || h.equals("objects.githubusercontent.com") || h.equals("release-assets.githubusercontent.com");
        } catch (Exception e) { return false; }
    }

    static final long DAY = 24L * 3600_000L, RETRY = 6L * 3600_000L;

    /** Ask at most once a day after a success, and not more than every six hours after a failure (an offline TV does not retry in a loop). */
    static boolean due(long now, long lastOk, long lastTry) {
        if (lastOk > now || lastTry > now) return true;                      // the clock was set back: do not wait for a date in the future
        return (lastOk <= 0 || now - lastOk >= DAY) && (lastTry <= 0 || now - lastTry >= RETRY);
    }

    /** The downloaded file must be for the same package, newer than what is installed, and larger than nothing. */
    static boolean installable(String installedPkg, long installedCode, String filePkg, long fileCode) {
        return installedPkg != null && installedPkg.equals(filePkg) && fileCode > installedCode;
    }

    /** "2.6 MB" style size for the sheet. */
    static String size(long bytes) { return bytes <= 0 ? "" : String.format(Locale.US, "%.1f MB", bytes / 1048576.0); }

    /** The first few non-empty lines of a release's notes, without markdown noise, for the sheet. */
    static String notes(String body, int maxLines, int maxChars) {
        if (body == null) return "";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String l : body.split("\\r?\\n")) {
            String t = l.replace("**", "").replace("`", "").replaceAll("^[#>\\-*\\s]+", "").trim();
            if (t.isEmpty() || SHA.matcher(t).find()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t.length() > 110 ? t.substring(0, 110) + "…" : t);
            if (++n >= maxLines || sb.length() >= maxChars) break;
        }
        return sb.toString();
    }
}
