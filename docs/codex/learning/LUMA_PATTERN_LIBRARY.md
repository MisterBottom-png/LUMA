# LUMA Pattern Library

Reusable implementation and UX patterns discovered during work.

## Format

```md
## Pattern: <name>

Use when:
<when to apply>

Rules:
- ...

Avoid:
- ...

Related areas:
- ...
```

## Pattern: Compact Home entry point → deeper secondary surface

Use when:
A feature belongs on Home but would become too dense if fully shown there.

Rules:
- Keep Home element compact.
- Use tap/click to open full secondary surface.
- Make back/close behavior obvious.
- Preserve selected state only where useful.
- Check dark/light mode.

Avoid:
- dense permanent Home cards
- dashboard creep
- hidden navigation dead ends

Related areas:
- Home
- mini calendar
- Situation AI
- Review

## Pattern: Missing-information prompt before action

Use when:
AI or parser detects an action/reminder/task but lacks required details.

Rules:
- Ask for the missing detail.
- Show a clear proposed interpretation.
- Let user confirm or dismiss.
- Do not silently save important changes.

Avoid:
- guessing dates/times when confidence is low
- silently scheduling reminders
- guilt language

Related areas:
- capture
- reminders
- AI analyzer
- Review
- Situation AI

## Pattern: Settings category index to focused submenus

Use when:
Settings begins to grow beyond a short list of controls.

Rules:
- Keep the default Settings screen as a compact category index.
- Put dense controls inside focused submenus.
- Keep settings state, repositories, and persistence behavior unchanged during UI-only restructuring.
- Provide an obvious in-screen back path from each submenu.
- Preserve bottom-nav clearance and IME handling for long settings forms.

Avoid:
- turning Settings into one long mixed-control scroll
- changing DataStore/Room shape during a layout-only categorization pass
- hiding risky controls without a clear category entry

Related areas:
- Settings
- Appearance
- AI settings
- Local data

## Pattern: Local user media as appearance settings

Use when:
The user wants to personalize LUMA with an image or media asset that affects presentation only.

Rules:
- Keep the media local-first.
- Store only the local URI or settings reference needed to reopen it.
- Provide an obvious remove/reset path.
- Preserve existing presets and visual adjustment controls as fallback.
- Render behind existing calm UI surfaces rather than adding new Home content.

Avoid:
- cloud upload or sync
- database schema changes for presentation-only media
- making Home denser to expose personalization controls

Related areas:
- Settings
- Appearance
- background rendering
- local-first behavior

## Pattern: Appearance effects stay scroll-light

Use when:
Changing backgrounds, glass, blur, custom images, or other app-wide appearance effects.

Rules:
- Treat background blur, Haze blur, noise, shadows, and decoded image size as shared scroll-performance costs.
- Cap custom background decode size before rendering it behind every screen.
- Keep presets and custom media mutually clear in Settings so inactive options are not rendered unnecessarily.
- Keep one stable Haze source across background modes and place the complete rendered background treatment inside it.
- Derive glass mode from the background actually rendered, not from a URI or requested mode that may still be loading.
- Validate changes by scrolling Settings, Spaces, Review, Search, and any sheet/dialog that uses glass.

Avoid:
- high full-screen blur multipliers
- high Haze blur/noise on frequently visible surfaces
- decoding very large custom images for app-wide backgrounds
- rendering inactive preset grids while a custom background is active

Related areas:
- Settings
- Appearance
- background rendering
- glass surfaces
- menu scrolling

## Pattern: App-wide appearance palettes

Use when:
The user wants custom text colors, accent colors, or other app-wide visual personalization.

Rules:
- Prefer named palette choices over arbitrary color input unless the user explicitly asks for free-form hex.
- Persist presentation-only choices in local settings/DataStore, not Room.
- Apply app-wide color choices through the Material theme so existing screens inherit them consistently.
- Keep contrast-safe light and dark variants for each palette choice.
- Group dense appearance controls into focused sections with a compact preview.

Avoid:
- storing visual-only preferences in life-inbox entities
- adding cloud sync or accounts for appearance preferences
- letting arbitrary colors create unreadable text or buttons by default

Related areas:
- Settings
- Appearance
- theme
- DataStore settings

## Pattern: Centralized app time formatting

Use when:
Adding or changing visible time display, reminder/task date-time pickers, or local AI summaries that mention times.

Rules:
- Persist the time-format preference in local settings/DataStore, not Room.
- Offer Device default plus explicit 12-hour and 24-hour modes.
- Resolve Device default through Android's current 12/24-hour setting.
- Route visible labels and TimePickerDialog 24-hour flags through one shared formatter or resolved boolean.
- Keep scheduling timestamps and timezone conversion behavior unchanged unless explicitly requested.

Avoid:
- hardcoded `h:mm a` strings in UI screens
- changing notification scheduling while only changing display preferences
- storing presentation-only time preferences on life-inbox entities

Related areas:
- Settings
- reminders
- Review
- Spaces
- Capture suggestions
- Situation AI

## Pattern: Validate, summarize, replace, then reconcile

Use when:
Restoring a local export that preserves identifiers but has no merge-conflict metadata.

Rules:
- Parse the entire file and validate version, required fields, identifiers, and relationships before any write.
- Compare current data again at confirmation so a stale summary cannot overwrite newer changes.
- Perform the supported data replacement in one database transaction.
- Reconcile external side effects such as reminder work only after the transaction commits.
- Treat device-specific work identifiers as non-portable.

Avoid:
- merge-by-guessing
- deleting before validation or confirmation
- scheduling or cancellation before the database commit
- restoring device-specific scheduler identifiers

Related areas:
- export and restore
- Room
- reminders
- Settings

## Pattern: Restore schedule fields on the original row

Use when:
Adding date-only or timed scheduling, rescheduling, removal, and short-lived Undo for an existing finalized item.

Rules:
- Represent date-only and timed schedules as mutually exclusive states.
- Update the original Room row through its repository; never insert a Calendar-owned copy.
- Preserve every unrelated field and change only schedule fields plus the normal update timestamp.
- Keep reminder notification work behind the established reminder scheduler boundary.
- Make Undo single-use and restore only the prior schedule fields onto the latest row so later metadata edits survive.
- Let the Calendar projection react to Room instead of maintaining a second mutable Calendar model.

Avoid:
- storing both date-only and timed values on one item
- copying an old full-row snapshot over later edits during Undo
- creating a second Calendar editor or scheduler
- treating a task date as an implicit notification

Related areas:
- Calendar
- item detail
- Room repositories
- reminders
- undo
