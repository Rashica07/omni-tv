package com.rasha.omni;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.Uri;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The TV's physical inputs as an ordinary app can see them: only what the public TvInputManager API exposes (input list, system label,
 * custom label, type, connection state, parent port). HDMI-CEC device details (HdmiDeviceInfo) are hidden from apps, so a device name
 * appears here only when the TV itself put it in an input label. Everything else is either the port name or something the user set.
 *
 * Provenance is kept per field: {@link Entry#nameFromTv}, {@link Entry#typeFromUser}, {@link Entry#typeGuessed}. The UI shows which is which
 * and never presents a guess as a detection.
 */
final class Inputs {
    static final class Entry {
        String id;
        /** Port name as the TV labels it, e.g. "HDMI 2" (may be a CEC device name when the TV shows one). */
        String portLabel;
        /** The label of a hot-plugged HDMI-CEC child input, when the TV created one (this is the device's own CEC name). */
        String cecName;
        /** The input of the connected HDMI-CEC device (the PlayStation itself), when the TV created one. This, not the bare port, is what the TV maker's own app tunes and what delivers the picture. */
        String childId;
        /** The label the user typed in the system's input settings, if any. */
        String customLabel;
        int tvType;                // TvInputInfo.TYPE_*
        int state;                 // TvInputManager.INPUT_STATE_*
        boolean hdmi;
        int type = T_UNKNOWN;
        boolean typeFromUser;
        boolean typeGuessed;       // inferred from a name the TV supplied (never from nothing)

        String playId() { return childId != null ? childId : id; }

        String displayName() {
            if (cecName != null) return cecName;
            if (customLabel != null && !customLabel.isEmpty()) return customLabel;
            return portLabel;
        }

        boolean nameFromTv() { return cecName != null || (customLabel != null && !customLabel.isEmpty()); }

        String stateText() {
            switch (state) {
                case TvInputManager.INPUT_STATE_CONNECTED: return "Signal";
                case TvInputManager.INPUT_STATE_CONNECTED_STANDBY: return hdmi ? "Connected, standby" : "Connected";
                default: return hdmi ? "Nothing connected" : "No signal";
            }
        }
    }

    static final int T_UNKNOWN = DeviceTypes.T_UNKNOWN, T_CONSOLE = DeviceTypes.T_CONSOLE, T_PC = DeviceTypes.T_PC, T_STREAMER = DeviceTypes.T_STREAMER,
            T_BOX = DeviceTypes.T_BOX, T_AUDIO = DeviceTypes.T_AUDIO, T_OTHER = DeviceTypes.T_OTHER, T_TUNER = DeviceTypes.T_TUNER, T_AV = DeviceTypes.T_AV;

    private Inputs() { }

    static String typeKey(String id) { return "in_type_" + id; }

    static void setUserType(Context c, String inputId, int type) {
        SharedPreferences.Editor e = c.getSharedPreferences("omni", Context.MODE_PRIVATE).edit();
        if (type == T_UNKNOWN) e.remove(typeKey(inputId)); else e.putInt(typeKey(inputId), type);
        e.apply();
    }

    /** Reads the inputs. Call off the main thread. Returns an empty list when the API is not available. */
    static List<Entry> query(Context c) {
        List<Entry> out = new ArrayList<>();
        TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (m == null) return out;
        SharedPreferences sp = c.getSharedPreferences("omni", Context.MODE_PRIVATE);
        List<TvInputInfo> all;
        try { all = m.getTvInputList(); } catch (RuntimeException e) { return out; }
        Map<String, Entry> byId = new HashMap<>();
        List<TvInputInfo> children = new ArrayList<>();
        for (TvInputInfo i : all) {
            if (!i.isPassthroughInput()) continue;            // channel apps (their own content) are not devices
            if (i.getParentId() != null) { children.add(i); continue; }
            Entry e = new Entry();
            e.id = i.getId();
            e.tvType = i.getType();
            e.hdmi = e.tvType == TvInputInfo.TYPE_HDMI;
            e.portLabel = String.valueOf(i.loadLabel(c));
            CharSequence custom = i.loadCustomLabel(c);
            e.customLabel = custom == null ? null : custom.toString();
            e.state = m.getInputState(e.id);
            byId.put(e.id, e);
            out.add(e);
        }
        // a hot-plugged CEC device appears as a child of the port it is on; its label is the device's own name
        for (TvInputInfo i : children) {
            Entry parent = byId.get(i.getParentId());
            if (parent == null) continue;
            String name = String.valueOf(i.loadLabel(c));
            if (!name.isEmpty() && !name.equals(parent.portLabel)) parent.cecName = name;
            parent.childId = i.getId();
            // the child's own state is more specific than the port's when the device is switched on
            int cs = m.getInputState(i.getId());
            if (cs < parent.state || parent.state == TvInputManager.INPUT_STATE_DISCONNECTED) parent.state = cs;
        }
        for (Entry e : out) {
            int user = sp.getInt(typeKey(e.id), T_UNKNOWN);
            if (user != T_UNKNOWN) { e.type = user; e.typeFromUser = true; continue; }
            int g = DeviceTypes.guessType(e.cecName != null ? e.cecName : e.customLabel);
            if (g != T_UNKNOWN) { e.type = g; e.typeGuessed = true; continue; }
            if (!e.hdmi) e.type = e.tvType == TvInputInfo.TYPE_TUNER ? T_TUNER : T_AV;
        }
        Collections.sort(out, (a, b) -> {
            if (a.hdmi != b.hdmi) return a.hdmi ? -1 : 1;
            return a.portLabel.compareToIgnoreCase(b.portLabel);
        });
        return out;
    }

    /** The documented way to ask the system TV app to show a pass-through input (HDMI, composite...). */
    static Intent switchIntent(String inputId) {
        Uri u = TvContract.buildChannelUriForPassthroughInput(inputId);
        return new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
}
