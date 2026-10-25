# Third-party material

* **Geist** font (`launcher/assets/Geist.ttf`): SIL Open Font License 1.1, (c) Vercel. https://github.com/vercel/geist-font
* **Fallback wallpaper** (`launcher/assets/wallpaper-fallback.jpg`): CC0 1.0, Wikimedia Commons (from Unsplash); details in `wallpaper-fallback.json`.
* **TMDB**: film metadata and artwork come from The Movie Database API. "This product uses the TMDB API but is not endorsed or certified by TMDB."
  You must use your own TMDB key (`tmdb.token`, never committed). Artwork is fetched at run time from TMDB's image host, not shipped.
* **Channel logos and the channel catalogue are NOT included.** Logos are trademarks of their owners; `launcher/tools/fetch_logos.py` can fetch logos
  to your own machine for personal use. Add your own live-TV sources (see `server/docs/SETUP.md`, `server/server/import_m3u.py`) and only use streams you are
  allowed to watch. This project does not provide, host or relay any streams, and does not bypass geo-blocking or DRM.
* Kotlin apps use AndroidX, Jetpack Compose, tv-material, Media3 (all Apache-2.0).
* Other-platform launchers (FLauncher, LTvLauncher, tv-launcher) were used for learning only; no code was copied.
* Names such as Android, Google TV, Netflix, YouTube, PlayStation, webOS are trademarks of their owners and are used only to describe compatibility.
