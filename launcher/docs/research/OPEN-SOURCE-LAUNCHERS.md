# Open-source TV launchers: source review (see also FEATURE-REFERENCE-MATRIX.md)

Cloned shallow (2026-10-24) into an isolated directory; source files read, nothing executed. LOC from `find ... | wc -l`.
"Matteogrifantini/launchertv" now redirects to **Gathin23/cleanyourtv**. A repo literally named "AndroidTVLauncher" does not
exist (my guessed owner 404'd); substitutes below are the credible projects the search surfaced.

## Licensing rule for OMNI
**GPL-3.0 sources are for learning only**: copying any of their code would force OMNI under GPL-3.0. MIT/Apache-2.0
sources may be adapted with attribution. Fonts: Geist (OFL) is already bundled; keep its license file with the APK source.

| | FLauncher (osrosal fork) | LTvLauncher | nielsvanvelzen/tv-launcher | CleanYourTV launcher | lonelytransistor/LauncherAndroidTV | android/tv-samples |
|---|---|---|---|---|---|---|
| License | GPL-3.0 | GPL-3.0 | GPL-3.0 | **MIT** | **MIT** | **Apache-2.0** |
| Last commit | 2025-07-26 | 2026-10-24 (active) | 2026-10-21 | 2026-10-19 | 2023-12-31 (stale) | 2026-07-22 |
| Stack | Flutter/Dart 15k + Java 0.9k | Flutter 37k + Java 6.6k | Kotlin + Compose TV 2.3k | **Java Views** 3.8k | Java Views 4.8k | Kotlin/Compose + Leanback Java |
| Home composition | Customisable category rows/grids, wallpaper | FLauncher fork + weather/status bar, "Continue Watching" rows | Toolbar tabs + Favorites + Channels + All apps | Apple-TV-style hero + dock, glass panel, themes (JSON) | Streaming-service bar + recents | Reference apps (browse/immersive) |
| Focus model | Custom traversal policy (`custom_traversal_policy.dart`), `ensure_visible`, `FocusKeyboardListener` (long press) | same lineage | `focusRestorer()` on tabs/rows/lists, `onKeyDown` MENU -> long-press map | Manual `requestFocus` on row 0 / last tile | `requestFocus` on adapters | `focusRestorer`, `focusGroup`, `BackHandler`, `onPreviewKeyEvent` |
| Discovery | Java `queryIntentActivities` for LEANBACK + LAUNCHER **in parallel** | same | `AppResolver`: LEANBACK + LAUNCHER, `PackageChangeReceiver` | `<queries>` + `getLeanbackLaunchIntentForPackage`, lists itself with LEANBACK_LAUNCHER | n/a | n/a |
| Recommendations | none | recency-sorted continue rows | **TvProvider Watch Next/preview channels** (`READ_TV_LISTINGS` runtime perm) | none (ads removed) | movie/series cards | Watch Next publishing samples |
| Animation | `AnimatedContainer` 100-250 ms, ease/decelerate | same | Compose defaults | `ViewPropertyAnimator` 120-600 ms decelerate; hero name slides 12 dp; tile enter 420 ms | `animate()` scale 1.5x | n/a |
| Accessibility (explicit labels in source) | none found | none found | 6 files | only `contentDescription=@null` (decorative) | none | **AccessibilityDemo**: ExploreByTouchHelper / AccessibilityNodeProvider for custom views |
| Tests (files) | 24 | 54 | 0 | 2 | 0 | n/a |
| Memory | **D: 98 MB PSS on this TV** (measured earlier) | not measured | not measured (Compose runtime) | not measured | not measured | n/a |
| Special | OLED-safe screensaver idea (LTv) | | | Accessibility service to catch HOME on TVs that can't change launcher | | |

## What OMNI adopts (re-implemented independently, no code copied)
1. **Watch Next as a real "Continue watching" shelf** (tv-launcher pattern: runtime `READ_TV_LISTINGS`, `TvContractCompat`):
   the one honest, ad-free recommendation source. Shown only when data exists.
2. **Parallel discovery** for LEANBACK and LAUNCHER queries (FLauncher) to cut startup scan time.
3. **`focusRestorer` semantics** in Views: remember the last focused child per shelf (OMNI already restores per app; extend per shelf).
4. **List OMNI itself with LEANBACK_LAUNCHER** (CleanYourTV) so users can reopen it from other app lists.
5. **MENU key = hold-OK** (tv-launcher) so the options menu is reachable without a long press.
6. **Accessibility via `AccessibilityNodeProvider`** for the custom-drawn Dock (Google AccessibilityDemo, Apache-2.0).
7. **Short, decelerating focus motion** (FLauncher 100-250 ms; CleanYourTV 120-260 ms) is the convention; OMNI uses springs but
   must settle in that window.

## What OMNI rejects
- A Flutter/Compose runtime: measured 98 MB for FLauncher on this TV; OMNI's Java Views Home is 48-77 MB.
- Frosted "glass" panels (CleanYourTV): costly blur and generic.
- Accessibility-service HOME interception: unnecessary, OMNI holds the real HOME role (D: `dumpsys role`).
- Wrap-around focus, wallpaper picker, weather/status widgets: scope that does not help launching apps.
