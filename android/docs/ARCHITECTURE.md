# OMNI OS: native app architecture

Decision (2026-10-24): every **new standalone OMNI app is Kotlin + Jetpack Compose** (tv-material 1.0), an independently buildable APK with its own
application id. The existing Java Views **launcher is not rewritten**; it stays in `omni-tv/` and launches these apps by package name.

## Audit (verified on this machine and the target TV)
| Item | Finding |
|---|---|
| Launcher build | raw SDK tools (`build.sh`: javac, d8, aapt2), no Gradle, no Kotlin |
| This repo | Gradle 8.10.2 wrapper, AGP 8.7.3, Kotlin 2.0.21 (Compose compiler plugin), compileSdk 35, minSdk 26, targetSdk 34, JDK 17 |
| Target TV | Android 11 (API 30), **armeabi-v7a only**, 4 cores, 907 MB RAM (a low-RAM device), 1280x720 at 213 dpi |
| Package names | launcher `com.rasha.omni`; apps `com.rasha.omni.<name>` (reference, weather, ...) |

## Modules
```
core/common    UiState (Loading/Empty/Error/Content+age), AppDispatchers            (pure Kotlin + coroutines)
core/network   HttpJson: https-only, disk cache with TTL, stale-on-failure, size limit, always on IO
core/design    tokens (identical values to Theme.java), Geist theme, OmniScreen, OmniCard, omniFocus, GlassPanel, StateHost
core/weather   weather data + domain + presentation model (no Compose), JVM unit tests
apps/reference proves the foundation on the TV
apps/weather   the weather app
```
Rules: apps depend on `core/*`, never on each other; `core/*` never depends on an app; I/O only on `AppDispatchers.io`; screens collect state with
`collectAsStateWithLifecycle`; presentation strings are built once in the ViewModel, not in composables; animation that changes every frame is read
only in the draw phase (no recomposition); focus is drawn with `drawBehind`, not state in content.

## Performance conventions (learned the hard way on this TV)
- Never change a variable font's weight per frame (each change rebuilds a Typeface: this alone made the Java weather app take 106 ms per frame).
  One `Font` entry per weight, created once.
- No blur, no layer-wide blend modes, no per-frame shadows: glass is a translucent fill + faint tint + hairline rim.
- Measure on the TV (`dumpsys gfxinfo`, `am start -W`, `dumpsys meminfo`) before calling anything done. Release builds only (R8), AOT-compiled
  after install (`cmd package compile -m speed -f <pkg>`).

## Measured on the TV (reference app, release, AOT)
cold start 440 ms (first ever launch 829 ms), PSS 37-43 MB, 0% janky frames, p95 7 ms while moving D-pad focus.

## Build, install
    cd omni-os && ./gradlew :apps:<name>:assembleRelease      # JDK 17 via org.gradle.java.home in gradle.properties
    adb install -r apps/<name>/build/outputs/apk/release/<name>-release.apk
    adb shell cmd package compile -m speed -f com.rasha.omni.<name>
Local signing uses `omni-os.keystore` (git-ignored; local-install password only, not a secret that protects anything public).
