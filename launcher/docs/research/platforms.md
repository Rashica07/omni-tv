# TV operating systems: what they do, what OMNI takes, what it rejects

Legend: V verified (read primary page/source), S search-snippet only, D observed on the target TV, U unverified.
Target hardware (D): Vestel/MediaTek m5621, 4x ARMv7, ~930 MB RAM, panel **1280x720 @ 60.000004 Hz**
(`dumpsys display`), Android 11 (SDK 30). Frame budget = 16.7 ms.

## Cross-platform safe areas (the one place sources give numbers)
| Platform | Rule | Source | Level |
|---|---|---|---|
| Apple tvOS | Inset primary content **60 pt top/bottom, 80 pt sides** (on a 1920x1080 point grid = 5.6% / 4.2%) | Apple HIG "Layout" JSON | V |
| Amazon Fire TV | Nothing in the **outer 5%** of any edge; focused item and text inside the inner 90% | developer.amazon.com design guidelines | V |
| Roku | Title zone 80% (text), action zone 90% (important visuals); FHD title 1534x866 @192,106 | developer.roku.com/dev/docs/graphics | V |
| LG webOS 6 | **20 px** margin; all selectable objects, text and logos inside it (resolution not stated) | webostv.developer.lge.com overscan guide | V |
| Android TV | Not stated on the design pages I read | developer.android.com | V (absence) |
| Samsung Tizen | Not stated on UX checklist | developer.samsung.com | V (absence) |

**OMNI decision:** 5.5% top/bottom (30 dp = 40 px at 720p) and 64 dp (85 px) sides. v2 had the header at 20 dp and the
dock ending 8 dp above the bottom edge: both inside the 5% band. Fix in the redesign.

## Apple tvOS (V, primary: Apple HIG JSON endpoints for focus-and-selection, designing-for-tvos, layout, top-shelf, tab-bars)
- **Observed:** one focused item at a time; tvOS shows focus by lifting/"parallax" the item; focusing is separate
  from selecting ("selection requires a separate gesture"). Apple says rely on system focus effects, **avoid changing
  focus without the person's interaction**, and when a focused item disappears under a remote, move focus to a nearby
  remaining item. "Make sure that people can bring focus to every element."
- **Tab bar:** top, translucent, only the selected tab opaque, the selected tab gets a shadow when the bar has focus;
  default list of **five or fewer**.
- **Top Shelf:** previews of featured/new content; "avoid promoting content people have already purchased, rented, or watched."
- **Lesson:** focus must be the visual centre and must never move on its own; recommendations should not repeat what the
  person already finished.
- **Implication:** keep focus restoration deterministic (done in v2); tab bar max 4 (we have 4); the Watch hero should not
  show titles the user already opened (not knowable from TMDB, so we only promise "trending", never "for you").
- **Rejected:** Apple's parallax tilt as a *requirement*; OMNI's custom-drawn tiles use depth through width change instead.

## Android TV / Google TV (V, developer.android.com navigation page; D for permissions on the target TV)
- **Observed (doc):** "always have an item in focus that a user can take action on immediately after your app starts or any
  time it is idle"; focus moves to the nearest object in the pressed direction; use a **uniform focus scheme** across the
  app; "Only use nextFocus attributes ... if the default order does not work well"; **Back = previous destination**, with
  top navigation: scroll to top and focus the menu; "Let users exit out of the app without confirmation"; show Cancel when only
  destructive/confirming actions are visible.
- **Observed (D):** `android.permission.READ_TV_LISTINGS` is **dangerous** (runtime-grantable) on this TV;
  `ACCESS_ALL_EPG_DATA` is signature|privileged. The TvProvider tables (Watch Next, preview programs, channels) are
  readable as shell but **empty** on this TV today.
- **Observed (S, developer.android.com Watch Next pages):** Watch Next is a system-owned row; apps insert
  `WatchNextProgram` items (CONTINUE/NEXT/NEW/WATCHLIST); on Google TV "Continue watching" requires Google certification
  for publishers. A launcher reads these through `TvContractCompat`.
- **Lesson:** a launcher can legitimately show real "continue watching" from apps that publish it, ad-free, with a normal
  runtime permission. This is the honest recommendation source; TMDB is the *discovery* source.
- **Implication:** add a Watch Next reader as a Home shelf, shown only when data exists. Fix Back to follow the doc
  (OMNI currently does nothing on Back from Home content). Platform *requirements* here: HOME intent filter, package
  visibility, runtime permission. Conventions: tab bar placement, focus scheme. Implementation choices: everything visual.

## Amazon Fire TV (V, developer.amazon.com design guidelines)
- **Observed:** home = **global navigation menu on the left + content tiles on the right**; Up/Down moves the menu and
  focusing an item previews that node on the right; Left from content returns to the menu; body text **>= 14 sp** (about 19 px
  at 720p); "less saturated colors ... cool colors work better than warm"; design at 1920x1080/320 dpi = 960x540 dp;
  1-D lists wrap. 2026 accessibility blueprint (S): every core journey completable by remote alone; focus "visible,
  predictable, and recoverable."
- **Lesson:** a persistent left menu keeps location obvious and uses the horizontal axis for navigation; recoverable focus is
  an explicit requirement. Warm/saturated colour advice conflicts with OMNI's crimson+gold identity, so OMNI uses them
  sparingly (focus ring, one dash, one label) instead of as fields.
- **Commercial lesson (U):** recommendation density and promoted content on Fire TV's home are widely discussed; I did not
  verify specifics, so this is recorded as a design *guard*, not a finding: OMNI never fills space with promoted rows.
- **Rejected:** wrap-around focus in lists (OMNI clamps at the ends: predictable and no accidental page jumps).

## Samsung Tizen / One UI for TV (V partial: developer.samsung.com UX checklist; the One UI home itself is U)
- **Observed:** "All focusable objects should be aligned in grids, not diagonally" (1.4, should); users "must be able to
  locate selectable objects using the four directional buttons" (2.2, must); "Only one object can be focused at a time"
  (4.2); "The focused object must be clearly recognizable against the other objects" (4.3, must); "focus pattern ...
  should be similar or consistent within the same application" (4.4, should).
- **Lesson:** consistency of the focus pattern is a stated rule, not a taste. Grids over diagonals.
- **Implication:** one focus language across Home, Apps, Watch, Settings (crimson ring/marker), one shelf mechanism everywhere.
- **Gap (U):** current One UI TV home layout and persistent shortcut bar: not verified; not relied on.

## LG webOS (V partial: overscan guide; launcher/home design is U)
- **Observed:** 20 px safe margin for selectable objects/text/logos (webOS TV 6.0). Magic Remote pointer support is a
  review requirement per a vendor article (S, not LG).
- **Gap (U):** LG's current UX guideline pages were not reachable (my guessed URL returned 404); the 2014 NetCast PDF in
  the search results is outdated and not used.
- **Implication:** OMNI targets D-pad. This TV has no pointer remote; no pointer mode is built (documented limit).

## Roku (V: safe zones; focus/navigation rules U)
- **Observed:** two safe zones, text inside 80%, important visuals inside 90%.
- **Lesson:** separate, stricter margin for *text* than for imagery.
- **Implication:** text >= 8% in from edges only where it is critical; imagery may sit at 5%. OMNI's backdrop bleeds to the
  edge (decorative); titles sit inside the 64 dp margin.

## NVIDIA SHIELD / other Android TV (U)
Not researched beyond the Android TV platform docs; no distinct lesson was verified.

## Synthesis: decisions that follow
1. **Navigation model:** keep a persistent *top* header (Apple, Android TV doc) but make it the single place for location;
   Back from content goes to the header, then does nothing (Android doc).
2. **One interaction grammar:** Left/Right inside a shelf, Up/Down between shelves, Up from the first shelf to the header,
   OK opens, hold OK edits. Same on Home, Apps and Watch (Samsung 4.4; Amazon "predictable").
3. **Safe area 5.5% / 64 dp**, text >= 14 sp equivalent at 720p, clamp (not wrap) at list ends.
4. **Recommendations** come from the platform's Watch Next (ad-free, real) and TMDB (discovery, labelled as such).
5. **Accessibility** is a differentiator (none of the five open-source launchers has explicit support; see open-source.md)
   and Google's own TV sample says custom views need `AccessibilityNodeProvider`/`ExploreByTouchHelper`.
