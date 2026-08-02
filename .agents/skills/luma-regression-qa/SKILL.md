---
name: luma-regression-qa
description: Use when validating a focused LUMA change, gathering regression evidence, checking protected behavior, or running final MVP or release QA without adding features.
---

# LUMA Regression QA

## When to use

- After meaningful code changes.
- For focused regression validation, final QA, or release evidence gathering.
- When a current claim must be distinguished from historical documentation or a pre-existing failure.

## Do not use

- To implement features, redesign the product, or fix findings unless the user also requests implementation.
- As a substitute for the relevant implementation guardian.
- To claim device behavior from unit tests or structural checks.

## Role boundary

This skill executes checks and gathers evidence. The custom release reviewer independently audits supplied evidence; it does not run or own QA. The implementing agent remains responsible for deciding whether the task is complete.

## Workflow

1. Read the request, current diff, affected tests, and relevant `docs/codex/LUMA_PROTECTED_BEHAVIORS.md` entries.
2. Trace the changed behavior and adjacent call paths before selecting checks.
3. Run the smallest relevant compile, tests, or lint first.
4. Reproduce failures and compare them with the recorded baseline; do not relabel old failures as regressions.
5. Run broader affected checks once when risk warrants them.
6. Perform source inspection or device checks for behavior automated tests cannot prove.
7. Classify concrete findings and evidence gaps separately.
8. Report exact commands, outcomes, protected behavior checked, and remaining manual work without dumping logs.

## Core path matrix

Use only affected subsets: app launch; capture-before-AI persistence; restart persistence; one finalized visible item per source; raw/internal filtering; Spaces/Life Feed; Review actions; reminder target versus offset; Gemini fallback; Search and Undo; export/restore and Reset Mode; theme/settings persistence; contrast; accessibility; touch targets.

## Severity

```text
blocker: crash, data loss, security/privacy failure, unusable core path
major: incorrect behavior or likely normal-use regression
minor: bounded defect that does not block the core flow
evidence gap: required behavior not proven; not itself a confirmed defect
```

## Verification

- Every changed behavior maps to at least one test, inspection, or explicit manual check.
- Automated, structural, source-inspection, and physical-device evidence are labeled accurately.
- Findings cite files/symbols and reproducible behavior rather than speculation.
- Pre-existing failures and new regressions are separated.
- No feature or unrelated cleanup was introduced during QA.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.