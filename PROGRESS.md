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
