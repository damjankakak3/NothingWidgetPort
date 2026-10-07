# Validation

Build the main app first, then build the instrumentation APK with the same SDK and signing key:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
bash tools/build.sh
bash tools/build-tests.sh
adb install -r artifacts/NothingWidget-0.3.1.apk
adb install -r build/test/tests.apk
adb shell am instrument -w -e fixtures true \
  dev.dipa.widgets.tests/dev.dipa.widgets.tests.WidgetInstrumentation
adb uninstall dev.dipa.widgets.tests
```

If multiple devices are connected, add `-s SERIAL` to each `adb` command. Use a device or emulator running Android 16. If you already have a published build installed, a different local signing key will prevent installation as an update; use a separate test device or emulator.

`WidgetInstrumentation` checks 31 providers across portrait and landscape sizes in light and night mode. It tests circle bounds, font glyphs, image proportions, responsive size selection, and legacy size fallback. Additional battery and compass states exercise the renderers. The `fixtures` option adds 96 previews backed by immutable in-memory data; they do not modify widget settings or start step tracking.

The recorded Android 16 phone run passed 578 checks:

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
