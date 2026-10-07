package dev.dipa.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The original CompassView's arrow page, rendered only when its stored bearing changes. */
public final class CompassArtwork {
    private static final int MAX_BITMAP_EDGE = 768;
    private static final int RED = 0xffd71921;
    private static Typeface cardinalFont;

    private CompassArtwork() {}

    public static Bitmap render(Context context, String kind, int widthPx, int heightPx,
                                float heading) {
        int requestedWidth = Math.max(1, widthPx);
        int requestedHeight = Math.max(1, heightPx);
        float outputRatio = Math.min(1f, MAX_BITMAP_EDGE /
                (float) Math.max(requestedWidth, requestedHeight));
        int width = Math.max(1, Math.round(requestedWidth * outputRatio));
        int height = Math.max(1, Math.round(requestedHeight * outputRatio));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        boolean small = "CompassSmall".equals(kind);
        float reference = small ? 78 : 172;
        float side = Math.min(width, height);
        float pixelsPerUnit = side / reference;
        canvas.translate((width - side) / 2f, (height - side) / 2f);
        canvas.scale(pixelsPerUnit, pixelsPerUnit);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(background(context));
        canvas.drawCircle(reference / 2, reference / 2, reference / 2, paint);

        boolean known = Float.isFinite(heading);
        float bearing = known ? ((heading % 360) + 360) % 360 : 0;
        boolean north = known && (bearing > 356 || bearing < 4);
        if (known) {
            int saved = canvas.save();
            // CompassView animates iv_directions; iv_arrow always points toward the top.
            canvas.rotate(-bearing, reference / 2, reference / 2);
            String direction = small ? "compass_direction_all_1x1" : direction(bearing);
            boolean drawn = small
                    ? drawable(context, canvas, direction, 36, 4, 6, 70, pixelsPerUnit)
                    : drawable(context, canvas, direction, 0, 0, 172, 172, pixelsPerUnit);
            if (!drawn) fallbackDirections(context, canvas, paint, small, bearing);
            canvas.restoreToCount(saved);
        }

        String arrow = "compass_arrow" + (!known ? "_disable" : north ? "_red" : "")
                + (small ? "_1x1" : "");
        int arrowWidth = small ? 20 : 40;
        int arrowHeight = small ? 24 : 56;
        if (!drawable(context, canvas, arrow, (reference - arrowWidth) / 2,
                (reference - arrowHeight) / 2, arrowWidth, arrowHeight, pixelsPerUnit)) {
            fallbackArrow(context, canvas, paint, reference, small, known, north);
        }
        // The donor's arrow page contains no title, bearing number, or instruction caption.
        return bitmap;
    }

    private static String direction(float bearing) {
        if (bearing > 356 || bearing < 4) return "compass_direction_north";
        if (bearing > 86 && bearing < 94) return "compass_direction_east";
        if (bearing > 176 && bearing < 184) return "compass_direction_south";
        if (bearing > 266 && bearing < 274) return "compass_direction_west";
        return "compass_direction_all";
    }

    private static boolean drawable(Context context, Canvas canvas, String name,
                                     float x, float y, int width, int height,
                                     float pixelsPerUnit) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id == 0) return false;
        Drawable asset = context.getDrawable(id);
        if (asset == null) return false;
        // A rotated Canvas makes VectorDrawable cache its unscaled bounds. Use final
        // pixel-sized bounds and cancel the outer scale so its own cache stays sharp.
        int pixelWidth = Math.max(1, Math.round(width * pixelsPerUnit));
        int pixelHeight = Math.max(1, Math.round(height * pixelsPerUnit));
        asset.setBounds(0, 0, pixelWidth, pixelHeight);
        int saved = canvas.save();
        canvas.translate(x, y);
        canvas.scale(width / (float) pixelWidth, height / (float) pixelHeight);
        asset.draw(canvas);
        canvas.restoreToCount(saved);
        return true;
    }

    private static void fallbackArrow(Context context, Canvas canvas, Paint paint,
                                       float reference, boolean small, boolean known,
                                       boolean north) {
        paint.setColor(north ? RED : foreground(context));
        if (!known) paint.setAlpha(Math.round(255 * .6f));
        float[] xs = small ? new float[]{10, 10, 14, 6, 2, 18, 10, 10, 10, 10}
                : new float[]{20, 20, 28, 12, 4, 36, 20, 20, 20, 20, 20};
        float[] ys = small ? new float[]{2, 6, 6, 6, 10, 10, 10, 14, 18, 22}
                : new float[]{4, 12, 12, 12, 20, 20, 20, 28, 36, 44, 52};
        float width = small ? 20 : 40;
        float height = small ? 24 : 56;
        for (int i = 0; i < xs.length; i++) {
            canvas.drawCircle((reference - width) / 2 + xs[i],
                    (reference - height) / 2 + ys[i], small ? 2 : 4, paint);
        }
        paint.setAlpha(255);
    }

    private static void fallbackDirections(Context context, Canvas canvas, Paint paint,
                                            boolean small, float bearing) {
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        if (small) {
            paint.setColor(RED);
            canvas.drawCircle(39, 7, 3, paint);
            return;
        }
        paint.setTypeface(cardinalFont(context));
        paint.setTextSize(10);
        paint.setTextAlign(Paint.Align.CENTER);
        String selected = direction(bearing);
        if (!selected.equals("compass_direction_all")) {
            String letter = selected.endsWith("north") ? "N" : selected.endsWith("east") ? "E"
                    : selected.endsWith("south") ? "S" : "W";
            float x = letter.equals("E") ? 154 : letter.equals("W") ? 18 : 86;
            float y = letter.equals("N") ? 18 : letter.equals("S") ? 154 : 86;
            paint.setColor(letter.equals("N") ? RED : foreground(context));
            canvas.drawCircle(x, y, 10, paint);
            paint.setColor(background(context));
            drawLetter(canvas, paint, letter, x, y,
                    letter.equals("E") ? 90 : letter.equals("W") ? -90 : 0);
        } else {
            paint.setColor(RED);
            drawLetter(canvas, paint, "N", 86, 18, 0);
            paint.setColor(foreground(context));
            drawLetter(canvas, paint, "S", 86, 154, 0);
            drawLetter(canvas, paint, "E", 154, 86, 90);
            drawLetter(canvas, paint, "W", 18, 86, -90);
        }
    }

    private static void drawLetter(Canvas canvas, Paint paint, String letter,
                                    float x, float y, float rotation) {
        int saved = canvas.save();
        canvas.rotate(rotation, x, y);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(letter, x, y - (metrics.ascent + metrics.descent) / 2, paint);
        canvas.restoreToCount(saved);
    }

    private static Typeface cardinalFont(Context context) {
        if (cardinalFont == null) {
            int id = context.getResources().getIdentifier("roboto_regular", "font", context.getPackageName());
            if (id != 0) {
                try { cardinalFont = context.getResources().getFont(id); }
                catch (RuntimeException ignored) {}
            }
            if (cardinalFont == null) cardinalFont = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        return cardinalFont;
    }

    private static boolean night(Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    private static int background(Context context) {
        return context.getColor(night(context) ? android.R.color.system_neutral1_900
                : android.R.color.system_neutral1_50);
    }

    private static int foreground(Context context) {
        return context.getColor(night(context) ? android.R.color.system_neutral1_50
                : android.R.color.system_neutral1_900);
    }
}
