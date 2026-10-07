# Validation

Build the main app first, then build the instrumentation APK with the same SDK and signing key:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
bash tools/build.sh
bash tools/build-tests.sh
adb install -r artifacts/NothingWidget-0.3.2.apk
adb install -r build/test/tests.apk
adb shell am instrument -w -e fixtures true \
  dev.dipa.widgets.tests/dev.dipa.widgets.tests.WidgetInstrumentation
adb uninstall dev.dipa.widgets.tests
```

If multiple devices are connected, add `-s SERIAL` to each `adb` command. Use a device or emulator running Android 16. If you already have a published build installed, a different local signing key will prevent installation as an update; use a separate test device or emulator.

`WidgetInstrumentation` checks 31 providers across portrait and landscape sizes in light and night mode. It tests circle bounds, font glyphs, image proportions, responsive size selection, and legacy size fallback. Additional battery and compass states exercise the renderers. The `fixtures` option adds 96 previews backed by immutable in-memory data; they do not modify widget settings or start step tracking.

The Android 16 phone baseline (0.3.0) and the emulator run (0.3.2) each passed 578 checks:

| Checks | Count |
| --- | ---: |
| Actual and nominal widget-host layouts | 286 |
| Battery states | 112 |
| Compass states | 84 |
| Populated fixtures | 96 |

Exact native font checks need the ROM families `nothingdot57aligned`, `nothingdot`, `ntype82`, and the other families requested by the layouts. A stock emulator without these fonts can fail font checks even when the app builds and runs. The app does not change system font configuration.

`OriginalReferenceInstrumentation` is a separate comparison runner. It requires the unmodified donor APK (`com.nothing.hearthstone`) installed in an isolated Android 16 emulator with the original named font families. It inflates the donor's own views and calls their setters. The recorded reference run rendered 60 night and 60 light cases successfully.

```bash
adb shell am instrument -w \
  dev.dipa.widgets.tests/dev.dipa.widgets.tests.OriginalReferenceInstrumentation
```

These tests verify rendering and selected data logic. They do not measure battery use or prove that every Nothing OS animation and integration behaves identically.

## Runtime regressions

Run these stateful checks on a disposable Android 16 emulator. The runner rejects physical devices and requires the `isolated` flag. It temporarily binds test widgets and changes weather preferences, then removes the widgets and restores the saved preferences. It needs internet access for two real forecast requests.

```bash
adb shell pm grant dev.dipa.widgets android.permission.BLUETOOTH_CONNECT
adb shell pm grant dev.dipa.widgets android.permission.POST_NOTIFICATIONS
adb shell am instrument -w -e isolated true \
  dev.dipa.widgets.tests/dev.dipa.widgets.tests.RuntimeInstrumentation
```

The recorded run passed 29 checks. These include 30 repeated schedule calls without replacing the periodic job, two system-dispatched weather refreshes while the cache is fresh, a stale-cache catch-up job, a live compass update received by an AppWidgetHost, continued sensing on the home screen, sensor suspension and resumption across screen-off, and Bluetooth cache, widget-update, disconnect, and invalid-level handling.

Bluetooth reports in this test use a simulated speaker. The test also invokes the real Android cached-battery getter for an unconnected device; it does not verify a particular physical speaker. Some ROMs restrict that getter, so the app retains its broadcast fallback.
