#!/usr/bin/env bash
# Builds omni.apk with the Android SDK build-tools (no Gradle). Usage: ./build.sh <jdk-home>
set -euo pipefail
JDK="${1:?usage: build.sh <jdk-home>}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
BT=$SDK/build-tools/35.0.0
AJAR=$SDK/platforms/android-35/android.jar
export JAVA_HOME="$JDK" PATH="$JDK/bin:$PATH"
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes
# TMDB token is baked in at build time from tmdb.token (git-ignored, optional).
TOKEN=""; [ -f tmdb.token ] && TOKEN="$(tr -d ' \n\r' < tmdb.token)"
mkdir -p build/gen/com/rasha/omni
printf 'package com.rasha.omni;\nfinal class Secrets { static final String TMDB = "%s"; }\n' "$TOKEN" > build/gen/com/rasha/omni/Secrets.java
javac --release 8 -Xlint:-options -cp "$AJAR" -d build/classes $(find src build/gen -name '*.java')
"$BT/d8" --release --min-api 23 --lib "$AJAR" --output build $(find build/classes -name '*.class')
"$BT/aapt2" link -o build/base.apk -I "$AJAR" --manifest AndroidManifest.xml --min-sdk-version 23 --target-sdk-version 30 -A assets
python3 -I - <<'PY'
import zipfile
z = zipfile.ZipFile("build/base.apk", "a"); z.write("build/classes.dex", "classes.dex"); z.close()
PY
"$BT/zipalign" -f 4 build/base.apk build/aligned.apk
[ -f omni.keystore ] || keytool -genkeypair -keystore omni.keystore -storepass android -keypass android \
    -alias aura -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=OMNI" >/dev/null 2>&1
"$BT/apksigner" sign --ks omni.keystore --ks-pass pass:android --key-pass pass:android --out omni.apk build/aligned.apk
ls -l omni.apk
