---
name: luma-glass-haze-guardian
description: Use when reviewing or implementing LUMA glass roles, Haze source/effect wiring, backdrop blur, translucent surfaces, custom-background materials, contrast, or glass performance.
---

# LUMA Glass and Haze Guardian

## When to use

- LiveGlass, SoftGlass, ModalSurface, Haze, blur, tint, noise, fallback, or custom-background material behavior.
- Glass consistency, contrast, performance, source/effect architecture, or version-specific Haze API work.

## Do not use

- General Compose layout, typography, navigation, or accessibility unless it directly determines the glass result.
- Scrolling-content redesign or app-wide live blur.
- Dependency or toolchain upgrades unless explicitly requested.
- Room, AI, reminder, notification, or user-data behavior.

## Required context

Read the relevant portions of `docs/codex/LUMA_PROTECTED_BEHAVIORS.md`, `docs/codex/learning/LUMA_DECISIONS.md`, `docs/codex/learning/LUMA_PATTERN_LIBRARY.md`, and `docs/codex/LUMA_REGRESSION_CHECKLIST.md`. Inspect the pinned Haze version in `gradle/libs.versions.toml`. If it remains 1.7.2, use `references/haze-1.7.2-and-luma.md`; otherwise verify matching official versioned documentation before API work.

## Material roles

| Role | Use | Rendering |
|---|---|---|
| `LiveGlassSurface` | Small, bounded, fixed, high-value app-shell chrome | Haze when policy and source allow; otherwise SoftGlass |
| `SoftGlassSurface` | Cards, rows, lists, scrolling/resizing content, details | Shared translucent Material surface; no live backdrop blur |
| `ModalSurface` | App-owned sheets and dialogs | Shared soft material with modal shape, scrim, elevation, and padding |

Prefer SoftGlass unless the surface clearly qualifies for LiveGlass.

## Workflow

1. Inspect the current material role, call sites, shell source, rendering policy, settings inputs, and focused tests.
2. Establish whether the task is review-only, a focused fix, a new surface, or an explicitly approved version migration.
3. Preserve one stable app-shell `HazeState` and one background-only source containing the complete rendered background.
4. Reuse shared roles and recipes in `GlassSurface.kt`; do not create route-local blur/tint/noise constants.
5. Keep lazy, scrolling, repeated, resizing, and full-screen route content off live backdrop blur.
6. Derive custom-background glass from the bitmap actually rendered, not merely a requested URI.
7. Add focused policy or calculation tests, then run compile/lint and relevant visual/performance checks.
8. Review the affected theme, background, strength, API fallback, scrolling, and modal matrix.

## Architecture boundaries

- Never make route content, a lazy list, a scrolling card, or a full-screen route a Haze source.
- Select `GlassRenderingPolicy` from route/platform/effects policy, not ordinary scroll state.
- Keep user-image background blur bounded and cached; do not replace it with per-frame interface blur.
- Preserve readable content color, stable shapes, touch targets, semantics, insets, and Back behavior.

## Verification

- One shared source and shared visual recipe remain authoritative.
- Every affected surface has an explicit material role.
- Live blur remains bounded and fixed; scrolling content remains soft and stable.
- Fallback, custom-background transition, contrast, and performance behavior are checked.
- No unrelated dependency, app behavior, or data path changed.

## Workplace privacy

Read `docs/codex/WORKPLACE_PRIVACY_POLICY.md` before text-bearing work. Never repeat protected identity values; use generic role labels. Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes and before completion, then semantically review the changed text.