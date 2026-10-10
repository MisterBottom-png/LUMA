---
name: luma-compose-ui
description: Use when implementing or reviewing LUMA Jetpack Compose, Material 3, layout, theme, UI state, navigation surfaces, accessibility, or screenshot-driven visual fixes.
---

# LUMA Compose UI

## When to use

- Composables, Material 3, layouts, typography, themes, state rendering, insets, or UI navigation surfaces.
- Screenshot-driven visual corrections.
- Accessibility, touch-target, semantics, focus-order, text-scaling, or contrast work.

## Do not use

- Domain logic, persistence, scheduling, or parsing whose UI is only an endpoint.
- Haze source/effect wiring or glass-role architecture; use `luma-glass-haze-guardian` as primary or support.
- Broad redesign when the request is a bounded visual fix.

## Product direction

LUMA should remain calm, premium, private, soft, and uncluttered. Home is capture-first, not a dashboard. Raw captures and internal AI records must not appear as normal user-facing cards.

## Workflow

1. Inspect the current screen or screenshot and locate the owning Composable, state source, callbacks, and tests.
2. Classify the issue as layout, state, theme, typography, insets, navigation, accessibility, or data rendering.
3. Establish the existing behavior across affected states before editing.
4. Reuse existing design tokens and shared components; add a token only for a repeated semantic role.
5. Keep business logic out of Composables and preserve unidirectional state flow.
6. Make the smallest visual or behavioral patch that fully resolves the request.
7. Add focused state or calculation tests when they materially reduce regression risk.
8. Verify the affected matrix and review the final diff.

## UI quality checks

Use only relevant checks, but do not omit a relevant state:

- light, dark, and Auto theme behavior;
- preset and custom backgrounds;
- normal and large text;
- touch targets, roles, labels, selected state, focus order, and content descriptions;
- keyboard, IME, status/navigation bars, safe areas, and Back behavior;
- empty, loading, error, disabled, and populated states;
- readable contrast without hiding the selected background;
- stable keys and state ownership for lazy or animated content.

## Verification

- The owning state path and affected callbacks were traced.
- Relevant automated tests and compile/lint checks pass.
- The affected visual matrix was exercised or listed as a manual check.
- No business logic, raw/internal record visibility, or unrelated screen changed.
- Any Glass/Haze change follows the shared material roles and guardian contract.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.