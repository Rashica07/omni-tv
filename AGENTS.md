# Instructions for an AI coding agent (ChatGPT, Claude, Codex, ...)

You are helping a person install OMNI TV on **their own** Android TV. Follow `INSTALL.md`. Rules, in priority order:

1. **Never disable, uninstall or modify the TV maker's system packages** unless the person asks for that exact thing and you have written the undo command down first.
2. **Ask before** anything outward-facing or hard to reverse: running `sudo`, changing system settings (`settings put`, `pm disable-user`, CEC), opening ports, installing a service.
3. **Secrets**: a TMDB key goes only in `tmdb.token` / `/etc/omniptv/tmdb.token`; never print, log, commit, screenshot or paste it, and never share an APK built with it. Never commit `*.keystore` or PINs.
4. **A TV is shared.** Before sending key events or opening apps, check nobody is watching: `TV=<ip>:5555 python3 -I launcher/docs/tvguard.py` (exit 0 = free). Restore volume/settings you change.
5. **No streams are provided.** Do not add or invent channel lists, EPG or stream URLs; only help import a playlist the person is entitled to use. Do not bypass geo-blocks or DRM.
6. **Report honestly.** Say what you ran and what you saw; label anything unverified. Screenshots from `screencap` are blank while a hardware video plane (HDMI/tuner) is on screen.

## Steps
1. Check tools: `adb version`, `java -version` (17), Android SDK `build-tools/35.0.0` and `platforms/android-35`. If missing, tell the person what to install; do not guess paths.
2. Pair with the TV (`INSTALL.md` section 1). `adb devices` must show `device`.
3. Run `launcher/test/run.sh <jdk17>` first (expects `0 failed`), then `cd android && ./gradlew :app:assembleRelease` and `TV=<ip>:5555 ../launcher/deploy.sh` (one APK has everything).
4. Optional apps/server: sections 4-5 of `INSTALL.md`. After installing the server, test with `curl http://<server>:8765/api/v1/health`.
5. Verify on the TV: Home opens, INFO opens the Control Center, Back returns. Report what you saw and what you could not check (HDMI picture, sound).
6. If the TV is not a Vestel/MediaTek: expect differences (HDMI preview, key codes, memory). Run `launcher/docs/hdmi_cec_test.sh` only with the person's OK (it temporarily turns HDMI-CEC off and restores it).

## Conventions for changing code
Read `launcher/CLAUDE.md` first (performance rules learned the hard way: no clipped non-uniform cards in long rows, no per-frame font changes, size UI in dp from 960 dp wide).
Add a unit test for pure logic; run `launcher/test/run.sh`, `server/server/tests/*.py` (`python3 -I`), `./gradlew :core:library:test` in `android/`.
