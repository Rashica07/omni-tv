# OMNI TV

A custom, ad-free, black-and-white **TV operating-system layer for Android TV**: a launcher (Home, Watch, Apps, Live TV, Settings), a right-hand
**Control Center** (press INFO), universal search, a live HDMI-input preview, and a small family of native apps (Cinema, Weather, Server, Guide, Play,
Gallery, Control, Reference) plus an optional home-server (`omniptvd`) that indexes your own films and serves them to the TV.

It was built and measured on one very weak TV (Vestel/MediaTek, Android 11, 32-bit ARMv7, ~900 MB RAM, 1280x720), so it is fast on cheap hardware by design.

> **Honest status.** Developed and tested on **one TV**. Other TVs are untested; reports and fixes are welcome (see `CONTRIBUTING` below). The HDMI live preview
> depends on the TV maker's TV-input service and behaves differently per brand. Nothing here replaces or removes your TV's own software: OMNI is an *extra*
> launcher, and every system-level change is reversible (`INSTALL.md` lists the undo commands).

## At a glance (measured on the reference TV, not marketing numbers)
* **Footprint:** launcher APK about 0.65 MB; about 110 MB PSS on the Live TV page (our own budget was 85 MB, so there is still work to do). Stock Google TV was not measured side by side, so no comparison is claimed.
* **Stack:** launcher in plain Java Views (no libraries); the apps are Kotlin + Jetpack Compose; the server is Python standard library.
* **Smoothness:** Live TV page 0% janky frames, p95 9.6 ms; Weather median 12 ms, p95 15 to 16 ms (see `launcher/docs/perf/`).

| Settings | Weather |
|---|---|
| ![Settings](docs/screenshots/settings.jpg) | ![Weather](docs/screenshots/weather.jpg) |
| **Cinema** (public-domain films from the Internet Archive) | **Cinema title page** |
| ![Cinema](docs/screenshots/cinema-home.jpg) | ![Title](docs/screenshots/cinema-title.jpg) |

Screenshots of Home, the Control Center and Live TV are not included because they show third-party app icons and channel names; add your own.

## What is in this repository
| Folder | What | Language / build |
|---|---|---|
| `launcher/` | The launcher itself (OMNI TvOS) | Java Views, no Gradle, no libraries. `./build.sh`, ~0.65 MB APK |
| `android/` | Cinema, Weather, Server, Guide, Play, Gallery, Control, Reference + shared `core:*` modules | Kotlin + Jetpack Compose for TV, Gradle |
| `server/` | `omniptvd`: personal media library + catalogue + health service for your home server | Python 3 standard library only, SQLite |
| `docs/` | Roadmap, install guide for humans and for AI agents | |

## Features (all working on the reference TV, verified there)
* Launcher with springs-based motion, no per-frame blur (that is what the weak TV cannot afford), focus handling hardened after real-remote testing.
* Control Center on the right (volume, inputs, server, weather, network, shortcuts), opened with the remote's INFO key.
* Universal search over apps, channels, films, inputs and settings.
* Live HDMI preview card that expands in place to full screen with sound, without leaving the launcher (brand-dependent, see status).
* **Personal media library**: put films in a folder on a computer; the server scans them, matches posters/overviews through TMDB (you bring your own key), streams them with
  byte ranges, and keeps one shared "continue watching" position across devices. Cinema on the TV plays them and syncs resume points.
* Honest availability: items say whether their source is reachable; unplayable formats are reported, not hidden.

## Quick start
See [`INSTALL.md`](INSTALL.md). If you would rather have an AI assistant do it, tell it: *"Clone this repository and follow `AGENTS.md`."*

## Not included on purpose
Channel logos, a channel/stream catalogue, any TMDB credential, any signing key. See `THIRD-PARTY.md`. This project does not host, relay, bypass or advertise streams.

## Planned
See [`docs/ROADMAP.md`](docs/ROADMAP.md): device-class profiles (a **Lite** profile for weak TVs and a **Premium** profile with blur and heavier animation for powerful TVs),
a reversible "slim down" tool, and broader TV compatibility testing. None of those exist yet.

## License
Apache License 2.0, see `LICENSE`; third-party material in `THIRD-PARTY.md`.
