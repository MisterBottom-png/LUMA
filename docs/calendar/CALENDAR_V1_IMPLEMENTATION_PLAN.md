# Calendar V1 implementation plan

## Status and evidence baseline

- Batch 0 status: complete.
- Audit date: 2026-07-14 (Europe/Tallinn).
- Git root: `C:/Users/User/Projects/orbit-android`.
- Branch: `master`.
- Audited commit: `09c56f0e1cc566f8bfeaca6bb5cea824e6b9d25e`.
- The working tree contained substantial pre-existing MVP and cleanup work before this batch. Batch 0 preserves that work and changes documentation only.
- Recorded gates in `docs/codex/PROJECT_STATE.md`: workplace identity purge complete, cleanup baseline complete, and initial MVP pass.

This plan is based on the current working tree, not only on the audited commit. `docs/calendar/CALENDAR_V1_SPEC.md` remains the authoritative product and batch specification.

## Current reusable architecture

### Application and state

- `OrbitApplication` owns a simple lazy `OrbitContainer` with the Room database, repositories, reminder scheduler, export/restore services, and AI router.
- Screen state follows a ViewModel plus immutable UI-state/`StateFlow` pattern. Repositories expose Room-backed `Flow` values, and screens collect them with lifecycle awareness.
- `OrbitApp` owns a single Navigation Compose `NavHost`. Main destinations are Home, Spaces, Review, and Settings; Search, reminder detail, and generic item detail are secondary routes.
- Existing item navigation routes reminders to `ReminderDetailScreen` and notes/tasks to `ItemDetailScreen`. Calendar entries should reuse `ItemDetailDestination.route` rather than create another editor.

### Home and navigation

- Home already renders a seven-cell week strip, but it uses fixed one-letter labels, only marks today, has no date numbers, has no item-presence data, and is not interactive.
- Home remains capture-first and must not receive an agenda, counts, item cards, or a month grid.
- The bottom navigation is drawn outside the `NavHost`. A full-screen Calendar route will need an explicit rule that hides the bottom navigation while Calendar is active.

### UI and accessibility

- The app uses Material 3 color schemes, `OrbitTheme`, `OrbitBackground`, `GlassSurface`, and the existing typography and spacing patterns.
- Appearance supports Light, Dark, and Auto modes, preset backgrounds, custom images, configurable dim/blur/glass strength, accent colors, and text colors.
- Custom images receive a restrained top contrast treatment, and glass surfaces receive custom-background tonal reinforcement. Calendar should reuse these boundaries rather than add its own global background system.
- Existing screens use status/navigation-bar insets, content descriptions, and semantic labels selectively. Calendar controls and date cells must add explicit selected/today semantics and practical touch targets.

### Testing

- JVM tests use JUnit 4 and small fake repositories/schedulers.
- Instrumentation tests use AndroidJUnit4, in-memory Room, and `MigrationTestHelper` with exported schemas.
- There is no Compose UI test dependency in the current module. Pure date/state/layout calculations should therefore receive JVM coverage first; adding Compose test infrastructure is justified only when a later UI batch cannot obtain meaningful regression coverage otherwise.
- The repository contains one Gradle application module, `:app`, with Java 17, Compose, Room, Navigation Compose, WorkManager, and core-library time APIs already available through the platform minimum.

## Current data model

Room database version is 3. The schema contains spaces, captures, notes, tasks, reminders, and internal learning/history tables.

| Source | Current scheduling fields | Status/visibility fields | Calendar interpretation today |
|---|---|---|---|
| Note | None | `archived` | Cannot be dated or timed. |
| Task | nullable `dueAt`, nullable legacy `reminderAt` | `TaskStatus`, `completedAt` | `dueAt` is an epoch-millisecond value. Current creation can write end-of-day for date-only intent, while item detail can write an arbitrary date and time, so stored intent is mixed. `reminderAt` is persisted/exported but has no active scheduling call path. |
| Reminder | required `dueAt`, `notificationOffsetMinutes`, `notificationEnabled`, `notificationWorkId` | `completedAt` | `dueAt` is the user-facing target instant. Notification time is derived as target minus offset. |
| Capture | No finalized schedule | `CaptureStatus`, `linkedItemId` | Private source/internal record; never a Calendar entry. |

All current date/time persistence uses `Long` epoch milliseconds. UI pickers create values in `ZoneId.systemDefault()`, and formatters convert stored instants through the current system zone. No item stores a timezone, local civil date, duration, or end time.

## Data-model gaps and decisions

### Confirmed gaps

1. Date-only intent is not explicit. A date-only task can currently be encoded as a timestamp near the end of the selected day.
2. Notes cannot be scheduled.
3. The task `dueAt` field contains both date-only-like legacy values and true timed values; no reliable discriminator exists.
4. There are no range-specific DAO queries or indexes for Calendar day/week/month loading.
5. There is no Calendar-facing projection, repository, route, state owner, or UI.
6. There is no duration/end-time support. Calendar V1 must not invent it.

### Minimum additive contract for Batch 1

Batch 1 should use an additive Room migration from version 3 and introduce only the missing scheduling representation:

- `NoteEntity.scheduledDateEpochDay: Long?` for date-only notes.
- `NoteEntity.scheduledAt: Long?` for timed notes.
- `TaskEntity.scheduledDateEpochDay: Long?` for date-only tasks.
- Existing `TaskEntity.dueAt` remains the task's single timed scheduled-start value.
- Existing `ReminderEntity.dueAt` remains the reminder target/scheduled-start value.

Invariant: an item must not have both a date-only value and a timed start. Repository writes validate this. For notes, `scheduledDateEpochDay` and `scheduledAt` are mutually exclusive. For tasks, `scheduledDateEpochDay` and `dueAt` are mutually exclusive.

This contract avoids a second competing timed field for tasks, preserves reminder semantics, and gives notes only the fields they currently lack. It does not add a Calendar entity, duration, end time, timezone, recurrence, or external identifier.

### Existing-row migration policy

- Existing note and task date-only columns are initialized to `NULL`.
- Existing task `dueAt` values are preserved byte-for-byte and treated as timed legacy values. Batch 1 must not guess whether a particular historical value originally came from the date-only picker.
- Existing task `reminderAt` remains readable and exported but is not used as Calendar scheduling state. Removing or reinterpreting it is outside Calendar V1.
- Existing reminders are unchanged.

This policy may display a legacy end-of-day task as timed. That is the only safe automated interpretation because the current database contains no evidence that distinguishes date-only from timed intent. Users can convert it explicitly in the later scheduling batch.

## Date and time semantics

- Date-only values use ISO `LocalDate.toEpochDay()` semantics. They remain on the same civil date when the device timezone changes.
- Timed values remain absolute epoch-millisecond instants. Their displayed local time and Calendar day follow the current device timezone, matching current reminder and item-detail behavior.
- Range construction occurs outside composables: convert the selected local date's start and the following date's start through the current zone, then query the half-open interval `[start, end)`.
- Month and week ranges also use half-open boundaries. This prevents midnight duplication and correctly handles 23-hour and 25-hour local days.
- `LocalDateTime.atZone()` remains the existing picker conversion boundary. DST gaps/overlaps must receive explicit tests in Batch 1; no new date-time library is needed.
- Reminder notification time remains derived from `dueAt - notificationOffsetMinutes`, including offsets that land on the previous day. Calendar does not schedule work itself.
- Existing time-format behavior is preserved through `OrbitTimeFormat` and the Device/24-hour/12-hour setting. Calendar adds no new time-format preference.

## Completed-item and inclusion policy

The current visible-item surfaces include completed tasks and completed reminders while excluding archived notes and archived tasks. Calendar V1 will preserve that least-disruptive behavior:

- include notes when not archived and when date-only or timed scheduling exists;
- include tasks in Open, Done, Waiting For, or Someday state when scheduling exists;
- exclude tasks in Archived state;
- include completed tasks with completed display metadata;
- include reminders with a target time, including completed reminders, with completed display metadata;
- exclude deleted rows because they no longer exist;
- never query captures, AI suggestion history, correction history, learned memory, or other internal tables for Calendar cards.

Each projection key is `(sourceType, sourceItemId)`. A linked relationship does not generate an additional synthetic Calendar entry. Separately persisted finalized records remain separately addressable; Calendar never creates a second persistent copy of either record.

## Proposed Calendar projection and query strategy

Create a non-persistent domain model under a Calendar package, following existing naming:

- `CalendarEntryId(sourceType, sourceItemId)`;
- `CalendarItemType` with Note, Task, and Reminder;
- `CalendarSchedule` as a sealed date-only or timed value;
- `CalendarEntry` containing the original identifier, type, title, Space ID, schedule, task/reminder completion metadata, reminder target, reminder offset, and notification time where derivable.

Create a `CalendarRepository` backed by the existing DAOs. It combines three source queries into one deterministic flow and maps rows to immutable projections. It must not expose write methods or schedule reminders.

DAO queries should load only the requested range:

- notes: non-archived rows whose `scheduledDateEpochDay` is in the epoch-day range or whose `scheduledAt` is in the instant range;
- tasks: non-archived rows whose `scheduledDateEpochDay` is in the epoch-day range or whose `dueAt` is in the instant range;
- reminders: rows whose `dueAt` is in the instant range.

Indexes should match those predicates after query-plan review: note date/timed fields, task date/`dueAt`, and reminder `dueAt`. Add only indexes demonstrated useful by the final SQL. Day, week, and month use the same range APIs with different boundaries.

## Proposed route and state owner

- Add `CalendarDestination` in the existing navigation package.
- Route contract: an optional epoch-day argument, for example `calendar?date={epochDay}`. A missing or invalid value resolves to today without mutating data.
- Add one screen-level `CalendarViewModel`, created through the existing factory/container style and backed by `SavedStateHandle` where practical.
- The ViewModel owns selected date, visible month, active view, loaded range, entries, loading/empty state, and restoration metadata. Date generation, grouping, timeline placement, overlap handling, and timezone conversion stay outside composables.
- The Calendar screen is a dedicated full-screen destination. Bottom navigation is hidden while it is active. System back returns to the caller.
- Entry selection delegates to `ItemDetailDestination.route` so the existing reminder-specific and note/task detail flows remain authoritative.

## Reminder integration boundary

- `RoomReminderRepository` remains the only repository boundary that invokes `ReminderScheduler`.
- `WorkManagerReminderScheduler` continues to replace unique work and alarms by reminder ID; completion, disabling, deletion, and rescheduling continue through repository update/delete operations.
- Boot and package-replacement reconciliation remain owned by `ReminderBootReceiver` and `ReminderRescheduleWorker`.
- Calendar repository and UI never call `AlarmManager`, WorkManager, receivers, workers, or notification APIs directly.
- Batch 6 may update an original task/note/reminder through its established repository and then observe the resulting Calendar projection. It must preserve reminder target and offset as independent values.

## Export and restore implications

The current local JSON format is version 1 and includes spaces, captures, notes, tasks, and reminders. It preserves IDs and relationships, validates before replacement, replaces transactionally, and reconciles reminders afterward. App settings are not part of the export contract.

Because Batch 1 adds scheduling fields to notes/tasks, it must update the export contract deliberately:

- emit a new version that includes the new nullable fields;
- continue decoding version-1 exports with the new fields defaulted to `NULL`;
- validate that date-only and timed fields are not both present;
- preserve repeat-restore exactness and relationship validation;
- add round-trip, old-version, duplicate-restore, rollback, and Room replacement coverage;
- leave reminder target/offset encoding and post-restore reconciliation unchanged.

Calendar has no independent export collection because it has no persistent Calendar records. Settings remain excluded unless separately authorized.

## Batch-by-batch file impact

### Batch 1: data contract and queries

Likely production files:

- `data/local/entity/OrbitEntities.kt`
- `data/local/dao/OrbitDaos.kt`
- `data/local/OrbitDatabase.kt`
- generated Room schema JSON through the Room build
- `data/repository/EntityRepositories.kt` or a focused Calendar repository file
- new Calendar domain/projection and range-conversion files
- `OrbitApplication.kt` for repository wiring
- `data/export/LocalDataBackupCodec.kt`

Likely tests:

- Calendar range/projection JVM tests
- Room DAO and migration instrumentation tests
- export/restore compatibility tests

### Batch 2: Calendar shell and navigation

- `ui/navigation/OrbitDestination.kt`
- `ui/navigation/OrbitApp.kt`
- new `ui/screens/calendar/CalendarScreen.kt`
- new `ui/screens/calendar/CalendarViewModel.kt`
- route/state restoration JVM tests

No Home changes in this batch.

### Batch 3: Home week strip

- `ui/screens/home/HomeScreen.kt`
- possibly `HomeCaptureViewModel.kt` only for item-presence state
- `ui/navigation/OrbitApp.kt` for the date-selection callback
- locale/week-strip calculation and semantic tests

### Batch 4: month overview

- focused Calendar date-grid/domain calculation files
- Calendar composables within `ui/screens/calendar`
- month-grid and accessibility-state tests

### Batch 5: daily timeline

- focused timeline grouping/placement files
- Calendar screen/state files
- timeline calculation and navigation tests

### Batch 6: scheduling and reminder integrity

- Calendar reschedule UI/state files
- existing item repositories/detail boundaries only where required
- reminder repository/scheduler files only if a confirmed gap exists
- focused scheduling, cancellation, replacement, timezone, and persistence tests

### Batch 7: continuity

- `ui/navigation/OrbitApp.kt`
- Home/Calendar/item-detail integration callbacks
- capture context only through the existing confirmation pipeline
- cross-screen navigation/state tests

### Batch 8: final quality

- only confirmed Calendar-specific accessibility, performance, theme, or layout defects
- Calendar tests and documentation
- no unrelated application cleanup

## Verification strategy

Every implementation batch must run its focused JVM tests, affected compilation, broader relevant tests once, `:app:assembleDebug`, the strict workplace privacy checker, and a final diff review. Schema batches also run migration and Room instrumentation tests. Reminder behavior requires device evidence before claiming notification, exact-alarm, reboot, or package-replacement success.

Calendar-specific coverage must include:

- date-only versus timed projection;
- half-open day/week/month boundaries;
- timezone and DST behavior;
- completed/archived/deleted filtering;
- raw/internal exclusion;
- route arguments and state restoration;
- locale/first-day-of-week month generation;
- timeline overlap and large-font behavior;
- original-item navigation and no duplicate persistent Calendar records;
- reminder replacement/cancellation and previous-day offsets;
- Light, Dark, Auto, preset, and custom-background device checks.

## Risks

1. Legacy task `dueAt` intent is ambiguous. Preserve values and classify them as timed rather than infer date-only intent.
2. The current export format is strict. Schema changes require explicit version compatibility work in the same authorized data batch.
3. The current DAO layer observes entire tables. Calendar must add bounded range queries to avoid month/timeline performance problems.
4. Compose UI instrumentation support is not configured. Later UI batches may need a narrowly justified test dependency or must document manual device evidence.
5. Timestamp day membership changes with the device timezone by current design; date-only epoch-day values must not.
6. Reminder scheduling failures are intentionally non-destructive to local data. Calendar must show authoritative stored state and not imply delivery success.
7. The pre-existing dirty tree makes commit attribution fragile. Each Calendar batch must review only its paths and record the exact tested state.

## Non-goals

- external calendar sync, Calendar Provider access, accounts, or shared calendars;
- a second Calendar database or generic Calendar event entity;
- recurrence, complex series editing, drag-and-drop by default, duration/end-time invention, or attendee/location systems;
- a Home agenda/dashboard;
- a new global completion policy;
- a new time-format setting;
- unrelated cleanup or redesign of capture, Review, Spaces, Search, reminders, item detail, export/restore, themes, or Settings.

## Batch 0 conclusion

The current model cannot represent Calendar V1 correctly without an additive Batch 1 migration because date-only state is not explicit and notes cannot carry scheduling data. The safe representation, legacy migration policy, range-query boundary, projection identity, completion policy, route contract, reminder boundary, export compatibility work, and per-batch impact are defined above. Batch 1 can proceed without guessing about data representation, but it is not started in this run.
