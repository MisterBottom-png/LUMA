---
name: luma-mvp-release-manager
description: Audit, finish, stabilize, or approve the LUMA initial MVP through verification-first status, bounded blocker repair, scope protection, and a clear pass, partial, or blocked result.
---

# LUMA MVP Release Manager

## Preconditions

Read `docs/codex/PROJECT_STATE.md`. If the cleanup baseline is not complete, stop MVP work and activate `luma-project-cleanup` in a separate run.

## Canonical sources

```text
docs/codex/mvp/LUMA_MVP_GATE.md
docs/codex/mvp/LUMA_MVP_BACKLOG.md
docs/codex/mvp/LUMA_MVP_VERIFICATION_POLICY.md
docs/codex/LUMA_PROTECTED_BEHAVIORS.md
docs/codex/PROJECT_STATE.md
```

## Verification-first rule

A backlog entry or previously completed claim is not current evidence.

Classify each relevant area as:

```text
verified
partial
broken
missing
unknown
not applicable
```

Do not rebuild working behavior merely because old documentation is uncertain.

## Two distinct task modes

### Audit mode

- Do not edit application code.
- Verify the gate using source, tests, build output, and reproducible behavior.
- Update evidence and identify the first coherent blocker batch.

### Repair mode

- Work on one named blocker batch only.
- Activate only necessary implementation skills.
- Preserve verified behavior.
- Run targeted validation and affected protected-behavior checks.
- Update the backlog and project state with evidence.
- Stop before the next unrelated batch.

## MVP boundaries

During initial MVP completion, defer unless required to restore a broken core flow:

- advanced learning memory;
- backlinks and related-thought graphs;
- automatic duplicate merging;
- people/project profiling;
- broad pattern insights;
- external calendar sync;
- backend/accounts/cloud sync;
- large Situation AI expansion.

## Final status

Use exactly one:

```text
MVP PASS
MVP PARTIAL
MVP BLOCKED
```

Report:

```text
MVP status:
Verified areas:
Blockers fixed:
Blockers remaining:
Validation:
Protected behavior checked:
Manual device checks:
Next single batch:
```
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

