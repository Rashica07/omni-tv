# Liquid Glass: what it is, what this TV can do, what OMNI does instead

Researched 2026-10-24. Sources: Apple newsroom (tvOS 26 announcement, V: "reflects and refracts its surroundings in real time"), the
`kwlck/liquid-glass-web` README (V, MIT), Kyant0's `AndroidLiquidGlass` README and package listings (README fetched; licence not stated
there, an aggregator lists Apache-2.0: **unverified**), `gentpan/liquidglass`, `Z1Code/glass-refraction` (search listings only, S).
No code or assets from any of them are used.

## The layers (from the open-source implementations)
| Layer | How the web component does it | How the Android library does it |
|---|---|---|
| Backdrop blur | CSS `backdrop-filter` (`blur` 0-40 px) | `RenderEffect` blur on a captured backdrop layer |
| Refraction at the edges | SVG displacement map (`refraction` 0-100) | AGSL `RuntimeShader` lens |
| Specular highlight | `light` 0-100, follows the pointer | shader highlight |
| Tint and rim | `--lg-tint`, rim | tint, vibrancy |
| Fallbacks | tint + rim without blur on browsers lacking `backdrop-filter`; reduced-transparency gets an opaque surface | gracefully degrades below API 31 |

## Why OMNI cannot use them (measured on the target)
The TV runs **Android 11 (SDK 30)**. `RenderEffect` needs API 31 and AGSL `RuntimeShader` needs API 33, so neither exists here. Software
blur of a full-screen layer every frame is also out of budget on a 4x ARMv7 SoC that already needs care at 1280x720 (see the Apps frame-time
history in `docs/perf`). The web components are not an option either: OMNI is native Views and embedding a WebView would cost far more
memory than the whole launcher.

## What OMNI implements instead (`Glass.java`, `Ambient.java`)
Each layer is rebuilt from per-frame-free pieces:
1. **Pre-blurred backdrop:** when a Watch backdrop is set, `Ambient` also keeps a 40x24 copy made by two bilinear down-scales. Glass surfaces
   paint it, stretched and aligned to the screen, through one `BitmapShader` under a rounded rect. Colour from the artwork bleeds through softly;
   cost is one texture draw. With no backdrop (Home, Apps) the base is a quiet near-black.
2. **Veil and tint:** a dark veil keeps text legible over bright art; a faint white tint lifts the surface off the page.
3. **Sheen:** a vertical gradient, brightest at the top edge, stands in for the specular highlight.
4. **Rim:** a 1 dp stroke, brightest at the top, stands in for refraction at the edge.
5. **Shadow:** three stacked, fading rounded rects (no blur).
No clip paths, no `saveLayer`, no per-frame allocation (gradients are cached by surface height).
Used for the navigation capsule, the clock, the editing/confirmation banner, the options sheet and the Apps category cards. Content tiles stay
flat on purpose: glass is for system surfaces, content keeps its own colour.

## Limits
- It is not refractive: edges do not bend what is behind them. It reads as frosted glass, not liquid.
- Over Home and Apps there is nothing behind the glass to blur, so those surfaces read as lightly lit panels.
- If a later TV runs Android 12/13+, `RenderEffect` could replace layer 1 behind the same `Glass.draw` call.
