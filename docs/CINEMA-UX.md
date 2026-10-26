# OMNI Cinema: how it looks, feels and works

Cinema is a part of the OMNI operating system, so it follows the launcher's rules (`launcher/docs/design/OMNI-TV-DESIGN-SPEC.md`); it differs only in colour, because films bring colour.

| Rule (from the launcher) | In Cinema |
|---|---|
| **Safe area** 64 dp sides, 30 dp top | Every Cinema page uses it. |
| **Navigation** (D11): the OMNI mark, one glass capsule of icons, only the selected item opens to show its name | Mark, then a capsule with Home, Movies, Shows. The utilities (Search, My List, Sources) are a second, smaller capsule on the right, icon only; the name opens on focus or selection. |
| **One focus language** (D3): a control that has focus is solid, bright, with dark content; artwork gets a ring | Controls, tabs and chips turn solid ivory with dark text. Posters get a gentle 6% lift, an ivory edge and a soft shadow. |
| **Glass, not blur** | Capsules are translucent charcoal with a hairline; backdrops have their fades baked into the bitmap once. No live blur. |
| **Real data only** (D6) | Rows, genres, ratings and "More Like This" come from the library; a row with nothing in it does not exist. Missing artwork becomes a composed card, never a fake picture. |
| **Back** | Sheet/page, then the previous screen, then the Home tab, then leaves Cinema. |
| **Motion**: short, interruptible, never blocking keys | 120-180 ms fades and lifts; the hero follows focus with a short delay so held keys do not thrash it. |
| **States**: loading = a gray sentence, empty = what to do, offline = what failed | "Your cinema is empty" with *Add a source*; "No series yet" says how episodes must be named; offline copies are dimmed and tagged. |
| **Colour**: the launcher is monochrome; Cinema adds a palette | Charcoal `#1A1A1D`, ivory `#E8DCC8`, crimson `#C83246` (only the main action and progress), gold `#E8B44F` (ratings, saved). The artwork's own colour lights the page from below. |

## Remote map
Up/Down move between the bar, the hero buttons and the rows; Left/Right stay inside a row and stop at its ends; OK opens or plays; Back steps out as above. Searching uses an on-screen keyboard on the left and results on the right.
