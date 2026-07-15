---
name: luma-docs-package-maintainer
description: Use for maintaining LUMA Codex package docs, AGENTS.md proposals, manifests, prompts, install docs, skill folders, reviewer agent files, and cleanup without changing app behavior.
---

# LUMA Docs Package Maintainer

Use this skill for package/docs/agent-stack maintenance.

## Scope

```text
- README_START_HERE.md
- INSTALLATION.md
- FIRST_PROMPT_TO_CODEX.md
- PACKAGE_MANIFEST.md
- manifest.txt
- AGENTS.md proposals
- docs/codex/*.md
- .agents/skills/*
- .codex/skills/* compatibility mirror
- .codex/agents/*.toml
- prompts/*.md
```

## Rules

- Do not change app feature behavior.
- Do not rewrite permanent rules without explicit user approval.
- Keep docs practical and short enough to be used.
- Remove duplication only when it is truly redundant.
- Preserve useful project-specific knowledge.
- Update manifest when files change.

## Package quality checklist

```text
- Clear purpose
- Clear install steps
- Current skill locations
- Compatibility mirrors if needed
- Routing map
- Protected done items
- Regression checklist
- Learning files
- Reviewer agents are read-only by default
- External references treated as references only
```
