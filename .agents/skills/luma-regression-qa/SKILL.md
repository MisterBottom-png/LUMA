---
name: luma-regression-qa
description: Validate focused LUMA changes, review diffs for real regressions, protect completed behavior, and run final MVP or release QA without adding features.
---

# LUMA Regression QA

## Scope

QA verifies. It does not use review as an excuse to redesign or expand the product.

## Method

1. Read the request and final diff.
2. Trace the changed behavior and adjacent call sites.
3. Run the smallest relevant compile/tests/lint first.
4. Run broader checks once if risk warrants them.
5. Compare failures with the recorded baseline.
6. Check affected entries in `LUMA_PROTECTED_BEHAVIORS.md`.
7. Report concrete findings before style preferences.

## Core path checks

Use relevant subsets:

```text
app launch
capture saved before optional AI work
persistence after restart
one finalized visible item per capture
raw/internal records hidden
Spaces and Life Feed filtering
Review wording and actionable states
reminder target versus notification offset
Gemini failure fallback
search and undo
export/restore and Reset Mode
theme and settings persistence
light/dark readability
accessibility and touch targets
```

## Review severity

```text
blocker: crash, data loss, security/privacy failure, unusable core path
major: incorrect behavior or likely regression in normal use
minor: bounded issue that does not block MVP use
note: evidence gap or manual check, not a claimed defect
```

Do not report speculative issues as facts. Include file/symbol evidence and reproduction when possible.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

