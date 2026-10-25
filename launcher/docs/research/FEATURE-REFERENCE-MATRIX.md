# Feature reference matrix

Columns: where it is seen (image ID in `design/references/INDEX.md`) | why it works | problem solved | better than the alternative? | Android TV
requirements | performance cost on this TV | OMNI adaptation. "Obs" = observed on the user's TV; "Static" = appearance only.

## Focus and selection
| Feature | Seen | Why it works / problem solved | Better? | Android TV needs | Cost | OMNI adaptation |
|---|---|---|---|---|---|---|
| **Item lifts, neighbours stay put** | A2 static, G2 obs, T2 static | Position is stable, so the eye does not hunt; focus is unambiguous | Yes for stability. Worse for *identifying* unfocused apps | none | negligible (one tile transform) | Built as D2: tiles keep a fixed pitch and only the focused tile lifts (scale 1.08, -6 dp); shelves open and close by relevance |
| **Label appears under/over the focused tile** | G1 obs, T2 static | Unfocused tiles stay clean; name shown only when needed | Yes where icons are recognisable (they are: real app icons) | none | negligible | Banner apps carry their name in the banner; icon-only apps show it under the icon; the stage shows the focused name large |
| **Inverted white fill for controls** | A3 static, G3 obs | One high-contrast rule that works over any artwork; monochrome by nature | Yes: always visible, needs no colour | none | none | Header tab focus, sheet options, Settings rows, retry button |
| **Thin outside ring** | T2 static, A2 (edge) | Survives light tiles because it sits outside with a gap | Yes for tiles with white banners (YouTube, 1001 TVs) | none | one stroke | Built: white 3 dp ring, 3 dp black gap, outside the tile (checked on the YouTube, Netflix and 1001 TVs banners) |
| **Dim inactive rows** | G3 obs | Directs attention to the active shelf | Yes | none | one alpha pass (already used) | Keep; grayscale + dim for inactive shelves |

## Artwork and backgrounds
| Feature | Seen | Notes | OMNI |
|---|---|---|---|
| **Art as environment (full-bleed, dimmed, bottom scrim)** | A1 static, G1 obs | Gives content a place; on a launcher with no key art it needs real art | Only on **Watch** (TMDB backdrops, baked scrims). Home/Apps = plain near-black: apps ship icons/banners, not key art, and faking it would break the "no fabricated art" rule |
| **Near-black page, not pure black** | A2 measured #16171C, G1 #0D0D0D | Pure black on LCD looks gray and bands; slight lift separates tiles | BG `#0A0A0A`, true black `#000` only in scrims and overscan edge |
| **Title as logo over art** | A1 static | Strong identity for content | Not available for apps; stage uses large Geist type instead |

## Navigation
| Feature | Seen | Notes | OMNI |
|---|---|---|---|
| **Top tabs + underline** | T1 static, G1 obs | Shared by every observed launcher; keeps vertical space for shelves | Keep; selected = white + underline; focused = white pill, black text |
| **Favourites row first** | T1, G1 | Fast access to what the person uses | Keep Favorites as the first shelf |
| **Shelves labelled by source** | T2, G4 | "On Netflix" tells where content lives | Keep on Watch; availability line from JustWatch data only |
| **System/overlay chrome top-right** | A1, A3, G1, T1 | Clock + settings live out of the content path | Keep clock; Settings stays a tab |

## Launching and returning
| Feature | Seen | OMNI |
|---|---|---|
| Launch feedback | not observable from stills (U) | Keep ripple (white) + scale-up from the tile, already measured fast |
| Return to place | G5 obs (focus returns to the top shelf) | OMNI restores the same item (tested, regress `launch_and_return`) - stricter than stock |

## Contextual interactions
| Feature | Seen | OMNI |
|---|---|---|
| Overlay uses the same highlight language | A3 static | Sheet options use the inverted fill; destructive confirm has Cancel focused first |
| Long-press discoverability | none observed | Keep hold-OK and MENU; hint line bottom-right |

## Motion (all unmeasured for references)
No recordings were used. The only motion facts are OMNI's own: input to first draw p50 11 ms, focus spring settles within 0.75 s (unit tested).

## Usability
| Observation | Source | Decision |
|---|---|---|
| Promo-driven hero pushes apps down the page | G1, T1 | Apps first, always |
| Counters, eyebrow labels and dot-joined meta lines add noise | frontend-design skill, own audit | Removed in the spec |
| Chips/filters add a second navigation layer | G1 | Not adopted |
