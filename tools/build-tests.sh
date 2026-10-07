#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
task_tools="$task_sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
task_android="$task_sdk/platforms/android-36/android.jar"
task_keystore="${KEYSTORE_PATH:-$task_root/source/local-widgets.keystore}"
export KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-localwidgets}"
task_alias="${KEYSTORE_ALIAS:-personal}"
mkdir -p "$task_root/build/test/classes" "$task_root/build/test/dex"
cat > "$task_root/build/test/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="dev.dipa.widgets.tests"><uses-sdk android:minSdkVersion="31" android:targetSdkVersion="36"/><queries><package android:name="com.nothing.hearthstone"/></queries><application android:label="NothingWidget tests"/><instrumentation android:name="dev.dipa.widgets.tests.WidgetInstrumentation" android:targetPackage="dev.dipa.widgets"/><instrumentation android:name="dev.dipa.widgets.tests.OriginalReferenceInstrumentation" android:targetPackage="dev.dipa.widgets.tests"/></manifest>
XML
javac --release 11 -encoding UTF-8 -cp "$task_android:$task_root/build/classes" -d "$task_root/build/test/classes" "$task_root/tools/WidgetInstrumentation.java" "$task_root/tools/OriginalReferenceInstrumentation.java"
jar cf "$task_root/build/test/tests.jar" -C "$task_root/build/test/classes" .
"$task_tools/d8" --min-api 31 --lib "$task_android" --classpath "$task_root/build/classes.jar" --output "$task_root/build/test/dex" "$task_root/build/test/tests.jar"
"$task_tools/aapt2" link -I "$task_android" --manifest "$task_root/build/test/AndroidManifest.xml" -o "$task_root/build/test/unsigned.apk"
python3 - "$task_root" <<'PY'
import zipfile,sys
from pathlib import Path
r=Path(sys.argv[1])
with zipfile.ZipFile(r/'build/test/unsigned.apk','a') as z:z.write(r/'build/test/dex/classes.dex','classes.dex')
PY
"$task_tools/zipalign" -f 4 "$task_root/build/test/unsigned.apk" "$task_root/build/test/aligned.apk"
"$task_tools/apksigner" sign --ks "$task_keystore" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --ks-key-alias "$task_alias" --out "$task_root/build/test/tests.apk" "$task_root/build/test/aligned.apk"
