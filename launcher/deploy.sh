#!/usr/bin/env bash
# Installs the OMNI APK (default: the Gradle release build, or APK=path) on the TV and AOT-compiles it, so the first use isn't run by the slow JIT.
set -euo pipefail
TV="${1:-${TV:?usage: deploy.sh <tv-ip>:5555  (or set TV=...)}}"
export TV
adb connect "$TV" >/dev/null
adb -s "$TV" install -r "${APK:-$(dirname "$0")/../android/app/build/outputs/apk/release/app-release.apk}"
adb -s "$TV" shell cmd package compile -m speed -f com.rasha.omni
if python3 -I "$(dirname "$0")/docs/tvguard.py"; then
  adb -s "$TV" shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null
else
  echo "installed; not bringing OMNI to the front because the TV is in use"
fi
