package com.rasha.omni;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the platform's Watch Next programs (Android TV TvProvider): "continue watching" items that apps publish
 * themselves. This is real, ad-free data. It needs the runtime permission READ_TV_LISTINGS, which the user can
 * grant (protection level "dangerous", verified on the target TV). Publisher data is untrusted input.
 */
final class WatchNext {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    private static final Uri URI = Uri.parse("content://android.media.tv/watch_next_program");
    private static final int TYPE_CONTINUE = 0;
    private static final int TYPE_NEXT = 1;

    static final class Entry {
        long id;
        String title;
        String pkg;
        String poster;
        String intentUri;
    }

    private WatchNext() { }

    static boolean granted(Context c) {
        return android.os.Build.VERSION.SDK_INT < 23 || c.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }

    /** Most recently watched first; only CONTINUE and NEXT items (what the person is in the middle of). */
    static List<Entry> query(Context c) {
        List<Entry> out = new ArrayList<>();
        if (!granted(c)) return out;
        String[] proj = {"_id", "title", "package_name", "poster_art_uri", "intent_uri", "watch_next_type"};
        try (Cursor q = c.getContentResolver().query(URI, proj, null, null, "last_engagement_time_utc_millis DESC")) {
            if (q == null) return out;
            PackageManager pm = c.getPackageManager();
            while (q.moveToNext() && out.size() < 12) {
                int type = q.getInt(5);
                if (type != TYPE_CONTINUE && type != TYPE_NEXT) continue;
                Entry e = new Entry();
                e.id = q.getLong(0);
                e.title = q.getString(1);
                e.pkg = q.getString(2);
                e.poster = q.getString(3);
                e.intentUri = q.getString(4);
                if (e.title == null || e.pkg == null || e.intentUri == null) continue;
                try { pm.getApplicationInfo(e.pkg, 0); } catch (PackageManager.NameNotFoundException ex) { continue; }
                out.add(e);
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            // permission revoked mid-session, or an older provider without these columns: show nothing
        }
        return out;
    }

    /**
     * Turns a publisher's intent URI into something safe to start, or null. Rules (android-intent-security):
     * it must stay inside the publisher's own package, carry no selector and no URI-grant flags.
     */
    static Intent safeIntent(Entry e) {
        try {
            Intent it = Intent.parseUri(e.intentUri, Intent.URI_INTENT_SCHEME);
            it.setSelector(null);
            ComponentName cn = it.getComponent();
            if (cn != null && !e.pkg.equals(cn.getPackageName())) return null;
            if (cn == null && it.getPackage() != null && !e.pkg.equals(it.getPackage())) return null;
            if (cn == null) it.setPackage(e.pkg);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);   // drops any FLAG_GRANT_* the publisher asked for
            return it;
        } catch (Exception ex) {
            return null;
        }
    }
}
