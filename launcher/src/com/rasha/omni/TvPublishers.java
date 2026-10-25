package com.rasha.omni;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;

import java.util.HashSet;
import java.util.Set;

/**
 * Packages that publish channels or "continue watching" items to the TV. An app that does this is feeding the TV's own rows, which is
 * good evidence that it is a streaming or TV app even when it never declared a category (Netflix and YouTube do not). Needs the
 * READ_TV_LISTINGS permission; without it the set is simply empty.
 */
final class TvPublishers {
    private TvPublishers() { }

    static Set<String> query(Context c) {
        Set<String> out = new HashSet<>();
        if (!WatchNext.granted(c)) return out;
        add(c, out, TvContract.Channels.CONTENT_URI, TvContract.Channels.COLUMN_PACKAGE_NAME);
        add(c, out, TvContract.WatchNextPrograms.CONTENT_URI, "package_name");
        return out;
    }

    private static void add(Context c, Set<String> out, android.net.Uri uri, String column) {
        try (Cursor q = c.getContentResolver().query(uri, new String[]{column}, null, null, null)) {
            if (q == null) return;
            while (q.moveToNext()) { String p = q.getString(0); if (p != null) out.add(p); }
        } catch (RuntimeException ignored) { /* permission revoked or provider unavailable: no evidence, no category */ }
    }
}
