package dev.dipa.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Original Weather 3.1.7 primary compositions, drawn from existing cached data. */
public final class WeatherArtwork {
    private static final int MAX_EDGE = 768;
    private static Typeface dots;
    private static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private static final Typeface THIN = Typeface.create("sans-serif-thin", Typeface.NORMAL);
    private static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private WeatherArtwork() {}

    public static Bitmap render(Context c, String kind, int widthPx, int heightPx) {
        return render(c, kind, widthPx, heightPx, 0);
    }

    public static Bitmap render(Context c, String kind, int widthPx, int heightPx, int page) {
        boolean circle = kind.equals("WeatherCircle"), square = kind.equals("WeatherSquare");
        boolean combo = kind.equals("WeatherCombo"), forecast = kind.equals("WeatherWide");
        if (!circle && !square && !combo && !forecast) return null;
        int requestedW = Math.max(1, widthPx), requestedH = Math.max(1, heightPx);
        float bound = Math.min(1f, MAX_EDGE / (float) Math.max(requestedW, requestedH));
        int width = Math.max(1, Math.round(requestedW * bound)), height = Math.max(1, Math.round(requestedH * bound));
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(image);
        boolean compact = forecast && width / (float) height > 3f;
        float aspect = forecast ? 945f / (compact ? 205f : 452f) : 1f;
        float cardH = Math.min(height, width / aspect), cardW = cardH * aspect;
        canvas.translate((width - cardW) / 2f, (height - cardH) / 2f);
        int ink = color(c, "widget_icon_color", dark(c) ? 0xfff5f3f0 : 0xff262523);
        int background = color(c, "widget_style_circle_ring_bg_color", dark(c) ? 0xff262523 : 0xfff5f3f0);
        int secondary = color(c, "widget_rectangle_low_temp_txt_color", 0xff888580);
        SharedPreferences prefs = Data.prefs(c);
        JSONObject weather = Data.weather(c), daily = json(prefs.getString("forecast", "{}"));
        double current = weather.optDouble("temperature_2m", Double.NaN);
        String city = prefs.getString("city", "");
        boolean noCity = city == null || city.trim().isEmpty();
        boolean available = !noCity && Double.isFinite(current);
        String message = string(c, noCity ? "widget_tap_to_add_city" : "weather_widget_no_connection",
                noCity ? "tap to add city" : "No connection");
        if (combo) {
            if (!available) drawEmptyCombo(c, canvas, cardW, background, ink, message, noCity);
            else drawCombo(c, canvas, cardW, background, ink, current, weather, daily, prefs);
            return image;
        }
        Paint fill = paint(REGULAR, 1, background);
        Path outline = new Path();
        RectF card = new RectF(0, 0, cardW, cardH);
        if (circle) {
            canvas.drawCircle(cardW / 2f, cardH / 2f, cardH / 2f, fill);
            outline.addCircle(cardW / 2f, cardH / 2f, cardH / 2f, Path.Direction.CW);
        } else {
            float radius = forecast ? cardW * 20f / 360f : cardW * 20f / 172f;
            canvas.drawRoundRect(card, radius, radius, fill);
            outline.addRoundRect(card, radius, radius, Path.Direction.CW);
        }
        canvas.clipPath(outline);
        if (!available) {
            float scale = forecast ? Math.min(cardW / 945f, cardH / (compact ? 205f : 452f)) : cardW / 452f;
            centeredMessage(c, canvas, message, new RectF(cardW * .093f, 0, cardW * .907f, cardH),
                    53f * scale, ink);
        } else if (circle) {
            int selected = Math.floorMod(page, 3);
            if (selected == 0) {
                float side = cardW * 231f / 452f;
                icon(c, canvas, weather.optInt("weather_code", -1), weather.optInt("is_day", 1) != 0,
                        new RectF((cardW - side) / 2f, (cardH - side) / 2f,
                                (cardW + side) / 2f, (cardH + side) / 2f), ink);
            } else if (selected == 1) {
                Paint value = paint(THIN, cardW * 147f / 452f, ink);
                long temperature = converted(current, prefs);
                float left = cardW * temperatureGuideline(temperature, false);
                text(canvas, temperature + "°", left, centeredBaseline(value, cardH / 2f),
                        cardW - left - cardW * 12f / 452f, value, Paint.Align.LEFT);
            } else drawRangePage(c, canvas, cardW, true, weather, daily, prefs, ink);
        } else if (square) {
            if (Math.floorMod(page, 2) == 0) drawSquare(c, canvas, cardW, current, city, weather, prefs, ink);
            else drawRangePage(c, canvas, cardW, false, weather, daily, prefs, ink);
        }
        else drawForecast(c, canvas, cardW, cardH, compact, current, city, weather, daily, prefs, ink, secondary);
        return image;
    }

    private static void drawSquare(Context c, Canvas canvas, float side, double current, String city,
            JSONObject weather, SharedPreferences prefs, int ink) {
        float scale = side / 452f;
        Paint temperature = paint(LIGHT, 63f * scale, ink);
        textTop(canvas, temperature(current, prefs), side / 2f, 63f * scale, side - 84f * scale,
                temperature, Paint.Align.CENTER);
        float iconSide = 116f * scale;
        icon(c, canvas, weather.optInt("weather_code", -1), weather.optInt("is_day", 1) != 0,
                new RectF((side - iconSide) / 2f, (side - iconSide) / 2f,
                        (side + iconSide) / 2f, (side + iconSide) / 2f), ink);
        Paint name = paint(LIGHT, 37f * scale, ink);
        float baseline = side - 63f * scale - name.getFontMetrics().bottom;
        text(canvas, cityName(city), side / 2f, baseline, side - 84f * scale, name, Paint.Align.CENTER);
    }

    private static void drawEmptyCombo(Context c, Canvas canvas, float side, int background, int ink,
            String message, boolean noCity) {
        float pillH = side * 205f / 452f;
        RectF top = new RectF(0, 0, side, pillH), bottom = new RectF(0, side - pillH, side, side);
        Paint fill = paint(REGULAR, 1, background);
        canvas.drawRoundRect(top, pillH / 2f, pillH / 2f, fill);
        canvas.drawRoundRect(bottom, pillH / 2f, pillH / 2f, fill);
        centeredMessage(c, canvas, message, new RectF(side * 42f / 452f, 0,
                side - side * 42f / 452f, pillH), side * 53f / 452f, ink);
        float iconSide = side * 116f / 452f;
        drawable(c, canvas, noCity ? "ic_widget_location_permission_needed" : "ic_widget_no_connection",
                new RectF((side - iconSide) / 2f,
                bottom.centerY() - iconSide / 2f, (side + iconSide) / 2f,
                bottom.centerY() + iconSide / 2f), ink);
    }

    private static void drawCombo(Context c, Canvas canvas, float side, int background, int ink, double current,
            JSONObject weather, JSONObject daily, SharedPreferences prefs) {
        float scale = side / 452f, diameter = 205f * scale;
        Paint fill = paint(REGULAR, 1, background);
        canvas.drawRoundRect(new RectF(0, 0, side, diameter), diameter / 2f, diameter / 2f, fill);
        canvas.drawCircle(diameter / 2f, side - diameter / 2f, diameter / 2f, fill);
        canvas.drawCircle(side - diameter / 2f, side - diameter / 2f, diameter / 2f, fill);
        float iconSide = 116f * scale, iconLeft = 45f * scale;
        icon(c, canvas, weather.optInt("weather_code", -1), weather.optInt("is_day", 1) != 0,
                new RectF(iconLeft, (diameter - iconSide) / 2f, iconLeft + iconSide,
                        (diameter + iconSide) / 2f), ink);
        String description = description(weather);
        RectF descriptionBox = new RectF(iconLeft + iconSide + 21f * scale, 0, side - 45f * scale, diameter);
        Paint descriptionPaint = paint(LIGHT, 37f * scale, ink);
        if (descriptionPaint.measureText(description) > descriptionBox.width() * 2f)
            descriptionPaint.setTextSize(32f * scale);
        paragraph(canvas, description, descriptionBox, descriptionPaint, 2);
        Paint value = paint(LIGHT, 63f * scale, ink);
        long number = converted(current, prefs);
        float guideline = temperatureGuideline(number, true);
        float y = side - diameter / 2f;
        text(canvas, number + "°", diameter * guideline, centeredBaseline(value, y),
                diameter * (1 - guideline), value, Paint.Align.LEFT);
        double high = arrayValue(daily, "temperature_2m_max", 0), low = arrayValue(daily, "temperature_2m_min", 0);
        float rangeLeft = side - diameter;
        Paint range = paint(LIGHT, 37f * scale, ink);
        String highText = temperature(high, prefs), lowText = temperature(low, prefs);
        float arrow = 53f * scale, rowHeight = lineHeight(range), gap = scale;
        float startY = y - rowHeight - gap / 2f;
        float textX = rangeLeft + diameter * rangeGuideline(high, low, prefs, true);
        float startX = textX - arrow;
        arrow(c, canvas, true, new RectF(startX, startY + (rowHeight - arrow) / 2f,
                startX + arrow, startY + (rowHeight + arrow) / 2f), ink);
        text(canvas, highText, startX + arrow, centeredBaseline(range, startY + rowHeight / 2f),
                diameter - arrow - 12f * scale, range, Paint.Align.LEFT);
        arrow(c, canvas, false, new RectF(startX, startY + 1.5f * rowHeight + gap - arrow / 2f,
                startX + arrow, startY + 1.5f * rowHeight + gap + arrow / 2f), ink);
        text(canvas, lowText, startX + arrow, centeredBaseline(range, startY + 1.5f * rowHeight + gap),
                diameter - arrow - 12f * scale, range, Paint.Align.LEFT);
    }

    private static void drawRangePage(Context c, Canvas canvas, float side, boolean circle,
            JSONObject weather, JSONObject daily, SharedPreferences prefs, int ink) {
        float scale = side / 452f, arrowSide = 106f * scale;
        double high = arrayValue(daily, "temperature_2m_max", 0), low = arrayValue(daily, "temperature_2m_min", 0);
        Paint range = paint(LIGHT, 84f * scale, ink), description = paint(LIGHT, 37f * scale, ink);
        float rowHeight = lineHeight(range), gap = scale;
        float descriptionHeight = circle ? 0 : lineHeight(description) + 42f * scale;
        float top = (side - rowHeight * 2f - gap - descriptionHeight) / 2f;
        float textX = side * rangeGuideline(high, low, prefs, false);
        for (int index = 0; index < 2; index++) {
            float centerY = top + rowHeight / 2f + index * (rowHeight + gap);
            arrow(c, canvas, index == 0, new RectF(textX - arrowSide, centerY - arrowSide / 2f,
                    textX, centerY + arrowSide / 2f), ink);
            text(canvas, temperature(index == 0 ? high : low, prefs), textX, centeredBaseline(range, centerY),
                    side - textX - 42f * scale, range, Paint.Align.LEFT);
        }
        if (!circle) {
            float descriptionTop = top + 2f * rowHeight + gap + 42f * scale;
            textTop(canvas, description(weather), side / 2f,
                    descriptionTop, side - 84f * scale, description, Paint.Align.CENTER);
        }
    }

    /** Exact ce.a.u/A optical temperature offsets from the original Weather provider. */
    private static float temperatureGuideline(long temperature, boolean combo) {
        int bits = Math.abs(temperature) >= 100 ? combo ? 0x3e4b7cb8 : 0x3e53594d
                : Math.abs(temperature) >= 10 ? combo ? 0x3e93b13b : 0x3e97d05f
                : combo ? 0x3ebe5be6 : 0x3ec5f418;
        float offset = temperature < 0 ? Float.intBitsToFloat(combo ? 0x3d520d21 : 0x3d9aca6b) : 0;
        return Float.intBitsToFloat(bits) - offset;
    }
    private static float rangeGuideline(double high, double low, SharedPreferences prefs, boolean combo) {
        long largest = Math.max(Double.isFinite(high) ? Math.abs(converted(high, prefs)) : 0,
                Double.isFinite(low) ? Math.abs(converted(low, prefs)) : 0);
        int bits = largest >= 100 ? combo ? 0x3ed20d21 : 0x3eca6b2a
                : largest >= 10 ? combo ? 0x3eec4ec5 : 0x3ee82fa1
                : combo ? 0x3f034835 : 0x3f02fa0c;
        return Float.intBitsToFloat(bits);
    }

    private static void drawForecast(Context c, Canvas canvas, float w, float h, boolean compact,
            double current, String city, JSONObject weather, JSONObject daily, SharedPreferences prefs,
            int ink, int secondary) {
        float scale = Math.min(w / 945f, h / (compact ? 205f : 452f));
        float margin = 42f * scale, iconSide = 116f * scale, centerY = margin + iconSide / 2f;
        icon(c, canvas, weather.optInt("weather_code", -1), weather.optInt("is_day", 1) != 0,
                new RectF(margin, margin, margin + iconSide, margin + iconSide), ink);
        Paint currentPaint = paint(LIGHT, 84f * scale, ink);
        float tempLeft = margin + iconSide + 32f * scale;
        String currentText = temperature(current, prefs);
        float tempWidth = Math.min(currentPaint.measureText(currentText), 175f * scale);
        text(canvas, currentText, tempLeft, centeredBaseline(currentPaint, centerY), 175f * scale,
                currentPaint, Paint.Align.LEFT);
        Paint range = paint(LIGHT, 32f * scale, ink);
        float arrowSide = 42f * scale, rangeLeft = tempLeft + tempWidth + 11f * scale;
        float rowHeight = Math.max(lineHeight(range), arrowSide);
        double high = arrayValue(daily, "temperature_2m_max", 0), low = arrayValue(daily, "temperature_2m_min", 0);
        float rangeTop = centerY - rowHeight;
        arrow(c, canvas, true, new RectF(rangeLeft, rangeTop, rangeLeft + arrowSide, rangeTop + rowHeight), ink);
        text(canvas, temperature(high, prefs), rangeLeft + arrowSide, centeredBaseline(range, centerY - rowHeight / 2f),
                95f * scale, range, Paint.Align.LEFT);
        arrow(c, canvas, false, new RectF(rangeLeft, centerY, rangeLeft + arrowSide, centerY + rowHeight), ink);
        text(canvas, temperature(low, prefs), rangeLeft + arrowSide, centeredBaseline(range, centerY + rowHeight / 2f),
                95f * scale, range, Paint.Align.LEFT);
        float cityLeft = Math.max(w * .52f, rangeLeft + arrowSide + 95f * scale + margin), available = w - margin - cityLeft;
        Paint name = paint(LIGHT, 37f * scale, ink);
        float cityTop = centerY - lineHeight(currentPaint) / 2f;
        textTop(canvas, cityName(city), w - margin, cityTop, available, name, Paint.Align.RIGHT);
        textTop(canvas, description(weather), w - margin,
                cityTop + lineHeight(name), available, name, Paint.Align.RIGHT);
        if (compact) return;
        Paint day = paint(REGULAR, 26f * scale, secondary);
        Paint highPaint = paint(LIGHT, 32f * scale, ink), lowPaint = paint(LIGHT, 32f * scale, secondary);
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        float forecastTop = margin + iconSide + 42f * scale;
        float columnWidth = 64f * scale, first = margin + columnWidth / 2f;
        float spacing = (w - 2f * margin - columnWidth) / 5f;
        JSONArray times = daily.optJSONArray("time"), codes = daily.optJSONArray("weather_code");
        for (int index = 0; index < 6; index++) {
            LocalDate date = LocalDate.now().plusDays(index);
            if (times != null && index < times.length()) try { date = LocalDate.parse(times.getString(index)); }
                    catch (Exception ignored) {}
            float x = first + index * spacing;
            textTop(canvas, date.format(DateTimeFormatter.ofPattern("EEE", locale)).toUpperCase(locale), x,
                    forecastTop, columnWidth * 1.6f, day, Paint.Align.CENTER);
            float iconTop = forecastTop + lineHeight(day) + 16f * scale;
            float forecastIcon = 58f * scale;
            int code = codes != null && index < codes.length() ? codes.optInt(index, -1)
                    : index == 0 ? weather.optInt("weather_code", -1) : -1;
            icon(c, canvas, code, true, new RectF(x - forecastIcon / 2f, iconTop,
                    x + forecastIcon / 2f, iconTop + forecastIcon), ink);
            float highTop = iconTop + forecastIcon + 13f * scale;
            textTop(canvas, temperature(arrayValue(daily, "temperature_2m_max", index), prefs), x,
                    highTop, columnWidth * 1.4f, highPaint, Paint.Align.CENTER);
            textTop(canvas, temperature(arrayValue(daily, "temperature_2m_min", index), prefs), x,
                    highTop + lineHeight(highPaint) + 5f * scale, columnWidth * 1.4f,
                    lowPaint, Paint.Align.CENTER);
        }
    }

    private static void centeredMessage(Context c, Canvas canvas, String message, RectF bounds, float size, int ink) {
        if (dots == null) {
            int id = c.getResources().getIdentifier("ndot", "font", c.getPackageName());
            dots = id == 0 ? Typeface.create("nothingdot", Typeface.NORMAL) : c.getResources().getFont(id);
        }
        paragraph(canvas, message, bounds, paint(dots, size, ink), 2);
    }
    private static void paragraph(Canvas canvas, String text, RectF bounds, Paint paint, int maxLines) {
        TextPaint p = new TextPaint(paint);
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), p,
                Math.max(1, Math.round(bounds.width()))).setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(true).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).build();
        canvas.save(); canvas.clipRect(bounds);
        canvas.translate(bounds.left, bounds.centerY() - layout.getHeight() / 2f); layout.draw(canvas); canvas.restore();
    }
    private static void icon(Context c, Canvas canvas, int code, boolean day, RectF bounds, int ink) {
        String name;
        if (code < 0) return;
        if (code == 0) name = day ? "sunny" : "clear_night";
        else if (code == 1 || code == 2) name = day ? "partly_cloudy" : "cloudy_night";
        else if (code == 3) name = "cloudy";
        else if (code == 45 || code == 48) name = "fog";
        else if (code == 56 || code == 57 || code == 66 || code == 67) name = "sleet";
        else if (code == 71 || code == 73 || code == 75 || code == 77 || code == 85 || code == 86) name = "snow";
        else if (code == 95 || code == 96 || code == 97 || code == 99) name = "thunderstorms";
        else if (code == 51 || code == 53 || code == 55 || code == 61 || code == 63 || code == 65
                || code == 80 || code == 81 || code == 82) name = "rainy";
        else return;
        if (drawable(c, canvas, "ic_widget_" + name + "_neutral", bounds, ink)) return;
        if (drawable(c, canvas, "ic_widget_" + name, bounds, ink)) return;
        drawable(c, canvas, "ic_" + (name.equals("partly_cloudy") ? "partly_sunny" : name), bounds, ink);
    }
    private static void arrow(Context c, Canvas canvas, boolean up, RectF bounds, int ink) {
        if (drawable(c, canvas, "ic_widget_temp_arrow_" + (up ? "up" : "down"), bounds, ink)) return;
        Paint p = paint(REGULAR, 1, ink); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(bounds.width() / 24f);
        Path path = new Path(); float mid = bounds.centerY(), shift = bounds.height() * .12f * (up ? -1 : 1);
        path.moveTo(bounds.left + bounds.width() * .29f, mid - shift);
        path.lineTo(bounds.centerX(), mid + shift);
        path.lineTo(bounds.right - bounds.width() * .29f, mid - shift); canvas.drawPath(path, p);
    }
    private static boolean drawable(Context c, Canvas canvas, String name, RectF bounds, int ink) {
        int id = c.getResources().getIdentifier("weather_" + name, "drawable", c.getPackageName());
        if (id == 0) id = c.getResources().getIdentifier(name, "drawable", c.getPackageName());
        if (id == 0) return false;
        Drawable d = c.getDrawable(id); if (d == null) return false; d = d.mutate(); d.setTint(ink);
        d.setBounds(Math.round(bounds.left), Math.round(bounds.top), Math.round(bounds.right), Math.round(bounds.bottom));
        d.draw(canvas); return true;
    }
    private static Paint paint(Typeface font, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(font); p.setTextSize(size); p.setColor(color); return p;
    }
    private static void textTop(Canvas canvas, String value, float x, float top, float width, Paint p, Paint.Align align) {
        text(canvas, value, x, top - p.getFontMetrics().top, width, p, align);
    }
    private static void text(Canvas canvas, String value, float x, float baseline, float width, Paint p, Paint.Align align) {
        TextPaint copy = new TextPaint(p); copy.setTextAlign(align);
        String fitted = TextUtils.ellipsize(value, copy, Math.max(1, width), TextUtils.TruncateAt.END).toString();
        canvas.drawText(fitted, x, baseline, copy);
    }
    private static float lineHeight(Paint p) { return p.getFontMetrics().bottom - p.getFontMetrics().top; }
    private static float centeredBaseline(Paint p, float center) {
        Paint.FontMetrics metrics = p.getFontMetrics(); return center - (metrics.top + metrics.bottom) / 2f;
    }
    private static long converted(double celsius, SharedPreferences prefs) {
        return Math.round(prefs.getBoolean("fahrenheit", false) ? celsius * 9d / 5d + 32d : celsius);
    }
    private static String temperature(double celsius, SharedPreferences prefs) {
        return Double.isFinite(celsius) ? converted(celsius, prefs) + "°" : "—";
    }
    private static double arrayValue(JSONObject json, String key, int index) {
        JSONArray values = json.optJSONArray(key); return values == null ? Double.NaN : values.optDouble(index, Double.NaN);
    }
    private static JSONObject json(String value) {
        try { return new JSONObject(value); } catch (Exception ignored) { return new JSONObject(); }
    }
    /** WMO conditions from the real cache, phrased like Nothing's weather descriptions. */
    private static String description(JSONObject weather) {
        boolean day = weather.optInt("is_day", 1) != 0;
        switch (weather.optInt("weather_code", -1)) {
            case 0: return day ? "Sunny" : "Clear";
            case 1: return day ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: return "Fog";
            case 48: return "Freezing fog";
            case 51: return "Light drizzle";
            case 53: return "Drizzle";
            case 55: return "Heavy drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: return "Light rain";
            case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: return "Light snow";
            case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: return "Light showers";
            case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: return "Snow showers";
            case 86: return "Heavy snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Thunderstorm with hail";
            case 97: return "Heavy thunderstorm";
            default: return "";
        }
    }
    private static String cityName(String city) {
        if (city == null) return ""; int comma = city.lastIndexOf(','); return comma < 0 ? city : city.substring(0, comma).trim();
    }
    private static String string(Context c, String name, String fallback) {
        int id = c.getResources().getIdentifier("weather_" + name, "string", c.getPackageName());
        if (id == 0) id = c.getResources().getIdentifier(name, "string", c.getPackageName());
        return id == 0 ? fallback : c.getString(id);
    }
    private static int color(Context c, String name, int fallback) {
        int id = c.getResources().getIdentifier("weather_" + name, "color", c.getPackageName());
        if (id == 0) id = c.getResources().getIdentifier(name, "color", c.getPackageName());
        return id == 0 ? fallback : c.getColor(id);
    }
    private static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
