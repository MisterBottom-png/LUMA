---
name: luma-room-data-guardian
description: Use when changing LUMA Room entities, DAOs, migrations, repositories, persistence, item visibility, export/restore, reset behavior, or local-first data safety.
---

# LUMA Room and Data Guardian

## When to use

- Room entities, DAOs, database versions, migrations, converters, or repository persistence.
- Export, restore, reset, archive, undo, destructive actions, or data reconciliation.
- Queries or mappings that control raw/internal versus finalized item visibility.

## Do not use

- Pure UI presentation with no data-path change.
- Schema or destructive work without explicit authorization and a migration/recovery design.
- AI parsing or reminder scheduling except as a supporting specialist for persistence boundaries.

## Non-negotiable rules

- Never delete or reset user data as a shortcut.
- Never change an entity or database version without migration analysis.
- Never remove an old migration because current development installs no longer start there.
- Preserve local-first behavior and export/restore compatibility unless a versioned change is explicitly approved.
- Keep raw captures and internal processing records distinct from finalized user-facing items.
- Persist captured source material before optional AI work where capture safety requires it.

## Workflow

1. Trace the current schema or data path from write through DAO, repository, state holder, and rendered result.
2. Record: current path, proposed change, migration requirement, every supported upgrade path, export/restore impact, reset impact, rollback risk, and verification.
3. Inspect existing schemas, migrations, fixtures, transaction boundaries, and sibling queries before editing.
4. Add a focused repository, migration, or serialization test that fails for the missing behavior.
5. Implement the smallest transactionally safe patch.
6. Verify visibility and exactly-once behavior across source, finalized, archived, restored, and restarted states as relevant.
7. Run focused tests, compile instrumentation tests when affected, then run broader data checks once.
8. Review the final diff for accidental schema/version/format changes.

## Visibility trace

For Spaces, Life Feed, Review, Search, capture processing, or item queries, trace entity flags, DAO filters, repository mapping, ViewModel state, and UI rendering. One source thought must not create duplicate visible cards, and internal records must remain hidden from normal user surfaces.

## Verification

- Every supported old database version has a defined upgrade path.
- Migration and transaction tests pass where infrastructure exists.
- Export/restore compatibility and reset behavior are unchanged or explicitly versioned and tested.
- Restart, rollback, duplicate, and failure paths are covered where affected.
- No destructive action occurs without the required user confirmation.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Do not silently alter protected values in real databases, exports, or backups. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.