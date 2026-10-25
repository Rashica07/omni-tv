# Design directions, decision, interaction and motion model

Inputs: [platforms.md](platforms.md), [open-source.md](open-source.md), [audit.md](audit.md), measured hardware limits
(1280x720@60, 4x ARMv7, ~930 MB RAM; custom-drawn views are cheap, clipped/shadowed card rows are not).

## Three directions

### A. "Accordion stack": one shelf grammar on both axes (CHOSEN)
- **Home:** header, a typographic stage, and a *stack of visible shelves*. The active shelf is tall; the next shelves stay visible as
  thin collapsed strips. Down expands the next shelf and collapses the current one (vertical accordion); Left/Right opens the focused
  tile into a wide feature tile while its neighbours become labelled **spines** (horizontal accordion).
- **Navigation:** top header (Apple/Android doc convention), Back goes content -> header, Up from the first shelf goes to the header.
- **Hero/artwork:** the focused tile *is* the featured art (no duplicate preview card); environment is a dim art field (apps) or a
  baked backdrop (Watch).
- **Applications:** Home shelves = Continue watching (Watch Next, only when real data exists), Favorites, Recent, Most used.
  Apps tab = **category shelves** from `ApplicationInfo.category` plus an explicit Hidden shelf.
- **Watch:** the same stack with TMDB shelves; stage shows synopsis and *data-backed* availability.
- **Focus/motion:** one spring-driven continuous focus value per axis; everything (title, environment, tiles, shelves) retargets from it.
- **Accessibility/legibility:** every tile named (spine labels rotated 90 degrees), >=14 sp-equivalent text, node provider for TalkBack.
- **Performance risk:** low. One custom view, <= ~30 tiles drawn; clipPath on tiles only (measured fine).
- **Why distinctive:** an accordion on both axes is not a stock Android TV launcher layout, and the whole OS shares it.

### B. "Rail and ladder": persistent left rail + vertical labelled list + detail stage
- Left rail for 4 destinations (Fire TV / LG convention), a vertical list of apps with the focused row expanding, art at right.
- + Names always readable, long lists are easy, very accessible. - Wastes the cinematic strength for Watch (posters are horizontal),
  appears similar to Fire TV, little art for apps in a detail pane, left rail steals ~15% width.
- Rejected: weaker for the entertainment side and not more distinctive than A.

### C. "Marquee": immersive hero first (Home = recommendations)
- Full-bleed TMDB backdrop on Home with a slim dock of apps; Watch merged into Home.
- + Most cinematic. - Home becomes network-dependent; apps (the launcher's job) are demoted; hero art for apps does not exist;
  risks implying personalised recommendations we do not have (directive and Apple's Top Shelf guidance warn against it);
  continuous large-bitmap decoding on Home.
- Rejected.

### Choice
A scores highest on usability (visible shelves fix the "hidden shelf" problem, names fix identification), reuses the measured-fast
primitive, and is the only option that unifies Home, Apps and Watch. B and C each fix one weakness while creating a worse one.

## Interaction model (remote first)
| Input | Behaviour |
|---|---|
| Left/Right | move within the active shelf; clamp at ends (no wrap) |
| Up/Down | move between shelves; Up from the first shelf -> header tab (the **selected** tab, never the nearest) |
| OK | open/launch; brief ripple + scale-up |
| Hold OK or MENU | options sheet (move, pin, hide, info, uninstall); a hint line says so |
| Back | closes sheet -> else content -> header -> else nothing (launcher root; never exits, never loops) |
| Home | from an app we launched: restore the same tile; while on OMNI: reset to first tile |
Rules: always one focused item; focus never moves without input; after sheets, app returns, tab changes and data refresh
focus is restored by *identity* (package or title id), falling back to the nearest remaining item; removed/hidden focused item ->
nearest neighbour; destructive actions ask for confirmation with Cancel focused first.

## Layout system (960x540 dp, 1280x720 px)
Safe area: 64 dp sides, 30 dp top/bottom (>= 5.5%). Type scale (sp): 56 title, 22 meta, 18 shelf heading, 15 tab/body, 13 hint (never below 13).
One focus language: crimson 3 dp ring on tiles, crimson 3 dp marker on rows, crimson underline on tabs. Gold only for the active
shelf heading and the rating star. States: focused / idle / dimmed (other shelves) / loading (shimmer-free: static placeholder
tint) / unavailable (label + reason).

## Motion system
Springs (stiffness, damping ratio): focus axis 240/0.82 (settles ~250 ms), shelf axis 220/0.88, page/tab 330/0.86, sheet 260/0.86,
environment cross-fade 110/1.0. Distances: title rises 22 dp, shelves 28 dp. Rules: every animation is a retargetable spring (no
queued timers); under system "animations off" springs snap; no animation delays input; environment/backdrop changes never block
keys; title/environment work is skipped for intermediate focus events during a held key (coalesced to the latest value).

## Performance budget (target TV)
Frame <= 16.7 ms p95, <= 33 ms worst; jank (frames > 16.7 ms) <= 3% Home/Apps, <= 5% Watch; PSS <= 85 MB with Watch loaded;
cold start <= 500 ms; idle CPU 0%; bitmap budget: 10 MB posters + 8 MB baked backdrops; <= 2 concurrent image fetches near focus,
decode off the main thread; no per-frame allocation in `onDraw`.
