---
name: luma-room-data-guardian
description: Protect LUMA data when changing Room entities, DAOs, migrations, repositories, export/restore, reset behavior, item visibility, persistence, or local-first storage.
---

# LUMA Room and Data Guardian

## Non-negotiable rules

- Never delete or reset user data as a shortcut.
- Never change a Room entity or database version without migration analysis.
- Never remove an old migration merely because current installs no longer start there.
- Preserve export/restore compatibility unless a versioned change is explicitly designed.
- Keep raw captures and internal processing records distinct from finalized user-facing items.
- Ensure local persistence occurs before optional AI analysis where the product flow requires capture safety.

## Required analysis

For schema or persistence changes, document:

```text
Current schema/data path
Proposed change
Migration required
Existing-version upgrade path
Export/restore effect
Reset effect
Rollback/recovery risk
Tests and manual checks
```

## Visibility checks

When changing Spaces, Life Feed, Review, processing, or item queries:

- trace entity flags and status transitions;
- trace DAO filters;
- trace repository mapping;
- trace ViewModel state;
- trace UI rendering;
- ensure one raw thought does not create duplicate visible cards;
- keep internal AI records hidden from normal user surfaces.

## Validation

Prefer migration tests and repository tests when infrastructure exists. At minimum, compile affected variants and provide explicit upgrade/restart/export/restore manual checks.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

