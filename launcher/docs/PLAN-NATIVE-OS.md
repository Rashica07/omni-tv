# Plan: making the launcher feel like a native OS (starting with live HDMI)

> **Status 2026-10-25 (evening): steps 1 and 3 below are built and running on the TV; the picture itself is UNVERIFIED by me** (screenshots cannot see the video plane). See section 7.

Written 2026-10-25. This is a plan, not an implementation. The one experiment that was run is described under "What was measured".

## 1. The goal

On the TV tab, above the channel list, every HDMI input that is **active and working** shows up first, the way webOS shows its connected sources:
a card per input with the device's name (PlayStation 5, PC, Game console), a **live preview** of what it is showing, hold OK for options (rename, device type, switch), OK to switch to it.
The same card grammar as antenna channels and the other tiles, so you never have to leave the launcher to see what is on an input.
Later, more of the OS should be native in the same way: quick settings, notifications, global search, an app switcher, consistent dialogs.

## 2. What was measured (real TV, 2026-10-25)

A muted probe (`HdmiProbeActivity`, developer-gated, 10 s) tuned the PlayStation 5 on HDMI 3 inside a small `TvView` from the launcher's own process:

| Question | Answer |
|---|---|
| Does the platform let a normal app tune an HDMI input? | **Yes.** No connection failure; `onVideoAvailable` after **1459 ms**. |
| Can the picture be captured for verification? | No. `screencap` returns an empty image for the TV video plane, so the picture can only be judged by eye. |
| What happens to the launcher when it tunes? | **About 2 s later the TV maker's TV app (`com.mediatek.wwtv.tvcenter`) launches itself on that input** and takes the foreground. Log: `InputSourceManager$HdmiInputChangeListener.onChanged` starts `TurnkeyUiMainActivity` with the passthrough URI, from uid 1000. It listens to HDMI-CEC input changes, so *any* tune that changes the TV's selected HDMI input triggers it. |
| Is there a hint the hardware can do a small picture? | `com.mediatek.tvinput.PipResourceManager.grantMediaResource` is called by the HDMI input service: the TV's own stack has a picture-in-picture resource manager. |
| Does the TV already know what is plugged in? | Yes, without tuning: input state (signal / standby / none), the port label, CEC device names ("PlayStation 5" via the child input of HDMI 3). The PS5 currently reports **Signal**. |

So the live preview is *technically reachable* (the tune works, video arrives) but the OEM app reacts to the input change and covers the launcher. That is the single problem to solve; the rest is UI.

## 3. Options for the live preview, in the order to try

| # | Idea | Why it might work | Risk | Cost to test |
|---|---|---|---|---|
| A | **Tune as a non-main session** (`TvView` without `setMain`, or `TvInputManager.createSession` directly) | The OEM listener may react only to the *selected* input, not to extra sessions | The CEC layer may switch the mux regardless | 30 min |
| B | **Host the `TvView` in an overlay window** (`SYSTEM_ALERT_WINDOW`, grantable once via `adb appops`) so it stays above the OEM app after it launches | Overlay windows sit above other activities | The full-screen HDMI picture is the main video plane; the OEM app would still show it full screen *behind* our small preview | 1 h |
| C | **Use the PIP resource** the MediaTek service already manages: ask for a PIP/secondary video path (the TV app has "PIP/PAP") | It is the hardware's own mechanism | Probably system-only API | research + 1 h |
| D | **Swallow the OEM launch**: a tiny accessibility-free approach is not possible; but the launcher can re-open itself on top within one frame and keep the `TvView` alive in a floating window | Fast enough to look intentional | Flicker, race, and fights the OEM app (acceptable only as a last resort) | 1 h |
| E | **Do not preview live**: show a rich card (device art, name, signal, how long it has been active, last app/title if CEC reports it) and switch on OK | Always works, zero risk to the OEM app | No picture; less "webOS" | 0 (mostly built: the Inputs rail) |

Method: each option is tried against the PS5 with the same probe, judged by the log (`onVideoAvailable`, which activity is resumed after 5 s) **and by you looking at the TV**, because screenshots cannot see the video plane.
Stop at the first option where the launcher stays in front and the picture is small and live.

## 4. Build order (so something good ships even if A–D fail)

1. **Sources strip above the channels** (option E, no risk). Move active inputs out of the right-hand rail into a strip above the channel grid: only inputs with a signal, first; cards with device art and name; OK switches; hold OK opens the existing input menu. Inputs without a signal collapse to a quiet "Other inputs" row. Measured budget: no new full-screen layers.
2. **Run experiments A to D** with the probe (it already exists and is gated). Record results in this file.
3. **If one works:** put a live `TvView` in the strip's focused card only (one at a time, muted by default, unmuted when selected), tear it down on leaving the page, never during scrolling.
4. **If none works:** keep E and add the honest state ("Signal", "No signal", "Standby", since when). Do not fake a picture.

Guard rails for every step: never leave the TV on an input the user did not choose; probe closes itself; `docs/tvguard.py` before driving the TV; the OEM TV app is never disabled (rollback in README).

## 5. The wider "native OS" list (later, one at a time)

* **One popup system.** Done this session: sheets now take focus reliably, close once, scroll when long. Next: replace the stock Android address dialog with an OMNI number pad (the PIN pad in the Kotlin apps is the pattern).
* **One weather location.** The launcher chip and the Weather app keep separate cities. Plan: the Weather app exposes a tiny signature-protected provider (place, temperature, condition, updated time), exactly like the Cinema contract, and the chip reads it.
* **Global search** across apps, channels, films (Cinema provider), settings, and inputs, with the OMNI keyboard that already exists.
* **App switcher / recents overlay** on a long Home press (the launcher already tracks recents).
* **Quick settings panel**: network, sound output, picture mode, sleep timer; each a real system intent or a verified setting, nothing decorative.
* **Notification centre**: only notifications the launcher is allowed to read; otherwise the panel says it cannot see them.
* **Ambient mode**: the Gallery dream is the base; add clock and weather like the screensaver already shows the clock.
* **Consistency pass**: one focus language everywhere (ring on tiles, white fill on controls), no stock dialogs, every state (empty, offline, loading, error) worded the same way.

## 6. What this plan does not promise
A live in-launcher HDMI picture is **not guaranteed**: the OEM TV app currently takes over, and which workaround (if any) holds can only be known by trying A to D on this TV. Step 1 is worth shipping either way.

## 7. Results of building it (2026-10-25, evening)

**Built**
* An **Inputs** section is the first row of the TV page (filter "All" only): one card per HDMI input that has a signal, drawn like channel cards (`InputArt`), PlayStation 5 today. OK switches to the input, hold OK opens the existing input menu. Cards are cells of `ChannelGrid`.
* **Live preview** (`HdmiOverlay` service): after focus rests on an input card for 0.9 s, a small muted `TvView` is placed in an overlay window exactly over the card (4 dp inset). Moving focus, leaving the page, opening a menu or the launcher leaving the foreground stops it. If the "draw over other apps" permission is missing the card still works and says so.
* Needs `adb shell appops set com.rasha.omni SYSTEM_ALERT_WINDOW allow` once (done on this TV). A TV has no obvious settings screen for it.

**Verified by logs and the platform (not by eye)**
* Tune works from a service-owned window; `onVideoAvailable` in ~1.5 s.
* SurfaceFlinger shows a 232x126 px `SurfaceView` layer while the card is focused and none after leaving.
* The launcher stays the resumed activity throughout and the TV tab does not reset (the relaunch carries `omni_keep`, which `onNewIntent` ignores).
* The first tune after the TV was on another input made the TV maker's app launch (as in the earlier probe); the service brings OMNI back within ~150 to 400 ms (the 250 ms watchdog). A later tune of the same input caused no launch at all.

**NOT verified, and why it matters**
1. **That the small picture is really visible, correctly placed and sharp.** Screenshots are empty while it is up. Needs you to look at the TV.
2. **Audio.** The preview is muted, but the TV maker's app may unmute the input when it launches. Unknown until heard.
3. **Flicker** when the TV maker's app appears for the first tune.
4. **Side effect:** tuning leaves the TV's *selected* HDMI input on HDMI 3 after the preview. Leaving the TV page does not switch it back.

Next, in this order: you look and listen and tell me 1 to 3; then restore the previous input when the preview ends (record `TvInputManager` selected input before tuning); then a Home-tab "Inputs" shelf using the same cards.

## 8. The rest of the OS list, planned (nothing below is built)

The note you pasted says the live HDMI preview "is already built". It was not when you sent it; it is now (section 7), unverified by eye. Priorities from that note, adjusted to what this TV can actually do:

| Phase | Item | Real constraint on this TV | First deliverable |
|---|---|---|---|
| 1 | **Control Center** overlay | Volume/HDMI/Wi-Fi state is readable; changing system volume or picture settings needs OEM APIs. A Home-long-press cannot be intercepted by a third-party launcher; use a launcher-handled key (Menu/Settings) | Overlay panel on Menu with network, current input, server state, quick links. Read-only first, controls only where verified |
| 1 | Back/Home consistency | Done for popups this session | Audit of every page's Back path |
| 2 | **Universal search** | Cinema provider already shares stable ids | One overlay (existing OMNI keyboard) over apps, channels, films, inputs, settings |
| 2 | Activity and resume | Watch state already in Cinema; launcher restores focus by identity | "Continue" row on Home from Cinema + last HDMI input + last channel |
| 3 | Notifications | Only the launcher's own events (input plugged, server lost/back, film added); it cannot read other apps' notifications | The existing Banner as the one component |
| 3 | Ambient | Gallery dream exists and is the system screensaver | Add clock + weather to the dream |
| 3 | Input hub on Home | needs step 7 verified first | Inputs shelf on Home |

Honest limits: no system-wide overlay can sit above fullscreen playback of other apps without the overlay permission (granted here via appops, not a normal user path); HDMI-CEC device details stay hidden from apps.

## 9. Full-screen HDMI viewer (2026-10-25, later)

Clicking an HDMI input (card in the Inputs row, or the rail) now opens `HdmiViewerActivity`: the input full screen **inside OMNI**, unmuted, in the same OMNI panel as an antenna channel (name, port, Back to leave), instead of handing over to the Android TV app.
* Verified on the TV with the PlayStation 5: video available in ~1.5 s; the TV maker's app jumped in once and the viewer pulled itself back to the front within a second, then stayed the resumed activity for the whole 13 s sample; Back returned to the launcher.
* Not verified: **sound** (it is requested unmuted: `setStreamVolume(1)`; the Android audio dump showed only the normal speaker path, which says nothing about HDMI audio, so it needs ears), and the picture quality (screenshots cannot see the video plane).
* The click path from the card was verified only up to its routing code (`TvPage.switchInput`); a blind key test hit an antenna channel instead. The viewer itself was driven through the developer probe (`--es mode viewer`).
* Open: the TV stays *selected* on HDMI 3 after leaving; restoring the previous input is not built.

## 10. Seamless full screen, "connecting forever", and a wedged HDMI service (2026-10-25, night)

**Built:** the preview window now *is* the full-screen view. Clicking the card sends EXPAND to `HdmiOverlay`: the same window, with the same single tune, grows from the card to the whole screen in 280 ms (sound switched on, OMNI panel drawn inside it). No second tune, so no black frame in between. `HdmiViewerActivity` is now a transparent key-taker (Back leaves, OK shows the name); without the overlay permission it falls back to its own TvView (starts from black). Home in full screen ends the view. The take-over watchdog only pulls OMNI back in the first 6 s after a tune (the TV maker's app launching), so it can never fight the user.

**Found:** the tuner dump (`dumpsys tv_input`) showed 15 OMNI sessions open, because the preview never called `TvView.reset()`. Fixed (reset on every close and before every retune). Also the PlayStation's own input is a child (`HDMI300004`) of the port (`HW6`); the TV maker's app tunes the child, so OMNI now does too (`Inputs.Entry.playId()`). A retry re-tunes up to 3 times if no video arrives.

**State of the TV right now:** after the leak, every session request on the HDMI service (ours and the TV maker's, 21 in total) sits with `session: null`: the MediaTek HDMI input service is not answering. Even the oldest probe, which got video in 1.5 s this morning, now gets none. Killing OMNI's process did not clear it (the stale entries live in the system server). It most likely needs the TV restarted (standby / unplug), after which the fixes above can finally be judged. **Not verified:** that the seamless expand, the panel, the sound and the retry actually work end to end: the expand animation ran (1280x720 reached, log), but no picture could be produced to test it on.

## 11. Phase 1 built: Control Center (2026-10-25)

A drawer from the right over whatever page is showing (`ControlCenter.java`, rows from the tested `ControlRows.java`): real network, server, inputs with a signal, weather and storage, plus shortcuts (TV and inputs, network, sound, display, check server, weather, Cinema, all settings).
* **Entry points:** first row of the Settings page ("Control Center"), and the Menu key wherever a page does not use Menu itself (the Home shelves and the channel grid do, so it is unreliable there). The remote's Settings key (keycode 176) is taken by Android for its own panel and never reaches the launcher, so it cannot be used.
* **Verified on the TV (debug entry `--es open control`):** opens in under a second with focus on the first shortcut (`control focus ok attempt=1`), Back closes it once (`control=closed`), "Check OMNIPTV server" runs and closes the drawer (`server state=2`), inputs read "None with a signal" (the PlayStation is in standby), storage 849 MB free of 4.3 GB (real).
* **Not verified by eye:** the Settings-page row and the Menu key (blind key sequences kept missing), the bottom shortcuts (Weather, Cinema, All settings sit below the fold and scroll), and the status of the HDMI row with a console switched on.
* Unit tests: 3270 checks, 0 failed (7 new for `ControlRows`).

## 12. Control Center, second version (2026-10-25, evening)

Rebuilt after research and your feedback: a liquid-glass panel on the **right** (440 dp of the 960 dp screen = 46%), opened and closed with the remote's **INFO** button (also Back), made of round pills like the navbar with a white highlight that glides between them. Everything is done inside it:
* **Volume** (Left/Right change it in 5% steps, OK mutes): changes Android's music stream; verified 12 → 17 → 22 → back to 12 in `dumpsys audio`. Whether the TV's speakers follow is up to the TV: Android reports the speaker device index changing.
* **Inputs**: one row per HDMI input (with a signal first, named sleeping devices after); OK on one with a signal opens it full screen in OMNI. Verified: PlayStation 5 listed as "Standby".
* **OMNIPTV server**: OK checks and the answer replaces the row text in place (verified: `server state=2`, panel stays open).
* **Weather**: OK refreshes in place. **Network**: shows the connection, OK opens the system network screen (an app cannot toggle Wi-Fi on Android 11).
* Round shortcuts: TV & inputs, Weather, Cinema, All settings.
Research (Apple's own pages): tvOS Control Center opens with a press-and-hold of the Home button, closes with Menu/Back, holds users, the current song, AirPlay, Search and Sleep, and you highlight then press. Apple documents no visual layout, and for tvOS 27 only says it is "more responsive". Not offered here because an app cannot do them: Sleep, Now Playing (needs notification-listener access), user switching.
**Lesson:** this screen is about 960 dp wide (density 1.33), not 1280; a first version sized for 1280 overflowed.
**Not verified:** that the real Telefunken INFO button reaches the launcher as keycode 165 (tested with an injected key); the TV page/viewer opening from the inputs rows; weather refresh in place.

## 13. Phase 2 built: universal search (2026-10-25)

One search across apps, TV channels (Kosovo, Albania, antenna), films from OMNI Cinema, HDMI/other inputs and settings, opened from the **Search** button in the Control Center (`MainActivity.openUniversalSearch`, debug entry `--es open search`). The TV page keeps its own channel-only search; both use the same overlay (`SearchOverlay`), now generalised to `SearchHit`s (pure, ranked and tested in `SearchHit`: a title that starts with what you typed ranks first, then words of the title, then other text; ties: apps, channels, films, inputs, settings).
* **Verified on the TV:** 103 things gathered off the main thread; "net" -> Netflix (real icon) then the Network setting; "jungle" -> Jungle Book (FILM, from Cinema's provider); "rai" -> 21 channels with logos; a nonsense/absent word says "Nothing matches"; Enter on Jungle Book opened Cinema's player and Back returned to the launcher. Unit tests: 3288 checks, 0 failed (15 new).
* **Not verified by eye:** opening an app, a setting, an input or a channel from a result (code paths are small and compile; only the film path was run), and the Control Center's new Search button (the overlay was opened through the debug entry).
* Not done: film posters in results (a "FILM" tile is shown), search history, voice.

## 14. Phase 3 started: system notifications (2026-10-25)

Through the existing Banner (`Host.flash`), only for real changes, no polling: **network lost / back** (from `ConnectivityManager`, must hold 3 s so a flapping link is one event, never announced on first reading) and **an HDMI device switched on / off** (from `TvInputManager.TvInputCallback`; first snapshot is silent; standby <-> unplugged is not news). Rules are pure and tested (`EventRules`, 10 checks; suite 3298 / 0 failed). The Banner lives in the launcher, so nothing is drawn over full-screen playback or subtitles of another app.
**Not verified live:** it needs a real change (the PlayStation powering on, the network dropping), which was not forced on a shared TV. Verified only: deployed, no crash, silent first snapshot, no leaked TV sessions.
**Left from the OS list, with reasons:** "Continue" row on Home (Watch already opens on Cinema's Continue shelf, so Home would duplicate it); Ambient weather (the Gallery dream cannot read the launcher's weather without a second provider; the clock and date are already there); Inputs shelf on Home (waits for the HDMI preview to be verified with a console switched on).
