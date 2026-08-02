# LUMA

LUMA is a calm, private, local-first Android life inbox.

## Repository map

```text
app/                 Android application and tests
macrobenchmark/      Android performance test module
app/schemas/         exported Room schemas
.agents/skills/      canonical version-controlled LUMA playbooks
.codex/agents/       optional read-only Codex reviewers (not Hermes profiles)
.codex/config.toml   project-scoped Codex defaults
docs/codex/          product, safety, cleanup, and verification contracts
docs/calendar/       calendar specifications and progress
docs/planning/       bounded UI/UX implementation plans
prompts/              operational cleanup and MVP prompts
scripts/codex/       privacy, cleanup, and stack validation tools
tests/codex/         tests for repository tooling
docs/archive/        historical package and cleanup records
```

## Start here

- Agent instructions: [`AGENTS.md`](AGENTS.md)
- Hermes/Codex boundary: [`docs/AGENT_TOOLING.md`](docs/AGENT_TOOLING.md)
- Documentation index: [`docs/README.md`](docs/README.md)
- Agent-stack map: [`docs/codex/LUMA_SKILL_STACK.md`](docs/codex/LUMA_SKILL_STACK.md)
- Current verified state: [`docs/codex/PROJECT_STATE.md`](docs/codex/PROJECT_STATE.md)
- Product rules: [`docs/codex/LUMA_PRODUCT_RULES.md`](docs/codex/LUMA_PRODUCT_RULES.md)
- Protected behavior: [`docs/codex/LUMA_PROTECTED_BEHAVIORS.md`](docs/codex/LUMA_PROTECTED_BEHAVIORS.md)

## Common validation

```text
python scripts/codex/validate_luma_codex_stack.py
python scripts/codex/check_workplace_privacy.py --strict
./gradlew :app:test :app:assembleDebug :app:lintDebug
```

Use the smallest relevant Gradle task during iteration. Consult `AGENTS.md` before changing application behavior or project policy.
