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

## Small change 17 - Make Review rows tappable

- Made all Review item rows use the same clickable Material surface and item-selection callback.
- Preserved pressed feedback, button semantics, existing reminder navigation, and non-interactive informational content.
- Deferred task and capture destination routing to small change 18.

## Small change 18 - Open the correct Review item

- Routed Review task, capture, and reminder rows to their existing detail destinations with the original item IDs.
- Preserved the reminder-specific route and reused the generic task and capture detail screen.
- Added focused route coverage without changing Review visuals, data, or item actions.

## Small change 19 - Add carry-forward decisions

- Added Tomorrow, Choose date, Keep unscheduled, and Mark complete controls to Evening Review carry-forward items.
- Reused existing task scheduling and completion behavior and existing reminder rescheduling/cancellation behavior.
- Preserved reminder local time and notification offset when changing its date.
- Kept unscheduling task-only because reminders require a target time.

## Small changes 20, 22, and 23 - Improve bottom-navigation accessibility

- Preserved a non-color circular selection indicator for the active destination.
- Exposed standard destinations as labeled tabs with selected-state semantics.
- Enforced at least 48 dp touch targets while keeping the existing icon artwork sizes.
- Kept Situation AI as a precisely labeled central button and preserved navigation behavior.

## Small changes 36-38 - Add the Home visible-month context

- Added a localized, quiet month label above the Home weekday strip.
- Added comfortable spacing and a subtle month-label transition.
- Added horizontal week navigation without changing date-tap or capture behavior.
- Defined split-month weeks by their middle day so the displayed month remains stable.
- Persisted the visible week and refreshed scheduled-item indicators for each newly visible range.
- Follow-up: balanced the header with a 22 sp medium month label and 13 sp subdued `Week N` label on one baseline, 14 dp above the weekday names.
