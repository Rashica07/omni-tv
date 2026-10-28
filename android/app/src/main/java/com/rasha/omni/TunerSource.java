package com.rasha.omni;

import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The TV's own tuner channels, read through the public TV provider (needs the READ_TV_LISTINGS permission, which OMNI asks for once).
 * These are the channels the TV itself scanned; OMNI does not scan, decrypt or alter anything. Only channels of real tuner input services are
 * listed, so apps' own "internet live channels" are not mixed in. The same service on two numbers (HD and SD) appears once.
 */
final class TunerSource {
    private TunerSource() { }

    /** True when the TV has an antenna tuner input at all (scanned or not). */
    static boolean hasTuner(Context c) {
        try {
            TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo i : m.getTvInputList()) if (i.getType() == TvInputInfo.TYPE_TUNER && !i.isPassthroughInput()) return true;
        } catch (RuntimeException ignored) { }
        return false;
    }

    /** The TV maker's own channel-scan screen for the first tuner, or null. OMNI never scans itself: the TV's tuner app does, and OMNI reads the result. */
    static android.content.Intent scanIntent(Context c) {
        try {
            TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo i : m.getTvInputList()) if (i.getType() == TvInputInfo.TYPE_TUNER && !i.isPassthroughInput()) {
                android.content.Intent s = i.createSetupIntent();
                if (s != null) return s.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            }
        } catch (RuntimeException ignored) { }
        return null;
    }

    /** One line for the setup and the TV page: how many antenna channels there are and from which countries, or that there are none. */
    static String summary(Context c) {
        List<PtvModel.Channel> l = load(c);
        if (l.isEmpty()) return hasTuner(c) ? "No antenna channels yet" : "";
        int it = 0, ch = 0, al = 0, xk = 0;
        for (PtvModel.Channel x : l) { switch (x.country) { case "IT": it++; break; case "CH": ch++; break; case "AL": al++; break; case "XK": xk++; break; default: } }
        StringBuilder sb = new StringBuilder(Tr.count(l.size(), "channel", "channels"));
        if (it + ch + al + xk > 0) {
            sb.append(": ");
            String[] names = {Tr.t("Italy"), Tr.t("Switzerland"), Tr.t("Albania"), Tr.t("Kosovo")};
            int[] n = {it, ch, al, xk};
            boolean first = true;
            for (int i = 0; i < 4; i++) if (n[i] > 0) { if (!first) sb.append(", "); sb.append(names[i]).append(' ').append(n[i]); first = false; }
        }
        return sb.toString();
    }

    static List<PtvModel.Channel> load(Context c) {
        List<PtvModel.Channel> out = new ArrayList<>();
        Set<String> tunerInputs = new HashSet<>();
        try {
            TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
            for (TvInputInfo i : m.getTvInputList()) if (i.getType() == TvInputInfo.TYPE_TUNER && !i.isPassthroughInput()) tunerInputs.add(i.getId());
        } catch (RuntimeException e) {
            return out;
        }
        if (tunerInputs.isEmpty() || !WatchNext.granted(c)) return out;
        Set<String> seen = new HashSet<>();
        try (Cursor d = c.getContentResolver().query(TvContract.Channels.CONTENT_URI, new String[]{TvContract.Channels._ID, TvContract.Channels.COLUMN_DISPLAY_NUMBER,
                TvContract.Channels.COLUMN_DISPLAY_NAME, TvContract.Channels.COLUMN_INPUT_ID, TvContract.Channels.COLUMN_TYPE, TvContract.Channels.COLUMN_SERVICE_TYPE},
                null, null, null)) {
            if (d == null) return out;
            while (d.moveToNext()) {
                String input = d.getString(3), name = PtvRules.cleanName(d.getString(2));
                String type = String.valueOf(d.getString(4));
                if (input == null || !tunerInputs.contains(input) || name.isEmpty() || !type.startsWith("TYPE_DVB")) continue;
                String num = PtvRules.cleanName(d.getString(1));
                if (!seen.add(PtvSearch.fold(name))) continue;                  // HD/SD twins: keep the first listed
                PtvModel.Channel ch = new PtvModel.Channel();
                ch.source = "tuner";
                ch.id = "tuner:" + num + ":" + PtvSearch.fold(name).replace(' ', '-');
                ch.name = name;
                ch.number = num;
                ch.tunerInput = input;
                ch.tunerId = d.getLong(0);
                ch.country = AntennaRules.country(name);                        // Italy, Switzerland, Albania, Kosovo by name; "" when not recognised
                ch.categories.add(String.valueOf(d.getString(5)).contains("AUDIO_VIDEO") ? "tv" : "radio");
                out.add(ch);
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        java.util.Collections.sort(out, (a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.number), Integer.parseInt(b.number)); } catch (NumberFormatException e) { return a.number.compareTo(b.number); }
        });
        return out;
    }
}
