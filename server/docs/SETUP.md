# OMNIPTV setup and recovery

## 1. Server (the MacBook) 
Read `deploy/install.sh` first; it needs `sudo` and touches only the paths it lists.

    cd ~/omniptv && sudo ./deploy/install.sh
    curl http://<laptop-ip>:8765/health          # expect {"service":"omniptvd","ok":true,...}
    systemctl status omniptvd                    # running, enabled
    journalctl -u omniptvd -n 50                 # logs

Lid closed keeps running only after `sudo systemctl restart systemd-logind` (this logs out the graphical session; do it from a console or after saving work).
Verify afterwards with `systemd-inhibit --list` or by closing the lid for a minute and checking `/health`; **not verified yet on this machine**.

Please also: use **Ethernet** (the port is present, no cable today), give the laptop a **DHCP reservation** in the router so its address never changes, keep it on AC power, leave air flow around it.
Updating the catalogue: edit `/var/lib/omniptv/catalog.json` (same fields as `catalog/albanian-channels.json`), then `curl -X POST -H "Authorization: Bearer $(sudo cat /etc/omniptv/admin.token)" http://localhost:8765/probe`.
EPG: set `"epg_source": "<https XMLTV url>"` in `/etc/omniptv/config.json` only when you have a source you are allowed to use, then restart the service.
Rollback: `sudo ./deploy/uninstall.sh` (add `--purge` to delete data). Backup: copy `/etc/omniptv` and `/var/lib/omniptv`.
Diagnose a dead channel: `/catalog.json` shows each stream's last probe (`plays`, `blocked-403`, `fails`) and when. A channel that plays in a browser but not here is usually a referrer/geo/app restriction: OMNIPTV does not work around those.

## 2. TV
Install the new OMNI build (deploy only when the TV is free). Open **TV** in the top bar for live channels. For the server: top bar, network icon, **Set server address**, enter the laptop's address (e.g. `192.168.1.146`). The dot on the network icon appears after a real check: green = the server answered like OMNIPTV, amber = it did not. No dot = not checked. Without a server the TV uses its built-in channel list.

## 3. Licences and attribution
| Item | Use | Licence |
|---|---|---|
| Open-Meteo API | weather (shown with attribution in the weather sheet) | data CC BY 4.0; free for non-commercial use, no key |
| iptv-org channel/stream database | consulted for discovery only; entries re-probed and re-tiered; not redistributed as a playlist | **not confirmed** by me (check the repo's LICENSE before publishing the catalogue) |
| omniptvd, OMNI | own code | private |
| Geist font | UI | SIL OFL 1.1 (already in the project) |
No third-party code was copied for OMNIPTV. No API keys are in the APK or this repository; the admin token exists only on the server (mode 0600).

## 4. Limitations (honest list)
- Streams were tested from Italy only, with this machine's HTTP client, **not** on the TV's MediaPlayer. HLS playback in the TV's player is unverified until tested.
- No Albanian XMLTV source was verified: no programme guide yet; the UI says so.
- 14 channels enabled by default. RTSH, TV Klan, Top Channel, RTV Dukagjini and others are not available through free sources I could verify; five channels refuse non-app clients (403).
- "Authorized" is not established for any channel: tier B means only that the stream host belongs to the broadcaster or a platform broadcasters use.
- Channel cards are generated (name + colour), not logos.
- The Google account cannot be read by an ordinary app: the account zone is a shortcut to account settings.
- Weather needs you to pick a city (no location permission is requested).
- Server install and the lid/power settings have not been applied to this laptop yet; thermal behaviour under a closed lid is untested.
- Screenshots and TV measurements for this round are pending the TV being free.
