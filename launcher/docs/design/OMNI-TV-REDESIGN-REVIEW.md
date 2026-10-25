# Redesign review log

## Pre-implementation critique of the spec (done before coding)
1. **Inconsistency found and fixed:** the matrix first said "keep the accordion on the focused shelf"; the evidence (A2, G2, T2) says neighbours stay still. Resolved as D2 (shelf-level accordion only).
2. **Risk: grayscale filter per tile per frame.** Mitigation: one static `ColorMatrixColorFilter`, applied only to visible unfocused tiles, never recreated; measure with `bench.py home` before/after.
3. **Risk: white focus ring invisible on white banners.** Mitigation: ring sits *outside* the tile with a black gap (D3); verify on YouTube/1001 TVs/Netflix tiles in captures.
4. **Risk: Home without an art field looks empty.** Mitigation: large title stage, real banners at focus; judge from screenshots, not assumption.
5. **Risk: removing colour hurts app recognition.** Mitigation: D4/D5 (focused tile in colour, names always present). Judge in captures.
6. **Gap acknowledged:** no Samsung/LG interface evidence and no motion references; the spec makes no claim of matching them.

## Post-implementation review (v4, captured on the TV, scripted keys)

Evidence: `docs/v4/` (all states), `docs/compare_v3_v4_*.png` (before/after), `docs/design/boards/` (references vs OMNI), `docs/perf/results.jsonl`.

### What changed and whether the evidence supports it
| Spec decision | Built | Result observed in captures |
|---|---|---|
| D1 monochrome chrome | `#0A0A0A` page, white/gray text, no accent anywhere (`grep CRIMSON|GOLD|IVORY src` is empty) | Home/Apps/Settings are fully black-and-white; colour appears only in the focused tile and in posters/backdrops |
| D2 stable tiles, relevant shelves | Fixed-pitch tiles, only the focused tile lifts; shelves open/close | Neighbours no longer shift; shelf switching works; the earlier rotated labels are gone |
| D3 one focus language | White ring outside the tile with a black gap; white fill + black text for controls (header tab, sheet, settings row, retry) | Ring is clearly visible on white banners (YouTube, 1001 TVs); sheet, Settings and header share the same rule |
| D4 grayscale unfocused, colour focused | Static grayscale filter on unfocused tiles | Reads as monochrome; focus pops. Icon-only apps are the weakest read in gray |
| D5 names, no rotated text | Banners carry the name, icon-only apps show it under the icon | Readable |
| D6 no art field on Home/Apps | Removed the colour field and accent computation | Calmer; but see defect 3 |
| D7/D9 header and safe area | 30 dp / 64 dp, hint line inside the safe area | Matches Titan/Google structure |

### Defects found by reviewing the captures, and what was done
1. v4 first pass: unfocused white banners were bright slabs; collapsed strips cropped banners into nonsense. **Fixed** (darker unfocused tiles; icons, not cropped banners, in collapsed strips).
2. Apps as a single row needed up to 23 presses. **Fixed**: A-Z in short named shelves (`Apps # to F`, `H to P`, `P to Y`).
3. "Try again" was invisible until focused; hints shown on empty/offline screens; Move mode looked identical to focus; the sheet showed a package name. **Fixed** and re-captured.
4. Collapsed strips too dim (55% veil). **Tuned** to 30%; unfocused active tiles 62% to 70%. (Recaptured after the final deploy; see below.)

### Remaining defects and limits (not fixed)
- **Home's right side is empty.** There is no honest art for apps to put there. A tvOS-like art-first Home would need real key art per app, which does not exist; inventing it is against the spec. Possible future option: show Watch backdrops for streaming apps only. Not built.
- Gray icon-only apps in collapsed strips (a single glyph such as Plex's chevron) are hard to identify.
- **Samsung One UI and LG webOS were not observed** (no interface imagery available); no motion references were used for any third-party OS.
- Verified with scripted key events and the accessibility node tree only: **not with a hand-held remote and not with TalkBack running** (disabled on this TV).
- The TMDB key is compiled into the APK; keep the APK private.
