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

## Pattern: Calm Settings menu list

Use when:
A Settings index or submenu presents peer destinations with a current value or short explanation.

Rules:
- Use one coherent rounded SoftGlass group rather than a separate card for every row.
- Give rows a consistent icon well, primary label, short secondary line, and navigation cue.
- Keep secondary or reset actions outside the group.
- Open a focused submenu when controls or explanatory copy would make the index dense.
- Reuse shared typography, spacing, shape, color, surface, and motion roles.
- Extract a shared Settings component before repeating a menu implementation inside Settings.

Avoid:
- mixed-purpose groups
- chevrons on non-navigation controls
- long helper copy in index rows
- shrinking text, spacing, or touch targets to fit more controls
- duplicating an existing destination or control nearby

Related areas:
- Settings
- Appearance
- Settings category indexes
- visual hierarchy
- accessibility

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

## Pattern: Select glass by behavioral role

Use when:
A new translucent surface is added or an existing route is visually aligned with the app glass system.

Rules:
- Choose LiveGlass, SoftGlass, or ModalSurface from the surface's behavior before composing it.
- Reserve LiveGlass for a bounded, fixed, high-value region backed by the shell ambient-background source.
- Use SoftGlass for repeated, scrolling, resizing, list-heavy, and fallback content.
- Select the route rendering policy once; do not switch material during scrolling.
- Keep modal scrim, shape, elevation, and padding in the app-owned modal composition.
- Process custom-image background blur when inputs change and cache the bitmap.

Avoid:
- treating translucency as proof that Haze is required
- route-owned full-screen Haze sources
- animated blur radius, progressive blur, or active effect resizing
- per-frame blur for a stable background image

Related areas:
- Compose design system
- app shell
- Home navigation
- list-heavy routes
- Situation AI
- Appearance

## Pattern: Calm modal briefing hierarchy

Use when:
A modal presents several short explanatory sections plus a persistent composer or action area.

Rules:
- Keep primary briefing prose on the primary content color and use the shared comfortable body style.
- Group peer sections into one calm reading surface with clear section spacing or subtle dividers.
- Use typography-aligned bullets or hanging indents so wrapped lines stay aligned at larger font scales.
- Reserve secondary content color for subtitles, provenance, and compact metadata.
- Visually separate a fixed composer footer without moving it into the scrolling body.

Avoid:
- styling primary prose like secondary metadata
- one equally prominent card for every short section
- fixed-position dot bullets that drift away from the text baseline
- letting a footer heading compete with the briefing hierarchy

Related areas:
- Situation AI
- Ask LUMA
- modal summaries
- accessibility and font scaling

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

## Pattern: Anchor compact week context to the visible week

Use when:
Adding navigation or labels to the Home weekday strip.

Rules:
- Hoist and restore the visible-week anchor independently from the selected date.
- Move in exact seven-day increments and refresh date-backed indicators for the newly visible range.
- Derive a split-month week's label from its middle day so the label is stable across date taps.
- Keep the label compact, localized, and independent from detailed Calendar controls.

Avoid:
- deriving the label from the last tapped day
- showing stale item indicators after the week changes
- turning Home into a full calendar surface

Related areas:
- Home
- mini calendar
- Calendar navigation
- accessibility

## Pattern: Introduce design tokens without visual churn

Use when:
A repeated spacing rhythm or surface shape needs a shared design-system role.

Rules:
- Define a small documented scale before migrating call sites.
- Replace only repeated values whose rendered dimensions and semantic role match the token.
- Keep component-specific measurements local.
- Keep circles and pills as explicit geometry exceptions.
- Add focused value tests so later token edits are intentional and reviewable.

Avoid:
- mechanical whole-app replacement
- changing dimensions while claiming a token-only refactor
- forcing circles, pills, or asymmetric component geometry into generic surface roles

Related areas:
- Compose theme
- spacing
- surface shapes
- visual regression prevention

## Pattern: Calm motion from shared roles

Use when:
Adding motion across multiple Compose screens or interactive components.

Rules:
- Define a small ordered duration and scale vocabulary instead of screen-local magic numbers.
- Let Android's animator duration scale remain authoritative so Remove animations is respected.
- Animate navigation direction, meaningful content changes, and direct interaction feedback.
- Use the same interaction source for press state and click indication so feedback matches the gesture.
- Animate stable-key lazy items with restrained fade and placement timing.
- Keep motion finite and verify scrolling with preset and custom backgrounds.
- For app-wide component haptics, observe pointer changes at shared screen and modal roots without
  consuming them, require an enabled descendant to accept the completed press, use one light
  platform feedback role, and let the system haptic setting win.
- Classify an accepted press with platform touch-slop and long-press thresholds so blank areas,
  disabled controls, scrolling, dragging, long presses, and multi-touch gestures remain silent.

Avoid:
- looping decoration, large parallax, or repeated bounce
- animating every recomposition or unchanged content
- replacing Material control feedback with duplicate effects
- motion that delays confirmation or important actions
- stacking screen-local confirmation vibrations on top of shared component feedback
- consuming parent pointer events to add haptics, which can cancel child clicks or scrolling

Related areas:
- Compose navigation
- shared components
- Spaces
- Review
- Search
- Settings
- accessibility
- appearance performance

## Pattern: Fixed chrome over dissolving scroll content

Use when:
A top-level screen needs a persistent heading or action row while its content scrolls beneath fixed top or bottom chrome.

Rules:
- Keep the persistent header outside the lazy list so its title and actions do not scroll away.
- Measure the rendered header and include status-bar inset plus breathing room in the list's initial top clearance, with a safe minimum for normal font scale.
- Fade scroll content with an alpha mask so the existing preset or custom background remains the visible treatment instead of painting a second decorative gradient.
- Keep stable lazy-list keys and enough bottom content padding for the final item to scroll fully above floating navigation.
- Skip offscreen compositing when neither edge needs a fade.
- Verify light, dark, preset, and custom backgrounds plus increased font scale and scroll performance on a device.

Avoid:
- placing the heading inside the scrolling item stream
- hardcoding clearance that overlaps wrapped header text
- adding an opaque gradient that hides the selected background
- fading the floating controls together with the content behind them

Related areas:
- Spaces
- Review
- shared navigation
- Compose lazy lists
- accessibility
- appearance performance

## Pattern: Convert table-backed item types as one confirmed identity move

Use when:
An existing Note, Task, or Reminder changes user-facing type while each type is stored in a separate Room table.

Rules:
- Keep the same item identifier when the destination namespace permits it.
- Preflight destination identity conflicts and leave the source untouched on conflict.
- Insert the destination row and delete the source row in one Room transaction.
- Preserve title, notes, Space, creation time, life-state meaning, and compatible schedule metadata.
- Reconcile reminder alarms and work only after the database transaction commits.
- Require a complete date and time before committing a conversion to Reminder.

Avoid:
- insert-then-delete repository sequences without a transaction
- scheduling reminder work before the converted row commits
- overwriting an unrelated destination row with the same identifier
- leaving both source and destination rows visible

Related areas:
- Item Details
- Room
- reminders
- navigation
- export/restore
