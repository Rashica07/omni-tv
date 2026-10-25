package com.rasha.omni;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;

/** The weather app. Opens on the stored forecast instantly and refreshes in the background (at most every 30 minutes on its own, once a minute when asked). */
public class WeatherActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private WeatherView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new WeatherView(this, new WeatherView.Listener() {
            @Override public void refresh() { fetch(true); }
            @Override public void pickPlace(Weather.Place p) { Weather.setPlace(WeatherActivity.this, p); show("Loading " + p.name + "…"); fetch(true); }
            @Override public void close() { finish(); }
        });
        setContentView(view);
        view.requestFocus();
        show("");
        if (Weather.place(this) == null) view.post(view::openPicker);
    }

    @Override protected void onResume() {
        super.onResume();
        fetch(false);
    }

    /** Reads and parses the stored forecast on a worker thread; the screen only receives the finished result. */
    private void show(final String status) {
        final Weather.Place pl = Weather.place(this);
        new Thread(() -> {
            final Weather.Full full = Weather.full(WeatherActivity.this);
            ui.post(() -> view.setData(full, pl == null ? "" : pl.name, status));
        }, "wx-parse").start();
    }

    private void fetch(final boolean force) {
        Weather.refresh(this, force, (r, failed) -> ui.post(() -> show(failed ? "Couldn't reach the weather service" : "")));
    }
}
