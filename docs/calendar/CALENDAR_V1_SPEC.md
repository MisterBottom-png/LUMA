LUMA: Implement Calendar V1 through controlled, independently verified batches.

# Execution scope

This document is the authoritative product and implementation specification for Calendar V1.

Execute only one Calendar batch per Codex run.

For the first run, execute Batch 0 only.

For later runs, execute only the batch explicitly requested by the user. Never continue automatically into the next Calendar batch.

Do not treat the full roadmap as authorization to implement every batch in one run.

Each batch must be:

* coherent;
* independently reviewable;
* safe to commit;
* validated before completion;
* documented before stopping.

Follow the repository-root `AGENTS.md` and use `luma-autopilot` as the controller.

Use one primary specialist and no more than one supporting specialist unless the repository instructions clearly require otherwise. Do not activate every LUMA skill at once.

---

# Preflight gates

Before performing Calendar implementation:

1. Read the repository-root `AGENTS.md`.
2. Read:

   * `docs/codex/PROJECT_STATE.md`
   * `docs/codex/LUMA_PRODUCT_RULES.md`
   * `docs/codex/LUMA_PROTECTED_BEHAVIORS.md`
   * `docs/codex/WORKPLACE_PRIVACY_POLICY.md`
   * `docs/codex/post-mvp/LUMA_POST_MVP_BRIEFS.md`
3. Read additional canonical documents only when required by the requested batch.
4. Inspect the current repository state rather than trusting historical completion claims.

Calendar V1 is post-MVP feature work.

Do not begin Calendar implementation unless:

* `Workplace identity purge` is recorded as `COMPLETE`;
* `Cleanup baseline` is recorded as `COMPLETE`;
* the core MVP has reached an honest `PASS`, supported by current repository evidence.

If any prerequisite is incomplete:

* do not implement Calendar;
* do not combine cleanup, MVP repair, and Calendar work into one run;
* stop and report the exact unmet prerequisite;
* never quote or reproduce workplace-person identifiers.

Do not silently edit `PROJECT_STATE.md` merely to satisfy a gate.

---

# Objective

Build Calendar V1 as LUMA’s local visual planning surface for dated and scheduled LUMA items.

Calendar must help the user understand:

* what belongs to a particular day;
* what has a specific time;
* what may happen at any time during the day;
* when reminders will occur;
* how an item can be rescheduled without friction.

Calendar is not a separate productivity system.

Calendar must not create duplicate user-facing copies of LUMA items.

Room remains the source of truth. Calendar reads and updates the same finalized user-facing items already used by Inbox, Review, Spaces, reminders, Search, undo, and item detail.

---

# Required execution method

For the requested batch:

1. Inspect the narrowest relevant implementation before editing.
2. Confirm the current behavior or data flow.
3. Select the smallest appropriate specialist workflow.
4. Reuse existing architecture, components, repositories, navigation, design tokens, state conventions, item models, and reminder infrastructure.
5. Make the smallest reliable change that completes the requested batch.
6. Keep the affected module compiling throughout the work.
7. Add or update tests when they materially reduce regression risk.
8. Run targeted checks during implementation.
9. Run broader relevant checks once before completing the batch.
10. Review the final diff for unrelated changes.
11. Update:

    * `docs/calendar/CALENDAR_V1_PROGRESS.md`
12. Update other Calendar documents required by the current batch.
13. Run the workplace privacy check after text-bearing changes:

    * `python scripts/codex/check_workplace_privacy.py --strict`
14. Stop after the requested batch.

Do not request approval for ordinary low-risk implementation decisions already resolved by this specification.

Stop and report instead of improvising when encountering:

* a destructive Room migration;
* uncertainty that could cause user-data loss;
* conflict with approved LUMA product rules;
* architecture gaps requiring a broad unrelated rewrite;
* a security-sensitive or irreversible decision not explicitly authorized;
* an unresolved core-MVP dependency;
* reminder behavior that cannot be changed safely using the existing scheduling boundary.

Each batch must record:

* batch status;
* goal;
* included work;
* excluded work;
* files inspected;
* files changed;
* architecture used;
* schema changes;
* tests and builds run;
* protected behavior checked;
* remaining risks;
* manual tests required;
* suggested commit message;
* recommended next batch.

Do not start the recommended next batch during the same run.

---

# Product definition

Calendar V1 is a custom LUMA calendar.

It is not Google Calendar embedded inside LUMA.

It must:

* open from the existing Home week strip;
* focus on the exact date selected by the user;
* show finalized LUMA items associated with that date;
* provide a month overview;
* provide a visual daily timeline;
* distinguish date-only items from timed items;
* open the existing item-detail experience;
* allow calm and reversible rescheduling;
* remain local-first;
* integrate with the existing reminder system;
* respect locale-aware date presentation;
* preserve the existing time-format behavior;
* work without Gemini;
* work without an internet connection.

AI may suggest:

* dates;
* times;
* durations;
* reminder offsets;
* scheduling interpretations.

AI must never directly commit important Calendar changes to Room without the existing user-confirmation boundary.

The user remains in control of scheduling changes.

---

# Source-of-truth rule

Do not create an unrelated Calendar database containing copies of tasks, reminders, notes, or other finalized items.

Prefer a Calendar projection derived from existing finalized LUMA items.

A Calendar projection may contain calculated UI-facing values, but it must retain the original LUMA item identifier as its source.

Do not introduce a new generic “calendar event” item type merely to support Calendar V1.

Add a new field, entity, index, or relation only when the existing model cannot safely represent the required behavior.

Any Room migration must be:

* explicitly authorized by the requested batch;
* additive where practical;
* reviewed through `luma-room-data-guardian`;
* covered by migration tests;
* compatible with export and restore behavior;
* safe for existing user data.

Do not modify export or restore formats accidentally as a side effect of Calendar work.

---

# Calendar date and time invariants

Before changing persistence, explicitly document the existing date and time model.

Calendar V1 must follow these invariants:

1. A date-only item is not a timed item at midnight.
2. Date-only state must be represented explicitly.
3. A timed item must have one canonical scheduled-start representation.
4. Reminder target time and notification offset remain separate concepts.
5. Do not create multiple competing scheduled-date or scheduled-time fields.
6. Preserve existing stored-data semantics during migration.
7. Define whether the existing system represents:

   * local civil date and time;
   * an absolute instant;
   * timezone information;
   * or a deliberate combination of these.
8. Document behavior when the device timezone changes.
9. Document daylight-saving behavior where applicable.
10. Add only the database indexes justified by actual day, week, or month queries.
11. Use the project’s existing date-time abstraction.
12. Prefer existing `java.time` usage and Android desugaring when already established.
13. Do not introduce a second date-time library solely for Calendar.

Use the existing time-format preference when one already exists.

If LUMA does not currently provide a selectable 12-hour or 24-hour setting:

* preserve the protected existing 24-hour behavior;
* do not add a new Settings feature solely for Calendar V1.

---

# Calendar inclusion rules

Calendar must show finalized user-facing items only.

Include an item when:

* it is finalized;
* it has a relevant Calendar date or scheduled time;
* its current state allows it to appear under existing LUMA rules.

Do not display as Calendar cards:

* raw captures;
* internal AI-processing records;
* hidden processing artifacts;
* deleted items;
* duplicate source records;
* archived items unless existing product behavior explicitly requires them.

Date-only items appear in an all-day or “Any time” section.

Timed items appear on the daily timeline.

Preserve the application’s current completed-item visibility behavior.

If no explicit completed-item rule exists:

* identify the ambiguity in Batch 0;
* preserve the least disruptive existing behavior;
* do not create a new global completion policy as part of Calendar V1.

---

# Home-screen rule

Home must remain a calm capture surface.

Allowed Calendar-related Home changes:

* weekday names;
* small date numbers;
* selected-day state;
* today state;
* one restrained indicator that a day contains dated items;
* tapping a date to open Calendar.

Forbidden on Home:

* agenda lists;
* Calendar item cards;
* task counts;
* reminder counts;
* productivity summaries;
* month grids;
* large Calendar widgets;
* permanent timelines;
* additional dashboard sections;
* guilt-based overdue indicators.

Calendar must not increase the visual or cognitive density of Home unnecessarily.

---

# UI architecture rules

Calendar must follow the existing LUMA state and architecture conventions.

Use one coherent screen-level state owner for:

* selected date;
* visible month;
* active Calendar view;
* loaded date range;
* Calendar entries;
* loading and empty states;
* restored navigation state;
* scroll position where practical.

Avoid separate composables maintaining conflicting selected-date state.

Keep these outside composables where practical:

* date-range generation;
* month-grid calculations;
* item grouping;
* timeline positioning;
* overlap calculations;
* timezone conversion;
* reminder calculations;
* persistence decisions.

Composables should primarily:

* render immutable UI state;
* emit user actions;
* avoid direct Room access;
* avoid direct notification scheduling;
* avoid hidden mutation.

Reuse the existing ViewModel, repository, coroutine, and state-flow conventions.

Do not perform broad architecture replacement solely for Calendar.

---

# Reminder integrity rule

The existing reminder scheduler must remain the single scheduling boundary.

Calendar UI, composables, and Calendar-specific repositories must not independently schedule Android notifications or alarms.

When scheduling information changes:

1. update the original LUMA item through the existing repository boundary;
2. commit the data change transactionally where required;
3. cancel obsolete reminder work;
4. schedule exactly the required replacement;
5. prevent duplicate alarms or notifications;
6. preserve the original item’s Space, content, links, type, and source information;
7. update Calendar state from the authoritative data source.

Reminder handling must account for:

* notification permission denied;
* exact-alarm capability unavailable or denied;
* rescheduling to a time in the past;
* reminder offsets crossing into the previous day;
* repeated edits;
* item completion;
* item reopening;
* item deletion;
* app process death;
* device reboot;
* package replacement;
* device timezone changes;
* daylight-saving transitions where relevant.

Do not claim any of these behaviors were verified unless evidence exists.

---

# Batch 0: Audit and implementation map

## Goal

Create an evidence-based Calendar V1 implementation plan without changing production behavior.

## Primary workflow

Use `luma-autopilot` with `luma-android-developer` as the likely primary specialist.

Use one supporting specialist only when required for a concrete high-risk finding.

## Inspect

Inspect the current:

* Home week strip;
* navigation architecture;
* Room entities;
* DAOs;
* repositories;
* database versions;
* migrations;
* export and restore model;
* finalized item model;
* item types;
* item statuses;
* completed-item visibility;
* due-date fields;
* scheduled-time fields;
* duration or end-time support;
* reminder target fields;
* notification-offset fields;
* reminder scheduler;
* boot and package-replacement receivers;
* item-detail and edit flows;
* natural-language date interpretation;
* Gemini suggestion and confirmation flow;
* theme system;
* custom-background handling;
* glass and surface components;
* dark-mode handling;
* accessibility conventions;
* testing infrastructure.

Locate and reuse existing approved technical patterns.

Treat canonical LUMA documents as authoritative when repository documentation conflicts.

## Create or update

Create:

* `docs/calendar/CALENDAR_V1_SPEC.md`
* `docs/calendar/CALENDAR_V1_IMPLEMENTATION_PLAN.md`
* `docs/calendar/CALENDAR_V1_PROGRESS.md`

`CALENDAR_V1_SPEC.md` must preserve the normalized product requirements and batch roadmap from this instruction.

`CALENDAR_V1_IMPLEMENTATION_PLAN.md` must document:

* current reusable components;
* current data model;
* data-model gaps;
* date/time semantics;
* completed-item visibility;
* existing time-format behavior;
* proposed Calendar route;
* proposed state owner;
* proposed projection model;
* query strategy;
* reminder integration boundary;
* likely database changes;
* export and restore implications;
* batch-by-batch file impact;
* risks;
* non-goals;
* verification strategy.

## Restrictions

Do not perform broad production implementation.

Do not change Room schemas.

Do not add the Calendar destination yet.

Do not alter Home behavior.

## Validation

* confirm production behavior did not change;
* run documentation or repository checks appropriate to the change;
* run a compile check only when practical and relevant;
* run the strict workplace privacy checker;
* review the final diff.

## Done when

Batch 0 is complete when the implementation plan contains enough evidence to execute Batch 1 without guessing about data representation.

## Suggested commit message

`Plan Calendar V1 implementation`

Stop after Batch 0.

---

# Batch 1: Calendar data contract and date queries

## Goal

Implement the minimum safe domain and persistence support required to query existing finalized LUMA items by date.

## Primary workflow

Use `luma-room-data-guardian` as the primary specialist.

Use `luma-android-developer` as the supporting specialist when needed.

## Requirements

Create a Calendar-facing domain projection such as `CalendarEntry`, following current repository naming conventions.

The projection must reference the original LUMA item.

It may expose:

* source item ID;
* item type;
* Space association;
* title or display text;
* date-only state;
* scheduled start;
* optional end or duration when already supported;
* reminder target;
* reminder offset or notification time where safely derived;
* current item status;
* display metadata already available in the existing model.

Do not duplicate the authoritative item content into a second persistent Calendar record.

Implement efficient queries for:

* one selected day;
* a visible week;
* a visible month;
* items crossing a date boundary only when duration support already exists.

Use half-open date ranges where appropriate to prevent boundary duplication.

Add justified indexes when query evidence shows they are required.

## Migration rules

Avoid a migration when the current model can represent Calendar correctly.

When a schema change is necessary:

* keep it additive;
* preserve existing semantics;
* update the Room version correctly;
* provide migration tests;
* inspect export and restore impact;
* do not silently reinterpret existing timestamps.

## Tests

Cover:

* date-only items;
* timed items;
* day boundaries;
* month boundaries;
* year boundaries;
* timezone-sensitive conversion;
* daylight-saving-sensitive behavior where relevant;
* archived and deleted exclusions;
* raw-capture exclusion;
* internal-processing exclusion;
* completed-item behavior;
* migration from the currently shipped schema;
* export and restore compatibility where affected.

## Validation

Run:

* targeted DAO, repository, domain, and migration tests;
* affected-module compilation;
* complete relevant unit tests;
* debug build;
* strict workplace privacy check.

## Done when

Calendar data can be queried reliably without creating duplicate persistent Calendar items.

## Suggested commit message

`Add Calendar V1 data projection and date queries`

Stop after Batch 1.

---

# Batch 2: Full Calendar shell and navigation

## Goal

Create the dedicated Calendar destination before connecting Home to it.

## Primary workflow

Use `luma-android-developer` as the primary specialist.

Use `luma-compose-ui` as the supporting specialist.

## Requirements

Implement Calendar as a dedicated full-screen destination or full-screen application layer.

Do not place the full Calendar inside Home.

The Calendar shell must include:

* clear back or close navigation;
* visible current month and year;
* selected date;
* calm Today action;
* day or week navigation;
* month navigation;
* coherent active-view state;
* correct system-back behavior;
* edge-to-edge layout consistent with LUMA;
* state restoration after rotation or process recreation where practical;
* light-mode support;
* dark-mode support;
* preset-background support;
* custom-background readability.

Opening behavior:

* accept an optional initial date navigation argument;
* use today when no date is supplied;
* avoid creating or modifying an item merely by opening Calendar;
* prevent duplicate destinations from rapid repeated navigation.

Navigation behavior:

* preserve predictable selected-date behavior;
* preserve a coherent visible month;
* allow the user to return to today;
* avoid gesture conflict with Android system-back behavior.

Do not add external calendar accounts or Android Calendar Provider access.

A minimal placeholder body is acceptable until the month and timeline batches, but the destination, route, state owner, restoration, and navigation contract must be real and tested.

## Tests

Cover:

* route creation;
* initial-date argument;
* invalid or missing date argument;
* selected-date restoration;
* system-back behavior;
* duplicate-navigation prevention where testable.

## Validation

Run:

* targeted navigation and state tests;
* affected-module compilation;
* broader relevant checks once;
* debug build;
* strict workplace privacy check.

## Done when

The Calendar destination exists independently and can reliably receive a selected date.

## Suggested commit message

`Build Calendar V1 shell and date navigation`

Stop after Batch 2.

---

# Batch 3: Home week-strip interaction

## Goal

Connect the existing Home week strip to the established Calendar route without turning Home into a dashboard.

## Primary workflow

Use `luma-compose-ui` as the primary specialist.

Use `luma-android-developer` as the supporting specialist.

## Requirements

Refine the existing Home week strip without redesigning Home.

Show:

* locale-aware weekday name;
* small date number;
* today state;
* selected-date state;
* one restrained item-presence indicator where appropriate.

Respect:

* the configured locale;
* the configured first day of the week when one exists;
* the platform locale convention otherwise;
* custom-background readability;
* existing Home spacing and hierarchy.

Avoid increasing Home height unnecessarily.

## Interaction

* tapping a visible date opens the existing Calendar destination;
* pass the exact tapped date;
* tapping today opens Calendar focused on today;
* opening Calendar must not create or alter an item;
* rapid repeated taps must not create duplicate destinations;
* returning from Calendar must preserve reasonable Home state.

## Motion

Use a restrained Compose transition.

Do not use:

* theatrical animation;
* bouncing;
* excessive blur;
* decorative motion;
* motion that conflicts with reduced-motion accessibility behavior.

Keeping the selected date visually connected during the transition is optional when it can be done without architectural complexity.

## Tests

Cover:

* date selection;
* exact navigation argument;
* today selection;
* repeated taps;
* Home-state restoration;
* semantic labels.

## Validation

Run:

* targeted Compose UI or state tests;
* affected-module compilation;
* broader navigation checks;
* complete relevant tests and debug build;
* strict workplace privacy check.

## Done when

Every visible Home date opens Calendar on the correct date while Home remains visually calm.

## Suggested commit message

`Connect Home week strip to Calendar V1`

Stop after Batch 3.

---

# Batch 4: Custom month overview

## Goal

Implement a calm, custom Jetpack Compose month overview.

## Primary workflow

Use `luma-compose-ui` as the primary specialist.

Use `luma-android-developer` as the supporting specialist.

## Requirements

Provide:

* month and year header;
* locale-aware weekday labels;
* seven-column grid;
* correct first-day-of-week handling;
* muted adjacent-month days where included;
* today state;
* selected-day state;
* restrained indicators for dates containing Calendar items;
* correct four-, five-, and six-row month layouts;
* smooth but restrained month movement;
* fast return to today.

Do not place dense item titles inside month cells.

## Interaction

* tapping a date selects it;
* tapping an adjacent-month date changes the visible month and selects it;
* changing month preserves predictable selection;
* selecting a date reveals or leads to its daily view according to the established shell design.

## Accessibility

Each date cell must provide:

* useful content description;
* selected-state semantics;
* today-state semantics;
* practical touch target;
* information not conveyed by color alone.

Support increased font scaling without destroying the grid.

## Date calculation

Keep month-grid generation outside composables where practical.

Do not duplicate date-generation logic across UI components.

## Tests

Cover:

* leap years;
* February;
* year boundaries;
* months starting on different weekdays;
* four-, five-, and six-row layouts;
* locale behavior;
* first-day-of-week behavior;
* selected-date behavior;
* adjacent-month selection.

## Validation

Run:

* targeted date-generation and Compose tests;
* affected-module compilation;
* broader relevant checks;
* strict workplace privacy check.

## Done when

The month overview is correct, accessible, locale-aware, and connected to the shared Calendar state.

## Suggested commit message

`Add custom Calendar V1 month overview`

Stop after Batch 4.

---

# Batch 5: Daily visual timeline

## Goal

Implement Calendar V1’s primary selected-day experience.

## Primary workflow

Use `luma-compose-ui` as the primary specialist.

Use `luma-android-developer` as the supporting specialist.

## Day-view structure

Provide:

1. selected-date header;
2. date-only or “Any time” section;
3. scrollable timeline for timed items;
4. current-time indicator when viewing today;
5. calm loading and empty states;
6. existing LUMA items positioned by scheduled time.

## Timeline behavior

* preserve the existing time-format behavior;
* support the complete day;
* do not force every hour to occupy excessive space;
* open near the current time when viewing today;
* open near the first relevant item on another day where appropriate;
* remain usable with many items;
* show reminder information subtly when useful;
* avoid corporate meeting-calendar styling;
* avoid red guilt styling for past or incomplete items.

## Overlap behavior

V1 does not require a complex desktop-style collision engine.

Use a deterministic and accessible strategy such as:

* limited side-by-side placement;
* compact vertical stacking;
* grouped overlapping presentation.

All items must remain:

* readable;
* individually tappable;
* semantically accessible.

## Item cards

Cards must:

* remain compact;
* identify the item clearly;
* use existing item-type or Space visual language where useful;
* open existing item detail;
* avoid duplicate raw or internal records.

## Empty states

Use calm, neutral language.

Do not imply:

* failure;
* falling behind;
* low productivity;
* guilt.

## Tests

Cover:

* no items;
* date-only items;
* timed items;
* overlapping items;
* long titles;
* many items;
* completed-item visibility;
* current-time indicator;
* midnight boundaries;
* items near day boundaries;
* large fonts;
* item-detail navigation.

## Validation

Run:

* targeted timeline and state tests;
* affected-module compilation;
* performance-sensitive checks where practical;
* broader relevant checks;
* strict workplace privacy check.

## Done when

The selected day clearly distinguishes date-only and timed items and all entries open the original item.

## Suggested commit message

`Add Calendar V1 daily timeline`

Stop after Batch 5.

---

# Batch 6: Scheduling, rescheduling, and reminder integrity

## Goal

Allow safe Calendar scheduling changes through the existing item and reminder infrastructure.

## Primary workflow

Use `luma-ai-reminder-guardian` as the primary specialist.

Use `luma-room-data-guardian` as the supporting specialist.

## User actions

Allow the user to:

* open the original item;
* assign a date;
* change a date;
* assign a time;
* change a time;
* convert timed to date-only;
* convert date-only to timed;
* remove scheduling without deleting the item;
* move an item to today;
* move an item to tomorrow;
* choose another date;
* choose another time;
* undo a recent rescheduling action when the existing architecture supports safe undo.

Prefer an existing-style reschedule sheet or item-detail action.

Do not create a complex competing Calendar editor.

## Drag-and-drop

Do not implement drag-and-drop by default.

Implement it only when:

* the user explicitly requests it for this batch;
* scrolling remains reliable;
* accessibility remains complete;
* reminder integrity remains testable;
* the implementation does not destabilize the timeline.

## Persistence and reminders

When scheduling changes:

* update the original Room item;
* use the established repository boundary;
* use the existing reminder scheduler as the single scheduling boundary;
* cancel obsolete reminder work;
* create exactly one correct replacement;
* prevent duplicate alarms;
* update Calendar reactively from Room;
* preserve all unrelated item metadata.

## Natural-language flow

Natural-language scheduling remains part of the existing capture pipeline.

Example capture:

`Need to send a package tomorrow at 10 in the morning and remind me an hour earlier.`

Expected interpretation:

* item date: tomorrow;
* item time: 10:00;
* reminder target: one hour earlier;
* raw capture saved before analysis;
* proposed interpretation shown for confirmation where required;
* after confirmation, the same finalized item appears at 10:00;
* no duplicate raw item appears in Calendar or Spaces.

Calendar-provided date context must be a visible or inspectable default in the existing capture pipeline.

Do not secretly rewrite the raw capture text to inject Calendar context.

## Tests

Cover:

* reminder rescheduling;
* obsolete-work cancellation;
* replacement scheduling;
* repeated edits;
* duplicate prevention;
* scheduling removal;
* date-only to timed conversion;
* timed to date-only conversion;
* undo;
* completion;
* reopening;
* deletion;
* reminder offset into previous day;
* scheduling in the past;
* process recreation;
* process death;
* device reboot where test infrastructure supports it;
* package replacement reconciliation;
* timezone changes;
* notification permission denial;
* exact-alarm restriction;
* Gemini unavailable.

## Validation

Run:

* targeted repository, reminder, worker, alarm, and notification tests;
* migration or persistence tests when affected;
* affected-module compilation;
* complete relevant unit tests;
* debug build;
* available instrumentation tests;
* strict workplace privacy check.

Do not claim physical-device notification behavior was verified without actual device evidence.

## Done when

Rescheduling updates the original item and produces no stale or duplicate reminder work.

## Suggested commit message

`Connect Calendar V1 scheduling and reminders`

Stop after Batch 6.

---

# Batch 7: Calendar entry points and continuity

## Goal

Make Calendar behave as an integrated LUMA surface rather than an isolated feature.

## Primary workflow

Use `luma-android-developer` as the primary specialist.

Use the single supporting specialist most relevant to the concrete integration work.

## Required continuity

Verify and implement:

* Home date opens Calendar on that date;
* Calendar item opens existing item detail;
* item edits update Calendar;
* completion updates Calendar according to existing policy;
* reopening updates Calendar;
* archiving removes or hides the item according to existing policy;
* deletion removes the item;
* undo restores the item and its Calendar projection;
* reminder notification opens the correct original item;
* returning from item detail restores Calendar date and view;
* Search opens the original item without creating Calendar records;
* Spaces continue showing finalized items only;
* Life Feed behavior remains unchanged;
* raw captures remain hidden from Calendar.

## Add-for-this-day action

Add a minimal “add for this day” action only when it can reuse the existing capture flow.

Preferred behavior:

* Calendar passes the selected date as capture context;
* the user writes naturally;
* LUMA proposes the interpretation;
* the proposed date remains visible and confirmable;
* confirmation produces one finalized item;
* no second creation form competes with capture.

Do not introduce a separate Calendar event editor unless the current architecture genuinely requires one and the user explicitly authorizes that product change.

## Tests

Cover the relevant cross-screen flows and state restoration.

Use existing test seams rather than duplicating repositories or navigation logic.

## Validation

Run:

* targeted integration and navigation tests;
* affected-module compilation;
* broader relevant regression checks;
* debug build where shared navigation changed;
* strict workplace privacy check.

## Done when

Calendar remains consistent with Home, item detail, Search, Spaces, reminders, completion, deletion, and undo.

## Suggested commit message

`Integrate Calendar V1 with LUMA item flows`

Stop after Batch 7.

---

# Batch 8: Visual polish, accessibility, performance, and final QA

## Goal

Complete Calendar V1 quality verification without redesigning unrelated screens.

## Primary workflow

Use `luma-regression-qa` as the primary specialist.

Use one supporting specialist only for concrete defects discovered during verification.

Do not combine broad unrelated cleanup with final Calendar QA.

## Review

Review:

* light mode;
* dark mode;
* preset backgrounds;
* custom backgrounds;
* glass and haze readability;
* contrast;
* font scaling;
* touch targets;
* screen-reader semantics;
* reduced-motion behavior;
* system-back gestures;
* day and month navigation;
* loading states;
* empty states;
* database query performance;
* recomposition behavior;
* timeline scrolling;
* long titles;
* many items;
* locale-aware formatting;
* first-day-of-week behavior;
* timezone correctness;
* existing time format;
* English readiness;
* Estonian readiness;
* Russian readiness.

Do not hardcode English date strings when Android locale formatting can be used.

Do not claim complete translation unless the relevant string resources actually exist.

## Verify Calendar with

* no scheduled items;
* many items on one day;
* many item indicators in one month;
* overlapping timed items;
* long item titles;
* large font size;
* offline mode;
* Gemini disabled;
* notification permission denied;
* exact-alarm capability unavailable;
* process death;
* activity recreation;
* device restart;
* package replacement;
* timezone change;
* custom backgrounds.

## Automated validation

Run the repository’s relevant checks.

Prefer the project’s existing commands. When appropriate, include:

`./gradlew test`

`./gradlew assembleDebug`

Use Windows equivalents where required.

Run available instrumentation tests relevant to:

* Room migrations;
* restore;
* reminder reconciliation;
* navigation;
* Compose UI.

Run:

`python scripts/codex/check_workplace_privacy.py --strict`

Review the final diff and confirm no unrelated feature expansion occurred.

## Manual Galaxy S25 verification

Perform when an actual device is available. Otherwise document these as unverified manual checks.

Verify:

* every visible Home date;
* exact selected-date opening;
* day navigation;
* week navigation;
* month navigation;
* year boundary;
* return to today;
* date-only section;
* timed timeline;
* overlapping items;
* item detail;
* rescheduling;
* reminder replacement;
* undo;
* activity recreation;
* process recreation;
* device restart;
* dark mode;
* custom backgrounds;
* large fonts;
* TalkBack semantics;
* offline operation;
* Gemini-disabled operation;
* Home visual density.

Record:

* device;
* Android version;
* app variant;
* exact steps;
* observed result.

## Final documentation

Create or update:

* `docs/calendar/CALENDAR_V1_FINAL_REPORT.md`
* `docs/calendar/CALENDAR_V1_PROGRESS.md`

The final report must contain:

* completed batches;
* architecture used;
* database changes;
* migrations;
* Calendar projection behavior;
* main UI behavior;
* reminder behavior;
* tests and builds run;
* device checks actually performed;
* unresolved limitations;
* deferred V2 features;
* remaining manual checks;
* recommended release decision;
* suggested final commit message.

Do not place the entire final report in the chat response.

Use the concise completion report required by `AGENTS.md`.

## Done when

Calendar V1 meets the completion criteria below with current evidence.

## Suggested commit message

`Complete LUMA Calendar V1`

Stop after Batch 8.

---

# Calendar V1 completion criteria

Calendar V1 is complete only when current evidence confirms:

* tapping a Home date opens Calendar on that exact date;
* Home remains calm and contains no agenda;
* Calendar exists as a dedicated destination;
* month overview works;
* daily visual timeline works;
* date-only and timed items are distinct;
* date-only items are not stored as midnight events;
* Calendar uses original finalized LUMA items;
* no duplicate persistent Calendar item system exists;
* item detail opens correctly;
* item edits update Calendar;
* completion and reopening behave consistently;
* archive, delete, and undo behave consistently;
* rescheduling updates Room safely;
* stale reminder work is cancelled;
* duplicate reminders are prevented;
* AI remains suggestion-only;
* raw captures remain private source material;
* internal AI records never appear as Calendar cards;
* locale behavior is correct;
* first-day-of-week behavior is correct;
* existing time-format behavior is preserved;
* custom backgrounds remain readable;
* offline operation works;
* Gemini-disabled behavior works;
* migrations are additive and tested where used;
* export and restore compatibility is preserved;
* automated tests pass;
* debug build passes;
* device-only checks are either evidenced or honestly listed as pending;
* Calendar documentation is complete.

---

# Explicit Calendar V1 non-goals

Do not add in Calendar V1:

* Google Calendar synchronization;
* Outlook synchronization;
* external calendar accounts;
* Android Calendar Provider import;
* backend services;
* Firebase;
* authentication;
* cloud synchronization;
* shared calendars;
* attendee management;
* meeting invitations;
* video-call links;
* complex recurring-event editing;
* routine builder;
* automatic AI time-blocking;
* autonomous schedule changes;
* a new generic Calendar event item type;
* drag-and-drop unless separately authorized in Batch 6;
* desktop synchronization;
* permanent Calendar agenda on Home;
* productivity scoring;
* overdue guilt language;
* unrelated Settings expansion;
* a new date-time library without demonstrated need.

Record these as deferred possibilities rather than quietly expanding the batch.

---

# Completion response

At the end of every batch, return only the repository completion structure:

Result:

Changed:

Validation:

Protected behavior checked:

Workplace privacy checked: yes

Remaining risk or manual check:

Do not paste command logs, full documentation files, workplace-person identifiers, or the next-batch prompt into the completion response.
