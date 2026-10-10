# LUMA UI/UX change progress

## 2026-07-14 — Small change 1: Remove internal AI IDs

- Removed internal source identifiers such as `task:5` and `capture:26` from Situation AI source chips and Weekly Review source presentation.
- Preserved `sourceId` and `itemId` internally for source validation, lookup, and navigation.
- Added a shared presentation helper with human-readable empty-title fallbacks.
- Added focused JVM tests proving technical identifiers and database IDs are not included in rendered source labels.
- Focused test and debug-build execution were attempted through the Gradle wrapper, but Gradle 8.9 was not cached and the isolated environment could not reach `services.gradle.org`; no test or build pass is claimed. Static source inspection found no remaining UI rendering of `sourceId` or joined `sourceItemIds`.

## 2026-07-14 — Small change 2: Create the Source Row component

- Added a reusable Compose `SourceRow` for AI evidence without migrating existing screens.
- The row supports a human-readable title, item type, optional date/time text, a full-row click callback, a trailing open affordance, two-line title ellipsis, and merged accessibility text.
- Added previews covering dated and long-title variants.
- Added focused JVM tests for accessible, human-readable source descriptions and blank-title fallback behavior.
- The strict workplace-privacy checker passed.
- Focused unit-test and debug-build execution were attempted, but Gradle 8.9 was not cached and the isolated environment could not reach `services.gradle.org`; no test or build pass is claimed.

## 2026-07-14 — Small change 4: Hide the Home microphone

- Removed the disabled microphone placeholder from the Home capture card because voice capture is unavailable.
- Kept the save/analyze action right-aligned so the capture card layout and spacing remain stable.
- Preserved all capture text, analysis, loading, and enablement behaviour.
- Static verification confirms the Home source no longer imports or renders the microphone icon or its unavailable-state accessibility label.
- Focused unit-test and debug-build execution were attempted, but Gradle 8.9 was not cached and the isolated environment could not reach `services.gradle.org`; no test or build pass is claimed.

## 2026-07-14 — Small change 3: Use Source Rows in Situation AI

- Replaced both Situation AI evidence chip lists with the reusable `SourceRow` component.
- Preserved source ordering and the existing source-selection callback, so tapping a row still opens the same item detail destination.
- Kept recommendation wording and action hierarchy unchanged.
- Static verification confirms Situation AI no longer renders source evidence with `FilterChip`; action chips remain unchanged.
- Focused `SourceRowTest` and `:app:assembleDebug` execution were attempted, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.
- The strict workplace-privacy checker passed, followed by semantic review of the three changed text-bearing files.


## 2026-07-14 — Small change 6: Remove remaining dead controls

- Removed the unavailable `Check Monday` action chip and its explanatory availability text from Situation AI. Source evidence: `SituationAiViewModel` hardcodes `mondayConfigured = false`, and the chip callback was empty.
- Replaced the selected reminder notification-offset `Button` with a non-clickable `Surface`. Source evidence: the selected option's callback was empty; only the other offset options call `updateNotificationOffset`.
- Preserved all functional Situation AI actions and reminder offset choices.
- Gradle compilation was attempted, but Gradle 8.9 was not cached and the isolated environment could not reach `services.gradle.org`; no build pass is claimed.

## 2026-07-14 — Small change 7: Fix the Situation AI header

- Moved the Situation AI title and Close control outside the body `LazyColumn` so they remain fixed while body content scrolls.
- Preserved the existing header content, body order, spacing, and Close callback.
- Gradle compilation was attempted, but Gradle 8.9 was not cached and the isolated environment could not reach `services.gradle.org`; no build pass is claimed.

## 2026-07-15 — Change 8: Initialize Drive workflow

- Confirmed the configured `LUMA-source-current.zip` could be fetched from Google Drive as raw ZIP bytes.
- Inspected the complete source package and preserved all application files and behavior.
- Added root `SOURCE_MANIFEST.json`, `PROJECT_STATE.md`, and `CHANGELOG.md` continuity records.
- Recorded change 7 as the detected predecessor based on the existing progress and project-state evidence.
- Noted that historical small change 5 evidence is absent from the root progress ledger.
- Repackaged the complete source for in-place Drive replacement and numbered backup retention.

## 2026-07-15 — Small change 5: Hide unavailable Monday actions

- Confirmed the Home capture decision sheet only exposes `Send to Monday` when the integration is configured and a working callback is supplied.
- Kept unavailable Monday setup/actions out of the capture UI; no Settings setup surface exists to preserve.
- Made the existing action-list logic module-visible for focused testing without changing runtime behavior.
- Added regression tests for unconfigured, missing-callback, and configured-working states.
- Static source checks passed and the strict workplace-privacy checker passed.
- Focused unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.

## 2026-07-15 — Small change 8: Make the Situation AI body independently scrollable

- Preserved the fixed Situation AI header outside the body scroll container.
- Kept all body content in the existing single `LazyColumn`, avoiding nested scrolling.
- Changed the body from underfilling its weighted allocation to filling the remaining bounded sheet height beneath the header.
- Preserved all content order, spacing, callbacks, and visual design.
- Static scroll-boundary checks and the strict workplace-privacy checker passed.
- Unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.

## 2026-07-15 — Small change 10: Fix Situation AI Back behaviour

- Preserved the existing dialog `onDismissRequest` path that Android Back uses to close Situation AI.
- Added a `FocusRequester` to the central Situation AI control in the floating bottom navigation.
- After sheet dismissal, focus is restored only once the overlay is closed and the IME is no longer visible.
- Added no global `BackHandler`, preserving normal navigation Back behaviour while Situation AI is closed.
- Static Back/focus checks and the strict workplace-privacy checker passed.
- Unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.
- Android Back and focus restoration were not physically exercised because no device or emulator was attached.


## 2026-07-15 — Small change 12: Embed the send icon

- Replaced the separate full-width Ask button with an embedded send icon inside the Situation AI `OutlinedTextField`.
- Preserved the existing `onAskLuma` submission callback and the minimum-query/in-flight enablement rules.
- Set the icon button to a 48 dp target with standard Compose pressed feedback, disabled behavior, and the accessible label `Send question`.
- Added a focused unit test covering empty, too-short, valid, and in-flight query states.
- Static composer checks and the strict workplace-privacy checker passed.
- Focused unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.
- The composer interaction and accessibility semantics were not physically exercised because no device or emulator was attached.


## 2026-07-15 — Small change 13: Add Ask loading state

- Replaced the Ask send icon with a compact progress indicator while an answer is being generated.
- Kept the send action disabled during the in-flight request, preventing duplicate submissions.
- Wrapped the request lifecycle in `try/finally` so the send control is restored after either success or failure.
- Preserved existing query validation, answer content, source rows, and overall composer layout.
- Added focused unit tests for loading visibility, duplicate blocking, and restored idle state.
- Static composer/coroutine checks and the strict workplace-privacy checker passed.
- Focused unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.
- The loading animation and failure recovery were not physically exercised because no device or emulator was attached.


## 2026-07-15 — Small change 14: Support IME Send

- Configured the Ask LUMA text field to expose the keyboard IME Send action.
- Routed both keyboard Send and the embedded send icon through the same guarded submission helper.
- Preserved validation that blocks blank, too-short, and in-flight duplicate requests.
- Added focused unit tests for rejected and accepted shared submission paths.
- Static IME/shared-path checks and the strict workplace-privacy checker passed.
- Focused unit-test and debug-build execution were attempted through a temporary normalized Gradle launcher, but Gradle 8.9 was not cached and the environment could not reach `services.gradle.org`; no test or build pass is claimed.
- IME Send behavior was not physically exercised because no device or emulator was attached.


## 2026-07-15 — Small change 15: Clear stale Ask responses

- Centralized Ask query, answer, and loading transitions in one ViewModel state object.
- Clear the displayed answer and its source evidence when the question content, case, or punctuation changes; whitespace-only edits keep the current answer.
- Clear the prior answer when a new submission starts and accept a completed answer only while its submitted question still matches the current query.
- Added focused state tests for edit, loading, successful submission, stale in-flight completion, and failure recovery transitions.
- Focused Ask state/composer tests and `:app:assembleDebug` passed with the Android Studio JBR.
- Strict workplace-privacy checking passed.
- The behavior was not physically exercised because no device or emulator was attached.

## 2026-07-15 - Small change 17: Make Review rows tappable

- Routed every Review item row through one item-selection callback, including due, capture, completed, carry-forward, stale-loop, Waiting For, and Someday rows.
- Reused Material 3 clickable surfaces for standard pressed feedback and button accessibility semantics.
- Kept summaries, headings, source evidence, suggestions, and empty messages non-interactive.
- Preserved existing reminder navigation; task and capture destination wiring remains scoped to small change 18.
- Added focused callback coverage for task, capture, and reminder rows.
- All 12 Review JVM tests and `:app:assembleDebug` passed with the Android Studio JBR.
- The strict privacy script could not execute because no Python interpreter is installed; all changed text was reviewed semantically.
- Press feedback and accessibility semantics were verified from the compiled Compose path and the cached Material 3 source; no device or emulator interaction was run.

## 2026-07-15 - Small change 18: Open the correct Review item

- Routed Review task rows to the existing task detail destination with the original task ID.
- Routed Review capture rows to the existing capture detail destination with the original capture ID.
- Preserved the existing reminder-specific detail destination and original reminder ID.
- Reused the current detail screens and navigation routes without changing Review layout, item data, or destination behavior.
- Added focused route coverage for task, capture, and reminder Review items.
- Focused Review navigation and row interaction tests passed, and `:app:assembleDebug` passed with the Android Studio JBR.
- Physical row tapping and Back navigation were not exercised because no device or emulator was attached.

## 2026-07-15 - Small change 19: Add carry-forward decisions

- Added explicit Tomorrow, Choose date, Keep unscheduled, and Mark complete decisions to overdue Evening Review items.
- Reused the existing task date-only scheduling helper and task completion transition.
- Routed reminder rescheduling and completion through the existing reminder repository so obsolete work is replaced or cancelled and notification offsets remain unchanged.
- Preserved each reminder's local clock time when moving it to Tomorrow or another chosen date; Keep unscheduled is task-only because reminders require a target time.
- Focused Review action, row interaction, and mode tests passed with the Android Studio JBR.
- `:app:assembleDebug` and `:app:lintDebug` passed; the debug APK remains local because external temporary upload was not authorized.
- The strict privacy script could not execute because no Python interpreter is installed; all changed text was reviewed semantically.
- Physical action tapping, date-picker interaction, and light/dark rendering were not exercised because no device or emulator was attached.

## 2026-07-16 - Small changes 20, 22, and 23: Clarify bottom navigation selection and accessibility

- Preserved the existing circular selected container so the active destination remains distinguishable without relying on color.
- Exposed Home, Spaces, Review, and Settings as labeled tabs with explicit selected state.
- Kept every destination target at least 48 dp and retained the larger, precisely labeled Situation AI button.
- Added focused Compose instrumentation coverage for tab roles, selected state, labels, and measured target sizes.
- Preserved the current destination structure, centered navigation geometry, focus restoration, and visual icon sizes.
- Production and instrumentation Kotlin compilation, the full JVM test suite, debug APK assembly, lint, and Android-test APK assembly passed with the Android Studio JBR.
- The focused Compose test was compile-verified but not physically executed because no attached device or emulator was available during the implementation pass.

## 2026-07-15 - Small changes 9 and 11: Pin and protect the Ask composer

- Moved the existing Ask LUMA composer below the single weighted Situation AI `LazyColumn`, keeping the fixed header above it and the body as the only scrolling region.
- Kept Ask answer text and source evidence in the scrolling body so long responses remain reachable without nested scrolling.
- A user-provided physical screenshot then confirmed the first inset pass still failed: focusing Ask LUMA moved the sheet downward and left the composer behind the keyboard.
- Removed the inner IME padding because Material 3 already applies platform IME padding at the modal root, retained navigation-bar padding inside the custom content, and disabled partial expansion so IME resizing cannot re-anchor the sheet downward.
- Preserved source callbacks, the 48 dp embedded send/loading control, shared IME Send path, validation, duplicate blocking, and stale-answer state handling.
- After the follow-up repair, nine focused Situation AI JVM tests, `:app:assembleDebug`, and `:app:lintDebug` passed; lint reported 0 errors and 46 warnings.
- The required strict workplace-privacy command was attempted with both `python` and the Windows launcher, but no Python runtime is installed; all changed text was reviewed semantically.
- No Android device or emulator was attached for the follow-up build, so the repaired keyboard-open layout still requires physical confirmation.

## 2026-07-15 - Small changes 36-38: Add the Home visible-month context

- Added a quiet localized month name directly above the Home weekday strip with 8 dp of separation.
- Added horizontal week swiping while preserving date taps and Calendar navigation.
- Hoisted and restored the visible-week anchor in `HomeWeekViewModel`; moving weeks now refreshes scheduled-item presence for the new date range.
- Used the middle visible day as the stable majority-month rule for weeks spanning two months.
- Added a short fade transition when the visible month changes and previous/next week accessibility actions for non-touch navigation.
- Focused Home week tests, the full `:app:test` suite, `:app:assembleDebug`, and `:app:lintDebug` passed with the Android Studio JBR.
- The strict workplace-privacy command was attempted, but no Python runtime is installed; all changed text was reviewed semantically.
- Physical swipe behavior and light/dark rendering were not exercised because no device or emulator was attached.

## 2026-07-15 - Small changes 36-38 visual follow-up: Balance the Home week header

- Replaced the undersized standalone month label with one full-width month/week header.
- Styled the localized month at 22 sp medium weight and the locale-aware `Week N` label at 13 sp regular weight with 66% secondary-text opacity.
- Baseline-aligned both labels, placed them at opposite edges of the weekday strip, and set 14 dp between the header and weekday names.
- Preserved the existing month/week fade, week swiping, date taps, selection styling, and capture layout.
- Added focused coverage for locale-aware week numbering; focused Home tests, `:app:assembleDebug`, and `:app:lintDebug` passed.
- Physical light/dark visual confirmation remains pending because no device or emulator was attached.

## 2026-07-15 - Small changes 45-46: Introduce shared design tokens

- Added a documented six-step spacing scale for repeated layout rhythm and four semantic shape roles for small, standard, prominent, and modal surfaces.
- Migrated only dimension-equivalent values in plan-touched Home, Situation AI, Spaces, Settings, bottom navigation, and source-row UI.
- Kept component-specific sizes local and documented circle and pill shapes as intentional geometry exceptions.
- Added focused token-value tests; the focused tests, full JVM suite, `:app:assembleDebug`, and `:app:lintDebug` passed with the Android Studio JBR.
- The strict workplace-privacy command was attempted with both available launchers, but no Python runtime is installed; all changed text was reviewed semantically.
- No device or emulator was attached, so light/dark rendering and surface geometry remain manual checks.
