# Haze 1.7.2 and LUMA reference

Use this reference only while LUMA pins `dev.chrisbanes.haze:haze:1.7.2`. Re-check the official versioned documentation if the dependency changes. Do not copy Haze 2.x alpha APIs into the 1.7.2 code path.

## Official sources

- Haze 1.7.2 usage: https://chrisbanes.github.io/haze/1.7.2/usage/
- Haze 1.7.2 platform behavior: https://chrisbanes.github.io/haze/1.7.2/platforms/
- Haze 1.7.2 performance: https://chrisbanes.github.io/haze/1.7.2/performance/
- Haze 1.7.2 FAQ: https://chrisbanes.github.io/haze/1.7.2/faq/

Verified against those pages on 2026-07-16.

## Core model

Background glass uses one remembered `HazeState`, `Modifier.hazeSource(state)` on content behind the glass, and `Modifier.hazeEffect(state, style)` on the foreground surface. `Modifier.blur` blurs its own content and is not a substitute for backdrop glass.

For LUMA:

```text
OrbitBackground
  complete rendered preset/custom background -> hazeSource(shared state)
  route content
  bounded LiveGlassSurface -> hazeEffect(shared state)
```

Keep the effect surface transparent apart from the shared Haze tint, border, and shadow. Preserve modifier order where clipping and overlapping source/effect nodes are involved.

## Styling

In 1.7.2, `HazeStyle` and the `hazeEffect` block resolve each property independently. Effect-scope values override the provided style, which overrides `LocalHazeStyle`. LUMA avoids mixed ownership by constructing the complete shared `HazeStyle` in `orbitGlassVisuals`.

- Blur radius controls blur strength and can help foreground legibility.
- Tint is the primary contrast layer; multiple tints apply in sequence.
- Noise is optional texture and adds rendering cost.
- `fallbackTint` must visually relate to the live material.
- `HazeInputScale.Auto` enables platform-selected source scaling. Fixed values below 1 may reduce cost but must be benchmarked for quality.
- Progressive blur is not part of LUMA's default material. Prefer a simple mask when a fading effect is truly required and validate platform behavior.

Do not scatter blur, tint, noise, borders, or opacity constants across screens. Add a shared style role only when an existing role cannot express a real behavioral difference.

## Layering and hierarchy

Normal LUMA glass needs one background source and foreground effects. Explicit `zIndex`, source keys, and `canDrawArea` filters are needed only for deliberately overlapping nodes that are both sources and effects. Do not introduce that complexity for ordinary cards, navigation, or modals.

A composition local is a supported way to pass a shared `HazeState` through a deep hierarchy. LUMA already uses this pattern; reuse it instead of threading new route-owned states.

## Android policy

Haze 1.7.2 is optimal on Android 13 and newer. Android 12/12L uses additional invalidation behavior, while Android 11 and older defaults to a translucent fallback because live blur is disabled. LUMA intentionally narrows LiveGlass further to API 31+ and uses SoftGlass otherwise.

Keep that LUMA fallback unless a separately approved, measured compatibility change proves a better policy.

For Robolectric screenshot tests, use SDK 35+ when asserting Haze edges because older Robolectric SDK behavior may render clamp edges incorrectly.

## Performance rules

Official benchmarks show that Haze has a meaningful and scenario-dependent frame cost. Input scaling offers a modest improvement, not a reason to expand the blurred area. LUMA's first performance controls are architectural:

1. Minimize the number and area of live effects.
2. Keep effects fixed and bounded.
3. Keep scrolling/repeated content on SoftGlass.
4. Avoid animated blur radius, progressive blur, and active effect resizing.
5. Treat background decode/blur, Haze, noise, shadows, and animation as one shared frame budget.
6. Measure LUMA's real populated routes; library sample benchmarks are guidance, not proof.

Validate Settings, Spaces, Review, Search, bottom navigation, and app-owned sheets with preset and custom backgrounds, including maximum blur/glass settings. Prefer release-mode Macrobenchmark evidence for performance-sensitive changes.

## Existing LUMA ownership

- `gradle/libs.versions.toml`: pinned Haze version.
- `app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt`: shared state, rendered background, and source.
- `app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt`: roles, policy fallback, and visual recipes.
- `app/src/main/java/com/orbit/app/ui/components/GlassRolePreview.kt`: bounded production preview.
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`: route rendering policy.
- `app/src/test/java/com/orbit/app/ui/components/GlassRolePolicyTest.kt`: role and appearance calculations.
- `app/src/test/java/com/orbit/app/ui/navigation/BottomNavigationVisibilityTest.kt`: route-policy coverage.

## Review heuristics

Flag as must-fix:

- a route or lazy list becoming a Haze source;
- live blur on repeated or scrolling surfaces;
- glass without a readable fallback or content contrast;
- multiple independent app-shell states/sources;
- material switching driven by scroll position;
- custom-background state that changes before the new bitmap is rendered;
- unrequested dependency/toolchain expansion.

Flag as should-improve:

- a one-off recipe that duplicates a shared role;
- excessive blur/noise/shadow for the surface's importance;
- inconsistent shape, border, scrim, or padding;
- missing focused policy/calculation tests;
- visual validation that omits a theme, background mode, strength extreme, or fallback platform.
