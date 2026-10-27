# Updates

Settings > Updates. Android does not let an ordinary app install itself silently (only system apps and device owners can), so OMNI does everything up to that point and asks for **one press**.

1. **Check**: once a day while the launcher is on screen (6 hours after a failure), from `api.github.com/repos/Rashica07/omni-tv/releases`. No account, no identifying data: the request carries only `OMNI-TV/<version>`. Switch off under Settings > Updates > "Check automatically". Pre-releases are included until 1.0.0 (a switch), because every release so far is one.
2. **Download**, automatically when a newer release is found, from `github.com` or GitHub's release hosts over https only, capped at 60 MB.
3. **Verify** before anything is offered: the SHA-256 stated in the release notes must match (a release without one is never installed), the file must be OMNI (`com.rasha.omni`), have a higher version code than the installed one (no downgrades), and be signed with the same certificate as the installed OMNI. A file failing any check is deleted.
4. **Install**: "Install now" hands the file to Android's installer, which asks you to confirm ("update to this existing application, your data will not be lost"). The first time, Android also asks you to allow OMNI to "install unknown apps" (a one-time switch on its own screen).

**Personal builds** (an APK that bundles its own channel list and logos) are told about updates but never replaced by the public release, which would drop that list.

**Not verified on a real TV**: how the system behaves when the launcher that is also the Home app is replaced (it should return to OMNI; on the emulator it did not matter). If it lands on the stock launcher, press Home and choose OMNI.

Developer test: with the developer flag (`setprop log.tag.OmniDebugOpen DEBUG`) the feed can be pointed at a local server (`--es open updates --es feed http://10.0.2.2:PORT/releases.json`); this is ignored otherwise.
