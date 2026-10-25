#!/usr/bin/env bash
# JVM unit tests for the pure logic (no Android, no device). Usage: test/run.sh <jdk-home>
set -euo pipefail
JDK="${1:?usage: test/run.sh <jdk-home>}"
cd "$(dirname "$0")/.."
OUT=build/test-classes; rm -rf "$OUT"; mkdir -p "$OUT"
"$JDK/bin/javac" -d "$OUT" src/com/rasha/omni/Accordion.java src/com/rasha/omni/SpringMath.java \
    src/com/rasha/omni/ListOps.java src/com/rasha/omni/AppFilter.java src/com/rasha/omni/CategoryRules.java src/com/rasha/omni/DeviceTypes.java src/com/rasha/omni/PtvRules.java src/com/rasha/omni/WeatherCodes.java src/com/rasha/omni/NetKind.java src/com/rasha/omni/PtvModel.java src/com/rasha/omni/PtvSearch.java src/com/rasha/omni/PtvUsage.java src/com/rasha/omni/ChannelSort.java src/com/rasha/omni/PreviewPolicy.java src/com/rasha/omni/Dominant.java src/com/rasha/omni/LumaVeil.java src/com/rasha/omni/WallpaperRules.java src/com/rasha/omni/WeatherFormat.java src/com/rasha/omni/ControlRows.java src/com/rasha/omni/SearchHit.java src/com/rasha/omni/EventRules.java src/com/rasha/omni/SetupFlow.java src/com/rasha/omni/Intents.java src/com/rasha/omni/KeyMap.java test/com/rasha/omni/*.java
"$JDK/bin/java" -cp "$OUT" com.rasha.omni.RunAll
