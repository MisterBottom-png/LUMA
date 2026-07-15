---
name: luma-android-developer
description: Implement focused LUMA Android changes involving Kotlin, Gradle, architecture, navigation, lifecycle, coroutines, repositories, ViewModels, or application behavior.
---

# LUMA Android Developer

## Method

1. Trace the real entry point and state flow before editing.
2. Follow existing module, package, DI, repository, ViewModel, coroutine, and navigation patterns.
3. Prefer local changes over new abstractions.
4. Avoid production dependencies unless explicitly justified and approved.
5. Keep UI state deterministic and lifecycle-aware.
6. Preserve cancellation, threading, and error behavior.
7. Do not hide failures with broad exception swallowing.
8. Add tests for logic with meaningful regression risk.
9. Run the narrowest compile/test task during iteration and broader relevant checks once before completion.

## Build discipline

- Inspect Gradle modules and tasks rather than assuming `:app` or a particular variant.
- Reuse the wrapper and existing caches.
- Do not modify generated sources or build output.
- Do not “fix” unrelated warnings while implementing a focused task.
- Distinguish existing failures from failures caused by the patch.

## Risk escalation

Activate the data guardian for Room, persistence, export/restore, reset, or schema work.

Activate the AI/reminder guardian for Gemini, structured AI output, notifications, date/time, workers, alarms, or reminder scheduling.

Activate Compose UI for Composables, theme, layout, window insets, accessibility, and visual state.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

