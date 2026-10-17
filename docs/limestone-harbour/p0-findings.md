# P0 findings: current behavior vs design v1.3

Evidence: code reading by three read-only auditors plus lead spot-checks, on `main` `f2662dd` (app code is identical to `43a1642`, the commit the proposal read: `git diff 43a1642 HEAD -- app` is empty). Paths are under `app/src/main/java/com/orbit/app/`. "Pinned by" names the test that proves the current behavior; tests under `app/src/test/.../limestone/` were added in P0.

Line numbers are approximate pointers for the next session; search the symbol if they have drifted.

## 1. Behavior matrix B1–B11 (and B12)

| # | Design change | Current behavior (evidence) | Verdict | Phase | Pinned by |
|---|---|---|---|---|---|
| B1 | Undo on the receipt after automatic filing, returning a kept thought | Auto-filing exists (`domain/capture/AutoFiler.kt`, rules in `CaptureDecision.kt`, High ≥ 0.78 / Medium ≥ 0.62 in `domain/analyzer/CaptureAnalyzer.kt:76`). Home receipt has **no Undo**; "Change" only opens the item. A domain Undo exists, `CaptureResolution.undo` (`domain/capture/CaptureResolution.kt:129`), reachable only after a *manual* accept (sort sheet, Review). It **hard-deletes** the item without checking edits/completion/link, then resets the capture to Inbox and stamps `decidedAt` (so late filing cannot re-file — that part is safe). | Confirmed need. Existing Undo is **unsafe** for edited/completed items. | P3 | `AutoFilerRoomTest.undoRestoresSaved…` (existing); `P0AutoFilingCharacterizationTest` (undo deletes an edited/completed task) |
| B2 | Kept thoughts in Search by default, own group, no duplicates | `LocalSearch.search` (`domain/search/LocalSearch.kt:46-61`) searches notes, tasks, reminders only; `SearchCorpus.captures` is loaded but **never read**. De-duplication works today only because captures are omitted. Stale `SearchGroup.ToSort` and strings already exist. | Confirmed | P2 | `LocalSearchTest.excludesRawAndProcessedCaptures`, `linkedCaptureDoesNotDuplicateFinalizedItem` (existing) |
| B3 | Truthful receipt for a kept thought and for Brain Dump | After AutoFiler keeps a thought (or "Just keep it"), receipt maps `Saved && decided` to **"Saved as a note."** (`ui/screens/home/CaptureReceipt.kt:83`, `receipt_saved_note`) although no note exists. A multi-thought entry gets the same line; no count. | Confirmed | P3 | `HomeCaptureViewModelTest` (~:156), `SaveReceiptTest` (existing) |
| B4 | "Organising…" while sorting really runs | `CaptureProcessingState.Analyzing` (`ui/screens/home/HomeCaptureViewModel.kt:33`) is **never assigned**. Receipt shows "Saved." while `decidedAt == null`. Review's `ToSortState.Analyzing` is a separate 2-minute grace heuristic. A failed analysis is silent until the next app start. | Confirmed | P3 | Code reading |
| B5 | Reset erases everything | Reset (`ui/screens/settings/LocalDataToolsViewModel.kt:184-226`) cancels each reminder, deletes 12 Room tables, clears archived-status memory. **Keeps** settings DataStore, Gemini key, learned rules, AI history, person/project memory, several prefs (see §3). | Confirmed narrow | P5 | `P0ResetScopeCharacterizationTest` |
| B6 | Month view shows neighbouring-month days, greyed and tappable | Grid builds leading/trailing cells and data covers ±6 days (`ui/screens/calendar/CalendarViewModel.kt:85`), but `CalendarScreen.kt:474` draws and makes clickable only `cell.isInVisibleMonth`. Rows are 4–6, not a fixed 6. | Confirmed | P4 | Code reading |
| B7 | Notes/tasks with a date **and** a time appear on Calendar | A dated+timed note/task is stored as one millis field (`scheduledAt` / `dueAt`) and **is included** as a timed entry. A row with both a date-only day and a time is impossible through repositories (`data/repository/EntityRepositories.kt:240-250`), sort UI, and restore codec; the projection drops such rows (`data/repository/CalendarRepository.kt:135`). Possible look-alikes: note finalisation keeps only the context day (`CaptureResolution.kt:115`), Brain Dump turns a timed task into date-only when its day equals the Calendar context day (`BrainDumpActions.kt:257`). | **Unreproduced** as stated | P4 (re-check on a phone with real data) | `P0CalendarDateTimeCharacterizationTest`; `CalendarProjectionTest.conflictingScheduleIsExcludedSafely` (existing) |
| B8 | Test connection before consent runs the test after consent | `ui/screens/settings/AiSettingsCard.kt:361-370`: without consent, Test opens the consent dialog; confirming sets `aiMode = GeminiApi` and the test **never runs**. Not silent (a dialog shows) but the requested test is dropped. | Partly confirmed | P5 | Code reading |
| B9 | Removing a learned rule shows Undo | `AiSettingsViewModel.kt:215-221` deletes immediately; no confirmation, no Undo. | Confirmed | P5 | Code reading |
| B10 | Item detail updates when the item changes elsewhere | `ItemDetailViewModel.load()` reads `getById` once (init and after its own actions). No Flow, no resume refresh. Note/task/reminder DAOs have no `observeById` (only captures do). A notification Done leaves an open detail screen stale. | **Reproduced** | P4 | `P0ItemDetailStaleCharacterizationTest` |
| B11 | Glass tab bar only; old appearance removed and converted once; built-in backgrounds | Glass is live on the tab bar **and** the Home capture card, Review cards and the local-data card (`GlassSurface.kt`, `HomeScreen.kt:855`, `ReviewScreen.kt:915`, `ReviewRedesign.kt:854`, `ReviewWeekly.kt:168`, `LocalDataSettings.kt:133`). Fallback below API 33 and with animations off (read once per context, `ui/components/ReducedMotion.kt`); **no battery-saver detection**. Gradient presets + own-photo picker with persisted URI grant. Appearance values in §4. | Confirmed (large change) | P1 (tokens, tab bar, resources), P5 (conversion, removal) | `GlassRolePolicyTest`, `AppSettingsAppearanceTest` (existing) |
| B12 | *(Optional)* "Show the original" on item detail | Not present. | **Deferred** — needs owner approval | — | — |

## 2. Capture pipeline (for P3)

- **Save path:** Keep → `HomeCaptureViewModel.send()` (`:219-258`) → `CaptureInbox.save` (`domain/capture/CaptureInbox.kt:70-93`) inserts `CaptureEntity(status = Inbox)` then launches analysis on the application scope. Editor text and draft clear **after** the insert returns. Matches "store first".
- **Failure:** text kept, snackbar `core_home_message_capture_save_failed` ("Could not save that capture. Your text is still here."). **No Try again control.** Double-tap blocked by `isProcessing`. Not pinned by a test (insert failure injection needs a repository seam).
- **Draft:** `SavedStateHandle` key `homeCaptureDraft` only — lost if the task is removed. Narrow duplicate window if the process dies between insert and clear.
- **Receipt binding:** by capture id (`lastReceiptCaptureId` in DataStore, `ReceiptGate.Expect(id)`), not "latest" → two rapid keeps are safe.
- **Late results:** `CaptureInbox.store()` (`:180-235`) re-reads in a transaction and drops results unless the capture is still undecided Inbox; AutoFiler requires `decidedAt == null`. Undo/user decision wins today.
- **Latent bug (new finding):** `CaptureSuggestionEntity.toAnalysis` (`domain/capture/CaptureSuggestions.kt:13`) does not restore `analyzerFailed`, and AutoFiler decides from the stored suggestion (`AutoFiler.kt:82`). The `decide()` guard "failed analysis → Keep" (`CaptureDecision.kt:42`) is therefore bypassed: a Gemini-failed local fallback at High confidence can be auto-filed. Design §5 says that case shows "Suggestions weren't available. You can sort it yourself." Fix in P3 (decision D-09). Pinned by `P0AutoFilingCharacterizationTest`.
- **Links:** capture → item via `captures.linkedItemId` + `captures.suggestedType` (note and task ids collide across tables). Reminders also carry `reminders.linkedCaptureId`. Notes/tasks have no back-link. Filing sets `status = Processed`, `outcome`, `decidedAt`; raw text stays in the row.
- **Undo without a schema change is feasible:** guard on `captures.{status, linkedItemId, suggestedType, outcome}` plus item unchanged since filing (`createdAt == updatedAt`, open, not completed/archived, reminder not yet delivered). Pattern to copy: `ui/screens/review/ReviewChangeUndo.kt:63-67` (snapshot compare).
- **Brain Dump:** multi-line text creates `brain_dump_sessions/items`; overview with ticks, "Save N", "Finish later", resume already exist; per-row exactly-once save (`BrainDumpActions.apply`, `WHERE outcome='Pending'`); partial failure keeps failed rows. On completion the session rows are deleted and the parent capture's link is cleared → **no lasting provenance** from saved notes/tasks to the dump. "Keep as thought" inserts a new unlinked Inbox capture (can later be auto-filed). Result counts ("3 saved · 1 kept · 1 skipped") exist only in memory.
- **Space "+":** `OrbitApp.kt:770-796` → `CaptureInbox.save(text, spaceId)` → `suggestedSpaceId`; modal sheet, keeps text on failure, closes on success, **shows no receipt** and doesn't set `lastReceiptCaptureId`.
- **Voice / share / tile / shortcut:** only fill or focus the editor; nothing saves without Keep. `CaptureSource.Voice` and `Calendar` are never written (everything is `Manual`).
- **Setting:** "Review each capture" is today's `sort_right_after_saving` ("Sort right after saving"); same stored value can be relabelled.
- **Sort sheet loading** (`isLoading`) is set but never drawn — confirmed.

## 3. Erase-everything inventory (checklist for P5, B5)

Any store added in P1–P4 must be appended here.

| Store | Location | Holds | Cleared by today's Reset |
|---|---|---|---|
| Room `orbit.db` v11 (`data/local/OrbitDatabase.kt`) — spaces, captures, notes, tasks, reminders, brain_dump_sessions/items, labels, note/task/reminder_labels, capture_suggestions | `databases/` | user data | **Yes** (12 tables, one transaction) |
| ↳ `space_alias_memory` | same DB | learned Space aliases | Yes, indirectly (FK cascade from spaces) |
| ↳ `learned_rules` | same DB | learned rules | **No** |
| ↳ `ai_suggestion_history`, `ai_correction_history` | same DB | AI history incl. raw text snippets | **No** (only 90-day pruning, `data/local/AiHistoryRetention.kt`) |
| ↳ `person_memory`, `project_memory` | same DB | learned names/projects | **No** |
| ↳ `sqlite_sequence` | same DB | id counters | No — keep it: non-reused ids protect against stale alarms matching new reminders |
| DataStore `orbit_settings` (`data/repository/AppSettingsRepository.kt:32`) | `files/datastore/` | all settings, receipt state (`last_receipt_capture_id`, `last_receipt_dismissed`), tutorial/tip flags, `has_asked_for_notifications` | **No** (`reset()` exists, never called) |
| SharedPrefs `luma_secure_ai` + Keystore alias `luma_gemini_api_key` (`security/GeminiApiKeyStore.kt`) | `shared_prefs/`, AndroidKeyStore | Gemini key (AES-GCM ciphertext + IV) | **No**; `deleteKey()` also leaves the Keystore alias |
| SharedPrefs `tallele_archived_task_status` | `shared_prefs/` | pre-archive task status | Yes |
| SharedPrefs `tallele_local_data_tools` (`data/local/LastExportMemory.kt`) | `shared_prefs/` | last export time | **No** |
| SharedPrefs `luma_reminder_delivery` (`reminders/ReminderCapabilities.kt`) | `shared_prefs/` | last blocked delivery time | **No** |
| Persisted URI read grant for the own-photo background (`ui/screens/settings/AppearanceSettings.kt:101`) | system | photo access | **No** (never released anywhere) |
| App locale (AppCompat `autoStoreLocales`) | filesDir < API 33, LocaleManager ≥ 33 | language | **No** — decision D-07 |
| AlarmManager per reminder (request code from reminder id, `reminders/ReminderNotifier.kt:226`; action intents `ReminderActionReceiver.kt:63`) | system | alarms | Yes, per reminder read before the delete (race: a row inserted after the read keeps its alarm) |
| WorkManager unique work `orbit_reminder_<id>` | `androidx.work.workdb` | backup delivery | Yes |
| WorkManager unique work `luma_reschedule_reminders` | same | reconciliation | No (harmless on an empty table) |
| Posted per-reminder notifications | system | notifications | Yes |
| Missed-summary notification (id `0x4C554D41`, `ReminderNotifier.kt:217`) | system | notification | **No** |
| Notification channel `luma_reminders` | system | channel | No — keep |
| In-memory: `CaptureInbox.inFlight` + events, AutoFiler listener, undo controllers, `ui/DebouncedSettingsWriter.kt` pending overlay, background bitmap cache, Home draft in `SavedStateHandle` | process | transient state | **No** — a pending debounced settings write could rewrite settings after an erase |
| Cloud backup / device transfer (`res/xml/data_extraction_rules.xml`) | — | cloud excluded; device transfer includes DB, settings, prefs except `luma_secure_ai` | n/a |

Not present (proposal assumed them): backup/periodic WorkManager jobs. Starter Spaces: seeded by `seedStarterSpacesSilently` (`ui/screens/tutorial/TutorialSpaceSetupViewModel.kt:146`) from `LaunchedEffect(Unit)` in `OrbitApp.kt:137-141` when the tutorial flag is unset and no Spaces exist — after a full erase it would re-seed only on the next activity creation (decision D-08).

## 4. Appearance values today (input for the B11 conversion)

Enums in `domain/model/AppSettings.kt`; keys in `AppSettingsRepository.kt:232-268`. Defaults in bold.

| Key | Values | Proposal mapping (§7.17) |
|---|---|---|
| `theme_mode` | Light, Dark, **Auto** | Theme System/Light/Dark (Auto → System) |
| `accent_color` | **InkPaper**, LumaViolet, Sage, Rose, Amber, Ocean | InkPaper, Amber → Ink; Ocean → Harbour; Sage → Moss; Rose, LumaViolet ("Violet") → Plum |
| `background_preset` | InkPaper, SoftDawn, **VioletMist**, CalmSky, NightOrbit (also by label) | All gradients → Background None |
| `custom_background_uri` | string / absent | → None; release the URI grant |
| `background_blur_mode` (+ legacy float `background_blur`) | None, **Soft**, Medium, Strong | Dropped |
| `background_dim` | float, **0.12** | Dropped |
| `background_dimming_mode` | **Adaptive**, Manual | Dropped |
| `glass_effect` | **Strong**, Soft, Off | Strong → Glass; Soft → Frosted; Off → Solid |
| `glass_preference` (+ legacy float `glass_strength`) | Subtle, **Standard**, Prominent | Dropped (presets Clear/Soft/Solid are derived in UI only, `AppearanceSettings.kt:563-565`) |
| `palette_mode` ("second colour") | **Standard**, FullPalette | Dropped |
| `text_color` (legacy "Default", "Ink" → Neutral) | **Neutral**, Plum, Forest, WarmIvory | Dropped |
| unknown strings | `runCatching` → default | Default, never a crash |

Two read-time legacy remaps already exist (`isLegacyDefaultAppearance`, SoftDawn → InkPaper). **Backups contain no settings** (`data/export/LocalDataBackupCodec.kt:101-124`), so "old backups go through the same conversion" has nothing to convert; device-to-device transfer does carry the old DataStore (decision D-05).

## 5. Navigation and external entry points (for P2)

Routes (`ui/navigation/OrbitDestination.kt`): tabs `home`, `spaces`, `calendar?date={date}`, `review` ("Needs you"); pushed `settings`, `tutorial?replay={bool}`, `spaces/unfiled`, `spaces/{spaceId}`, `search?query={query}`, `reminder/{reminderId}`, `item/{type}/{itemId}` (type note/task/capture).

- **Calendar is a tab today**; reached also from the Home week strip and Spaces' Today link via `popUpTo(Home)` → Back always lands on Home.
- **Badge:** a dot, never a count (`OrbitApp.kt:121-124`). Lit when a capture has `outcome == NeedsYou` with reason `TimePassed` or `NotificationsOff` (`ui/screens/review/ReviewToSort.kt:183`); `TimeUnclear`, `TypeUnclear`, `NotScheduled` don't count. Design wants a To-decide count (decision D-10).
- Tab state: `saveState/restoreState` on tab switches; Calendar keeps date/month/view in `SavedStateHandle`; Search keeps query/filters in VM flows only (lost on process death); Search results blank ≥ 150 ms after > 5 s away (`stateIn(WhileSubscribed(5000))`), and Search refocuses the keyboard on every return — both conflict with §3 "Back returns to the same query and scroll".

| Entry point | Mechanism | Lands on | Keep for compatibility |
|---|---|---|---|
| Share | `ACTION_SEND text/plain` on `MainActivity`; `capture/SharedText.kt` (subject+body, 20,000 chars) → Home draft | Home editor, not saved | filter; label "Keep in Tallele" is new (P5) |
| Launcher shortcut | `res/xml/shortcuts.xml` `new_thought`, action `com.orbit.app.action.NEW_THOUGHT`, target `com.orbit.app.MainActivity` | Home, focused | action string, class name, shortcut id |
| Quick Settings tile | `capture/NewThoughtTileService.kt` → same action | Home, focused | action string |
| Reminder notification tap | extra `EXTRA_REMINDER_ID`, CLEAR_TOP, singleTop → `onNewIntent` | `reminder/{id}` pushed | extra name, route |
| Missed-summary notification | extra `EXTRA_OPEN_REVIEW` | `review` pushed (not a tab switch) | extra name |
| Done / Snooze 15 min | unexported `ReminderActionReceiver`; snooze sets `snoozedUntil` only | no UI | actions |
| Calendar "+", Brain Dump resume | write into Home's `SavedStateHandle` | Home | — |
| URI deep links, widget | none | — | — |

`EXTRA_CAPTURE_ID` and `EXTRA_TASK_ID` are set on notifications but never read.

## 6. Search semantics today (B2 must keep)

All-tokens substring match in any order; tokens split on whitespace/punctuation; minimum 1 character; NFKC + lowercase + ё→е; **diacritics are not stripped** ("cafe" ≠ "café") — the proposal says "accents are ignored" (decision D-06); 150 ms debounce on the query; archived hidden unless chosen (reminders have no archived state). For dedup: kept thought = capture `status == Inbox`; sorted = `Processed`; a partly handled Brain Dump parent is still `Inbox` while some children already exist, and `NeedsYou` captures are also `Inbox` — P2 needs an explicit rule.

## 7. Reminders today (P4 must keep)

`ReminderEntity`: `dueAt`, `notificationOffsetMinutes`, `snoozedUntil`, `repeatRule` (daily/weekdays/weekly/monthly, optional `@HH:mm`), `notificationEnabled`, `completedAt`, `notificationWorkId`, `deliveredNotificationAt`. Notify time = `snoozedUntil` or `dueAt − offset`. No persisted scheduling-status enum: `ReminderSaveOutcome` (Saved / SavedNotScheduled / SavedNotificationsBlocked) is derived at read time from `notificationWorkId` + live permission checks (`ReminderCapabilities`: Ready / MayBeLate / MayBeStopped / Blocked). Exact alarm with inexact fallback plus WorkManager backup. Reconcile on boot, package replace, exact-alarm permission change, time-zone and clock change, and every cold start. Channel "Tallele reminders"; actions Done and Snooze 15 min; > 3 missed → one summary. Notification body is the notes text or "Tap to open Tallele", not "Due 09:00 · Health" (new work, P5). Manifest declares both `SCHEDULE_EXACT_ALARM` and `USE_EXACT_ALARM` (the latter must go before any Play release; not part of this redesign).

## 8. Other confirmations

- Backup format `luma-local-json` v7 (accepts 1–7). Restore **replaces** core data but **merges** learned rules and keeps aliases and AI history (`data/export/LocalDataRestorer.kt:102-151`) — "Replace what's on this device?" is not fully literal (decision D-11).
- Haze 1.7.2, Compose BOM 2024.12.01, Navigation 2.8.5, Room 2.7.1, WorkManager 2.10.0, minSdk 26, targetSdk 36 — all match §10.4.
- Fonts today: Google Sans Flex (3 TTFs, ~94 KB each) with its OFL text in `assets/licenses/`. Literata and Noto Sans are not bundled yet (P1).
- Calendar "Opens from a notification": no such path today.

## 9. Risks (evidence-backed)

1. **Erase completeness (P5):** beyond the proposal's list, must cover person/project memory, three prefs files, the Keystore alias, the URI grant, the missed-summary notification, in-flight application-scope work and the debounced settings overlay; cancel-then-delete race with AutoFiler inserting a reminder. Deleting the DB *file* would reset `sqlite_sequence` and allow id reuse with stale alarms — prefer table deletes inside a transaction after quiescing background work.
2. **Undo (P3):** must be built with a transactional guard; the existing `undo` also serves the sort-sheet and Review callers; reminder Undo must cancel alarm and work.
3. **B2 dedup (P2):** no back-link from notes/tasks to captures, ids collide across tables, Brain Dump provenance is deleted on completion → define "sorted" from capture status/outcome, not text matching.
4. **Receipt copy (P3):** replacing "Saved." / "Saved as a note." breaks `SaveReceiptTest`, `HomeCaptureViewModelTest`, `FirstReceiptTipTest` and ET/RU strings — update tests deliberately, never weaken them.
5. **Calendar as pushed screen (P2):** breaks `popUpTo(Home)` assumptions in `returnHomeWithCalendarCaptureDate`, `TabJourneyJvmTest` and two instrumented Calendar navigation tests; Review extra must become a tab switch.
6. **B10 (P4):** a Flow rewrite must keep one-shot messages/undo ids and must not overwrite an edit in progress (the screen already guards the edit buffer).
7. **Glass (P1):** cards currently use glass; removing it changes many screens' look before their own phase. Battery-saver detection is new.
8. **Conversion (P5):** compose with the two existing read-time remaps; prefer a write-once DataStore migration; device transfer brings old values.
