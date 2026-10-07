package dev.dipa.widgets;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Small deterministic rules shared by widgets and the sensor collector. */
public final class Logic {
    private Logic() {}
    public static long daysUntil(String today, String target) {
        return ChronoUnit.DAYS.between(LocalDate.parse(today), LocalDate.parse(target));
    }
    public static long stepDelta(long previous, long current) {
        // A reboot or counter reset must never subtract already recorded steps.
        return previous < 0 || current < previous ? 0 : current - previous;
    }
    public static String duration(long millis) {
        long minutes = Math.max(0, millis) / 60000;
        return minutes >= 60 ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m";
    }
    public static String weather(int code) {
        if (code < 0) return "";
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code <= 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }
    public static String weather(int code, boolean day) {
        if(code==0)return day?"Sunny":"Clear";
        if(code==1)return day?"Mostly sunny":"Mostly clear";
        return weather(code);
    }
}
