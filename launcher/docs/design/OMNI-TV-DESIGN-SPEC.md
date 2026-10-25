# OMNI TV design specification (v4, monochrome)

Authoritative for all UI work. Evidence: `references/INDEX.md`, `../research/TV-OS-RESEARCH.md`, `../research/FEATURE-REFERENCE-MATRIX.md`.
Target: 1280x720 @ 60 Hz Android TV, ~930 MB RAM. Implementation: native Java Views, tokens in `Theme.java`.

## Decisions (each tied to evidence)
| # | Decision | Evidence | Rejected alternative |
|---|---|---|---|
| D1 | **Monochrome chrome.** Page `#0A0A0A`, true black `#000` only in scrims; text white / gray; no accent colour anywhere in the OS. Colour exists only inside media pixels (app icons/banners at focus, posters, backdrops). | tvOS A2 (#16171C near-black), Google G1 (#0D0D0D to #000); user brief | Pure `#000` everywhere (bands on LCD, no tile separation) |
| D2 | **Stable tiles, relevant shelves.** Tiles keep a fixed pitch; only the focused tile lifts (scale 1.08, -6 dp) and gets the ring. **Shelves** still open/close by relevance: the active shelf shows full tiles, the others collapse to a thin strip of small tiles. | A2, G2, T2 (neighbours do not move); own v3 audit (rotated labels, crowd) | v3 horizontal accordion (moves neighbours, unreadable spine text) |
| D3 | **One focus language.** Tiles: white 3 dp ring outside the tile with a 3 dp black gap. Controls (header tab, sheet option, Settings row, retry): solid white fill, black text. Header selected-not-focused: white text + 2 dp white underline. | A3, G3 (white fill), T2 (outside ring), own finding that a ring on white banners vanishes | Coloured ring (v3 crimson) |
| D4 | **Unfocused apps are grayscale, the focused app is in colour.** Gray tiles keep the page monochrome; colour at focus gives the "alive" moment. | A2 (focused tile is the lively one), brief | Colour icons everywhere (colourful chrome); fully gray apps (hurts recognition) |
| D5 | **Names, no rotated text.** Banner apps carry their name in the banner; icon-only apps show their name under the icon at all times (13 sp, gray; white when focused). | audit; Samsung checklist 4.3 (focus clearly recognisable) | Rotated spine labels |
| D6 | **No art field on Home/Apps.** Apps have no key art; inventing one would break "no fabricated art". Watch uses the title's real backdrop with baked scrims. | A1 (art-first only works with real art) | Colour field from the icon (v3) |
| D7 | **Header = top tabs + underline** (Titan, Google, v3). Order Home, Watch, Apps, Settings. Back: sheet, content, header, Home tab, nothing. | T1, G1, Android navigation doc | Left rail |
| D8 | **Type:** Geist. Title 52/44/36 by length, meta 18, shelf heading 16 (white active, gray inactive), tile label 13, hint 13. No ALL-CAPS eyebrows, no dot-joined meta strings (frontend-design skill's template tells), except the single JustWatch attribution on Watch. | skill review | v3 caps eyebrows/counters |
| D9 | **Safe area** 64 dp sides, 30 dp top/bottom. Hint line bottom-right inside it. | Apple 60/80 pt, Amazon 5%, Roku 90%, LG 20 px (platforms.md) | |
| D10 | **Motion:** springs only; focus spring settles < 0.75 s; page transition shares the header; sheets and launch use one easing family. Reduced motion snaps. | v3 measurements | Timers |

## Tokens (single source: `Theme.java`)
Colour: `BG #0A0A0A`, `BLACK #000000`, `WHITE #FFFFFF`, `GRAY1 #A8A8A8` (secondary text), `GRAY2 #6B6B6B` (tertiary), surfaces `S1 #141414`, `S2 #1C1C1C`, `S3 #2A2A2A`, hairline `#2E2E2E`.
Shape: tile radius 12 dp (uniform: the notched shape is dropped; it cost 38-79% jank in view-based rows and adds nothing monochrome needs).
Focus: lift 6 dp, scale 1.08, ring 3 dp + 3 dp gap. Motion springs in `Motion` block (focus 240/0.82, shelf 220/0.88, page 330/0.86, sheet 260/0.86).

## Screens
- **Home:** stage (title + one meta line) over BG; shelves: Favorites (active at start), Continue watching (only with real Watch Next data), Recent, Most used. Active shelf = 178x100 tiles; collapsed = 56x48 strip tiles.
- **Apps:** same stage + shelves All apps (A-Z), Recently installed (real install dates), Hidden. Hold OK = options.
- **Watch:** stage = title, year/rating/type, two-line synopsis, availability line (+ "JustWatch" attribution). Shelves of posters (112x168). Backdrop environment. Loading / offline (Try again, white pill) / needs-token states.
- **Settings:** big title left; rows right; focused row = white fill, black text; destructive row confirms in a sheet with Cancel focused first.
- **Sheet:** panel `S1` with hairline, options with white-fill focus; scrim 70% black.
- **States:** loading = text "Loading..." in gray, no skeleton; empty = sentence saying what to do; offline = what failed + one action.

## Accessibility
Every tile named (`contentDescription`/virtual node); focus ring visible against any tile; white-on-black contrast >= 15:1 (text), gray text >= 7:1 on BG; reduced motion honoured; ShelfStack exposes virtual accessibility nodes for the active shelf.

## Performance budget (must hold)
Frame p95 <= 16.7 ms, no frame > 33 ms in normal use, PSS <= 85 MB with Watch loaded, cold start <= 500 ms, idle CPU 0%.
Rules: no per-frame allocation in `onDraw`; one static grayscale `ColorFilter`; no full-screen layers except the baked Watch backdrop.


---
# v5 additions (this revision)

## System chrome: glass, not blur
The OS chrome (navigation, clock, banner, sheets, category cards) uses one glass material (`Glass.java`, research in `docs/research/LIQUID-GLASS.md`):
pre-blurred backdrop sample, dark veil, white tint, top sheen, top-bright rim, short shadow. Content (app art, posters, backdrops) is never tinted
or monochromized except by the existing grayscale-until-focused rule for app tiles.

## D11 Navigation (replaces the plain-text tab bar)
One component, `NavBar`: a large OMNI mark (ring + dot + wordmark) and a single glass capsule of five oversized icons (Home, Watch, Apps, Inputs,
Settings). The selected destination opens horizontally to show its name; the rest stay compact (52 dp). Focus on the bar turns the open pill solid
white with dark content (the control-focus rule, D3); focus elsewhere relaxes it to a glass pill. One focus target; Left/Right move the selection live;
all motion is an interruptible spring. Header height 56 dp inside the safe area.

## D12 Clock
A glass capsule at the right of the header: time in Geist 28 (device 12/24-hour setting, device locale) and a two-line date. Updates once per minute
(ACTION_TIME_TICK) and on time/zone/locale changes. No seconds, no weather, no invented information.

## D13 Apps
Landing page of large category cards (Favorites, Recently opened, Games, Streaming and TV, Music and audio, Tools, All apps, Hidden), each with a glyph,
a count, and a 2x2 collage of the real app icons (grayscale until focused). A category appears only if it contains apps. Categories come from evidence
the platform provides (`CategoryRules`): declared category, game flag, publishes channels/Watch Next, opens video/audio files. No evidence = no category
(the app stays under All apps). Inside a category: the shelf grammar; more than 8 apps are cut into A to Z shelves. Back returns to the landing page.

## D14 Icons
`IconArt` pipeline (docs/research/ICON-AUDIT.md): banners cover-fit and rounded once; self-contained icons are the tile; free-form icons keep transparency on a plate.

## D15 Editing modes
Move is an explicit mode: a glass banner replaces the navigation ("Move Netflix; Left and Right to move, OK to place, Back to cancel"); the held tile floats
above a dashed slot that shows exactly where it will land; the other tiles slide aside and everything else steps back; chevrons show which ways it can move;
OK drops it with a small bounce and a confirmation capsule; Back restores the original order (nothing is saved until OK). Pin, unpin, hide and show confirm
through the same capsule.

## D16 Inputs
`Inputs` page: HDMI and other inputs as device cards on the shelf grammar. Names and state come only from the public TvInputManager API. Device type is
"set by you", "guessed from the name your TV reports", or absent: never presented as detected. OK asks the system TV app to show the input.

## D4 revised (supersedes the grayscale-until-focused rule above)
The OS chrome is black, white and neutral gray; **content keeps its own colours**. App icons and banners, posters, backdrops and device art are never
desaturated or tinted. Unfocused content is only darkened a little for hierarchy (and more on inactive shelves); focus adds scale, lift and the white ring.
Colour also reaches the room: Home and Apps sample a few pixels of the focused item's artwork and wash them, dark and faded away from the navigation, behind
the page (`Ambient.setGlow`); Watch shows the title's own backdrop. Chrome (navigation, clock, sheets, banner, cards) stays monochrome glass.
Acceptance test: one screenshot must read as a clean black-and-white OS, authentic colourful content, and glass-like depth.
