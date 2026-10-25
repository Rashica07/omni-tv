# Audit of OMNI "v2" (as found at the start of this pass)

Evidence: source tree (2,870 lines Java, 16 classes), screenshots `docs/v2/*.png` (captured on the TV), baseline benchmark
`docs/perf/results.jsonl` (label `v2-baseline`). Visual claims below are from the captured screenshots.

## What works (keep)
- **Home Dock** (`Dock.java`): one custom-drawn view; focus reshapes the shelf; warm Home benchmark p50 8.5 ms, p99 16.9 ms, 3 frames
  over budget in 293. This is the proven-fast primitive.
- App discovery (LEANBACK + LAUNCHER, scoped `<queries>`), real icons/banners, launch with ripple + scale-up, return-to-place,
  live package add/remove, favorites/hide/reorder persistence, hold-OK sheet.
- Watch: TMDB lists, backdrop with **baked scrims** (this fixed 47% -> 4% jank), RGB_565 decoding, disk cache.
- Header, Settings list, sheet styling, reduced-motion (springs snap when system animations are off).

## Visually weak (redesign)
1. **Three interaction models, not one system.** Home = accordion dock; Apps = grid of equal rounded cards; Watch = rows of equal
   posters (each its own scroll view). Moving between tabs changes the grammar, which reads as three apps pasted together.
2. **Template tells** (flagged by the `frontend-design` skill's own list): tracked ALL-CAPS eyebrow (`FAVORITES  CONTINUE  MOST USED`),
   meta strings joined by middle dots, a `1 / 8` counter that is not a sequence, a crimson dash above a heading.
3. **Unfocused dock tiles are unreadable at distance:** a 40 dp icon on a dimmed tinted strip; non-focused apps are identified only
   by a small icon (ROI of the accordion is lost for 7 of 8 apps).
4. **Safe area violated:** header 20 dp from the top, dock ends 8 dp from the bottom; Apple/Amazon/Roku all define ~5% (V).
5. **Home environment hue swings fully with each app** (red YouTube, olive Aptoide, blue BrowseHere): tiring on repeated presses and
   blotchy for bright banners.
6. **Watch is thin:** no provider availability, the detail sheet is a text wall, no visible loading/empty/offline state (a failed
   TMDB load shows one message; partial failures show empty rows silently).
7. **Apps:** hidden apps appear as dimmed cards with no label saying "hidden"; no categories although Android provides
   `ApplicationInfo.category`.

## Technically fragile / missing
- **No automated tests at all.** Regressions so far were caught only by manual scripted runs.
- **Reset layout deletes user data with no confirmation** (Android TV guidance: confirm destructive actions, show Cancel).
- **Back does nothing on Home content** (Android navigation doc: Back should reach the header, V).
- **Dock is invisible to TalkBack** (custom-drawn, no node provider). Google's AccessibilityDemo shows the required pattern (V).
- **No Watch Next / "continue watching"**, although the platform allows it with a runtime permission (D) and one open-source launcher does it (V).
- `refreshAll()` rebuilds every page on any data change (focus + allocation churn); acceptable at 24 apps, not elegant.
- TMDB credential is compiled into the APK (extractable by anyone holding the APK; not in source control or logs).
- `onNewIntent` / `launched` flag logic is subtle (restore vs reset); easy to break, untested.
- OMNI is not listed with `LEANBACK_LAUNCHER`, so it cannot be reopened from other app lists.

## Expensive to render (measured)
- Notched `clipToOutline` cards in long rows: Watch 79% janky, Apps 38% (D). Already removed for dense pages. **Rule: custom shapes only
  inside custom-drawn views.**
- Per-frame gradient allocation or software gradient rendering (Ambient) and full-screen multi-layer overdraw (backdrop + 3 scrims).
  Already replaced by baked bitmaps.

## Deliberate performance choices that must NOT be undone
Java Views (no Compose/Flutter runtime); one custom-drawn Dock; baked backdrop scrims (`Ambient.compose`); cached tiny art fields;
RGB_565 decoding; scoped `<queries>`; AOT compile in `deploy.sh`; invisible pre-draw of other tabs; springs on vsync with snap
under reduced motion; `hasOverlappingRendering=false` on pages.

## Retain / redesign / remove
| Component | Verdict |
|---|---|
| `Spring`, `Theme`, `Ambient` (glow/field/backdrop), `Images`, `Tmdb`, `AppRepo`, `Sheet`, `Ripple` | **Retain** (extend) |
| `Dock` | **Retain and generalise** into the single shelf primitive for Home, Apps and Watch |
| `Card` + `Row` + app-card helpers in `Page` | **Remove** from Home/Apps/Watch once Dock covers them (Card stays only if a non-shelf use remains) |
| `HomePage`, `AppsPage`, `WatchPage` | **Redesign** around the shared shelf grammar |
| `SettingsPage`/`SettingRow` | Retain; add confirmation sheet for destructive row |
| Header (`MainActivity.buildUi`) | Retain; fix safe area; add Back-to-header behaviour |
