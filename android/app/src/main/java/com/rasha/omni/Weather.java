package com.rasha.omni;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Current weather from Open-Meteo (open-meteo.com): documented HTTP API, no key needed for non-commercial use, CC BY 4.0 data (attribution is
 * shown in the weather sheet). The location is chosen by the user (no location permission is requested). Readings are cached with their time;
 * a refresh is at most every 30 minutes on its own and at most once a minute when the user asks. Failure keeps the last reading and says how old it is.
 */
final class Weather {
    static final class Place {
        final String name;
        final double lat, lon;
        Place(String n, double a, double o) { name = n; lat = a; lon = o; }
    }

    /** Coordinates are public city-centre values. The user picks one; nothing is guessed from the network. */
    static final Place[] PLACES = {
            new Place("Civate", 45.82808, 9.34294),      // Open-Meteo geocoder; the owner's town and the default
            new Place("Rome", 41.9028, 12.4964), new Place("Milan", 45.4642, 9.1900), new Place("Naples", 40.8518, 14.2681),
            new Place("Turin", 45.0703, 7.6869), new Place("Bologna", 44.4949, 11.3426), new Place("Florence", 43.7696, 11.2558),
            new Place("Tirana", 41.3275, 19.8187), new Place("Durrës", 41.3246, 19.4565), new Place("Pristina", 42.6629, 21.1655),
            new Place("Prizren", 42.2139, 20.7397)};

    static final class Reading {
        double temp = Double.NaN;
        int kind = WeatherCodes.UNKNOWN;
        boolean day = true;
        long fetched;                 // wall clock when we got it
        String place = "";
        boolean valid() { return fetched > 0 && !Double.isNaN(temp); }
    }

    /** The whole forecast for the weather app, read from the last stored response (so it opens instantly and works offline). */
    static final class Full {
        long fetched;
        String place = "";
        double temp, feels, wind, windDir, pressure, precip;
        int code, humidity, cloud;
        boolean day = true;
        final java.util.List<int[]> hours = new java.util.ArrayList<>();           // {hourOfDay, weatherCode, precipitation %, isDay}
        final java.util.List<Double> hourTemp = new java.util.ArrayList<>();
        final java.util.List<String> dates = new java.util.ArrayList<>();           // yyyy-MM-dd
        final java.util.List<int[]> dayCodes = new java.util.ArrayList<>();         // {weatherCode, precipitation %}
        final java.util.List<double[]> dayRange = new java.util.ArrayList<>();      // {min, max, uv}
        final java.util.List<String> sunrise = new java.util.ArrayList<>(), sunset = new java.util.ArrayList<>();
        boolean valid() { return fetched > 0 && !dates.isEmpty(); }
    }

    static Full full(Context c) {
        Full f = new Full();
        try {
            SharedPreferences p = prefs(c);
            String raw = p.getString("wx_full", "");
            if (raw.isEmpty()) return f;
            JSONObject d = new JSONObject(raw);
            JSONObject cur = d.getJSONObject("current");
            f.temp = cur.getDouble("temperature_2m");
            f.feels = cur.optDouble("apparent_temperature", f.temp);
            f.humidity = cur.optInt("relative_humidity_2m", -1);
            f.code = cur.optInt("weather_code", -1);
            f.day = cur.optInt("is_day", 1) == 1;
            f.wind = cur.optDouble("wind_speed_10m", 0);
            f.windDir = cur.optDouble("wind_direction_10m", 0);
            f.pressure = cur.optDouble("surface_pressure", 0);
            f.precip = cur.optDouble("precipitation", 0);
            f.cloud = cur.optInt("cloud_cover", -1);
            String now = cur.optString("time", "");
            JSONObject h = d.getJSONObject("hourly");
            org.json.JSONArray ht = h.getJSONArray("time"), hc = h.getJSONArray("weather_code"), hp = h.getJSONArray("precipitation_probability"), hT = h.getJSONArray("temperature_2m");
            String hourNow = now.length() >= 13 ? now.substring(0, 13) : "";
            int start = 0;
            for (int i = 0; i < ht.length(); i++) if (ht.getString(i).startsWith(hourNow)) { start = i; break; }
            JSONObject dd = d.getJSONObject("daily");
            org.json.JSONArray dt = dd.getJSONArray("time"), dc = dd.getJSONArray("weather_code"), dmx = dd.getJSONArray("temperature_2m_max"), dmn = dd.getJSONArray("temperature_2m_min"),
                    sr = dd.getJSONArray("sunrise"), ss = dd.getJSONArray("sunset"), uv = dd.getJSONArray("uv_index_max"), pp = dd.getJSONArray("precipitation_probability_max");
            for (int i = 0; i < dt.length(); i++) {
                f.dates.add(dt.getString(i));
                f.dayCodes.add(new int[]{dc.optInt(i, -1), pp.optInt(i, 0)});
                f.dayRange.add(new double[]{dmn.optDouble(i), dmx.optDouble(i), uv.optDouble(i, -1)});
                f.sunrise.add(sr.optString(i, ""));
                f.sunset.add(ss.optString(i, ""));
            }
            for (int i = start; i < ht.length() && f.hours.size() < 24; i++) {
                String t = ht.getString(i);
                int hour = Integer.parseInt(t.substring(11, 13));
                int day = 1;
                int di = f.dates.indexOf(t.substring(0, 10));
                if (di >= 0) {
                    String r = WeatherFormat.clock(f.sunrise.get(di)), s2 = WeatherFormat.clock(f.sunset.get(di));
                    String hhmm = t.substring(11, 16);
                    if (!r.isEmpty() && !s2.isEmpty()) day = hhmm.compareTo(r) >= 0 && hhmm.compareTo(s2) < 0 ? 1 : 0;
                }
                f.hours.add(new int[]{hour, hc.optInt(i, -1), hp.optInt(i, 0), day});
                f.hourTemp.add(hT.optDouble(i));
            }
            f.fetched = p.getLong("wx_full_at", 0);
            f.place = p.getString("wx_full_place", "");
        } catch (Exception e) {
            return new Full();
        }
        return f;
    }

    interface Listener { void changed(Reading r, boolean failed); }

    private static final long AUTO_MS = 30 * 60 * 1000L, MANUAL_MS = 60 * 1000L;
    private static long lastTry;

    private Weather() { }

    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("omniptv", Context.MODE_PRIVATE); }

    static Place place(Context c) {
        String n = prefs(c).getString("wx_place", "Civate");     // default until the user picks or removes one
        for (Place p : PLACES) if (p.name.equals(n)) return p;
        return null;
    }

    static void setPlace(Context c, Place p) {
        prefs(c).edit().putString("wx_place", p == null ? "none" : p.name).remove("wx_json").apply();
        lastTry = 0;
    }

    static Reading cached(Context c) {
        Reading r = new Reading();
        try {
            JSONObject j = new JSONObject(prefs(c).getString("wx_json", "{}"));
            if (!j.has("t")) return r;
            r.temp = j.getDouble("t");
            r.kind = j.optInt("k", WeatherCodes.UNKNOWN);
            r.day = j.optBoolean("d", true);
            r.fetched = j.optLong("at");
            r.place = j.optString("p");
        } catch (Exception ignored) { }
        return r;
    }

    /** Fetches if due (or if forced and a minute has passed). Background thread; the listener is called on it. */
    static void refresh(final Context ctx, final boolean force, final Listener l) {
        final Context app = ctx.getApplicationContext();
        final Place p = place(app);
        Reading old = cached(app);
        long now = System.currentTimeMillis();
        if (p == null) return;
        long gap = force ? MANUAL_MS : AUTO_MS;
        boolean haveFull = prefs(app).contains("wx_full");
        if (now - lastTry < gap || (!force && haveFull && old.valid() && now - old.fetched < AUTO_MS && p.name.equals(old.place))) return;
        lastTry = now;
        new Thread(() -> {
            try {
                String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m,wind_direction_10m,surface_pressure,precipitation,cloud_cover&hourly=temperature_2m,weather_code,precipitation_probability&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max&timezone=auto&forecast_days=7", p.lat, p.lon);
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setConnectTimeout(5000);
                c.setReadTimeout(6000);
                String body;
                try (InputStream in = c.getInputStream()) {
                    ByteArrayOutputStream b = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0 && b.size() < 120000) b.write(buf, 0, n);
                    body = b.toString("UTF-8");
                } finally {
                    c.disconnect();
                }
                JSONObject cur = new JSONObject(body).getJSONObject("current");
                JSONObject j = new JSONObject();
                j.put("t", cur.getDouble("temperature_2m"));
                j.put("k", WeatherCodes.kind(cur.getInt("weather_code")));
                j.put("d", cur.optInt("is_day", 1) == 1);
                j.put("at", System.currentTimeMillis());
                j.put("p", p.name);
                prefs(app).edit().putString("wx_json", j.toString()).putString("wx_full", body).putLong("wx_full_at", System.currentTimeMillis()).putString("wx_full_place", p.name).apply();
                l.changed(cached(app), false);
            } catch (Exception e) {
                l.changed(cached(app), true);
            }
        }, "weather").start();
    }
}
