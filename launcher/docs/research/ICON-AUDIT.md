# App icon audit (this TV, 24 launchable apps, measured 2026-10-24)

Method: OMNI logs, for every installed app, the Drawable class and intrinsic size of its launcher icon and banner
(`adb shell setprop log.tag.OmniState DEBUG`, lines `icon pkg=...`). Source: `docs/research/` data below, produced by `IconArt.build`.

## What the apps actually ship
| Finding | Examples | Count |
|---|---|---|
| Widescreen raster banner (BitmapDrawable) | YouTube 426x240, Netflix 213x120, Plex 213x120, Spotify 213x120, Disney+ 341x192, Mobiflix 852x479 | 18 |
| **Low-resolution banners** (213x120): upscaling them was why Netflix/Plex looked blurry | Netflix, Plex, Spotify, Prime Video, TCL browser, FX | 7 |
| Vector banner (VectorDrawable 426x240) | YouTube Music | 1 |
| **Square "banner"** (682x682, 64x64): not widescreen; cropping it into a 16:9 tile garbles it | `com.tvplator.iptv` (682x682), `com.tvplator.fusion` (64x64) | 2 |
| Icon-only app, opaque rounded-square PNG (909x909) | `com.tvplator.elite` | 1 |
| Icon-only app, **64x64 raster with a free-form silhouette** | Chrome (the `atvl.chrome` shim), `com.home.iptvfree` | 2 |
| `AdaptiveIconDrawable` | none on this TV (still handled) | 0 |

## Failure modes in v4 (before this pass) and the fix
1. Icon-only apps were drawn small on a rounded plate: an opaque rounded-square icon looked like "a tiny rounded square trapped inside another
   square". **Fix:** an icon with its own opaque silhouette is the tile (full-bleed, no plate, no extra corners); transparent margins are cropped
   so every icon fills its square the same way.
2. A square image supplied as a banner was cover-cropped into a wide tile. **Fix:** a banner counts only if its aspect is 1.45 to 2.1 (16:9 is 1.78);
   otherwise the launcher icon is used and treated as an icon.
3. Small banners were upscaled to 400 px, softening them. **Fix:** banner bitmaps are rendered at the source width (capped 256..400 px).
4. Rounded clipping was done per frame with `clipPath`, which was the GPU cost behind Apps jank. **Fix:** all rounding is baked once on the scan
   thread (`IconArt.roundCorners`), tiles are plain `drawBitmap` calls.
5. Free-form transparent icons (Chrome): kept transparent, set on a neutral plate at 62% so the silhouette is respected.

## Rules (see `IconArt.java`)
- `opaque fraction of the content box >= 0.78` (or `AdaptiveIconDrawable`) = **self-contained**; otherwise **free-form**.
- Adaptive icons are drawn by the system's own mask (the correct API), not re-masked by OMNI.
- Never assume a square PNG should be cropped or have its corners removed.
