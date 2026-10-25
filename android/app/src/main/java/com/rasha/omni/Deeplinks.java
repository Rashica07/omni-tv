package com.rasha.omni;

import java.net.URLEncoder;

/** Deep links into streaming apps. Only links the installed app registers for are built (checked against its manifest filters on the TV). */
final class Deeplinks {
    private Deeplinks() { }

    static final String NETFLIX_PKG = "com.netflix.ninja";

    /** Opens Netflix on the results for a title. Netflix registers nflx://www.netflix.com/... ; title ids are not available from TMDB, so search is the honest route. */
    static String netflixSearch(String title) {
        String t = title == null ? "" : title.trim();
        if (t.length() > 120) t = t.substring(0, 120);
        try { return "nflx://www.netflix.com/search?q=" + URLEncoder.encode(t, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException e) { return "nflx://www.netflix.com/browse"; }
    }

    /** A direct link to one title when its Netflix id is known (for example from Netflix's own Watch Next rows). */
    static String netflixTitle(long id) { return id > 0 ? "https://www.netflix.com/title/" + id : null; }

    /** Pulls a Netflix title id out of a link a publisher gave us (".../title/80057281" or ".../watch/80057281"), or 0. */
    static long netflixIdFrom(String uri) {
        if (uri == null) return 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("netflix\\.com/(?:[a-z\\-]{2,8}/)?(?:title|watch)/(\\d{3,12})").matcher(uri);
        if (!m.find()) return 0;
        try { return Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return 0; }
    }
}
