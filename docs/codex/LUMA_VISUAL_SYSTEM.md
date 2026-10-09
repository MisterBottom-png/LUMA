# Tallele visual system: calm and premium

App-wide rules for how screens look and move. Settings menus also follow
`LUMA_DESIGN_LANGUAGE.md`; glass roles and route policy follow
`LUMA_GLASS_SURFACE_SYSTEM.md`. When this file and a screen disagree, the screen is wrong.

Premium here means fewer things on screen, each done with care, all following the same
rules. Calm means nothing competes for attention it has not earned.

## 1. Glass

- **Glass is the floating layer, not the content.** The tab bar, the Home capture box and
  sheets float; they may cast a shadow and may use live blur. Content cards (Spaces,
  Review, Settings groups, Calendar) sit flat on the page. (Apple HIG Materials: "Don't
  use Liquid Glass in the content layer"; NN/g on glassmorphism.)
- **Never glass on glass.** Inside a glass surface, use fills and plain transparency.
- **Glass needs something behind it.** Preset backgrounds have two very soft light pools
  (accent top right, palette bloom bottom left). On a flat fill glass reads as grey.
- **No forced dim on presets.** Black dim is for photos only (or set by hand). A dim on a
  light preset turns cream into dirty grey.
- **Light theme surfaces are lighter than the page** (frosted white,
  `surfaceContainerLowest`), never greyer. Dark theme surfaces step up in tone.
- **One edge for every surface:** a 1 dp hairline lit from above (`glassEdgeBrush`): white
  highlight at the top fading to a faint ink line (light) or almost nothing (dark).
  No solid grey outlines.
- **Shadows are soft, low and tinted**, and only on the floating layer
  (`floatingElevationFor`). A grey shadow under a translucent card shows through it.
- **Live blur values:** 18–24 dp blur, surface-coloured tint, noise 2–4 %. Fall back to
  the soft recipe below Android 12 and when "Glass effect" is Soft or Off.

## 2. Colour

- **One accent per screen, used for one strong element** (the filled button, the selected
  tab, today). In the standard palette, secondary and tertiary are quiet tints of the
  same accent (`quietContainer` in `Theme.kt`); no peach, brown or pink appears.
- **Secondary controls are tonal pills with no ring.** `components/QuietControls.kt`
  provides drop-in `AssistChip` and `OutlinedButton`; import those, not Material's.
- **Text contrast:** 4.5:1 for text below 18 sp, 3:1 for large text and icons, checked on
  the worst spot behind glass. No grey text on tinted surfaces; use `onSurfaceVariant`.
- **Red only for destructive actions.** Never for late or overdue items.

## 3. Type

- One scale in `OrbitTypography`; no screen-local sizes for the same role.
- Two weights: Normal for reading, SemiBold for titles (Medium only for small labels).
- Large type is tightened (−0.45 sp at 28 sp), small type opened slightly (+0.2 sp at 12 sp).
- Screen titles on main tabs use `headlineMedium` (28 sp). Numbers in dates and times use
  tabular figures (`tnum`).

## 4. Space and shape

- 4 dp grid. Screen side margin 20–24 dp, the same on every main tab.
- Less space inside a group than between groups. Empty space only where it is meant
  (Home); a gap under a title is a bug.
- Corner radii: 14 (small controls), 18–24 (cards), 28–32 (hero surfaces, bar, sheets),
  full (pills). Nested corners are concentric: inner radius = outer radius − padding.
- Boxes in boxes are a smell. Home has one surface: the capture box.
- Touch targets at least 48 dp.

## 5. Motion

- **Springs without overshoot for routine actions** (`OrbitMotion` spatial 0.9 damping,
  stiffness 1400 fast / 700 default; effects 1.0 damping). Bounce is for rare moments only.
- **Tabs fade through**: out in 90 ms, in over 220 ms from 98 % scale. Tabs are siblings,
  so nothing slides sideways between them.
- **Pushed screens** (Settings, item details) slide in a short way (1/10 width) with an
  emphasized decelerate curve (0.05, 0.7, 0.1, 1) over 380 ms and leave faster (200 ms).
- **Press feedback** scales to 0.97 on a spring and turns around smoothly when released.
- Never animate from scale 0; start at 0.95 or more. Exits are faster than entrances.
- Things used many times a day get little or no animation.
- "Remove animations" is respected everywhere; decorative motion is skipped, state still
  changes.

## 6. Haptics

- None on ordinary taps. A haptic means something happened: send (CONFIRM), done, undo.
- Crisp, short effects only; if the choice is a buzzy haptic or none, choose none.

## Checks before a visual change is done

- The **Screens** workflow renders every main screen in light and dark, default and
  violet, to the branch `screens/<branch>`. Look at them before and after.
- Light and dark, preset and custom background, largest font size.
- No text cut off, nothing hidden behind the tab bar, no new outline or second accent.

## Sources

- Apple HIG, Materials and Liquid Glass; WWDC25 "Meet Liquid Glass"; WWDC23 "Animate with springs".
- NN/g: "Glassmorphism", "Liquid Glass", "Animation duration".
- Material 3 motion tokens (`StandardMotionTokens.kt`), easing and duration tokens.
- Haze library docs (materials, performance).
- Refactoring UI (Wathan, Schoger); Linear, "How we redesigned the Linear UI".
- Emil Kowalski, "7 practical animation tips"; Rauno Freiberg, "Invisible details of interaction design".
- Android haptics design principles.
