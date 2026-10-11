# Tallele: implementation plan

Oct 11, 2026 · @Vitautas

## Summary

Nine steps, about 70–95 hours for one developer who knows the code. The order matters: first make reminders unmissable, then give every thought one stored state, then build the receipt and the auto-filing rules on top of that state. Visual work comes last, because the review's "matte glass" problem turns out to be a one-line gate in `GlassSurface.kt`, not a design-system rebuild.

This plan was written against the real code on branch `ccr-eed43729-xsml4c` at commit `4d0a6ff` (PR #10 merged, 11 Oct 2026). That is newer than the commit the review looked at (`52dca54`), and several things the review flagged are already fixed on it: `ReminderSaveOutcome`, the boot receiver, reconcile on app start, `ToSortState.NeedsChoice`, Undo everywhere, Clear/Soft/Solid glass presets, and the Calendar "+" that opens the sort sheet.

| Step | What | Effort | Needs |
| --- | --- | --- | --- |
| 0 | Re-render screens from HEAD; confirm what is already fixed | S (2 h) | — |
| 1 | Reminders that always ring: `USE_EXACT_ALARM`, time-change receivers, battery check | M (6–8 h) | — |
| 2 | Reminder health row in Settings | M (5–7 h) | 1 |
| 3 | One stored outcome per thought (Room v11) | L (10–14 h) | — |
| 4 | Save receipt under the capture box | M (6–8 h) | 3 |
| 5 | Act / ask / keep: auto-file clear thoughts | L (14–18 h) | 3, 4 |
| 6 | Review becomes "Needs you" | M (8–12 h) | 3, 5 |
| 7 | New first run | M (5–7 h) | 4 |
| 8 | Glass and default look | M (6–10 h) | — |
| 9 | Small fixes | S each (8–12 h total) | — |

Steps 1, 3 and 8 do not depend on each other, so they can run in parallel if you have help. Everything else follows the numbers.

**Build facts the plan relies on:** `minSdk 26`, `targetSdk 36`, `compileSdk 36`, Kotlin 2.2.21, Compose BOM 2024.12.01, Room 2.7.1 (database version 10), WorkManager 2.10.0, Haze 1.7.2 already a dependency, Robolectric 4.16.1, no DI framework (a hand-written `OrbitContainer`). Application id `com.tallele.app`, package `com.orbit.app`.

## What changed after the GPT-6 Astra review

The review at the end of this doc made ten points. Nine are right and are now folded into the steps below; one is partly right. In short:

| # | Point | What changed in the plan |
| --- | --- | --- |
| 1 | Reminder rules contradicted the text | `decide()` rewritten: an explicit reminder with no time now **asks**; the reminder branch requires High confidence; a time is "passed" only when it is really in the past (Step 5a). |
| 2 | "Just keep it" must cancel a linked reminder | It now goes through `CaptureResolution.undo()`, which deletes the reminder and cancels its alarm and backup work (Step 5c). |
| 3 | "Change" must not delete first | "Change" opens the existing item in `ItemDetailScreen`, which already has type change with confirmation and Undo. Nothing is deleted until the user confirms (Step 5d). |
| 4 | Recovery must be durable | New `decidedAt` column; `AutoFiler.applyPending()` runs on every app start; duplicate filing is blocked by the existing `requireInboxCapture()` check; Undo stamps `decidedAt` so a late result cannot re-file (Step 3e, 5b). |
| 5 | Migration assumptions | Backfill joins `reminders.linkedCaptureId` instead of trusting `suggestedType`; `ReminderSet` never claims an alarm exists — the receipt reads `notificationWorkId` live; automatic filing records no "Accepted" event (Step 3a, 5b). |
| 6 | Android promises | Acceptance now says "after allowing notifications"; three separate recovery tests (process death, reboot, force-stop); battery optimisation is a soft hint on known-aggressive brands only and is never red; "Reminders will ring" became "Nothing is blocking reminders" (Steps 1, 2). |
| 7 | Receipt persistence | The last receipt's capture id lives in DataStore (`AppSettings`), not `SavedStateHandle`; the first-save tip has one text per outcome (Steps 4, 7). |
| 8 | UX definition | Needs you has two visibly different sections, "Needs a decision" and "Today"; saved thoughts are browsed in the existing Later tile and Search; the privacy line now mentions voice (Steps 6, 7). |
| 9 | Glass must be checked on a device | New 8f device checklist; screenshots and the contrast test are gates, not proof (Step 8). |
| 10 | Acceptance targets | Zero lost captures, zero false "Reminder set", zero duplicate rings; questions judged by necessity; misfilings counted separately (Step 5, Testing). |

**Where I only partly agree:** the review's order puts "settle behaviour rules" before state and recovery. The rules are a 120-line pure file (5a); write and unit-test that file first, during Step 3, but keep reminders (Step 1) first, because nothing else is worth building while a reminder can be an hour late on a fresh install. Effort stays 70–95 h; the recovery work (3e) adds about 4 h and the ItemDetail route for "Change" removes about 3 h.

## The code today

158 Kotlin files, about 37,000 lines, all under `app/src/main/java/com/orbit/app/`. The pieces each step touches:

| Area | File | Lines | What it does now |
| --- | --- | --- | --- |
| Capture | `domain/capture/CaptureInbox.kt` | 286 | `save()` stores the thought, then `analyze()` stores a suggestion in a transaction. Its own comment says: nothing here creates a note, task or reminder. |
| Capture | `ui/screens/home/HomeCaptureViewModel.kt` | 286 | `send()`, then `afterSaving()` decides: open sort sheet, ask for a time (`QuickReminderQuestion`), or nothing. `HomeMessage` is the short flash. |
| Capture | `ui/screens/home/HomeSendFeedback.kt` | 114 | `SavedConfirmation` ("Saved. You can let it go.", fades out) and `QuickReminderCard` — Home's one question. Both already use `liveRegion = Polite`. |
| Filing | `domain/capture/CaptureResolution.kt` | 136 | `acceptSuggestion(captureId)` turns a suggestion into a note, task or reminder and returns `AcceptedSuggestion` with a `ReminderSaveOutcome`. `undo()` reverses it. This is the "act" path; today only Review calls it. |
| Filing | `domain/usecase/ConfirmCaptureActionUseCase.kt` | 204 | `saveNote`, `createTask`, `createReminder`, `markProcessed`. Wrapped by `RoomCaptureFinalizationTransaction`. |
| Analysis | `domain/analyzer/CaptureAnalyzer.kt` | 783 | `CaptureAnalysis` with `suggestedType`, `confidence` (High ≥ 0.78, Medium ≥ 0.62), `reminderTimeStatus` (Unspecified / Resolved / NeedsClarification), `taskDateEpochDay`. |
| Analysis | `domain/analyzer/ReminderTimeInterpreter.kt` | 803 | Your own time reader for en, et and ru. |
| Analysis | `domain/capture/CaptureSuggestions.kt` | 65 | `needsImmediateTimeQuestion()` — the rule that already decides when Home asks. |
| Data | `data/local/entity/OrbitEntities.kt` | 500 | `CaptureEntity(status: Inbox/Processed/Archived, suggestedType, suggestedSpaceId, linkedItemId, contextDateEpochDay)`. `ReminderEntity(dueAt, notificationWorkId, deliveredNotificationAt, snoozedUntil, repeatRule)`. `CaptureSuggestionEntity` per capture. |
| Data | `data/local/OrbitDatabase.kt` | 498 | Version 10; `Migration9To10` is the pattern to copy. Schemas exported to `app/schemas/`. |
| Reminders | `reminders/WorkManagerReminderScheduler.kt` | 138 | `setExactAndAllowWhileIdle` when allowed, else `setAndAllowWhileIdle`, plus a WorkManager backup 2 min later. First path to deliver wins. |
| Reminders | `reminders/ReminderRescheduleWorker.kt` | 117 | `reconcileReminders()`: re-arms future ones, shows missed ones once (max 3, then a summary). Enqueued from `ReminderBootReceiver` and from `MainActivity.onCreate`. |
| Reminders | `reminders/ReminderBootReceiver.kt` | 18 | Handles `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`. Not time or time-zone changes. |
| Reminders | `reminders/ReminderCapabilities.kt` | 97 | `ReminderCapabilityStatus` → `Ready / MayBeLate / Blocked`. Checks notifications, channel, exact alarms. Not battery optimisation. |
| Reminders | `reminders/ReminderSaveOutcome.kt` | 50 | `Saved / SavedNotScheduled / SavedNotificationsBlocked`, computed after commit. |
| Review | `ui/screens/review/ReviewViewModel.kt` | 935 | `ReviewUiState` with `toSort`, `dueToday`, `carryForwardSuggestions`, `openLoops`, `staleLoops`, `waitingFor`, `someday`, `weeklySummary` and more. |
| Review | `ui/screens/review/ReviewToSort.kt` | 109 | `buildToSort()` derives `ToSortState` (Analyzing / Suggested / NeedsChoice / NoSuggestion / BrainDump) from capture + suggestion. |
| Review | `ui/screens/review/ReviewRedesign.kt` | 919 | `ReviewHeader` (Ask + Breathe), `ToSortGroup`, `TodayGroup`, `FromEarlierGroup`, `WeeklyLookBackRow`, `SortOneByOne`. |
| Glass | `ui/components/GlassSurface.kt` | 508 | `LiveGlassSurface` draws real Haze blur **only when `style == Prominent`**. Every real screen uses `SoftGlassSurface` (14 files). `LiveGlassSurface` is called from one place: `GlassRolePreview.kt`, the Settings preview. |
| Glass | `ui/components/OrbitBackground.kt` | 410 | Puts `hazeSource(hazeState)` on the background, so blur is possible everywhere; nothing reads it. |
| Settings | `ui/screens/settings/CaptureReminderSettings.kt` | 161 | The "red warning": `MaterialTheme.colorScheme.error` when `Blocked`, buttons "Turn on" / "Allow". |
| Settings | `domain/model/AppSettings.kt` | 149 | `glassEffect` (Strong/Soft/Off, default Strong), `backgroundPreset` (default `InkPaper`), `sortRightAfterSaving` (default false), `hasCompletedFirstTimeTutorial`. |
| First run | `ui/navigation/OrbitApp.kt` | 857 | `initialOrbitRoute(settings)` sends new users to `FirstTimeTutorialDestination`; 4 pages in `tutorial/FirstTimeTutorialContent.kt`. |
| Voice | `capture/VoiceCapture.kt` | — | Uses the system intent with `EXTRA_PREFER_OFFLINE = true` (a preference only). |
| Search | `ui/screens/search/SearchScreen.kt` | — | `query.trim().length < 2 -> MinimumQuery`. `SearchViewModel` already debounces. |
| Tests | `app/src/test/…` | 117 files | `reminders/` has 6 test files incl. `ReminderSchedulingTest`, `WorkManagerReminderSchedulerTest`, `ReminderDeliveryRoomTest`. `data/local/OrbitDatabaseMigrationJvmTest.kt`. `screens/ScreenTour.kt` renders 40 steps at 411×891 dp, light and dark. |
| CI | `.github/workflows/` | 4 | `android-verify.yml`, `android-release.yml`, `screens.yml` (publishes the `screens/*` branch), `package-source.yml`. |

**Manifest today** (`app/src/main/AndroidManifest.xml`): `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, `SCHEDULE_EXACT_ALARM`, `INTERNET`. Receivers: `ReminderBootReceiver`, `ReminderAlarmReceiver`, `ReminderActionReceiver`, all not exported. A Quick Settings tile `NewThoughtTileService`.

**Two findings that change the plan**

1. **The glass is matte because live blur is switched off for real screens.** `GlassSurface.kt` line 69: `val liveGlassEnabled = style == GlassSurfaceStyle.Prominent && …`. The capture box uses `HomeCapture`, the bar uses `NavigationBar`, sheets use `Sheet`. None of them is `Prominent`, so they all fall through to `SoftGlassSurface`. The Settings preview is the only place a user ever sees blur. Step 8 is mostly widening that gate.
2. **The "act" path already exists.** `CaptureResolution.acceptSuggestion()` does exactly what auto-filing needs, with Undo. Step 5 is about *calling it automatically* under clear rules, not writing new filing code.

## Step 0 — Re-render screens and confirm what is already fixed (S, about 2 h)

**Goal:** make sure the next review (or you) looks at the current build, and strike out review items that are already done.

1. Run the `screens.yml` workflow on `ccr-eed43729-xsml4c` at `4d0a6ff` so the `screens/` branch matches HEAD.
2. Run `./gradlew :app:testDebugUnitTest :app:lintDebug` and keep the output as a baseline (the repo reports 573 JVM tests).
3. Tick these off from the review, since the code already has them: `ReminderSaveOutcome` (never says "Reminder set" while notifications are off), boot and update receiver, reconcile on every app open (`MainActivity.onCreate`), `ToSortState.NeedsChoice`, Undo on Review actions, Clear/Soft/Solid presets, Calendar "+" to sort sheet, `tallele-export.json`.
4. Fix the seed row now, it is one line: `app/src/test/java/com/orbit/app/screens/ScreenTour.kt` line 185 — `"Prepare slides for Monday"` is due `today.plusDays(3)`. Change to the next Monday: `today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))`.

**Check:** the re-rendered `light-a-space-detail.png` shows the slides task on a Monday.

## Step 1 — Reminders that always ring (M, about 6–8 h)

**Goal:** a reminder saved on a fresh install rings at the right minute without the user granting anything, and survives reboot, app update, time change and time-zone change.

**Why it is still needed:** the manifest asks for `SCHEDULE_EXACT_ALARM`, which Android 14+ denies by default on new installs. Until the user finds "Alarms & reminders" in system settings, `WorkManagerReminderScheduler.scheduleAlarm()` falls to `setAndAllowWhileIdle`, which may be up to an hour late. The code handles this honestly (`MayBeLate`), but honest-and-late is not what the app promises.

### 1a. Manifest

File: `app/src/main/AndroidManifest.xml`

```xml
<!-- Android 13+: granted at install, cannot be revoked. Private build only; see Things not to do.
     Keep SCHEDULE_EXACT_ALARM below it unchanged: it covers Android 12 and 12L, and it is the
     line that stays if USE_EXACT_ALARM ever has to come out for a Play release. -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<!-- Lets the health row open the battery screen for this app (Step 2). -->
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
```

Add two actions to the existing `ReminderBootReceiver` entry:

```xml
<action android:name="android.intent.action.TIMEZONE_CHANGED" />
<action android:name="android.intent.action.TIME_SET" />
```

No code change is needed in `ReminderCapabilities.canScheduleExactAlarms()`: with `USE_EXACT_ALARM` present, `AlarmManager.canScheduleExactAlarms()` returns true on 33+, so the existing `exact` branch runs.

### 1b. Receiver

File: `reminders/ReminderBootReceiver.kt` — add `Intent.ACTION_TIMEZONE_CHANGED` and `Intent.ACTION_TIME_CHANGED` to the `when`. Both call `ReminderRescheduleWorker.enqueue(context)`, which already merges repeated requests (`ExistingWorkPolicy.REPLACE`).

What the reconcile does for each case: one-off reminders store `dueAt` as an instant, so a time-zone change does not move them (that is correct). Repeating reminders (`ReminderRepeat.kt`, `repeatRule`) compute the next ring in local time, so they do need re-arming. A clock moved forward makes some reminders "missed"; the existing `ReminderDeliveryPolicy.reconcile()` shows those once.

`TIME_SET` may not reach a manifest receiver on every Android version. That is fine: the app also reconciles on every `MainActivity.onCreate`, and the WorkManager backup runs on elapsed time.

### 1c. Battery check (data only; the UI is Step 2)

File: `reminders/ReminderCapabilities.kt`

- Add to `ReminderCapabilityStatus`: `val batteryRestricted: Boolean` (from `PowerManager.isIgnoringBatteryOptimizations(packageName)` negated) and `val aggressiveBatteryBrand: Boolean` (`Build.MANUFACTURER.lowercase()` in samsung, xiaomi, huawei, oneplus, oppo, vivo, realme).
- Add `ReminderCapabilityState.MayBeStopped`, used only when `batteryRestricted && aggressiveBatteryBrand`. Order: `!canNotify -> Blocked; !exactAlarmsAllowed -> MayBeLate; batteryRestricted && aggressiveBatteryBrand -> MayBeStopped; else Ready`. Plain battery optimisation on other phones is not a problem: `setExactAndAllowWhileIdle` fires through Doze, so it stays `Ready`.
- Add intents: `batteryOptimizationIntent(context)` = `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with `package:` data; `samsungNeverSleepingIntent()` = action `com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY`, package `com.samsung.android.lool`, extra `activity_type = 2`. Resolve it with `packageManager.resolveActivity` before offering it; fall back to the app's own battery page.

### 1d. Tests

- `app/src/test/java/com/orbit/app/reminders/WorkManagerReminderSchedulerTest.kt`: add a case where `canScheduleExactAlarms` is true on API 34 and assert `ShadowAlarmManager.peekNextScheduledAlarm()` is exact `RTC_WAKEUP`.
- `ReminderSchedulingTest.kt`: add "after `TIMEZONE_CHANGED` a daily 09:00 repeat is re-armed at 09:00 in the new zone" and "a clock moved forward past a reminder delivers it once as missed". Both go through `reconcileReminders()` with a fake `now`.
- New `ReminderBootReceiverTest.kt`: send each of the five actions through Robolectric and assert one unique work request named `luma_reschedule_reminders` exists.
- Three separate recovery tests in `ReminderDeliveryRoomTest.kt`, because they fail differently: (a) **process death** — alarms survive, so after re-creating the database layer nothing is re-armed and nothing is duplicated; (b) **reboot** — `ShadowAlarmManager` is cleared, then `BOOT_COMPLETED` re-arms exactly one alarm per future undelivered reminder; (c) **force-stop** — alarms are cancelled and no receiver fires until the next launch, so `MainActivity.onCreate`'s reconcile re-arms them and shows at most one missed notification per reminder.

### Acceptance

- Fresh install on an Android 14+ phone. Allow notifications when the app asks (Step 7 asks on the first reminder; `USE_EXACT_ALARM` does not cover notifications). Save "call dentist tomorrow 9:00": `adb shell dumpsys alarm | grep com.tallele.app` shows an `RTC_WAKEUP` alarm with the exact minute, and Settings never shows "may be late".
- Reboot before the time: it still rings within one minute of 09:00, exactly once.
- Force-stop the app before the time, then open it once: it rings within one minute of 09:00, exactly once.

### Risks

- If Tallele ever goes to Google Play, `USE_EXACT_ALARM` must come out unless the app qualifies as an alarm or calendar app. Keep `SCHEDULE_EXACT_ALARM` + the health row as the Play path.
- Samsung's deeplink is from Samsung's own developer guide but untested on One UI 7/8; the fallback is the normal battery page plus a two-line instruction.

## Step 2 — Reminder health row (M, about 5–7 h; needs Step 1)

**Goal:** one row in Settings › Capture & reminders that is calm when all is well and shows exactly one fix button when it is not. The red warning goes away unless a reminder is due soon and cannot ring.

### Files

| File | Change |
| --- | --- |
| `ui/screens/settings/CaptureReminderSettings.kt` | Replace the current title + message + button block (lines \~49–90) with `ReminderHealthRow(status, nextDueWithin24h)`. One line of text, one `RowActionButton` or none. |
| `reminders/ReminderCapabilities.kt` | Uses the `MayBeStopped` state and intents from Step 1c. Add `fixIntent(context, state): Intent?` that returns the right screen per state. |
| `data/local/dao/OrbitDaos.kt` (`ReminderDao`) | Add `suspend fun nextUndeliveredNotificationAt(now: Long): Long?` — the earliest `notificationTime` in the future that has not been delivered. Used to decide red vs grey. |
| `ui/screens/settings/SettingsHome.kt` | The Capture & reminders entry shows a small dot only when state is not `Ready`. No red text on the overview page. |
| `res/values/strings_screens.xml` (+ `values-et`, `values-ru`) | New strings, see Wording. |

### Behaviour

| State | Row text | Button | Opens |
| --- | --- | --- | --- |
| Ready | Nothing is blocking reminders. | — | — |
| Blocked (notifications off) | Notifications are off, so reminders can't ring. | Turn on | `ACTION_APP_NOTIFICATION_SETTINGS` (exists) |
| Blocked (channel muted) | The Reminders sound is off. | Turn on | `ACTION_CHANNEL_NOTIFICATION_SETTINGS` for `luma_reminders` |
| MayBeLate (API 31–32 only) | Reminders may be a few minutes late. | Allow | `exactAlarmSettingsIntent` (exists) |
| MayBeStopped (restricted battery on Samsung, Xiaomi, Huawei, OnePlus, Oppo, Vivo, Realme) | This phone sometimes stops apps in the background. If a reminder is ever missed, this is the first thing to check. | Check | Samsung deeplink if it resolves, else `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

The Ready text is a capability statement, not a promise: it means no permission, channel or exact-alarm block exists right now. The app cannot see what an OEM battery manager will do later, so it does not claim to.

Colour rule: text is `onSurfaceVariant` always, except `error` when the state is **Blocked** and `nextUndeliveredNotificationAt` is within 24 hours. That is the only time red appears in Settings. `MayBeLate` and `MayBeStopped` are never red — they are hints, not failures.

Re-check on resume: `CaptureReminderSettings` already reads `ReminderCapabilities.status()`; wrap it in `LifecycleResumeEffect` so coming back from system settings refreshes the row.

### Tests

- Pure mapping test `ReminderCapabilityStateTest`: every combination of the four flags → state and button label.
- `ScreenTour.kt`: two new shots, `settings-reminders-ok` and `settings-reminders-blocked` (use Robolectric's `ShadowNotificationManager.setNotificationsEnabled(false)`).

### Acceptance

Turn notifications off in Android, return to Tallele: the row changes before you scroll. Tap Turn on, allow, return: it says "Reminders will ring." with no button.

## Step 3 — One stored outcome per thought (L, about 10–14 h)

**Goal:** every capture row carries one honest answer to "what did Tallele do with this?", written in the same transaction as the thing that happened, so the receipt, Home, Needs you and search all read the same truth and it survives process death.

Today that answer is scattered: `CaptureEntity.status` (Inbox/Processed/Archived), `linkedItemId`, the suggestion row, `ToSortState` derived at display time in `buildToSort()`, and `ReminderSaveOutcome` returned once and then forgotten.

### 3a. Data model

File: `data/local/entity/OrbitEntities.kt`

```kotlin
enum class CaptureOutcome { Saved, Filed, ReminderSet, NeedsYou }

enum class CaptureOutcomeReason {
    None, TimeUnclear, TypeUnclear, NotificationsOff, NotScheduled, TimePassed
}

// Added to CaptureEntity, after contextDateEpochDay:
val outcome: CaptureOutcome = CaptureOutcome.Saved,
val outcomeReason: CaptureOutcomeReason = CaptureOutcomeReason.None,
val outcomeAt: Long? = null,
/** When act/ask/keep was applied to this thought, or the user touched it. Null = still pending. */
val decidedAt: Long? = null,
```

`data/local/OrbitTypeConverters.kt`: add converters for both enums (store the name as TEXT, like `CaptureStatus`).

File: `data/local/OrbitDatabase.kt` — version 11, and copy the `Migration9To10` pattern:

```kotlin
val Migration10To11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `captures` ADD COLUMN `outcome` TEXT NOT NULL DEFAULT 'Saved'")
        db.execSQL("ALTER TABLE `captures` ADD COLUMN `outcomeReason` TEXT NOT NULL DEFAULT 'None'")
        db.execSQL("ALTER TABLE `captures` ADD COLUMN `outcomeAt` INTEGER")
        db.execSQL("ALTER TABLE `captures` ADD COLUMN `decidedAt` INTEGER")
        // Backfill from what really exists, not from suggestedType.
        // A reminder that points back at the capture and is still open -> ReminderSet.
        db.execSQL(
            "UPDATE captures SET outcome='ReminderSet', outcomeAt=updatedAt, decidedAt=updatedAt " +
            "WHERE status='Processed' AND id IN " +
            "(SELECT linkedCaptureId FROM reminders WHERE linkedCaptureId IS NOT NULL AND completedAt IS NULL)"
        )
        // Any other processed capture with a linked item -> Filed.
        db.execSQL(
            "UPDATE captures SET outcome='Filed', outcomeAt=updatedAt, decidedAt=updatedAt " +
            "WHERE status='Processed' AND linkedItemId IS NOT NULL AND outcome='Saved'"
        )
        // Everything still in the Inbox stays 'Saved' with decidedAt NULL, so AutoFiler
        // looks at it once on the next start. Old thoughts never become 'NeedsYou' here.
    }
}
```

`ReminderSet` here means "a reminder row exists and is open", nothing more. Whether its alarm is armed is a live question answered by `notificationWorkId` and `ReminderRescheduleWorker`, which runs on the first app start after the migration anyway. The receipt and the Needs you card read `notificationWorkId` at display time; they never trust `ReminderSet` alone.

Add it to `AllMigrations`, export the schema (`app/schemas/.../11.json`), and extend `app/src/test/java/com/orbit/app/data/local/OrbitDatabaseMigrationJvmTest.kt` with 10→11 and the full 1→11 chain.

### 3b. Where each outcome is written

| Event | Code | Sets |
| --- | --- | --- |
| Thought saved | `CaptureInbox.save()` | `Saved`, reason `None` (default) |
| Auto-filed or user-accepted as note or task | `ConfirmCaptureActionUseCase.markProcessed()` (called by `saveNote`, `createTask`) | `Filed` |
| Reminder created and scheduled | `ConfirmCaptureActionUseCase.createReminder()` then `CaptureResolution` reads `ReminderSaveOutcome.Saved` | `ReminderSet` |
| Reminder created but notifications off | same, outcome `SavedNotificationsBlocked` | `NeedsYou`, `NotificationsOff` |
| Reminder created but could not be armed | same, outcome `SavedNotScheduled` | `NeedsYou`, `NotScheduled` |
| Analysis says reminder but time unclear | Step 5 decision `Ask` in `CaptureInbox.analyze()` | `NeedsYou`, `TimeUnclear` |
| Analysis is low confidence with a reminder cue | Step 5 decision `Ask` | `NeedsYou`, `TypeUnclear` |
| Analysis fails or is plain note/idea with nothing to do | `AnalysisFailed` event, or decision `Keep` | stays `Saved` |
| User taps Undo on a filed item | `CaptureResolution.undo()` | back to `Saved` |
| User archives | `CaptureResolution.archive()` | unchanged (status Archived hides it) |
| Reminder's time passed without delivery (phone off > 15 min) | `ReminderDeliveryPolicy.reconcile()` → `DeliverNow(missed=true)` | `NeedsYou`, `TimePassed` on the linked capture |
| Health check passes again | `ReminderRescheduleWorker` after re-arm | `NeedsYou/NotificationsOff` → `ReminderSet` |

&#91;embedded content: capture outcomes · 4 states, 1 decision\]

The only road into "Needs you" is a real question or a reminder that cannot ring. A plain idea never goes there; it stays Saved and findable.

All writes go through `CaptureRepository.update()` inside the existing `RoomCaptureFinalizationTransaction`, so a late analysis result can never move a `Filed` row back to `Saved` (this extends the race test already in `CaptureInboxRoomTest`).

### 3e. Recovery: a database write and an Android alarm are never one transaction

The database commits first, then the alarm is armed, then `notificationWorkId` is stored (`RoomReminderRepository.scheduleStored()` already works this way). The app can die between any two of those. The rules that make this safe:

1. **The database is the truth; alarms are copies.** `ReminderRescheduleWorker.reconcileReminders()` already rebuilds alarms from rows on boot, update, permission change and app start. Step 1 adds time changes. Nothing else needs to remember to re-arm.
2. **A pending decision is a row, not a coroutine.** `decidedAt == null` on an Inbox capture with a stored suggestion means act/ask/keep has not run. `AutoFiler.applyPending()` runs on every app start (from `MainActivity.onCreate`, next to `analyzePending()`) and whenever an `Analyzed` event arrives.
3. **Duplicates are impossible by construction.** `ConfirmCaptureActionUseCase.requireInboxCapture()` throws if the capture is already `Processed`, and it runs inside `RoomCaptureFinalizationTransaction`. Two `applyPending()` runs racing for the same row produce one item; the loser gets an exception that `AutoFiler` catches and ignores.
4. **The user always wins over a late result.** Undo, Change, Just keep it and archive all stamp `decidedAt = now`. `applyPending()` only touches rows where `decidedAt IS NULL`, and `CaptureInbox.store()` already refuses to write a suggestion over a capture that left the Inbox.
5. **Retries are bounded.** `ReminderRescheduleWorker` returns `Result.retry()` on failures (exists); `AutoFiler` does not retry at all — the next app start is the retry.

Test for this (new `AutoFilerRecoveryTest`, Robolectric): save a thought, let analysis store the suggestion, then stop before `applyPending()`; recreate the container; assert exactly one note exists and `decidedAt` is set. Then Undo, recreate again, assert still one capture and zero notes.

### 3c. Backup format

`data/export/` writes and reads a JSON format (version 6 after PR #8). Bump to 7, write the three new fields, and make the reader default them to `Saved / None / null` for formats 1–6. Add a round-trip case to the existing export tests.

### 3d. Keep `buildToSort()` working

`ui/screens/review/ReviewToSort.kt` still derives `ToSortState` for display. Do not delete it in this step; Step 6 replaces its consumers. Just make `ToSortItem` carry `outcome` and `outcomeReason` so the new screens can filter on them.

### Tests

- Migration tests as above.
- `CaptureInboxRoomTest`: after `save()`, outcome is `Saved`; after `acceptSuggestion()`, it is `Filed`/`ReminderSet`; after `undo()`, `Saved` again; a late `store()` after `undo()` leaves `Saved`.
- `ReminderOutcomeEntryPointsTest`: each of the three `ReminderSaveOutcome` values maps to the right `CaptureOutcome` + reason.

### Acceptance

Kill the app (`adb shell am force-stop com.tallele.app`) right after saving a reminder; reopen; the capture row in `adb shell` SQLite shows `ReminderSet` and the item detail shows the same.

## Step 4 — The save receipt (M, about 6–8 h; needs Step 3)

**Goal:** under the capture box, one quiet line says what happened to the last thought, with one button. It stays until the next capture or until the user swipes it away, and it is still right after the app is killed.

Today `SavedConfirmation` in `ui/screens/home/HomeSendFeedback.kt` shows "Saved. You can let it go." and fades out, and `HomeMessage` flashes reminder outcomes once. Both are events. The receipt is state.

### Files

| File | Change |
| --- | --- |
| `domain/model/AppSettings.kt` + `data/repository/AppSettingsRepository.kt` | Two new DataStore fields: `lastReceiptCaptureId: Long?` and `lastReceiptDismissed: Boolean`. DataStore survives process death, force-stop, reboot and task removal; `SavedStateHandle` does not, so it is not used for this. |
| `ui/screens/home/HomeCaptureViewModel.kt` | In `send()`'s `onSaved`, write `lastReceiptCaptureId = id, lastReceiptDismissed = false`. Expose `receipt: StateFlow<CaptureReceipt?>` = `appSettingsRepository.settings` → `captureRepository.observeById(id)` → combined with the linked `ReminderEntity` (for `notificationWorkId` and `dueAt`) when `outcome == ReminderSet`. Keep `HomeMessage.SaveFailed` as the only remaining flash. |
| `ui/screens/home/CaptureReceipt.kt` (new, \~70 lines) | `data class CaptureReceipt(captureId, outcome, reason, title, spaceName, itemType, itemId, reminderAt, reminderArmed: Boolean)` and `fun CaptureReceipt.line(timeFormat): String` + `action(): ReceiptAction?` (`View`, `Change`, `ChooseTime`, `TurnOn`, `TryAgain`). `reminderArmed` is `notificationWorkId != null`; when false, the line says "Saved, but the reminder isn't set yet" even if the outcome row says `ReminderSet`. |
| `ui/screens/home/HomeSendFeedback.kt` | Replace `SavedConfirmation` with `SaveReceipt(receipt, timeFormat, onView, onChange, onChooseTime, onTurnOn, onTryAgain, onDismiss)`. Same `SoftGlassSurface` family as `QuickReminderCard`, same `liveRegion = Polite`. Swipe-to-dismiss with `SwipeToDismissBox`; dismiss writes `lastReceiptDismissed = true`. |
| `ui/screens/home/HomeScreen.kt` | Mount `SaveReceipt` where `SavedConfirmation` was. Wire: View → `ItemDetailDestination` for the linked item, or the capture row when still `Saved`; Change → `ItemDetailDestination` for the linked item (see Step 5d); Choose time → `SortRequest(captureId, startWithReminderSetup = true)`; Turn on → `ReminderCapabilities.notificationSettingsIntent`. |
| `data/repository/EntityRepositories.kt` (`CaptureRepository`) | Add `fun observeById(id: Long): Flow<CaptureEntity?>` (the DAO query is one line in `OrbitDaos.kt`). |
| `res/values/strings_core_ui.xml` (+ et, ru) | New receipt strings, see Wording. Retire `core_home_saved_let_go` only after Step 7 (the tutorial still quotes it). |

### Behaviour

- Appears 0–200 ms after send with "Saved." (outcome `Saved`), then updates in place when analysis lands (`Filed`, `ReminderSet`, `NeedsYou`). No second animation on update — text swaps, the surface does not move. With reduced motion, no animation at all (`ReducedMotion.kt` already provides the flag).
- Hides on the next `send()`, on dismiss, and when the user opens the item. It does not time out.
- `QuickReminderCard` keeps its job for the `Ask` case and sits *in place of* the receipt (never both).
- TalkBack: polite region, so the update is read after current speech. Do not put the time in a separate node; one sentence.
- If `send()` throws, the text stays in the box and `HomeMessage.SaveFailed` shows in `error` colour. That path is unchanged.

### Tests

- `HomeCaptureViewModelTest` (new, next to `HomeAfterSavingTest.kt`): after `send()`, `receipt` emits `Saved`; after a fake `Analyzed` + auto-file, it emits `Filed` with the Space name; a reminder with `notificationWorkId == null` renders the "isn't set yet" line, not "Reminder set".
- Robolectric: save, then recreate the whole `OrbitContainer` (simulates force-stop); the receipt comes back from DataStore + Room with the same text.
- Compose JVM test `SaveReceiptTest`: each of the seven receipt lines renders with the correct single button.
- `ScreenTour.kt`: shots `home-receipt-filed`, `home-receipt-reminder`, `home-receipt-needs-you`.

### Acceptance

Save "Parking code 4826", press Home, kill the app, reopen: the receipt still reads "Saved as a note · View" and View opens it.

## Step 5 — Act / ask / keep (L, about 14–18 h; needs Steps 3–4)

**Goal:** a clear thought is filed by Tallele without a question. Only a reminder with an unclear time, or a real tie, becomes a question. Everything else is kept as a saved note, with no label and no badge.

Today `CaptureInbox.analyze()` stores a suggestion and stops. The user accepts it in Review. `afterSaving()` in `HomeCaptureViewModel` only decides whether to open the sort sheet or ask for a time.

### 5a. The decision

New file: `domain/capture/CaptureDecision.kt` (\~120 lines, pure, no Android)

```kotlin
sealed interface CaptureDecision {
    data class Act(val type: SuggestedItemType) : CaptureDecision
    data class Ask(val reason: CaptureOutcomeReason) : CaptureDecision
    data object Keep : CaptureDecision
}

fun decide(analysis: CaptureAnalysis, hasSpace: Boolean, now: Long): CaptureDecision {
    if (analysis.analyzerFailed || analysis.brainDumpItems.isNotEmpty()) return Keep
    val high = analysis.confidenceLevel == CaptureConfidence.High
    return when (analysis.suggestedType) {
        Reminder -> when {
            !high -> Keep                                   // not sure it is a reminder at all: keep, no question
            analysis.reminderTimeStatus == Resolved -> {
                val at = analysis.suggestedReminderAt ?: return Ask(TimeUnclear)
                if (at > now) Act(Reminder) else Ask(TimePassed)   // "in 1 minute" is still the future
            }
            else -> Ask(TimeUnclear)                        // explicit reminder, no usable time: always ask
        }
        Task -> if (high) Act(Task) else Keep
        Note -> if (high && hasSpace) Act(Note) else Keep
        else -> Keep
    }
}
```

Rules in words: act only on High confidence, for every type. A reminder with a clear future time is set; a reminder the analyzer is sure about but whose time is missing, unclear or already past is always a question (TimeUnclear or TimePassed); a weak reminder guess is kept quietly. A note is filed only if a Space was found; otherwise it is kept, not asked about. Low confidence never asks. `ReminderDeliveryPolicy.CreationGraceMillis` (60 s) still lets a reminder set for "now" ring once it is created.

### 5b. Applying it

New file: `domain/capture/AutoFiler.kt` (\~90 lines). Started once in `OrbitContainer` on `applicationScope`; collects `captureInbox.events`:

- `Analyzed(captureId, analysis)` → `decide(...)`:
  - `Act` → `captureResolution.acceptSuggestion(captureId)` (exists; sets `Filed`/`ReminderSet` via Step 3), then `decidedAt = now`. **No learning event is recorded**: an automatic filing is not the user agreeing with anything. `RecordAiLearningEventUseCase` is called only when the user later changes it (`Corrected`) or keeps it (`Accepted`, from the item screen after an explicit edit-and-save).
  - `Ask` → `captureRepository.update(outcome = NeedsYou, reason, decidedAt = now)`.
  - `Keep` → `decidedAt = now`, outcome stays `Saved`.
- `AnalysisFailed` → nothing; `decidedAt` stays null and `analyzePending()` retries on the next start.
- `applyPending()` on app start: every Inbox capture with a non-dismissed suggestion and `decidedAt IS NULL` goes through the same `decide()`. This is the durable path (Step 3e).

Why a separate listener and not inside `CaptureInbox`: `CaptureInbox` must stay free of `CaptureResolution` (which depends on `ConfirmCaptureActionUseCase`, which depends on the inbox's repositories) — a cycle. The listener pattern also lets `analyzePending()` on app start auto-file thoughts saved before a force-stop.

`HomeCaptureViewModel.afterSaving()`: remove the `AskForTime` branch's own logic and read the stored `NeedsYou/TimeUnclear` outcome instead; `QuickReminderCard` shows when the receipt's outcome is `NeedsYou` and reason is `TimeUnclear` or `TimePassed`. `sortRightAfterSaving` stays as a setting but moves under Advanced in `CaptureReminderSettings.kt`; when on, `AutoFiler` does nothing and the sheet opens as today.

### 5c. One question only

File: `ui/screens/home/HomeSendFeedback.kt` — `QuickReminderCard`'s "Not now" becomes "Just keep it". What it does depends on what exists: if the capture has no linked item (TimeUnclear, TimePassed, TypeUnclear), set outcome `Saved`, reason `None`, `decidedAt = now`. If a reminder row already exists (NotificationsOff, NotScheduled), call `CaptureResolution.undo(captureId, Reminder, linkedItemId)` first — `RoomReminderRepository.deleteById()` cancels the alarm and the WorkManager backup — then set `Saved`. Either way the thought stays in Later and in Search, and nothing can ring for it.

### 5d. Undo window

"Change" does **not** delete anything first. It opens the linked item in `ItemDetailScreen`, which already supports editing, a Space change, and a type change with a confirmation dialog and Undo (`ItemDetailViewModel.changeType` / `undoTypeChange`). Cancelling leaves the item and its schedule exactly as they were. `CaptureSortViewModel.kt` line 182 refuses processed captures, so the sort sheet is used only for thoughts that are still `Saved` (via "Choose time").

### 5e. Optional: ML Kit Entity Extraction as a second opinion

Not in this step. Your own `ReminderTimeInterpreter.kt` covers en/et/ru and ML Kit does not cover Estonian. Add it later only if English dates are often missed; it adds about 5.6 MB.

### Tests

- `CaptureDecisionTest` (new, table-driven): at least 60 rows across en/et/ru. Include: "Buy milk tomorrow" → Act(Task); "remind me tomorrow" → Ask(TimeUnclear); "remind me about the car" (no time at all) → Ask(TimeUnclear); "call mum Tue 9:00" → Act(Reminder); "remind me in 1 minute" → Act(Reminder), not TimePassed; a thought with yesterday's time → Ask(TimePassed); "maybe learn photography" → Keep; "parking code 4826" → Keep; any Low-confidence row → Keep.
- `AutoFilerRoomTest` (new, copy `CaptureInboxRoomTest` setup): Act creates the note and sets `Filed` + `decidedAt`; Undo restores `Saved` and deletes the note; a late `Analyzed` for an already-undone capture does nothing; "Just keep it" on a `NotificationsOff` capture deletes the reminder row and `ShadowAlarmManager` has no alarm for it.
- `AutoFilerRecoveryTest` (Step 3e).
- Existing `CaptureInboxRoomTest` and `ReviewChangeUndoRoomTest` must still pass unchanged.

### Acceptance

Capture 20 ordinary thoughts from your day. Hard rules: zero thoughts lost, zero receipts saying "Reminder set" without an armed alarm, zero duplicate rings. Then count two things separately: questions that were **not** needed (the right number is 0, however many questions there were), and misfilings (wrong Space or type — track the number, fix the rule or the Space keywords, re-run).

### Risks

- Over-filing: a note filed into the wrong Space. Receipt + Change + Undo make it one tap to fix; the learning history records corrections.
- Under-filing: too much stays `Saved`. That is the safe failure; raise the Medium threshold later with data from `AiSuggestionHistoryEntity`.

## Step 6 — Review becomes "Needs you" (M, about 8–12 h; needs Steps 3 and 5)

**Goal:** the fourth tab shows only thoughts that really need a decision, one question per card, and is honestly empty most days. Today's plan and the weekly look-back stay, but quieter and lower.

Today `ReviewUiState` holds 14 fields and `ReviewRedesign.kt` renders To sort, Today, From earlier, Weekly look-back, Sort one by one, plus Ask and Breathe in the header. The `toSort` list includes every Inbox capture — after Step 5 most of those are plain `Saved` notes, not questions.

### Files

| File | Change |
| --- | --- |
| `res/values/strings.xml` (+ et, ru) | `navigation_review` → "Needs you". Keep the route string `"review"` in `OrbitDestination.kt`; only the label changes. |
| `ui/screens/review/ReviewViewModel.kt` | Add `needsYou: List<ToSortItem>` = captures with `outcome == NeedsYou`, newest first. Add `savedToday: Int` (count of `Saved` + `Filed` today) for the empty state. `nothingWaiting` becomes `needsYou.isEmpty() && dueToday.isEmpty()`. |
| `ui/screens/review/ReviewToSort.kt` | `buildToSort()` keeps working for the "Tidy up" screen; add `buildNeedsYou()` that filters on outcome and sets the question text from `outcomeReason`. |
| `ui/screens/review/ReviewRedesign.kt` | Replace `ToSortGroup` at the top with `NeedsYouGroup`: one `NeedsYouCard` per item (reuse `QuickReminderCard`'s layout from `HomeSendFeedback.kt` — move it to `ui/components/OneQuestionCard.kt` and use it in both places). Keep `TodayGroup` below, collapsed to 3 rows. Move `FromEarlierGroup`, `WeeklyLookBackRow` and `SortOneByOne` behind one "More" row. |
| `ui/screens/review/ReviewRedesign.kt` → `ReviewHeader` | Ask and Breathe move into the existing `LumaMenu` overflow. The header shows only the title and `reviewSummary()`. |
| `ui/screens/review/ReviewScreen.kt` | Empty state when `needsYou.isEmpty()`: "Nothing needs you right now." plus one line "Today Tallele kept 6 and filed 4." (uses `savedToday`). Bottom padding already uses `OrbitBottomNavigationDefaults.ContentClearance` (120 dp); keep it, and add a 24 dp fade (`Brush.verticalGradient`) behind `FloatingBottomNavigation` so scrolled text never shows through. |
| `ui/components/FloatingBottomNavigation.kt` | No badge on the Needs you tab unless `needsYou` contains a `TimePassed` or `NotificationsOff` item (something failed). |

**Two sections that cannot be confused.** "Needs a decision" uses the `OneQuestionCard` surface with buttons; "Today" uses plain `ReviewListRow`s with a checkbox, no buttons, and a quieter heading. A task due today is a task, not a question, so it never appears as a card. Nothing in Needs you is required: every `Saved` thought is browsable without sorting in the Later tile on Spaces (exists since PR #9) and in Search, which already indexes unsorted thoughts.

### Each card asks one thing

| Reason | Question | Buttons |
| --- | --- | --- |
| TimeUnclear | When should this remind you? | Today 18:00 · Tomorrow 09:00 · Pick a time · Just keep it |
| TimePassed | This time has already passed. | Pick a new time · Just keep it |
| TypeUnclear | Save as a task, or just keep it? | Make it a task · Just keep it |
| NotificationsOff | Reminders can't ring while notifications are off. | Turn on · Just keep it |
| NotScheduled | Tallele couldn't set this reminder. | Try again · Just keep it |

"Just keep it" always ends with outcome `Saved` and removes the card; when a reminder row exists it is deleted through `CaptureResolution.undo()` first, which cancels its alarm and backup work (Step 5c). The two quick times use the user's existing defaults (`reminderOffsetOptions` in `ReminderTiming.kt` for the offset; 18:00 / 09:00 as constants in `CaptureDecision.kt`, later a setting).

### Tests

- `ReviewViewModelTest` (create next to `ReviewChangeUndoRoomTest` if it does not exist): three `Saved` captures and one `NeedsYou` → `needsYou.size == 1`, `nothingWaiting == false`; answer it → empty.
- `ScreenTour.kt`: `review-needs-you-empty`, `review-needs-you-two-cards`, `review-more-expanded`.
- `ReviewChangeUndoRoomTest` unchanged and green.

### Acceptance

Fresh install, save three clear thoughts and one "remind me about the car": the tab shows one card, answer it with Tomorrow 09:00, and the screen reads "Nothing needs you right now."

## Step 7 — New first run (M, about 5–7 h; needs Step 4)

**Goal:** a new user is on Home within one second and learns the app from the first receipt, not from four cards.

Today `OrbitApp.initialOrbitRoute(settings)` sends new users to `FirstTimeTutorialDestination` (4 pages in `tutorial/FirstTimeTutorialContent.kt`), then `TutorialSpaceSetupViewModel` asks them to pick starter Spaces.

### Files

| File | Change |
| --- | --- |
| `ui/navigation/OrbitApp.kt` | `initialOrbitRoute()` always returns `OrbitDestination.Home.route`. Keep the tutorial route and its `isReplay` argument for Settings › "Show the guide again" (line \~508 already wires it). |
| `ui/screens/tutorial/TutorialSpaceSetupViewModel.kt` + `data/local/StarterSpaces.kt` | Seed the starter Spaces silently on first launch via `spaceRepository.insertStarterSpaces()` (exists, skips taken names). Remove the picker from first run; keep it reachable from Spaces › "Add starter Spaces". |
| `ui/screens/home/HomeScreen.kt` | When `!settings.hasCompletedFirstTimeTutorial`: one line above the capture box, "Write anything. Tallele keeps it safe and sorts what it can." and one line below, "Everything stays on this phone unless you turn on cloud AI or use voice." After the first successful `send()`, set `hasCompletedFirstTimeTutorial = true` through `onSettingsChanged`. |
| `ui/screens/home/HomeSendFeedback.kt` | A `FirstReceiptTip` shown once under the first receipt. Its text follows the real outcome: Filed → "Tallele filed this for you. Tap Change if it got it wrong."; Saved → "Tallele kept this. You'll find it in Later and in Search."; ReminderSet → "The reminder is set. Tap Change to move it."; NeedsYou → no tip, the question is the tip. Stored flag: `AppSettings.hasSeenFirstReceiptTip` (new field, default false). |
| `ui/screens/home/CaptureSortHost.kt` / `CaptureSortViewModel.kt` | These already request `POST_NOTIFICATIONS`. Move the request to the first time a capture's outcome becomes `ReminderSet` or `NeedsYou/NotificationsOff`: show one line "So this reminder can ring at 09:00, Tallele needs to show notifications." then the system dialog. Never on cold start. |
| `tutorial/FirstTimeTutorialContent.kt` | Change the page-1 copy from "Write it down. Sort it later." to "Write it down. Tallele takes it from here." and page-2 from "Tallele suggests. You decide." to "Tallele files what's clear and asks only when it must." (the guide still exists for replay). |
| `res/values/strings_screens.xml` (+ et, ru) | New strings; see Wording. |

### Tests

- Journey test under `app/src/test/java/com/orbit/app/journey/`: fresh settings → `MainActivity` → Home is the first screen, no dialog, capture box present; after one send, `hasCompletedFirstTimeTutorial` is true.
- `TutorialPrimaryLabelTest` / `StarterSpacesTest` (exist): update for the new copy and silent seeding.
- `ScreenTour.kt`: `first-launch` step now shows Home with the two lines; `guide` steps still render via replay.

### Acceptance

Uninstall, install, open: the keyboard-free Home appears at once. Type "Buy milk tomorrow", send: the receipt says "Task for tomorrow · Change" and the tip appears once. Open Spaces: the starter Spaces are there without having been asked.

## Step 8 — Glass and default look (M, about 6–10 h)

**Goal:** the capture box, the bottom bar and sheets look like glass on a real phone; list cards stay solid and readable; nothing drops frames.

### 8a. Turn live blur on where it belongs

File: `ui/components/GlassSurface.kt`, line 69:

```kotlin
// before
val liveGlassEnabled = style == GlassSurfaceStyle.Prominent && shouldRenderLiveGlass(…)
// after
val liveGlassEnabled = style in LiveGlassStyles && shouldRenderLiveGlass(…)
private val LiveGlassStyles = setOf(
    GlassSurfaceStyle.Prominent, GlassSurfaceStyle.HomeCapture,
    GlassSurfaceStyle.NavigationBar, GlassSurfaceStyle.Sheet,
)
```

Then swap `SoftGlassSurface` → `LiveGlassSurface` in exactly three call sites: the capture box in `ui/screens/home/HomeScreen.kt` (style `HomeCapture`), `ui/components/FloatingBottomNavigation.kt` line 82 (style `NavigationBar`), and `ModalSurface` in `GlassSurface.kt` line 215 (style `Sheet`). All 11 other `SoftGlassSurface` call sites (cards, rows) stay as they are. `LiveGlassSurface` already falls back to `SoftGlassSurface` when the policy is `SoftOnly`, when there is no `hazeSource`, or when the API is too low, so the `Soft` and `Off` presets keep their meaning.

### 8b. Platform policy

File: `ui/components/GlassSurface.kt`, `shouldRenderLiveGlass()`: return false below API 33. Haze 1.7.2 uses a runtime shader on 33+, multiple layers on 32, and a scrim below that; only the shader path is worth the cost. Also return false when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` ("Remove animations") — read it once in `ReducedMotion.kt`, which already exists.

### 8c. Make the glass readable and visible

- `orbitGlassVisuals()` (line 279): the `NavigationBar` blur radius is 10–12 dp; raise it to 20 dp and keep the tint alpha (`glassTintAlpha`) at or above 0.55 so text behind the bar is colour, not words.
- `glassEdgeBrush()` (line 187) already draws the edge light. Set the top-edge alpha to 0.24 light / 0.14 dark for the three live styles.
- `AppSettings.backgroundPreset` default: `InkPaper` → `VioletMist` *for new installs only* (existing users keep what they have; `AppSettingsRepository` reads the stored value, so only the Kotlin default changes). Glass needs colour behind it; the review found Ink & Paper reads as matte.
- Bottom bar fade: Step 6 adds the 24 dp vertical fade behind `FloatingBottomNavigation`; it serves every tab.

### 8d. Contrast check

New JVM test `GlassContrastTest`: for each `BackgroundPreset` × light/dark, take the lightest and darkest colour the gradient in `OrbitBackground.kt` can put under a surface, blend with `orbitGlassVisuals().fallbackColor`, and assert body text (`onSurface`) reaches 4.5:1 (WCAG AA). Failing presets get a higher tint alpha, not a smaller font.

### 8e. Performance

The `macrobenchmark/` module exists. Add `HomeScrollBenchmark` and `ReviewScrollBenchmark` with `FrameTimingMetric`; pass when P90 frame time < 16 ms on your own phone. If Review fails, set `inputScale = HazeInputScale.Fixed(0.5f)` on the `NavigationBar` style only.

### Later, not now

Haze 2.0 (performance modes, a "glass" refraction effect) is a bigger upgrade with API changes. Do 8a–8e on 1.7.2 first; upgrade in its own PR after the benchmark exists, so you can see the difference.

### 8f. Check it on a real phone before calling it done

Robolectric screenshots show the soft fallback, and the contrast test is arithmetic. Neither proves the glass is readable, smooth or premium. After 8a–8e, run this on your phone and keep a screen recording:

1. Default preset, light and dark: open a Space with 30+ items and scroll slowly under the bottom bar. The bar must show blurred colour, never a readable word, and the tab labels must stay crisp.
2. Same, with font size at 130% and at the largest setting.
3. Soft Dawn, Violet Mist and Night Glow: type three lines into the capture box; the text must be readable against the glass without squinting.
4. Open and close a sheet five times fast; no stutter, no flash of solid before the blur appears.
5. Settings › Glass › Soft, then Clear: the change must be obvious on the capture box and bar, and invisible on cards.
6. Developer options › Profile GPU rendering on, scroll Review: no bar over the green line while scrolling.

If any of these fails, lower the blur radius or the `inputScale` before touching the design, and re-run.

### Tests

- `GlassContrastTest` as above.
- `ScreenTour.kt` renders in Robolectric, which cannot draw the shader, so screenshots will keep showing the soft fallback. Add a one-line note in `ScreenTour.kt` saying so, so no one reads the PNGs as the real look again.

### Acceptance

On your phone with the default preset: scroll a long Space detail; the bottom bar shows blurred colour, never readable text; the capture box shows the background softly through it; Settings › Glass › Soft turns it off everywhere.

## Step 9 — Small fixes (S each, about 8–12 h total)

Each is independent. Do them in any order, one PR each.

| # | Fix | File | Change | Check |
| --- | --- | --- | --- | --- |
| 9.1 | Voice stays on-device when it can | `capture/VoiceCapture.kt` | On API 31+, if `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`, use `createOnDeviceSpeechRecognizer()` with a `RecognitionListener` instead of the intent. Otherwise keep the intent and show once: "Voice may use Google's servers on this phone." Store the seen flag in `AppSettings`. | Airplane mode: voice either works or says why not. |
| 9.2 | Search from the first letter | `ui/screens/search/SearchScreen.kt` line 104 and line 494 | `length < 2` → `isEmpty()`. `SearchViewModel.SearchDebounceMillis` → 150 ms. Before typing, show the last 5 saved thoughts instead of a blank. | Type "p": results appear. |
| 9.3 | No results that help | `ui/screens/search/SearchScreen.kt` | Replace the empty result body with "No matches for 'x'." and a "Search archived too" toggle (`includeArchived` already exists in the view model). | — |
| 9.4 | Chips look scrollable | `ui/screens/search/SearchScreen.kt` chip row | Add `contentPadding = PaddingValues(end = 48.dp)` to the `LazyRow` so the last chip peeks, plus the fade the code already has. | Last chip is cut in the middle, not at its edge. |
| 9.5 | Name-only Space | `ui/screens/spaces/` (Space editor sheet) | Create with the name field only; pick colour and icon automatically (`SpaceNames.kt` + a simple hash into the 8 colours and 12 icons). Show "Colour and icon" as a collapsed row. | New Space in one field. |
| 9.6 | Space "+" stays in place | `ui/navigation/OrbitDestination.kt` (`returnHomeWithSpaceCapture`) and `ui/screens/home/CaptureSortHost.kt` | Open the capture sheet as a bottom sheet over the Space with `spaceId` preset, the same way Calendar "+" opens the sort sheet since PR #9. Return to the Space after save. | Save from a Space, you are still in it. |
| 9.7 | Only relevant fields on items | `ui/screens/item/ItemDetailScreen.kt` | Hide due date, status and reminder controls when the item is a note; show them under "More" for tasks. | A note shows title, body, Space, labels. |
| 9.8 | Calendar filler once | `ui/screens/calendar/` day view | Show `calendar_nothing_planned` at most once per day, after the last item, in `onSurfaceVariant`. | — |
| 9.9 | Settings order | `ui/screens/settings/SettingsHome.kt` | Order: Reminders · Look · AI · Data · About. "Look" is one row that opens `AppearanceSettings`. Move the two behaviour toggles (`sortRightAfterSaving`, `focusCaptureOnOpen`) under Reminders › Advanced. | Overview fits on one screen at default font. |
| 9.10 | Plain backup words | `ui/screens/settings/LocalDataSettings.kt` | Buttons "Create backup" / "Restore backup"; the `.json` note and replace rule as small text under them. | — |
| 9.11 | Ask answers first | `ui/screens/situation/` (Ask entry) | When opened from Ask, the question box and answer are the first thing; the overview is below. | — |
| 9.12 | Seed data | `app/src/test/java/com/orbit/app/screens/ScreenTour.kt` line 185 | Done in Step 0. | — |

## Wording

All new strings in plain English. Add them to `res/values/strings_core_ui.xml` (Home and receipt), `strings_screens.xml` (Settings, Needs you) and the matching `values-et` and `values-ru` files; the app already ships all three languages, so a missing translation shows English with a lint warning. Placeholders are the usual Android `%1$s` / `%1$d`.

| Key | English | Where |
| --- | --- | --- |
| `receipt_saved` | Saved. | Receipt, outcome Saved |
| `receipt_saved_note` | Saved as a note. | Receipt, Saved, no Space |
| `receipt_filed_note` | Filed in `%1$s` as a note. | Receipt, Filed |
| `receipt_filed_task` | Task for `%1$s`. | Receipt, Filed task (day) |
| `receipt_reminder_set` | Reminder set for `%1$s`. | Receipt, ReminderSet with an armed alarm |
| `receipt_reminder_not_armed` | Saved, but the reminder isn't set yet. | Receipt, ReminderSet but `notificationWorkId` is null |
| `receipt_needs_time` | Saved. What time should it remind you? | Receipt, NeedsYou/TimeUnclear |
| `receipt_time_passed` | That time has already passed. Pick a new one? | Receipt, NeedsYou/TimePassed |
| `receipt_notifications_off` | Saved, but reminders can't ring. Notifications are off. | Receipt, NeedsYou/NotificationsOff |
| `receipt_not_scheduled` | Saved, but Tallele couldn't set the reminder. | Receipt, NeedsYou/NotScheduled |
| `receipt_action_view` | View | Button |
| `receipt_action_change` | Change | Button |
| `receipt_action_choose_time` | Choose time | Button |
| `receipt_action_turn_on` | Turn on | Button |
| `receipt_action_try_again` | Try again | Button |
| `receipt_action_keep` | Just keep it | Button, replaces "Not now" |
| `home_first_line_above` | Write anything. Tallele keeps it safe and sorts what it can. | Home, first run |
| `home_first_line_below` | Everything stays on this phone unless you turn on cloud AI or use voice. | Home, first run |
| `home_first_receipt_tip_filed` | Tallele filed this for you. Tap Change if it got it wrong. | After first receipt, Filed |
| `home_first_receipt_tip_kept` | Tallele kept this. You'll find it in Later and in Search. | After first receipt, Saved |
| `home_first_receipt_tip_reminder` | The reminder is set. Tap Change to move it. | After first receipt, ReminderSet |
| `notification_priming` | So this reminder can ring at `%1$s`, Tallele needs to show notifications. | Before the system dialog |
| `navigation_review` | Needs you | Bottom bar (existing key) |
| `needs_you_decisions_heading` | Needs a decision | Needs you, first section |
| `needs_you_today_heading` | Today | Needs you, second section |
| `needs_you_empty` | Nothing needs you right now. | Needs you, empty |
| `needs_you_empty_detail` | Today Tallele kept `%1$d` and filed `%2$d`. | Needs you, empty |
| `needs_you_more` | More | Row that hides look-back, earlier, tidy up |
| `reminder_health_ok` | Nothing is blocking reminders. | Settings row |
| `reminder_health_notifications_off` | Notifications are off, so reminders can't ring. | Settings row |
| `reminder_health_channel_off` | The Reminders sound is off. | Settings row |
| `reminder_health_battery` | This phone sometimes stops apps in the background. If a reminder is ever missed, this is the first thing to check. | Settings row, aggressive brands only |
| `reminder_health_late` | Reminders may be a few minutes late. | Settings row, API 31–32 |
| `reminder_health_samsung_steps` | Battery › Background usage limits › Never sleeping apps › add Tallele | Fallback text on Samsung |
| `tutorial_page1` | Write it down. Tallele takes it from here. | Guide, replay only |
| `tutorial_page2` | Tallele files what's clear and asks only when it must. | Guide, replay only |
| `voice_cloud_notice` | Voice may use Google's servers on this phone, even with cloud AI off. | Once, before first voice use |

Rule for every new string: say what is true now, in words a tired person understands. No "successfully", no "processing", no "AI".

## Testing

### After every step

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug      # 573 JVM tests today; must not drop
./gradlew :app:assembleDebug
```

Then push and let `android-verify.yml` and `screens.yml` run. Compare the new PNGs on the `screens/` branch with the previous ones; every changed screen should be one you meant to change.

### Per step, the one test that proves it

| Step | Test | File |
| --- | --- | --- |
| 1 | Fresh API 34 install arms an exact `RTC_WAKEUP` alarm | `reminders/WorkManagerReminderSchedulerTest.kt` |
| 1 | Daily 09:00 repeat survives a time-zone change | `reminders/ReminderSchedulingTest.kt` |
| 1 | Process death, reboot and force-stop each recover with exactly one alarm per reminder | `reminders/ReminderDeliveryRoomTest.kt` |
| 2 | Every flag combination → one row state and one button; battery hint is never red | `reminders/ReminderCapabilityStateTest.kt` (new) |
| 3 | 1→11 migration chain; backfill uses `reminders.linkedCaptureId`, not `suggestedType` | `data/local/OrbitDatabaseMigrationJvmTest.kt` |
| 3 | Late analysis never overwrites Filed; Undo stamps `decidedAt` | `capture/CaptureInboxRoomTest.kt` |
| 3 | Pending decision survives a container restart with one item, no duplicates | `capture/AutoFilerRecoveryTest.kt` (new) |
| 4 | Receipt comes back from DataStore + Room after a container restart | `ui/screens/home/HomeCaptureViewModelTest.kt` (new, next to `HomeAfterSavingTest.kt`) |
| 5 | 60-row decision table, en/et/ru; explicit reminder with no time → Ask | `domain/capture/CaptureDecisionTest.kt` (new) |
| 5 | Act files, "Just keep it" cancels the alarm, late event is ignored | `capture/AutoFilerRoomTest.kt` (new) |
| 6 | Three Saved + one NeedsYou → one card; today's task is a row, not a card | `ui/screens/review/ReviewViewModelTest.kt` |
| 7 | Fresh install opens on Home, no dialog; tip text matches the outcome | `journey/` (existing journey test, extended) |
| 8 | Body text ≥ 4.5:1 on every preset, light and dark (a gate, not proof) | `ui/components/GlassContrastTest.kt` (new) |
| 8 | P90 frame time < 16 ms scrolling Home and Review | `macrobenchmark/` |
| 8 | The 8f device checklist, with a screen recording | by hand |

### Hands-on checks on your phone (30 minutes, after Step 6 and again after Step 9)

1. Fresh install. Save 20 real thoughts from your day in a row, as fast as you can type. Hard rules: 20 of 20 are findable in Search, no receipt says "Reminder set" without an armed alarm (check `dumpsys alarm`), and no reminder rings twice. Then write down two numbers: questions that were not needed (target 0) and misfilings (just count them; they are a tuning job, not a failure).
2. Save "remind me to call the bank tomorrow at 9". Force-stop the app. Reboot the phone. Confirm it rings at 09:00 ± 1 minute, once.
3. Turn off notifications in Android. Save another reminder. The receipt must say "can't ring", Settings must show one Turn on button, Needs you must show one card. Turn notifications back on: all three clear without you touching the item.
4. Switch Settings › Glass to Soft, then back to Clear, on the default background. The capture box and bar should visibly change; cards should not. (Full list in 8f.)
5. Turn on TalkBack. Save a thought. You should hear "Saved" and then, a moment later, the filed line, without focus jumping.
6. Set font size to the largest. Every receipt, card and the Settings row still fit without clipped text.

### The one question to ask after each step

Write 10 thoughts in the evening, close the app, and answer the next morning: *Did I have to think about any of them again without a good reason?* The number should go down with every step; if it goes up, the step is wrong, however good the code looks.

## Things not to do

- **Don't auto-create a reminder when the time is unclear or in the past.** `decide()` returns `Ask` for those, and nothing else may create one.
- **Don't leave an explicit reminder request unanswered.** A High-confidence reminder with no usable time always asks; only a weak guess is kept quietly.
- **Don't delete an item to edit it.** "Change" opens `ItemDetailScreen`; Undo is for Undo.
- **Don't record automatic filing as the user agreeing.** No `Accepted` event for an `Act`; only user corrections feed the learning history.
- **Don't publish to Google Play with `USE_EXACT_ALARM`** unless Tallele qualifies under Play's alarm/calendar policy. The `SCHEDULE_EXACT_ALARM` path and the health row stay in the code for that day.
- **Don't use full-screen intents for reminders.** They are loud, and Android 14+ restricts them anyway. `IMPORTANCE_HIGH` on the `luma_reminders` channel is enough.
- **Don't wait for Gemini (or any analyzer) before showing the receipt.** The receipt shows "Saved." from the stored row within 200 ms and updates later.
- **Don't promise what the app cannot see.** "Nothing is blocking reminders" is the strongest wording allowed; the app does not know what an OEM battery manager will do.
- **Don't put a badge count on Needs you** unless something failed. A number is homework.
- **Don't turn on live blur for cards and rows**, only for the three floating surfaces. Readability of body text comes first.
- **Don't delete `buildToSort()` or the Tidy up flow.** They become tools behind More, not the main path.
- **Don't rewrite `CaptureInbox` to call `CaptureResolution` directly.** Use the `AutoFiler` listener plus `applyPending()`; the dependency cycle is real and the in-memory listener alone is not durable.
- **Don't change the backup format without bumping the version** and keeping formats 1–6 readable (the PR #8 pattern).
- **Don't touch the architecture for looks.** Steps 1–6 settle what a thought *is*; Step 8 only changes how surfaces draw.
- **Don't let old thoughts become homework on upgrade.** The migration backfills to `Saved`, never to `NeedsYou`.

Vitautas: ChatGPT GPT-6 Astra has reviewed this implementation plan. Here's the summary:

The plan has a strong product direction, but needs a focused revision before implementation. Keep the overall approach: persistent receipts, act/ask/keep, an exception-focused Review screen, simpler onboarding and restrained glass.

Correct these issues:

1\. Automation rules contradict the intended behaviour.

Explicit reminder requests without a time must ask for one, not silently remain saved. The reminder branch lacks the confidence check promised in the text. A reminder due within two minutes is incorrectly treated as already passed.

2\. “Just keep it” must cancel any linked reminder and pending delivery work, not merely change the capture’s status.

3\. “Change” must not undo/delete the existing item before opening the editor. Cancelling editing must preserve the original item and schedule.

4\. Define reliable recovery.

Database updates and Android alarm scheduling cannot form one atomic transaction. Specify pending work, safe retries, duplicate prevention and recovery after interruption. An in-memory AutoFiler listener alone is insufficient without durable recovery. Prevent late analysis from overriding user changes or undo.

5\. Fix migration assumptions.

Infer completed actions from actual linked items, not suggestedType. Do not assume an existing reminder record proves that its alarm is currently scheduled. Automatic filing must not count as positive user feedback.

6\. Correct Android promises and tests.

USE\_EXACT\_ALARM does not grant notification permission. Separate ordinary process death, reboot and force-stop recovery tests. Do not treat normal battery optimisation as proof reminders are broken. Replace “Reminders will ring” with accurate capability wording.

7\. Persist receipts properly.

SavedStateHandle alone does not guarantee restoration after force-stop, reboot or task removal. Persist or reconstruct the receipt from durable data. First-save guidance must match what actually happened.

8\. Finish the UX definition.

Needs you should clearly distinguish unresolved decisions from ordinary daily tasks. Keep saved thoughts easy to browse without requiring sorting. Fix the privacy statement: voice processing may use an external provider independently of cloud AI.

9\. Validate actual glass on a device.

Enabling blur is a starting point. Fallback screenshots and calculated contrast tests do not prove live readability, smoothness or premium appearance.

10\. Replace misleading acceptance targets.

Require zero lost captures, zero false completion claims and zero duplicate reminders. Judge questions by necessity, not a maximum count. Measure classification mistakes separately.

Recommended order:

Verify current build → settle behaviour rules → reliable state/recovery → receipts → conservative automation → Needs you/onboarding → visual polish.

Treat the 70–95-hour estimate as provisional until these gaps are resolved.
