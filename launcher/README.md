# OMNI TvOS

A lightweight, ad-free, black-and-white Android TV launcher. Native Java Views, no Gradle, no libraries. Built for a low-RAM
Vestel / MediaTek Android 11 TV (1280x720 @ 60 Hz, ~930 MB RAM), so it is deliberately small (~0.2 MB APK).

## Build, test, install
    ./build.sh <jdk-home>        # javac + d8 + aapt2 + apksigner -> omni.apk (JDK 17; not installed system-wide)
    test/run.sh <jdk-home>       # JVM unit tests for the pure logic (geometry, springs, ordering, app filter)
    ./deploy.sh [host:port]      # install, AOT-compile (avoids slow first-run JIT), launch Home
    python3 -I docs/regress.py   # on-device regression suite (drives the TV with key events, asserts on state logs)
    python3 -I docs/bench.py <home|apps|watch|all> <label>   # frame-time benchmark from gfxinfo framestats

Keep `omni.keystore`: a new key makes updates fail with a signature mismatch.

## TMDB (Watch tab)
Put a TMDB v3 API key or v4 read token in `tmdb.token` and rebuild. It is baked in at build time (`build/gen/.../Secrets.java`).
Without it the Watch tab says so. `tmdb.token` is git-ignored: do not commit or share the APK. Availability text comes from
TMDB's watch-provider data (JustWatch), shown only when the data exists, with attribution.

## Watch Next ("Continue watching")
Home shows a shelf from the platform's Watch Next provider when apps publish items. It needs the runtime permission
`READ_TV_LISTINGS` (asked once; declining is respected). OK launches only an intent inside the publisher's own package.

## Becoming the default Home
The manifest declares HOME + DEFAULT. On the target TV the stock launcher and FLauncher are disabled (`pm disable-user`), which is reversible:

    adb shell pm enable com.google.android.tvlauncher      # stock Google TV home
    adb shell pm enable me.efesser.flauncher               # FLauncher
    adb shell pm disable-user com.rasha.omni               # turn OMNI off
    adb shell dumpsys role | grep -A1 android.app.role.HOME   # who holds Home

## Design
Monochrome identity: near-black page, white focus, gray text; colour only inside media (focused app, posters, backdrops).
Specification and evidence: `docs/design/OMNI-TV-DESIGN-SPEC.md`, `docs/research/`, `docs/design/references/INDEX.md`.
Screens before/after: `docs/v1`..`docs/v4`. Tokens: `Theme.java`. Motion: springs (`Spring.java`); with system animations off every spring jumps (reduced motion).

## Structure
`ShelfStack` (one custom-drawn view: shelves of fixed-pitch tiles) drives Home, Apps and Watch; `Page` subclasses own the stage text and data.
`AppRepo` (discovery, order, pins, hidden, recents), `Tmdb`/`Images` (Watch), `WatchNext`, `Sheet` (options), `Ripple` (launch), `Ambient` (backdrop).

## Known limits
- Apps ship icons/banners, not key art; Home therefore has no art background (inventing one would be fabricated art).
- The Watch tab needs internet and a TMDB key. Everything else works offline.
- Samsung One UI and LG webOS have no usable interface evidence in the reference library; no motion was taken from any third-party platform.
- Tested on one device with scripted key events, not by hand with a physical remote. TalkBack is installed but disabled on this TV, so screen-reader
  behaviour was checked only through the accessibility node tree.
