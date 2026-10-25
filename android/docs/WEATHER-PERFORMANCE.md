# Weather app performance: diagnosis, fix and results

Measured on the target TV (Android 11, armeabi-v7a, 907 MB RAM) with `dumpsys gfxinfo` (frame times), `am start -W` (cold start) and `dumpsys meminfo`.
Scenario: open, then Down/Up (panel change) x3, Right/Left x3 along the hours, Down/Up x3, 2 s apart. One warm-up pass before the measured pass.

## Root cause (launcher's Java weather screen)
Per-frame `onDraw` was **106 ms** on the UI thread (GPU only 14 ms), so the app ran at 5 fps. Per-section timers showed the 7-day panel costing 98 ms of it.
The cost was **`TextPaint.setFontVariationSettings()` called twice per detail tile, six tiles, every frame**: each call rebuilds a `Typeface`, so every frame
re-rasterised all those glyphs. (My first hypothesis, `SimpleDateFormat` per frame, was real but small: fixing it alone moved 98 ms to 76 ms.)
Also fixed: all text and shaders precomputed per data update, off-screen panels not drawn, crescent `Path.op` cached, forecast JSON parsed on a worker thread.

## Results
| | frames over budget | median | p95 | p99 | UI-thread draw | memory (PSS) | cold start |
|---|---|---|---|---|---|---|---|
| Java, before | **100%** | **200 ms** | 200 ms | 200 ms | **106 ms** | n/a | n/a |
| Java, after | 0.9% | 8 ms | 10 ms | 15 ms | 3.0 ms | n/a | n/a |
| **Kotlin + Compose (new app)** | **1.7%** | **7 ms** | **9 ms** | 17 ms | n/a | **29 MB** | 340 ms typical, 566-740 ms first launch |

## Compose design choices that matter on this TV
- One `Font` entry per weight, created once (the same trap exists in Compose).
- Presentation strings are built once in the ViewModel (`UiMapper`) on `Dispatchers.Default`; composables read ready-made data.
- Rain/snow and the sun's rotation read their clock only in the draw phase (no recomposition per frame).
- Panel change is one animated offset (`graphicsLayer`), not a scroll container (a scroll container fought the focus-driven scroll and left the panel half in view).
- Opens on the stored forecast ("Updated 16 min ago"); a request is made only if the stored copy is older than 30 minutes, at most once a minute, never polled.
  A failed refresh keeps the last data and says it is offline. All network and disk I/O is on the IO dispatcher.
- Glass is fill + tint + rim: no blur, no shadow, no blend modes (a blend mode on the launcher's bloom once cost 83 ms per frame).
- Reduced motion: the platform animator scale of 0 disables particles, the sun and the panel transition.

## Not measured here
Offline behaviour was exercised by design (cached open, stale label) but not by disconnecting the TV's network; no automated frame-time test runs in CI.
