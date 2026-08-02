# LUMA Codex-side agent and skill stack

This document describes the repository-discovered Codex side of the cross-tool setup. Hermes uses external profiles plus `AGENTS.md` and reads these canonical playbooks as project documents when needed. See `../AGENT_TOOLING.md` for the complete boundary.

## Source of truth

```text
AGENTS.md                    permanent repository laws and routing
.agents/skills/              canonical version-controlled LUMA playbooks
.codex/config.toml           project-scoped Codex defaults
.codex/agents/               optional read-only Codex reviewers
docs/codex/                  product, safety, cleanup, and verification contracts
scripts/codex/               structural, privacy, and cleanup validation
```

Do not create a `.codex/skills` mirror. Codex discovers repository skills from `.agents/skills`, and duplicate skill names are not merged. Hermes profiles are separate machine-local runtime agents and are not represented by `.codex/agents/`.

## Workflow

```text
User request
  -> AGENTS.md
  -> luma-autopilot
  -> one primary specialist
  -> one supporting specialist only when required
  -> bounded reviewer when risk warrants it
  -> luma-regression-qa for meaningful final validation
```

The active agent owns decisions and execution. Skills are operational playbooks. Custom agents are independent read-only reviewers. The user remains the approval authority for consequential choices.

## Active skills

| Skill | Responsibility |
|---|---|
| `luma-autopilot` | Route requests and enforce project gates. |
| `luma-android-developer` | Kotlin, Gradle, architecture, navigation, and app behavior. |
| `luma-compose-ui` | Compose, Material 3, layout, theme, state, and accessibility. |
| `luma-glass-haze-guardian` | Glass roles, Haze, blur, contrast, and performance. |
| `luma-room-data-guardian` | Room, repositories, migrations, persistence, export, restore, and reset. |
| `luma-ai-reminder-guardian` | Gemini, structured AI output, dates, reminders, notifications, and timezones. |
| `luma-project-cleanup` | Evidence-backed, behavior-preserving repository cleanup. |
| `luma-mvp-release-manager` | MVP and later-release evidence, readiness, and bounded blocker repair. |
| `luma-regression-qa` | Focused validation, protected behavior, and regression evidence. |

## Active reviewers

| Reviewer | Use for |
|---|---|
| `luma_code_data_reviewer` | Correctness, lifecycle, concurrency, Room, persistence, and data-loss risk. |
| `luma_ui_accessibility_reviewer` | Compose behavior, themes, layout, navigation, and accessibility. |
| `luma_release_regression_reviewer` | MVP/release evidence, protected behaviors, and unsupported completion claims. |

Reviewers are read-only. Do not use them for ordinary small changes or let multiple agents edit the same files.

`luma-regression-qa` executes validation and gathers evidence. `luma_release_regression_reviewer` audits the supplied evidence and completion claim. Neither reviewer nor skill replaces the implementing agent's final judgment.

Every skill carries optional discovery metadata at `agents/openai.yaml`. Bulky or trigger-specific procedures belong under the skill's `references/` directory and are loaded only when needed.

## Validation

Run:

```text
python scripts/codex/validate_luma_codex_stack.py
python scripts/codex/check_workplace_privacy.py --strict
```

The structural validator defines the expected active skill and reviewer names.
