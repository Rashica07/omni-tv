# Popup diagnosis (2026-10-25)

Every popup in the launcher was opened on the real TV, driven with the remote's keys, and checked against the same list: does it open, does **focus land inside it**, can it be
navigated, does its action work, does **Back** close it and return focus to where it came from, does it fit the screen. Logs (`log.tag.OmniState`) and screenshots were used,
not code reading alone.

## Root causes (shared by every sheet-based popup)

| # | Defect | Evidence | Fix |
|---|---|---|---|
| 1 | **Focus was not reliably given to the popup.** The sheet asked for focus once, plus one retry after 150 ms. While the page behind hands its own focus away, that request is lost; the popup opened with nothing highlighted and an OK press did nothing. | New log line `sheet focus ok attempt=1` on **every** open: the first request always failed. The screenshot you sent showed exactly this (no option highlighted; first Down highlighted the first option). | `Sheet.ensureFocus`: keeps asking (up to 6 times, 60 to 160 ms apart) until focus is verified inside the sheet, logs the result, and a key press with no focus inside takes focus first instead of being lost. |
| 2 | **The close callback ran many times.** The spring that slides the sheet away keeps ticking after the sheet is removed and called the "gone" handler on every tick (`sheet=closed` printed 6 times for one close). When one popup opens another (Input menu → Set device type) the late calls cleared the *new* sheet's reference and unblocked the page behind it. | Log: six `sheet=closed` per close; after the fix exactly one, and the chained Device type sheet opens on the right page with focus. | `Sheet.finish()` runs once (`gone` flag). |
| 3 | **No height limit or scrolling.** A long list simply ran off the bottom of the screen. | Code: panel was `WRAP_CONTENT` with no cap. | Options now live in a scroll view; the sheet is capped to the safe area. Verified on the longest existing list (6 options) that nothing changed visually; the cap only engages when needed. |

## Per popup (after the fixes)

| Popup | Opens | Focus | Action | Back / focus restore | Notes |
|---|---|---|---|---|---|
| App options (Home, hold OK) | yes | yes | not exercised (Move/Hide/Uninstall are destructive on your apps) | closes, ring returns to the tile | |
| Input menu (TV page rail, hold OK) | yes | yes | Set device type opens the second sheet | yes | |
| Device type (chained) | yes | yes | not saved (would change your labels) | Home press dismisses it cleanly; no stale sheet | this was your screenshot |
| Channel menu (TV, hold OK) | yes | yes | **Add to favorites works**, star appears; menu then offers Remove (removed again) | yes | |
| Network / server (status chip) | yes | yes | shows "Server reachable, 14 channels" | yes | |
| Set server address | yes | system field | works | yes | **Stock Android dialog**, teal buttons and system keyboard: functional but off-brand |
| Account (user chip) | yes | yes | opens system account screens | yes | |
| Wallpaper | yes | yes | not changed | yes | |
| Reset layout? | yes | **Cancel first** | not confirmed (destructive) | yes | |
| Watch / Cinema title sheet (hold OK) | yes | yes | **Open in Cinema works; one Back returns to Watch on the same title** | yes | |
| TV search overlay | yes | **was on "i", now "a"** | typing and results work | yes, focus returns to the search button | `kr = 1` start row leftover |
| Weather chip hold OK | opens the Weather app | n/a | n/a | | the launcher's own weather-location sheets are unreachable dead code |

## Other defects found while testing, and their status

* Confirmation banner covered half the navigation pill (too narrow): fixed, it now always covers the whole pill.
* Back did nothing on a Cinema page opened from the launcher (the root screen consumed Back with nowhere to go): fixed, Back leaves and returns to the launcher.
* Watch page kept stale availability ("Ready to play" for a server that was off): now refreshes when revisited after 30 s; verified offline state says "Source not reachable right now".
* Two independent weather locations (launcher chip vs Weather app): **open**, see the plan in docs/PLAN-NATIVE-OS.md.
* The first Back in the Weather app was swallowed once right after launch from the launcher: could not be reproduced after the fixes; retested below.

## Method note
Blind key sequences were wrong several times (Home needs two Up presses to reach the navigation); every step above was therefore checked by a log line or a screenshot, not assumed.
