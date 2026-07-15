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

## Home

- Greeting/header displays correctly.
- Main capture input remains visually centered and calm.
- Bottom nav is aligned and tappable.
- Mini calendar/week strip displays correctly if present.
- Situation AI entry point still works if present.
- Home has not become a dense dashboard.

## Capture

- Empty input is handled.
- Normal text capture works.
- Task-like capture works.
- Reminder-like capture works.
- AI unavailable/local fallback behavior works if applicable.

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

## Calendar/date UI

- Mini calendar displays weekday/date correctly.
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
- Items are grouped correctly if grouping exists.
- Empty space state is clear.
- Life Feed shows finalized notes, tasks, and reminders only; raw captures and "Processed" records do not appear as Space cards.
- Note feed subtitles do not repeat the note title.
- Active and done tasks have distinct labels/icons.

## Review

- Review opens.
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
- It summarizes/suggests without silently changing data.
- User confirmation is required for important actions.
- It handles no-data/empty states.

## Ask LUMA

- Ask LUMA opens if present.
- It uses local context only where intended.
- It does not hallucinate saved data as fact.

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
- Category rows open the correct Settings submenus.
- Submenu back returns to the Settings category index.
- Appearance menu rows open Profile, Colors, Background, and Glass controls, and the in-screen back returns to the Appearance menu.
- Theme settings work.
- Appearance accent and text color choices persist and update app-wide Material colors in light and dark mode.
- Custom background image choose/remove works and presets remain available as fallback.
- Choosing Custom hides preset tiles; choosing Preset clears the custom image and shows preset tiles again.
- Preset-to-custom and custom-to-preset transitions update the rendered background and all glass surfaces atomically; no stale preset color remains inside glass.
- Custom-background glass preserves readable contrast without masking the image's colors or becoming an opaque theme-colored surface.
- Settings, Spaces, Review, Search, and glass sheets still scroll smoothly after background/glass changes.
- Language/time-format settings work if present.
- Changes persist if designed to persist.
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
