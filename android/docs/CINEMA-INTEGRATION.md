# OMNI Cinema ⇄ OMNI Launcher integration (contract v1)

Two independent APKs. Neither contains the other's code; they meet at a small, versioned, permission-protected contract.

```
OMNI Launcher (Java Views)            OMNI Cinema (Kotlin/Compose)             OMNI Server (omniptvd, Linux)
  Watch page  ──ContentResolver──►  CinemaProvider (read-only catalogue) ──►  Library DB ──► sources:
  tile OK     ──explicit intent──►  DeepLinkActivity ──► Movie / Player          LAN server (/media, PIN)
                                                                                 Internet Archive (public domain)
                                                                                 local / USB folders
```

## Trust: who may call whom
* Both apps are signed with the same key (the launcher's `omni.keystore`). Cinema declares a **signature-level** permission,
  `com.rasha.omni.permission.CINEMA` (`protectionLevel="signature"`); the launcher declares `uses-permission` for it.
* The provider and the deep-link activity are `exported` **and** guarded by that permission, so Android refuses any other app (no caller-spoofing
  logic of ours is involved). A different app, a browser link or `adb shell am start` without the permission is rejected by the system.
* Nothing sensitive crosses the boundary: no PINs, no tokens, no file paths on other apps' storage. The launcher gets titles, years, TMDB image
  paths (public https URLs), progress and an availability state. Playback happens inside Cinema, which holds the server PIN.
* The apps never read each other's private files.

## Stable media IDs
`omni:movie:tmdb:<id>` — a film identified by TMDB. `omni:show:tmdb:<id>` and `omni:ep:tmdb:<showId>:<season>:<episode>` likewise.
`omni:file:<sha1(kind|sourceId|path)[:16]>` — a file that has not been identified (honest: no metadata, no merge).
A film available from several sources is **one** entry with several *locations*; the best accessible location is chosen at play time
(order: local/USB, OMNI server, Internet Archive). Watch state is stored per media id, so progress follows the film, not the copy.
Identifiers are validated: `^omni:(movie|show|ep|file):[a-z0-9:]{1,64}$`; anything else is refused.

## Provider (read-only, version 1) — authority `com.rasha.omni.cinema.catalogue`
| URI | Result |
|---|---|
| `content://…/version` | one row: `contract=1`, `app_version`, `ready` (0/1: false while the first library load runs) |
| `content://…/shelves` | rows: `shelf_id`, `title`, `count`. Only shelves with real content are listed |
| `content://…/items?shelf=<id>&limit=<n≤60>&offset=<n>` | paged rows (columns below) |
| `content://…/item/<media_id>` | one row for that title |
Columns: `media_id, kind (movie|show|episode), title, year, overview, poster (https URL or null), backdrop, rating, source_label, availability, position_ms, duration_ms, watched, updated_at`.
`availability`: `PLAYABLE` (a location was reachable in the last check), `OFFLINE` (known location, source not reachable now), `ACCOUNT` (needs a sign-in),
`METADATA` (metadata only, no media location). The launcher never assumes PLAYABLE.
Shelves: `continue`, `free` (verified free providers), `device` (local/USB), `network` (OMNI server), `recent`, `discover` (only from real catalogue data).

## Deep links (explicit intents to `com.rasha.omni.cinema/.DeepLinkActivity`, permission-guarded)
`omni-cinema://home` · `omni-cinema://movie/<media_id>` · `omni-cinema://show/<media_id>` · `omni-cinema://episode/<media_id>` ·
`omni-cinema://source/<source_id>` · `omni-cinema://play/<media_id>` (plays only if verified playable; otherwise opens the detail page with the reason).
Unknown/invalid ids open the detail screen of an honest "not found"; a missing file or an expired PIN is explained there, never a blank player.
Back: Cinema opened from the launcher is a task of its own; Back from its first screen returns to the launcher exactly where it was.

## Launcher behaviour
* Watch shelves read the provider off the main thread, cache the last good result, show posters via the existing image pipeline, and never block start-up.
* If Cinema is missing, or the provider throws, the Cinema shelves are simply absent and **one** "OMNI Cinema" card says it is not installed / not available.
* The launcher plays nothing itself for these titles: it hands the id to Cinema (`play`), because Cinema owns sources, credentials and resume state.

## Server (omniptvd)
Authenticated, read-only: `/status`, `/media/<path>` (listing + Range streaming, no transcoding). Metadata comes from TMDB inside Cinema; the server
is a file source. Failure modes are explicit: unreachable, wrong PIN, locked out (10 wrong tries → 60 s).

## Known limits (current)
* Plain http on the LAN for the server (PIN-protected). TLS would need a certificate on the TV.
* Free streaming providers: the Internet Archive public-domain film collection is verified; other providers need their own terms/keys and are not added.
* Local/USB discovery depends on how this TV exposes storage (verified separately on the device).
