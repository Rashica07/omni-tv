# OMNI TV (project instructions)

A custom Android TV launcher ("OMNI TvOS", black-and-white design system) for a low-memory Vestel/MediaTek Android 11 TV at 192.168.1.242:5555.
Native Java Views, no Gradle, no libraries.

**Before any UI, motion, focus, Watch/TMDB, image or performance change, use the project skill
`omni-tv-design-engineering` (`.claude/skills/omni-tv-design-engineering/SKILL.md`) and read `docs/research/README.md`.**

## Commands
- Build: `./build.sh <jdk-home>`  (JDK 17 lives in `~/.cache/omni-jdk/jdk-17*`; not system-wide. Re-download Temurin 17 from api.adoptium.net if it is missing)
- Install + AOT compile + launch: `./deploy.sh [host:port]`
- Screens: `docs/capture.sh docs/<label>`   Benchmark: `python3 -I docs/bench.py <home|apps|watch|all> <label>`
- Regression (on-device, needs the TV): `python3 -I docs/regress.py`   Unit tests (JVM): `test/run.sh <jdk-home>`
- Reference library and boards: `docs/design/references/INDEX.md`, `python3 -I docs/design/make_boards.py`

## Rules that have bitten before
- `adb input keyevent` lags ~1 s per key: wait 3 s+, and measure only on a warm second pass.
- Never use non-uniform-radius clipped cards in long rows (Watch 79% / Apps 38% janky). Custom shapes live in custom-drawn views.
- The TMDB token is in `tmdb.token` (git-ignored). Never print/log/commit it. `omni.keystore` must stay or installs fail.
- Do not disable or uninstall system packages without a restore path (README has rollback commands).
- Third-party code: check LICENSE first; GPL-3 launchers (FLauncher, LTvLauncher, tv-launcher) are learning-only.
- Never claim a test/benchmark/visual check you did not run; label verified vs unverified research.

Installed skills (project scope): `frontend-design` (Anthropic plugin), `android-intent-security`, `anr-audit-and-fix`
(Google android/skills, Apache-2.0), `source-driven-development` (addyosmani/agent-skills, MIT), `omni-tv-design-engineering` (ours).
See `docs/research/skills.md`.
