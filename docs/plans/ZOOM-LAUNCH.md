# Plan: "open small, zoom to full screen while it loads" (apps and HDMI), then the port to other TV platforms

Status: **plan only, nothing built.** Rule: a plan like this comes before any big feature.

## 1. What you asked for
Click YouTube (or an HDMI input): it appears **small, in place of the tile**, already loading, and **grows smoothly to full screen**. No cut, no black flash, on every platform OMNI runs on.

## 2. What already exists (audit)
* Apps: `ActivityOptions.makeScaleUpAnimation(tile rect)` (the system scales the app's window up from the tile), plus a white ripple and the page colour "bloom" intensifying (`MainActivity.launchAt`, `Ripple`, `Ambient.boost`).
* HDMI: **already is exactly this.** A small live preview window grows to full screen in 280 ms through the same single tune, no black frame (`HdmiOverlay.expand`).
* Return: `returnAnim()` brings the tile back when you come Home.
So the work is the app case, and making the whole thing one consistent, measured motion.

## 3. The hard truth, before designing
* **A third-party app cannot really load "small".** Android always lays out and renders an app at full screen size. A small window does not make YouTube start faster; the system only *shows* its window scaled. (Android TV has no freeform windows; picture-in-picture is the app's choice, not ours.)
* So the zoom **hides and shapes the load**, it does not shorten it. What is genuinely faster: (a) our own screens (Cinema, Weather, Server, Gallery): **pre-warm their process** when their tile gets focus for a moment, so they open warm; (b) HDMI: one tune, kept alive (done); (c) instant feedback within one frame so it never feels dead.
* On non-Android platforms we **cannot draw over another app at all**, so the zoom there can only be inside OMNI's own content (see section 7).

## 4. Design (Android)
**Motion spec (one set of tokens, reused everywhere):** tile rect -> full screen in 320 ms, expo-out curve `(0.16, 1, 0.3, 1)`; corner radius 12 dp -> 0 over the same time; content crossfade 160 ms starting at 60%; Back during the zoom reverses it from where it is (interruptible); animations off => instant.
**Mechanism ("launch shell"):**
1. On OK the launcher puts a **shell window** over everything (it already holds the "draw over other apps" permission for HDMI; without it, fall back to today's `makeScaleUpAnimation`).
2. The shell shows the tile's art (banner or icon on its colour) and grows to full screen with the spec above. Frame 1 happens within 50 ms of the key.
3. The app is started **at the same moment, behind the shell**, with the system's own launch animation switched off (`ActivityOptions.makeCustomAnimation(0,0)`), so there is no double animation.
4. The shell stays (art, a thin progress line only if it takes over 800 ms) until the app's first frame is on screen, then dissolves in 160 ms. Detecting "app is up": top-window change from the optional accessibility service (instant) or, without it, a short poll of the foreground package for at most 2.5 s; a hard timeout dissolves the shell anyway so it can never get stuck.
5. If the app fails to start (not installed, crash) the shell shrinks back into the tile and shows one line why. Never a black screen.
6. Coming Home reverses it (shrink into the tile) using the same token set.
**HDMI:** keep as is, but route it through the same shell/spec so apps and inputs feel identical; the preview-on-focus stays.
**Our own apps:** pre-warm on 600 ms of focus (process only, no UI); measure the benefit before keeping it.
**Memory/perf budget:** one extra full-screen layer for 0.5 s, hardware layer only during the animation, no blur; target p95 frame <= 16.7 ms and no frame > 33 ms while the app is cold-starting (the hardest case on a 1 GB TV).

## 5. Android phases
1. Write the motion spec as data (`docs/design/MOTION.md` + constants) and a **jank harness** (record frame times during a launch).
2. Shell with art + grow + dissolve for apps, behind a setting (default off while testing), with the timeout and failure paths.
3. App-ready detection (accessibility event path, then the poll fallback).
4. Switch off the system animation; fix double-animation edge cases (apps that set their own launch transition).
5. HDMI through the same spec; reverse/return animation; Back-during-zoom.
6. Pre-warm our own apps; measure; keep only if it helps.
7. Test matrix: emulator for correctness; the Vestel TV for frame times; one more brand if available. Acceptance: no black frame in any recording, Back works at every moment, animation-off works, p95 within budget.

## 6. Risks
* Apps with their own splash or orientation change can pop in the middle of our zoom: the dissolve waits for the *first stable frame*, with a timeout.
* The system's launch animation cannot be fully removed on every Android build: keep a test per version (9, 11, 14) and fall back to `makeScaleUpAnimation` if the shell path misbehaves.
* Over-long dissolves hide a slow app and feel worse than a plain wait: cap at 2.5 s and show progress.
* Needs the overlay permission; the setup step already asks for it.

## 7. Porting to the other platforms
Common to all four web platforms (webOS, Tizen, Titan OS, VIDAA): they run our web app in a browser engine and **launching another app hands the screen to the system**, which we cannot draw over. So the "zoom" is split in two:
* **Inside OMNI (full control, same motion spec):** channel tile -> Live TV player, film poster -> player, Weather/Server/Gallery pages, the Now Playing view. Implementation: the **FLIP technique** (measure the tile, switch the element to fixed full screen, animate `transform` from the tile's rect to identity with `cubic-bezier(.16,1,.3,1)` 320 ms, radius and crossfade as in section 4); GPU-only properties, no layout animation. This works anywhere with CSS transforms.
* **Leaving OMNI for another app/input:** play the grow animation up to the moment of the launch call, then let the platform's own launch take over (accept one native cut). Where the platform allows resizing a video window, use it for a **real** small-to-full input view:
  * **Tizen:** the TV/HDMI source can be shown in a resizable window (`tizen.tvwindow` / AVPlay display rect), so a small live preview that grows to full screen is likely possible for inputs. To verify on a real set.
  * **webOS:** inputs and apps are launched through the system's service bus; whether a resizable input window is available to a third-party app is **unverified**. Plan the in-app zoom for sure, the input zoom as a spike.
  * **Titan OS and VIDAA:** HTML5 apps with vendor-specific media/TV APIs; **unverified**. Start with the in-app zoom only; check each vendor's developer docs before promising anything.
* **One spec, three implementations:** `MOTION` tokens (durations, curve, radius, crossfade) live in one JSON file; Android reads them as constants, the web apps as CSS variables, so a change in one place changes all.
* **Port order:** shared web core + FLIP component first (works on all four), then webOS, Tizen, VIDAA, Titan OS (see ROADMAP). Contributors with these TVs are needed to measure: the author owns none.
* **Test:** a screen-recording plus frame-time check per platform; acceptance is the same list as section 5.

## 8. Decisions needed from you
1. OK that on Android the zoom **hides** third-party load time rather than shortening it (only our own apps and HDMI get truly faster)?
2. Default on or off at first? (Recommended: off in the first RC, on after it has been measured on your TV.)
3. Priority against Simple mode (no-server mode) and the HDMI fix that is already built: Recommended order: HDMI fix (done) -> Simple mode -> Zoom launch -> web ports.
