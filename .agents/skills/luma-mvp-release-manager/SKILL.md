---
name: luma-mvp-release-manager
description: Use when auditing, stabilizing, or approving the LUMA MVP or a later release through current evidence, bounded blocker repair, scope protection, and explicit readiness status.
---

# LUMA MVP and Release Manager

## When to use

- Initial-MVP audit, repair batching, final QA, or readiness claims.
- Later release audits that need protected-behavior evidence and blocker classification.
- Reconciliation of current source/results with stale or contradictory status documents.

## Do not use

- Ordinary feature implementation or small fixes.
- To treat backlog entries, old screenshots, or previous PASS claims as current proof.
- To add feature ideas while evaluating readiness.

## Preconditions

Read `docs/codex/PROJECT_STATE.md`. For initial-MVP work, also read the relevant files under `docs/codex/mvp/` and `docs/codex/LUMA_PROTECTED_BEHAVIORS.md`. If the required privacy purge or cleanup baseline is incomplete, stop release work and route that gate separately.

## Workflow

1. Define the exact gate or release claim being evaluated.
2. Build a requirement-to-evidence table using current source, tests, build output, and reproducible behavior.
3. Classify each relevant area as `verified`, `partial`, `broken`, `missing`, `unknown`, or `not applicable`.
4. Separate automated evidence, source inspection, physical-device evidence, and manual gaps.
5. In audit mode, do not edit application code; identify the first coherent blocker batch.
6. In repair mode, work on one named blocker batch with only the needed implementation specialist, then stop.
7. Run `luma-regression-qa` for affected behavior and use the read-only release reviewer only when an independent final audit adds value.
8. Update project evidence only with current, reproducible results; preserve historical evidence in its proper record.

## Scope control

- Do not rebuild verified behavior because documentation is uncertain.
- Do not combine unrelated blockers into one implementation batch.
- During initial MVP work, defer post-MVP expansion unless required to restore a core flow.
- A partial manual evidence gap does not become PASS merely because automated checks are green.

## Verification

- Every readiness claim maps to current evidence.
- Core data safety, confirmation, fallback, scheduling, navigation, and visibility invariants are addressed where applicable.
- Pre-existing failures, regressions, blockers, and evidence gaps are distinct.
- The final status is exactly one of `MVP PASS`, `MVP PARTIAL`, `MVP BLOCKED`, `RELEASE PASS`, `RELEASE PARTIAL`, or `RELEASE BLOCKED`, matching the evaluated gate.
- The next action, if any, is one coherent batch.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.