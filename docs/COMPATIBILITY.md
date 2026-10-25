# Which devices OMNI runs on

OMNI is an Android TV launcher plus Android apps. It needs **Android TV / Google TV 8.0 or newer** (built and tested on Android 11).
Only one TV has actually been tested so far; everything else below is what should happen and why. Please report results in an issue ("Compatibility report").

| Device | Install the apps | OMNI as the Home screen | Notes |
|---|---|---|---|
| **Vestel / MediaTek TVs (Android 11)** (Telefunken, Hitachi, JVC, Toshiba-by-Vestel) | yes | yes | **Tested.** HDMI live preview works here (MediaTek TV input service). |
| Sony Bravia, Philips, TCL, Hisense, Xiaomi (Android TV / Google TV) | should work | should work | HDMI preview depends on each maker's TV input service; the feature hides itself when inputs cannot be shown. Some Google TV builds re-select their own launcher after updates. |
| Chromecast with Google TV / Google TV Streamer | should work | should work | No HDMI inputs, no tuner: those sections stay empty. |
| Nvidia Shield TV | should work | should work | Strong hardware: a good candidate for the future Premium profile. |
| Amazon Fire TV (Fire OS) | yes (sideload) | **no** | Fire OS does not let another app replace its home screen. Open OMNI and the apps from Fire's own home instead. |
| Raspberry Pi | only with an **Android TV build** (for example LineageOS Android TV by KonstaKANG) | yes | Raspberry Pi OS cannot run Android apps. Performance depends on the Pi model (Pi 4/5). |
| Android phones and tablets | apps run | not meant for it | OMNI is designed for a remote, not touch. |
| **Titan OS, LG webOS, Samsung Tizen, Roku** | **no** | **no** | Not Android. They would need a separate web version; none exists. |

## Features that depend on the TV
* **HDMI live preview**: needs a TV input service that allows third-party apps (works on MediaTek). Elsewhere the inputs still switch, without the preview.
* **Assistant**: needs a speech recognition service (Google app on almost every Android TV) for voice; typing always works. General questions are handed to the TV's own assistant (Google Assistant / Gemini) when it has one.
* **Casting notices**: need Chromecast built-in on the TV and "Casting" allowed in setup (notification access).
* **Remote buttons**: work while OMNI is open. Some TVs keep certain buttons (mic, Netflix, Prime) for the system and never pass them to apps.
* **Screen mirroring (Miracast / AirPlay)**: provided by the TV or by a receiver app such as AirScreen. OMNI does not include a receiver.
