---
name: luma-android-developer
description: Use when implementing or debugging LUMA Kotlin, Gradle, architecture, navigation, lifecycle, coroutine, repository, ViewModel, or application behavior.
---

# LUMA Android Developer

## When to use

- Kotlin or Gradle implementation and debugging.
- Navigation, lifecycle, coroutine, repository, ViewModel, dependency wiring, or app-start behavior.
- Cross-layer changes whose primary risk is Android correctness rather than data format or visual design.

## Do not use

- Pure Compose styling or accessibility work; use `luma-compose-ui`.
- Room schema, migration, export, restore, or destructive data work without `luma-room-data-guardian`.
- AI, time parsing, reminder scheduling, alarms, workers, or notifications without `luma-ai-reminder-guardian`.
- Glass/Haze architecture work without `luma-glass-haze-guardian`.

## Required context

Trace the entry point, state owner, call sites, tests, and Gradle module before editing. Read `docs/codex/LUMA_PROTECTED_BEHAVIORS.md` when the path touches a protected flow.

## Workflow

1. Reproduce the issue or establish the current behavior from source and tests.
2. Trace state and control flow end-to-end; do not guess APIs, variants, DI, or ownership.
3. Match existing module, package, repository, ViewModel, coroutine, and navigation patterns.
4. Add or adjust the smallest regression test that proves the intended behavior.
5. Implement the smallest complete patch; prefer local changes over new abstractions.
6. Preserve cancellation, lifecycle, threading, and error behavior. Do not swallow failures broadly.
7. Run the narrowest compile or test during iteration, then broader affected checks once.
8. Inspect the final diff for generated output, unrelated formatting, or accidental architecture changes.

## Engineering boundaries

- Do not add a production dependency unless explicitly justified and approved.
- Do not modify generated sources or build output as source of truth.
- Do not fix unrelated warnings or refactor adjacent code without evidence that the task requires it.
- Keep UI state deterministic and lifecycle-aware.
- Distinguish pre-existing failures from regressions introduced by the patch.

## Verification

- The affected module and variant were discovered rather than assumed.
- Focused tests cover the changed logic and pass.
- Relevant compile, lint, or broader tests pass, or exact blockers are reported.
- Protected behavior and sibling call paths were checked where affected.
- No schema, export, reminder, or visual-material boundary changed without its guardian.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.