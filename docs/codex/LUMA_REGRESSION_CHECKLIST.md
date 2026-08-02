# LUMA Regression Checklist

Use this after meaningful changes.

Do not run the whole checklist for tiny visual fixes. Use the affected sections. Shocking concept: proportionality.

## Global checks

- App builds.
- App launches.
- No obvious crash on first launch.
- Dark mode still works.
- Light mode still works.
- Navigation back behavior works.
- Empty states still look intentional.
- Error states do not expose raw crashes.

## Settings menu design language and hierarchy

- These checks apply only to Settings menus; other screens retain their role-specific layouts.
- The Settings menu has one obvious title and, only when useful, one short purpose subtitle.
- Peer Settings destinations use one coherent grouped list instead of a stack of unrelated cards.
- Grouped rows keep a consistent icon well, primary label, short secondary line, divider treatment, and trailing affordance.
- Chevrons indicate navigation only; selection, switches, and destructive actions retain their own affordances.
- Secondary and reset actions remain visually separate from the primary navigation group.
- New Settings UI reuses existing typography, spacing, shape, color, surface, and motion roles or records why a new role is required.
- Dense controls use progressive disclosure rather than smaller text, tighter touch targets, or long explanations in the index.
- No nearby Settings control, label, destination, or private menu pattern was duplicated by the addition.
- Increased font scale preserves hierarchy, row reachability, and readable secondary text.

## Motion and interaction

- Top-level navigation moves consistently with destination order; Back uses the reverse direction.
- Detail destinations enter and return without changing navigation or saved-state behavior.
- Press feedback appears only on interactive surfaces and does not shrink touch targets.
- A short single-finger press on an enabled clickable or selectable component produces at most one
  light haptic across normal screens, floating navigation, capture confirmation, and Situation AI.
- Blank areas, disabled controls, scrolls, drags, long presses, and multi-touch gestures do not
  trigger shared component haptics; disabling Android touch feedback keeps the app silent.
- Search, Spaces, and Review list changes preserve stable keys and animate without item duplication.
- Fixed screen headers remain outside their lazy lists; scrolling content clears the measured header at rest, dissolves into the existing background treatment beneath fixed chrome, and remains fully reachable above the floating bottom navigation at the end of the list.
- Fixed-chrome edge fades stay on the smallest affected scrolling layer and do not force the complete navigation host through offscreen compositing.
- Settings and Spaces internal pane transitions preserve Back behavior and scroll reachability.
- Sheets, dialogs, keyboard transitions, and bottom-navigation visibility remain reachable and calm.
- Android Remove animations or a zero animator duration scale produces immediate state changes.
- Preset and custom backgrounds remain smooth while navigating and scrolling animated surfaces.
- The app shell owns one stable background-only Haze source; route content and lazy lists are never Haze sources.
- LiveGlass is confined to bounded fixed regions and resolves to SoftGlass under the route, platform, reduced-effects, or test policy chosen before rendering.
- No route changes glass material in response to ordinary list scrolling.

## Home

- Greeting/header displays correctly.
- Main capture input remains visually centered and calm.
- Bottom nav is aligned and tappable.
- Bottom navigation uses subtle SoftGlass so low surface-opacity settings reveal the background without an opaque Haze backing layer.
- Mini calendar/week strip displays correctly if present.
- The Home month and locale-aware week number share one baseline at opposite edges, 14 dp above the weekday names.
- Swiping the Home week strip changes exactly one week, updates the month label and item dots, and leaves date selection unchanged.
- A Home week spanning two months labels the month containing its middle day; the label does not change when a date in that week is tapped.
- Situation AI entry point still works if present.
- Home has not become a dense dashboard.

## Capture

- Empty input is handled.
- Normal text capture works.
- Task-like capture works.
- Reminder-like capture works.
- AI unavailable/local fallback behavior works if applicable.

## Brain Dump

- Every non-blank source line appears exactly once and in source order, including short and repeated lines.
- Gemini output is accepted only when it covers every immutable source fragment exactly once; malformed, missing, duplicate, or unknown source identifiers fall back locally.
- Pending progress survives sheet dismissal, navigation, process recreation, and export/restore.
- Each Brain Dump item can be saved, kept in Inbox, or skipped at most once, and a failed database write leaves it pending.
- Note, task, and reminder actions preserve the selected type, Space, calendar context, and interpreted reminder time.
- Review and Capture details offer Resume Brain Dump while pending work exists; dismiss and archive remove that pending work without creating finalized items.
- Long lists and task/reminder setup remain reachable above navigation bars and the IME.

## Reminders and date/time

- "tomorrow at 10" style input is handled if supported.
- Compact local times such as `1600`, `0830`, and contextual `800` resolve to the intended 24-hour value; invalid values such as `2460` require review.
- The displayed interpretation phrase and structured reminder timestamp represent the same local date and time.
- The accepted timestamp survives confirmation/state restoration and is not reparsed or replaced by a current-time fallback in the editor.
- Reminder offset like "one hour earlier" is handled if supported.
- 12/24 hour format is respected if setting exists.
- Timezone assumptions are not hardcoded incorrectly.
- A non-UTC local-time-to-instant round trip preserves the displayed hour.
- Notifications are scheduled only after user confirmation where required.

## Item Details

- Note, Task, and Reminder details open in read-only mode with no permanent fields or Save button.
- Edit exposes title and notes with Save and Cancel; rotation preserves the active draft where supported.
- Cancel discards draft changes and Save updates exactly one existing row.
- Type, Schedule, Life state, and Space open focused selectors in that order with clear selected states.
- Task-to-Reminder without a complete date and time changes nothing when scheduling is cancelled.
- Type conversion preserves the item identifier, content, Space, creation time, and compatible metadata.
- Reminder-to-Task and Reminder-to-Note cancel obsolete alarm and worker state after the row move commits.
- Archive and Delete remain available from More; Delete keeps confirmation and the contextual bottom action remains visible.
- Floating bottom navigation is hidden on both normal and reminder Item Details routes and returns after Back.

## Calendar/date UI

- Mini calendar displays weekday/date correctly.
- Day timeline places timed entries and the live current-time indicator by local minute-of-day rather than list order, and opens near the relevant time.
- Horizontal Calendar swipes move exactly one day in Day view or one month in Month view without interfering with vertical timeline scrolling or item taps.
- Timeline hour labels and the live current-time label follow the resolved Device/12-hour/24-hour setting.
- Tapping mini calendar opens full calendar if feature exists.
- Selected date state works.
- Close/back behavior returns safely.
- Date-linked local items/reminders display only if supported by data.
- Date-only and timed edits update the original note or task row without creating duplicates.
- Timed-to-date-only, date-only-to-timed, schedule removal, and single-use Undo preserve unrelated item metadata.
- Reminder target edits preserve notification offset, and offset edits preserve target time.
- Reminder rescheduling cancels obsolete work and creates one replacement; completion, disabling, and deletion cancel work.
- Calendar updates reactively after scheduling changes and continues to exclude raw and internal records.
- Add for this day reuses Home capture, displays a removable date context, and never rewrites raw capture text.
- Confirming Calendar-context capture schedules the single finalized item on the selected date without creating a Calendar-owned copy.
- Returning from original-item detail restores the same Calendar date and Day/Month view.
- No external calendar sync is added unless requested.

## Spaces

- Spaces list loads.
- Space detail opens.
- Populated Spaces overview and Life Feed scroll smoothly in debug and release builds with preset and custom backgrounds, including maximum blur/glass settings.
- Space list partitions, item counts, date labels, and feed sorting update when source contents or time format change without being rebuilt inside lazy item composition.
- Spaces overview and Life Feed retain stable lazy-list keys.
- Items are grouped correctly if grouping exists.
- Empty space state is clear.
- Life Feed shows finalized notes, tasks, and reminders only; raw captures and "Processed" records do not appear as Space cards.
- Note feed subtitles do not repeat the note title.
- Active and done tasks have distinct labels/icons.

## Review

- Review opens.
- Device-local time shows Morning before 12:00, passive Midday from 12:00 until 17:00, and Evening from 17:00 onward without mode tabs, chips, or a manual selector.
- Midday contains no tasks, suggestions, prompts, review questions, checklists, required actions, Weekly Review, or open-loop sorting.
- Weekend Morning and Evening add one horizontally swipeable Weekly Review with Look back, Loose ends, and Look ahead pages; leaving any page requires no completion.
- Sort open loops remains a secondary action below Morning, Evening, and weekend Weekly content and preserves the established review mutations.
- Items are understandable.
- Actions are clear.
- Item completion wording uses "Done" language; review workflow state uses "Review ..." language.
- "Completed today" and standalone item-card "Complete" labels do not appear in Review.
- Missing-information prompts work if present.
- AI suggestions are optional and non-bossy.
- Capture confirmation creates one finalized item; dismiss and archive create none.
- Processed capture records never appear under `Done today` or as finalized Review items.

## Situation AI

- Situation AI opens.
- The briefing surfaces at most a few active Room-backed items, never database counts, and each item states its deterministic reason such as overdue, due soon, unresolved, stale, or recently captured.
- Completed, archived, processed, and otherwise inactive rows do not appear in the active briefing.
- The briefing refreshes when repository flows emit and as local-time due/stale boundaries change.
- Primary briefing and answer prose remains readable as primary content; subtitles, provenance, and compact labels remain visually secondary.
- Briefing bullets keep a hanging alignment when lines wrap at 1.0x, 1.3x, 1.5x, and 2.0x font scale.
- Header and Close remain fixed above one independently scrolling body.
- Ask LUMA composer remains fixed below the body and visible above navigation-bar and IME insets.
- Opening the IME does not move the Situation AI sheet into a partial-expanded anchor.
- Long answers and source evidence remain reachable in the body with the keyboard closed and open.
- Android Back closes the keyboard before dismissing the sheet according to platform behavior, then restores focus to the Situation AI entry control.
- It summarizes/suggests without silently changing data.
- User confirmation is required for important actions.
- It handles no-data/empty states.
- Opening from every available route uses the same app-owned scrim, modal shape, padding, insets, and SoftGlass surface without a route-owned Haze dependency.

## Ask LUMA

- Ask LUMA opens if present.
- It uses local context only where intended.
- It does not hallucinate saved data as fact.
- Common local queries such as overdue, due soon, stuck, recent, item type, and completed return deterministic source-linked results offline.
- Every returned source opens the existing detail route, and a displayed answer is invalidated when its Room snapshot or relevant local-time bucket changes.

## AI learning memory

- Room schema changes include an explicit migration and exported schema.
- Learning memory remains local and does not store API keys or raw Gemini JSON.
- Learned rules can be represented as enabled/disabled data for future Settings controls.
- Suggestion/correction history does not delete or replace raw captures or existing items.
- Suggestion/correction history writes do not block confirmed capture actions if learning storage fails.
- Gemini prompt context remains bounded and does not send unbounded learning history.
- Gemini learning profiles include only enabled memory and aggregate repeated corrections without raw history dumps.

## Settings

- Settings opens.
- Categories display correctly if categorized.
- Settings category indexes retain the Appearance-style grouped hierarchy: focused heading, coherent rounded group, consistent rows, and separate secondary actions.
- Category rows open the correct Settings submenus.
- Submenu back returns to the Settings category index.
- Appearance menu rows open Profile, Colors, Background, and Transparency controls, and the in-screen back returns to the Appearance menu.
- System menu rows open Time, AI, and Local data controls, and the in-screen back returns to the System menu.
- Appearance subsections show one focused header, hide the Appearance menu and floating bottom navigation, and restore both the index and floating navigation after in-screen or Android Back.
- Appearance color choices remain a balanced two-column layout with a visible checkmark, selected semantics, and a contrast-safe selected surface in light and dark themes.
- Theme settings work.
- Appearance accent and text color choices persist and update app-wide Material colors in light and dark mode.
- Custom background image choose/remove works and presets remain available as fallback.
- Choosing Custom hides preset tiles; choosing Preset clears the custom image and shows preset tiles again.
- Preset-to-custom and custom-to-preset transitions update the rendered background and all glass surfaces atomically; no stale preset color remains inside glass.
- Custom-background glass preserves readable contrast without masking the image's colors or becoming an opaque theme-colored surface.
- Settings, Spaces, Review, Search, and glass sheets still scroll smoothly after background/glass changes.
- Language/time-format settings work if present.
- Changes persist if designed to persist.
- Transparency shows a bounded production LiveGlass/SoftGlass preview beside its controls without enabling Haze across Settings.
- Image blur is enabled only for user-selected images, spans a visibly useful cached range, retains the prior processed image while a new blur step is prepared, and reuses cached results while rendering.
- Background dim visibly changes preset and custom backgrounds in light and dark themes without changing surface opacity.
- Surface opacity produces a clear transparent-to-solid change across cards, fixed controls, and app-owned sheets while retaining readable content and translucency at both ends.
- Reset appearance restores visual defaults without changing profile, time format, AI, or life-inbox data.
- Profileable frame-timing benchmarks cover Spaces, Review, Settings, Situation AI, Home-to-Spaces navigation, and the Appearance preview before release acceptance.
- Restore validates the complete export and shows replacement counts before confirmation.
- Cancelling file selection or confirmation changes no data.
- Restore replacement is transactional, repeat-safe, and preserves exported identifiers and relationships.
- Restored reminder work is reconciled only after the database transaction commits.

## Learning updates

After medium/large tasks:

- Useful lessons added to learning files.
- Regression checklist updated if new risk discovered.
- Bug graveyard updated if a bug was fixed.
- Permanent-rule proposals added to promotion queue, not directly to AGENTS.md.

## Agent stack / project maintenance

- Active agent stack paths are backed up before cleanup or upgrade work.
- `AGENTS.md`, `.agents/`, `.codex/`, `docs/codex/`, and `scripts/codex/` remain in their installed locations unless the user explicitly requests a migration.
- Old zips, extracted packages, duplicate root-level agent folders, logs, and temporary files are quarantined rather than merged into the active stack.
- V3/V4 installer work does not treat quarantined package folders as the source of truth.
