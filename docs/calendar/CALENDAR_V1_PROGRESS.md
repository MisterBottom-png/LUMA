# Calendar V1 progress

## Batch 0: Audit and implementation map

- Status: COMPLETE
- Date: 2026-07-14
- Goal: create an evidence-based implementation plan without changing production behavior.
- Included: preflight gate confirmation; audit of Home, navigation, Room, repositories, date/time fields, item visibility, reminder scheduling, export/restore, AI confirmation, theme/background, accessibility conventions, and tests; implementation map creation.
- Excluded: production code changes, Room schema changes, Calendar route/destination, Home behavior changes, Calendar UI, and all later Calendar batches.
- Architecture used: existing Room/repository/ViewModel/Navigation Compose architecture; Calendar planned as a non-persistent projection of finalized notes, tasks, and reminders.
- Schema changes: none.
- Suggested commit message: `Plan Calendar V1 implementation`
- Recommended next batch: Batch 1, Calendar data contract and date queries. Not started.

### Files inspected

- `AGENTS.md`
- `docs/calendar/CALENDAR_V1_SPEC.md`
- `docs/codex/PROJECT_STATE.md`
- `docs/codex/LUMA_PRODUCT_RULES.md`
- `docs/codex/LUMA_PROTECTED_BEHAVIORS.md`
- `docs/codex/WORKPLACE_PRIVACY_POLICY.md`
- `docs/codex/post-mvp/LUMA_POST_MVP_BRIEFS.md`
- Room entities, DAOs, database, type converters, repositories, schemas, and migration tests
- export/restore codec, store, reminder reconciliation, and tests
- application container and activity
- Home week strip and capture confirmation flow
- navigation destinations and item-detail routes
- Spaces, Review, Search, item detail, and reminder detail state flows
- reminder timing, repository, scheduler, alarm/worker, boot/package receiver, and manifest
- local/Gemini capture analysis and confirmation boundaries
- theme, background, glass, bottom navigation, time formatting, Settings, and relevant tests
- Gradle module configuration and test dependencies

### Files changed

- `docs/calendar/CALENDAR_V1_IMPLEMENTATION_PLAN.md`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

`docs/calendar/CALENDAR_V1_SPEC.md` was reviewed and preserved unchanged as the authoritative specification.

### Evidence summary

- Workplace identity purge: recorded COMPLETE.
- Cleanup baseline: recorded COMPLETE.
- Initial MVP: recorded PASS.
- Database: version 3 with migrations 1-to-2 and 2-to-3.
- Current Calendar-capable sources: tasks with nullable mixed-semantics `dueAt`; reminders with target `dueAt` and independent notification offset.
- Confirmed gaps: no explicit date-only representation, no scheduling fields on notes, no Calendar range queries/projection/route/state owner, and no duration/end-time model.
- Chosen safe direction: additive explicit epoch-day fields, preserve existing timestamps exactly, derive Calendar entries from original finalized records, and keep the reminder scheduler as the only notification boundary.
- Production behavior changed: no.

### Validation

- Documentation completeness check: passed; every implementation-plan section required by Batch 0 is present.
- Whitespace/error check and final path-scoped diff review: passed.
- Strict workplace privacy scan: passed; changed documentation also received semantic review.
- Compile/build: not run because Batch 0 changed documentation only and production behavior was not affected.

### Protected behavior checked

- Home remains capture-first and unchanged.
- Raw captures and internal processing records remain excluded from user-facing Calendar planning.
- Reminder target and notification offset remain separate.
- Existing 24-hour/device time-format behavior is preserved.
- Search, Spaces, Review, item detail, undo, export/restore, Reset Mode, and reminder scheduling are unchanged.

### Remaining risks and manual tests

- Legacy task date-only intent cannot be reconstructed reliably from existing timestamps.
- Later Room changes require migration and export/restore compatibility tests.
- Later reminder claims require emulator or physical-device evidence.
- Later Calendar UI requires locale, font-scale, theme/background, rotation, process recreation, and accessibility device checks.
- The working tree contains intentional pre-existing changes; later batches must maintain path-scoped diff evidence.

## Batch 1: Calendar data contract and date queries

- Status: COMPLETE
- Date: 2026-07-14
- Goal: implement the minimum safe domain and persistence support required to query finalized LUMA items by date without creating a second Calendar item system.
- Included: additive date-only/timed fields, database migration 3-to-4, range indexes and half-open DAO queries, immutable Calendar projection models, Room-backed Calendar repository, export format version 2 with version-1 restore compatibility, container wiring, and focused tests.
- Excluded: Calendar destination, Calendar UI, Home interaction, month grid, timeline, rescheduling UI, capture context, and all later Calendar batches.
- Architecture used: existing Room DAOs, repository/Flow pattern, application container, Java time APIs, export/restore codec, and reminder timing calculation.
- Suggested commit message: `Add Calendar V1 data projection and date queries`
- Recommended next batch: Batch 2, full Calendar shell and navigation. Not started.

### Current schema and data path

- Room remains the source of truth.
- Notes, tasks, and reminders remain the only finalized Calendar-capable sources.
- `CalendarRepository.observeRange` issues bounded source-table queries and maps each row to a projection keyed by source type and original source ID.
- Captures and internal AI/history tables are not queried by the Calendar repository.
- The Calendar repository is read-only and does not schedule notifications or mutate items.

### Proposed change implemented

- Notes now have nullable `scheduledDateEpochDay` and nullable `scheduledAt` fields.
- Tasks now have nullable `scheduledDateEpochDay`; existing `dueAt` remains the single timed task start.
- Reminders retain target `dueAt` and independent `notificationOffsetMinutes` unchanged.
- Repository writes reject note/task values that are simultaneously date-only and timed.
- Date-only values use `LocalDate.toEpochDay`; timed values remain absolute epoch milliseconds.
- Day, week, and month ranges use half-open local-date boundaries converted through an explicit zone.
- Completed tasks and reminders remain visible; archived notes/tasks and deleted rows are excluded.
- No duration, end time, timezone field, recurrence model, or persistent Calendar entity was added.

### Migration and upgrade path

- Database version: 3 to 4.
- Migration type: additive and non-destructive.
- Existing note scheduling fields initialize to `NULL`.
- Existing task `dueAt` values remain unchanged and are treated as timed legacy values; no date-only intent is guessed.
- New indexes cover note date/timed fields, task date/`dueAt`, and reminder `dueAt`.
- Room schema `4.json` was generated and migration validation passed on the connected Android device.
- Older migrations remain registered.

### Export, restore, reset, and recovery

- Local export format advances from version 1 to version 2 and includes the new nullable scheduling fields.
- The decoder continues to accept version-1 exports and defaults absent Calendar scheduling fields to `NULL`.
- Restore validation rejects conflicting date-only/timed state and invalid epoch-day values before replacement.
- Existing transactional replacement, relationship checks, repeat-restore behavior, and reminder reconciliation remain unchanged.
- Reset behavior is unchanged because it already operates on the source tables; there is no Calendar table to reset.
- Rollback risk is bounded to the additive migration. A failed migration prevents opening the upgraded database rather than deleting or reinterpreting rows; device migration tests provide current upgrade evidence.

### Files changed

- `app/src/main/java/com/orbit/app/OrbitApplication.kt`
- `app/src/main/java/com/orbit/app/data/local/entity/OrbitEntities.kt`
- `app/src/main/java/com/orbit/app/data/local/dao/OrbitDaos.kt`
- `app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt`
- `app/src/main/java/com/orbit/app/data/repository/EntityRepositories.kt`
- `app/src/main/java/com/orbit/app/data/repository/CalendarRepository.kt`
- `app/src/main/java/com/orbit/app/data/export/LocalDataBackupCodec.kt`
- `app/src/main/java/com/orbit/app/domain/calendar/CalendarModels.kt`
- `app/schemas/com.orbit.app.data.local.OrbitDatabase/4.json`
- `app/src/test/java/com/orbit/app/domain/calendar/CalendarDateRangeTest.kt`
- `app/src/test/java/com/orbit/app/data/repository/CalendarProjectionTest.kt`
- `app/src/androidTest/java/com/orbit/app/data/repository/CalendarRoomRepositoryTest.kt`
- `app/src/androidTest/java/com/orbit/app/data/local/OrbitDatabaseMigrationTest.kt`
- `app/src/test/java/com/orbit/app/data/export/LocalDataRestoreTest.kt`
- `app/src/test/java/com/orbit/app/reminders/ReminderSchedulingTest.kt`
- `app/src/test/java/com/orbit/app/data/export/LocalReminderRestoreReconcilerTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused Calendar date-range, projection, and export/restore JVM tests: passed.
- Complete `:app:test`: 244 test executions, zero failures and zero errors across configured unit-test variants.
- Focused connected Room migration and Calendar repository tests: 4 passed on the connected Android device.
- Affected connected Room export/restore tests: 2 passed on the connected Android device.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with 0 errors and 47 existing warnings.
- Room query-plan checks confirmed the Calendar range indexes are selected on the connected device.
- Strict workplace privacy scan: passed after the final documentation update; changed content also received semantic review.

### Protected behavior checked

- Existing task and reminder timestamps are preserved without reinterpretation.
- Reminder target and notification offset remain separate; notification time is derived read-only in the projection.
- Reminder scheduling, cancellation, boot/package replacement, and notification code are unchanged.
- Raw captures and internal processing records cannot become Calendar entries through these queries.
- Completed-item visibility follows current visible-item behavior.
- Archived and deleted items are excluded without changing Spaces, Review, Search, or item-detail filtering.
- Export/restore remains transactional and accepts existing version-1 exports.
- Home, navigation, UI, time-format settings, custom backgrounds, undo, Reset Mode, and AI confirmation are unchanged.

### Remaining risks and manual tests

- Legacy task values that originally represented date-only intent remain indistinguishable and are conservatively treated as timed.
- A real installed version-3 production database upgrade and version-1 user export restore remain recommended manual checks before release, although migration and Room restore instrumentation pass.
- Timezone changes intentionally move timed entries between local days; explicit date-only entries remain on their civil date.
- Notification delivery, exact-alarm behavior, reboot behavior, and package-replacement behavior were not changed or re-claimed in this batch.
- The repository retains substantial intentional pre-existing dirty state; only the listed Batch 1 paths were reviewed for this change.

## Batch 2: Full Calendar shell and navigation

- Status: COMPLETE
- Date: 2026-07-14
- Goal: create a dedicated full-screen Calendar destination with a real route, optional initial date, coherent navigation state, and restoration before connecting Home.
- Included: Calendar route and single-top navigation contract; full-screen shell; back and Today actions; day and month navigation; Day/Month active-view state; optional-date parsing and today fallback; saved-state restoration; focused JVM and connected navigation tests.
- Excluded: Home entry point, Calendar data rendering, month grid, daily timeline, rescheduling, capture context, external calendar access, and all later Calendar batches.
- Architecture used: existing Navigation Compose host, lifecycle-aware state collection, `ViewModel` with `SavedStateHandle`, Java time APIs, LUMA background/theme layer, and reusable glass surfaces.
- Schema changes: none.
- Suggested commit message: `Build Calendar V1 shell and date navigation`
- Recommended next batch: Batch 3, Home week-strip interaction. Not started.

### Navigation and state contract

- `calendar` opens with today when no date is supplied.
- `calendar?date={epochDay}` accepts an optional civil-date argument and falls back safely for missing, malformed, or out-of-range values.
- Calendar navigation uses `launchSingleTop` so rapid repeated navigation to the same route does not stack duplicate destinations.
- The selected date, coherent visible month, and active Day/Month view are persisted through `SavedStateHandle` for recreation.
- Day navigation updates the visible month when crossing a month boundary.
- Month navigation preserves the selected day where valid and clamps it to the target month's last valid day.
- Today restores both the selected date and visible month without changing the active view.
- App and system back use the standard Navigation Compose back stack.
- Opening or navigating inside the shell does not access repositories or mutate item data.

### Layout behavior

- The Calendar is a dedicated full-screen destination, not content embedded in Home.
- Status-bar and navigation-bar insets are applied to the screen content.
- The shell provides 48 dp back and date-navigation controls with meaningful accessibility descriptions.
- Month/year, selected date, Today, day navigation, month navigation, and active view are visible.
- The placeholder body is intentionally non-functional until later rendering batches.
- Existing theme colors and glass surfaces provide Light, Dark, Auto, preset-background, and custom-background treatment without adding a separate Calendar theme.
- The floating application navigation is hidden while the full-screen Calendar destination is active.

### Files changed

- `app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarScreen.kt`
- `app/src/test/java/com/orbit/app/ui/navigation/CalendarNavigationTest.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarViewModelTest.kt`
- `app/src/androidTest/java/com/orbit/app/ui/navigation/CalendarNavigationInstrumentedTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused Calendar route and state JVM tests: passed.
- Connected Calendar navigation tests: 2 passed on the connected Android test target; optional-date delivery, standard back-stack return, and repeated-navigation duplicate prevention were exercised.
- Complete `:app:test`: passed across configured unit-test variants.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with zero errors; the existing warning baseline remains unchanged.
- No Room, repository, export/restore, reminder, or scheduling code changed in this batch.

### Protected behavior checked

- Home remains capture-first and is not connected to Calendar in Batch 2.
- Opening Calendar cannot create, edit, archive, complete, schedule, or delete an item.
- Raw captures and internal processing records are not read or rendered by the shell.
- Calendar does not access external calendar accounts or the Android Calendar Provider.
- Reminder target time, notification offset, scheduling, cancellation, and delivery behavior are unchanged.
- Spaces, Life Feed, Review, Search, item detail, export/restore, Reset Mode, AI confirmation, and protected deletion are unchanged.
- Existing preset, custom-background, and no-background rendering continues through the application theme/background architecture.

### Remaining risks and manual tests

- Batch 2 intentionally has no Home entry point; that connection belongs to Batch 3.
- The placeholder shell has not received a manual matrix check for every theme, custom image, font scale, rotation, or process-death scenario; automated state restoration and existing theme architecture provide bounded evidence only.
- Calendar entries are not rendered yet, so no data visibility or item-opening claim is made for this batch.
- The repository retains substantial intentional pre-existing dirty state; the Batch 2 paths listed above were reviewed without modifying unrelated work.

## Batch 3: Home week-strip interaction

- Status: COMPLETE
- Date: 2026-07-14
- Goal: connect every visible Home date to the established Calendar destination without turning Home into a dashboard.
- Included: locale-ordered current-week dates; weekday and date labels; today and selected states; one item-presence indicator; exact-date Calendar navigation; saved Home selection; restrained selection motion; semantic labels; focused state and navigation coverage.
- Excluded: Calendar month grid, timeline content, item editing, rescheduling, capture context, Home dashboard content, external calendar access, and all later Calendar batches.
- Architecture used: existing Home composition and spacing, read-only Calendar projection repository, a dedicated `HomeWeekViewModel`, `SavedStateHandle`, existing Calendar single-top navigation, theme colors, and the established custom-background contrast layer.
- Schema changes: none.
- Suggested commit message: `Connect Home week strip to Calendar V1`
- Recommended next batch: Batch 4 as defined by the Calendar V1 specification. Not started.

### Home week behavior

- The strip displays the current seven-day week using the platform locale's first-day convention because LUMA has no separate configured week-start setting.
- Each date shows a locale-aware short weekday label and date number.
- Today uses a restrained outline and emphasized typography; the selected date uses a subtle tonal fill.
- Dates backed by one or more Calendar projections show one small presence dot, regardless of item count.
- Presence data is derived read-only from the existing finalized note, task, and reminder Calendar projection for the bounded current-week window.
- Date-only entries retain their civil date; timed entries use the current application zone to determine their local date.
- Tapping a visible date updates saved Home selection and opens Calendar with that exact epoch-day argument.
- Tapping today follows the same exact-date path.
- The established `launchSingleTop` route contract prevents rapid repeated taps from stacking the same Calendar destination.
- Returning from Calendar restores the selected Home date through the Home destination's saved state.
- Opening Calendar performs no repository write and does not create or alter an item.

### Layout, motion, and accessibility

- The strip remains in the existing Home header and adds only the height needed for the date number and presence dot.
- Seven equal-width 52 dp-high targets preserve the existing horizontal footprint.
- A short tonal-color transition connects selection without bounce, blur, or decorative motion.
- Each date exposes a merged button semantic containing the full localized date plus Today, selected, and scheduled-item states when applicable.
- Locale configuration changes trigger weekday/date recomposition.
- Text and selection colors reuse the application theme and the verified custom-background contrast treatment; no separate background panel or Home redesign was added.

### Files changed

- `app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/orbit/app/ui/screens/home/HomeWeekViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- `app/src/test/java/com/orbit/app/ui/screens/home/HomeWeekViewModelTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused Home week and Calendar route JVM tests: passed.
- Home week tests cover locale first-day behavior, seven unique dates, exact selection, today selection, saved-state restoration, date-only/timed presence mapping, deduplicated presence state, and semantic descriptions.
- Connected Calendar navigation tests: 2 passed on the connected Android test target; exact argument delivery, standard back-stack return, and duplicate-top prevention were exercised.
- Complete `:app:test`: passed across configured unit-test variants.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with zero errors; the existing warning baseline remains unchanged.

### Protected behavior checked

- Home remains calm and capture-first; no dashboard, agenda, item list, or additional action surface was added.
- Capture input position, keyboard/inset behavior, confirmation, and local fallback logic are unchanged.
- Raw captures and internal processing records cannot produce Home presence indicators because the Calendar repository projects finalized source tables only.
- Calendar opening and Home selection are read-only with respect to user items.
- Reminder target, offset, scheduling, cancellation, and delivery behavior are unchanged.
- Bottom navigation, Spaces, Life Feed, Review, Search, item detail, protected deletion, export/restore, Reset Mode, and AI behavior are unchanged.
- No external calendar account or Android Calendar Provider access was introduced.

### Remaining risks and manual tests

- A manual visual pass remains for narrow screens, 1.3 font scale, representative locales, Light/Dark/Auto, preset backgrounds, and varied custom images.
- A manual interaction pass remains for rapid physical taps, rotation, process recreation, and returning from Calendar with the selected Home date visible.
- Item-presence observation and navigation contracts have automated coverage, but the complete Home-to-Calendar gesture was not exercised through a Compose UI test because that test dependency is not present.
- The repository retains substantial intentional pre-existing dirty state; only the Batch 3 paths listed above were reviewed for this change.

## Batch 4: Custom month overview

- Status: COMPLETE
- Date: 2026-07-14
- Goal: implement a calm, locale-aware custom Compose month overview connected to the shared Calendar state.
- Included: pure month-grid generation; locale weekday ordering; adjacent-month cells; four-, five-, and six-row layouts; today, selection, and item-presence states; adjacent-date selection; read-only visible-range observation; restrained month crossfade; accessibility semantics; focused date/state tests.
- Excluded: daily timeline content, drag or gesture rescheduling, item titles inside cells, item editing, new scheduling behavior, external calendar access, and all later Calendar batches.
- Architecture used: existing Calendar destination and `SavedStateHandle` state owner, existing read-only Calendar projection repository, pure Java time grid calculations, Material theme colors, reusable glass surfaces, and the established Day/Month shell control.
- Schema changes: none.
- Suggested commit message: `Add custom Calendar V1 month overview`
- Recommended next batch: Batch 5, daily visual timeline. Not started.

### Month calculation and state

- Grid generation is outside composables in one `buildCalendarMonthGrid` function.
- The platform locale determines the first day of the week and localized weekday labels because LUMA has no separate configured week-start setting.
- Leading and trailing adjacent-month dates fill complete weeks without forcing a fixed six-row grid.
- Non-leap February can render four rows when aligned exactly; other month shapes render five or six rows as required.
- Leap-day, year-boundary, and every possible month-start weekday are covered by focused tests.
- Month movement preserves the selected day where valid and clamps it to the target month's final valid day.
- Tapping any cell selects its exact date. Tapping an adjacent-month cell also changes the visible month while preserving Month view.
- The existing Day control remains the explicit path to the selected day's view until Batch 5 supplies timeline content.
- Today refreshes the today marker, selected date, and visible month without changing the current view mode.
- Selected date, visible month, and active view continue to restore through saved state; today is refreshed after recreation.

### Item presence and data boundaries

- The Calendar ViewModel observes a bounded range covering the visible month plus up to six adjacent days on either side.
- Finalized note, task, and reminder projections are mapped to local civil dates through one shared domain helper.
- Date-only entries retain their civil date; timed entries use the current application zone.
- The grid renders one restrained dot per date with one or more projected items and never places item titles inside cells.
- Stale emissions from a prior visible month are rejected before updating the current grid state.
- No write method, scheduler, capture record, or internal processing record is accessed by the month overview.

### Layout, motion, and accessibility

- The month grid appears only in the existing Month view and retains the established full-screen shell, month/year header, Today action, and back behavior.
- Weekday headers and seven equal-width date columns adapt across four, five, and six grid rows.
- Adjacent-month dates use muted theme color; today uses an outline; selection uses a subtle tonal backing; item presence uses one small dot.
- A short crossfade provides restrained month movement without bounce, blur, or decorative motion.
- Each date is a practical full-cell target with button role, selected-state semantics, a full localized date description, and explicit Today, adjacent-month, and item-presence wording when applicable.
- Weekday abbreviations expose full weekday descriptions.
- Date text remains single-line and uses existing typography so increased font scale does not change the seven-column structure.

### Files changed

- `app/src/main/java/com/orbit/app/domain/calendar/CalendarModels.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarMonthGrid.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarScreen.kt`
- `app/src/main/java/com/orbit/app/ui/screens/home/HomeWeekViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarMonthGridTest.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarViewModelTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused Calendar month-grid and state tests: 14 passed.
- Date-generation tests cover leap February, four/five/six rows, locale first-day behavior, all month-start weekdays, year-boundary adjacent cells, today, selection, item presence, and semantic descriptions.
- State tests cover predictable month movement, adjacent-date selection, Month-view preservation, today, saved-state recreation, and projection-to-local-date observation.
- Complete `:app:test`: passed across configured unit-test variants.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with zero errors and 46 warnings. This is one warning below the preceding recorded baseline; Batch 4 performed no broad warning cleanup and added no Calendar-specific lint finding.
- No production dependency, Room entity, migration, export format, restore behavior, or scheduler changed.

### Protected behavior checked

- Home remains capture-first and its Batch 3 Calendar interaction is unchanged.
- Raw captures and internal processing records cannot enter the month projection.
- Calendar month browsing and date selection do not create, edit, archive, complete, schedule, or delete an item.
- Reminder target, notification offset, scheduling, cancellation, completion, and delivery behavior are unchanged.
- Existing 24-hour behavior is unchanged; the month overview introduces no time rendering.
- Bottom navigation, Spaces, Life Feed, Review, Search, item detail, protected deletion, export/restore, Reset Mode, and AI behavior are unchanged.
- No external calendar account or Android Calendar Provider access was introduced.

### Remaining risks and manual tests

- A manual visual matrix remains for four-, five-, and six-row months across Light, Dark, Auto, preset backgrounds, varied custom images, narrow screens, and 1.3 font scale.
- Manual accessibility traversal remains for weekday headers, date-cell reading order, selected/today announcements, and touch targets.
- Manual interaction remains for repeated month movement, adjacent-month taps, Today, rotation, and process recreation.
- Compose semantics are implemented and their source descriptions are unit-tested, but no Compose UI test dependency is present for node-level assertions.
- The repository retains substantial intentional pre-existing dirty state; only the Batch 4 paths listed above were reviewed for this change.

## Batch 5: Daily visual timeline

- Status: COMPLETE
- Date: 2026-07-14
- Goal: implement the selected day's primary experience with clear date-only and timed sections whose entries open the original item.
- Included: pure selected-day presentation model; Any time section; compact time-sorted lazy timeline; live current-time indicator for today; deterministic overlap stacking; calm loading and empty states; compact finalized-item cards; existing detail routing; focused timeline, state, boundary, and navigation tests.
- Excluded: scheduling edits, rescheduling, drag-and-drop, duration/end-time support, complex collision layout, item creation, external calendar access, and all later Calendar batches.
- Architecture used: existing Calendar projection and ViewModel, existing Day/Month shell, existing device-aware `OrbitTimeFormat`, Java time APIs, `LazyColumn`, existing soft glass cards, and established item/reminder detail routes.
- Schema changes: none.
- Suggested commit message: `Add Calendar V1 daily timeline`
- Recommended next batch: Batch 6, scheduling, rescheduling, and reminder integrity. Not started.

### Day presentation and positioning

- The selected-date header, day navigation, and Today action remain in the established Calendar shell.
- Date-only entries appear first under an `Any time` heading.
- Timed entries are filtered to the selected local day, sorted across the complete day, and grouped by local minute.
- Entries sharing the same minute are compactly stacked in one deterministic group; every card remains individually tappable.
- The timeline uses only rows containing relevant entries instead of allocating a large fixed block for every hour.
- Today includes a live current-time row that refreshes at minute boundaries and provides the initial scroll position.
- Other days open at the first relevant section; the lazy list remains usable for many items.
- Date-only entries retain their civil date and timed entries use the current application zone, including around midnight boundaries.
- The 100-item focused case preserves every item in the presentation model.

### Cards, status, and reminder context

- Cards show the finalized title and a compact Note, Task, or Reminder label.
- Completed tasks and reminders remain visible and receive a neutral `Completed` label.
- Reminder cards may show notification-disabled state or the independently stored notification offset without changing target time or scheduling.
- Long titles remain unchanged in the presentation model and use a two-line visual limit with ellipsis.
- No raw capture, internal analysis, classification, or processing record can enter the timeline because the source remains the finalized Calendar projection.
- Cards route notes and tasks to existing item detail and reminders to the existing reminder editor.
- Detail navigation uses the original source type and identifier and applies single-top behavior.

### Visual, time-format, and accessibility behavior

- Existing 24-hour, 12-hour, and device-following behavior is preserved through `OrbitTimeFormat`.
- The time label column has room for scaled 12-hour labels while retaining compact card width.
- Current time uses the theme primary color rather than red or urgency styling.
- Loading copy is neutral, and empty states do not imply failure, delay, or low productivity.
- Existing theme colors and soft glass surfaces preserve Light, Dark, Auto, preset-background, and custom-background treatment.
- Clickable Material surfaces expose each card's visible title and supporting details to accessibility services.
- The lazy layout and flexible card height support large fonts and long or numerous entries without fixed card heights.

### Files changed

- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarDayTimeline.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarDayTimelineView.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarScreen.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarDayTimelineTest.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarViewModelTest.kt`
- `app/src/test/java/com/orbit/app/ui/navigation/CalendarNavigationTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused timeline, Calendar state, and detail-routing tests: 21 passed.
- Timeline tests cover empty days, date-only entries, timed ordering, same-minute overlaps, long titles, 100 items, completed-item visibility, current-time positioning, and both midnight boundaries.
- Calendar state tests confirm projection entries and local dates reach the shared state and loading completes.
- Navigation tests confirm Note, Task, and Reminder projections resolve to their existing detail routes.
- Complete `:app:test`: passed across configured unit-test variants.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with zero errors and 46 warnings; no Batch 5 lint finding was introduced.
- No production dependency, Room entity, migration, export format, restore behavior, scheduler, alarm, or notification worker changed.

### Protected behavior checked

- Home remains capture-first; Home week-strip interaction is unchanged.
- Raw captures and internal processing records remain excluded from Calendar UI.
- Completed finalized items remain visible without altering their state.
- Calendar Day browsing and card opening do not create, edit, complete, archive, schedule, reschedule, or delete an item.
- Reminder target and notification offset remain separate; scheduling and cancellation behavior are unchanged.
- Existing 24-hour/device time-format behavior is reused rather than reimplemented.
- Spaces, Life Feed, Review, Search, protected deletion, undo, export/restore, Reset Mode, and AI behavior are unchanged.
- No external calendar account or Android Calendar Provider access was introduced.

### Remaining risks and manual tests

- A manual visual matrix remains for empty, date-only, timed, overlapping, long-title, completed, reminder, and many-item days across Light, Dark, Auto, preset backgrounds, varied custom images, and 1.3 font scale.
- Manual checks remain for both 12-hour and 24-hour displays, current-time initial positioning and minute refresh, midnight transitions, rapid day navigation, rotation, and process recreation.
- Manual accessibility traversal remains for section order, grouped overlaps, current-time semantics, card announcements, and large-font touch usability.
- Item-detail routing has pure mapping coverage and existing destination coverage, but the complete card-tap gesture was not exercised through a Compose UI test because that test dependency is not present.
- The repository retains substantial intentional pre-existing dirty state; only the Batch 5 paths listed above were reviewed for this change.

## Batch 6: Scheduling, rescheduling, and reminder integrity

- Status: COMPLETE
- Date: 2026-07-14
- Goal: connect safe date-only and timed scheduling changes to original finalized items while retaining the established reminder scheduling boundary.
- Included: note and task schedule assignment; Today and Tomorrow shortcuts; another-date selection; timed scheduling; timed/date-only conversion; schedule removal; single-use undo; original-row metadata preservation; existing reminder replacement, cancellation, offset, completion, reopening, and deletion verification.
- Excluded: Calendar capture entry points, drag-and-drop, duration or end-time modeling, a competing Calendar editor, external calendar access, schema or export changes, and all later Calendar batches.
- Architecture used: existing note and task repositories, original Room rows, existing item detail screen, existing reminder detail editor, existing reminder repository and scheduler, Java time APIs, and the shared Calendar projection for reactive updates.
- Schema changes: none.
- Suggested commit message: `Connect Calendar V1 scheduling and reminders`
- Recommended next batch: Batch 7, Calendar entry points and continuity. Not started.

### Scheduling behavior

- Finalized notes and tasks expose a compact Schedule section in their existing item detail screen.
- Today, Tomorrow, and Choose date produce a date-only schedule. Add time or Change time uses the existing date/time picker style and produces a timed schedule.
- Date only converts a timed schedule on the same local civil date. Remove schedule clears scheduling without deleting or duplicating the item.
- Each change updates the original row through its repository and changes only the appropriate schedule fields plus `updatedAt`.
- A successful change offers one short-lived Undo action. Undo restores the prior schedule on the same identifier while preserving unrelated metadata edited after the scheduling action.
- Repeated edits replace the pending undo snapshot; stale or repeated callbacks do nothing.
- Invalid, missing, unsupported, unchanged, and repository-failure paths fail safely without inserting another item.
- Calendar continues to react to Room projection changes; no Calendar-owned mutable copy or second editor was introduced.

### Reminder integrity

- Reminder cards continue to open the existing reminder editor rather than generic item detail.
- Reminder target and notification offset remain independent. Target edits preserve the selected offset, and offset edits preserve the target.
- The existing reminder repository remains the only persistence boundary and the existing scheduler remains the only work/alarm boundary.
- Rescheduling cancels obsolete work and uses unique replacement work for the same reminder identifier. Completion, disabling, and deletion cancel work; reopening or re-enabling schedules one replacement.
- Offset calculation, including a notification time on the previous day, overdue handling, repeated target edits, local persistence, and scheduler-failure retention have focused coverage.
- The scheduler continues to use inexact `setAndAllowWhileIdle`; no exact-alarm permission or exact-alarm behavior was introduced.
- Natural-language capture, raw-capture persistence, confirmation, and local Gemini fallback were not changed. Calendar-provided capture defaults remain outside this batch because Calendar capture entry points belong to Batch 7.

### Files changed

- `app/src/main/java/com/orbit/app/ui/screens/item/ItemScheduleActions.kt`
- `app/src/main/java/com/orbit/app/ui/screens/item/ItemDetailViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/screens/item/ItemDetailScreen.kt`
- `app/src/test/java/com/orbit/app/ui/screens/item/ItemScheduleActionsTest.kt`
- `app/src/test/java/com/orbit/app/reminders/ReminderSchedulingTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused schedule-action and reminder tests: 24 passed.
- Schedule-action tests cover note and task date-only/timed conversion, removal, single-use undo, repeated edits, stale callbacks, duplicate prevention, exact identifier reuse, metadata preservation, invalid input, missing rows, unsupported types, and repository failure.
- Reminder tests cover target-minus-offset calculation, previous-day offsets, invalid and overdue timing, insertion, target and offset editing, replacement, repeated edits, completion, reopening, disabling, re-enabling, deletion, persistence across repository recreation, and scheduler failure.
- Complete `:app:test`: passed across configured unit-test variants.
- `:app:assembleDebug`: passed.
- `:app:lintDebug`: passed with zero errors and 46 warnings; no Batch 6 lint finding was introduced.
- No connected Android target was available, so instrumentation, notification delivery, reboot, package replacement, process-death, permission-denial UI, and physical interaction checks were not run or claimed.

### Protected behavior checked

- Scheduling updates existing finalized rows and creates no raw, internal, or duplicate user-facing item.
- Reminder target and notification offset remain separate, and the existing replacement/cancellation boundary is unchanged.
- Raw capture is not rewritten or mutated; capture confirmation, exactly-once finalization, and local Gemini fallback are unchanged.
- Completion, reopening, archive, protected deletion, Spaces, Life Feed, Review, Search, export/restore, Reset Mode, themes, and 12/24-hour settings are unchanged outside the schedule fields exercised here.
- No Room entity, database version, migration, export contract, restore behavior, production dependency, external calendar integration, or Calendar Provider access changed.

### Remaining risks and manual tests

- Run note and task scheduling, conversion, removal, Undo, relaunch, rotation, and process-death checks on an emulator or physical device.
- Verify reminder notification replacement and cancellation through target edits, offset edits, completion, reopening, disabling, re-enabling, deletion, reboot, and package replacement on a physical target.
- Verify notification-permission denial and later grant, timezone change behavior, inexact-alarm delivery behavior, and previous-day offsets on supported Android versions.
- Run the available Room and Calendar instrumentation tests when a connected target is available.
- Manually verify the Schedule section with Light, Dark, Auto, 1.3 font scale, accessibility traversal, and 12/24-hour display.
- The repository retains substantial intentional pre-existing dirty state; only the Batch 6 paths listed above were reviewed for this change.

## Batch 7: Calendar entry points and continuity

- Status: COMPLETE
- Date: 2026-07-14
- Goal: integrate Calendar with existing Home capture, original-item detail, Search, reminder, and shared Room projection flows.
- Included: selected-day capture context; minimal Add for this day action; explicit and removable Home context; visible confirmation context; date-aware note and task finalization; reminder picker date default; shared navigation and back-stack continuity verification; reactive projection regression coverage.
- Excluded: a separate Calendar editor, direct Calendar record creation, raw-text rewriting, drag-and-drop, external calendar access, new repositories, schema changes, and later Calendar work.
- Architecture used: existing Home capture and confirmation flow, existing note/task/reminder repositories, existing Calendar projection, existing item and reminder detail routes, Navigation saved state, and current Material/Glass UI components.
- Schema changes: none.
- Suggested commit message: `Integrate Calendar V1 with LUMA item flows`
- Next Calendar batch: none in the current specification. No deferred feature was started.

### Entry points and capture continuity

- Home week dates continue to open Calendar on the exact selected date through the established encoded route.
- Calendar exposes one restrained `Add for this day` action in the existing shell; it returns to the existing Home capture surface rather than opening a second creation form.
- The selected date is transferred through the Home back-stack entry as a validated epoch day and appears as a removable `Adding for` context above capture.
- Submitting capture consumes the Home context only after capture analysis is accepted, then carries the validated date in the in-memory confirmation suggestion.
- Raw capture text is passed to the existing analyzer unchanged. Calendar context is separate metadata and is never injected into the text.
- Confirmation displays the selected date before any final action. Task and reminder setup retain the visible date context; a reminder date/time picker starts on that date when analysis supplied no time.
- Confirmed notes receive the selected date as their existing date-only schedule. A task whose visible due date matches the Calendar context is finalized as date-only; clearing or choosing another date retains the existing task flow.
- Finalization still uses the serialized exactly-once capture use case, links the single finalized item to its raw source, and rolls back the finalized row if capture processing fails.

### Cross-screen continuity

- Calendar entries continue routing notes and tasks to generic original-item detail and reminders to the dedicated reminder editor.
- Reminder notifications continue opening the original reminder identifier through the existing reminder destination.
- Item detail is pushed above Calendar, so Back restores the same Calendar entry, encoded date, ViewModel, selected date, visible month, and Day/Month view state.
- Editing, completion, reopening, archiving, deletion, and archive Undo continue changing original Room rows; Calendar observes those changes through its single finalized-item projection.
- Focused projection coverage verifies title/status updates on the same identifier, removal, and restoration without a duplicate.
- Search and Spaces retain their established original-item routes and finalized-only query boundaries. Neither creates Calendar records.
- Life Feed and raw/internal visibility boundaries are unchanged; Calendar still receives only finalized note, task, and reminder projections.

### Files changed

- `app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt`
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- `app/src/main/java/com/orbit/app/ui/screens/calendar/CalendarScreen.kt`
- `app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt`
- `app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt`
- `app/src/main/java/com/orbit/app/domain/usecase/ConfirmCaptureActionUseCase.kt`
- `app/src/test/java/com/orbit/app/ui/navigation/CalendarNavigationTest.kt`
- `app/src/test/java/com/orbit/app/ui/screens/calendar/CalendarViewModelTest.kt`
- `app/src/test/java/com/orbit/app/ui/screens/home/CalendarCaptureContextTest.kt`
- `app/src/test/java/com/orbit/app/domain/usecase/ConfirmCaptureActionUseCaseTest.kt`
- `app/src/androidTest/java/com/orbit/app/ui/navigation/CalendarNavigationInstrumentedTest.kt`
- `docs/calendar/CALENDAR_V1_PROGRESS.md`

### Tests and builds

- Focused navigation, state, capture-context, and exactly-once finalization suite: 23 tests passed.
- Unit coverage includes date route validation, context validation, original-item routes, saved Calendar date/view restoration, reactive edit/completion/removal/Undo projection updates, date-only note/task finalization, exact source linking, duplicate prevention, and rollback behavior.
- Android navigation tests compile with coverage for Home-to-Calendar arguments, duplicate-top prevention, Add-for-day context transfer, and Calendar restoration after returning from item detail.
- Complete `:app:test`: 338 configured debug/release JVM executions passed with 0 failures, 0 errors, and 0 skipped.
- `:app:assembleDebug`: passed.
- `:app:assembleDebugAndroidTest`: passed.
- `:app:lintDebug`: passed with zero errors and the existing 46 warnings; no Batch 7 lint finding was introduced.
- No connected Android target was available, so the compiled navigation instrumentation and physical Home/Calendar/capture interaction were not executed or claimed.

### Protected behavior checked

- Home remains capture-first; the date context is compact, removable, and uses the existing capture card and confirmation sheet.
- Raw capture is saved before optional AI work and is never rewritten with Calendar context.
- Exactly one finalized item is produced through the existing confirmation use case; raw and internal records remain excluded from Calendar, Spaces, and Life Feed.
- Reminder target, notification offset, reminder-specific routing, scheduling, and cancellation are unchanged.
- Search opens original items and creates no Calendar record.
- Completion, reopening, archive, deletion, Undo, protected deletion, Waiting For, Someday, Make Smaller, Brain Dump, Ask LUMA, export/restore, Reset Mode, themes, and time-format behavior remain unchanged outside the integration exercised here.
- No Room entity, migration, export contract, restore behavior, dependency, external calendar integration, or Calendar Provider access changed.

### Remaining risks and manual tests

- Execute the compiled navigation instrumentation on an emulator or physical device.
- Manually verify Home date to Calendar, Add for this day, visible context removal, natural capture, confirmation, note/task/reminder finalization, and exact Calendar projection after returning.
- Verify Back from note, task, and reminder detail restores the exact selected date and Day/Month view after edits, completion, reopening, archive, deletion, and Undo.
- Verify Search and Spaces item opening followed by Calendar observation creates no duplicate records and keeps raw/internal data hidden.
- Exercise rotation, process recreation, keyboard/inset behavior, Light/Dark/Auto, 1.3 font scale, accessibility reading order, and long localized dates.
- The repository retains substantial intentional pre-existing dirty state; only the Batch 7 paths listed above were reviewed for this change.
