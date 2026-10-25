package com.rasha.omni;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's side of the OMNI Cinema contract (docs/CINEMA-INTEGRATION.md in omni-os, version 1). Cinema is a separate app: this class only
 * reads its catalogue provider (guarded by a signature permission, so it works only because both apps carry the OMNI key) and sends validated
 * deep links. Nothing sensitive crosses: titles, public image paths, progress, availability. Every call here must run off the main thread.
 */
final class CinemaBridge {
    static final String PKG = "com.rasha.omni.cinema";
    private static final String AUTH = "content://com.rasha.omni.cinema.catalogue";
    private static final java.util.regex.Pattern ID = java.util.regex.Pattern.compile("^omni:(movie|show):tmdb:[1-9][0-9]{0,8}$|^omni:ep:tmdb:[1-9][0-9]{0,8}:[0-9]{1,3}:[0-9]{1,4}$|^omni:file:[0-9a-f]{16}$");

    static final int OK = 0, NOT_INSTALLED = 1, UNAVAILABLE = 2;

    static final class Title {
        String id, kind, title, overview, sourceLabel, availability, poster, backdrop;
        int year;
        double rating;
        long positionMs, durationMs;
        boolean watched;
        boolean playable() { return "PLAYABLE".equals(availability); }
    }

    static final class Shelf {
        String id, name;
        final List<Title> items = new ArrayList<>();
    }

    static final class Result {
        int state = UNAVAILABLE;
        String detail = "";
        final List<Shelf> shelves = new ArrayList<>();
    }

    static boolean installed(Context c) {
        try { c.getPackageManager().getPackageInfo(PKG, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
    }

    /** Reads the shelves Cinema offers. Failure of any kind (not installed, provider down, permission missing) is a state, never an exception. */
    static Result load(Context c) {
        Result r = new Result();
        if (!installed(c)) { r.state = NOT_INSTALLED; r.detail = "OMNI Cinema is not installed."; return r; }
        ContentResolver cr = c.getContentResolver();
        try {
            try (Cursor v = cr.query(Uri.parse(AUTH + "/version"), null, null, null, null)) {
                if (v == null || !v.moveToFirst() || v.getInt(v.getColumnIndexOrThrow("contract")) != 1) { r.detail = "Cinema speaks a different version of the OMNI contract."; return r; }
            }
            List<String[]> shelves = new ArrayList<>();
            try (Cursor s = cr.query(Uri.parse(AUTH + "/shelves"), null, null, null, null)) {
                while (s != null && s.moveToNext()) shelves.add(new String[]{s.getString(0), s.getString(1)});
            }
            for (String[] sh : shelves) {
                Shelf shelf = new Shelf(); shelf.id = sh[0]; shelf.name = sh[1];
                try (Cursor q = cr.query(Uri.parse(AUTH + "/items?shelf=" + Uri.encode(sh[0]) + "&limit=24"), null, null, null, null)) {
                    while (q != null && q.moveToNext()) { Title t = row(q); if (t != null) shelf.items.add(t); }
                }
                if (!shelf.items.isEmpty()) r.shelves.add(shelf);
            }
            r.state = OK;
        } catch (SecurityException e) {
            r.detail = "Cinema is installed but does not trust this launcher (the two apps must be signed with the same OMNI key).";
        } catch (RuntimeException e) {
            r.detail = "Cinema's catalogue is not answering.";
        }
        return r;
    }

    private static Title row(Cursor q) {
        Title t = new Title();
        t.id = q.getString(q.getColumnIndexOrThrow("media_id"));
        if (t.id == null || !ID.matcher(t.id).matches()) return null;           // ids from outside are validated before they are ever used in a link
        t.kind = q.getString(q.getColumnIndexOrThrow("kind"));
        t.title = q.getString(q.getColumnIndexOrThrow("title"));
        t.year = q.getInt(q.getColumnIndexOrThrow("year"));
        t.overview = q.getString(q.getColumnIndexOrThrow("overview"));
        t.poster = imagePath(q.getString(q.getColumnIndexOrThrow("poster")));
        t.backdrop = imagePath(q.getString(q.getColumnIndexOrThrow("backdrop")));
        t.rating = q.getDouble(q.getColumnIndexOrThrow("rating"));
        t.sourceLabel = q.getString(q.getColumnIndexOrThrow("source_label"));
        t.availability = q.getString(q.getColumnIndexOrThrow("availability"));
        t.positionMs = q.getLong(q.getColumnIndexOrThrow("position_ms"));
        t.durationMs = q.getLong(q.getColumnIndexOrThrow("duration_ms"));
        t.watched = q.getInt(q.getColumnIndexOrThrow("watched")) == 1;
        if (t.title == null) return null;
        return t;
    }

    /** The provider sends full TMDB URLs; the launcher's image pipeline wants the path ("/abc.jpg") and picks its own size. Anything else is dropped. */
    static String imagePath(String url) {
        if (url == null || !url.startsWith("https://image.tmdb.org/t/p/")) return null;
        String rest = url.substring("https://image.tmdb.org/t/p/".length());
        int slash = rest.indexOf('/');
        String p = slash < 0 ? null : rest.substring(slash);
        return p != null && p.matches("/[A-Za-z0-9_.-]{1,80}") ? p : null;
    }

    static String clock(long ms) {
        long s = ms / 1000, h = s / 3600, m = s % 3600 / 60;
        return h > 0 ? String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s % 60) : String.format(java.util.Locale.US, "%d:%02d", m, s % 60);
    }

    /** Starts Cinema at a deep link. Returns false (and the caller says so) if it cannot be started. */
    static boolean open(Context c, String link) {
        try {
            c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)).setClassName(PKG, PKG + ".DeepLinkActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (RuntimeException e) { return false; }
    }

    static String playLink(Title t) { return "omni-cinema://play/" + t.id; }
    static String titleLink(Title t) { return "omni-cinema://" + ("show".equals(t.kind) ? "show" : "episode".equals(t.kind) ? "episode" : "movie") + "/" + t.id; }
}
