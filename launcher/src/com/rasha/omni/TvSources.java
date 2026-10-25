package com.rasha.omni;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

/**
 * Records, in the debug log only, what the platform's TV frameworks expose to an ordinary app on this TV: which input services exist and how
 * many channels the TV provider will hand over. This is how the "all TV sources" question is answered with evidence instead of assumptions.
 */
final class TvSources {
    private TvSources() { }

    static void log(Context c) {
        if (!android.util.Log.isLoggable("OmniState", android.util.Log.DEBUG)) return;
        try {
            TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            int tuner = 0, hdmi = 0, other = 0;
            for (TvInputInfo i : m.getTvInputList()) {
                if (i.getType() == TvInputInfo.TYPE_TUNER) tuner++;
                else if (i.getType() == TvInputInfo.TYPE_HDMI) hdmi++;
                else other++;
                OmniLog.state("tif input id=" + i.getId() + " type=" + i.getType() + " passthrough=" + i.isPassthroughInput() + " pkg=" + i.getServiceInfo().packageName);
            }
            OmniLog.state("tif inputs tuner=" + tuner + " hdmi=" + hdmi + " other=" + other);
        } catch (RuntimeException e) {
            OmniLog.state("tif inputs unreadable: " + e.getClass().getSimpleName());
        }
        int total = 0, withGuide = 0, withDesc = 0;
        long now = System.currentTimeMillis(), latest = 0;
        for (PtvModel.Channel ch : TunerSource.load(c)) {
            int perChannel = 0;
            try (Cursor pc = c.getContentResolver().query(TvContract.buildProgramsUriForChannel(ch.tunerId, now, now + 48L * 3600_000L), new String[]{TvContract.Programs.COLUMN_TITLE,
                    TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, TvContract.Programs.COLUMN_SHORT_DESCRIPTION, TvContract.Programs.COLUMN_BROADCAST_GENRE}, null, null, null)) {
                while (pc != null && pc.moveToNext()) {
                    perChannel++;
                    total++;
                    latest = Math.max(latest, pc.getLong(2));
                    if (pc.getString(3) != null && pc.getString(3).length() > 3) withDesc++;
                    if (total <= 5) OmniLog.state("tif program " + ch.name + ": '" + pc.getString(0) + "' in " + (pc.getLong(1) - now) / 60000 + " min genre=" + pc.getString(4));
                }
            } catch (RuntimeException e) {
                OmniLog.state("tif programs " + ch.name + ": " + e.getClass().getSimpleName() + " " + e.getMessage());
                break;
            }
            if (perChannel > 0) withGuide++;
        }
        OmniLog.state("tif programs (48h) total=" + total + " channelsWithGuide=" + withGuide + " withDescription=" + withDesc + " latestEndInHours=" + (latest - now) / 3600000);
        try (Cursor cur = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_PACKAGE_NAME, TvContract.Channels.COLUMN_INPUT_ID}, null, null, null)) {
            if (cur == null) { OmniLog.state("tif channels: provider returned no cursor"); return; }
            java.util.Map<String, Integer> byPkg = new java.util.HashMap<>();
            while (cur.moveToNext()) byPkg.merge(String.valueOf(cur.getString(1)), 1, Integer::sum);
            OmniLog.state("tif channels visible=" + cur.getCount() + " byPackage=" + byPkg);
            // detail for the TV's own tuner channels, to learn what an app can and cannot do with them
            try (Cursor d = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                    TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_TYPE, TvContract.Channels.COLUMN_SERVICE_TYPE, TvContract.Channels.COLUMN_BROWSABLE,
                    TvContract.Channels.COLUMN_PACKAGE_NAME}, null, null, TvContract.Channels.COLUMN_DISPLAY_NUMBER)) {
                int n = 0;
                while (d != null && d.moveToNext() && n < 60) {
                    if (!"com.mediatek.tvinput".equals(d.getString(6))) continue;
                    n++;
                    OmniLog.state("tif tuner ch id=" + d.getLong(0) + " no=" + d.getString(1) + " name=" + d.getString(2) + " type=" + d.getString(3) + " svc=" + d.getString(4) + " browsable=" + d.getInt(5));
                }
            } catch (RuntimeException e) {
                OmniLog.state("tif tuner detail: " + e.getClass().getSimpleName());
            }
        } catch (SecurityException e) {
            OmniLog.state("tif channels: denied (" + e.getMessage() + ")");
        } catch (RuntimeException e) {
            OmniLog.state("tif channels: " + e.getClass().getSimpleName());
        }
    }
}
