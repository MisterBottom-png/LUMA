# LUMA Settings menu design language

This language applies only to Settings menus. The Appearance settings index is the reference for calm hierarchy and grouped navigation inside Settings. Other LUMA screens keep their own role-specific layouts and must not be converted to this menu pattern by default.

## Hierarchy

Use one obvious reading order:

1. A Settings menu title states where the user is.
2. One short subtitle explains the menu's purpose when the title alone is insufficient.
3. Related choices sit inside one rounded grouped surface.
4. Each row has one primary label and, when useful, one short current-value or explanatory line.
5. Secondary or reset actions sit outside the main group so they do not compete with navigation.

Do not add extra headings, badges, helper paragraphs, or cards to a Settings menu unless they communicate a distinct state or decision. Prefer progressive disclosure: a compact index row should open a focused submenu when its controls would make the index dense.

## Grouped list pattern

Use the Appearance-style grouped list for Settings indexes and Settings submenus that contain peer destinations:

- One `SoftGlassSurface` contains the related rows.
- Use `OrbitShapes.Prominent` or the nearest existing shared shape role for the outer group.
- Give every row a consistent leading icon well, text column, and trailing navigation cue.
- Use `titleSmall` or the established row-title role with semibold weight for the primary label.
- Use `bodySmall` and `onSurfaceVariant` for the current value or explanation.
- Keep row copy short enough to scan; move long explanations into the destination.
- Separate rows with a low-emphasis divider; do not wrap every row in its own card.
- Make the complete row interactive with a minimum 48 dp touch target and a clear accessibility label.
- Use a chevron only when the row navigates. Selection, switches, and destructive actions use their own established affordances.

Keep one group focused on one job. If a group becomes difficult to scan, split it by meaning or introduce a focused submenu; do not solve density by shrinking text, icons, spacing, or touch targets.

## Visual roles

- Typography comes from `OrbitTypography`; do not create screen-local type scales for equivalent hierarchy.
- Repeated spacing comes from `OrbitSpacing`; component-specific measurements may remain local when they have a distinct role.
- Comparable surfaces use `OrbitShapes`; circles and pills remain explicit exceptions.
- App colors come from `MaterialTheme.colorScheme` so appearance choices, light mode, and dark mode remain authoritative.
- Repeated and scrolling content uses `SoftGlassSurface`. `LiveGlassSurface` remains limited by `LUMA_GLASS_SURFACE_SYSTEM.md`.
- Motion uses `OrbitMotion`, remains finite, and respects Android's animation accessibility setting.

## Settings addition rules

Before adding or changing a Settings menu:

1. Name the element's role: heading, grouped navigation, content card, action, modal, or fixed chrome.
2. Reuse the existing component and token for that role when one exists.
3. Add the new item to an existing group only when it serves the same purpose and hierarchy.
4. Use a focused submenu when controls or explanation would make the parent surface dense.
5. Do not duplicate a control, label, or destination already available nearby.
6. Extract a shared Settings component when the same new pattern is needed in more than one Settings menu; do not copy a private implementation into unrelated app screens.
7. Remove obsolete UI and imports made redundant by the addition, but only with call-site evidence.

## Definition of done for Settings menu additions

- The reading order is obvious without color alone.
- The new element uses existing type, spacing, shape, color, surface, and motion roles or documents why a new role is necessary.
- The Settings menu remains scannable at increased font scale and does not become a stack of unrelated cards.
- Interactive elements have correct semantics, visible state, and usable touch targets.
- Light, dark, preset-background, and custom-background behavior is checked where affected.
- Insets, keyboard behavior, Back navigation, scrolling, and floating-navigation clearance remain correct.
- The final diff contains no duplicated controls, stale UI, or unrelated visual changes.

## Source of truth

- Theme and color roles: `app/src/main/java/com/orbit/app/ui/theme/Theme.kt`
- Typography: `app/src/main/java/com/orbit/app/ui/theme/Type.kt`
- Spacing, shape, and motion roles: `app/src/main/java/com/orbit/app/ui/theme/DesignTokens.kt`
- Surface behavior: `docs/codex/LUMA_GLASS_SURFACE_SYSTEM.md`
- Reference grouped list: `AppearanceMenuCard` and `SettingsMenuRow` in `SettingsScreen.kt`
