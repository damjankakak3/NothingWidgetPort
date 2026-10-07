package dev.dipa.widgets;

import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.app.usage.UsageEvents;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

public final class Data {
    private Data() {}
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("widgets", Context.MODE_PRIVATE); }
    public static String today() { return LocalDate.now().toString(); }
    public static int steps(Context c, String day) { return prefs(c).getInt("steps:" + day, 0); }
    public static int streak(Context c) {
        int count = 0, goal = prefs(c).getInt("step_goal", 6000);
        LocalDate d = LocalDate.now();
        if (steps(c, d.toString()) < goal) d = d.minusDays(1);
        for (int i = 0; i < 365 && steps(c, d.minusDays(i).toString()) >= goal; i++) count++;
        return count;
    }
    public static boolean hasUsage(Context c) {
        AppOpsManager a = c.getSystemService(AppOpsManager.class);
        return a.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }
    public static long screenTime(Context c) {
        if (!hasUsage(c)) return -1;
        long end = System.currentTimeMillis();
        long start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        // Screen-interactive events measure device screen time without double counting split-screen apps.
        UsageEvents events = c.getSystemService(UsageStatsManager.class).queryEvents(start, end);
        if (events == null) return 0;
        UsageEvents.Event e = new UsageEvents.Event();
        long opened = -1, total = 0;
        boolean first = true;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            int type = e.getEventType();
            if (type != UsageEvents.Event.SCREEN_INTERACTIVE && type != UsageEvents.Event.SCREEN_NON_INTERACTIVE) continue;
            if (type == UsageEvents.Event.SCREEN_INTERACTIVE) { if (opened < 0) opened = e.getTimeStamp(); }
            else { if (opened >= 0) total += e.getTimeStamp() - opened; else if (first) total += e.getTimeStamp() - start; opened = -1; }
            first = false;
        }
        if (opened >= 0) total += end - opened;
        return Math.max(0, total);
    }
    public static String nextEvent(Context c) {
        if (c.checkSelfPermission("android.permission.READ_CALENDAR") != 0) return "Tap to enable calendar";
        long now = System.currentTimeMillis();
        Uri.Builder b = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(b, now);
        android.content.ContentUris.appendId(b, now + 86400000L);
        try (Cursor cursor = c.getContentResolver().query(b.build(),
                new String[]{CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN},
                null, null, CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null && cursor.moveToFirst()) {
                String time = android.text.format.DateFormat.getTimeFormat(c).format(new Date(cursor.getLong(1)));
                return time + "  " + cursor.getString(0);
            }
        } catch (Exception ex) { Log.w("NothingWidget", "Calendar unavailable", ex); }
        return "No upcoming events";
    }
    public static JSONObject getJson(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "NothingWidget/0.3.2");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Weather server returned " + connection.getResponseCode());
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) >= 0) {
                    if (out.size() + n > 1024 * 1024) throw new IOException("Weather response too large");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
    }
    public static String findCity(Context c, String query) throws Exception {
        JSONObject response = getJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" + URLEncoder.encode(query, "UTF-8"));
        JSONArray results = response.optJSONArray("results");
        if (results == null || results.length() == 0) throw new IOException("City not found");
        JSONObject result = results.getJSONObject(0);
        String label = result.getString("name") + ", " + result.optString("country");
        prefs(c).edit().putString("city", label).putString("lat", result.getString("latitude"))
            .putString("lon", result.getString("longitude")).remove("weather_time").apply();
        return label;
    }
    public static boolean weatherStale(Context c) {
        long updated = prefs(c).getLong("weather_time", 0), now = System.currentTimeMillis();
        return updated <= 0 || now < updated || now - updated >= 3600000L;
    }
    public static synchronized void refreshWeather(Context c, boolean force) throws Exception {
        SharedPreferences p = prefs(c);
        if (!p.contains("lat")) return;
        if (!force && !weatherStale(c)) return;
        String lat = p.getString("lat", "0"), lon = p.getString("lon", "0");
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
            + "&longitude=" + lon
            + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day"
            + "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=6&timezone=auto";
        JSONObject response = getJson(url), current = response.getJSONObject("current"), daily = response.getJSONObject("daily");
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Weather job stopped");
        // A city selected during this request must not receive the previous city's forecast.
        if (!lat.equals(p.getString("lat", "")) || !lon.equals(p.getString("lon", ""))) return;
        SharedPreferences.Editor edit = p.edit().putString("weather", current.toString()).putString("forecast", daily.toString())
            .putLong("weather_time", System.currentTimeMillis()).remove("weather_error");
        edit.apply();
    }
    public static JSONObject weather(Context c) {
        try { return new JSONObject(prefs(c).getString("weather", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
}
