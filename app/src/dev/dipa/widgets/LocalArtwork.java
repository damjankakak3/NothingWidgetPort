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
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Static donor-style artwork, redrawn only by the existing widget data updates.
 * All typography uses donor fonts inside this process; launcher font substitution
 * and user font scaling cannot change a widget's designed proportions.
 */
public final class LocalArtwork {
    private static final int MAX_EDGE = 768;
    private static final Map<String, Typeface> FONTS = new HashMap<>();
    private LocalArtwork() {}

    /** Returns complete artwork, or null for a widget handled by another renderer. */
    public static Bitmap render(Context c, String kind, int widthPx, int heightPx) {
        boolean square = kind.equals("Date") || kind.equals("Steps") || kind.equals("ScreenTime");
        boolean steps = kind.equals("Steps") || kind.equals("StepsDaily")
                || kind.equals("StepsProgress") || kind.equals("StepsStreak");
        boolean screen = kind.equals("ScreenTime") || kind.equals("ScreenDuration") || kind.equals("ScreenShape");
        if (!square && !steps && !screen) return null;
        int requestedW = Math.max(1, widthPx), requestedH = Math.max(1, heightPx);
        float limit = Math.min(1f, MAX_EDGE / (float) Math.max(requestedW, requestedH));
        int width = Math.max(1, Math.round(requestedW * limit));
        int height = Math.max(1, Math.round(requestedH * limit));
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        float aspect = square ? 1f : 172f / 78f;
        float cardH = Math.min(height, width / aspect);
        float cardW = cardH * aspect;
        canvas.translate((width - cardW) / 2f, (height - cardH) / 2f);
        int foreground = color(c, kind.equals("Date") ? "date_date_text_color"
                : steps ? "pedometer_text_color" : "screen_time_text_color", foreground(c));
        int background = color(c, kind.equals("Date") ? "date_date_bg_color"
                : steps ? "pedometer_bg_color" : "screen_time_bg_color", background(c));
        long usage = screen ? Data.screenTime(c) : -1;
        if (usage >= 0) usage = usage / 60000L * 60000L;
        long goalMs = Math.max(1, Data.prefs(c).getInt("screen_goal", 180)) * 60000L;
        boolean overtime = screen && usage >= goalMs && !kind.equals("ScreenShape");
        if (overtime) {
            background = color(c, "screen_time_overtime_bg_color", 0xffd71921);
            foreground = color(c, "screen_time_overtime_item_text_color", 0xfff5f3f0);
        }
        Paint paint = paint(Typeface.DEFAULT, 1, background);
        float radius = square ? kind.equals("Date") ? cardH / 8.3f : cardH * 20f / 172f : cardH / 2f;
        canvas.drawRoundRect(new RectF(0, 0, cardW, cardH), radius, radius, paint);
        Path outline = new Path();
        outline.addRoundRect(new RectF(0, 0, cardW, cardH), radius, radius, Path.Direction.CW);
        canvas.clipPath(outline);
        if (kind.equals("Date")) drawDate(c, canvas, cardW, cardH, foreground);
        else if (steps) drawSteps(c, canvas, kind, cardW, cardH, foreground);
        else drawScreenTime(c, canvas, kind, cardW, cardH, foreground, usage, goalMs, overtime);
        return image;
    }

    private static void drawDate(Context c, Canvas canvas, float w, float h, int ink) {
        LocalDate date = LocalDate.now();
        Typeface face = font(c, "ntype_82_headline", "fonts/ntype_82_headline.otf", "ntype82");
        // DateProvider's 451 px design: date 335 px; weekday 52 px.
        Paint day = paint(face, h * 335f / 451f, ink);
        String number = Integer.toString(date.getDayOfMonth());
        fit(day, number, w * .92f);
        Paint.FontMetrics metrics = day.getFontMetrics();
        float baseline = h - h * 5f / 166f - metrics.bottom;
        drawHorizontal(canvas, number, w / 2f, baseline, day, Paint.Align.CENTER);
        Paint weekday = paint(face, h * 52f / 451f, ink);
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        String label = date.format(DateTimeFormatter.ofPattern("EEE", locale));
        fit(weekday, label, w * .5f);
        float top = h * 12f / 172f;
        drawHorizontal(canvas, label, w - w * 16f / 172f,
                top - weekday.getFontMetrics().top, weekday, Paint.Align.RIGHT);
    }

    private static void drawSteps(Context c, Canvas canvas, String kind, float w, float h, int ink) {
        SharedPreferences prefs = Data.prefs(c);
        Typeface dots = font(c, "ndot", null, "nothingdot");
        Typeface label = font(c, "roboto_regular", null, "sans-serif");
        if (!prefs.getBoolean("tracking", false)) {
            centeredMessage(canvas, "Enable step", "tracking", w, h, dots, ink);
            return;
        }
        int count = Data.steps(c, Data.today());
        int goal = Math.max(1, prefs.getInt("step_goal", 6000));
        if (kind.equals("StepsProgress")) {
            drawWalking(c, canvas, w, h, count, goal, ink);
            return;
        }
        if (kind.equals("StepsStreak")) {
            drawStreak(c, canvas, w, h, goal);
            return;
        }
        float numberSize = w * 30f / 172f;
        float labelSize = w * (kind.equals("Steps") ? 11f : 10f) / 172f;
        float side = w * (kind.equals("Steps") ? 16f : 24f) / 172f;
        Paint number = paint(dots, numberSize, ink);
        Paint captions = paint(label, labelSize, ink);
        captions.setLetterSpacing(.1f);
        Locale locale = c.getResources().getConfiguration().getLocales().get(0);
        NumberFormat format = NumberFormat.getIntegerInstance(locale);
        String current = format.format(count);
        String description = localString(c, "pedometer_todayStepsDes", "Total today").toUpperCase(locale);
        String percentage = count > 0 ? (long) count * 100 / goal + "%" : "";
        float gap = h * (kind.equals("Steps") ? 4f / 172f : 8f / 225f);
        float statHeight = unpaddedLineHeight(number) + gap + lineHeight(captions);
        if (kind.equals("StepsDaily")) {
            drawStat(canvas, current, description, percentage, side, (h - statHeight) / 2f,
                    w - side * 2f, gap, number, captions);
        } else {
            float margin = h * 16f / 172f;
            drawStat(canvas, current, description, percentage, side, margin,
                    w - side * 2f, gap, number, captions);
            // Donor average covers the seven preceding days and excludes empty days.
            long total = 0; int daysWithSteps = 0;
            for (int i = 1; i <= 7; i++) {
                int previous = Data.steps(c, LocalDate.now().minusDays(i).toString());
                if (previous > 0) { total += previous; daysWithSteps++; }
            }
            long average = daysWithSteps == 0 ? 0 : total / daysWithSteps;
            drawStat(canvas, format.format(average),
                    localString(c, "pedometer_sevenDayAverageDes", "7-day average").toUpperCase(locale),
                    average > 0 ? average * 100 / goal + "%" : "", side,
                    h - margin - statHeight, w - side * 2f, gap, number, captions);
        }
    }

    private static void drawStat(Canvas canvas, String value, String label, String percentage,
            float left, float top, float available, float gap, Paint number, Paint caption) {
        Paint fitted = new Paint(number);
        fit(fitted, value, available);
        // Donor number TextViews have includeFontPadding=false; captions retain it.
        float baseline = top - fitted.getFontMetrics().ascent;
        drawHorizontal(canvas, value, left, baseline, fitted, Paint.Align.LEFT);
        float captionBaseline = baseline + fitted.getFontMetrics().descent + gap - caption.getFontMetrics().top;
        Paint fittedCaption = new Paint(caption);
        fit(fittedCaption, label, available - caption.measureText(percentage) - caption.getTextSize());
        drawHorizontal(canvas, label, left, captionBaseline, fittedCaption, Paint.Align.LEFT);
        drawHorizontal(canvas, percentage, left + available, captionBaseline, caption, Paint.Align.RIGHT);
    }

    private static void drawStreak(Context c, Canvas canvas, float w, float h, int goal) {
        float first = w * .15116279f, spacing = w * .6976744f / 6f;
        float radius = w * 3f / 172f, futureRadius = w / 172f;
        int neutral = color(c, "pedometer_goal_not_reached_color", 0xff888580);
        int reached = color(c, "pedometer_calender_goal_reached_color", foreground(c));
        int todayColor = color(c, "pedometer_calender_currentDay_Color", 0xffd71921);
        LocalDate today = LocalDate.now();
        int todayIndex = today.getDayOfWeek().getValue() - 1;
        LocalDate monday = today.minusDays(todayIndex);
        Paint p = paint(Typeface.DEFAULT, 1, reached);
        // A completed past day joins to a completed preceding day, as WeeklyStreakView.
        for (int day = 1; day < todayIndex; day++) {
            boolean currentReached = Data.steps(c, monday.plusDays(day).toString()) >= goal;
            boolean previousReached = Data.steps(c, monday.plusDays(day - 1).toString()) >= goal;
            if (currentReached && previousReached) {
                canvas.drawRect(first + spacing * (day - 1), h / 2f - radius,
                        first + spacing * day, h / 2f + radius, p);
            }
        }
        for (int day = 0; day < 7; day++) {
            p.setColor(day == todayIndex ? todayColor
                    : day < todayIndex && Data.steps(c, monday.plusDays(day).toString()) >= goal ? reached : neutral);
            canvas.drawCircle(first + spacing * day, h / 2f, day > todayIndex ? futureRadius : radius, p);
        }
    }

    private static void drawWalking(Context c, Canvas canvas, float w, float h, int count, int goal, int ink) {
        float sx = w / 172f, sy = h / 78f;
        long percentage = Math.max(0L, (long) count * 100L / goal);
        // WidgetProgressLayout hides the track on completion and changes the pose location.
        float fraction = percentage == 100 ? .5f : percentage > 100 ? .22f : percentage / 100f;
        float humanLeft = (8f + 132f * fraction) * sx;
        float humanWidth = 24f * sx, humanHeight = 36f * sy;
        int completed = color(c, "pedometer_walking_circle_color", ink);
        int remaining = color(c, "pedometer_walking_dot_color", 0xff888580);
        Paint p = paint(Typeface.DEFAULT, 1, completed);
        if (percentage < 100) {
            int lastCompleted = (int) Math.floor((humanLeft / sx - 12f) / 8f);
            int firstRemaining = (int) Math.ceil(((humanLeft + humanWidth) / sx - 8f) / 8f) + 1;
            p.setColor(completed);
            for (int dot = 1; dot <= lastCompleted + 1; dot++)
                canvas.drawCircle((8f * dot + 2f) * sx, h / 2f, 2f * sy, p);
            p.setColor(remaining);
            for (int dot = firstRemaining; dot <= 20; dot++)
                canvas.drawCircle((8f * dot + 2f) * sx, h / 2f, sy, p);
        } else if (percentage > 100) {
            Paint percent = paint(font(c, "ndot", null, "nothingdot"), 24f * sx, completed);
            String value = percentage + "%";
            float right = w - 37.5f * sx;
            fit(percent, value, right - humanLeft - humanWidth - 4f * sx);
            Paint.FontMetrics metrics = percent.getFontMetrics();
            float baseline = h / 2f - (metrics.ascent + metrics.descent) / 2f + 3f * sy;
            drawHorizontal(canvas, value, right, baseline, percent, Paint.Align.RIGHT);
        }
        int ident = c.getResources().getIdentifier("ic_human_walking_1", "drawable", c.getPackageName());
        Drawable human = ident == 0 ? null : c.getDrawable(ident);
        if (human != null) {
            human.setBounds(Math.round(humanLeft), Math.round((h - humanHeight) / 2f),
                    Math.round(humanLeft + humanWidth), Math.round((h + humanHeight) / 2f));
            human.draw(canvas);
        } else {
            // Exact dots from donor ic_human_walking_1, with no animated drawable.
            int[][] points = {{18,32},{22,32},{18,28},{14,24},{10,20},{10,16},{14,12},{18,12},
                    {10,12},{10,7},{14,7},{10,3},{14,3},{2,16},{6,12},{10,24},{6,28},{2,32}};
            p.setColor(completed);
            for (int[] point : points) canvas.drawOval(new RectF(humanLeft + (point[0]-2)*sx,
                    (h-humanHeight)/2f+(point[1]-2)*sy, humanLeft+(point[0]+2)*sx,
                    (h-humanHeight)/2f+(point[1]+2)*sy), p);
        }
    }

    private static void drawScreenTime(Context c, Canvas canvas, String kind, float w, float h,
            int ink, long usage, long goalMs, boolean overtime) {
        Typeface numberFace = weightedFont(c, "ndot57", "nothingdot57", 500);
        Typeface unitFace = font(c, "ndot", null, "nothingdot");
        if (usage < 0) {
            centeredMessage(canvas, "Allow usage", "access", w, h, unitFace, ink);
            return;
        }
        if (kind.equals("ScreenShape")) {
            float side = w * 30f / 172f;
            float gridW = w - side * 2f, gridH = gridW * 3f / 14f;
            drawPoints(c, canvas, side, (h - gridH) / 2f, gridW, 14, usage / (double) goalMs);
            return;
        }
        boolean square = kind.equals("ScreenTime");
        float numberSize = h * 104f / (square ? 451f : 225f);
        float padding = square ? h * 32f / 451f : 0;
        float baseline;
        if (square) {
            if (overtime) {
                Typeface textFace = font(c, "roboto_regular", null, "sans-serif");
                Paint amount = paint(textFace, h * 52f / 451f, ink);
                String excess = durationText(usage - goalMs);
                float amountBaseline = padding - amount.getFontMetrics().top;
                drawHorizontal(canvas, excess, padding, amountBaseline, amount, Paint.Align.LEFT);
                Paint label = paint(textFace, h * 26f / 451f, ink);
                drawHorizontal(canvas, "OVER LIMIT", padding,
                        amountBaseline + amount.getFontMetrics().bottom - label.getFontMetrics().top,
                        label, Paint.Align.LEFT);
            } else drawPoints(c, canvas, padding, padding, w - padding * 2f, 20, usage / (double) goalMs);
            Paint digits = paint(numberFace, numberSize, ink);
            baseline = h - padding - digits.getFontMetrics().bottom;
        } else {
            Paint digits = paint(numberFace, numberSize, ink);
            baseline = h / 2f - (digits.getFontMetrics().ascent + digits.getFontMetrics().descent) / 2f
                    - h * 3f / 78f;
        }
        drawDuration(canvas, usage, square ? padding : w / 2f, baseline,
                numberSize, numberFace, unitFace, ink, !square, w - (square ? padding * 2f : w * .18f));
    }

    private static void drawDuration(Canvas canvas, long millis, float anchor, float baseline,
            float size, Typeface numberFace, Typeface unitFace, int ink, boolean centered, float maxWidth) {
        long minutes = Math.max(0, millis) / 60000L, hours = minutes / 60L;
        String[] strings = hours > 0 ? new String[]{Long.toString(hours), "H", Long.toString(minutes % 60), "M"}
                : new String[]{Long.toString(minutes % 60), "M"};
        Paint[] paints = new Paint[strings.length];
        float[] gaps = new float[strings.length];
        float width = 0;
        for (int i = 0; i < strings.length; i++) {
            boolean numeric = i % 2 == 0;
            paints[i] = paint(numeric ? numberFace : unitFace, numeric ? size : size / 2f, ink);
            gaps[i] = i == 0 ? 0 : size * (numeric ? 4f : 2f) / 40f;
            width += gaps[i] + paints[i].measureText(strings[i]);
        }
        float scale = Math.min(1, maxWidth / Math.max(1, width));
        float x = centered ? anchor - width * scale / 2f : anchor;
        for (int i = 0; i < strings.length; i++) {
            Paint p = paints[i]; p.setTextSize(p.getTextSize() * scale);
            x += gaps[i] * scale;
            // The original horizontal row aligns TextView bottoms rather than baselines.
            float digitBottom = baseline + paints[0].getFontMetrics().bottom;
            float textBaseline = i % 2 == 0 ? baseline : digitBottom - p.getFontMetrics().bottom;
            drawHorizontal(canvas, strings[i], x, textBaseline, p, Paint.Align.LEFT);
            x += p.measureText(strings[i]);
        }
    }

    private static void drawPoints(Context c, Canvas canvas, float left, float top, float width,
            int columns, double progress) {
        // ScreenTimeConfigBean first truncates usage to an integer percentage.
        progress = Math.floor(Math.max(0, Math.min(1, progress)) * 100d) / 100d;
        float cell = width / columns, radius = cell / 2f;
        int track = color(c, "screen_time_points_progress_track_color", dark(c) ? 0xff44413d : 0xffddd9d3);
        int lit = color(c, "screen_time_points_progress_progress_color", foreground(c));
        int second = color(c, "screen_time_points_progress_second_progress_color", 0xffd71921);
        Paint p = paint(Typeface.DEFAULT, 1, lit);
        for (int row = 0; row < 3; row++) for (int col = 0; col < columns; col++) {
            double columnProgress = (col + 1d) / columns;
            p.setColor(columnProgress > Math.min(1, progress) ? track
                    : columnProgress > .8 ? second : lit);
            canvas.drawCircle(left + (col + .5f) * cell, top + (row + .5f) * cell, radius, p);
        }
    }

    private static String durationText(long millis) {
        long minutes = Math.max(0, millis) / 60000L;
        return (minutes >= 60 ? minutes / 60 + "h " : "") + minutes % 60 + "m";
    }

    private static void centeredMessage(Canvas canvas, String first, String second,
            float w, float h, Typeface face, int ink) {
        Paint p = paint(face, Math.min(h * .19f, w * .11f), ink);
        fit(p, first.length() > second.length() ? first : second, w * .82f);
        float line = lineHeight(p);
        float baseline = h / 2f - line - p.getFontMetrics().top;
        drawHorizontal(canvas, first, w / 2f, baseline, p, Paint.Align.CENTER);
        drawHorizontal(canvas, second, w / 2f, baseline + line, p, Paint.Align.CENTER);
    }

    private static Paint paint(Typeface face, float size, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face); p.setTextSize(size); p.setColor(color); return p;
    }
    private static void drawHorizontal(Canvas canvas, String text, float x, float baseline,
            Paint p, Paint.Align alignment) {
        p.setTextAlign(alignment); canvas.drawText(text, x, baseline, p);
    }
    private static void fit(Paint p, String text, float available) {
        float measured = p.measureText(text);
        if (measured > available && measured > 0) p.setTextSize(p.getTextSize() * Math.max(1, available) / measured);
    }
    private static float lineHeight(Paint p) {
        Paint.FontMetrics metrics = p.getFontMetrics(); return metrics.bottom - metrics.top;
    }
    private static float unpaddedLineHeight(Paint p) {
        Paint.FontMetrics metrics = p.getFontMetrics(); return metrics.descent - metrics.ascent;
    }
    private static synchronized Typeface weightedFont(Context c, String resource, String fallback, int weight) {
        String key = resource + ":" + weight;
        Typeface face = FONTS.get(key);
        if (face == null) {
            face = Typeface.create(font(c, resource, null, fallback), weight, false);
            FONTS.put(key, face);
        }
        return face;
    }
    private static synchronized Typeface font(Context c, String resource, String asset, String fallback) {
        Typeface cached = FONTS.get(resource);
        if (cached != null) return cached;
        Typeface face = null;
        int id = c.getResources().getIdentifier(resource, "font", c.getPackageName());
        if (id != 0) try { face = c.getResources().getFont(id); } catch (RuntimeException ignored) {}
        if (face == null && asset != null) try { face = Typeface.createFromAsset(c.getAssets(), asset); }
                catch (RuntimeException ignored) {}
        if (face == null) face = Typeface.create(fallback, Typeface.NORMAL);
        FONTS.put(resource, face); return face;
    }
    private static String localString(Context c, String name, String fallback) {
        int id = c.getResources().getIdentifier(name, "string", c.getPackageName());
        return id == 0 ? fallback : c.getString(id);
    }
    private static int color(Context c, String name, int fallback) {
        int id = c.getResources().getIdentifier(name, "color", c.getPackageName());
        return id == 0 ? fallback : c.getColor(id);
    }
    private static boolean dark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }
    private static int foreground(Context c) { return dark(c) ? 0xfff5f3f0 : 0xff262523; }
    private static int background(Context c) { return dark(c) ? 0xff262523 : 0xfff5f3f0; }
}
