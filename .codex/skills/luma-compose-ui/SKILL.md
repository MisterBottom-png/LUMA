---
name: luma-compose-ui
description: Use for Jetpack Compose UI work in LUMA: layouts, Material 3, state hoisting, LazyColumn behavior, dark/light mode, edge-to-edge, touch targets, accessibility, visual polish, and UI regressions.
---

# LUMA Compose UI

Use this skill for LUMA UI implementation and review in Jetpack Compose.

## Product UI direction

LUMA should feel calm, personal, quiet, premium, and low-cognitive-load.

Home must not become a dashboard. Apparently restraint is now a competitive advantage.

## Compose rules

- Follow existing Compose structure.
- Prefer small composables with clear names.
- Keep state hoisted where appropriate.
- Avoid storing long-lived business state only inside composables.
- Use Material 3 components when consistent with existing UI.
- Avoid hardcoded colors that break dark mode.
- Preserve edge-to-edge/inset behavior.
- Keep touch targets usable.
- Check empty/loading/error states if affected.

## Lazy list checks

For lists, check:

```text
- stable keys where item identity matters
- no accidental item duplication
- no raw Processed/internal items shown as cards
- no heavy work inside item composition
- grouping/sorting stable across recomposition
```

## Dark/light mode checks

For UI changes, inspect:

```text
- background contrast
- text contrast
- icon contrast
- card/surface colors
- disabled state readability
- selected state readability
```

## Home-specific rules

Allowed on Home:

```text
- compact date/week awareness
- capture input
- microphone/send actions
- bottom nav
- Situation AI entry
- light contextual hints
```

Avoid:

```text
- dense task dashboards
- giant calendar grid
- analytics cards
- long lists
- noisy feeds
```

## Final UI evidence

Final report should include:

```text
- visual areas touched
- light/dark mode checked or not checked
- affected navigation paths
- manual screenshot/app test steps
```
