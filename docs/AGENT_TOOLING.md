# Agent tooling boundaries

LUMA supports both Hermes Agent and OpenAI Codex. The similarly named directories are complementary Codex conventions, not duplicate Hermes agents.

## Shared repository layer

| Location | Purpose | Hermes | Codex |
|---|---|---:|---:|
| `AGENTS.md` | Portable project rules, safety boundaries, routing, and completion policy | Loaded as project context when Hermes starts in the repository root | Loaded as repository instructions |
| `.agents/skills/<name>/SKILL.md` | Canonical, version-controlled LUMA playbooks | Read as project documentation when the selected Hermes workflow needs one; not a Hermes profile definition | Discovered as repository skills |
| `docs/` | Product contracts, state, backlog, specifications, and evidence | Read as needed | Read as needed |

There is one repository skill tree: `.agents/skills/`. Do not create a `.codex/skills` mirror.

## Codex-only layer

| Location | Purpose |
|---|---|
| `.codex/config.toml` | Project-scoped Codex model, sandbox, approval, and concurrency defaults |
| `.codex/agents/*.toml` | Optional bounded, read-only Codex reviewer definitions |
| `.agents/skills/*/agents/openai.yaml` | Codex/OpenAI discovery metadata for the corresponding repository skill |

Hermes does not turn `.codex/agents/*.toml` into Hermes agents and does not use `.codex/config.toml` as Hermes configuration.

## Hermes-only layer

Hermes agents are durable profiles stored outside the repository under the active Hermes home directory. On this workstation the LUMA role profiles are:

```text
lumabuilder   primary implementation role
lumaprivacy   privacy and data-correctness review role
lumaui        Compose UI and accessibility review role
lumarelease   release and regression review role
```

Their profile configuration and `SOUL.md` files are machine-local. Do not copy profile directories, credentials, memories, or Hermes `.env` files into this repository.

Hermes project behavior is assembled from:

1. the selected Hermes profile;
2. repository-root `AGENTS.md`;
3. the task-specific LUMA playbook under `.agents/skills/`, read when needed;
4. the relevant product/evidence documents under `docs/`.

The Codex reviewer TOML files do not replace the Hermes reviewer profiles, and the Hermes profiles do not replace the Codex reviewer definitions. They provide equivalent role boundaries for different runtimes.

## Runtime routing

### When using Codex

1. Load `AGENTS.md`.
2. Use `luma-autopilot` from `.agents/skills/` as the controller.
3. Select the smallest relevant specialist skill.
4. Use at most one `.codex/agents/` reviewer when risk warrants independent review.

### When using Hermes

1. Start the appropriate LUMA profile in the repository root.
2. Let Hermes load `AGENTS.md` as project context.
3. Read the selected `.agents/skills/<name>/SKILL.md` as the repository playbook when the task requires it.
4. Use the matching Hermes reviewer profile or bounded delegation when independent review is warranted; do not treat `.codex/agents/` as executable Hermes configuration.

## Source-of-truth rule

```text
portable project policy       -> AGENTS.md
version-controlled playbooks  -> .agents/skills/
Codex runtime configuration   -> .codex/
Hermes runtime configuration  -> active Hermes profile outside the repository
product and evidence records  -> docs/
```

Do not duplicate one runtime's configuration into the other runtime's directory. Keep behavior aligned through shared `AGENTS.md`, canonical repository playbooks, and explicit role mapping rather than copied configuration files.
