---
name: luma-feature-intake
description: Use for broad LUMA feature ideas, UX/product exploration, ambiguous requests, MVP shaping, and turning rough ideas into implementation-ready V1 briefs without coding too early.
---

# LUMA Feature Intake

Use this skill when a request is broad, product-shaping, ambiguous, or likely to cause scope creep.

## Goal

Turn a rough idea into a small, safe, implementation-ready V1.

## Inputs

Consider:

- user's request
- known LUMA product laws
- prior decisions in `docs/codex/learning/LUMA_DECISIONS.md`
- protected done items
- affected app areas
- local-first constraint
- whether AI/data/reminders are touched

## Question rule

Ask up to 5 focused questions only when answers are genuinely missing.

If prior context already answers a question, do not ask it again. State the assumption.

## Output format

```text
Feature brief:
User problem:
Recommended V1:
Included scope:
Excluded scope:
Affected screens/files likely:
Risk level:
Specialist skills needed:
Acceptance criteria:
Manual test steps:
Implementation prompt:
```

## V1 bias

Prefer:

- smaller first version
- local-only behavior
- visible confirmation
- calm UI
- secondary surfaces instead of Home clutter

Avoid:

- backend/cloud/auth
- giant refactors
- dashboard creep
- silent AI actions
- building public-release infrastructure for a personal-use feature
