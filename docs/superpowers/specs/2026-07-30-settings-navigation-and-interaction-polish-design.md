# Settings navigation and interaction polish design

## Goal

Redesign LUMA Settings around a calm four-category hierarchy while preserving every existing setting and protected behavior. The result should make settings easier to find, keep dense or sensitive controls focused, improve accessibility and responsive behavior, and remove interaction defects caused by the current in-screen state machine.

Success means a user can locate any existing setting from the Settings hub, understand its current state, change it without stale or lost input, and return predictably without encountering a dense dashboard.

## Product direction

- Preserve categorized Settings.
- Prefer progressive disclosure over dense forms.
- Keep language calm, direct, and non-technical where possible.
- Show one concise status per navigation row.
- Keep local-first behavior, Gemini consent, and user confirmation boundaries unchanged.
- Reuse the established LUMA Settings visual language, Material roles, glass surfaces, spacing, shapes, and motion.

## Information architecture

Settings begins with one grouped hub containing four destinations:

1. **Personal**
   - Profile name
   - Application language
   - Time format
2. **Appearance**
   - Theme
   - Accent and text colors
   - Background
   - Surface transparency and background effects
3. **AI & privacy**
   - AI processing mode
   - Gemini setup
   - Gemini feature access
   - Local learning
4. **Data & app**
   - Local data export and restore
   - First-time guide replay

Each hub row contains a leading icon, category name, one short current-state summary, and a trailing navigation cue. The hub contains no settings controls, warnings, destructive actions, or additional cards.

## Navigation model

Settings uses real Navigation Compose destinations rather than a screen-local hierarchy state machine:

```text
Settings hub
└── Category
    └── Focused detail, only when controls are dense or sensitive
```

Navigation depth is limited to three levels:

- Detail Back returns to its category.
- Category Back returns to the Settings hub.
- Back from the Settings hub pops Settings and returns to the prior app destination; it does not synthesize a Home redirect.

The floating bottom navigation appears on the Settings hub and is hidden on category and detail destinations. Each destination owns its scroll state and saved UI state. Returning to the same destination may restore its prior position; opening a different destination always begins at the top.

Navigation transitions derive their direction from the back stack. Forward navigation enters from the end edge, Back reverses the transition, and Android reduced-motion behavior remains authoritative.

## Screen composition

All Settings destinations use a shared, limited component vocabulary:

- Fixed Back header with one title and an optional short subtitle.
- One rounded `SoftGlassSurface` for each related group.
- Full-width navigation row with icon, label, short status, and chevron.
- Full-row labeled toggle.
- Accessible single-choice control.
- Adaptive choice grid.
- Secondary action area outside the primary group.
- Dismissible operation-status message.

The visual reading order must remain understandable without relying on color. Navigation rows use chevrons only for navigation. Toggles, selections, and destructive actions keep their own affordances.

### Personal

The Personal page presents its three simple settings directly:

- Profile name uses a local editing draft and commits on IME Done or focus loss.
- Language uses an accessible single-choice group.
- Time format uses an accessible single-choice group and retains Device, 12-hour, and 24-hour behavior.

### Appearance

The Appearance page presents theme and color choices directly. Background and Surfaces are navigation rows leading to focused detail pages.

Color options use an adaptive grid rather than fixed two-column, fixed-height cards. Labels may wrap and remain readable at increased font sizes. Every option exposes its label, radio role, and selected state.

The Background page retains mutually clear Preset and Custom modes, custom image choose/change/remove behavior, and preset fallback behavior. Removing a custom background offers Undo.

The Surfaces page retains the production surface preview, image blur, background dim, adaptive/manual dimming, and surface-opacity choices. Image blur remains unavailable without a custom image and explains why it is unavailable.

### AI & privacy

The AI & privacy category presents AI mode directly. Selecting Gemini continues to require the current consent confirmation. Selecting Local only clears Gemini feature permissions and does not affect local data.

Three focused destinations hold the denser controls:

- **Gemini setup:** API key, connection test, and model identifiers.
- **Feature access:** per-feature Gemini permissions.
- **Local learning:** learning enablement, Gemini sharing, learned rules, and clear-learning controls.

The existing cloud-AI privacy explanation remains visible where the user configures or grants Gemini access. Feature toggles are full-row labeled controls rather than isolated switches.

Learned rules use vertically arranged cards. Rule content appears first, followed by a labeled enabled toggle and actions. Rule removal offers Undo. Editing uses a focused dialog with non-blank validation.

### Data & app

The Data & app category contains:

- A Local data row leading to export and restore.
- A First-time guide row that replays the existing tutorial.

The Local data page keeps export and restore visually distinct. Export retains the unencrypted-file warning. Restore validates before confirmation, displays replacement counts, and cannot mutate existing data until the user confirms.

## State ownership and persistence

A Settings-facing state owner exposes intent-specific operations such as:

- `setTheme`
- `setLanguage`
- `setTimeFormat`
- `setAccentColor`
- `setBackground`
- `setAiMode`
- `setGeminiFeatureEnabled`

UI code does not submit complete `AppSettings` snapshots for individual changes. Repository mutations transform the latest persisted settings atomically so rapid sequential changes cannot overwrite unrelated fields.

Each editable text field owns a local draft:

- Profile name commits on IME Done or focus loss.
- API-key input remains present until storage succeeds.
- Model identifiers use explicit Save and Restore defaults actions.
- Blank model identifiers are rejected in the UI rather than silently replaced during typing.

No Room entity, database version, migration, export format, or restore contract changes are part of this work.

## Operation states and feedback

Longer operations expose specific progress states and disable only conflicting controls.

Transient success and error feedback is modeled as consumable or dismissible UI state:

- Starting a related operation clears an obsolete prior message.
- A message cannot persist into an unrelated operation.
- API-key save failure preserves the draft for retry.
- Connection results distinguish testing, success, and failure.
- Restore results can be dismissed and are replaced by later restore state.

The app must not display raw exceptions or provider responses as Settings messages.

## Risk-tiered safeguards

### Immediate and reversible

- Theme
- Accent and text colors
- Time format
- Language
- AI feature toggles
- Blur
- Dimming
- Surface opacity

### Undo

- Learned-rule removal
- Custom-background removal

### Confirmation

- API-key removal
- Appearance reset
- Clearing all local learning data
- Replacing local data from an export

Restore confirmation retains its validated replacement-count summary. Appearance reset continues to affect appearance settings only.

## Accessibility and responsive behavior

- Full-row controls have at least a practical 48 dp touch target.
- Toggle labels and toggle state are exposed as one accessible control.
- Single-choice controls expose radio roles and selected state.
- Disabled controls expose their disabled state. When the required precondition is not evident from the control label, adjacent text explains how to enable it.
- Decorative icons remain absent from the accessibility tree.
- Long labels wrap instead of clipping.
- Fixed-height choice cards are avoided when text size can change.
- Learned-rule content and actions stack safely at increased font sizes.
- Paired actions stack vertically when width or font scale makes a horizontal row unsafe.
- Headers, scrolling content, IME padding, system bars, and floating-navigation clearance remain reachable.

## Architecture boundaries

The current Settings implementation is split along destination boundaries. The implementation uses these units:

- Settings navigation graph and route definitions
- Settings hub
- Personal category
- Appearance category
- Background detail
- Surfaces detail
- AI & privacy category
- Gemini setup detail
- Feature access detail
- Local learning detail
- Data & app category
- Local data detail
- Shared Settings components
- Settings-facing state owner

These units remain inside the Settings feature. Shared components are not promoted app-wide unless an existing non-Settings consumer has the same semantic role.

The split must preserve unidirectional state flow and keep business logic out of Composables.

## Scope

### Included

- Four-category Settings hierarchy
- Dedicated category and detail destinations
- Independent destination scroll state
- Responsive Settings layouts
- Complete semantics for custom controls
- Safer text-entry behavior
- Risk-tiered confirmation and Undo behavior
- Atomic settings mutations
- Correct transient-message lifecycle
- Focused tests for the new hierarchy and interactions

### Excluded

- New settings, integrations, or cloud services
- New production dependencies
- Room or export-format changes
- Gemini prompt or AI decision changes
- Broad redesign of non-Settings screens
- Tablet-specific two-pane Settings in this milestone

## Verification

### Automated

- Settings hub, category, and detail routing
- Back behavior at every depth
- Floating-navigation visibility by depth
- Independent scroll restoration between destinations
- Atomic setting updates under rapid sequential changes
- Profile-draft commit behavior
- API-key failure retaining its draft
- Model validation, Save, and Restore defaults behavior
- Confirmation and Undo routing
- Toggle labels, roles, selected state, and disabled state
- Adaptive layout behavior at representative widths and font scales
- Learned-rule responsive layout
- Restore-message consumption and replacement
- Existing appearance reset, time-format, Gemini consent, and export/restore tests
- Focused compile and Android lint checks

### Manual

- Light, Dark, and Auto themes
- Preset and custom backgrounds
- Normal and increased font sizes
- Keyboard, IME actions, and focus movement
- TalkBack traversal and focus order
- Android reduced-motion behavior
- Rotation and process recreation
- No-key, saved-key, Local-only, and Gemini AI states
- Export cancellation, restore cancellation, confirmation, success, and failure
- Scrolling performance with preset and custom backgrounds

## Protected behavior

This design preserves:

- Categorized Settings
- 24-hour time behavior
- Centered floating bottom navigation
- Local-first operation
- Gemini consent and per-feature permission
- User confirmation before important mutations
- Export/restore validation and replacement confirmation
- Appearance reset scope
- First-time guide replay behavior
- Raw-capture and internal-AI visibility boundaries
