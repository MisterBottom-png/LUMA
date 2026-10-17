# Tallele visual system: calm and premium

App-wide rules for how screens look and move. Settings menus also follow
`LUMA_DESIGN_LANGUAGE.md`; the tab bar glass follows
`LUMA_GLASS_SURFACE_SYSTEM.md`. Design authority: Limestone Harbour proposal v1.3 (`docs/limestone-harbour/`). When this file and a screen disagree, the screen is wrong.

Premium here means fewer things on screen, each done with care, all following the same
rules. Calm means nothing competes for attention it has not earned.

## 1. Glass and surfaces (Limestone Harbour, proposal v1.3 §0, §6.4)

- **Only the floating tab bar is glass.** Lists, cards, sheets, dialogs, menus, snackbars,
  editors and the capture box are solid. Roles, fallbacks and tests:
  `LUMA_GLASS_SURFACE_SYSTEM.md`.
- **No gradients, no light pools, no noise, no shine.** The page is plain `page` colour; a
  built-in background (Capture only) carries its own fixed wash.
- **One flat edge:** a 1 dp `hairline` where a surface needs an edge. Shadows only on the
  tab bar and sheets, soft and low.
- Ordinary lists are rows with dividers. Cards are only for the capture box, decisions and
  important status.

## 2. Colour

Tokens live in `ui/theme/LimestoneColors.kt` (`TalleleTheme.colors`) and are mapped onto
`MaterialTheme.colorScheme` so existing screens follow them. Values: proposal §6.1 and the
four accents of §7.17 (Ink, Harbour, Moss, Plum).

- **Ink** (the accent) only on Keep or the main button, the selected tab, links and the focus ring.
- **Ochre** only where a choice is needed, never as decoration. It is not mapped onto any
  Material role; reach it through `TalleleTheme.colors`.
- **Red** only for errors and erasing.
- A colour never carries meaning alone; there is always a word or an icon.
- **Contrast:** 4.5:1 for text, 3:1 for UI edges and icons. `LimestoneColorsTest` checks every
  token pair in both themes and every accent.

## 3. Type

- Two voices: **the person's words in Literata**, **the app's words in Noto Sans**. Both are
  bundled (`res/font/`, OFL texts in `assets/licenses/`) and cover Latin, Estonian and
  Cyrillic, so Russian looks the same as English.
- Roles in `TalleleTheme.typography` (§6.2): screen title 24/32 600, item title 24/34 500
  (Literata), capture text 20/30, words in lists 17/26 (Literata), body 16/23, metadata 14/20,
  tab labels 12 sp 600 and growing with font size. Material `Typography` uses Noto Sans.
- No all-caps, no light weights, nothing important below 13 sp. Times use tabular figures.

## 4. Space and shape

- 4 dp grid (4, 8, 12, 16, 20, 24, 32, 48). Side margins 20 dp, 16 dp under 360 dp wide.
- Corners: capture box 20 dp, cards 12 dp, chips and small buttons 8 dp, sheets 24 dp at the
  top, tab bar 20 dp (`TalleleShapes`; `OrbitShapes` points at the same values).
- A 2 dp ink line runs down the left of the capture box and the receipt.
- Touch targets at least 48 × 48 dp. Rows grow with text and never clip. Scrolling lists end
  with room for the tab bar.

## 5. Motion

Durations (§6.5, `TalleleMotion`): quick 120 ms, settle ~220 ms, move ~300 ms, tabs 180 ms
fade. No bounce, confetti, flying text or looping animation; nothing depends on an animation
finishing. With "Remove animations" on (re-read whenever the app comes to the front) every
movement is instant and the tab bar uses Frosted.

- **Springs without overshoot for routine actions** (`OrbitMotion` spatial 0.9 damping,
  stiffness 1400 fast / 700 default; effects 1.0 damping). Bounce is for rare moments only.
- **Tabs fade**, nothing slides sideways between them. Today's route transition (out 90 ms,
  in 220 ms from 98 % scale, `OrbitApp.kt`) becomes the 180 ms fade in P2 with the new shell.
- **Pushed screens** (Settings, item details) slide in a short way (1/10 width) with an
  emphasized decelerate curve (0.05, 0.7, 0.1, 1) over 380 ms and leave faster (200 ms).
- **Press feedback** scales to 0.97 on a spring and turns around smoothly when released.
- Never animate from scale 0; start at 0.95 or more. Exits are faster than entrances.
- Things used many times a day get little or no animation.
- "Remove animations" is respected everywhere; decorative motion is skipped, state still
  changes.

## 6. Haptics

- One short tick on Keep. None on ordinary taps. A haptic means something happened: keep, done, undo.
- Crisp, short effects only; if the choice is a buzzy haptic or none, choose none.

## Checks before a visual change is done

- Light and dark, every accent, a built-in background on Capture, 200 % font size.
- No text cut off, nothing hidden behind the tab bar, no new outline or second accent.

## Sources

- Apple HIG, Materials and Liquid Glass; WWDC25 "Meet Liquid Glass"; WWDC23 "Animate with springs".
- NN/g: "Glassmorphism", "Liquid Glass", "Animation duration".
- Material 3 motion tokens (`StandardMotionTokens.kt`), easing and duration tokens.
- Haze library docs (materials, performance).
- Refactoring UI (Wathan, Schoger); Linear, "How we redesigned the Linear UI".
- Emil Kowalski, "7 practical animation tips"; Rauno Freiberg, "Invisible details of interaction design".
- Android haptics design principles.
