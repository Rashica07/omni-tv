# Motion tokens

One set of numbers for every platform (Android now; the web ports read the same values as CSS variables).

| Token | Value | Used for |
|---|---|---|
| `grow` | 320 ms, expo-out `cubic-bezier(0.16, 1, 0.3, 1)` | a tile (app, channel, input) growing to full screen |
| `dissolve` | 160 ms linear | the zoom shell fading once the app is on screen |
| `min-total` | 420 ms | the zoom never dissolves sooner, even for an app that is already running |
| `max-total` | 2500 ms | the zoom always ends by now, whatever the app does |
| `progress-after` | 800 ms | a thin progress line appears only for a slow app |
| `tile-radius` | 12 dp, to 0 at full screen | corners during the zoom |
| `art-calm` | artwork fades to 45% over the last 40% of the grow | calm full-screen colour |
| focus spring | 520 / 0.84 (stiffness / damping ratio) | tile focus |
| reduced motion | animations off in the system, or the setting off | no zoom: the system's own scale-up is used |

Android: `ZoomRules` (pure, tested) and `LaunchShell`; the zoom is off until switched on under Settings > Zoom when opening and needs the "draw over other apps" permission (the same one the HDMI picture uses).
