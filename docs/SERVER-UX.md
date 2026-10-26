# OMNI Server: how it looks, feels and works

The Server screen is OMNI's window onto the home server (`omniptvd`). It follows the same rules as Cinema and Weather (shared top bar, 64 dp / 30 dp safe area, charcoal `#1A1A1D`, ivory `#E8DCC8`, crimson for failure and the main action, gold for warnings and highlights) and adds only restrained status colour: green for healthy, gold for "look at this", crimson for down.

## What the backend really provides (and so what is shown)
| Source | Data | Shown as |
|---|---|---|
| `GET /health` (open) | service up, channel probe results, guide data | Services |
| `GET /status` (PIN) | uptime, load average, CPU count, memory, temperature, disks (state, media), network rates and link speed, battery/mains | Vitals rings, charts, Network |
| `GET /api/v1/library` (PIN) | folders and whether each is reachable, film counts (present, missing, without a poster match), last scan, scan state, whether TMDB matching is on | Library, Services |
| `GET /api/v1/progress` (PIN) | watch positions with the device that saved them and when | Devices |
| `POST /api/v1/library/scan` (PIN) | starts a library scan | **Scan library now** (the only operation) |

**Not shown, because the server cannot do or report it:** starting/stopping/restarting services (no such endpoint), a list of "connected" clients (the server does not track connections; a device appears in Devices once it has saved watch progress, labelled "seen", never "connected"), per-process figures, weather-style alerts beyond the warnings below.

## Pages
- **Overview**: the verdict first (Healthy / Needs attention / Unreachable / Not signed in), its reasons in plain words, address, last reading and uptime; then four ring gauges (CPU load, memory, storage, temperature), network up/down, services, recent changes. OK refreshes.
- **Resources**: four charts built only from readings this TV actually took (every 5 s while the screen is open): CPU load against the core count, memory, network down and up, temperature with the 85 degree warning line. With fewer than six readings there is no chart, only the current value, and the screen says how many readings it has.
- **Library**: film count, what is missing or unmatched, last scan and its problems, the folders and whether each is reachable, storage, whether posters are on; **Scan library now**.
- **Devices**: devices that have synced progress, with films synced, in progress and watched, and "Active just now" (saved within 5 minutes) or "Last seen ...".
- **Server settings** (icon on the right): the address, how often it polls, and *Forget this server*, which asks first with Cancel focused.

## Reliability and honesty
Status polls every 5 s and the library every 15 s (5 s while it scans), only while the screen is open. Each part fails on its own: a server without the library API is still a working server and says "older, no media library"; an unreachable server dims the last values and says "not current"; a rejected PIN is "Not signed in", not "unreachable". A value the server did not report is "n/a", never 0. The PIN lives on this TV only and is never displayed or logged.
