# Roadmap (plans, not features)

Nothing on this page exists yet unless it says so.

## Device-class profiles ("responsive and dynamic")
Today the launcher is tuned for one weak TV, so it avoids blur and heavy animation. Plan: detect a device class at start-up (RAM, CPU ABI, GPU/API level, measured frame time over the first seconds)
and pick a profile, always user-overridable in Settings.
* **Lite**: what exists now. Springs, flat glass, no live blur, small image caches. Target: 1 GB RAM / 720p.
* **Premium**: for 2 GB+ RAM, 64-bit, 4K TVs: real backdrop blur (RenderEffect on API 31+), larger caches, richer transitions, video-backed hero. Needs a device to build and measure on; **no premium TV is available to the author, so contributors with one are very welcome**.
Rule that stays: every effect must be measured on a real device (frame-time p95) before it ships; fall back automatically when it janks.

## Slim down (optional, reversible)
A script that lists pre-installed apps that are safe to hide (not system-critical), asks, uses `pm disable-user --user 0` only, and writes an undo file. Never uninstalls, never touches the TV maker's input/tuner stack.

## Simplified "just works" APK
A single signed APK for people who do not want a toolchain: first-run wizard, no TMDB key inside (server provides metadata), prebuilt releases on GitHub. Needs a release process and signing-key policy first.

## Compatibility
Test on more TVs (Sony, TCL, Philips, Xiaomi, Chromecast with Google TV, Nvidia Shield, Fire TV is different) and keep a table of results in `docs/COMPATIBILITY.md`.

## Library
Shows from the server library, manual re-match UI, subtitle picker, scan button on the TV, launcher shelves fed directly by server progress.
