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
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A static snapshot of the donor countdown's default FIRST shape and its two pages. */
public final class CountdownArtwork {
    private static final int MAX_EDGE = 768;
    private static Typeface dots;

    private CountdownArtwork() {}

    public static Bitmap render(Context context, int widthPx, int heightPx, int widgetId) {
        int requestedWidth = Math.max(1, widthPx), requestedHeight = Math.max(1, heightPx);
        float outputRatio = Math.min(1f, MAX_EDGE / (float) Math.max(requestedWidth, requestedHeight));
        int width = Math.max(1, Math.round(requestedWidth * outputRatio));
        int height = Math.max(1, Math.round(requestedHeight * outputRatio));
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        image.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(image);
        float side = Math.min(width, height), scale = side / 166f;
        canvas.translate((width - side) / 2f, (height - side) / 2f);
        canvas.scale(scale, scale);
        float density = context.getResources().getDisplayMetrics().density;
        // chatgpt_2x2_scrollable_bg keeps its 20dp corners independent of the artwork scale.
        float physicalDp = density * outputRatio / scale;
        float corner = 20f * physicalDp;
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(color(context, "chatgpt_bg_color", night(context)
            ? android.R.color.system_neutral1_900 : android.R.color.system_neutral1_50));
        canvas.drawRoundRect(new RectF(0, 0, 166, 166), corner, corner, paint);
        Path card = new Path();
        card.addRoundRect(new RectF(0, 0, 166, 166), corner, corner, Path.Direction.CW);
        canvas.clipPath(card);

        SharedPreferences prefs = Data.prefs(context);
        LocalDate target = widgetId < 0 ? null : date(prefs, "date:" + widgetId);
        LocalDate today = LocalDate.now();
        long remaining = target == null ? 0 : ChronoUnit.DAYS.between(today, target);
        String title = prefs.getString("title:" + widgetId, "");
        if (target != null && Math.floorMod(prefs.getInt("page:" + widgetId, 0), 2) == 1) {
            drawSecondPage(context, canvas, paint, title, remaining);
        } else {
            drawFirstPage(context, canvas, paint, prefs, widgetId, target, today, remaining, physicalDp);
        }
        return image;
    }

    private static void drawFirstPage(Context context, Canvas canvas, Paint paint,
            SharedPreferences prefs, int widgetId, LocalDate target, LocalDate today,
            long remaining, float physicalDp) {
        // CountdownCardInfoBuilder.n: 102dp square, top16dp when its date is displayed.
        float shapeSide = 102f;
        int shapeSave = canvas.save();
        canvas.translate((166f - shapeSide) / 2f, 16f);
        Path shape = firstShape(shapeSide, 4f * physicalDp, 100f * physicalDp);
        int base = color(context, "shape_base_color", night(context)
            ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100);
        int passed = color(context, "shape_pass_progress", night(context)
            ? android.R.color.system_neutral1_700 : android.R.color.system_neutral1_200);
        if (target == null || remaining < 0) {
            // Original no-event and passed state uses progress1.1 and a3dp outline.
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * physicalDp);
            paint.setColor(passed);
            canvas.drawPath(shape, paint);
        } else if (remaining == 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffd71921);
            canvas.drawPath(shape, paint);
        } else {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(base);
            canvas.drawPath(shape, paint);
            float progress = futureProgress(prefs, widgetId, target, today, remaining);
            int fillSave = canvas.save();
            canvas.clipPath(shape);
            paint.setColor(passed);
            // ShapeProgressView's zero-tilt liquid boundary is horizontal.
            canvas.drawRect(0, shapeSide * progress, shapeSide, shapeSide, paint);
            canvas.restoreToCount(fillSave);
        }
        canvas.restoreToCount(shapeSave);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(dotFont(context));
        paint.setTextSize(20f);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(color(context, "countdown_event_text_color", night(context)
            ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900));
        String label = target == null ? string(context, "countdown_no_event_description", "No Event")
            : remaining == 0 ? string(context, "countdown_hint_today", "Today")
            : Math.abs(remaining) + string(context, "countdown_display_day", "D");
        fit(paint, label, 150f);
        // card_countdown_first_page: the label begins6dp below the102dp shape.
        canvas.drawText(label, 83f, 16f + shapeSide + 6f - paint.getFontMetrics().top, paint);
    }

    private static Path firstShape(float side, float padding, float radius) {
        // ShapePathUtil.getFirstShapePath unions two horizontal rounded rectangles.
        float inset = Math.min(padding, side / 8f);
        Path top = new Path(), bottom = new Path(), union = new Path();
        top.addRoundRect(new RectF(inset, inset, side - inset, side / 2f),
            radius, radius, Path.Direction.CW);
        bottom.addRoundRect(new RectF(inset, side / 2f, side - inset, side - inset),
            radius, radius, Path.Direction.CW);
        union.op(top, bottom, Path.Op.UNION);
        return union;
    }

    private static float futureProgress(SharedPreferences prefs, int widgetId, LocalDate target,
            LocalDate today, long remaining) {
        LocalDate created = date(prefs, "countdown_created:" + widgetId);
        // Existing events have no creation date; retain the authentic initial full shape.
        if (created == null || created.isAfter(today)) created = today;
        long duration = Math.max(1, ChronoUnit.DAYS.between(created, target));
        double fraction = Math.max(0, Math.min(1, remaining / (double) duration));
        // CountdownUtil.k's original easing. Only a snapshot is rendered; no tilt sensor or timer.
        double progress = 1 - (.8 * Math.exp(3 * fraction) / Math.exp(3) + .2 * fraction);
        return (float) Math.max(0, Math.min(1, progress));
    }

    private static void drawSecondPage(Context context, Canvas canvas, Paint paint,
            String title, long remaining) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(dotFont(context));
        paint.setTextSize(20f);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(0xffd71921);
        String hint = remaining > 0 ? string(context, "countdown_hint_until", "Until")
            : remaining < 0 ? string(context, "countdown_hint_since", "Since")
            : string(context, "countdown_hint_today", "Today");
        fit(paint, hint, 134f);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(hint, 16f, 16f - metrics.top, paint);
        float textTop = 16f + metrics.bottom - metrics.top;
        TextPaint caption = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        caption.setTypeface(dotFont(context));
        caption.setTextSize(20f);
        caption.setColor(color(context, "countdown_event_text_color", night(context)
            ? android.R.color.system_neutral1_50 : android.R.color.system_neutral1_900));
        String name = title == null || title.isEmpty()
            ? string(context, "countdown_event_name_empty_placeholder", "Nothing") : title;
        float lineHeight = caption.getFontMetrics().bottom - caption.getFontMetrics().top;
        int lines = Math.max(1, Math.min(3, (int) ((150f - textTop) / Math.max(1f, lineHeight))));
        StaticLayout layout = StaticLayout.Builder.obtain(name, 0, name.length(), caption, 134)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(true)
            .setMaxLines(lines).setEllipsize(TextUtils.TruncateAt.END).build();
        int save = canvas.save();
        canvas.translate(16f, textTop);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private static LocalDate date(SharedPreferences prefs, String key) {
        try {
            String value = prefs.getString(key, null);
            return value == null ? null : LocalDate.parse(value);
        } catch (RuntimeException invalid) {
            return null;
        }
    }

    private static synchronized Typeface dotFont(Context context) {
        if (dots == null) {
            int id = context.getResources().getIdentifier("ndot", "font", context.getPackageName());
            if (id != 0) try { dots = context.getResources().getFont(id); }
                catch (RuntimeException ignored) {}
            if (dots == null) dots = Typeface.create("nothingdot", Typeface.NORMAL);
        }
        return dots;
    }

    private static void fit(Paint paint, String value, float available) {
        float width = paint.measureText(value);
        if (width > available) paint.setTextSize(paint.getTextSize() * available / width);
    }

    private static boolean night(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
            == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int color(Context context, String name, int fallbackSystemColor) {
        int id = context.getResources().getIdentifier(name, "color", context.getPackageName());
        return context.getColor(id == 0 ? fallbackSystemColor : id);
    }

    private static String string(Context context, String name, String fallback) {
        int id = context.getResources().getIdentifier(name, "string", context.getPackageName());
        return id == 0 ? fallback : context.getString(id);
    }
}
