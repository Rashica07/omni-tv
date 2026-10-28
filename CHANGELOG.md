# Changelog

## 1.0.0-rc.12
* **Simple mode** (no home server): chosen in the first-run setup (the default for new installs) or under Settings > Simple mode. It hides the Server screen and rows and server polling, and the setup skips the server step. Existing installs keep everything.
* **Languages**: English, Albanian (Shqip), Italian and Swiss Standard German, chosen at first run or under Settings > Language (the TV's own language is used until then). Covers navigation, Home, Live TV, Settings, the setup, quick access and the remote-button screens; translations are machine-written and need native review (`docs/TRANSLATIONS.md`).
* **All 33 Albanian and Kosovan channels come with the app** (catalogue only, no logos), so Live TV works with no server.
* **Antenna recognition**: channels the TV's tuner has scanned are grouped by the country OMNI recognises from their names (Italy, Switzerland, Albania, Kosovo). A tuner with no channels yet is offered the TV's own scan (in the setup and via a remote button); OMNI never scans itself.
* **Quick access**: Settings > Quick access (also in the setup) is a tick-list of apps for the Home row. **Remote buttons** can now open any app, HDMI input or channel (pick the button, then what it opens), or the antenna list.
* Updates: personal builds are now recognised by their channel logos.

## 1.0.0-rc.11
* **Fix: the HDMI picture no longer ends after 10 minutes.** The preview's 10-minute timer kept running after the picture went full screen; when it fired the picture disappeared and the empty viewer left on top of the launcher looked like a crash until Home was pressed. A full-screen picture now has no timer (it ends when you leave), and if the picture window ever goes away the empty viewer closes with it.
* Roadmap: Tizen, webOS, Titan OS and VIDAA web-app ports, and a no-server "Simple mode".

## 1.0.0-rc.10
* **Updates** (Settings > Updates): OMNI checks GitHub's public releases once a day while the launcher is on screen (off switch included), downloads a newer APK, verifies it (size, SHA-256 from the release notes, same package, newer version, same signing key as the installed OMNI) and hands it to the system installer, which asks you to confirm: Android does not allow a silent self-update. Pre-releases are included until 1.0.0 (a switch). A personal build with its own channel list is told about updates but never replaced. Only `api.github.com` and GitHub's release hosts are contacted; nothing identifying is sent.

## 1.0.0-rc.9
* **Crash guard** (from issue #1, a Sony Bravia on Android 14 that crashed on the last Settings page and stayed broken until reinstalled): OMNI now records its own crashes (device, Android version, stack) and shows the last one under Settings > Crash report, so a report can carry it. After two crashes within two minutes it starts in a plain safe mode (no setup card, no background watchers, no idle timer, no media follower) so a crash cannot loop. The cause of that crash is not yet known; the report from RC 9 will show it.

## 1.0.0-rc.8
* **Gallery redesigned**: an ambient display (full-screen photograph, oversized thin clock placed in the quietest part of each photograph, date, credit, slow drift and cross-fade) and a gallery (Photos and Videos grids, full-screen viewer). Minimal and expressive clock styles.
* **Bing's daily photograph** as a source, with title, description, credit and date; cached and refreshed at most twice a day, kept offline. Isolated behind a replaceable provider.
* **Optional idle ambient** (off by default; 5/10/30 min), configurable behaviour while music plays, bounded keep-screen-on. Settings -> "Ambient display" starts it directly.
* Compact header: smaller nav bar, logo, labels and status bar.

## 1.0.0-rc.7
* **Compact launcher**: smaller, more proportionate Home tiles, a lighter focus (3 dp lift, 4% scale, 2 dp ring) and a smaller title. App artwork and colours are unchanged.
* **Now Playing**: a compact pill (artwork, title, artist, progress) fed by the active Android media session (Spotify or any player); OK opens a larger view with previous / play-pause / next. No polling; needs OMNI's notification-listener access and is simply absent without it. See `docs/NOW-PLAYING.md`.
* **Screensaver** shows the playing track's artwork and title in a small capsule. Playback is never touched.

## 1.0.0-rc.6
* **OMNI Server redesigned** as a control center on the shared OMNI top bar: Overview (verdict first, four ring gauges, network, services), Resources (charts of real polled readings; no chart until there is enough data), Library (counts, folders, storage, **Scan library now**), Devices (devices that have synced watch progress) and a settings sheet with a confirmed *Forget this server*. Only what the backend provides is shown: no service start/stop (no such endpoint) and no "connected clients" (not tracked). Honest states: unreachable, not signed in, older server without a library, missing values shown as n/a.
* Fix: a resolved temperature warning was listed as "Resolved:" with no text.

## 1.0.0-rc.5
* **OMNI Weather redesigned.** The shared OMNI top bar (now one component for Cinema and Weather) with Today, Hourly and Week and small Refresh / Change city icons; the temperature as the primary element; an honest one-line outlook from the real hourly forecast; a quiet details row; a temperature curve with focusable hour cards; a seven-day list with range bars. The existing procedural sky is kept and tuned (brighter night, sun and moon moved clear of the text) with a lighter variant for low-memory TVs. Old data is labelled "Last known ... Not live." Unchanged: Open-Meteo, caching, the city list, OK to refresh, hold OK / Menu for cities. No alerts section: the data source has none.

## 1.0.0-rc.4
* Cinema's top bar now follows the launcher's navigation rules: the OMNI mark, a glass capsule of icons (Home, Movies, Shows) where the selected item opens to show its name, and on the right a small capsule of icon utilities (Search, My List, Sources). Pages use the launcher's 64 dp / 30 dp safe area. Choosing a tab keeps focus on it. The rules are written down in `docs/CINEMA-UX.md`.

## 1.0.0-rc.3
* **OMNI Cinema redesigned** as a premium streaming experience: charcoal / ivory / crimson / gold, a cinematic hero that follows focus with the artwork's own colour as ambient light, artwork-led poster and landscape cards with a restrained focus lift, rows that snap under the hero, a top navigation (Home, Movies, Shows, My List, Search, Sources), a new title page with More Like This, an on-screen-keyboard Search across titles, genres, years and descriptions, and a real My List kept on the TV. Rows exist only when the library can fill them. Playback, resume, sources and the server library are unchanged.
* Fix found while testing on an Android TV 11 emulator: building the Home rows recursed forever (crash on open); now covered by a test.

## 1.0.0-rc.2
* **One app.** Cinema, Weather, the Server dashboard and the screensaver are now inside the OMNI launcher APK (about 2.5 MB), each in its own process that exits when you close it. Guide, Play, Control and Reference are removed. One install, one update, no extra icons; weather uses the one place you picked for Home; the Server screen reuses the server you paired in Cinema.
* Settings has *Server* and *Screensaver* rows. Upgrade note: uninstall the old separate OMNI apps first (they use the same names as the built-in Cinema, so Android refuses to keep both); Cinema's local data (the server pairing) must be entered once again, films and positions on your server are kept.

## 1.0.0-rc.1
* The assistant is a glass pill in the top navbar zone (drops in like a notification, the nav fades, the page stays visible); chips on a second row.
* Remote buttons: one row per physical button; optional "use the buttons everywhere" (Accessibility service you turn on yourself, sees only assigned buttons).
* Fixes: a short app name no longer matches a whole sentence ("play ..." opened the PlayStation / an app called Play); wallpaper default source is Wikimedia (Bing only if chosen).

## 1.0.0 (planned)
* **Welcome & setup**: a liquid-glass "WELCOME TO OMNI UI" first run over your wallpaper, then Home, remote check, OMNI server discovery on your network, weather place and permissions. Run it again from Settings.
* **Assistant**: press the mic/search button or the Control Center's Assistant button. Voice (or typing) for TV commands in English, Italian and Albanian: open apps, switch to an input or device ("PlayStation"), volume, weather, search, films. Anything else is handed to the TV's own Google assistant / Gemini.
* **Device notices**: speakers and soundbars (Bluetooth, HDMI ARC/eARC, USB), game controllers and keyboards, Chromecasts appearing on your network, and casting to this TV. Shown over other apps when OMNI is not in front.
* **Control Center**: Assistant shortcut; where sound is playing now, under Volume.
* **Remote buttons**: choose what INFO, colour keys and other buttons do in OMNI (Settings > Remote buttons).
* Docs: compatibility table (`docs/COMPATIBILITY.md`).

## 0.1.0 (2026-10-25)
First public pre-release: launcher, Control Center, universal search, HDMI preview, Cinema with the personal media library, Weather, Server, Guide, Play, Gallery, Control, Reference.
