# 01 · Feature and subsystem coverage

Audit snapshot: branch `ccr-eed43729-xsml4c` created from `main` at `43a16428d070523e7aed516c69a5db8173adaa43`, the same commit as the source review (`Tallele_full_review.md`, 12 October 2026). The working tree was clean when the audit started on 2026-10-12.

| Item | Value |
| --- | --- |
| Application ID / namespace | `com.tallele.app` / `com.orbit.app` |
| versionCode / versionName | 1 / 1.0 |
| minSdk / targetSdk / compileSdk | 26 / 36 / 36 |
| Room database version | 11 (explicit migrations 1→11, no destructive fallback) |
| Backup (export) format | 7 (restore accepts 1–7; 10 MB and 100 000 rows per type) |
| Permissions | POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, SCHEDULE_EXACT_ALARM, USE_EXACT_ALARM, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, INTERNET, RECORD_AUDIO |
| Exported components | `MainActivity` (launcher + `SEND text/plain`), `NewThoughtTileService` (signature permission). All receivers are not exported. |
| Main sources / tests | 165 Kotlin files; 135 JVM test classes and 17 instrumented test classes |

**How the inventory was built.** Every entry point was traced from the manifest, `res/xml/shortcuts.xml`, the navigation graph (`ui/navigation/OrbitApp.kt`, `OrbitDestination.kt`) and each screen's ViewModel. Six read-only passes were made: capture, AI, data, reminders, screens and platform. The highest-risk claims were then spot-checked by hand against the source (marked ✔ in `02-findings-register.md`).

**Evidence words used here.**
- **Source trace:** code read, not run.
- **Automated test:** a JVM or instrumented test exists and passed at the baseline (see `05-test-evidence.md`).
- **Not verified:** needs a device or emulator, which this environment does not have (no `/dev/kvm`, no emulator image). The CI API 36 emulator job is the only on-device evidence available.

Paths are relative to `app/src/main/java/com/orbit/app/`.

---

## A. Capture, drafts and external entry points: Examined

| Feature | Entry → UI/VM → rule → storage | User sees | Recovery | Tests | Coverage / gaps |
| --- | --- | --- | --- | --- | --- |
| Home input + draft | `HomeScreen` CaptureCard → `HomeCaptureViewModel.onInputChanged` (draft in SavedStateHandle) | Text stays in the box | Edits ignored while saving | HomeCaptureProcessingStateTest | Draft restore after process death untested. No length cap on typed text (N022). |
| Send | `HomeCaptureViewModel.send` → `CaptureInbox.save` (Inbox, trimmed) → `analyze` on app scope → `AutoFiler.listen` | Box clears, receipt appears | Save failure keeps text and shows a snackbar | HomeCaptureViewModelTest, CaptureInboxRoomTest, AutoFilerRoomTest, AutoFilerRecoveryTest | Double tap guarded. |
| Save receipt (View / Change / Choose time / Turn on / Try again / swipe-hide) | `CaptureReceipt.line()/action()`, `CaptureReceiptReader.observe` | One line + one button | `ReminderFailed` snackbar | SaveReceiptTest | Wrong line for kept thoughts (B2) and finished Brain Dumps (N018). |
| First-run line, first-receipt tip | `HomeScreen`, `HomeCaptureViewModel.firstReceiptTip` | Tip under first receipt | – | HomeFirstRunTest, FirstReceiptTipTest, FirstRunJourneyJvmTest | "Kept" tip promises Search (F01). |
| Notification priming | `HomeCaptureViewModel.notificationPriming` | One line, then the system dialog | Asked once | HomeFirstRunTest | Delayed launch not UI-tested. |
| Time question card | `QuickReminderCard` → `keepQuestionThought` → `CaptureResolution.keep` | "Pick a time" / "Just keep it" | Snackbar | HomeCaptureViewModelTest | – |
| Voice input | `VoiceInput.rememberVoiceInput` → `VoiceCapture` (on-device API 31+, else system screen with one-time cloud notice) | Listening state; transcript appended | Denied mic → system screen; missing language → system screen | VoiceCaptureTest, VoiceCloudNoticeJvmTest | Permission paths untested. Transcript during save dropped (N020). Mic label while listening (review §6). |
| Share intent (cold/warm) | Manifest SEND → `MainActivity.readLaunchIntent` / `onNewIntent` → `OrbitApp` → Home draft | Text appended to draft, never auto-saved | Intent consumed | SharedTextTest (pure functions) | Routing untested. B1. Instance/task behaviour with `singleTop` unverified (N024). |
| Launcher shortcut "New thought" | `shortcuts.xml` → `NewThought.isRequest` → focus Home box | Home opens focused | – | none | B1. Platform `CLEAR_TASK` behaviour unverified (N024). |
| Quick Settings tile | `NewThoughtTileService` → same path | Same | – | none | B1. Lock-screen behaviour not verified. |
| Space "+" sheet | Spaces/Space detail → `OrbitApp` sheet → `SpaceCaptureSheet` → `CaptureInbox.save(spaceId)` (`suggestedSpaceId`) | Sheet closes; no confirmation | Error line keeps text | SpaceCaptureSheetJvmTest, CaptureInboxRoomTest | F13, B8, N021, N023. |
| Calendar "+" | Calendar header / empty day → `returnHomeWithCalendarCaptureDate` → Home banner → `send(epochDay)` → sort sheet after analysis | Banner, then sort sheet | Analysis failure still opens the sheet | CalendarNavigation(Instrumented)Test, HomeCaptureViewModelTest | **N001**: date wiped after an earlier save. N019: process death loses the sheet trigger. Approved redesign: open the add sheet with the date (review §11). |
| Long-press a week day | Home week strip → date + focus | Banner + keyboard | – | HomeWeekStripAccessibilityTest | – |
| Brain Dump entry | Multi-line thought → session → overview / one-by-one | Overview list | Per-item transaction; failure keeps the draft | BrainDump*Test, BrainDumpActionsRoomTest (instrumented) | N017 (Space ignored; "Keep for later" not analysed). |

## B. AI, interpretation and learning: Examined

| Feature | Entry → rule → storage | Tests | Coverage / gaps |
| --- | --- | --- | --- |
| Local analysis | `LocalRulesCaptureAnalyzer` (confidence 0.55 + 0.12 × signals, High ≥ 0.78) → `capture_suggestions` | LocalRulesCaptureAnalyzerTest, CaptureDecisionTest | F02, F03, N002. Uneven EN/ET/RU word lists. Inflection gaps (N058). |
| Time interpretation | `ReminderTimeInterpreter` (asks on several dates, adjustments, passed explicit day, ambiguous bare hour without a day) | ReminderTimeInterpreterTest, MultilingualTimePhraseTest, TaskDateParityTest | N002 (numbers as times), N013, N014. |
| Act / ask / keep | `CaptureDecision.decide` + `AutoFiler.apply` (High only; reminder needs a resolved future time; note needs a Space; Brain Dump and failed analysis keep) | CaptureDecisionTest, AutoFilerRoomTest, AutoFilerRecoveryTest | N003, N012. Notes never auto-file locally (F02). |
| Pause automatic filing | "Sort right after saving" (`AutoFiler(paused = …)`), under Reminders › Advanced | AutoFilerRoomTest | Not discoverable as a pause (review §6). N003. |
| Gemini routing | `OrbitAiRouter` (capture, Brain Dump, split, make smaller, weekly review) | OrbitAiRouterTest | `summarizeSituation` has no caller. N012 (failure → keep). |
| Consent | `AiSettingsCard` dialog; `AppSettingsRepository` downgrades stale consent | AppSettingsGeminiTest | F11 wording; N049 (gate is indirect). |
| Gemini JSON validation | `GeminiJsonValidator`, `SourceLinkedGeminiValidator` | GeminiJsonValidatorTest | Unbounded body read (N043). Prompt has no delimiters (N056). |
| Learning events / learned rules | `RecordAiLearningEventUseCase`, `ProposeLearnedRuleUseCase`, rule edit/remove in AI settings | RecordAiLearningEventUseCaseTest, LearnedRuleProposalGateTest | B12, B16, N016. |
| Ask LUMA | `SituationAiViewModel.askLuma` → `LocalAiRetriever` (local only) | LocalAiRetrieverTest, LocalAnswerTextTest | F06. |
| Preset questions | `AskLumaPromptAnswerer` (uses date-only days correctly) | AskLumaPromptAnswererTest | – |
| Situation AI | `LocalRulesSituationAnalyzer` (local, refreshed per minute) | LocalRulesSituationAnalyzerTest | N015 (date-only tasks ignored). |
| AI history retention | `AiHistoryRetention.prune` (90 days, cold start) | AiHistoryRetentionRoomTest | – |

## C. Persistence and data lifecycle: Examined

| Subsystem | Behaviour | Tests | Coverage / gaps |
| --- | --- | --- | --- |
| Tables and ids | Every key is `AUTOINCREMENT` per table; notes, tasks and reminders count independently. `captures.linkedItemId` has no FK; `suggestedType` is the type discriminator. | Migration tests | F07 relies on same-id inserts. |
| Migrations 1→11 | Explicit, no destructive fallback | OrbitDatabaseMigrationTest (instrumented 1→6), OrbitDatabaseMigrationJvmTest (6→11, full chain) | Instrumented part runs only in CI. |
| Finalisation | Mutex + in-transaction Inbox check (`ConfirmCaptureActionUseCase`) | ConfirmCaptureActionUseCaseTest, CaptureInboxRoomTest | N006 (undo after type change). |
| Item type conversion + Undo | Same id in target table; refuses on collision | ItemTypeConversionRoomTest, ItemTypeChangeUndoTest | F07, B3, N008. |
| Export | Format 7; validates its own output; plaintext JSON via SAF | LocalDataExporterSnapshotTest, BackupRoundTripRoomTest | Omits AI history, memory, aliases, settings, key, archived-task memory, delivery state (C3). N033, N034. |
| Restore | Decode + validate → compare snapshot (txn) → replace (separate txn) → reconcile reminders outside txn | LocalDataRestoreTest, LocalDataRestoreRoomTest, LegacyCaptureOutcomesTest | F09, N007, N032, N048. |
| Reset | Cancels each reminder, then deletes the 12 user tables in one transaction | **none** | F10/B7: AI history, learned rules, memory, settings, key, prefs and the summary notification survive. |
| Clear learned data | One transaction over history, rules, memory, aliases | none | – |
| Android backup | Cloud backup excludes everything. Device transfer (API 31+) includes DB, settings and prefs, not the key. API ≤ 30 transfers nothing. | none | Documented below. |
| Package migration | `com.orbit.app` → `com.tallele.app` install side by side; data moves only through Export/Restore | – | C5. |

## D. Reminders, dates and background execution: Examined (delivery not device-verified)

| Feature | Behaviour | Tests | Coverage / gaps |
| --- | --- | --- | --- |
| Create (Home question, sort sheet, Needs you, Accept all, Brain Dump, type change) | Inserted in the finalisation transaction, armed after commit, one `ReminderSaveOutcome` | ReminderOutcomeEntryPointsTest | F05 (text lost). B5 (past times). Type change reports no outcome. |
| Edit time / offset / repeat / notification toggle | `RoomReminderRepository.update` re-arms only when timing changes | ReminderSchedulingTest, ReminderDeliveryRoomTest | B5 (always "Schedule updated."). N038. |
| Delivery | Exact alarm when allowed, else inexact; WorkManager backup; atomic `claimDelivery` | ReminderDeliveryRoomTest, WorkManagerReminderSchedulerTest | N004 (REPLACE). N009 (lock screen). |
| Done / Snooze | Done: one-off completes, repeat advances. Snooze: +15 min. | ReminderDeliveryRoomTest | Action ignores which occurrence it came from. |
| Swipe / ignore | Nothing happens (no delete intent) | none | F04. |
| Repeats | daily, weekdays, weekly, monthly (31st clamps and returns) | ReminderRepeatsTest, CalendarProjectionTest | F04: advances only on Done. Legacy monthly token drift (N039). |
| Reconcile (boot, update, zone, time set, exact-alarm change, app start) | `ReminderRescheduleWorker` (unique work, REPLACE) | ReminderBootReceiverTest, ReminderSchedulingTest | N004. Not run on resume (B6). |
| Permission / channel state | `ReminderCapabilities`; Settings re-reads on resume | ReminderCapabilityStateTest | B6 (dot and outcomes not refreshed). |
| Retry reminder setup | Only after a restore; success when no exception | LocalDataToolsMessageTest | Review extra check: confirmed; see register `RS1`. |
| Calendar day/month, week strip | Range = visible month ± 6 days; repeats projected | CalendarViewModelTest, CalendarAgendaTest, CalendarRoomRepositoryTest | F12, B13, L1, L6, N039. No overdue marker (review §6). |
| Date-only tasks/notes | Stored as epoch day; Calendar "Any time" | CalendarProjectionTest, TaskDateParityTest | F06, F14. |
| 24-hour display | Default mode follows the device; 12/24 override | OrbitTimeFormatTest | Conflicts with the doc's "always 24-hour" (C8). |

## E. UI and navigation: Examined (renders only in English at 411 dp)

| Screen | Main elements | Tests / Screen tour | Coverage / gaps |
| --- | --- | --- | --- |
| Needs you | Header menu (Breathe and the Ask sheet), summary, question cards, Today, More (From earlier, To sort, one-by-one, weekly look back, open loops) | ReviewScreenJvmTest, ReviewNeedsYouTest, ReviewViewModelTest, ReviewToSortAndLayoutTest, tour shots | B4, B9, B10, B15, N005, N025, N026. A test locks B4 in. |
| Spaces list / detail / editor / move | Count tiles, Space rows + menus, hidden section, detail list with tick and move, editor sheet, move dialog | SpacesTransformationsTest, SpaceItemMoveActionsTest, SpaceEditorJvmTest | B8, L4, L8, N028 (unlabelled swatches). |
| Search | Bar, filter pills, Space shortcuts, recent, results, "Search archived too" | SearchContentJvmTest, SearchFilterTest, Search*InstrumentedTest | F01, F05, review §6 Search items. |
| Item detail | Fields, sheets (type, schedule, status, repeat, notification, Space), Make smaller, dialogs | ItemDetailFieldsTest, ItemArchiveUndoTest, ItemTypeChangeUndoTest | F07, F14, B3, B5, B14, L7, N027. |
| Brain Dump | Overview, single card, sort sheet, skip Undo, learned-rule dialog | BrainDump*Test, CaptureSuggestionSheetTest | B11, B17, B18, L5. |
| Settings | Home + Profile, Look, Colours, Background, Glass, Reminders (+Advanced), Language, Time, AI (4 pages), Backup | Settings*Test, AiSettingsPageContentTest, LocalDataToolsMessageTest | L2, L3, F11, review §6 Settings items, N053, N054. |
| First-time guide | 4 pages, Space picker | FirstTimeTutorial*Test | Non-replay path unreachable. Page 3 text wrong on replay (review §6). |
| Tab bar | Five tabs, failure dot | FloatingBottomNavigation*Test, TabJourneyJvmTest | Glass differs by tab (review §6, approved redesign 9.4). N028 (dot hides "Selected"). |
| Situation AI / Ask sheet | Overview sections, free question, sources | SituationAiSheetTest | Review §6 Ask items. |

**Not examined:**
- Visual rendering at 320/360 dp, at 130/200 % font scale, in ET/RU and at tablet or split-screen sizes. The screen tour renders English at 411 dp only.
- TalkBack traversal on a device.

These remain open (see `05-test-evidence.md`).

**String parity.** All 873 English keys exist in ET and RU, and placeholders match. User-visible hard-coded English remains in Kotlin:
- `"Inbox"` used as a Space name (`CaptureSortViewModel.kt`)
- `"#label ×"` on the remove-label chip
- `" · "` joins
- An English-only `removePrefix` in `CaptureSuggestionSheet.kt`

## F. Security, privacy, performance and delivery: Examined

| Area | Finding | Coverage |
| --- | --- | --- |
| Manifest | Receivers not exported. `MainActivity` exported for launcher/share; it also accepts navigation extras from any app (navigation only). | Source trace |
| Gemini key | AndroidKeystore AES-GCM; ciphertext in `luma_secure_ai` prefs; excluded from device transfer. **Deleted on any decrypt error** (N011). | Source trace |
| Gemini transport | HTTPS only, key in header, no logging, 15 s connect + 15 s read, no overall deadline, unbounded body read (N043) | GeminiErrorMappingTest |
| Consent gate | Stale consent downgrades to Local only when settings are read. `geminiOnFor` does not check the version itself (N049). | AppSettingsGeminiTest |
| Logs | No `Log.*`/`println` in main sources | Source trace (grep) |
| Notifications | Title and full notes shown on the lock screen (N009) | Source trace |
| Play policy | `USE_EXACT_ALARM` and a direct battery-optimisation request are declared or used (N010) | Source trace |
| Release | Release build is silently unsigned when signing variables are missing; CI does not check signing (N045). R8 and resource shrinking on. Startup profile equals the full baseline profile. | CI `Fast release gates` |
| Performance | Benchmarks exist (startup, scrolling); no numeric budgets. Several screens observe whole tables; the tab dot observes every capture (N044). | Not measured here |
| CI | Unit (debug + release), lint, release build, API 36 emulator instrumented tests + baseline profile on every PR; benchmarks weekly | Only API 36 is tested; minSdk is 26 |

---

## Cross-feature journeys (brief §8)

Status before remediation. **(c)** = from a code trace; **(t)** = an existing automated test covers it.

| Journey | Status |
| --- | --- |
| J01 Plain thought | Saved immediately (t). **Not findable in Search when kept** (F01, c). |
| J02 Clear positive reminder | EN/RU set a reminder (t). ET kept silently (F02, c). Ringing: CI emulator only. |
| J03 Negative / quoted / conditional | **Sets a reminder** (F03, c). |
| J04 Reminder missing time | Asks (t). |
| J05 Space "+" unclear thought | **Not visible in the Space; draft lost on dismiss** (B8, F13, c). |
| J06 Calendar "+" | **Date lost after an earlier save** (N001, c). Works on the first save (t). |
| J07 Brain Dump | Exactly-once per item (t). Chosen Space ignored (N017, c). |
| J08 Date-only task | Calendar correct (t). **Ask and Situation omit it** (F06, N015, c). |
| J09 Long original text | Notes and tasks keep it. **Reminders lose it** (F05, c). |
| J10 Type change | **Usually fails on id collision** (F07, c). |
| J11 Repeat after ignored alert | **Stops** (F04, c). |
| J12 Permissions revoked/restored | Dot stale until restart (B6, c). |
| J13 In-flight AI during restore/reset | **Can attach a different thought's suggestion** (F09, c). |
| J14 Export / restore | Formats 1–7 accepted (t). Omissions not disclosed in the dialog (C3). |
| J15 Reset | **Incomplete** (F10, c). No test. |
| J16 Edit then share | **Edit lost** (B1, c). |
| J17 Offline / process death | Thought saved (t). Calendar-day sheet trigger lost (N019, c). |
| J18 EN/ET/RU accessibility | Strings complete. Layout at large text not rendered (not verified). |
| J19 Search / archive / hidden Space | "Search archived too" works (t). Hidden Space shown as "No Space" on the item (B14, c). |
| J20 Multi-day use | Blocked by F01, F04 and F03 until fixed. |
