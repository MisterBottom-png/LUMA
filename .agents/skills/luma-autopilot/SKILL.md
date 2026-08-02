---
name: luma-autopilot
description: Use when a request concerns LUMA and needs routing, scope control, approval decisions, specialist selection, or coordinated implementation and verification.
---

# LUMA Autopilot

## Overview

Own the task. Repository instructions define boundaries; they do not replace engineering judgment. Choose the smallest workflow that can finish the user's request, make ordinary reversible decisions without interruption, and stop only at a real approval boundary.

## When to use

- Any request beginning with `LUMA:`.
- Any request clearly concerning this application, its repository, release evidence, or agent stack.
- Broad requests that cross implementation, safety, and verification concerns.

## Do not use

- Unrelated work outside this repository.
- As a reason to load every specialist or reviewer.
- To turn a focused request into cleanup, redesign, feature expansion, or release work.

## Routing

Choose one primary specialist. Add one supporting specialist only when a concrete boundary is crossed.

| Work | Primary skill |
|---|---|
| Kotlin, Gradle, architecture, navigation, lifecycle | `luma-android-developer` |
| Compose, Material, layout, theme, accessibility | `luma-compose-ui` |
| Glass roles, Haze, blur, translucent materials | `luma-glass-haze-guardian` |
| Room, DAO, migration, persistence, export/restore | `luma-room-data-guardian` |
| Gemini, AI output, dates, reminders, notifications | `luma-ai-reminder-guardian` |
| Evidence-backed repository cleanup | `luma-project-cleanup` |
| MVP or release readiness and blocker management | `luma-mvp-release-manager` |
| Regression validation and final QA | `luma-regression-qa` |

## Workflow

1. Read `AGENTS.md`, `docs/codex/PROJECT_STATE.md`, and only the task-specific contract needed for the request.
2. Classify the request as review-only, focused fix, implementation, high-risk change, cleanup, or release work.
3. Establish current behavior or evidence before treating a plan, backlog entry, screenshot, or prior claim as fact.
4. Open and follow the primary specialist. Add one supporting specialist only for an identified cross-boundary risk.
5. For broad work, define `Goal`, `Included`, `Excluded`, `Affected areas`, `Risks`, `Done when`, and `Verification`; then execute one coherent milestone.
6. Make the smallest complete change. Preserve unrelated dirty work and protected behavior.
7. Use `luma-regression-qa` after meaningful code changes or when final evidence is requested.
8. Review the final diff and report what actually ran, what passed, and what remains manual.

## Decision and approval boundaries

- Proceed without questions for clear, reversible, low-risk decisions.
- Ask when missing product intent materially changes behavior or when an action is destructive, irreversible, schema-changing, security-sensitive, externally publishing, or otherwise requires explicit approval.
- Never ask the user to choose between internal implementation details when repository evidence supports a best option.
- Review requests are read-only unless the user also asks for implementation.

## Numbered planning requests

For `implement small change <number>` requests, load `references/numbered-changes.md` and follow that bounded workflow. Do not keep catalog-specific instructions in the always-loaded controller body.

## Reviewer use

Custom reviewers are independent, read-only audits—not builders or decision-makers. Use at most one reviewer for a bounded high-risk diff, or the release reviewer for a final readiness claim. The implementing agent remains responsible for the final decision and verification.

## Verification

- The selected specialist matches the actual changed path.
- No unnecessary specialist or reviewer was loaded.
- Every requested deliverable is complete and exercised where tools permit.
- Claims distinguish current evidence, pre-existing failures, and manual gaps.
- The final diff contains no unrelated edits.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.