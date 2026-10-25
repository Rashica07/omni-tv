# OMNIPTV: research, channel findings and architecture decision

Checked 2026-10-24 from an Italian residential connection (Wi-Fi). **V** = verified by me here, **S** = from search results/documentation only (unverified), **G** = gap.

## 1. The server hardware (V)
`MacBookPro9,2` (mid-2012, 13-inch, **non-Retina**; the brief said Retina: the machine in front of me is not), i5-3210M 2c/4t 2.5 GHz (turbo 3.1), 7.7 GB RAM, 832 GB free,
Linux Mint 22.3, kernel 6.14. Gigabit Ethernet present but **no cable now**: it is on Wi-Fi. Package temperature already 68 C with ordinary desktop use
(high 87, crit 105). Battery 72% of design capacity after 624 cycles: it is not a dependable UPS. Lid closed currently = suspend (needs the logind override).
No DVB tuner. No ffmpeg, nginx or Docker installed. Realistic limits: it can forward or remux many streams; software transcoding of even one 1080p stream would
use most of the CPU and heat it, so transcoding is excluded by design.

## 2. Approaches compared
| Approach | Works here? | Verdict |
|---|---|---|
| **Direct playback of broadcaster HLS on the TV** (ExoPlayer/MediaPlayer) | Yes (V: 18 of 37 listed URLs deliver real media segments from Italy) | **Core.** Zero server load; works when the server is down |
| **Tiny catalogue + health + EPG-cache service** (`omniptvd`, stdlib Python) | Yes (V: 25 MB RAM, ~4% of one core while probing, idle otherwise) | **Chosen server.** Does what the TV cannot: periodic real probing, one place to update the catalogue, EPG cache |
| Threadfin (xTeVe fork, Go; HDHomeRun emulation, M3U+XMLTV merge) | Would run (S) | Not needed: it solves "feed an app that only speaks HDHomeRun". OMNIPTV speaks M3U/JSON itself. Revisit if Jellyfin/Kodi clients are wanted |
| Tvheadend | Would run, heavier (S) | Needed only for DVB/SAT>IP tuners, DVR or transcoding. No tuner here |
| Jellyfin Live TV | Runs but is a full .NET media server (S) | Overkill for ~15 channels; adds idle RAM and a login surface |
| ErsatzTV | Builds channels from a media library (S) | Wrong job: it schedules your own files, not live broadcasters |
| USB DVB-T/T2, SAT>IP | Needs hardware (G: none present) | Legal and good for **Italian** terrestrial TV; no Albanian terrestrial signal is receivable in Italy over the air. Optional later |
| Public M3U lists (iptv-org) | Used as an **index only** (V) | Useful for discovery; every URL re-checked and tiered, never trusted wholesale |
| Official catch-up/VOD | G | Not researched to a verified endpoint: see gaps |
| Containers | Not needed | One stdlib process under systemd is simpler, smaller and easier to roll back than Docker |
| Remote access outside the LAN | Not built | Exposing it publicly adds risk for no need. If wanted later: WireGuard to the laptop, never a forwarded port |

## 3. Channel findings (V, see `catalog/albanian-channels.json`)
Source index: iptv-org channel+stream database (licence **not confirmed** by me; treated as an index of public links only, no list redistributed as-is).
37 stream URLs for 33 Albanian/Kosovar channels were probed (manifest, variant, one real media segment):
- **Plays (18 URLs)**, 14 on hosts consistent with the broadcaster (tier B, enabled by default): AlbKanale Music, Kohavision, News 24, RTK 3, RTV21, Syri, Tropoja TV, TV Arta, TV Dielli, TV Opoja, Vizion Plus, Zjarr TV. A few more play from tier C hosts (TV Johaniter, ntv, Fieri TV, Panorama TV): **disabled by default**: bare IPs, duckdns, protokolldns.xyz or valashop.online hosts are not the broadcaster's.
- **403 Forbidden** (Alpo TV, CNA, Ora News, Cufo, TV 7 Albania): the servers refuse this client: referrer, app or geo protection. **Not bypassed.** Listed as blocked; an official app or site may be the only route.
- **Timeout**: all five RTSH channels. The listed URLs point to a bare IP, not an RTSH domain, so they are not accepted as official. RTSH's own live page is `tv.rtsh.al` (site listed by iptv-org and Wikipedia); whether it exposes a stable public HLS URL without an app or geo-lock is **unverified: manual check needed**.
- **404/other**: TV Arberia (3 channels), T7, TV Syri, A2 CNN, Report TV.
- **"Authorized" is not proven for any channel.** Tier B means "the stream host belongs to the broadcaster or a platform broadcasters use", nothing more. Public reachability is not permission to redistribute; OMNIPTV links to the broadcaster's own URL and never re-hosts.
- **Not found (G):** TV Klan, Top Channel, TV Klan Kosova, RTV Dukagjini, Klan Kosova, Euronews Albania, Digitalb/Tring/Supersport channels. They are commercial or paid; no free official public stream was verified. They are listed as unavailable through the discovered free sources.
- Geo: everything above was tested from Italy only. A stream that plays here may be region-locked elsewhere and vice versa.

## 4. EPG (G)
No legitimate, maintained Albanian XMLTV source was verified. iptv-org's `epg` project builds guides by scraping sites (terms vary per site) and I did not validate any output for these channels. Consequence: **the UI shows no programme data** until a source is configured via `epg_source`; the guide screen says so and nothing is fabricated. The server serves `/epg.xml` only if a real file exists.

## 5. Architecture decision
```
 TV (OMNIPTV client) ──── direct HLS ────► broadcaster / CDN        (always works if the internet does)
        │  GET /catalog.json  /health  /epg.xml   (LAN only)
        ▼
 MacBook (omniptvd, systemd) ── probes streams every 30 min, caches EPG ──► broadcasters
```
- Playback never passes through the laptop: no transcoding, no relaying, nearly no load. If the laptop is off, the TV uses its **bundled/cached catalogue** and says the server is unreachable.
- Server: stdlib Python, `Restart=always`, `MemoryMax=150M`, `CPUQuota=60%`, sandboxed (`ProtectSystem=strict`, own user, no new privileges), config in `/etc/omniptv`, state in `/var/lib/omniptv`, admin token (0600) required for the only write endpoint, LAN-only firewall rule, no internet-facing port, no auto-discovery broadcast beyond optional mDNS on the LAN.
- Recovery: `install.sh`/`uninstall.sh`; state survives reinstall; a failed EPG refresh keeps the last good file; catalogue is untrusted input and is re-validated at load (schemes http/https only, bounded fields).
- Ethernet is strongly recommended over Wi-Fi for a server; the port exists. Heat: sustained load is tiny, but keep the machine ventilated; closed-lid operation reduces cooling on this chassis: leaving it open and the screen off is safer.

## 6. Risks and limits (honest)
- Battery wear means a power cut is a hard stop. A sticker on the lid does not stop a closed lid cutting airflow.
- Tier B hosts can change without notice; the health probe will show it.
- Wi-Fi only today: expect occasional stalls compared with Ethernet.
- Stream licences were not examined channel by channel.
