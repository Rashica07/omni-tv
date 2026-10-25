# Changelog

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
