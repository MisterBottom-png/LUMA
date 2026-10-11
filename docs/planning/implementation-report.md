# Tallele plan run: implementation report

Branch: `tallele-plan-all` (from `ccr-eed43729-xsml4c` at `4d0a6ff`). Plan: `docs/planning/tallele-implementation-plan.md`. Date: 11 October 2026.

## 1. Summary

Steps 0–9 of the plan are on `tallele-plan-all`. Every merge passed the full gate before the next wave started: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`. The run made no change to Haze or Gemini and added no new dependency. Nothing was pushed to `main` or `ccr-eed43729-xsml4c`. No PR was opened.

**What changed for the user**
- **Home first.** A new install opens straight on Home, with the starter Spaces already in place and two short lines explaining the app.
- **A receipt under the capture box.** It says what happened to the last thought ("Saved.", "Filed in Work as a note.", "Reminder set for tomorrow 09:00.") and offers one button. It survives the app being killed. It says "Reminder set" only when the alarm is really armed.
- **Automatic filing.** Clear thoughts are filed without asking. A reminder Tallele is sure about but has no usable time becomes one question; everything else is kept quietly. The old Review tab is now **Needs you**: empty most days, one card per real question, Today below it, the tools behind "More", and a dot only when something failed.
- **Reminders.** They use exact alarms from the first install, and survive reboot, update, clock changes and time-zone changes. A health row in Settings shows one fix button when something blocks them.
- **Glass.** The capture box, bottom bar and sheets use live blur on Android 13+. New installs start on Violet Mist.
- **Smaller fixes.**
  - Voice capture stays on the phone where it can.
  - Search works from the first letter.
  - A Space can be created from its name alone.
  - Notes no longer show task fields.
  - Calendar shows one "Nothing planned" line per day.
  - Backup buttons use plain words.
  - Ask shows the answer first.
  - Settings is ordered Reminders · Look · AI · Data · About.
  - A Space's "+" writes the thought without leaving the Space.

**Numbers**

| | Before (Step 0 baseline) | After (final) |
| --- | --- | --- |
| JVM tests | 578 (2 skipped, 0 failed) | **711** (2 skipped, 0 failed) |
| Lint | 0 errors, 48 warnings | 0 errors, 72 warnings |
| `assembleDebug` | OK | OK |
| `compileDebugAndroidTestKotlin`, macrobenchmark compile | — | OK |
| ScreenTour light / dark | — | no problems / no problems |

The plan quoted 573 tests; the real count at `4d0a6ff` was 578.

Lint warnings rose by 24. The final list:
- 24 `UnusedResources`. Mostly strings left in place on purpose, so parallel branches could merge cleanly: old Search and Calendar texts, the old quick-reminder strings and the old "all sorted" text.
- 19 `GradleDependency`, 3 `AndroidGradlePluginVersion` (newer versions exist; nothing was upgraded).
- 8 `PluralsCandidate`, 4 `UseKtx`, 2 `UnusedAttribute`.
- 1 each: `BatteryLife` (from `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, which the plan expects), `InlinedApi`, `ObsoleteSdkInt`, `DiscouragedApi`, `LockedOrientationActivity`, `AppBundleLocaleChanges`.

There are no errors.

**What was not checked:** nothing on a real phone. Everything in section 6 is still open. CI (`android-verify.yml`, `screens.yml`) has only run the Step 0 render, not the final branch.

## 2. How the run went

| Wave | Steps | Merge order | Tests after the wave | Notes |
| --- | --- | --- | --- | --- |
| 0 | Step 0 (integrator) | — | 578 | Plan saved word for word; seed row fixed; `screens.yml` dispatched (success). |
| 1 | 3 + 5a, 1, 8, 9 (part) in parallel | 3, 1, 8, 9 | 663 | Step 3's `decide()` was first written to ask on Medium confidence; it was sent back and realigned to the plan's code. Step 1 × Step 3 conflict in `ReminderRescheduleWorker.reconcileReminders` was resolved by keeping both parameters. Two lint errors from 9.1 were fixed by the integrator. |
| 2 | 4, 2 | 4, 2 | 677 | Step 2 × Step 4 `ScreenTour` conflict: both kept. `RemindersGroupJvmTest` still expected the removed "Reminder delivery" title and was updated. |
| 3 | 5, 7 | 5, 7 | 702 | Step 5 × Step 7 conflicts in `HomeCaptureViewModel`, `HomeScreen`, `HomeSendFeedback` and `ScreenTour`: all kept. Step 7's priming check pointed at a field Step 5 removed and was fixed. |
| 4 | 6, 9.9, 9.6 (integrator) | — | 711 | One leftover assertion in `TabJourneyJvmTest` was updated to the new Settings "Look" row. |

Every merge was followed by the full gate. No merge needed more than one fix attempt, and none was reverted.

The screen tours were run separately for light and dark, as `screens.yml` does. Run together in one JVM, the second tour's first-launch step times out, because first-run state is process-wide. This is a test artifact, not an app bug.


## 3. Step reports, in plan order

Each step report has five parts: (1) what the user will notice, (2) files, (3) tests, (4) what needs a real phone, (5) departures.


### Step 0 — Re-render screens and confirm what is already fixed (integrator)

Commits `ef3249d` (plan saved word for word) and `2b5c0f0` (seed fix), both on `tallele-plan-all`.

**(1) What the user will notice.** Nothing in the app. In the screen tour, "Prepare slides for Monday" is now due on the next Monday. Before, it was always due three days out.

**(2) Files changed**
- `docs/planning/tallele-implementation-plan.md` (new): the plan, byte-identical to the attachment.
- `app/src/test/java/com/orbit/app/screens/ScreenTour.kt`: the seed row is due `today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))`.

**(3) Checks**
- Baseline: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` gave 578 JVM tests (2 skipped, 0 failed), lint 0 errors and 48 warnings, and the build passed.
- `screens.yml` was dispatched on `tallele-plan-all` (run 38126392441, success).
- The local tour render shows the slides task on the Monday ("Tomorrow 9:00 AM" on Sunday 11 October).

**Ticked off as already in the code** (checked against 4d0a6ff, not rebuilt):
- `ReminderSaveOutcome`;
- the boot/update receiver;
- reconcile on every app open;
- `ToSortState.NeedsChoice`;
- Undo on Review actions;
- the Clear/Soft/Solid glass presets;
- Calendar "+" opening the sort sheet;
- `tallele-export.json`.

**(4) Needs a real phone.** Nothing.

**(5) Departures**
- **The baseline is 578 tests, not the plan's 573.** PRs #5–#10 added tests after the plan's count was taken.
- **Step 0 runs on `tallele-plan-all`,** not on `ccr-eed43729-xsml4c` at `4d0a6ff`. As instructed, the branch starts from that commit.


### Step 1 — Reminders that always ring

Branch `plan/step-1`, commit `d297fae`.

**(1) What the user will notice.** On a fresh install on Android 13 or newer, a saved reminder gets an exact alarm straight away. Nobody has to go to "Alarms & reminders" first. Reminders are re-armed after a reboot, an app update, a manual clock change and a time-zone change. A daily 09:00 repeat rings at 09:00 in the new time zone, while a one-off reminder stays at its fixed moment. If the clock jumps past a reminder, it is shown once as missed. The battery check exists as data only: a new `MayBeStopped` state plus the intents that fix it. Settings text is unchanged; the health row comes in Step 2.

**(2) Files changed**
- `AndroidManifest.xml`:
  - adds `USE_EXACT_ALARM` (with the plan's comment) and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`;
  - adds `TIMEZONE_CHANGED` and `TIME_SET` to `ReminderBootReceiver`;
  - adds a `<queries>` entry for `com.samsung.android.lool`.
- `reminders/ReminderBootReceiver.kt`: handles `ACTION_TIMEZONE_CHANGED` and `ACTION_TIME_CHANGED`.
- `reminders/ReminderCapabilities.kt`:
  - new fields `batteryRestricted` and `aggressiveBatteryBrand`;
  - new state `MayBeStopped`, checked in the plan's order;
  - new helpers `batteryOptimizationIntent`, `samsungNeverSleepingIntent` and `batteryFixIntent`. The Samsung screen is only offered if it resolves; otherwise it falls back.
- `reminders/ReminderRepeat.kt`: new pure function `ReminderRepeats.reanchoredToZone()`.
- `reminders/ReminderRescheduleWorker.kt`: `reconcileReminders(zoneId)` re-anchors repeats before the delivery policy runs.
- `ui/screens/settings/CaptureReminderSettings.kt`: one line so `messageRes()` covers the new state. The text shown is unchanged.
- Tests:
  - extended: `WorkManagerReminderSchedulerTest`, `ReminderSchedulingTest`, `ReminderDeliveryRoomTest`;
  - new: `ReminderBootReceiverTest`, `ReminderBatteryCheckTest`.

**(3) Tests**
- `WorkManagerReminderSchedulerTest`: on API 34 the alarm is an exact `RTC_WAKEUP`.
- `ReminderSchedulingTest`:
  - after a time-zone change, a daily 09:00 repeat is re-armed at 09:00 in the new zone;
  - a zone change never moves a reminder into the past;
  - when the clock moves forward, a skipped reminder is delivered once as missed.
- `ReminderBootReceiverTest`:
  - each of the five actions leaves one unique work request named `luma_reschedule_reminders`;
  - repeated broadcasts still leave one;
  - an unrelated broadcast leaves none.
- `ReminderDeliveryRoomTest`: process death, reboot and force-stop recovery, as three separate tests.
- `ReminderBatteryCheckTest`: six cases covering state order, the brand list, the battery restriction and the intents.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.reminders.*'`. Result: 80 tests, 0 failures.

**(4) Needs a real phone**
- On a fresh install on Android 14 or newer, `dumpsys alarm` shows an exact `RTC_WAKEUP` alarm.
- After a reboot, and after a force-stop followed by opening the app once, the reminder rings once, within a minute of its time.
- `TIME_SET` and `TIMEZONE_CHANGED` actually reach the receiver.
- The Samsung "Never sleeping apps" deeplink works on One UI 7/8.

**(5) Departures**
- **Time-zone re-anchoring is new code.** The plan assumes a re-arm is enough, but the code stores a repeat's pending occurrence as an instant. Reconcile now moves the pending occurrence to its chosen time on the closest day, so the 09:00 intent survives a zone change. It never moves an occurrence that is snoozed, already delivered, or would land in the past. This touches `ReminderRepeat.kt` and `ReminderRescheduleWorker.kt`, which the plan does not name for this step.
- **One line in a Step 2 file.** `CaptureReminderSettings.kt` needed the new enum value to compile.
- **Process-death test.** The app-start reconcile does re-arm reminders, but it replaces each alarm in place with the same identity. The test therefore asserts identical alarms with no duplicates, rather than "nothing re-armed".
- **Additions not in the plan:**
  - the `<queries>` entry, without which the Samsung screen check always fails on Android 11 and newer;
  - `ReminderBatteryCheckTest`, named so it does not clash with Step 2's `ReminderCapabilityStateTest`.
- **Test setup.** Robolectric's API 34 jar was rate-limited by Maven (HTTP 429). It was fetched by hand into the local Maven cache and checked against the published checksums. CI downloads it normally.


### Step 2 — Reminder health row

Branch `plan/step-2`, commit `69191a8`.

**(1) What the user will notice.** In Settings › Reminders, the old "Reminder delivery" title and sentence are replaced by one line with at most one button:

| Situation | Line | Button | Opens |
| --- | --- | --- | --- |
| Nothing wrong | "Nothing is blocking reminders." | none | — |
| Notifications off | "Notifications are off, so reminders can't ring." | Turn on | app notification settings |
| Reminders channel muted | "The Reminders sound is off." | Turn on | that channel's settings |
| No exact alarms (Android 12/12L) | "Reminders may be a few minutes late." | Allow | exact-alarm setting |
| Battery restricted on a known aggressive brand | the battery hint | Check | Samsung's "never sleeping" list if it resolves, otherwise the app's battery request |

On Samsung, if the "never sleeping" screen does not resolve, the menu path is also spelled out under the line.

The text is grey except in one case: it turns red only when the state is Blocked and an undelivered reminder is due within 24 hours. The row re-reads the system state every time the app resumes.

**(2) Files changed**
- `reminders/ReminderCapabilities.kt`:
  - new types `ReminderHealthIssue`, `ReminderHealthFix` and `ReminderHealth`;
  - a pure function `health(nextNotificationAt, now)`;
  - `fixIntent(context, issue)`, `channelSettingsIntent`, `needsSamsungSteps`.
- `data/local/dao/OrbitDaos.kt`: `ReminderDao.nextUndeliveredNotificationAt(now)`. This is a query only; the schema is unchanged.
- `ui/screens/settings/CaptureReminderSettings.kt`:
  - new `ReminderHealthRow`, which is stateless and announces changes politely;
  - one `RowActionButton` or none;
  - a re-read on resume.
- `res/values{,-et,-ru}/strings_screens.xml`: 7 new strings (the six `reminder_health_*` keys and `reminder_health_action_check`).
- `res/values{,-et,-ru}/strings_redesign.xml`: the four retired `settings_reminder_delivery_*` strings are removed.
- Fake DAOs in `ReminderSchedulingTest` and `LocalReminderRestoreReconcilerTest` get one line each.
- `ScreenTour.kt`: `reminderHealthScreens()`.

**(3) Tests**
- New `ReminderCapabilityStateTest` (7 tests):
  - all 64 flag combinations map to the right line and button;
  - red appears only for Blocked with a reminder due within 24 h, boundaries included;
  - the late and battery hints are never red;
  - each issue maps to its fix screen;
  - the Samsung steps appear only on Samsung;
  - a Room test for `nextUndeliveredNotificationAt`.
- ScreenTour shots `settings-reminders-ok` and `settings-reminders-blocked`.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.reminders.ReminderCapabilityStateTest'`.

**(4) Needs a real phone**
- Notifications off, then back to the app: the row changes straight away. Turn them on again: the row reads "Nothing is blocking reminders." again.
- Muting only the Reminders channel.
- The Samsung deeplink on One UI 7/8.
- Allow on Android 12/12L.
- The largest font size.

**(5) Departures**
- **No separate "Capture & reminders" entry, so no dot.** The plan assumes such an entry, but the reminder row sits directly on the Settings overview. `SettingsHome.kt` is unchanged, and the red rule is the only red on the overview.
- **Re-read on resume already existed.** The row already used `LifecycleResumeEffect`; the DAO query is re-read the same way.
- **`fixIntent` takes the issue, not the state.** Blocked covers two different fixes.
- **No ViewModel.** The next reminder time is read in the composable, following the existing pattern for this row.
- **Wording.** "Nothing is blocking reminders." is used, not the plan's Acceptance text "Reminders will ring.".
- **Extra string.** "Check" is not in the Wording table; the plan's Behaviour table names it.
- **Samsung menu path in et/ru.** Written as a plain translation, because the official One UI menu names could not be checked.


### Step 3 — One stored outcome per thought (with 3e recovery) + Step 5a — The decision

Branch `plan/step-3`, commits `108a8d6` (Room v11, backup format 7), `ea18764` (decide()), `c5ce43f` (interrupted decisions finish on next start), `638a642` (decide() realigned to the plan's code by the integrator's request).

**(1) What the user will notice.** Nothing on screen yet: no screen changed in this step. Each thought now stores one answer to "what did Tallele do with this?": Saved, Filed, ReminderSet or NeedsYou, with a reason. It is written in the same transaction as the action, and it survives app restarts and backups.
- **Upgrade.** Existing thoughts get their answer from what really exists in the database. An open linked reminder means ReminderSet; a linked item means Filed. Everything else stays Saved, and no old thought becomes NeedsYou.
- **Reminders that can't ring.** A reminder saved while notifications are off, or one that could not be armed, is stored as NeedsYou. The reminder reconcile turns it back into ReminderSet once it is armed. A reminder missed while the phone was off becomes NeedsYou / TimePassed.

**(2) Files changed**
- `data/local/entity/OrbitEntities.kt`: enums `CaptureOutcome` and `CaptureOutcomeReason`; four new fields on `CaptureEntity`.
- `data/local/OrbitTypeConverters.kt`: converters for both enums.
- `data/local/OrbitDatabase.kt`: version 11, `Migration10To11` with the backfill through `reminders.linkedCaptureId`.
- `app/schemas/…/11.json`: exported schema.
- `androidTest/…/OrbitDatabaseMigrationTest.kt`: `CURRENT_VERSION` is now 11.
- `domain/capture/CaptureOutcomes.kt` (new):
  - maps `ReminderSaveOutcome` to `CaptureOutcome`;
  - `withOutcome()`;
  - `LinkedCaptureOutcomes` (the reconcile hooks for armed and missed reminders).
- `domain/usecase/ConfirmCaptureActionUseCase.kt`:
  - `markProcessed` writes Filed or ReminderSet and `decidedAt`;
  - new `onlyIfUndecided` guard, checked inside the transaction.
- `domain/capture/CaptureResolution.kt`:
  - new `recordReminderOutcome()`;
  - `undo()` sets Saved and stamps `decidedAt`;
  - `archive()` stamps `decidedAt`;
  - `acceptSuggestion(captureId, automatic)`.
- `ui/screens/home/CaptureSortViewModel.kt`: records the reminder outcome from the sort sheet.
- `ui/screens/item/ItemDetailViewModel.kt`: the capture status toggle stamps `decidedAt`.
- `reminders/ReminderRescheduleWorker.kt`: `reconcileReminders(…, linkedCaptures)` updates linked captures.
- `data/export/LocalDataBackupCodec.kt`: format 7. Formats 1–6 restore as Saved / None / null.
- `ui/screens/review/ReviewToSort.kt`: `ToSortItem` carries `outcome` and `outcomeReason`.
- `domain/capture/CaptureDecision.kt` (new, Step 5a): `CaptureDecision` and `decide()`.
- `domain/capture/AutoFiler.kt` (new, minimal): `applyPending()` and `apply(captureId)`. Not wired yet; Step 5 wires it.

**(3) Tests**
- `OrbitDatabaseMigrationJvmTest`: 10→11 backfill and the full 1→11 chain.
- `CaptureInboxRoomTest` (+6): Saved after save; Filed or ReminderSet after accept; Saved after undo; a late result after undo or Filed is ignored; archive stamps `decidedAt`.
- `ReminderOutcomeEntryPointsTest`: the three `ReminderSaveOutcome` values through every entry point; re-arm and missed.
- `BackupRoundTripRoomTest` (+2): format 7 round trip; formats 1–6 come back as Saved.
- `CaptureDecisionTest`: 68 real thoughts (28 en, 20 et, 20 ru) through the local analyzer and `decide()`, plus hand-built rule rows.
- `AutoFilerRecoveryTest`:
  - the plan's scenario: interrupted, restart, one note and `decidedAt` set; Undo, restart, still zero notes;
  - racing runs produce one item;
  - no time gives one question and no reminder row;
  - a past time gives TimePassed;
  - a plain thought is kept;
  - a thought with no suggestion waits.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.data.local.OrbitDatabaseMigrationJvmTest' --tests 'com.orbit.app.domain.capture.*' --tests 'com.orbit.app.data.export.*'`. Result: 147 tests in 16 classes (incl. neighbours), 0 failures.

**(4) Needs a real phone**
- Force-stop right after saving a reminder, reopen, and check that the SQLite row says ReminderSet.
- The 10→11 upgrade on an install with real data.
- Notifications off, then a reminder saved (NeedsYou / NotificationsOff), then notifications back on and the app reopened: the row returns to ReminderSet.
- A reminder missed while the phone was off becomes TimePassed.

**(5) Departures**
- **The decision was first written to ask on Medium confidence.** The local analyzer scores explicit Estonian reminders ("tuleta mulle … meelde") lower than English and Russian ones. The integrator had it realigned to the plan's code, following the run rule "keep the thought saved, ask nothing": anything not High is kept. Explicit Estonian reminders the analyzer scores Medium are therefore kept, not asked about. This is listed as an open question.
- **Some of the plan's example rows don't match the real analyzer.**
  - "call mum Tue 9:00" is read as a Task, so the tests use "remind me to call mum Tue 9:00".
  - The time reader never produces a past time for "yesterday 9:00", so TimePassed rows use a decision made after the time had passed, plus hand-built rows.
- **Local rules never give a note High confidence.** Notes are therefore never auto-filed by the local analyzer alone; the recovery test uses a fixed High-confidence note.
- **AutoFiler is minimal.** It has `applyPending()` and `apply()` only and is not wired in; Step 5 wires it. `acceptSuggestion(automatic)` and `onlyIfUndecided` close the race where Undo could be re-filed.
- **"Recreate the container"** means fresh objects over a reopened on-disk database, because `OrbitContainer` shares one database per process.
- **Backup writes four fields,** including `decidedAt`. A thought whose linked item is missing from the export is exported as Saved.
- **Small additions outside the named files:**
  - the item-detail status toggle and the sort-sheet reminder path write outcomes;
  - the androidTest `CURRENT_VERSION` is 11.
- **Left as Saved (not in the plan's table):** a finished Brain Dump and `markCaptureReviewed`.


### Step 4 — The save receipt

Branch `plan/step-4`, commits `f3481be` (receipt), `b233dc8` (ScreenTour shots).

**(1) What the user will notice.** A quiet line now appears under the capture box with one button. It replaces the fading "Saved. You can let it go." The line updates in place as the thought gets filed. The card itself doesn't move: it only fades in, and not even that when reduced motion is on.

| Situation | Line | Button |
| --- | --- | --- |
| Kept | "Saved." | View |
| Kept as a note | "Saved as a note." | View |
| Filed in a Space | "Filed in Garden as a note." | Change |
| Task with a date | "Task for tomorrow." | Change |
| Reminder armed | "Reminder set for tomorrow 09:00." | Change |
| Reminder row has no armed alarm | "Saved, but the reminder isn't set yet." | Try again |
| No usable time | "Saved. What time should it remind you?" | Choose time |
| Time already passed | "That time has already passed. Pick a new one?" | Choose time |
| Notifications off | "Saved, but reminders can't ring. Notifications are off." | Turn on |
| Reminder couldn't be set | "Saved, but Tallele couldn't set the reminder." | Try again |

The receipt goes away on the next save, on a swipe, or when View or Change opens the item. TalkBack users get a "Hide" action instead of the swipe. The receipt has no timeout.

It survives force-stop and reboot: the capture id is kept in DataStore and the line is read back from Room.

"Reminder set" appears only while the reminder row has a `notificationWorkId`.

The quick reminder question takes the receipt's place; the two never show together. The success snackbars are gone; only save-failed and reminder-failed remain.

**(2) Files changed**
- `domain/model/AppSettings.kt` and `data/repository/AppSettingsRepository.kt`: `lastReceiptCaptureId` and `lastReceiptDismissed`, stored in DataStore.
- `data/local/dao/OrbitDaos.kt` and `data/repository/EntityRepositories.kt`: `observeById`.
- `ui/screens/home/CaptureReceipt.kt` (new):
  - the model, with `line()`, `action()` and `openTarget()`;
  - time formatting;
  - `CaptureReceiptReader`;
  - `rearmReceiptReminder()`.
- `ui/screens/home/HomeCaptureViewModel.kt`: the `receipt` StateFlow, the id written after save, dismiss, choose time and retry. The success `HomeMessage`s are removed.
- `ui/screens/home/HomeSendFeedback.kt`: `SaveReceipt` (SoftGlassSurface, `SwipeToDismissBox`, polite live region) replaces `SavedConfirmation`.
- `ui/screens/home/HomeScreen.kt`: mounts the receipt and wires its actions.
- `ui/navigation/OrbitApp.kt`: factory wiring and navigation to the item.
- `res/values{,-et,-ru}/strings_core_ui.xml`: the receipt strings.
- Tests:
  - new: `HomeCaptureViewModelTest`, `SaveReceiptTest`;
  - adjusted: `HomeCaptureProcessingStateTest`;
  - removed: `HomeReminderMessageTest`, because the mapping it tested is gone.
- `ScreenTour.kt`: `home-receipt-filed`, `home-receipt-reminder`, `home-receipt-needs-you`.

**(3) Tests**
- `HomeCaptureViewModelTest`:
  - "Saved." appears right after `send()`;
  - after filing, the line reads Filed with the Space name;
  - a reminder without `notificationWorkId` reads "isn't set yet", and Try again arms it;
  - the next save replaces the receipt, and dismiss hides it;
  - after recreating `OrbitContainer`, the identical receipt returns.
- `SaveReceiptTest`:
  - all ten lines, each with exactly one button;
  - "Reminder set" never appears without an alarm;
  - Hide works without swiping;
  - the today / tomorrow / date wording.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.ui.screens.home.*'`. 149 tests across home, capture, review, model and journey pass.

**(4) Needs a real phone**
- The plan's acceptance check:
  1. Save "Parking code 4826".
  2. Kill the app and reopen it: the receipt is unchanged.
  3. Tap View: it opens the thought.
- TalkBack: "Saved" is read first, then the filed line, without focus jumping.
- The swipe-to-dismiss feel.
- The largest font size.
- Turn on opens the notification settings.

**(5) Departures**
- **Two strings not in the Wording table,** for a task with no date: "Saved as a task." and "Filed in %1$s as a task."
- **"today" and "tomorrow"** reuse the existing `core_today` and `core_tomorrow` strings.
- **The time follows the app's time-format setting.** Robolectric screenshots show 12-hour time; 24-hour phones show "09:00".
- **`HomeMessage.ReminderFailed` is kept.** When creating a reminder throws, there is no receipt row to show it on.
- **A reminder that has already rung or been completed hides the receipt.** Otherwise it would wrongly read "isn't set yet".
- **The linked item's type is read from `suggestedType`, with an existence check.** The capture row stores only the linked id.
- **Choose time** opens the sort sheet for an unsorted thought. If a reminder already exists, it opens that reminder instead.
- **Try again re-arms the alarm** through `scheduleStored`, then records the outcome.
- **`SaveReceipt` takes a single `onAction(receipt, action)` callback** instead of five separate ones.
- **The Filed screenshot is driven by `acceptSuggestion`,** because AutoFiler is wired only in Step 5.
- **`core_home_saved_let_go` is kept** until Step 7.


### Step 5 — Act / ask / keep (5b, 5c, 5d; 5a came with Step 3)

Branch `plan/step-5`, commits `4af0ac8` (auto-filing), `749fdca` (ScreenTour shots).

**(1) What the user will notice.** A clear thought is filed by itself a moment after saving. "Clear" means High confidence in one of these: a task, a note with a Space found for it, or a reminder with a future time. The receipt updates in place, for example "Task for tomorrow." · Change.

When the analyzer is sure something is a reminder but the time is missing or already past, Home shows one question card in the receipt's place, with **Pick a time** and **Just keep it**:
- "Saved. What time should it remind you?"
- "That time has already passed. Pick a new one?"

The card comes from the stored outcome, so it survives a kill. "Just keep it" leaves the thought saved; if a reminder row exists, it is deleted and its alarm cancelled first. Everything else is kept quietly. "Change" opens the item and deletes nothing. "Sort right after saving" now sits under an "Advanced" heading; while it is on, Tallele files nothing by itself and the sort sheet opens as before.

**(2) Files changed**
- `domain/capture/AutoFiler.kt`:
  - `listen(scope, events)`;
  - a `paused` gate for `sortRightAfterSaving`;
  - thoughts tied to a Calendar day are left for the sort sheet.
- `domain/capture/CaptureResolution.kt`: `keep(captureId)`, i.e. "Just keep it", which goes through `undo()` when a reminder row exists.
- `OrbitApplication.kt` (`OrbitContainer`): `autoFiler`, listening on `applicationScope`.
- `MainActivity.kt`: `applyPending()` runs after `analyzePending()`.
- `ui/screens/home/HomeCaptureViewModel.kt`:
  - the question is derived from the stored receipt;
  - new `keepQuestionThought()`;
  - `afterSaving()` no longer has an AskForTime branch;
  - the quick-reminder path is removed.
- `ui/screens/home/HomeSendFeedback.kt`: `QuickReminderCard` gets the "Pick a time" and "Just keep it" buttons and text that matches the reason.
- `ui/screens/home/HomeScreen.kt`: the card takes the receipt's place, and "Pick a time" uses the receipt's ChooseTime routing.
- `ui/navigation/OrbitApp.kt`: factory wiring.
- `ui/screens/settings/CaptureReminderSettings.kt`: new `ReminderAdvancedSettings`.
- `ui/screens/settings/SettingsHome.kt`: calls `ReminderAdvancedSettings`.
- Strings:
  - new: `receipt_action_keep`, `settings_reminders_advanced`;
  - reworded: `settings_sort_right_after_saving_body`.
- Tests:
  - new: `AutoFilerRoomTest`;
  - updated: `HomeCaptureViewModelTest`, `HomeAfterSavingTest`, `HomeCaptureProcessingStateTest`, `ScreenTour.kt`.

**(3) Tests.** `AutoFilerRoomTest` has 9 cases:
- **Filing.** Act files the note, sets Filed and `decidedAt`, and records no learning events.
- **Undo.** Undo restores Saved and deletes the note, and the thought is never re-filed.
- **Live listener.** It files a thought as soon as its analysis lands.
- **Late events.** An `Analyzed` that arrives after Undo is ignored.
- **TimeUnclear.** It asks; "Just keep it" then sets Saved.
- **Blocked reminder.** "Just keep it" on a NotificationsOff thought deletes the reminder row; no alarm and no backup work remain.
- **Filed note.** "Just keep it" leaves an already filed note alone.
- **Sort right after saving.** It pauses filing, and the thought is filed later once the setting is turned off.
- **Calendar day.** A thought tied to a Calendar day is left for the sort sheet.

Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.domain.capture.*' --tests 'com.orbit.app.ui.screens.home.*'`. 146 step tests pass, plus 91 tests in the touched packages; `CaptureInboxRoomTest` and `ReviewChangeUndoRoomTest` are unchanged and green.

**(4) Needs a real phone**
- The 20-thought acceptance run, counting unneeded questions and misfilings separately.
- "Just keep it" on a blocked reminder, followed by `dumpsys alarm`.
- Force-stop right after saving: the next start files the thought exactly once.
- TalkBack on the question card.

**(5) Departures**
- **Thoughts tied to a Calendar day are not auto-filed.** The sort sheet opens for them, and it refuses processed captures, so filing them first would break the Calendar "+" flow.
- **While "Sort right after saving" is on, AutoFiler does nothing,** and `decidedAt` stays null. Turning the setting off files those thoughts on the next start.
- **The switch lived in `SettingsHome.kt`'s RemindersGroup,** not in `CaptureReminderSettings.kt` as the plan says. The Advanced composable is in `CaptureReminderSettings.kt`, called from `SettingsHome.kt`.
- **5d needed no new code.** Step 4 already routes Change to ItemDetail.
- **The card reuses the receipt strings,** and there is no "Remind me at X?", because a clear future time is now set automatically.
- **Left unused for later cleanup:** `needsImmediateTimeQuestion()` and the old `core_home_quick_reminder_*` strings.


### Step 6 — Review becomes "Needs you" (integrator)

Commit `f0a9643` on `tallele-plan-all`.

**(1) What the user will notice.** The fourth tab is now called "Needs you" in all three languages: "Vajab sind" in Estonian, "Ждёт вас" in Russian. It shows a card only for a thought that really needs an answer. Each card asks one thing:

| Situation | Question | Buttons |
| --- | --- | --- |
| No usable time | "When should this remind you?" | Today 18:00 (only while it is at least 15 min ahead) · Tomorrow 09:00 · Pick a time · Just keep it |
| Time already passed | "This time has already passed." | Pick a new time · Just keep it |
| Notifications off | "Reminders can't ring while notifications are off." | Turn on · Just keep it |
| Reminder not set | "Tallele couldn't set this reminder." | Try again · Just keep it |

The screen is honestly empty most days. It then reads "Nothing needs you right now." with "Today Tallele kept N and filed M."

Below that comes Today: plain rows with a quieter heading, three at most, then Show more. A task is never shown as a card.

Behind one "More" row sit the tools: earlier tasks, To sort with Sort one by one, the weekly look back, and open loops. Ask and Breathe moved into the header's overflow menu.

The tab gets a small red dot only when something failed: a missed time, or notifications off. It never shows a count.

On Needs you and Spaces, scrolled content fades out over 24 dp just above the bar, so no words show through it.

**(2) Files changed**
- `ui/screens/review/ReviewToSort.kt`:
  - `NeedsYouItem` and `buildNeedsYou()` (a filter on the stored outcome; it finds the linked reminder);
  - `savedTodayCounts()`;
  - `needsYouSomethingFailed()`.
- `ui/screens/review/ReviewViewModel.kt`:
  - new state: `needsYou`, `keptToday`, `filedToday`;
  - `nothingWaiting = needsYou.isEmpty() && dueToday.isEmpty()`;
  - `somethingFailed`;
  - new actions: `remindAt`, `keepThought`, `makeTask`, `retryReminder`. Each card answers on its own, so tapping two cards quickly works.
- `ui/screens/review/ReviewRedesign.kt`:
  - `ReviewHeader` has one overflow menu holding Ask and Breathe;
  - `reviewSummary` reads "N to decide · N today";
  - new `NeedsYouGroup` and `NeedsYouMoreRow`;
  - `TodayGroup` takes a `previewSize` parameter.
- `ui/screens/review/ReviewScreen.kt`: new layout (Needs a decision → Today → More); callbacks for the card actions.
- `ui/screens/review/ReviewCards.kt`: `NeedsYouEmptyCard` replaces `AllSortedCard`.
- `ui/screens/review/ReviewLayout.kt`: `NeedsYouTodayPreviewSize = 3`.
- `ui/components/OneQuestionCard.kt` (new): used by Home's `QuickReminderCard` (now a thin wrapper in `HomeSendFeedback.kt`) and by every Needs you card.
- `domain/capture/CaptureDecision.kt`:
  - `QuickEveningTime` (18:00) and `QuickMorningTime` (09:00), as the plan asks;
  - `quickReminderTimes()`.
- `ui/components/FloatingBottomNavigation.kt`:
  - `alertDestinations` draws the dot, with a TalkBack state "Something didn't work";
  - `ScrollFadeHeight` and `ScrollFadeLength`.
- `ui/components/MotionModifiers.kt`: `orbitScrollEdgeFade(bottomFadeLength)`.
- `ui/navigation/OrbitApp.kt`: observes captures for the dot and wires the card actions. "Pick a time" opens the reminder itself if one exists, otherwise the sort sheet on its reminder setup.
- `ui/screens/spaces/SpacesScreen.kt`: the same 24 dp fade on its two lists.
- Strings (en/et/ru):
  - `navigation_review` and `core_review_title` → "Needs you";
  - 16 new `needs_you_*` strings and one plural;
  - three strings that named the old "Review" tab: the missed-reminders notification, the Spaces "waiting" count, and "Back to …".
- Tests:
  - new: `ReviewNeedsYouTest` (5);
  - `ReviewScreenJvmTest`: updated, plus a new card test;
  - `ReviewToSortAndLayoutTest`: updated;
  - `FloatingBottomNavigationJvmTest`: a new dot test;
  - `TabJourneyJvmTest`, plus two androidTest files, for the new label;
  - `ScreenTour.kt`: `review-needs-you-empty`, `review-needs-you-two-cards`, `review-more-expanded`, and its Review steps updated for More and the overflow menu.

**(3) Tests**
- `ReviewNeedsYouTest`:
  - three Saved thoughts and one NeedsYou thought give one card; "Just keep it" empties it, and the thought stays Saved and Inbox with `decidedAt` set;
  - a quick time sets the reminder, stores ReminderSet, and removes the card;
  - a task due today is a row, not a card, and the kept count is 1;
  - `buildNeedsYou` reads only NeedsYou, skips archived thoughts and finds the linked reminder;
  - 18:00 is offered only while it is ahead.
- `ReviewScreenJvmTest.eachNeedsYouCardAsksOneThingAndKeepItAnswersIt`.
- `FloatingBottomNavigationJvmTest.needsYouShowsADotOnlyWhenSomethingFailed_neverACount`.
- `ReviewChangeUndoRoomTest`: unchanged and green.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.ui.screens.review.*' --tests 'com.orbit.app.ui.components.*'`.
- Full suite after this step: 709 tests, 0 failures. Lint: 0 errors.

**(4) Needs a real phone**
- The plan's acceptance check:
  1. On a fresh install, save three clear thoughts and "remind me about the car".
  2. Needs you shows one card only if the analyzer is High-confidence on it (see the open question on Medium).
  3. Answer with Tomorrow 09:00: the screen should read "Nothing needs you right now."
- The dot appears when notifications are turned off and goes away once they are back on and the app is reopened. The reconcile runs on app start, not on resume.
- TalkBack reading each card, and the dot's state.
- The fade above the bar while scrolling, with live blur on.

**(5) Departures**
- **`needsYou` holds `NeedsYouItem`, not `ToSortItem`.** A card needs the linked reminder id and the reason, and ToSortItem only covers Inbox thoughts. A blocked or missed reminder belongs to a Processed thought.
- **TypeUnclear has a card ("Make it a task"), but nothing writes TypeUnclear** (see Step 3/5a).
- **The empty state hides "Today Tallele kept 0 and filed 0."** when both counts are zero.
- **The fade is a content mask, not a scrim.** It is applied to the scrolling lists on Needs you and Spaces, not drawn behind the bar. A scrim behind the bar would have hidden the colour the live blur needs (Step 8).
- **`reviewSectionOrder()`** (the time-of-day order) is no longer used by the screen. It is kept, still tested, and is a candidate for removal.
- **Copy outside the Wording table:** the card questions, their buttons, the dot's TalkBack text, and the summary plural "%d to decide".


### Step 7 — New first run

Branch `plan/step-7`, commits `60b2da5` (first run), `a5e1698` (ScreenTour).

**(1) What the user will notice**
- **Home first.** A fresh install opens straight on Home, with no guide and no dialog.
- **Two first-run lines.** Above the box: "Write anything. Tallele keeps it safe and sorts what it can." Below it: "Everything stays on this phone unless you turn on Gemini or use voice." Both go away after the first save.
- **Starter Spaces.** They are already there; nobody is asked to pick them.
- **First-receipt tip.** A one-time tip under the first receipt, matching what happened: kept, filed, or reminder set. A question gets no tip.
- **Notifications.** Home asks only when a reminder saved from Home needs them, never on opening the app.
  1. First a single line: "So this reminder can ring at 09:00, Tallele needs to show notifications."
  2. Then the system dialog, shown once.
  3. If the user allows it, the reminder is re-armed.
- **Guide.** Still available from Settings, with new copy on pages 1 and 2.

**(2) Files changed**
- `ui/navigation/OrbitDestination.kt`: `initialOrbitRoute()` always returns Home.
- `ui/navigation/OrbitApp.kt`: seeds the starter Spaces silently on first launch and passes `firstRun` to Home.
- `data/local/StarterSpaces.kt`: new `all(now)`.
- `ui/screens/tutorial/TutorialSpaceSetupViewModel.kt`: `seedStarterSpacesSilently()`. The picker shows whenever no Spaces exist.
- `AppSettings.kt` and `AppSettingsRepository.kt`: two new fields.
  - `hasSeenFirstReceiptTip`: existing users count as having seen it.
  - `hasAskedForNotifications`.
- `ui/screens/home/HomeCaptureViewModel.kt`:
  - new `firstReceiptTip` and `notificationPriming`;
  - `send()` marks first run complete in the same DataStore write.
- `ui/screens/home/HomeSendFeedback.kt`: `FirstReceiptTip` and `NotificationPrimingLine` (polite live regions).
- `ui/screens/home/HomeScreen.kt`: the first-run lines, the tip or priming line, and the permission launcher.
- `strings_screens.xml` (en/et/ru): six new strings, plus new guide copy.
- `strings_redesign.xml` (en/et/ru): `core_home_saved_let_go` removed, since nothing uses it any more.
- Tests:
  - new: `FirstRunJourneyJvmTest`, `HomeFirstRunTest` (10), `FirstReceiptTipTest`;
  - extended: `TutorialSpaceSetupViewModelTest`, `StarterSpacesTest`;
  - updated: `FirstTimeTutorialNavigationTest`, `TabJourneyJvmTest`, `FirstTimeTutorialScreenTest` (androidTest).
- ScreenTour: new `00-first-launch` and `00-first-receipt-tip`; the needs-you shot now shows the priming line.

**(3) Tests.**
- Step classes: 31 tests, 0 failures.
- Changed packages: 140 tests, 0 failures.
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.journey.*' --tests 'com.orbit.app.ui.screens.home.*' --tests 'com.orbit.app.ui.screens.tutorial.*'`.

**(4) Needs a real phone**
- Android 13+ notification flow:
  - the line appears first, then the dialog;
  - the dialog never shows on a cold start and is never shown twice;
  - allowing it turns the receipt into "Reminder set".
- A fresh install: Home appears at once and the starter Spaces are there.
- The largest font size, and the keyboard open on a small phone.
- Estonian and Russian line lengths.

**(5) Departures**
- **`home_first_line_below` wording.** It reads "…unless you turn on Gemini or use voice." The Wording table's "cloud AI" contains "AI", which the run rules forbid, and Settings already calls the option "Gemini".
- **There is no "Later" tile.** Spaces has a "To sort" tile, so the kept tip reads "…You'll find it in To sort and in Search." The tile name comes in through a placeholder.
- **`initialOrbitRoute` location.** It lives in `OrbitDestination.kt`, not `OrbitApp.kt`.
- **Where first run is marked complete.** It is set in the ViewModel's DataStore write, not through `onSettingsChanged`, so a stale settings copy can't overwrite it.
- **"Spaces › Add starter Spaces" does not exist.** The guide's picker is offered on replay whenever no Spaces exist.
- **Silent seeding gate.** Seeding runs only before the first save and only when no Spaces exist.
- **Notifications.** The sort sheet's own request is unchanged, since it already asks only on save. Home has its own one-line-then-dialog request, asked once, tracked by the extra field `hasAskedForNotifications`.
- **Guide keys.** The plan's `tutorial_page1`/`tutorial_page2` keys don't exist. Instead the existing `tutorial_write_title`, `tutorial_suggest_title` and `tutorial_suggest_body` were changed; the body no longer contradicts the title.


### Step 8 — Glass and default look

Branch `plan/step-8`, commits `4583564` (glass), `74dfac4` (macrobenchmarks).

**(1) What the user will notice.** With Clear glass on Android 13 or newer, three surfaces now blur the coloured background behind them: the Home capture box, the bottom bar, and sheets/dialogs. Cards and list rows do not blur. The bar uses a 20 dp blur with a tint of at least 0.55, so scrolled text behind it shows as colour, not words. New installs start on Violet Mist; existing users keep the background they have. Nothing changes from before in these cases: Soft glass, Solid, "Remove animations" turned on, or Android below 13.

**(2) Files changed**
- `ui/components/GlassSurface.kt`:
  - live blur is limited to `LiveGlassStyles` (Prominent, HomeCapture, NavigationBar, Sheet), on API 33+ and only when animations are on;
  - bar blur 20 dp with tint ≥ 0.55;
  - top edge 0.24 light / 0.14 dark on the live styles;
  - `ModalSurface` now uses `LiveGlassSurface`;
  - the live surface blocks touches like the soft one;
  - live sheets keep an opaque underlay.
- `ui/components/FloatingBottomNavigation.kt`: the bar uses `LiveGlassSurface`.
- `ui/screens/home/HomeScreen.kt`: the capture box uses `LiveGlassSurface`.
- `ui/components/OrbitBackground.kt`: palette and light-pool strengths are exposed to tests; drawing is unchanged.
- `domain/model/AppSettings.kt`: default `backgroundPreset` is now `VioletMist`.
- `data/repository/AppSettingsRepository.kt`: a stored legacy default still maps to Ink & Paper.
- Tests:
  - `GlassContrastTest` (new);
  - `GlassRolePolicyTest` (+6);
  - `AppSettingsAppearanceTest` (+1);
  - `ScreenTour.kt` gets a note that its PNGs show the soft fallback.
- `macrobenchmark/…/HomeScrollBenchmark.kt` and `ReviewScrollBenchmark.kt` (new): `FrameTimingMetric`, target P90 < 16 ms.

**(3) Tests.** `GlassContrastTest` checks every preset × light/dark × live style × glass strength × text colour, and all reach 4.5:1 or better. Policy tests cover:
- API 31/32 off and 33 on;
- animations removed;
- the exact set of live styles;
- bar blur and tint;
- top edge;
- sheet underlay.

Command:

`./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.ui.components.Glass*' --tests 'com.orbit.app.domain.model.AppSettingsAppearanceTest'`

These existing tests also exercise the live path and pass:
- `TabJourneyJvmTest`
- `BottomNavigationVisibilityTest`
- `FirstTimeTutorialNavigationTest`

`:macrobenchmark:compileBenchmarkBenchmarkKotlin` builds.

**(4) Needs a real phone**
- The whole 8f checklist.
- Running both benchmarks.
- Whether sheets really blur: they draw in their own window, and Haze 1.7.2 may not reach it. If it doesn't, they look solid, as today.

**(5) Departures**
- **Opaque underlay under live sheets.** Without it, the first render showed the sheet's text over the list behind it.
- **Old-default users pinned to Ink & Paper.** Some users have the old default stored. Without the pin, they would have silently moved to Violet Mist.
- **Top edge.** The edge gradient's middle stop is capped at the top value.
- **Touch blocking.** Added to the live surface so taps don't pass through the bar's gaps.
- **Line numbers drifted.** The bar is at line 75, not 82. `ModalSurface` is at 212, not 215.
- **Reduced motion.** "Remove animations" reuses `rememberReducedMotion()`; `ReducedMotion.kt` is unchanged.
- **No new ScreenTour shots.** Existing shots already cover the changed surfaces.
- **Left for follow-up:**
  - `docs/codex/LUMA_GLASS_SURFACE_SYSTEM.md` still describes the old gate.
  - The Settings glass preview uses the Subtle style, so it never shows live blur.


### Step 9 (part 1) — 9.1, 9.2, 9.3, 9.4, 9.5, 9.7, 9.8, 9.10, 9.11

Branch `plan/step-9`, commits `15eec97`, `d9827cc`, `b4e9ec4`, `816f0fc`, `5becaaa`, `694435d`, `e53aed4`, `6369cc3`.

**(1) What the user will notice**
- **Voice (9.1).** On Android 12 or newer, where the phone can recognise speech on the device, the Home mic listens in place and the words stay on the phone. The box shows "Listening…", and a second tap stops listening. On other phones, the first tap shows once "Voice may use Google's servers on this phone." before the phone's speech screen opens. If voice fails, Home says why.
- **Search (9.2–9.4).**
  - Results start from the first letter.
  - Before typing, the five newest saved items show.
  - With nothing found, the screen reads "No matches for "x"." and offers a "Search archived too" switch.
  - The last filter chip peeks, cut through its middle.
- **New Space (9.5).** Only a name is needed. Colour and icon are picked from the name and sit behind a folded "Color and icon" row.
- **Item details (9.7).** A note shows only Type and Space. A task keeps its date and status under "More".
- **Calendar (9.8).** "Nothing planned" appears once per day: alone on an empty day, or after the last item. The repeated "Nothing planned until …" gap rows are gone.
- **Backup (9.10).** The buttons say "Create backup" and "Restore backup". The .json note and the replace rule sit under them in small text.
- **Ask (9.11).** The sheet opens with the question box, then the answer, then the overview.

**(2) Files changed**
- `AndroidManifest.xml`: `RECORD_AUDIO`.
- Voice:
  - `capture/VoiceCapture.kt`: route choice, once-only notice, error reasons, `OnDeviceVoiceSession`.
  - `ui/screens/home/VoiceInput.kt` (new): the mic flow.
  - `HomeScreen.kt`: uses the mic flow and shows "Listening…".
- Settings and navigation:
  - `AppSettings.kt` and `AppSettingsRepository.kt`: `hasSeenVoiceCloudNotice`.
  - `OrbitApp.kt`: passes the notice flag to Home.
- Search:
  - `domain/search/LocalSearch.kt`: 1-letter minimum and `recent()`.
  - `SearchViewModel.kt`: recent items and an archive-aware chip filter.
  - `SearchScreen.kt`: first-letter search, recent list, no-match state with the switch, chip padding.
- Spaces:
  - `data/local/SpaceNames.kt`: `autoColorIndex` and `autoIconIndex`.
  - `ui/screens/spaces/SpacesScreen.kt`: name-first editor.
- `ItemDetailScreen.kt`: `itemDetailFields()` and the "More" row.
- `CalendarAgenda.kt` and `CalendarDayTimelineView.kt`: one filler line per day.
- `LocalDataSettings.kt`: backup wording.
- `SituationAiSheet.kt`: answer first.
- Strings in `values`, `values-et` and `values-ru`.
- `ScreenTour.kt`: new `s9-*` steps.

**(3) Tests**
- New:
  - `SearchContentJvmTest`
  - `SpaceAutoStyleTest`
  - `SpaceEditorJvmTest`
  - `ItemDetailFieldsTest`
  - `LocalDataBackupWordsJvmTest`
  - `SituationSheetOrderTest`
  - `VoiceCloudNoticeJvmTest`
- Extended:
  - `VoiceCaptureTest`
  - `LocalSearchTest`
  - `SearchFilterTest`
  - `SearchFeedbackStateTest`
  - `CalendarAgendaTest`
- The affected packages: 202 tests, 0 failures.

**(4) Needs a real phone**
- On-device dictation on Android 12 or newer: the microphone prompt, and dictation in airplane mode.
- The fallback path when the microphone is refused or the language isn't on the device.
- The once-only notice on a phone without on-device recognition.
- The chip peek in Estonian and Russian at large font.

**(5) Departures**
- **New `RECORD_AUDIO` permission (9.1).** On-device recognition can't work without it. It is asked for only on the first mic tap.
- **Voice notice wording (9.1).** It uses the Step 9 text, "Voice may use Google's servers on this phone." The Wording table's version ends "…even with cloud AI off.", and the run rules forbid "AI".
- **Extra voice strings (9.1).** "Listening…", "Continue", "Nothing was heard. Try again." and "Voice isn't available on this phone right now."
- **One-letter search (9.2).** The minimum also had to change in `LocalSearch.kt`.
- **Recent items (9.2).** "Last 5 saved thoughts" shows the newest notes, tasks and reminders. Search deliberately excludes raw captures, and an existing test protects that. So the plan's claim that Search "already indexes unsorted thoughts" is not true in this code.
- **Debounce (9.2).** It was already 150 ms.
- **Chip row (9.4).** It is a scrolling `Row`, not a `LazyRow`, so the 48 dp is end padding inside the scroll.
- **"Color and icon" (9.5).** Spelled to match the app's existing "Color" label.
- **Reminders (9.7).** The meaningless "Status" row on reminders is also hidden.
- **"Once per day" (9.8).** Read as removing the gap rows too.
- **Unused strings.** The old search and calendar strings are left unused, to keep the merge simple.


### Step 9 (part 2) — 9.9 Settings order and 9.6 Space "+" in place (integrator)

Commits `945e198` (9.9) and `4849eec` (9.6).

**(1) What the user will notice**
- **9.9 Settings order.** The Settings overview now reads, from the top:
  - the profile row;
  - **Reminders**: the health row from Step 2, then a folded **Advanced** row. It opens to show both behaviour switches: "Sort right after saving" and "Open the keyboard on Home".
  - **Look**: one row showing the current background. It opens a Look page with Theme, Overall color, Text color, Background and Glass, and "Reset the look" at the bottom. Pages opened from Look go back to Look.
  - Language and time;
  - AI;
  - Your data;
  - About.
- **9.6 Space "+".** A Space's "+" opens a small sheet over the Space: "Add to Work", one text field and Save thought. The thought is saved exactly as one from Home, with that Space chosen, so Tallele files it or keeps it as usual. You stay on the Space; before, "+" sent you to Home.

**(2) Files changed**
- 9.9:
  - `ui/screens/settings/SettingsHome.kt`: the new order. The Look row replaces the inline appearance group. The appearance controls and reset moved into the new `AppearanceLookSection`. `RemindersGroup` keeps only the health row and Advanced.
  - `ui/screens/settings/CaptureReminderSettings.kt`: `ReminderAdvancedSettings` is collapsible and holds both switches.
  - `ui/screens/settings/AppearanceSettings.kt`: new `AppearanceMenuSection.Look`, and `onOpenSection`.
  - `ui/screens/settings/SettingsScreen.kt`: back from a page opened from Look returns to Look.
  - Strings: `settings_look_title` and `settings_look_subtitle` (en/et/ru).
- 9.6:
  - `ui/screens/spaces/SpaceCaptureSheet.kt` (new).
  - `ui/navigation/OrbitApp.kt`: the three Space screens open the sheet instead of `returnHomeWithSpaceCapture`. The save runs on `applicationScope` through `captureInbox.save(text, spaceId)`.
  - Strings: `space_capture_title` and `space_capture_hint`.
- Tests:
  - `SettingsScreenJvmTest` and `RemindersGroupJvmTest` updated;
  - new `SpaceCaptureSheetJvmTest` (2);
  - ScreenTour adds `a-settings-look`, `space-capture-sheet`, and an Advanced-opened `settings-reminders-advanced`, and its Settings walk is updated.

**(3) Tests**
- Command: `./gradlew :app:testDebugUnitTest --tests 'com.orbit.app.ui.screens.settings.*' --tests 'com.orbit.app.ui.screens.spaces.*'`. All pass.
- The ScreenTour step checks that after Save the Work Space is still open and the thought exists.

**(4) Needs a real phone**
- **9.9:**
  - "fits on one screen at default font" (see departures);
  - back from Background/Glass returns to Look.
- **9.6:**
  - the keyboard opens with the sheet;
  - the sheet clears the keyboard on small phones;
  - the saved thought later shows up filed in the Space, or under To sort.

**(5) Departures**
- **9.9: Language and time stays as its own group, after Look.** The plan's order (Reminders · Look · AI · Data · About) does not place it, and folding it into Look would hide it.
- **9.9: the overview does not fully fit.** At 411×891 dp and default font, everything down to Your data fits; About sits just below the fold.
- **9.9: "Reset the look" moved to the Look page,** since it only resets the look.
- **9.6: `returnHomeWithSpaceCapture` and Home's Space-context handling are no longer called** from the Space screens. They are left in place, as cleanup candidates.
- **9.6: no receipt is shown after a save from a Space.** The sheet just closes; the receipt under Home's capture box is only for thoughts saved on Home.


## 4. Every departure from the plan

Each item is explained in its step report above.

**Step 0**
- The baseline was 578 tests, not 573.

**Step 1**
- **Time-zone re-anchoring is new code.** The plan expected a plain re-arm to be enough, but the code stores a repeat's pending occurrence as an instant. Reconcile now moves it to the chosen local time, so 09:00 stays 09:00 after a zone change (`ReminderRepeat.kt`, `ReminderRescheduleWorker.kt`).
- One line in `CaptureReminderSettings.kt`, so the new enum value compiles.
- A `<queries>` manifest entry for Samsung's package, so its battery screen can be checked for.
- The process-death test asserts that alarms are identical and not doubled, rather than "nothing re-armed": the app-start reconcile re-arms in place.
- The battery tests are in `ReminderBatteryCheckTest`.

**Step 2**
- No dot on a "Capture & reminders" entry: that entry doesn't exist; the row sits directly on the overview.
- The re-check on resume already existed.
- `fixIntent` takes the issue (notifications vs channel), not the state.
- The Ready text is "Nothing is blocking reminders.", not the plan's Acceptance line "Reminders will ring."
- An extra "Check" button label.

**Step 3**
- The backup writes four fields (including `decidedAt`).
- A thought whose linked item is missing from the export is exported as Saved.
- A minimal `AutoFiler.applyPending()` was written in Step 3, so the recovery test could run. Step 5 wired it.
- `acceptSuggestion(automatic)` and `onlyIfUndecided` close the race where an Undo could be re-filed.
- "Recreate the container" in tests means fresh objects over a reopened on-disk database.
- The item-detail status toggle and the sort-sheet reminder path also write outcomes.
- A finished Brain Dump and `markCaptureReviewed` stay Saved.

**Step 5a**
- `decide()` follows the plan's code exactly, after the integrator sent the first version back: anything below High confidence is kept.
- Some of the plan's example rows don't match the real analyzer. For example, "call mum Tue 9:00" is read as a task, and the time reader never yields a past "yesterday 9:00". The tests use equivalent phrasings and hand-built rows.

**Step 4**
- Two strings were added for a task without a date.
- Today/tomorrow wording reuses existing strings.
- Time follows the app's time-format setting.
- `HomeMessage.ReminderFailed` is kept.
- A reminder that already rang hides the receipt.
- The linked item's type is read from `suggestedType`, with an existence check.
- `SaveReceipt` has one `onAction` callback.
- The Filed screenshot is driven by `acceptSuggestion`.

**Step 5**
- Thoughts added from a Calendar day are not auto-filed. The sort sheet opens for them as before.
- While "Sort right after saving" is on, AutoFiler does nothing. Those thoughts are decided after it is turned off.
- The Advanced switch moved via `SettingsHome.kt`.
- 5d needed no new code.
- The question card reuses the receipt's texts.

**Step 6** (integrator)
- `needsYou` holds a `NeedsYouItem` that carries the linked reminder.
- The empty state hides "kept 0 and filed 0".
- The 24 dp fade is a content mask on the Needs you and Spaces lists, not a scrim behind the bar; a scrim would remove the colour the glass needs.
- `reviewSectionOrder()` is unused by the screen.
- The card questions and buttons are copy outside the Wording table.

**Step 7**
- The first-run line says "…unless you turn on Gemini or use voice." (no "AI").
- The kept tip says "To sort", because there is no "Later" tile.
- `initialOrbitRoute` lives in `OrbitDestination.kt`.
- First run is marked complete in the ViewModel's DataStore write.
- The starter-Space picker stays reachable only through the replayed guide (there is no "Add starter Spaces" in Spaces).
- An extra `hasAskedForNotifications` setting.
- The guide copy changed in the existing `tutorial_write_title`, `tutorial_suggest_title` and `tutorial_suggest_body`.

**Step 8**
- An opaque underlay under live sheets.
- Users on the stored legacy default stay on Ink & Paper.
- The edge gradient's middle stop is capped.
- The live surface blocks touches.
- No new ScreenTour shots; existing shots cover the changed surfaces.
- `docs/codex/LUMA_GLASS_SURFACE_SYSTEM.md` is not updated.

**Step 9**
- `RECORD_AUDIO` permission added (9.1).
- The voice notice drops "even with cloud AI off" (9.1).
- Extra voice strings (9.1).
- The one-letter minimum also had to change in `LocalSearch` (9.2).
- "Last 5 saved" shows items, not raw thoughts (9.2).
- The chip row is a scrolling `Row`, not a `LazyRow` (9.4).
- "Color and icon" spelling (9.5).
- The reminder "Status" row is hidden too (9.7).
- Calendar gap rows were removed (9.8).
- Language and time stays as its own group (9.9).
- About sits just below the fold (9.9).
- Reset moved to the Look page (9.9).
- A Space save shows no receipt; the old route back to Home is kept but unused (9.6).

## 5. Open questions for you

1. **Plan file privacy.** The plan was saved word for word, as you asked. Its header has the plan author's handle, and its last section quotes a review under the same name. The privacy checker passes, and I can't tell whether that handle is yours. If it is a colleague's, it should be replaced with a role label.
2. **Explicit Estonian reminders are kept, not asked.**
   - The local analyzer scores "tuleta mulle … meelde" one step lower than English "remind me" or Russian "напомни". The plan's `decide()` keeps everything below High, so these never get a time question.
   - Russian "напоминание: …" behaves the same.
   - The fix belongs in the analyzer, which this run did not touch. Should Estonian get the same signal weight?
3. **Nothing produces "TypeUnclear".** The plan's table mentions it, but its `decide()` has no source for it. The Needs you card for it ("Make it a task") exists but never appears. Keep it, or drop the reason?
4. **Notes are never auto-filed by the local analyzer.** It never gives a note High confidence, so only Gemini can file a note on its own. Tasks and reminders with clear times do file.
5. **Wording without "AI".** The Wording table's `voice_cloud_notice` and `home_first_line_below` both contain "AI", which the run rules forbid.
   - Used instead: "Voice may use Google's servers on this phone."
   - Used instead: "Everything stays on this phone unless you turn on Gemini or use voice."
   - Are these right?
6. **The microphone permission.** On-device voice (9.1) needs `RECORD_AUDIO`, asked on the first mic tap. OK to ship?
7. **Search doesn't find kept thoughts.** The plan says Search "already indexes unsorted thoughts". It doesn't: raw captures are deliberately excluded, and a test protects that. A kept thought is findable only through the Spaces "To sort" tile. Should Search include kept thoughts?
8. **Default background for some existing users.** Violet Mist is the new default. Users who never saved any setting have no stored background, so they will see Violet Mist after updating. Users whose stored value is the old default stay on Ink & Paper. Is that acceptable?
9. **Settings doesn't quite fit one screen.** At 411×891 dp and default font, About is just below the fold. Fold Language and time into Look, or trim the two footers?
10. **Leftovers to remove.**
    - The old Home route for a Space "+" (`returnHomeWithSpaceCapture` and the Space-context banner).
    - `reviewSectionOrder()`.
    - `needsImmediateTimeQuestion()`.
    - About 24 unused strings.

    All are behaviour-neutral; remove them in a cleanup pass?
11. **Notifications turned back on.** The NeedsYou/NotificationsOff → ReminderSet change happens when the reconcile runs, which is on app start, not on resume. The plan's phone check 3 says it clears "without you touching the item". It does, but only after reopening the app. Add a reconcile on resume?

## 6. By-hand phone checklist (none of this has been done)

From the plan's Testing section, pre-filled with what is still to check.

**Hands-on checks (after Step 6 and again after Step 9; both now apply)**

- [ ] 1. **Fresh install, 20 real thoughts in a row.** Check:
  - all 20 are findable (note: kept thoughts are under Spaces › To sort, not in Search; open question 7);
  - no receipt says "Reminder set" without an alarm in `adb shell dumpsys alarm | grep com.tallele.app`;
  - no reminder rings twice.

  Write down the unneeded questions (target 0) and the misfilings (just count them). Try at least two Estonian reminders (open question 2).
- [ ] 2. **"remind me to call the bank tomorrow at 9", then force-stop, then reboot.** It rings at 09:00 ± 1 min, exactly once. This checks Step 1's exact alarm without any grant on Android 14+, and the reboot receiver.
- [ ] 3. **Notifications off in Android, then save another reminder.**
  - The receipt says it "can't ring".
  - Settings shows one red "Turn on" when a reminder is due within 24 h; otherwise the line is grey.
  - Needs you shows one card and the tab shows a dot.

  Then turn notifications back on and reopen the app: all three clear (open question 11).
- [ ] 4. **Settings › Look › Glass: Soft, then Clear,** on the default background (Violet Mist). The capture box and bar visibly change; cards do not.
- [ ] 5. **TalkBack.** Save a thought: you hear "Saved", then the filed line, with no focus jump. Also listen to a Needs you card, and to the tab's "Something didn't work".
- [ ] 6. **Largest font size.** Every receipt, Needs you card, the Settings health row and the Space capture sheet fit without clipping.

**Step 8f glass checks (keep a screen recording)**

- [ ] 8f.1 **Default preset, light and dark:** a Space with 30+ items, scrolled slowly under the bar.
  - The bar shows blurred colour, never a readable word.
  - The tab labels stay crisp.
  - Content fades out over 24 dp above the bar.
- [ ] 8f.2 The same, at 130% font and at the largest size.
- [ ] 8f.3 **Soft Dawn, Violet Mist and Night Glow:** three lines typed into the capture box are readable against the glass.
- [ ] 8f.4 **Open and close a sheet five times fast:** no stutter, no solid flash. The sheet may simply look solid if Haze can't blur into the sheet's window; see Step 8.
- [ ] 8f.5 **Glass › Soft, then Clear:** the change is obvious on the capture box and bar, and invisible on cards.
- [ ] 8f.6 **Developer options › Profile GPU rendering:** while scrolling Needs you, no bar crosses the green line. Then run `HomeScrollBenchmark` and `ReviewScrollBenchmark` (P90 < 16 ms). If Review fails, set `inputScale = HazeInputScale.Fixed(0.5f)` on the bar only.

**Per-step device checks from the step reports**

- [ ] **Reminders after reboot and after a time-zone change.** A daily 09:00 repeat rings at 09:00 local in the new zone.
- [ ] **`TIME_SET` and `TIMEZONE_CHANGED`** reach the receiver.
- [ ] **Samsung (One UI 7/8):** the "never sleeping apps" screen opens from Check; if not, the spelled-out path is shown.
- [ ] **Android 12/12L:** "Allow" opens exact-alarm access.
- [ ] **First run on Android 13+.**
  - The notification line comes first, then the system dialog.
  - Never on a cold start, never twice.
  - Allowing turns the receipt into "Reminder set".
- [ ] **Force-stop right after saving, then reopen.**
  - The thought is filed once.
  - The receipt and any question card come back.
  - The SQLite row shows the stored outcome.
- [ ] **Upgrade from the current build** on a phone with real data. The 10 → 11 migration keeps every thought as Saved, Filed or ReminderSet. No old thought shows in Needs you.
- [ ] **On-device voice in airplane mode.** Also check the one-time notice on a phone without on-device recognition, and that a refused microphone falls back.
- [ ] **A Space's "+".** The keyboard opens, the sheet clears it, and you stay on the Space.
- [ ] **Estonian and Russian line lengths** on Home's first-run lines, the receipts and the Needs you cards.
