# LUMA glass surface system

## Roles

`LiveGlassSurface` is genuine Haze backdrop blur. It is reserved for small, fixed, high-value surfaces. It must not wrap repeated rows, full screens, scrolling containers, timelines, or continuously resizing content. It uses Haze automatic input scaling and has no progressive or animated blur.

`SoftGlassSurface` is the default material. It uses the same tint, border, shadow, shape, and color family without backdrop blur. Cards, lists, panels, calendar content, details, and performance fallbacks use this role.

`ModalSurface` is a behavioral modal role built from SoftGlass. `LumaModalBottomSheet` owns the shared scrim, shape, elevation, and surface composition while Material 3 continues to own dismissal, focus, Back, and window-inset behavior.

## Route policy

| Route or surface | LiveGlass | SoftGlass / ModalSurface |
|---|---:|---:|
| Top-level bottom navigation | Allowed when supported | Required fallback |
| Home capture | No | Yes |
| Calendar header, timeline, event cards | No | Yes |
| Spaces, Review, Settings content and Search | No | Yes |
| Repeated rows and cards | Never | Yes |
| Item Details and AI assistance cards | No | Yes |
| Situation AI | No by default | ModalSurface |
| Appearance preview | Bounded preview region only | Side-by-side production fallback |

The app shell owns one stable Haze source containing only the rendered ambient background. Route content and lazy lists are siblings above that source and are never Haze sources. `glassRenderingPolicyForRoute` allows the small, fixed bottom navigation to use LiveGlass on top-level routes; other route surfaces still choose their explicit SoftGlass or ModalSurface roles. The policy does not change while a list scrolls.

## Fallback rules

LiveGlass resolves to SoftGlass when the route policy is `SoftOnly`, the Haze source is unavailable, or the platform API is below Android 12. Tests and previews can supply `GlassRenderingPolicy.SoftOnly` explicitly. The fallback shares production role visuals instead of imitating blur.

## Background blur

“Background blur” applies only to a user-selected image. The image is decoded to a bounded size, processed off the UI thread when the image or quantized blur setting changes, and cached by image URI plus blur radius. Preset backgrounds disable this setting and explain that presets are already softly rendered. Interface glass and background-image processing are independent.

## Changed components

- `GlassSurface.kt`: LiveGlass, SoftGlass, ModalSurface, shared visuals, platform and route fallback.
- `LumaModal.kt`: app-owned modal composition.
- `OrbitBackground.kt`: stable background-only Haze source and cached image processing.
- `GlassRolePreview.kt`: bounded production-component preview plus light/dark role previews.
- `FloatingBottomNavigation.kt`: route-policy-aware LiveGlass with SoftGlass fallback.
- Home, Calendar, Spaces, Review, Settings, Item Details, capture modal, and Situation AI call sites now use explicit roles.

## Benchmark coverage and results

The `macrobenchmark` module contains profileable frame-timing scenarios for Spaces, Review, Settings, Situation AI open/close, Home-to-Spaces navigation, and the Appearance preview. Results are intentionally not fabricated: a connected physical device or emulator with representative populated data is required. Compare generated `frameDurationCpuMs` and frame-overrun distributions against a build of the pre-refactor revision; list-heavy routes must show no meaningful regression before release acceptance.

## Remaining visual checks

- Verify the modal shape and IME behavior on a physical phone in portrait and landscape.
- Compare light/dark and preset/custom backgrounds for the Home navigation and Appearance preview.
- Run populated list benchmarks; empty-state measurements are not release evidence.
