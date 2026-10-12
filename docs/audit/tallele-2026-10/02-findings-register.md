# 02 · Findings register

Snapshot: `main` at `43a1642` (the commit the review used). Paths are relative to `app/src/main/java/com/orbit/app/`, and line numbers are taken from that snapshot.

## Columns

| Column | Meaning |
| --- | --- |
| **Ev** | Evidence type: **T** source trace · **T✔** source trace re-checked by hand · **AT** automated test · **N** not verified. No device or emulator was available. |
| **Sev** | Severity, ranked by trust and data impact: **C** critical · **H** high · **M** medium · **L** low |
| **Batch** | See `04-remediation-plan.md` |
| **Status** | Starts as the reconciled verdict and is updated as batches land (*Fixed in &lt;commit&gt;*, *Open*, *Design*, *Blocked*) |
| **Tests** | Test IDs added or changed by the fix |

Required fields that every row shares are not repeated in each one:

- **Source:** the review, or this audit for N-items.
- **Dependencies:** the linked IDs.
- **Device proof:** none locally.

---

## Inherited: F01–F14

| ID | Requirement | Actual (current code) | Code | Ev | Sev | Batch | Status | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| F01 | A kept thought is findable once in Search, and in its chosen Space | `LocalSearch.search/recent` read only notes, tasks and reminders. `LocalSearchTest.excludesRawAndProcessedCaptures` asserts this. Spaces never list Inbox captures. The copy promises "in Search". | `domain/search/LocalSearch.kt:45-88`; `ui/screens/spaces/SpacesViewModel.kt:190-198`; `res/values/strings_screens.xml:135` | T✔ | C | 1 | Fixed in `f7d3c47` | LocalSearchTest |
| F02 | Same intent leads to the same outcome in EN/ET/RU; a chosen Space is respected | Score is 0.55 + 0.12 × signals. ET "tuleta meelde" counts once while EN "remind" and RU "напомни" count twice. A note peaks at 0.67, so it is never filed, even into a chosen Space. | `domain/analyzer/CaptureAnalyzer.kt:236-241, 349, 386-414, 476-489`; `domain/capture/CaptureDecision.kt:41-60` | T | H | 2 | Fixed in `03c1384` | CaptureDecisionTest |
| F03 | Negated, quoted and conditional thoughts never act automatically | No negation handling. "don't remind me to pay tomorrow at 9" scores 0.91 and becomes Act(Reminder). | `CaptureAnalyzer.kt:191-241` | T | C | 2 | Fixed in `03c1384` | IntentGuardTest, CaptureDecisionTest |
| F04 | Each repeat occurrence rings on schedule even if the last was ignored or swiped (decided) | The claim on delivery sets `deliveredNotificationAt` only. Only `markDone` advances. Reconcile returns `None`. Calendar still projects future repeats. The contract doc claims otherwise. | `reminders/ReminderNotifier.kt:49-63`; `ReminderDeliveryPolicy.kt:44`; `ReminderRepeat.kt:104-134` | T✔ | C | 3 | Fixed in `6af1cc7` | ReminderDeliveryRoomTest |
| F05 | A reminder keeps the full original thought | `createReminder` sets no `notes`. The note and task paths keep the text, and so does the Brain Dump reminder. | `domain/usecase/ConfirmCaptureActionUseCase.kt:125-133` | T | H | 1 | Fixed in `f7d3c47` | ConfirmCaptureActionUseCase tests |
| F06 | Calendar, Needs you and Ask agree on date-only and earlier-today items | `AiSourceItem` has only `dueAt`. "today" requires `dueAt >= now`. Auto-filed tasks store only the day. | `domain/ai/SourceLinkedAi.kt:96-111, 235-247` | T | H | 3 | Fixed in `6d0b3b0` | LocalAiRetrieverTest, LocalRulesSituationAnalyzerTest |
| F07 | Type change never fails on an id collision; links, labels and Undo move | Insert keeps the same id; returns `Conflict` if the target table has it. Message: "…uses this identity." | `ui/screens/item/ItemTypeConversion.kt:37-62, 87-92, 104-190` | T✔ | H | 4 | Fixed in `980c945` | ItemTypeConversionLabelsTest, ItemTypeChangeUndoTest |
| F08 | Someday / Waiting for survive acceptance | `createTask` default `Open`. Only Brain Dump maps `lifeSignal`. | `domain/capture/CaptureResolution.kt:98-108`; `ui/screens/home/CaptureSortViewModel.kt:418-429` | T | M | 2 | Fixed in `03c1384` | AutoFilerRoomTest |
| F09 | Restore is check-and-replace in one step; late analysis cannot contaminate | Compare and replace run in separate transactions. `CaptureInbox.store` checks only status, so a restored capture with the same id receives another thought's suggestion, and AutoFiler can then file it with the wrong title. | `data/export/LocalDataRestorer.kt:59-75`; `domain/capture/CaptureInbox.kt:114-213` | T | H | 4 | Fixed in `59d8fbd` and `980c945` | BackupRoundTripRoomTest |
| F10 | Reset deletes everything and returns to first launch (decided) | Survive reset: AI history (with text snippets), corrections, learned rules, memory, all settings, the Gemini key, last-export/delivery prefs, the missed-summary notification, and `lastReceiptCaptureId`. | `ui/screens/settings/LocalDataToolsViewModel.kt:184-227` | T✔ | H | 4 | Fixed in `980c945` | LocalDataResetTest |
| F11 | Consent states that Gemini suggestions are filed automatically, names the pause, and is asked again (decided) | Text says Gemini can't change or schedule without confirmation, and says "text you choose" while every save is sent. Consent version is 1. | `res/values*/strings_release.xml:8`; `domain/model/AppSettings.kt:78-79` | T | H | 2 | Fixed in `03c1384` | consent version 2 |
| F12 | Calendar and Needs you follow the real date across midnight and resume | Calendar sets `today` once. Review recomputes `now` only when data changes. | `ui/screens/calendar/CalendarViewModel.kt:47, 67`; `ui/screens/review/ReviewViewModel.kt:261-280, 599` | T | M | 3 | Fixed in `6d0b3b0` | CalendarViewModelTest |
| F13 | Space "+" keeps the unsent draft until saved or discarded | Text lives in the sheet. Dismiss loses it; text typed during save is lost. | `ui/screens/spaces/SpaceCaptureSheet.kt:56-113`; `ui/navigation/OrbitApp.kt:766-797` | T | H | 1 | Fixed in `e0cdfd3` | SpaceCaptureViewModelTest |
| F14 | A note can be given a date | Schedule row shown only when the note already has a date. A test locks this in. | `ui/screens/item/ItemDetailScreen.kt:129-135` | T | M | 3 | Fixed in `6d0b3b0` | ItemDetailFieldsTest |

**Review extra check RS1 ("Retry reminder setup" honesty):** Confirmed. Fixed in `a8ccd9e` (ReminderSchedulingTest, LocalDataToolsMessageTest).
- Success is reported when no exception was thrown.
- A null alarm token, notifications being off and `Blocked` deliveries are all ignored.
- The button disappears after "still failing".
- Thought outcomes are not refreshed.
- Code: `LocalDataToolsViewModel.kt:230-264`, `reminders/ReminderRescheduleWorker.kt:116-119`. Evidence T, severity M, batch 3.

## Inherited: B1–B18

| ID | Requirement | Actual | Code | Ev | Sev | Batch | Status | Tests |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| B1 | Share, shortcut or tile during an edit never discards the edit | `popBackStack(Home)` runs with no check. The edit state dies with the back-stack entry. | `ui/navigation/OrbitApp.kt:154-174`; `ItemDetailScreen.kt:281-311` | T | H | 1 | Fixed in `59d8fbd` | |
| B2 | Receipt says what was stored | `Saved` + decided gives "Saved as a note." even when no note exists. | `ui/screens/home/CaptureReceipt.kt:83` | T✔ | H | 1 | Fixed in `f7d3c47` | CaptureReceipt tests |
| B3 | Type-change Undo says "Type change undone." | Uses `core_sort_undone` ("…back in To sort."). | `ui/screens/item/ItemDetailViewModel.kt:542` | T | L | 1 | Fixed in `f7d3c47` | |
| B4 | Kept thoughts count as waiting and are visible (decided) | Summary counts only questions and today. To sort is hidden under More. A test asserts `nothingWaiting` with a non-empty To sort. | `ReviewRedesign.kt:165-173`; `ReviewScreen.kt:309-353` | T | H | 1 | Fixed in `e0cdfd3` | |
| B5 | Past target or alert times are refused with a clear reason | Item page saves and says "Schedule updated."; the offset change is not checked; the sort-sheet picker has no `minDate`, and the result is a vague "not set up". | `ItemDetailViewModel.kt:176-232`; `CaptureSuggestionSheet.kt:585-612` | T | H | 3 | Fixed in `a8ccd9e` | ReminderPassedTimeTest, ReminderTimeCheckTest |
| B6 | Notification state re-checked on resume; clear only re-armed future reminders | Reconcile runs only on a cold `onCreate`. The dot comes from stored outcomes. | `MainActivity.kt:43-46`; `OrbitApp.kt:121-125` | T | M | 3 | Fixed in `a8ccd9e` | ReconcileOnResumeTest |
| B7 | Reset is a fresh start | Same root as F10 | – | T✔ | H | 4 | Fixed in `980c945` | LocalDataResetTest |
| B8 | A Space "+" thought is visible in its Space | Stays in To sort with `suggestedSpaceId`; only shows if AutoFiler files it | `SpacesViewModel.kt:190-198`; `AutoFiler.kt:76-81` | T | H | 1 | Fixed in `e0cdfd3` | SpaceCaptureViewModelTest |
| B9 | "Looking at this…" expires on time | Re-checked only on data change | `ReviewToSort.kt:59, 90` | T | L | 3 | Fixed in `6d0b3b0` | |
| B10 | Weekly look back survives concurrent changes | Every emission clears the summary; the effect is keyed only on page | `ReviewViewModel.kt:252-258, 537`; `ReviewWeekly.kt:108-110` | T | L | 5 | Fixed in `7978dda` | |
| B11 | "No Space" in Brain Dump overview | Option named `"Inbox"` shown raw | `CaptureSortViewModel.kt:747`; `BrainDumpFlowCoordinator.kt:260`; `BrainDumpOverview.kt:303` | T | L | 5 | Fixed in `7978dda` | |
| B12 | Learned-rule question localised | English sentence with raw enum names | `domain/usecase/ProposeLearnedRuleUseCase.kt:68-79` | T | L | 5 | Fixed in `7978dda` | LearnedRuleProposalGateTest |
| B13 | Calendar day doesn't end "Nothing planned" after items | Codified by `CalendarAgendaTest` | `CalendarDayTimelineView.kt:135-146` | T | L | 5 | Fixed in `daf6fff` | |
| B14 | Item in a hidden or archived Space shows its name | Hidden and archived Spaces dropped, so the item shows "No Space" | `ItemDetailViewModel.kt:669-670`; `ItemDetailScreen.kt:883-884` | T | M | 5 | Fixed in `7978dda` | |
| B15 | One-by-one shows a dated task's date | Uses only `reminderAt`, so it shows "No date" | `ReviewRedesign.kt:897-901` | T | L | 5 | Fixed in `7978dda` | |
| B16 | Closing the sort sheet is not a rejection | `cancelSuggestion → keepInInbox → recordRejected` | `CaptureSortViewModel.kt:232-261` | T✔ | L | 2 | Fixed in `f7d3c47` | |
| B17 | Sheet can't be hidden while saving | `confirmValueChange` returns true while performing; an invisible sheet is possible | `CaptureSuggestionSheet.kt:179-210` | T | M | 1 | Fixed in `e0cdfd3` | |
| B18 | Remove-label chip is a button "Remove label" | `selectable(Role.RadioButton)` with "#label ×" | `ui/screens/home/CaptureSortContent.kt:346-352, 509-512` | T | L | 6 | Fixed in `b5b08b6` | SortChipSemanticsJvmTest |

## Inherited: L1–L8

The layout rows below are confirmed in code. Every exact break point still needs a render: **N (render)**.

| ID | Code | Actual | Sev | Batch | Status |
| --- | --- | --- | --- | --- | --- |
| L1 | `CalendarScreen.kt:129-209` | Month and year in one row with buttons; no weight; year can wrap ("202 / 6" on the owner's phone) | M | 6 | Fixed in `b5b08b6` |
| L2 | `SettingsHome.kt:393-405` | Title has weight; value capped at 168 dp with ellipsis | L | 6 | No change needed (already handled) |
| L3 | `AiSettingsCard.kt:574-595` | Label has no weight; switch unlabelled; row not toggleable | M | 6 | Fixed in `b5b08b6` |
| L4 | `SpacesScreen.kt:1232-1249` | Centred title overlaps Cancel | L | 6 | Fixed in `b5b08b6` |
| L5 | `BrainDumpOverview.kt:168-188` | Two unweighted buttons | L | 6 | Fixed in `b5b08b6` |
| L6 | `CalendarDayTimelineView.kt:230, 304` | Fixed 76 dp, one line, can clip "PM" | M | 6 | Fixed in `b5b08b6` |
| L7 | `ItemDetailScreen.kt:497-511` | Title has no line limit next to Cancel/Save | L | 6 | Fixed in `b5b08b6` |
| L8 | `SpacesScreen.kt:1487-1523` | Move dialog not scrollable | M | 6 | Fixed in `b5b08b6` |

The review's other per-screen observations (§6–§8) are design or wording items. They are tracked in `04-remediation-plan.md` (batches 5–7) rather than as separate defects.

---

## New findings (this audit)

Ranked by impact on trust and data safety. ✔ = re-checked by hand.

| ID | Area | Requirement | Actual / evidence | Ev | Sev | Batch | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| N001 | Calendar "+" | The chosen date survives until the thought is saved | `LaunchedEffect(uiState.savedPulse)` re-runs when Home re-enters composition. After any earlier save it consumes the Calendar date and fires the haptic again. | `HomeScreen.kt:229-239` — T✔ | H | 1 | Fixed in `f7d3c47` |
| N002 | Parser | Numbers that are prices, quantities or years are not clock times | `scanSeparatedTimes` / `scanCompactTimes` accept HH.MM and 3–4-digit numbers under weak context (`at`, any date). "pay 12.50 euros tomorrow" gives a reminder at 12:50. "buy 200 eggs at the store" gives a task for 02:00. "…before 2027" gives 20:27. | `domain/analyzer/ReminderTimeInterpreter.kt:255-256, 421-459, 578` — T | H | 2 | Fixed in `03c1384` |
| N003 | Sorting | A thought the user keeps in To sort is never filed automatically later | `keepInInbox` doesn't stamp `decidedAt`. With "Sort right after saving" on, turning it off later files the kept thoughts. Re-analysis on sheet open can also let AutoFiler file the thought under the open sheet. | `CaptureSortViewModel.kt:232-252`; `AutoFiler.kt:67-76` — T✔ | H | 1 | Fixed in `f7d3c47` |
| N004 | Delivery | A reconcile can't lose a notification | Reconcile work is `ExistingWorkPolicy.REPLACE`. A second enqueue can cancel a running reconcile between `claimDelivery` and `notify`, leaving the reminder claimed but never shown. | `ReminderRescheduleWorker.kt:68-75`; `ReminderNotifier.kt:49-61` — T✔ | M | 3 | Fixed in `6af1cc7` |
| N005 | Needs you | Quick times and one-tap suggestions never create past reminders | `remember(uiState.needsYou){now}` and the stale `now` in `buildUiState` keep offering "Today 18:00" and passed suggested times | `ReviewScreen.kt:299`; `ReviewToSort.kt:99` — T | M | 3 | Fixed in `6d0b3b0` |
| N006 | Undo | Undoing a sort after the item's type changed doesn't duplicate | `CaptureResolution.undo` deletes by the remembered type and id. After a type change it deletes nothing but still returns the thought to To sort, so it can be filed again. | `CaptureResolution.kt:129-150` — T✔ | H | 1 | Fixed in `f7d3c47` |
| N007 | Restore | Restore doesn't re-link device-only data to different items | AI history rows are re-linked by `captureId`, and Space aliases by `spaceId`, to whatever restored row has the same id. The dialog says links are cleared. | `LocalDataRestorer.kt:104-149`; `res/values/strings.xml:87` — T | M | 4 | Fixed in `980c945` |
| N008 | Type change | Converting keeps thought links and warns about everything dropped | A reminder made from a thought leaves the thought marked "Reminder set" with no reminder. Task → other type nulls `reminders.linkedTaskId`. Offset and snooze are dropped without warning. | `ItemTypeConversion.kt:132-203, 291-306` — T | M | 4 | Fixed in `980c945` |
| N009 | Privacy | The redacted lock-screen view is meaningful | No `setPublicVersion`. The default visibility is PRIVATE, so Android already follows the user's lock-screen setting (full text only when "show all content" is on). Hardening: add a public version ("Tallele reminder"). | `reminders/ReminderNotifier.kt:149-171` — T | L | 7 | Fixed in `052284d` |
| N010 | Release | Distribution-policy-safe permissions | `USE_EXACT_ALARM` declared (Play restricts it to alarm/calendar apps). The release checklist and verifier don't catch it. `SCHEDULE_EXACT_ALARM` has no `maxSdkVersion`. A direct battery-optimisation request is used. | `AndroidManifest.xml:9-17`; `ReminderCapabilities.kt:174-177` — T | M | 7 | Blocked on decision D2 (documented in release checklist) |
| N011 | Gemini key | A transient Keystore error doesn't erase the key | Any decrypt exception calls `deleteKey()` | `security/GeminiApiKeyStore.kt:40-58` — T | M | 7 | Fixed in `052284d` |
| N012 | AI | When Gemini fails, the local result still applies | `fallback` sets `analyzerFailed`, so Keep. Offline in Gemini mode means nothing is ever auto-filed, and there is no retry. | `domain/ai/OrbitAiRouter.kt:385-401`; `CaptureDecision.kt:42` — T | M | 2 | Not reproduced (analyzer failure is not stored; AutoFiler decides normally) |
| N013 | Parser | Ambiguous hours ask even when a day is given | "tomorrow at 5" resolves to 05:00 silently. "at 9:30" is never treated as ambiguous. | `ReminderTimeInterpreter.kt:83-100, 428-433` — T | M | 2 | Partly fixed in `03c1384` ("at 5" asks); "9:30" read as 24-hour is intended |
| N014 | Parser | A weekday that is today and still ahead means today | `TemporalAdjusters.next(day)`: "Monday 18:00" typed on Monday morning gives next week | `ReminderTimeInterpreter.kt:412` — T | L | 2 | Deferred (needs the clock time where the weekday is resolved) |
| N015 | Situation AI | Date-only tasks count | Ranks by `task.dueAt` only | `domain/analyzer/SituationAnalyzer.kt:190-200` — T | M | 3 | Fixed in `6d0b3b0` |
| N016 | Learning | Proposal moves on and respects "Not now" | Stuck on the first group; "Not now" isn't stored, so it is re-offered | `ProposeLearnedRuleUseCase.kt:36-43`; `CaptureSortViewModel.kt:724-726` — T | L | 5 | Fixed in `7978dda` |
| N017 | Brain Dump | A Space "+" dump keeps the Space; "Keep for later" is analysed and not auto-filed against the user's choice | Items ignore `suggestedSpaceId`. "Keep for later" creates a fresh capture, analysed only on the next cold start, and AutoFiler can then file it. | `BrainDumpInteractionState.kt:122-124`; `domain/usecase/BrainDumpActions.kt:120-150` — T | M | 1 | Fixed in `59d8fbd` |
| N018 | Receipt | A finished Brain Dump's receipt is truthful | Processed with no link, so the base receipt reads "Saved as a note." | `BrainDumpActions.kt:195-253`; `CaptureReceipt.kt:210` — T | M | 1 | Fixed in `f7d3c47` |
| N019 | Calendar | A Calendar-day thought always gets its sheet, even after process death | The trigger is in-memory only, and AutoFiler skips Calendar thoughts forever | `HomeCaptureViewModel.kt:145, 192-205`; `AutoFiler.kt:76` — T | M | 1 | Fixed in `f7d3c47` |
| N020 | Capture | Shared or spoken text arriving during a save isn't lost | `onInputChanged` returns while processing; the handoff key is cleared anyway | `HomeCaptureViewModel.kt:208-216`; `OrbitApp.kt:309-313` — T | L | 1 | Fixed in `f7d3c47` |
| N021 | Space "+" | A late result from one sheet doesn't affect the next | The failure flag and close-on-success are global | `OrbitApp.kt:779-795` — T | L | 1 | Fixed in `e0cdfd3` |
| N022 | Capture | Very large pastes can't crash on backgrounding | Home draft has no cap; it is stored in the saved-state Bundle | `HomeCaptureViewModel.kt:215` — T | L | 1 | Fixed in `f7d3c47` |
| N023 | Space "+" | After saving, the user sees where the thought went | No receipt or snackbar after a Space "+" save | `OrbitApp.kt:788-789` — T | M | 1 | Fixed in `e0cdfd3` |
| N024 | Entry points | Share and shortcut don't create a second instance or wipe the draft | `singleTop` + share from another task, and static shortcut `CLEAR_TASK`, are platform-dependent | Manifest :49; `shortcuts.xml` — N | M | – | Unverified (needs a device) |
| N025 | Needs you | Answering a card gives feedback and Undo; "Just keep it" never strands a card | No success message and no Undo; "Just keep it" deletes the linked reminder; it is a no-op if the reminder is missing | `ReviewViewModel.kt:300-348`; `CaptureResolution.kt:159-165` — T | M | 5 | Fixed in `7978dda` |
| N026 | Spaces | The "To sort" tile shows To sort | Opens Needs you with More folded | `OrbitApp.kt:401-407`; `ReviewScreen.kt:180` — T | M | 1 | Fixed in `e0cdfd3` |
| N027 | i18n | Starter Space names translated on item detail | Raw `.name` | `ItemDetailScreen.kt:585, 883` — T | L | 5 | Fixed in `7978dda` |
| N028 | a11y | Controls are named and announce state | Space colour swatches unlabelled; AI switches unlabelled; the tab dot's state replaces "Selected"; More/From earlier/Weekly rows have no role or expanded state; title-edit ✕ labelled "Done" | `SpacesScreen.kt:1334-1355`; `AiSettingsCard.kt:517-526, 590-594`; `FloatingBottomNavigation.kt:145`; `ReviewRedesign.kt:245, 496, 654`; `CaptureSortContent.kt:224-227` — T | M | 6 | Fixed in `b5b08b6` |
| N029 | a11y | Custom chips have 48 dp targets | `SortChip` and `FilterPill` are 40 dp | `CaptureSortContent.kt:511`; `SearchScreen.kt:394` — T | L | 6 | Fixed in `b5b08b6` |
| N030 | Settings | The live glass preview is live | Uses the Subtle style, which never draws live | `ui/components/GlassRolePreview.kt:69-76` — T | L | 7 | Done with Glass/Solid in `052284d` |
| N031 | Data tools | Restore and reset can't overlap or be dismissed mid-run | Restore dialog dismissible while running; reset cancels alarms before its transaction; restore doesn't check `isResetting` | `LocalDataSettings.kt:80`; `LocalDataToolsViewModel.kt:153-208` — T | L | 4 | Fixed in `980c945` |
| N032 | Restore | A reminder ringing between preview and confirm doesn't abort restore | Equality check includes delivery fields | `LocalDataRestorer.kt:61` — T | L | 4 | Fixed in `980c945` |
| N033 | Export | A failed export doesn't delete a file the user chose to overwrite | `resolver.delete(destination)` on failure | `data/export/LocalDataExporter.kt:23-25` — T | L | 4 | Fixed in `980c945` |
| N034 | Export | The size limit is explained | Over 10 MB or 100k rows: "could not be verified" | `LocalDataExporter.kt:40-44` — T | L | 4 | Fixed in `980c945` |
| N035 | Retry | (part of RS1) The retry button stays after "still failing" | `canRetryReminderSetup` checks only `RestoreCompleted` | `LocalDataToolsViewModel.kt:40-42` — T | L | 3 | Fixed in `a8ccd9e` |
| N036 | Receipt | Reset/restore clears the last receipt | `lastReceiptCaptureId` persists | `AppSettingsRepository.kt:103, 196` — T | L | 4 | Fixed in `980c945` |
| N037 | Undo | Archive Undo doesn't overwrite later edits | Writes the whole pre-archive snapshot back | `ui/screens/item/ItemArchiveUndo.kt:99-106` — T | L | 4 | Fixed in `980c945` |
| N038 | Reminders | Reopening or re-enabling a reminder whose time passed says so | Arms nothing, silently | `ItemDetailViewModel.kt:252-267, 360` — T | L | 3 | Fixed in `a8ccd9e` |
| N039 | Calendar | Zone change, DST and legacy monthly tokens handled | Zone captured once in the ViewModel and day view; DST fall-back merges two 01:30 rows; tokens without `/dom` drift to the 28th | `CalendarViewModel.kt:47`; `CalendarDayTimeline.kt:45-55`; `ReminderRepeat.kt:83` — T | L | 3 | Fixed in `6d0b3b0` |
| N040 | Gemini | Weekly review sends only what is needed | Full untruncated bodies for up to 10 items | `ReviewViewModel.kt:779-811`; `integrations/gemini/SourceLinkedGemini.kt:61-64` — T | L | 7 | Fixed in `052284d` |
| N041 | Gemini client | Connections closed, reads bounded, overall deadline | `disconnect` only on success; unbounded body; up to 6 × 30 s for a dump | `integrations/gemini/GeminiApiClient.kt:86-114` — T | L | 7 | Fixed in `052284d` |
| N042 | Performance | Tab dot doesn't observe every capture | `observeAll()` used for one boolean; other screens load whole tables | `OrbitApp.kt:121-125` — T | L | 7 | Open (not measured; not changed) |
| N043 | Release / CI | Unsigned release can't pass as release; more than API 36 tested | Missing signing variables give an unsigned build silently; CI tests only API 36 | `app/build.gradle.kts:51`; `.github/workflows/android-release.yml` — T | L | 7 | Fixed in `052284d` |
| N044 | Restore | Restored past repeats continue; outcomes reflect the new phone | Past repeats are only marked handled (silent until Done); old outcomes are kept | `LocalDataRestorer.kt:173-190` — T | M | 3 | Fixed in `6af1cc7` |
| N045 | Type change | The reason for a refused change is right | `Unsupported` always says "Choose a reminder date and time first." | `ItemDetailViewModel.kt:525` — T | L | 4 | Fixed in `980c945` |
| N046 | Gemini | Prompt text can't steer auto-filing | The thought is pasted without delimiters. Injected `confidence:1.0` can auto-file a task or note (not a reminder: its time status stays Unspecified, so Ask). | `integrations/gemini/GeminiPromptBuilders.kt:43-44` — T | M | 2 | Fixed in `03c1384` |
| N047 | Consent gate | Gemini can't run without current consent on any call path | Enforced only when settings are read | `domain/model/AppSettings.kt:148-149` — T | L | 7 | Fixed in `052284d` |
| N048 | Space "+" | A Space archived before analysis doesn't send a note to "no Space" | `hasSpace` is true from the stored id, but filing finds no active Space | `AutoFiler.kt:81`; `CaptureResolution.kt:80, 197-205` — T | L | 2 | Deferred (rare: Space archived in the seconds before analysis) |
| N049 | Settings | AI rows say what is on | "Features" shows the mode; "Gemini API ready" when no feature is on; remove key / delete rule have no confirmation | `AiSettingsCard.kt:231-235, 353-359`; `SettingsHome.kt:166-168` — T | L | 5 | Fixed in `7978dda` |
| N050 | Dead code | – | `summarizeSituation`, open-loop capture branches, unreachable non-replay guide path, unused `Analyzing` state, unused Review state | various — T | L | – | Open (cleanup run only, not mixed with fixes) |
| N051 | Parser | Inflected Space words match | RU "машину", EN "dogs" get no Space | `CaptureAnalyzer.kt:584-586` — T | L | 2 | Deferred (word forms need a stemmer) |
| N052 | Docs | Contract docs match code | `reminder-contract.md` says "always 24-hour" (default follows device), that zone changes never move alarms (repeats re-anchor), and that long-missed repeats advance (they don't) | T | L | each batch | Fixed in `6af1cc7` (C8, C9 docs) |
| N053 | Needs you | One-by-one overlay is modal | The list behind stays reachable by TalkBack, the tab bar stays tappable, and the total is frozen | `ReviewRedesign.kt:700-710`; `OrbitApp.kt:914-924` — T | L | 6 | Fixed in `b5b08b6` |
