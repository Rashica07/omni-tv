package com.rasha.omni;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/**
 * The TV's own connectivity, from ConnectivityManager. Transport (Wi-Fi/Ethernet) and the platform's "validated" flag (it actually reached the
 * internet) are reported separately. Signal strength, SSID and IP address are not shown: they need location permission or are not needed.
 */
final class NetState {
    interface Listener { void changed(); }

    int transport = NetKind.NONE;
    boolean validated;
    private final ConnectivityManager cm;
    private Listener listener;
    private final ConnectivityManager.NetworkCallback cb = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) { read(); }
        @Override public void onLost(Network n) { read(); }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) { read(); }
    };

    NetState(Context c) {
        cm = (ConnectivityManager) c.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        read();
    }

    void start(Listener l) {
        listener = l;
        try { cm.registerDefaultNetworkCallback(cb); } catch (RuntimeException ignored) { }
    }

    void stop() {
        try { cm.unregisterNetworkCallback(cb); } catch (RuntimeException ignored) { }
        listener = null;
    }

    private void read() {
        int t = NetKind.NONE;
        boolean v = false;
        try {
            Network n = cm.getActiveNetwork();
            NetworkCapabilities c = n == null ? null : cm.getNetworkCapabilities(n);
            if (c != null) {
                t = NetKind.transport(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI), c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET), true);
                v = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            }
        } catch (RuntimeException ignored) { }
        boolean changed = t != transport || v != validated;
        transport = t;
        validated = v;
        if (changed && listener != null) listener.changed();
    }

    String describe() { return NetKind.describe(transport, validated); }
}
