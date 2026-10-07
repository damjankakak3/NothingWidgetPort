package dev.dipa.widgets;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** Nothing's battery composition drawn once per battery update, for a native ImageView host. */
public final class BatteryArtwork {
    private static final int MAX_BITMAP_EDGE = 768;
    // Donor artwork uses the OEM 2.625-density reference (e.g. 105px for a 40dp arrow).
    private static final float CHARGING_SHIFT_UNITS = 22f / 2.625f;
    private static Typeface dot;

    private BatteryArtwork() {}

    public static Bitmap small(Context context, int sidePx, int level, boolean charging) {
        int requested = Math.max(1, sidePx);
        int side = Math.min(MAX_BITMAP_EDGE, requested);
        Bitmap bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        float scale = side / 72f;
        canvas.scale(scale, scale);
        Paint paint = paint(background(context));
        canvas.drawCircle(36, 36, 36, paint);
        cell(context, canvas, paint, 0, 0, 72, level, charging,
                "device_phone_default", CHARGING_SHIFT_UNITS);
        return bitmap;
    }

    public static Bitmap large(Context context, int widthPx, int heightPx, int level,
                               boolean charging, JSONArray devices) {
        int requestedWidth = Math.max(1, widthPx);
        int requestedHeight = Math.max(1, heightPx);
        float outputRatio = Math.min(1f, MAX_BITMAP_EDGE /
                (float) Math.max(requestedWidth, requestedHeight));
        int width = Math.max(1, Math.round(requestedWidth * outputRatio));
        int height = Math.max(1, Math.round(requestedHeight * outputRatio));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setDensity(Bitmap.DENSITY_NONE);
        Canvas canvas = new Canvas(bitmap);
        float side = Math.min(width, height);
        float scale = side / 172f;
        canvas.translate((width - side) / 2f, (height - side) / 2f);
        canvas.scale(scale, scale);
        Paint paint = paint(background(context));
        // battery_2x2_bg.xml keeps a fixed 20dp radius while the inner grid scales.
        float cornerRadius = 20f * context.getResources().getDisplayMetrics().density
                * outputRatio / scale;
        canvas.drawRoundRect(new RectF(0, 0, 172, 172), cornerRadius, cornerRadius, paint);

        ArrayList<JSONObject> validDevices = new ArrayList<>();
        if (devices != null) {
            for (int i = 0; i < devices.length() && validDevices.size() < 3; i++) {
                JSONObject device = devices.optJSONObject(i);
                if (device != null && device.optInt("level", -1) >= 0) validDevices.add(device);
            }
        }
        // A centered 158dp grid: 70dp cells with 6dp outer and 3dp inner margins.
        cell(context, canvas, paint, 13, 13, 70, level, charging,
                "device_phone_default", CHARGING_SHIFT_UNITS);
        if (validDevices.isEmpty()) {
            percentage(context, canvas, paint, level);
        } else {
            for (int i = 0; i < validDevices.size(); i++) {
                // The original two-device layout uses opposite corners.
                int slot = validDevices.size() == 1 ? 3 : i + 1;
                JSONObject device = validDevices.get(i);
                float x = (slot % 2 == 0) ? 13 : 89;
                float y = (slot < 2) ? 13 : 89;
                cell(context, canvas, paint, x, y, 70, device.optInt("level", 0), false,
                        deviceIcon(device.optString("name", "")), CHARGING_SHIFT_UNITS);
            }
        }
        return bitmap;
    }

    private static Paint paint(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(color);
        return paint;
    }

    private static void cell(Context context, Canvas canvas, Paint paint, float x, float y,
                             float side, int level, boolean charging, String icon,
                             float chargingShift) {
        if (level >= 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(!charging && level <= 20 ? Color.rgb(215, 25, 33) : progress(context));
            // BatteryCellView's drawable uses a filled sector, not a stroked ring.
            canvas.drawArc(new RectF(x, y, x + side, y + side), 270,
                    Math.min(100, Math.max(0, level)) * 3.6f, true, paint);
        }
        // The original cell's icon holder is 50x30dp, FIT_CENTER, inside its 70dp cell.
        // SmallBattery uses the same holder in its 72dp reference circle.
        float holderWidth = 50;
        float holderHeight = 30;
        float left = x + (side - holderWidth) / 2;
        float top = y + (side - holderHeight) / 2 - (charging ? chargingShift : 0);
        drawResource(context, canvas, icon, left, top, holderWidth, holderHeight);
        if (charging) {
            drawResource(context, canvas, "battery_indicator_charging",
                    x + (side - 16) / 2, y + 48, 16, 16);
        }
    }

    private static void percentage(Context context, Canvas canvas, Paint paint, int level) {
        paint.setColor(foreground(context));
        paint.setTypeface(dot(context));
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setStyle(Paint.Style.FILL);
        // BatteryLevelTextStyle defines 30sp in the 172dp composition. Its runtime 76px
        // setter assumes the OEM reference density. Keep the intended proportions on
        // other phones, using design units so neither ROM density nor fontScale alters them.
        paint.setTextSize(30f);
        String text = level < 0 ? "—" : Math.min(100, Math.max(0, level)) + "%";
        float width = paint.measureText(text);
        if (width > 142) paint.setTextSize(paint.getTextSize() * 142 / width);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        // Grid bottom is 165; original TextView bottom margin is 10dp, end margin 16dp.
        canvas.drawText(text, 156, 155 - metrics.descent, paint);
    }

    private static Typeface dot(Context context) {
        if (dot == null) {
            int id = context.getResources().getIdentifier("ndot", "font",
                    context.getPackageName());
            if (id != 0) {
                try { dot = context.getResources().getFont(id); }
                catch (RuntimeException ignored) {}
            }
            if (dot == null) dot = Typeface.create("ndot", Typeface.NORMAL);
        }
        return dot;
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

    private static int progress(Context context) {
        return night(context) ? context.getColor(android.R.color.system_neutral1_800) : 0x66c5c7c9;
    }

    private static String deviceIcon(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("watch")) return "device_watch_pro";
        if (lower.contains("headphone") || lower.contains("headset")) return "device_nothingx_headphone_a";
        if (lower.contains("buds") || lower.contains("earbud") || lower.contains("earphone")
                || lower.contains("nothing ear") || lower.contains("airpod")) return "device_default_ear";
        if (lower.contains("phone") || lower.contains("pixel") || lower.contains("galaxy")) return "device_phone_default";
        return "device_uncategorised";
    }

    private static void drawResource(Context context, Canvas canvas, String name,
                                     float left, float top, float width, float height) {
        int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        if (id == 0 && !name.equals("battery_indicator_charging")) {
            id = context.getResources().getIdentifier("device_phone_default", "drawable", context.getPackageName());
        }
        if (id == 0) return;
        Drawable drawable = context.getDrawable(id);
        if (drawable == null) return;
        int nativeWidth = Math.max(1, drawable.getIntrinsicWidth());
        int nativeHeight = Math.max(1, drawable.getIntrinsicHeight());
        float scale = Math.min(width / nativeWidth, height / nativeHeight);
        float drawnWidth = nativeWidth * scale;
        float drawnHeight = nativeHeight * scale;
        int save = canvas.save();
        canvas.translate(left + (width - drawnWidth) / 2, top + (height - drawnHeight) / 2);
        canvas.scale(scale, scale);
        drawable.setBounds(0, 0, nativeWidth, nativeHeight);
        drawable.draw(canvas);
        canvas.restoreToCount(save);
    }
}
