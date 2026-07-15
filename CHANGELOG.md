# Changelog

## Change 008 - Initialize Drive workflow

- Established the configured Google Drive ZIP as the authoritative source package.
- Added source continuity records at the repository root.
- Recorded the detected predecessor as change 7.
- Preserved the complete existing source tree.
- No application behavior changed.

## Change 009 - Hide unavailable Monday actions

- Confirmed and preserved the Home action gate requiring both Monday configuration and a working send callback.
- Added focused regression coverage for hidden unavailable states and preserved configured behavior.
- Exposed only the action-list decision logic at module scope for testing.
- No database, navigation, or integration architecture changed.

## Change 010 - Make Situation AI body independently scrollable

- Preserved the fixed Situation AI header and Close control outside the scrolling region.
- Kept the body in one `LazyColumn` and made it fill the remaining bounded sheet height.
- Avoided nested scrolling and preserved all existing Situation AI content and actions.
- No navigation, data, integration, or dependency behavior changed.

## Change 011 - Fix Situation AI Back behaviour

- Preserved Android Back dismissal through the existing Situation AI dialog callback.
- Restored focus to the central Situation AI control after dismissal and IME closure.
- Left ordinary Back navigation untouched while the Situation AI sheet is closed.
- No navigation destinations, data behavior, dependencies, or visual styling changed.


## Change 012 - Embed the send icon

- Removed the separate full-width Ask button from the Situation AI composer.
- Embedded a 48 dp send icon in the text field with enabled, disabled, pressed, and accessible states.
- Preserved the existing submission callback and validation behavior.
- Added focused regression coverage for send enablement.
- No data, navigation, dependency, or AI behavior changed.


## Change 013 - Add Ask loading state

- Replaced the Ask send glyph with a compact progress indicator while generation is in flight.
- Prevented duplicate submissions until the active request completes.
- Restored the send control after success or failure by resetting request state in `finally`.
- Added focused regression coverage for loading and restored idle states.
- No data, navigation, dependency, or AI prompt behavior changed.


## Change 014 - Support IME Send

- Added the keyboard IME Send action to the Ask LUMA composer.
- Routed IME Send and the embedded send icon through one guarded submission path.
- Kept blank, too-short, and in-flight duplicate requests blocked.
- Added focused regression coverage for shared submission behavior.
- No data, navigation, dependency, AI prompt, or visual styling behavior changed.
