# Tallele glass surface system (Limestone Harbour)

Owner decision (proposal v1.3 §0, §6.4): glass is used on **one** thing, the floating tab bar.
Lists, cards, sheets, dialogs, menus, snackbars, editors and the capture box are always solid.
This replaces the earlier role system (live glass on the capture box and sheets, soft glass on
cards). Decisions: `docs/limestone-harbour/decisions.md` D-03, D-24.

## Tab bar looks

| Tab bar setting | Look | When it is used |
|---|---|---|
| **Glass** (default) | `surface` colour at 84 % over live Haze blur | Android 13 or newer, animations on, not in battery saver, on a top-level route |
| **Frosted** | `surface` colour at 94 %, no blur | Automatic fallback for Glass on Android 12 and older, in battery saver, with "Remove animations" on, or on a route that does not allow live blur; or when the person picks it |
| **Solid** | `surface` colour at 100 % | When the person picks it |

The person's choice is `LimestoneAppearance.tabBar` (`domain/model/LimestoneAppearance.kt`). Until
P5 adds the new Appearance control it is derived from the old Glass effect setting
(Strong → Glass, Soft → Frosted, Off → Solid). The runtime fallback is a pure function
(`resolveTabBarLook`, unit-tested branch by branch). Battery saver is followed live; "Remove
animations" is re-read every time the app comes to the front.

## Rules

- `hazeEffect` appears only in the tab bar. A unit test scans the sources to keep it that way.
- No glass cards, no blur inside lists, no stacked blur, no shine, no noise, no accent tint,
  no gradients.
- `SoftGlassSurface`, `LiveGlassSurface` (outside the tab bar) and `ModalSurface` keep their
  names so existing screens compile, but draw opaque token colours (`surface`, `sheet`,
  `inkSoft`) with a flat 1 dp `hairline` edge. Screens move to the shared parts
  (`ui/components/parts/`) in their own phase.
- The app shell keeps one Haze state. Its sources are the page colour (zIndex 0) and the NavHost
  container (zIndex 1, `tabBarBlurSource()`), recorded only while the bar resolves to Glass and is
  shown (D-27). No lazy list, card or individual route is a Haze source; a unit test checks this.
- Tab labels must read at 4.5:1 or more in every look, theme and accent, worst case with full
  main text directly under the bar (tested in `ui/components`).

## Backgrounds

There is no own-photo background and no background blur any more (§0, §7.17). A built-in
background shows on Capture only, drawn crop-to-fill with a fixed per-image wash of the page
colour (`ui/components/CaptureBackground.kt`; washes and evidence in
`docs/limestone-harbour/p1-assets.md`, D-23). Everywhere else the page is plain `page` colour.

## Checks still owed on a phone

Every item is `NEEDS OWNER PHONE VERIFICATION` (`docs/limestone-harbour/verification.md`):
blur smoothness while scrolling on a mid-range phone, the battery-saver and Remove-animations
fallbacks, and tab-label legibility over real content in light and dark.
