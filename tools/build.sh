#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
task_tools="$task_sdk/build-tools/${ANDROID_BUILD_TOOLS_VERSION:-35.0.0}"
task_android="$task_sdk/platforms/android-36/android.jar"
task_keystore="${KEYSTORE_PATH:-$task_root/source/local-widgets.keystore}"
export KEYSTORE_PASSWORD="${KEYSTORE_PASSWORD:-localwidgets}"
task_alias="${KEYSTORE_ALIAS:-personal}"
mkdir -p "$task_root/build/classes" "$task_root/build/generated" "$task_root/build/dex" "$task_root/artifacts" "$task_root/source"
if [[ -d "$task_root/analysis/widgets-decoded/res" ]]; then
    python3 "$task_root/tools/prepare_app.py"
elif [[ ! -f "$task_root/app/AndroidManifest.xml" ]]; then
    printf '%s\n' 'Missing donor resources and generated app resources.' >&2
    exit 1
fi
task_version="$(python3 - "$task_root/app/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
print(ET.parse(sys.argv[1]).getroot().get('{http://schemas.android.com/apk/res/android}versionName'))
PY
)"
task_apk="$task_root/artifacts/NothingWidget-$task_version.apk"
"$task_tools/aapt2" compile --dir "$task_root/app/res" -o "$task_root/build/resources.zip"
"$task_tools/aapt2" link -I "$task_android" --manifest "$task_root/app/AndroidManifest.xml" --java "$task_root/build/generated" -o "$task_root/build/resources.apk" "$task_root/build/resources.zip"
find "$task_root/app/src" "$task_root/build/generated" -name '*.java' -print > "$task_root/build/sources.list"
javac --release 11 -encoding UTF-8 -classpath "$task_android" -d "$task_root/build/classes" @"$task_root/build/sources.list"
jar cf "$task_root/build/classes.jar" -C "$task_root/build/classes" .
"$task_tools/d8" --release --min-api 31 --lib "$task_android" --output "$task_root/build/dex" "$task_root/build/classes.jar"
cp "$task_root/build/resources.apk" "$task_root/build/unsigned.apk"
python3 - "$task_root" <<'PY'
from pathlib import Path
import sys,zipfile
r=Path(sys.argv[1])
with zipfile.ZipFile(r/'build/unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for p in (r/'build/dex').glob('*.dex'):z.write(p,p.name)
PY
"$task_tools/zipalign" -f -P 16 4 "$task_root/build/unsigned.apk" "$task_root/build/aligned.apk"
if [[ ! -f "$task_keystore" ]]; then
    keytool -genkeypair -keystore "$task_keystore" -storepass:env KEYSTORE_PASSWORD -keypass:env KEYSTORE_PASSWORD -alias "$task_alias" -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=NothingWidget, O=Local build'
    chmod 600 "$task_keystore"
fi
"$task_tools/apksigner" sign --ks "$task_keystore" --ks-pass env:KEYSTORE_PASSWORD --key-pass env:KEYSTORE_PASSWORD --ks-key-alias "$task_alias" --out "$task_apk" "$task_root/build/aligned.apk"
"$task_tools/apksigner" verify --verbose --print-certs "$task_apk"
"$task_tools/zipalign" -c -P 16 4 "$task_apk"
mkdir -p "$task_root/build/logic-tests"
javac -d "$task_root/build/logic-tests" "$task_root/app/src/dev/dipa/widgets/Logic.java" "$task_root/tools/LogicTest.java"
java -cp "$task_root/build/logic-tests" LogicTest
(cd "$task_root/artifacts" && sha256sum "NothingWidget-$task_version.apk" > SHA256SUMS)
