# OMNI apps: status, what was verified on the TV, limits

| App | id | Status | Verified on the TV | Limits |
|---|---|---|---|---|
| Reference | com.rasha.omni.reference | done | start 440 ms, 37 MB, 0% janky | proves the foundation |
| Weather | com.rasha.omni.weather | done | 1.7% janky, 7 ms median, 29 MB; launcher opens it | Open-Meteo city list only (no location permission) |
| Control | com.rasha.omni.control | done | real inputs (CEC child folded into its port), network, audio, display; **input switch works** (opens the TV's TV app on the input) | silent switching / CEC power need system permissions; custom names are labels you set, never detection |
| Guide | com.rasha.omni.guide | done (scope below) | permission flow, real EIT now/next grid, details, jump to channel opens the TV app on it; 1% janky, 27 MB | the broadcast gives only ~2-3 h for a few channels; no XMLTV source exists for the internet channels; no reminders/search/favourites yet |
| Play | com.rasha.omni.play | done | finds games by the system's own game flag, launches, Back returns with focus kept, recents; HDMI names; 0% janky | none of the streaming clients are installed on this TV, so launching one is untested; no real gamepad was connected, so controller detection is only verified to exclude the voice remote |
| Gallery | com.rasha.omni.gallery | done | downloads real CC-licensed landscapes (credit shown), slideshow with fade, clock, favourites, settings; 4 unit tests; 4.4% janky p95 10 ms, 63 MB. Cause of earlier 97% jank: 1080p bitmaps on a 720p panel, fixed by decoding at screen size (RGB_565) | screensaver service is registered but selecting it in the TV's Screen saver settings was **not** tested; "this TV's photos" needs storage permission and the TV has none, so that path is untested |

Not started: Cinema, Remote, Server, Audio, Home (see the briefs). Each must be built, installed and verified on the TV before it is marked done.

## Screensaver
The TV's screensaver is OMNI Gallery (`com.rasha.omni.gallery/.OmniDream`), set with
`settings put secure screensaver_components` and `screensaver_default_component`. Verified: the dream starts, shows real photos with credit and clock, and Back wakes it.
Nothing was uninstalled or disabled. To go back to Google's: 
`adb shell settings put secure screensaver_components com.google.android.backdrop/.Backdrop` (and the same for `screensaver_default_component`).
