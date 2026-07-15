---
name: luma-compose-ui
description: Implement or review LUMA Jetpack Compose UI, Material 3, layouts, themes, dark mode, state rendering, navigation surfaces, accessibility, and screenshot-driven fixes.
---

# LUMA Compose UI

## Product direction

LUMA should feel calm, premium, private, modern, soft, and uncluttered. Home remains capture-first.

## Workflow

1. Inspect the screenshot or current screen and locate the owning Composable and state source.
2. Identify whether the issue is layout, state, theme, typography, insets, navigation, or data rendering.
3. Make the smallest change that fixes the requested behavior.
4. Reuse existing design tokens and components.
5. Verify light and dark themes when affected.
6. Check text scaling, touch targets, content descriptions, focus/order, contrast, empty/loading/error states, keyboard/insets, and back behavior where relevant.
7. Avoid copying business logic into Composables.
8. Do not turn Home into a dashboard or expose raw/internal records.

## Screenshot requests

When the visual target is clear, do not interrupt with product questions. Implement the bounded visual correction and report manual checks.

## State rules

- Prefer unidirectional state flow already used by the project.
- Keep transient UI state separate from persistent domain state.
- Avoid unnecessary recomposition and unstable object creation in hot paths.
- Do not introduce hardcoded user-facing strings when resource localization exists or is being introduced.
## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md`. Never mention any coworker or workplace-associated person in repository-controlled or generated content. Use generic role labels only. If an identifier is found, cite only its location and category; do not quote it. Review changed output before completion and run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion.

