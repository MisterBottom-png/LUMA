# LUMA project contracts

## Core documents

| Document | Purpose |
|---|---|
| [`LUMA_PRODUCT_RULES.md`](LUMA_PRODUCT_RULES.md) | Product behavior and scope. |
| [`WORKPLACE_PRIVACY_POLICY.md`](WORKPLACE_PRIVACY_POLICY.md) | Repository-wide privacy boundary. |
| [`LUMA_PROTECTED_BEHAVIORS.md`](LUMA_PROTECTED_BEHAVIORS.md) | Flows that must not regress. |
| [`PROJECT_STATE.md`](PROJECT_STATE.md) | Current evidence ledger and gates. |
| [`LUMA_SKILL_STACK.md`](LUMA_SKILL_STACK.md) | Active skills, reviewers, and source locations. |

## Task-specific areas

- [`cleanup/`](cleanup/) — cleanup policy, manifests, evidence, and report.
- [`mvp/`](mvp/) — MVP gate, backlog, and verification policy.
- [`post-mvp/`](post-mvp/) — post-MVP briefs.
- [`learning/`](learning/) — supporting decisions, patterns, and regression history. These do not override core contracts.

## Supporting references

UI, Android, AI, glass, routing, risk, and delivery references remain in this directory. Load them only when the current task touches that subject.

## Agent stack

- Portable project policy: `../../AGENTS.md`
- Canonical version-controlled playbooks: `../../.agents/skills/`
- Codex-only read-only reviewers: `../../.codex/agents/`
- Codex-only project defaults: `../../.codex/config.toml`
- Hermes runtime roles: machine-local profiles outside the repository
- Cross-tool ownership map: `../AGENT_TOOLING.md`
- Structural validator: `../../scripts/codex/validate_luma_codex_stack.py`

There is intentionally no `.codex/skills` mirror.
