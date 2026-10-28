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

## Other TV platforms: Tizen (Samsung), webOS (LG), Titan OS (Philips, others), VIDAA (Hisense)
These are not Android, so the launcher APK cannot run on them, and none of them lets an app replace the TV's home screen. What is realistic is a **separate, smaller web app** per platform (HTML5/JS), sharing the server protocol and the data model, not the launcher:
* **What it could be**: "OMNI for webOS / Tizen / Titan OS / VIDAA": Live TV from the built-in channel list (direct streams, HLS), Cinema from the server library or free sources, Weather. No launcher, no HDMI/tuner integration beyond what each platform's app API exposes.
* **One codebase first**: write it once as a plain web app (no framework lock-in), then wrap it per platform: webOS `.ipk` (webOS Studio / ares-cli), Tizen `.wgt` (Tizen Studio, a signing certificate; Samsung's developer mode on the TV), Titan OS and VIDAA (both HTML5; their store/sideload rules differ and must be checked on each vendor's developer portal before any promise).
* **Limits to state honestly**: store review or developer-mode installs on most of them, no silent self-update, DRM and codecs vary, Live TV playback needs each platform's own media APIs. These facts are from general knowledge and must be re-checked against each vendor's current developer docs before work starts.
* **Order**: webOS first (open `ares` tooling and a hobbyist-friendly developer mode), then Tizen, then VIDAA and Titan OS. Contributors who own those TVs are needed to test; the author has none.
* **Shared core**: move the channel catalogue, search, library API client and update-check rules into a dependency-free module (JS) that the web apps use and the Android code can be tested against.

## Simple mode: no server, no setup, "just my TV, unbloated"
For people who want everything direct, with no home server (older relatives, anyone who just wants a clean TV). Plan:
* **First run asks one question**: "Do you have a home server?" with **No, keep it simple** as the default. Choosing it hides everything that needs a server: the Server screen and its Settings row, Cinema's "From your server" row, pairing and Sources, server polling.
* **What stays and works with nothing else**: Live TV from the built-in catalogue (direct streams), HDMI inputs and antenna, Apps (hide what you do not use), Weather, the ambient Gallery with Bing's daily photograph, Cinema's free public-domain films (Internet Archive) and files on a USB drive or this TV, Now Playing, updates.
* **Direct sources instead of a server**: add an M3U/IPTV playlist URL typed once on the TV (the person's own, legal source; OMNI ships none), USB/local video files, and direct links. The server stays optional and is the upgrade path, not a requirement.
* **Fewer things on screen**: a Simple profile with only Home, Apps, TV and Settings; larger text option; no assistant pill or Control Center clutter unless switched on.
* **"Unbloated"** means what OMNI itself can do without root: hide any app from OMNI, a clean Home with only chosen apps, and the existing reversible slim-down script. It does not uninstall the TV maker's system apps.
