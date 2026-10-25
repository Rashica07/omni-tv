# Claude Code skills: verification and decisions

Researched 2026-10-24. Sources cloned shallow into an isolated scratch dir and read; nothing was executed
from them. `claude plugin install` used only for an Anthropic-marketplace plugin.

| Candidate | Source (verified) | License | Decision | Why |
|---|---|---|---|---|
| `frontend-design` | Anthropic `claude-plugins-official` marketplace (local clone, plugin v58267944f921); SKILL.md read in full | Apache-2.0 | **Installed** (project scope, `claude plugin install frontend-design@claude-plugins-official --scope project`) | Strong critique advice (avoid template tells, plan then critique). Web-oriented, so only its *process* applies. It also flags traits my own v2 had (caps eyebrow labels, middle-dot meta strings), which fed the audit. |
| `android-intent-security` | github.com/android/skills @4ebabf2, `security/` | Apache-2.0 | **Installed** (copied to `.claude/skills/`, license kept) | OMNI exports a HOME activity and handles `onNewIntent`; this is the right audit lens. Single SKILL.md, no scripts. |
| `anr-audit-and-fix` | android/skills, `performance/` | Apache-2.0 | **Installed** | A launcher must never block the main thread (prefs, network, icon loading). Markdown only. |
| `source-driven-development` | github.com/addyosmani/agent-skills @aeac41d | MIT | **Installed** (single skill, **not** the plugin) | Framework-agnostic "detect, fetch official docs, implement, cite" loop; matches the evidence requirement. |
| `android-profiler` | android/skills `profilers/` | Apache-2.0 | **Not installed** | Bundles a prebuilt `trace_processor` binary I did not review. `gfxinfo framestats` + `perfetto` on-device cover the need (see ../perf). |
| `android-cli` skill / Android CLI | developer.android.com/tools/agents/android-cli (V) | n/a | **Not installed** | The CLI is not installed here and the page gives no checksum/signature for the download; redundant with the working `adb` flow. |
| `leanback-to-compose-tv-migration` | android/skills `tv/` | Apache-2.0 | **Read only** | Useful as a platform reference (focus memory, 10-foot hero patterns) but it steers toward a Compose migration that our measurements argue against. |
| `testing-setup`, Compose/Wear/XR/Play/etc. | android/skills | Apache-2.0 | Not installed | Compose/Hilt/Play oriented; irrelevant to a no-Gradle Java Views launcher. |
| `performance-optimization`, `frontend-ui-engineering` | agent-skills | MIT | Not installed | Web/backend (Core Web Vitals, endpoints, React). Wrong domain. |
| `doubt-driven-development` | agent-skills | MIT | Not installed | Needs fresh-context subagents; OMNI's own critique checklist is lighter. |
| Full `agent-skills` plugin | agent-skills | MIT | **Rejected** | Registers shell hooks (`session-start.sh`, sdd-cache). Not needed. |
| Full `android/skills` plugin (33 skills) | android/skills | Apache-2.0 | **Rejected** | Ships Python scrapers (`play-policy-insights`) and dozens of unused skills. |

Permanent project skill: `.claude/skills/omni-tv-design-engineering/` (see that directory).
