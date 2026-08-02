# LUMA Hermes source audit — 2026-07-26

Audited on branch `currentsource` against the imported `LUMA_Hermes-source-2026-07-26-updated-1.zip` snapshot.

Scope: all 97 `app/src/main` Kotlin sources, Room schemas v1–v5, manifest, backup rules, resources, and the JVM test inventory.

Environment note: no Java/Gradle toolchain is available in this workspace, so the audit is static. `./gradlew :app:test :app:assembleDebug :app:lintDebug` must be re-run in a toolchain-equipped environment after any change.

## A. Missing features / dead code

### A1. Notification offset and per-reminder notification toggle are unreachable (dead code) — HIGH
- `ui/screens/review/ReminderDetailScreen.kt` and `ReminderDetailViewModel.kt` implement the full reminder editor: `reminderOffsetOptions`, `updateNotificationOffset`, `setNotificationEnabled`, reschedule, delete.
- Nothing references them. The `reminder/{id}` route in `ui/navigation/OrbitApp.kt` builds `ItemDetailViewModel`/`ItemDetailScreen`, which exposes no offset or notification controls.
- Consequence: `notificationOffsetMinutes` and `notificationEnabled` are persisted and honored by the scheduler, but a user can never change them from any reachable UI.
- Verified: `reminderOffsetLabel`/`reminderOffsetOptions`/`updateNotificationOffset`/`setNotificationEnabled` are referenced only by the dead screen.

### A2. "Reset Mode" from the MVP gate is not implemented — MEDIUM
- `docs/codex/mvp/LUMA_MVP_GATE.md` outcome 9 requires "use Ask LUMA, search, undo, Settings, export/restore, and Reset Mode without critical regression".
- The app offers only "Reset appearance" and "Clear AI learning data". All `deleteAll()` DAOs exist but no UI performs a full data reset, and `RoomCaptureRepository` is not `Resettable`.
- Decision needed: restore the feature or mark the gate wording obsolete.

### A3. monday.com integration was vestigial — REMOVED (see section D)

## B. Logic issues / bugs

### B1. Past-time reminders fire immediately instead of rolling forward — MEDIUM
- `ReminderTimeInterpreter`: "remind me at 9am" typed at 10am resolves to today 09:00 (already past).
- `shouldScheduleNotification()` requires only `notificationTime > 0`, not `> now`; `reminderDelayMillis` coerces to 0, so the notification fires instantly.
- After boot, `ReminderRescheduleWorker` re-schedules any reminder with `dueAt > now`, so a future reminder whose notification time already passed (e.g., due tomorrow with a 1-day offset) fires immediately on boot.
- Suggest rolling to the next occurrence when the resolved time is in the past.

### B2. Notification scheduling failures are silent — MEDIUM
- `RoomReminderRepository.insert/update` wrap `scheduler.schedule()` in `runCatching { }.getOrNull()`: a failed schedule stores a null work ID and the user is never told the reminder will not notify.
- Insert → schedule → update is not transactional; a crash between steps leaves an unscheduled reminder row.

### B3. Dual notification path (AlarmManager + WorkManager) — LOW
- `WorkManagerReminderScheduler.schedule()` arms both `setAndAllowWhileIdle` (→ `ReminderAlarmReceiver`) and a one-time WorkManager request at the same instant. Both call `showReminderNotification` with the same ID; the second replaces the first, so no visible duplicate, but it is redundant work and can re-alert on some devices. Pick one path.

### B4. Provider-suggested reminder times are not validated — LOW
- `GeminiJsonValidator.captureAnalysis` accepts any `dueAtEpochMillis` (no `> 0` check). When local analysis is `Unspecified`, a malformed Gemini time can flow into the picker and a reminder can be persisted with a non-positive `dueAt` (the Brain Dump path does `require(dueAt > 0)`, the capture path does not).

### B5. Restore keeps stale learned memory — LOW
- `RoomLocalDataRestoreStore.replace` replaces items but leaves `learned_rules`, `person_memory`, `project_memory` untouched; they can reference Spaces that no longer exist after restore. No reconciliation exists.

### B6. Space item counts include completed reminders — LOW
- `calculateSpaceItemCounts` counts all reminders regardless of `completedAt`, while notes/tasks exclude archived ones; badges can disagree with the visible list.

### B7. Conversion edge cases — LOW
- Task → Note/Reminder conversions drop `reminderAt`; Reminder → Task keeps `dueAt` (an overdue completed reminder becomes a Done task still dated in the past). `ItemTypeConversion` itself is sound (id-reuse guarded, transactional, scheduler reconciled).

## C. Hardening / localization

- C1. API key is passed in the URL query string (`?key=`); prefer the `x-goog-api-key` header to avoid key leakage into logs/proxies.
- C2. Hardcoded English user-facing copy remains in `HomeCaptureViewModel` ("Saved as a note.", "Task created.", "Reminder created.", "Kept in Inbox.", "Capture cancelled.", "Brain Dump cancelled.", "It's safe in your Inbox…", …), `ReminderDetailViewModel`, `LocalDataToolsViewModel`, and `LocalReviewAnalyzer` ("Local suggestion"). Backlog MVP-009.
- C3. `screenOrientation="portrait"` is forced; tablet/landscape use is not supported.
- C4. `LocalSearch` minimum query length is 2 and captures are intentionally excluded (finalized-only search per product rules).

## Verified-good (no action)

- Export codec validation (bounds, depth, FK/relationship checks, strict numerics, v1–3 defaults).
- Restore cascade order; alias/suggestion-history preservation; FK CASCADE cleanup.
- Capture-first persistence; `Mutex`-guarded finalization (no duplicate items); stale-notification guard; boot/package-replacement rescheduling; backup rules (cloud backup excluded, secure prefs excluded from transfer).
- Room schema v1→v5 matches entities exactly.

## D. monday.com removal (2026-07-26)

The monday.com integration was never live (`SituationAiViewModel` hardcoded `mondayConfigured = false`). All integration surface was removed:

Removed from `app/src/main`:
- `CaptureAnalysis.possibleMondayItem` and all producers (local analyzer, Gemini validator, prompt schema).
- `CaptureRulePack.mondaySignals` and the Monday keyword signals in the English/Estonian/Russian Space and Topic rules ("monday", "esmaspäev", "esmaspäeval", "понедельник", "в понедельник").
- `CaptureDecisionAction.SendMonday`, `mondayConfigured`, and `onSendToMonday` plumbing through `CaptureSuggestionSheet`, `HomeScreen`, `HomeCaptureViewModel`, `SituationAiViewModel`.
- `SuggestedItemType` mapping for "mondayitem"/"monday_item"/"monday" in the Gemini validator.
- `core_capture_action_send_to_monday` string in en/et/ru.

Removed from `app/src/test`:
- Monday-action tests in `CaptureSuggestionSheetTest`, monday analyzer tests, `possibleMondayItem` fixtures in `OrbitAiRouterTest`, `GeminiJsonValidatorTest`, `RecordAiLearningEventUseCaseTest`; "Monday board" sample text in `GeminiPromptBuildersTest`/`LocalLearningProfileProviderTest`.

Intentionally kept (data compatibility — removal would require a Room schema migration and would break reads of existing rows/exports):
- `CaptureSource.Monday` and `SuggestedItemType.MondayItem` enum values (with legacy comments); their exhaustive `when` cases and the `core_capture_monday_item` label.
- `TaskEntity.mondayItemId` column (Room v5 schema identity) and its codec/encode-decode + migration-test references.

Follow-up if the tokens must go: Room migration 5→6 (table rebuild for `tasks`, or `ALTER TABLE ... DROP COLUMN` where SQLite supports it) plus a decision on decoding legacy export values (`Monday` → `Manual`, `MondayItem` → `Note`/`Task`).

Historical references intentionally left in place: `CHANGELOG.md`, `PROGRESS.md`, `docs/archive/`, `docs/planning/`, `docs/codex/cleanup/LUMA_CLEANUP_INVENTORY.md` (records of past changes), and `DayOfWeek.MONDAY` usages (java.time weekday, not monday.com).

## Resolution status (2026-07-26, applied on `currentsource`)

| ID | Status | Notes |
|---|---|---|
| A1 | FIXED | Notification offset and enable/disable controls were added to the reminder item detail UI (`ItemDetailViewModel.updateNotificationOffset`/`setNotificationEnabled`, `NotificationSheet` in `ItemDetailScreen`); the dead `ReminderDetailScreen`/`ReminderDetailViewModel` were deleted. |
| A2 | FIXED | "Reset local data" added to Settings → Local data (`LocalDataToolsViewModel.resetAllData`): cancels scheduled notifications, wipes captures/notes/tasks/reminders/Brain Dumps/Spaces in a transaction, re-seeds starter Spaces, confirmation dialog. |
| B1 | NO CHANGE (deliberate) | `ReminderTimeInterpreterTest.anEarlierExplicitTodayTimeIsNotSilentlyRolledToTomorrow` locks in the no-roll behavior. Keep as-is. |
| B2 | FIXED | Capture-path reminder creation now reports a "notification scheduling needs attention" message when the scheduler returns no work ID. |
| B3 | DEFERRED | Needs device verification; documented only. |
| B4 | FIXED | Gemini-suggested `dueAtEpochMillis` must now be > 0. |
| B5 | DEFERRED | Needs product decision on learned-memory lifecycle. |
| B6 | FIXED | Space item counts now exclude completed reminders. |
| B7 | DEFERRED | Low value; documented only. |
| C1 | FIXED | API key moved from the URL query string to the `x-goog-api-key` header. |
| C2 | PARTIAL | Home capture and reminder-detail messages were extracted to `values/strings_localization.xml` (English); et/ru translations remain backlog MVP-009. Analysis model strings (e.g., "Brain Dump", reasons) intentionally unchanged. |
| C3 | NO CHANGE | Product choice (portrait lock). |
| C4 | NO CHANGE | Intentional (finalized-only search). |

## Priority order

1. A1 — restore reminder offset/notification editing (reconnect or fold into item detail).
2. A2 — decide Reset Mode.
3. B1 — past-time reminder rollover.
4. B2 — visible scheduling failures.
