package com.rasha.omni;

import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Watches the devices around the TV and reports only real changes through {@link EventRules}: speakers and soundbars (Bluetooth, HDMI ARC/eARC, USB),
 * game controllers and keyboards connecting over Bluetooth, Chromecasts that appear on the network for the first time, and casting to this TV
 * (Chromecast built-in) when notification access is allowed. Pairing itself stays in the system's Bluetooth settings: accepting a pairing needs system privileges.
 */
final class DeviceWatch {
    interface Sink { void event(String[] headingAndDetail); }

    static final String CAST_RECEIVER = "com.google.android.apps.mediashell";

    private final Context ctx;
    private final Sink sink;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private boolean firstAudioBatch = true;
    private NsdManager nsd;
    private NsdManager.DiscoveryListener discovery;
    private final ArrayDeque<NsdServiceInfo> toResolve = new ArrayDeque<>();
    private boolean resolving;
    private final Set<String> castSessions = new HashSet<>();
    private MediaSessionManager sessions;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsCb;

    DeviceWatch(Context c, Sink s) {
        ctx = c.getApplicationContext();
        sink = s;
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    void start() {
        if (audio != null) {
            audio.registerAudioDeviceCallback(audioCb, ui);
            ui.postDelayed(() -> firstAudioBatch = false, 1500);           // the callback first lists what is already there: that is not news
        }
        IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        try { ctx.registerReceiver(bt, f); } catch (RuntimeException ignored) { }
        watchSessions();
        ui.postDelayed(scan, 30_000);                                     // first network scan after start-up settles
    }

    void stop() {
        if (audio != null) audio.unregisterAudioDeviceCallback(audioCb);
        try { ctx.unregisterReceiver(bt); } catch (RuntimeException ignored) { }
        stopDiscovery();
        ui.removeCallbacks(scan);
        if (sessions != null && sessionsCb != null) try { sessions.removeOnActiveSessionsChangedListener(sessionsCb); } catch (RuntimeException ignored) { }
    }

    // ------------------------------------------------------------ speakers, soundbars, headphones

    private final AudioDeviceCallback audioCb = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { report(added, true); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { report(removed, false); }
    };

    private void report(AudioDeviceInfo[] list, boolean added) {
        for (AudioDeviceInfo d : list) {
            if (!d.isSink()) continue;
            String[] e = EventRules.audio(d.getProductName() == null ? null : d.getProductName().toString(), d.getType(), added, firstAudioBatch);
            if (e != null) { OmniLog.state("device audio " + (added ? "+" : "-") + " type=" + d.getType()); sink.event(e); }
        }
    }

    /** Where sound plays now, for the Control Center. */
    String soundOutput() {
        if (audio == null) return "TV speakers";
        AudioDeviceInfo[] outs = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        int[] types = new int[outs.length];
        String[] names = new String[outs.length];
        for (int i = 0; i < outs.length; i++) { types[i] = outs[i].getType(); names[i] = outs[i].getProductName() == null ? "" : outs[i].getProductName().toString(); }
        return ControlRows.soundOutput(types, names);
    }

    // ------------------------------------------------------------ Bluetooth controllers and keyboards

    private final BroadcastReceiver bt = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (d == null) return;
            String name;
            try { name = d.getName(); } catch (SecurityException e) { return; }
            BluetoothClass cls = d.getBluetoothClass();
            int dc = cls == null ? 0 : cls.getDeviceClass();
            boolean peripheral = cls != null && cls.getMajorDeviceClass() == BluetoothClass.Device.Major.PERIPHERAL;
            String[] e = EventRules.peripheral(name, peripheral && (dc & 0x3C) == 0x08, peripheral && (dc & 0x40) != 0);
            if (e != null) { OmniLog.state("device bt peripheral"); sink.event(e); }
        }
    };

    // ------------------------------------------------------------ Chromecasts on the network

    private final Runnable scan = new Runnable() {
        @Override public void run() {
            discover();
            ui.postDelayed(this::stopAndReschedule, 15_000);
        }
        private void stopAndReschedule() { stopDiscovery(); ui.postDelayed(scan, 15 * 60_000); }
    };

    private SharedPreferences prefs() { return ctx.getSharedPreferences("omni", Context.MODE_PRIVATE); }

    private void discover() {
        try {
            nsd = (NsdManager) ctx.getSystemService(Context.NSD_SERVICE);
            if (nsd == null) return;
            discovery = new NsdManager.DiscoveryListener() {
                @Override public void onStartDiscoveryFailed(String t, int e) { discovery = null; }
                @Override public void onStopDiscoveryFailed(String t, int e) { }
                @Override public void onDiscoveryStarted(String t) { }
                @Override public void onDiscoveryStopped(String t) { }
                @Override public void onServiceFound(NsdServiceInfo s) { ui.post(() -> { toResolve.add(s); resolveNext(); }); }
                @Override public void onServiceLost(NsdServiceInfo s) { }
            };
            nsd.discoverServices("_googlecast._tcp", NsdManager.PROTOCOL_DNS_SD, discovery);
        } catch (RuntimeException e) { discovery = null; }
    }

    private void stopDiscovery() {
        if (nsd != null && discovery != null) try { nsd.stopServiceDiscovery(discovery); } catch (RuntimeException ignored) { }
        discovery = null;
    }

    /** NsdManager resolves one service at a time; the friendly name ("fn") is in the TXT record. */
    private void resolveNext() {
        if (resolving || toResolve.isEmpty() || nsd == null) return;
        resolving = true;
        NsdServiceInfo s = toResolve.poll();
        try {
            nsd.resolveService(s, new NsdManager.ResolveListener() {
                @Override public void onResolveFailed(NsdServiceInfo i, int e) { ui.post(() -> { resolving = false; resolveNext(); }); }
                @Override public void onServiceResolved(NsdServiceInfo i) {
                    byte[] fn = i.getAttributes() == null ? null : i.getAttributes().get("fn");
                    String name = fn != null ? new String(fn, java.nio.charset.StandardCharsets.UTF_8) : i.getServiceName();
                    ui.post(() -> { foundCast(name); resolving = false; resolveNext(); });
                }
            });
        } catch (RuntimeException e) { resolving = false; }
    }

    private void foundCast(String name) {
        SharedPreferences sp = prefs();
        Set<String> known = new HashSet<>(sp.getStringSet("known_casts", new HashSet<>()));
        boolean baseline = !sp.getBoolean("casts_baseline", false);
        String[] e = EventRules.found(name, "_googlecast._tcp", known, baseline);
        sp.edit().putStringSet("known_casts", known).apply();
        if (baseline) ui.postDelayed(() -> prefs().edit().putBoolean("casts_baseline", true).apply(), 14_000);    // the whole first scan is the baseline
        if (e != null) { OmniLog.state("device cast found"); sink.event(e); }
    }

    // ------------------------------------------------------------ casting to this TV

    static ComponentName listener(Context c) { return new ComponentName(c, MediaListener.class); }

    static boolean sessionsAllowed(Context c) {
        String s = android.provider.Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(listener(c).flattenToString());
    }

    void watchSessions() {
        if (sessionsCb != null || !sessionsAllowed(ctx)) return;
        try {
            sessions = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            sessionsCb = this::sessionsChanged;
            sessions.addOnActiveSessionsChangedListener(sessionsCb, listener(ctx), ui);
            sessionsChanged(sessions.getActiveSessions(listener(ctx)));
            OmniLog.state("device sessions watched");
        } catch (SecurityException | NullPointerException e) { sessionsCb = null; }
    }

    private boolean firstSessions = true;

    private void sessionsChanged(List<MediaController> list) {
        Set<String> now = new HashSet<>();
        if (list != null) for (MediaController m : list) {
            if (!CAST_RECEIVER.equals(m.getPackageName())) continue;
            String key = m.getSessionToken().toString();
            now.add(key);
            if (!castSessions.contains(key) && !firstSessions) {
                MediaMetadata md = m.getMetadata();
                String title = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_TITLE);
                String app = md == null ? null : md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                OmniLog.state("device casting");
                sink.event(EventRules.casting(title, app));
            }
        }
        firstSessions = false;
        castSessions.clear();
        castSessions.addAll(now);
    }
}
