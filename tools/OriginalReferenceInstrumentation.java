package dev.dipa.widgets.tests;

import android.animation.Animator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Draws classes/resources loaded from the installed, unmodified NothingCardLab APK.
 * Emulator-only reference runner: no attached window, sensor, service, or settings changes.
 * Register this instrumentation targeting dev.dipa.widgets and declare a queries entry for
 * com.nothing.hearthstone. Output is the target app's cache/original-reference directory.
 */
public final class OriginalReferenceInstrumentation extends Instrumentation {
    private static final String ORIGINAL = "com.nothing.hearthstone";
    private static final String CELL = "com.nothing.cardwidget.battery.view.BatteryCellView";
    private final JSONArray failures = new JSONArray();
    private int rendered;
    private String requestedMode = "both";

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        if (args != null) requestedMode = args.getString("mode", "both");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        JSONObject report = new JSONObject();
        try {
            Context target = getTargetContext();
            File output = new File(target.getCacheDir(), "original-reference");
            if (!output.isDirectory() && !output.mkdirs()) throw new IllegalStateException("Cannot create " + output);
            Context donor = target.createPackageContext(ORIGINAL,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
            report.put("package", donor.getPackageName());
            report.put("apk", donor.getApplicationInfo().sourceDir);
            report.put("density", donor.getResources().getDisplayMetrics().density);
            report.put("fontScale", donor.getResources().getConfiguration().fontScale);
            report.put("modeFilter", requestedMode);
            report.put("globalNightMode", (donor.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES);
            report.put("ndotAvailable", !Typeface.create("ndot", Typeface.NORMAL).equals(Typeface.DEFAULT));
            report.put("ndot57Available", !Typeface.create("ndot57", Typeface.NORMAL).equals(Typeface.DEFAULT));
            report.put("ntype82Available", !Typeface.create("ntype82", Typeface.NORMAL).equals(Typeface.DEFAULT));
            report.put("referencePolicy", "Unmodified donor layouts and methods. Ratio uses the original "
                    + "72dp/172dp bases and raw76px percentage setter. Charging captures the original "
                    + "private indicator setter's final state. Compass captures its rotation endpoint. "
                    + "Views remain detached; no CardService or sensor is started.");

            boolean[] modes = requestedMode.equals("night") ? new boolean[]{true}
                    : requestedMode.equals("light") ? new boolean[]{false} : new boolean[]{false, true};
            for (boolean night : modes) {
                Context original = detachedContext(donor, night);
                String mode = night ? "night" : "light";
                for (int level : new int[]{20, 70}) {
                    for (boolean charging : new boolean[]{false, true}) {
                        for (int sideDp : new int[]{74, 179}) {
                            String label = "battery-small-" + level + "-" + state(charging)
                                    + "-" + sideDp + "dp-" + mode;
                            attempt(label, output, () -> battery(original, true, px(original, sideDp),
                                    px(original, sideDp), level, charging, 0));
                        }
                        for (int devices : new int[]{0, 1, 3}) {
                            final int count = devices;
                            for (float[] size : new float[][]{
                                    {179.42857f, 179.42857f}, {179.42857f, 208.85715f},
                                    {179.42857f, 98.85714f}}) {
                                String shape = size[0] == size[1] ? "square"
                                        : size[1] > size[0] ? "actual-large" : "actual-compact";
                                String label = "battery-large-" + level + "-" + state(charging)
                                        + "-devices" + count + "-" + shape + "-" + mode;
                                attempt(label, output, () -> battery(original, false, px(original, size[0]),
                                        px(original, size[1]), level, charging, count));
                            }
                        }
                    }
                }
                for (String style : new String[]{"bold", "scale"}) {
                    for (String size : new String[]{"1x1", "2x2"}) {
                        String layout = "card_analog_clock_" + style + "_" + size;
                        int side = px(original, size.equals("1x1") ? 74 : 179.42857f);
                        attempt("analog-" + style + "-" + size + "-" + mode, output,
                                () -> analog(original, layout, side));
                    }
                }
                for (boolean small : new boolean[]{false, true}) {
                    for (float bearing : new float[]{0, 45, 90, 180, 270, 315}) {
                        int side = px(original, small ? 74 : 179.42857f);
                        attempt("compass-" + (small ? "small" : "large") + "-"
                                + Math.round(bearing) + "-" + mode, output,
                                () -> compass(original, small, side, bearing));
                    }
                }
            }
            report.put("rendered", rendered);
            report.put("failures", failures);
            writeJson(new File(output, "report.json"), report);
            result.putInt("rendered", rendered);
            result.putString("output", output.toString());
            result.putString("failures", failures.toString());
            finish(failures.length() == 0 ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        } catch (Throwable error) {
            result.putString("fatal", rootCause(error).toString());
            result.putString("failures", failures.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private interface Renderer { Bitmap render() throws Exception; }

    private void attempt(String label, File output, Renderer renderer) throws Exception {
        final Throwable[] failure = {null};
        runOnMainSync(() -> {
            try {
                Bitmap bitmap = renderer.render();
                try (FileOutputStream stream = new FileOutputStream(new File(output, label + ".png"))) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
                        throw new IllegalStateException("PNG compression failed");
                }
                bitmap.recycle();
                rendered++;
            } catch (Throwable error) { failure[0] = rootCause(error); }
        });
        if (failure[0] != null) failures.put(label + ": " + failure[0]);
    }

    private static Context detachedContext(Context donor, boolean night) {
        Configuration config = new Configuration(donor.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        Context configured = donor.createConfigurationContext(config);
        if (donor.getApplicationInfo().theme != 0) configured.setTheme(donor.getApplicationInfo().theme);
        return new ContextWrapper(configured) {
            private LayoutInflater inflater;
            @Override public Context getApplicationContext() { return this; }
            @Override public Object getSystemService(String name) {
                if (Context.LAYOUT_INFLATER_SERVICE.equals(name)) {
                    if (inflater == null) inflater = LayoutInflater.from(getBaseContext()).cloneInContext(this);
                    return inflater;
                }
                return super.getSystemService(name);
            }
        };
    }

    private static Bitmap battery(Context donor, boolean small, int width, int height,
                                    int level, boolean charging, int devices) throws Exception {
        View view = inflate(donor, small ? "widget_item_battery_1x1" : "widget_item_battery");
        Class<?> model = donor.getClassLoader().loadClass("ma.a");
        Constructor<?> constructor = model.getConstructor(String.class, String.class, String.class,
                int.class, String.class, String.class, int.class, int.class, int.class,
                boolean.class, long.class, List.class, List.class, List.class, List.class);
        ArrayList<Object> records = new ArrayList<>();
        records.add(batteryRecord(donor, constructor, "reference:phone", "PHONE", "Phone",
                level, charging, "device_phone_default"));
        String[] names = {"Nothing Ear", "Watch Pro", "Headphones"};
        String[] icons = {"device_default_ear", "device_watch_pro", "device_nothingx_headphone_a"};
        int[] levels = {87, 45, 67};
        for (int i = 0; i < devices; i++) {
            records.add(batteryRecord(donor, constructor, "reference:bluetooth:" + i,
                    "BLUETOOTH_DEVICE", names[i], levels[i], false, icons[i]));
        }
        invoke(view, "setBatteryWidgetId", new Class<?>[]{int.class}, -101);
        float referenceDp = small ? 72 : 172;
        float ratio = Math.min(width, height) / (referenceDp * donor.getResources().getDisplayMetrics().density);
        invoke(view, small ? "setSmallBatteryDisplayRatio" : "setBatteryDisplayRatio",
                new Class<?>[]{float.class}, ratio);
        invoke(view, "setPermission", new Class<?>[]{boolean.class}, true);
        invoke(view, "setBatteryInfo", new Class<?>[]{ArrayList.class}, records);
        settleStartedAnimators(view);
        settleCharging(view, model);
        return draw(view, width, height);
    }

    private static Object batteryRecord(Context donor, Constructor<?> constructor, String address,
                                         String type, String name, int level, boolean charging,
                                         String icon) throws Exception {
        int drawable = id(donor, "drawable", icon);
        List<Integer> images = Arrays.asList(drawable, drawable);
        return constructor.newInstance(address, type, name, level, charging ? "CHARGING" : "NORMAL",
                "NORMAL", -1, -1, -1, true, 1L, null, null, null, images);
    }

    private static void settleCharging(View view, Class<?> model) throws Exception {
        if (view.getClass().getName().equals(CELL)) {
            Object record = field(view, "lastBatteryInfo");
            if (record != null && "CHARGING".equals(model.getMethod("b").invoke(record))) {
                ImageView indicator = (ImageView) field(view, "batteryIndicatorIv");
                // Existing-visible branch of the donor's method sets the exact settled endpoint.
                indicator.setVisibility(View.VISIBLE);
                Method update = view.getClass().getDeclaredMethod("setBatteryIndicator", ImageView.class, model);
                update.setAccessible(true);
                update.invoke(view, indicator, record);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) settleCharging(group.getChildAt(i), model);
        }
    }

    private static void settleStartedAnimators(View view) throws Exception {
        for (Field field : view.getClass().getDeclaredFields()) {
            if (Animator.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                Animator animator = (Animator) field.get(view);
                if (animator != null && animator.isStarted()) animator.end();
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) settleStartedAnimators(group.getChildAt(i));
        }
    }

    private static Bitmap analog(Context donor, String layout, int side) throws Exception {
        View view = inflate(donor, layout);
        View clock = view.findViewById(id(donor, "id", "analog_clock"));
        ViewGroup.LayoutParams params = clock.getLayoutParams();
        params.width = 0;
        params.height = 0;
        // Apply AnalogWidgetProvider's exact four anchors and ratio. The 3.4.2 APK's
        // ConstraintLayout params are R8-renamed; aliases are verified in its XML parser.
        constraintField(params, "bottomToBottom", "l").setInt(params, 0);
        constraintField(params, "topToTop", "i").setInt(params, 0);
        constraintField(params, "startToStart", "t").setInt(params, 0);
        constraintField(params, "endToEnd", "v").setInt(params, 0);
        constraintField(params, "dimensionRatio", "I").set(params, "1:1");
        clock.setLayoutParams(params);
        return draw(view, side, side);
    }

    private static Field constraintField(Object params, String name, String alias) throws Exception {
        try { return params.getClass().getField(name); }
        catch (NoSuchFieldException renamed) { return params.getClass().getField(alias); }
    }

    private static Bitmap compass(Context donor, boolean small, int side, float bearing) throws Exception {
        View view = inflate(donor, small ? "widget_item_compass_1x1" : "widget_item_compass");
        if (!small) view.setBackground(donor.getDrawable(id(donor, "drawable", "compass_bg")));
        Bundle data = new Bundle();
        data.putFloat("degree", bearing);
        data.putBoolean("haptic", false);
        invoke(view, "onDataChanged", new Class<?>[]{Bundle.class}, data);
        // A detached view has no ViewPropertyAnimator; apply its original rotation endpoint.
        ((ImageView) field(view, "directionIv")).setRotation(-bearing);
        return draw(view, side, side);
    }

    private static View inflate(Context donor, String layout) {
        return LayoutInflater.from(donor).inflate(id(donor, "layout", layout), null, false);
    }

    private static int id(Context donor, String type, String name) {
        int result = donor.getResources().getIdentifier(name, type, ORIGINAL);
        if (result == 0) throw new IllegalStateException("Original resource missing: " + type + "/" + name);
        return result;
    }

    private static Object field(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void invoke(Object object, String name, Class<?>[] types, Object... values) throws Exception {
        object.getClass().getMethod(name, types).invoke(object, values);
    }

    private static Bitmap draw(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static int px(Context context, float dp) {
        return Math.max(1, Math.round(dp * context.getResources().getDisplayMetrics().density));
    }

    private static String state(boolean charging) { return charging ? "charging" : "idle"; }

    private static Throwable rootCause(Throwable error) {
        while ((error instanceof InvocationTargetException || error.getCause() != null)
                && error.getCause() != null && error.getCause() != error) error = error.getCause();
        return error;
    }

    private static void writeJson(File file, JSONObject value) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(value.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
