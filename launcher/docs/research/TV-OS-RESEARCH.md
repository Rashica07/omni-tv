# TV operating systems: research record (v4)

Evidence classes: **V** read from a primary page/source this session; **OBS** observed on the user's TV; **STATIC** a still image from the
platform owner or a reviewer (appearance only, see `design/references/INDEX.md`); **U** unverified, not relied on.
Detailed platform notes from the first pass (safe areas, Android navigation rules, Fire TV, Roku, Samsung checklist, LG overscan) remain in
[platforms.md](platforms.md); this file adds the image evidence and the decisions drawn from it.

## What the images show (one finding per platform, each tied to an image ID)

**Apple tvOS (A1-A5, STATIC + HIG text V).**
- The home is art-first: a full-bleed key art with the title as a logo, and apps in one low translucent strip (A1). There is no
  text block, no heading, no counter. Navigation chrome at rest is a clock/profile pill.
- Browsing shelves (A2): headings are small gray; the focused tile is **lifted and larger with a faint light edge; its neighbours do not move**.
  The page background measures **#16171C** (a near-black with a cool cast), not pure black.
- Controls (A3): the highlighted control is a **solid white circle with a dark glyph**; unhighlighted controls are translucent gray.
- HIG text (V, apple.com HIG JSON): "Avoid changing focus without people's interaction"; focus effects should be system-consistent;
  tab bar: translucent, selected tab opaque, five or fewer items; tvOS safe area 60 pt top/bottom, 80 pt sides.

**Google TV on this TV (G1-G5, OBSERVED).**
- Left-aligned top tabs with an underline on the active one; the focused **app tile scales up and its name appears beneath it**; neighbours do not move (G1, G2).
- Controls (chips) use the same **solid white pill with dark text** when focused (G3); rows above dim as the page scrolls; the background resolves from #0D0D0D to pure black.
- The page is dominated by promoted content rows ("Prossimi contenuti", per-service rows). It shows real "continue watching" only inside a service's row (G4).

**Titan OS (T1-T4, STATIC-REVIEW/OFFICIAL, 2024 pre-release demo).**
- Same structure as Google TV: top tabs (search, Home, Channels, Apps) with an underline on the selected one, clock and three icons on the right (T1).
- Below the hero: "Favourite Apps" as a row of square tiles, then one labelled row per service (T1, T2). Focus is a **thin ring outside the tile with the title underneath** (T2).
- The hero is a promotional carousel (an Amazon ad). Titan's value here is its plain hierarchy, not its content policy.

**Samsung One UI Tizen and LG webOS (GAPS).** No usable home-screen imagery was reachable. Samsung's official May 2025 images are lifestyle renders;
LG's reachable image is a 464 px render of a windowed News Portal (L1). Conclusions for these two rest on developer documentation only:
Samsung's UX checklist (focus must be clearly recognisable, one focus pattern per app, grids not diagonals) and LG's 20 px safe margin (both V, see platforms.md).
This is stated in the spec so no Samsung/webOS feature is presented as observed.

## Cross-platform conclusions (what is shared, therefore safest to adopt)
1. **Top tabs with an underline** (Titan, Google TV, OMNI already) - the one navigation model all three observed launchers share.
2. **Focus = the item itself changes** (lift/scale, label reveal) while neighbours stay still (tvOS A2, Google G2, Titan T2). OMNI's v3 accordion moves neighbours; this is a deliberate difference that must earn its place (see spec decision D2).
3. **Inverted white fill is the control-focus language** (tvOS A3, Google G3). It is monochrome by nature and survives any artwork.
4. **Art is the environment, chrome is quiet** (tvOS A1, Google G1). Colour belongs to media, not to the OS.
5. **Promoted content rows are the cost of commercial launchers** (G1, G4, T1). OMNI shows only data it can stand behind.

## Rejected
Promo hero carousels, ad rows, translucent "glass" panels (blur cost on this GPU), wrap-around focus, per-platform "modes",
tvOS parallax tilt as a requirement (kept optional; not drawn in the custom view), light theme.

## Unmeasured
All reference **motion** (no recordings used, per the user's source choice). OMNI's own timings are measured; see `docs/perf/results.jsonl`.
