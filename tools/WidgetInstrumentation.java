package dev.dipa.widgets.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.util.SizeF;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextClock;
import android.widget.TextView;
import dev.dipa.widgets.Registry;
import dev.dipa.widgets.Widgets;
import dev.dipa.widgets.BatteryArtwork;
import dev.dipa.widgets.CompassArtwork;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.LocalDate;
import org.json.JSONArray;
import org.json.JSONObject;

/** View-only rendering checks. Never binds widgets, sends broadcasts, or starts services. */
public final class WidgetInstrumentation extends Instrumentation {
    private boolean fixturePreviews;
    private boolean fixturesOnly;
    private static final String[] SHAPES = {"nominal", "wide", "tall"};
    // Exact distinct pairs from analysis/current-widget-host-state-decoded.xml.
    // Min width and min height belong to different orientations and must never be paired.
    private static final SizeF[] PLACED_LARGE = {
        new SizeF(179.42857f, 208.85715f), new SizeF(179.42857f, 207.71428f),
        new SizeF(365.7143f, 107.42857f)
    };
    private static final SizeF[] PLACED_COMPACT = {
        new SizeF(179.42857f, 98.85714f), new SizeF(179.42857f, 98.28571f),
        new SizeF(365.7143f, 47.714287f)
    };
    private static final SizeF[] PLACED_QUICK = {
        new SizeF(373.7143f, 98.85714f), new SizeF(373.7143f, 98.28571f),
        new SizeF(743.4286f, 47.714287f)
    };
    private static final int[] NUMERIC_IDS = {
        dev.dipa.widgets.R.id.value,
        dev.dipa.widgets.R.id.clock,
        dev.dipa.widgets.R.id.minutes,
        dev.dipa.widgets.R.id.subtitle,
        dev.dipa.widgets.R.id.date_weekday,
        dev.dipa.widgets.R.id.time_colon
    };

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        String fixtures = args == null ? "false" : args.getString("fixtures", "false");
        fixturePreviews = fixtures.equals("true") || fixtures.equals("1") || fixtures.equals("only");
        fixturesOnly = fixtures.equals("only");
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Context baseApp = getTargetContext();
            // A restricted foreign package reproduces the launcher's resource/font inflation.
            Context baseHost = getContext().createPackageContext(
                getContext().getPackageName(), Context.CONTEXT_RESTRICTED);
            Typeface dot = Typeface.create("nothingdot57aligned", Typeface.NORMAL);
            if (dot.equals(Typeface.DEFAULT)) throw new AssertionError("ROM dot family unavailable");
            if (Typeface.create("nothingdot", Typeface.NORMAL).equals(Typeface.DEFAULT))
                throw new AssertionError("ROM original Ndot family unavailable");
            if (Typeface.create("ntype82", Typeface.NORMAL).equals(Typeface.DEFAULT))
                throw new AssertionError("ROM Nothing headline family unavailable");
            if (Registry.ITEMS.length != 31) throw new AssertionError("Expected all 31 local providers");
            // Instrumentation executes as the target UID; use only its disposable test cache.
            File previews = new File(baseApp.getCacheDir(), "geometry-test-previews");
            if (!previews.isDirectory() && !previews.mkdirs())
                throw new AssertionError("Cannot create test preview directory");

            List<String> failures = new ArrayList<>();
            int rendered = 0;
            int artworkRendered = 0;
            int compassRendered = 0;
            int fixtureRendered = 0;
            if (!fixturesOnly) for (boolean night : new boolean[]{true, false}) {
                String mode = night ? "night" : "light";
                Context app = themeContext(baseApp, night);
                Context hostContext = themeContext(baseHost, night);
                for (String[] def : Registry.ITEMS) {
                    AppWidgetProviderInfo info = findProvider(app, def[0]);
                    for (HostCase test : hostCases(def)) {
                        final Throwable[] failure = {null};
                        runOnMainSync(() -> {
                            try {
                                checkCase(app, hostContext, info, def[0], test, night, dot, previews);
                            } catch (Throwable e) {
                                failure[0] = e;
                            }
                        });
                        if (failure[0] == null) rendered++;
                        else failures.add(def[0] + "/" + test.label + "/" + mode + " ("
                            + test.size + "dp): " + failure[0]);
                    }
                }
                for (int level : new int[]{20, 70}) {
                    for (boolean charging : new boolean[]{false, true}) {
                        for (int sideDp : new int[]{74, 179}) {
                            String label = "battery-artwork-small-" + level + "-"
                                + (charging ? "charging" : "idle") + "-" + sideDp + "dp-" + mode;
                            final Throwable[] failure = {null};
                            runOnMainSync(() -> {
                                try {
                                    checkBatteryArtwork(app, previews, label, true, new SizeF(sideDp, sideDp),
                                        level, charging, 0, night);
                                } catch (Throwable e) { failure[0] = e; }
                            });
                            if (failure[0] == null) artworkRendered++;
                            else failures.add(label + ": " + failure[0]);
                        }
                        SizeF[] batterySizes = {PLACED_LARGE[0], PLACED_COMPACT[0], PLACED_LARGE[2]};
                        for (int i = 0; i < batterySizes.length; i++) {
                            String shape = i == 0 ? "large" : i == 1 ? "compact" : "landscape";
                            for (int devices = 0; devices <= 3; devices++) {
                                String label = "battery-artwork-" + shape + "-" + level + "-"
                                    + (charging ? "charging" : "idle") + "-devices" + devices + "-" + mode;
                                final int deviceCount = devices;
                                final SizeF size = batterySizes[i];
                                final Throwable[] failure = {null};
                                runOnMainSync(() -> {
                                    try {
                                        checkBatteryArtwork(app, previews, label, false, size,
                                            level, charging, deviceCount, night);
                                    } catch (Throwable e) { failure[0] = e; }
                                });
                                if (failure[0] == null) artworkRendered++;
                                else failures.add(label + ": " + failure[0]);
                            }
                        }
                    }
                }
                for (String kind : new String[]{"Compass", "CompassSmall"}) {
                    float square = kind.equals("CompassSmall") ? 74f : 179.42857f;
                    SizeF[] sizes = {new SizeF(square, square), PLACED_COMPACT[0], PLACED_COMPACT[2]};
                    for (int i = 0; i < sizes.length; i++) {
                        String shape = i == 0 ? "square" : i == 1 ? "compact" : "landscape";
                        final SizeF size = sizes[i];
                        for (float bearing : new float[]{Float.NaN, 0, 45, 90, 180, 270, 315}) {
                            String label = "compass-artwork-" + kind + "-" + shape + "-"
                                + (Float.isNaN(bearing) ? "unknown" : Integer.toString((int) bearing)) + "-" + mode;
                            final Throwable[] failure = {null};
                            runOnMainSync(() -> {
                                try {
                                    checkCompassArtwork(app, previews, label, kind, size, bearing, night);
                                } catch (Throwable e) { failure[0] = e; }
                            });
                            if (failure[0] == null) compassRendered++;
                            else failures.add(label + ": " + failure[0]);
                        }
                    }
                }
            }
            if (fixturePreviews) {
                for (boolean night : new boolean[]{true, false}) {
                    Context configuredApp = themeContext(baseApp, night);
                    Context hostContext = themeContext(baseHost, night);
                    for (FixtureCase fixture : fixtureCases()) {
                        final Throwable[] failure = {null};
                        runOnMainSync(() -> {
                            try {
                                Map<String, Object> values = populatedValues();
                                values.putAll(fixture.values);
                                Context app = new FixtureContext(configuredApp, values);
                                AppWidgetProviderInfo info = findProvider(app, fixture.kind);
                                HostCase hostCase = new HostCase(fixture.label, fixture.size,
                                    sizeOptions(new SizeF[]{fixture.size}, true), fixture.size);
                                checkCase(app, hostContext, info, fixture.kind, hostCase, night,
                                    dot, previews, FIXTURE_WIDGET_ID);
                            } catch (Throwable e) { failure[0] = e; }
                        });
                        if (failure[0] == null) fixtureRendered++;
                        else failures.add(fixture.label + "/" + (night ? "night" : "light")
                            + " (" + fixture.size + "dp): " + failure[0]);
                    }
                }
            }
            if (!failures.isEmpty())
                throw new AssertionError(rendered + " host layouts and " + artworkRendered
                    + " battery artwork states and " + compassRendered + " compass states and "
                    + fixtureRendered + " populated fixtures passed; " + failures.size()
                    + " failed:\n" + String.join("\n", failures));
            result.putString("stream", "PASS: " + Registry.ITEMS.length + " providers, "
                + rendered + " launcher host layouts in light/night mode at display density "
                + baseHost.getResources().getDisplayMetrics().density + "; " + artworkRendered
                + " battery and " + compassRendered + " compass artwork state renders; "
                + fixtureRendered + " populated fixture previews\n");
            result.putInt("providers", Registry.ITEMS.length);
            result.putInt("rendered", rendered);
            result.putInt("artwork_rendered", artworkRendered);
            result.putInt("compass_rendered", compassRendered);
            result.putInt("fixture_rendered", fixtureRendered);
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) {
            result.putString("stream", android.util.Log.getStackTraceString(e));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static Context themeContext(Context context, boolean night) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
            | (night ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return context.createConfigurationContext(configuration);
    }

    private static final int FIXTURE_WIDGET_ID = 900001;

    private static Map<String, Object> populatedValues() throws Exception {
        // These samples belong only to labelled test previews and never reach app preferences.
        Map<String, Object> values = new HashMap<>();
        LocalDate today = LocalDate.now();
        values.put("tracking", true);
        values.put("step_goal", 6000);
        values.put("steps:" + today, 1200);
        int[] previousSteps = {6500, 8200, 3500, 7000, 6100, 4000, 9000};
        for (int day = 1; day <= previousSteps.length; day++)
            values.put("steps:" + today.minusDays(day), previousSteps[day - 1]);
        values.put("screen_goal", 180);
        values.put("compass", 45f);
        values.put("city", "Budapest");
        values.put("lat", "47.4979");
        values.put("lon", "19.0402");
        values.put("fahrenheit", false);
        values.put("weather_time", System.currentTimeMillis());
        JSONObject weather = new JSONObject();
        weather.put("temperature_2m", 20);
        weather.put("apparent_temperature", 19);
        weather.put("relative_humidity_2m", 65);
        weather.put("wind_speed_10m", 10.8);
        weather.put("weather_code", 0);
        weather.put("is_day", 1);
        values.put("weather", weather.toString());
        JSONObject forecast = new JSONObject();
        JSONArray dates = new JSONArray(), high = new JSONArray(), low = new JSONArray(), codes = new JSONArray();
        int[] highs = {22, 23, 20, 21, 24, 25}, lows = {12, 13, 11, 10, 14, 15};
        int[] conditions = {0, 2, 61, 3, 0, 1};
        for (int day = 0; day < 6; day++) {
            dates.put(today.plusDays(day).toString());
            high.put(highs[day]); low.put(lows[day]); codes.put(conditions[day]);
        }
        forecast.put("time", dates);
        forecast.put("temperature_2m_max", high);
        forecast.put("temperature_2m_min", low);
        forecast.put("weather_code", codes);
        values.put("forecast", forecast.toString());
        values.put("zone:" + FIXTURE_WIDGET_ID, "Europe/Budapest,America/New_York,Asia/Tokyo");
        values.put("date:" + FIXTURE_WIDGET_ID, today.plusDays(12).toString());
        values.put("title:" + FIXTURE_WIDGET_ID, "Fixture event in Budapest");
        values.put("countdown_created:" + FIXTURE_WIDGET_ID, today.minusDays(8).toString());
        values.put("page:" + FIXTURE_WIDGET_ID, 0);
        return values;
    }

    private static final class FixtureCase {
        final String kind, label;
        final SizeF size;
        final Map<String, Object> values;
        FixtureCase(String kind, String label, SizeF size, Map<String, Object> values) {
            this.kind = kind;
            this.label = "fixture-" + kind + "-" + label;
            this.size = size;
            this.values = values;
        }
    }

    private static List<FixtureCase> fixtureCases() {
        List<FixtureCase> fixtures = new ArrayList<>();
        SizeF[] slots = {PLACED_LARGE[0], PLACED_COMPACT[0]};
        for (int i = 0; i < slots.length; i++) {
            String shape = i == 0 ? "large" : "compact";
            for (String kind : new String[]{"Steps", "StepsDaily", "StepsProgress", "StepsStreak",
                    "ScreenTime", "ScreenDuration", "ScreenShape", "ScreenFace", "Compass", "CompassSmall"})
                fixtures.add(new FixtureCase(kind, shape, slots[i], Collections.emptyMap()));
            for (int page = 0; page < 3; page++) {
                Map<String, Object> values = Collections.singletonMap("page:" + FIXTURE_WIDGET_ID, page);
                fixtures.add(new FixtureCase("WeatherCircle", shape + "-page" + page, slots[i], values));
                if (page < 2)
                    fixtures.add(new FixtureCase("WeatherSquare", shape + "-page" + page, slots[i], values));
                fixtures.add(new FixtureCase("World", shape + "-zone" + page, slots[i], values));
            }
            for (boolean today : new boolean[]{false, true}) for (int page = 0; page < 2; page++) {
                Map<String, Object> values = new HashMap<>();
                values.put("page:" + FIXTURE_WIDGET_ID, page);
                values.put("date:" + FIXTURE_WIDGET_ID,
                    LocalDate.now().plusDays(today ? 0 : 12).toString());
                values.put("title:" + FIXTURE_WIDGET_ID,
                    today ? "Fixture event today" : "Fixture event in twelve days");
                fixtures.add(new FixtureCase("Countdown", shape + "-" + (today ? "today" : "future")
                    + "-page" + page, slots[i], values));
            }
        }
        fixtures.add(new FixtureCase("WeatherWide", "forecast", new SizeF(373.7143f, 208.85715f),
            Collections.emptyMap()));
        fixtures.add(new FixtureCase("WeatherCombo", "forecast", PLACED_LARGE[0], Collections.emptyMap()));
        fixtures.add(new FixtureCase("QuickCard", "weather", PLACED_LARGE[0], Collections.emptyMap()));
        fixtures.add(new FixtureCase("QuickTransparent", "weather", PLACED_QUICK[0], Collections.emptyMap()));
        return fixtures;
    }

    private static final class FixtureContext extends ContextWrapper {
        private final SharedPreferences prefs;
        FixtureContext(Context base, Map<String, Object> values) {
            super(base);
            prefs = new ReadOnlyPreferences(values);
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return name.equals("widgets") ? prefs : super.getSharedPreferences(name, mode);
        }
        @Override public Context getApplicationContext() { return this; }
    }

    private static final class ReadOnlyPreferences implements SharedPreferences {
        private final Map<String, Object> values;
        ReadOnlyPreferences(Map<String, Object> values) {
            this.values = Collections.unmodifiableMap(new HashMap<>(values));
        }
        @Override public Map<String, ?> getAll() { return new HashMap<>(values); }
        @Override public String getString(String key, String fallback) {
            return values.containsKey(key) ? (String) values.get(key) : fallback;
        }
        @SuppressWarnings("unchecked")
        @Override public Set<String> getStringSet(String key, Set<String> fallback) {
            Set<String> value = values.containsKey(key) ? (Set<String>) values.get(key) : fallback;
            return value == null ? null : Collections.unmodifiableSet(new java.util.HashSet<>(value));
        }
        @Override public int getInt(String key, int fallback) {
            return values.containsKey(key) ? (Integer) values.get(key) : fallback;
        }
        @Override public long getLong(String key, long fallback) {
            return values.containsKey(key) ? (Long) values.get(key) : fallback;
        }
        @Override public float getFloat(String key, float fallback) {
            return values.containsKey(key) ? (Float) values.get(key) : fallback;
        }
        @Override public boolean getBoolean(String key, boolean fallback) {
            return values.containsKey(key) ? (Boolean) values.get(key) : fallback;
        }
        @Override public boolean contains(String key) { return values.containsKey(key); }
        @Override public Editor edit() { throw new UnsupportedOperationException("Fixture preferences are read-only"); }
        @Override public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
        @Override public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {}
    }

    private static void checkCompassArtwork(Context app, File previews, String label,
            String kind, SizeF dpSize, float bearing, boolean night) throws Exception {
        float density = app.getResources().getDisplayMetrics().density;
        int requestedWidth = Math.round(dpSize.getWidth() * density);
        int requestedHeight = Math.round(dpSize.getHeight() * density);
        Bitmap image = CompassArtwork.render(app, kind, requestedWidth, requestedHeight, bearing);
        try {
            saveBitmap(previews, label, image);
            float ratio = Math.min(1f, 768f / Math.max(requestedWidth, requestedHeight));
            if (image.getWidth() != Math.max(1, Math.round(requestedWidth * ratio))
                    || image.getHeight() != Math.max(1, Math.round(requestedHeight * ratio)))
                throw new AssertionError("Compass output size/aspect changed");
            if (image.getDensity() != Bitmap.DENSITY_NONE)
                throw new AssertionError("Compass artwork has density scaling metadata");
            Rect painted = paintedBounds(image);
            if (painted.isEmpty() || Math.abs(painted.width() - painted.height()) > 2)
                throw new AssertionError("Compass circle became " + painted);
            if (Math.abs(painted.exactCenterX() - image.getWidth() / 2f) > 1.5f
                    || Math.abs(painted.exactCenterY() - image.getHeight() / 2f) > 1.5f)
                throw new AssertionError("Compass artwork is not centered: " + painted);
            int bgX = painted.left + Math.round(painted.width() * .75f);
            int bgY = painted.top + Math.round(painted.height() * .65f);
            int background = app.getColor(night ? android.R.color.system_neutral1_900
                : android.R.color.system_neutral1_50);
            if (image.getPixel(bgX, bgY) != background)
                throw new AssertionError("Compass uses wrong light/night background palette");
            // The fixed central arrow is red when pointing north; rotated edge badges are excluded.
            int redArrowPixels = 0;
            int left = painted.left + Math.round(painted.width() * .35f);
            int right = painted.left + Math.round(painted.width() * .65f);
            int top = painted.top + Math.round(painted.height() * .32f);
            int bottom = painted.top + Math.round(painted.height() * .68f);
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                int pixel = image.getPixel(x, y);
                if (android.graphics.Color.red(pixel) > android.graphics.Color.green(pixel) * 1.5f
                        && android.graphics.Color.red(pixel) > android.graphics.Color.blue(pixel) * 1.5f)
                    redArrowPixels++;
            }
            if ((redArrowPixels > 0) != (bearing == 0))
                throw new AssertionError("Compass north/disabled arrow state is wrong, heading=" + bearing);
        } finally {
            image.recycle();
        }
    }

    private static void checkBatteryArtwork(Context app, File previews, String label, boolean small,
            SizeF dpSize, int level, boolean charging, int deviceCount, boolean night) throws Exception {
        float density = app.getResources().getDisplayMetrics().density;
        int requestedWidth = Math.round(dpSize.getWidth() * density);
        int requestedHeight = Math.round(dpSize.getHeight() * density);
        JSONArray devices = new JSONArray();
        String[] names = {"Nothing Ear", "Watch Pro", "Headphones"};
        int[] levels = {87, 45, 67};
        for (int i = 0; i < deviceCount; i++) {
            JSONObject device = new JSONObject();
            device.put("name", names[i]);
            device.put("level", levels[i]);
            devices.put(device);
        }
        Bitmap image = small ? BatteryArtwork.small(app, requestedWidth, level, charging)
            : BatteryArtwork.large(app, requestedWidth, requestedHeight, level, charging, devices);
        try {
            saveBitmap(previews, label, image);
            float ratio = Math.min(1f, 768f / Math.max(requestedWidth, requestedHeight));
            int expectedWidth = Math.max(1, Math.round(requestedWidth * ratio));
            int expectedHeight = Math.max(1, Math.round(requestedHeight * ratio));
            if (image.getWidth() != expectedWidth || image.getHeight() != expectedHeight)
                throw new AssertionError("Artwork size/aspect changed: " + image.getWidth() + "x"
                    + image.getHeight() + ", expected " + expectedWidth + "x" + expectedHeight);
            if (image.getDensity() != Bitmap.DENSITY_NONE)
                throw new AssertionError("Artwork has density scaling metadata");
            Rect painted = paintedBounds(image);
            if (painted.isEmpty() || Math.abs(painted.width() - painted.height()) > 2)
                throw new AssertionError("Circular/square battery artwork became " + painted);
            if (Math.abs(painted.exactCenterX() - image.getWidth() / 2f) > 1.5f
                    || Math.abs(painted.exactCenterY() - image.getHeight() / 2f) > 1.5f)
                throw new AssertionError("Battery artwork is not centered: " + painted);
            // Sample untouched background, beyond the first device cell and inside the outline.
            int bgX = small ? painted.left + Math.round(painted.width() * .18f) : painted.centerX();
            int bgY = painted.top + Math.max(2, Math.round(painted.height() * (small ? .28f : .04f)));
            int expectedBackground = app.getColor(night ? android.R.color.system_neutral1_900
                : android.R.color.system_neutral1_50);
            if (image.getPixel(bgX, bgY) != expectedBackground)
                throw new AssertionError("Battery background uses wrong light/night palette");
            // The top-right part of the phone sector is filled at both tested charge levels.
            float cellLeft = small ? 0 : painted.width() * 13f / 172f;
            float cellTop = small ? 0 : painted.height() * 13f / 172f;
            float cellSide = small ? painted.width() : painted.width() * 70f / 172f;
            int sampleX = painted.left + Math.round(cellLeft + cellSide * .70f);
            int sampleY = painted.top + Math.round(cellTop + cellSide * .154f);
            int sector = image.getPixel(sampleX, sampleY);
            boolean red = android.graphics.Color.red(sector) > android.graphics.Color.green(sector) * 1.5f
                && android.graphics.Color.red(sector) > android.graphics.Color.blue(sector) * 1.5f;
            if (red != (level == 20 && !charging))
                throw new AssertionError("Low-battery/charging sector color is wrong");
        } finally {
            image.recycle();
        }
    }

    private static void saveBitmap(File previews, String label, Bitmap image) throws Exception {
        try (FileOutputStream out = new FileOutputStream(new File(previews, label + ".png"))) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, out))
                throw new AssertionError("Preview compression failed");
        }
    }

    private static Rect paintedBounds(Bitmap image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getPixels(pixels, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
        Rect bounds = new Rect(image.getWidth(), image.getHeight(), 0, 0);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            if (android.graphics.Color.alpha(pixels[y * image.getWidth() + x]) > 8) {
                bounds.left = Math.min(bounds.left, x);
                bounds.top = Math.min(bounds.top, y);
                bounds.right = Math.max(bounds.right, x + 1);
                bounds.bottom = Math.max(bounds.bottom, y + 1);
            }
        }
        return bounds;
    }

    private static final class HostCase {
        final String label;
        final SizeF size;
        final Bundle options;
        final SizeF startSize;
        HostCase(String label, SizeF size, Bundle options, SizeF startSize) {
            this.label = label;
            this.size = size;
            this.options = options;
            this.startSize = startSize;
        }
    }

    private static List<HostCase> hostCases(String[] def) {
        List<HostCase> cases = new ArrayList<>();
        for (String shape : SHAPES) {
            int[] slot = slotDp(def, shape);
            SizeF size = new SizeF(slot[0], slot[1]);
            cases.add(new HostCase(shape, size, sizeOptions(new SizeF[]{size}, true), size));
        }
        String name = def[0];
        if (name.equals("AnalogBold") || name.equals("Battery") || name.equals("Steps"))
            addPlacedCases(cases, "placed-large", PLACED_LARGE);
        if (name.equals("Date") || name.equals("Battery") || name.equals("ScreenFace")
                || name.equals("DigitalWide") || name.equals("StepsDaily") || name.equals("CompassSmall"))
            addPlacedCases(cases, "placed-compact", PLACED_COMPACT);
        if (name.equals("QuickTransparent")) addPlacedCases(cases, "placed-quick", PLACED_QUICK);
        return cases;
    }

    private static void addPlacedCases(List<HostCase> cases, String label, SizeF[] sizes) {
        Bundle responsive = sizeOptions(sizes, true);
        for (int i = 0; i < sizes.length; i++) {
            String orientation = i == 2 ? "landscape" : i == 1 ? "portrait-short" : "portrait";
            SizeF alternate = sizes[i == 2 ? 0 : 2];
            cases.add(new HostCase(label + "-responsive-" + orientation,
                sizes[i], responsive, alternate));
        }
        // Legacy hosts report integer bounds without the explicit list of physical size pairs.
        Bundle legacy = sizeOptions(sizes, false);
        SizeF portrait = new SizeF(legacy.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            legacy.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT));
        SizeF landscape = new SizeF(legacy.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH),
            legacy.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT));
        cases.add(new HostCase(label + "-legacy-portrait", portrait, legacy, landscape));
        cases.add(new HostCase(label + "-legacy-landscape", landscape, legacy, portrait));
    }

    private static Bundle sizeOptions(SizeF[] sizes, boolean explicitPairs) {
        float minWidth = Float.MAX_VALUE, minHeight = Float.MAX_VALUE;
        float maxWidth = 0, maxHeight = 0;
        ArrayList<SizeF> pairs = new ArrayList<>();
        for (SizeF size : sizes) {
            minWidth = Math.min(minWidth, size.getWidth());
            minHeight = Math.min(minHeight, size.getHeight());
            maxWidth = Math.max(maxWidth, size.getWidth());
            maxHeight = Math.max(maxHeight, size.getHeight());
            pairs.add(size);
        }
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, (int) minWidth);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, (int) minHeight);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, (int) maxWidth);
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, (int) maxHeight);
        if (explicitPairs) options.putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, pairs);
        return options;
    }

    private static AppWidgetProviderInfo findProvider(Context app, String name) {
        for (AppWidgetProviderInfo info : Widgets.providers(app))
            if (Widgets.kind(info).equals(name)) return info;
        throw new AssertionError("Missing provider " + name);
    }

    private static int[] slotDp(String[] def, String shape) {
        // Representative Pixel launcher slots are rectangular even for 1x1 and 2x2 widgets.
        int width = Integer.parseInt(def[3]) * 90 - 16;
        int height = Integer.parseInt(def[4]) * 108 - 20;
        if (shape.equals("wide")) width += 70;
        if (shape.equals("tall")) height += 72;
        return new int[]{width, height};
    }

    private static void checkCase(Context app, Context hostContext, AppWidgetProviderInfo info,
            String name, HostCase test, boolean night, Typeface dot, File previews) throws Exception {
        checkCase(app, hostContext, info, name, test, night, dot, previews, -1);
    }

    private static void checkCase(Context app, Context hostContext, AppWidgetProviderInfo info,
            String name, HostCase test, boolean night, Typeface dot, File previews, int widgetId) throws Exception {
        RemoteViews views = Widgets.render(app, name, widgetId, test.options);
        AppWidgetHostView host = new AppWidgetHostView(hostContext);
        host.setAppWidget(0, info);
        host.setOnLightBackground(!night);
        // Prime the physical host size without publishing options or allocating a widget ID.
        layoutHost(host, test.startSize);
        host.updateAppWidget(views);
        layoutHost(host, test.startSize);
        // Responsive cases switch orientation on the same host, forcing framework map selection.
        layoutHost(host, test.size);
        float density = hostContext.getResources().getDisplayMetrics().density;
        int width = host.getWidth(), height = host.getHeight();

        // Save even a failing layout; this changes only disposable test cache, never settings.
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            host.draw(new Canvas(image));
            String previewName = test.label.startsWith("fixture-")
                ? test.label + (night ? "-night" : "-light")
                : name + "-" + test.label + (night ? "" : "-light");
            try (FileOutputStream out = new FileOutputStream(
                    new File(previews, previewName + ".png"))) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new AssertionError("Preview compression failed");
            }
        } finally {
            image.recycle();
        }

        View root = host.findViewById(dev.dipa.widgets.R.id.root);
        if (root == null) throw new AssertionError("Foreign host failed to inflate widget");
        View content = host.findViewById(dev.dipa.widgets.R.id.content);
        if (content == null) throw new AssertionError("Missing geometry content container");
        Rect available = new Rect(host.getPaddingLeft(), host.getPaddingTop(),
            width - host.getPaddingRight(), height - host.getPaddingBottom());
        Rect contentBounds = bounds(host, content);
        if (content.getWidth() <= 0 || content.getHeight() <= 0)
            throw new AssertionError("Empty content " + contentBounds);
        requireInside(contentBounds, available, "Content exceeds launcher slot");
        int expectedWidth = Math.round(test.size.getWidth() * density);
        int expectedHeight = Math.round(test.size.getHeight() * density);
        if (squareContent(name)) {
            if (content.getWidth() != content.getHeight())
                throw new AssertionError("Square/circular content became " + content.getWidth()
                    + "x" + content.getHeight() + "px");
            int expected = Math.min(expectedWidth, expectedHeight);
            if (Math.abs(content.getWidth() - expected) > 2)
                throw new AssertionError("Content does not fill available square: "
                    + content.getWidth() + "px, expected " + expected + "px");
        } else if (Math.abs(content.getWidth() - expectedWidth) > 2
                || Math.abs(content.getHeight() - expectedHeight) > 2) {
            throw new AssertionError("Wrong responsive size selected: " + content.getWidth() + "x"
                + content.getHeight() + "px, expected " + expectedWidth + "x" + expectedHeight + "px");
        }
        if (Math.abs(contentBounds.exactCenterX() - available.exactCenterX()) > 1.5f
                || Math.abs(contentBounds.exactCenterY() - available.exactCenterY()) > 1.5f)
            throw new AssertionError("Content is not centered in launcher slot: " + contentBounds);

        for (int id : NUMERIC_IDS) {
            View candidate = host.findViewById(id);
            if (!(candidate instanceof TextView) || !visible(candidate)) continue;
            // Quick Look and World use original native Ndot captions; other captions are bitmaps.
            if (id == dev.dipa.widgets.R.id.subtitle && !(candidate instanceof TextClock)
                    && !name.startsWith("Quick") && !name.equals("World")) continue;
            TextView text = (TextView) candidate;
            String family = name.equals("Date") ? "ntype82"
                : name.startsWith("Quick") || (name.equals("World") && id == dev.dipa.widgets.R.id.subtitle)
                ? "nothingdot" : "nothingdot57aligned";
            if (!name.equals("ScreenFace")) checkFont(name, id, text.getTypeface(), family);
            if (text instanceof TextClock && (text.getText() == null || text.getText().length() == 0))
                throw new AssertionError("Native TextClock is empty after rendering, id=" + id);
            checkText(host, contentBounds, text);
        }
        checkImages(host, contentBounds, content);
    }

    private static void checkFont(String name, int id, Typeface actual, String family) {
        Typeface expected = Typeface.create(Typeface.create(family, Typeface.NORMAL), 400, false);
        boolean identity = actual.equals(expected);
        boolean style = actual.getStyle() == expected.getStyle() && actual.getWeight() == expected.getWeight();
        GlyphComparison glyphs = identity ? new GlyphComparison(true, "identical Typeface")
            : compareGlyphs(actual, expected);
        String details = name + " id=" + id + " family=" + family + " actual=" + actual
            + " weight=" + actual.getWeight() + " style=" + actual.getStyle()
            + " expected=" + expected + " weight=" + expected.getWeight()
            + " style=" + expected.getStyle() + " identity=" + identity + " " + glyphs.details;
        if (!identity) android.util.Log.i("NothingWidgetFontTest", details);
        if (!style || !glyphs.equal)
            throw new AssertionError("Original Nothing font differs in foreign host: " + details);
    }

    private static final class GlyphComparison {
        final boolean equal;
        final String details;
        GlyphComparison(boolean equal, String details) { this.equal = equal; this.details = details; }
    }

    private static GlyphComparison compareGlyphs(Typeface actual, Typeface expected) {
        String sample = "0088WEDNESDAY";
        Paint actualPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        actualPaint.setTypeface(actual); actualPaint.setTextSize(40f); actualPaint.setColor(0xffffffff);
        Paint expectedPaint = new Paint(actualPaint); expectedPaint.setTypeface(expected);
        float actualAdvance = actualPaint.measureText(sample), expectedAdvance = expectedPaint.measureText(sample);
        Paint.FontMetrics a = actualPaint.getFontMetrics(), e = expectedPaint.getFontMetrics();
        float[] actualMetrics = {a.top, a.ascent, a.descent, a.bottom, a.leading};
        float[] expectedMetrics = {e.top, e.ascent, e.descent, e.bottom, e.leading};
        boolean metrics = true;
        for (int i = 0; i < actualMetrics.length; i++)
            metrics &= Math.abs(actualMetrics[i] - expectedMetrics[i]) < .001f;
        int width = Math.max(1, (int) Math.ceil(Math.max(actualAdvance, expectedAdvance)) + 16);
        float baseline = 8f - Math.min(a.top, e.top);
        int height = Math.max(1, (int) Math.ceil(baseline + Math.max(a.bottom, e.bottom)) + 8);
        Bitmap actualImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Bitmap expectedImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        boolean pixels;
        try {
            new Canvas(actualImage).drawText(sample, 8f, baseline, actualPaint);
            new Canvas(expectedImage).drawText(sample, 8f, baseline, expectedPaint);
            pixels = actualImage.sameAs(expectedImage);
        } finally {
            actualImage.recycle(); expectedImage.recycle();
        }
        boolean advance = Math.abs(actualAdvance - expectedAdvance) < .001f;
        return new GlyphComparison(pixels && advance && metrics,
            "glyphPixels=" + pixels + " advance=" + actualAdvance + "/" + expectedAdvance
            + " metrics=" + java.util.Arrays.toString(actualMetrics) + "/"
            + java.util.Arrays.toString(expectedMetrics));
    }

    private static void layoutHost(AppWidgetHostView host, SizeF size) {
        float density = host.getResources().getDisplayMetrics().density;
        // Widget option sizes exclude the host's own default padding.
        int width = Math.round(size.getWidth() * density) + host.getPaddingLeft() + host.getPaddingRight();
        int height = Math.round(size.getHeight() * density) + host.getPaddingTop() + host.getPaddingBottom();
        host.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        host.layout(0, 0, width, height);
    }

    private static void checkImages(AppWidgetHostView host, Rect contentBounds, View view) {
        if (!visible(view)) return;
        if (view instanceof ImageView) {
            ImageView image = (ImageView) view;
            requireInside(bounds(host, image), contentBounds, "Image view exceeds content");
            Drawable drawable = image.getDrawable();
            if (drawable != null && drawable.getBounds().width() > 0 && drawable.getBounds().height() > 0
                    && image.getScaleType() != ImageView.ScaleType.CENTER_CROP) {
                RectF drawn = new RectF(drawable.getBounds());
                image.getImageMatrix().mapRect(drawn);
                drawn.offset(image.getPaddingLeft(), image.getPaddingTop());
                if (drawn.left < image.getPaddingLeft() - 1 || drawn.top < image.getPaddingTop() - 1
                        || drawn.right > image.getWidth() - image.getPaddingRight() + 1
                        || drawn.bottom > image.getHeight() - image.getPaddingBottom() + 1)
                    throw new AssertionError("Drawable image is clipped, id=" + image.getId() + ": " + drawn);
                float nativeAspect = drawable.getBounds().width() / (float) drawable.getBounds().height();
                float drawnAspect = drawn.width() / drawn.height();
                if (Math.abs(drawnAspect / nativeAspect - 1) > .005f)
                    throw new AssertionError("Image aspect ratio distorted, id=" + image.getId());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) checkImages(host, contentBounds, group.getChildAt(i));
        }
    }

    private static boolean squareContent(String name) {
        return name.equals("AnalogBold") || name.equals("AnalogScale")
            || name.equals("BatterySmall") || name.equals("WeatherCircle") || name.equals("PhotoCircle");
    }

    private static Rect bounds(AppWidgetHostView host, View view) {
        Rect rect = new Rect();
        view.getDrawingRect(rect);
        host.offsetDescendantRectToMyCoords(view, rect);
        return rect;
    }

    private static boolean visible(View view) {
        // isShown() also needs window attachment, which this view-only host intentionally lacks.
        for (View current = view; current != null;) {
            if (current.getVisibility() != View.VISIBLE) return false;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return true;
    }

    private static void requireInside(Rect rect, Rect available, String message) {
        if (rect.left < available.left || rect.top < available.top
                || rect.right > available.right || rect.bottom > available.bottom)
            throw new AssertionError(message + ": " + rect + " within " + available);
    }

    private static void checkText(AppWidgetHostView host, Rect contentBounds, TextView text) {
        CharSequence value = text.getText();
        if (value == null || value.length() == 0) return;
        requireInside(bounds(host, text), contentBounds, "Numeric/date view exceeds content");
        Layout layout = text.getLayout();
        if (layout == null || layout.getLineCount() == 0)
            throw new AssertionError("Numeric/date text has no layout: " + value);
        int innerWidth = text.getWidth() - text.getCompoundPaddingLeft() - text.getCompoundPaddingRight();
        int innerHeight = text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom();
        if (layout.getHeight() > innerHeight + 1)
            throw new AssertionError("Numeric/date text is vertically clipped: " + value);
        for (int line = 0; line < layout.getLineCount(); line++) {
            if (layout.getEllipsisCount(line) != 0 || layout.getLineWidth(line) > innerWidth + 1)
                throw new AssertionError("Numeric/date text is truncated: " + value);
            int start = layout.getLineStart(line), end = layout.getLineEnd(line);
            while (end > start && (value.charAt(end - 1) == '\n' || value.charAt(end - 1) == '\r')) end--;
            if (end > start) {
                Rect ink = new Rect();
                text.getPaint().getTextBounds(value.toString(), start, end, ink);
                float origin = text.getTotalPaddingLeft() + layout.getLineLeft(line) - text.getScrollX();
                float baseline = text.getTotalPaddingTop() + layout.getLineBaseline(line) - text.getScrollY();
                RectF glyphs = new RectF(ink);
                glyphs.offset(origin, baseline);
                if (glyphs.left < text.getCompoundPaddingLeft() - 1
                        || glyphs.top < text.getCompoundPaddingTop() - 1
                        || glyphs.right > text.getWidth() - text.getCompoundPaddingRight() + 1
                        || glyphs.bottom > text.getHeight() - text.getCompoundPaddingBottom() + 1)
                    throw new AssertionError("Numeric/date glyph ink is clipped: " + value + ": " + glyphs);
            }
        }
        if (layout.getLineEnd(layout.getLineCount() - 1) < value.length())
            throw new AssertionError("Numeric/date text lost characters: " + value);
    }
}
