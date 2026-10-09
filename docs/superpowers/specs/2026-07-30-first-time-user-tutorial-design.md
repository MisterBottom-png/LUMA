# First-time user tutorial design

## Goal

Give new LUMA users a calm, concise introduction to the product before they reach Home for the first time. The guide must explain LUMA's capture-first, confirmation-based, local-first model without turning onboarding into account setup, configuration, or a required sample task.

## Scope

### Included

- A dedicated seven-page, full-screen Compose tutorial.
- Automatic presentation before Home on first launch.
- Persistent completion state in the existing app-settings DataStore.
- A replay entry in Settings.
- Back, Next, Skip, swipe, and final completion behavior.
- English, Estonian, and Russian resources.
- Accessibility, adaptive layout, theme, and reduced-motion support.
- Focused routing, persistence, interaction, semantics, and regression tests.

### Excluded

- Account creation, cloud sync, analytics, telemetry, or backend work.
- A guided sample capture.
- Live coach marks over existing destinations.
- New permissions, Room schema changes, or production dependencies.
- Changes to Gemini consent or notification permission timing.
- Product redesign outside the tutorial and its Settings entry.

## User flow

On startup, the app reads `hasCompletedFirstTimeTutorial` from the existing `AppSettings` state.

- When the value is `false`, the navigation graph starts at the Tutorial destination.
- When the value is `true`, the graph starts at Home as it does today.
- Skip records completion and opens Home.
- The final page's primary action, "Start using LUMA," records completion and opens Home normally without focusing capture or creating sample data.
- Settings includes a "First-time guide" row that opens the same destination in replay mode.
- System Back from replay returns to Settings without changing completion state.
- Completing or skipping replay preserves the already-completed setting and returns to Settings.

The mandatory first-run flow cannot be bypassed accidentally through system Back. On the first page, Back follows the platform's app-exit behavior. On later pages, Back returns to the previous tutorial page.

## Architecture

### Persistent state

Add a Boolean completion property to `AppSettings`, defaulting to `false`, and map it through the existing `AppSettingsRepository` DataStore serializer. No Room or export-format changes are required because this is app configuration rather than user content.

The completion update is idempotent. Navigation away from first-run mode occurs after requesting the write. If the write fails, the current session may continue to Home, but the tutorial may appear again on a later launch rather than falsely treating the state as durable.

### Navigation

Add a Tutorial destination to the existing Navigation Compose graph. Select the graph's start destination from the loaded `AppSettings` completion value:

- incomplete: Tutorial in first-run mode;
- complete: Home.

Replay navigation includes an explicit replay argument or equivalent saved-state contract so Back and completion route to Settings instead of Home. First-run completion clears the tutorial from the back stack so Back from Home cannot reopen it.

The bottom navigation is hidden on the Tutorial destination. Existing destination routing and Home layout behavior remain unchanged.

### UI state

The tutorial owns a bounded page index from zero through six. Save the index with Compose saveable state so rotation and compatible process recreation restore the current page.

Back, Next, Skip, swipe, and progress controls call a single state-transition contract. Repeated completion taps are ignored after navigation begins.

## Presentation

The screen uses LUMA's existing background, theme, typography, spacing, shapes, and motion conventions. Each page includes:

- a short step label;
- a title;
- supporting text;
- one principle card;
- a lightweight code-native illustration;
- a seven-step progress indicator;
- Back and Next controls;
- a visible Skip action.

The final page replaces Next with "Start using LUMA." Illustrations are inspired by the supplied HTML prototype but implemented with existing Compose primitives and iconography. They do not copy the prototype's desktop phone frame or introduce raster assets. Illustrations are decorative when the adjacent text already communicates the meaning.

Motion uses short, calm page transitions and respects the app's reduced-motion behavior. The layout scrolls when needed for large text, narrow screens, or landscape rather than clipping controls.

## Page content

### 1. The idea

Explain that LUMA is a calm home for thoughts before they become plans. Emphasize capture first and organization second.

### 2. Home

Explain natural-language capture and lightweight time orientation through the Home week strip and Calendar.

### 3. Confirm

Explain that suggestions are proposals and that important actions require user confirmation.

### 4. Brain Dump

Explain that multi-thought captures become one calm decision at a time and that the original raw capture remains private source material.

### 5. Spaces

Explain that finalized items can be organized by life context without forcing every thought onto a schedule.

### 6. Review and ask

Explain Review, Situation AI, and Ask LUMA as calm, source-grounded aids rather than scores or pressure mechanisms.

### 7. Control

Explain local-first behavior, optional Gemini use with consent, permission timing, and user control. Invite the user to start with one real thought without automatically focusing the input.

All visible and semantic strings are externalized in English, Estonian, and Russian. Copy uses LUMA's calm tone and avoids guilt, productivity scoring, or internal processing labels.

## Accessibility

- Expose the page title and "Step N of 7" state to accessibility services.
- Mark the active progress step as selected and avoid relying on color alone.
- Give Back, Next, Skip, and final controls unambiguous localized labels.
- Keep touch targets at least the app's standard accessible size.
- Preserve logical focus order from page content to progress and navigation.
- Do not announce decorative illustrations.
- Support font scaling without overlapping or hiding the final action.
- Keep swipe optional; every page remains reachable with buttons and accessibility actions.

## Error handling and edge cases

- Clamp the page index to the valid range.
- Ignore repeated completion actions while persistence/navigation is in progress.
- Keep first-run completion and replay exit behavior separate.
- Do not request notification permission, Gemini consent, or any other permission during onboarding.
- Do not create, edit, schedule, or delete user data.
- If completion persistence fails, avoid blocking the current session; a later launch may show the tutorial again.
- A language or theme change already applied by the app is reflected through normal resource and theme recomposition.

## Verification

### Unit tests

- Incomplete settings select Tutorial as the start destination.
- Completed settings select Home.
- First-run Skip and final completion request the completion update once.
- Replay does not reset completion state.
- Page transitions remain within zero through six.
- Back behavior differs correctly between first-run and replay modes.

### Compose and navigation tests

- The first page exposes its title, step state, and controls.
- Next, Back, progress navigation, and swipe change pages correctly.
- The final action opens Home during first-run mode.
- Replay exits to Settings.
- Bottom navigation is hidden during the tutorial and restored afterward.
- Large text and narrow layouts keep controls reachable.
- Settings exposes a localized "First-time guide" entry.

### Repository checks

- Run focused tutorial, navigation, settings, and app-settings tests during iteration.
- Run the relevant JVM test suite, debug compilation, and Android lint once before completion.
- Run the strict workplace-privacy checker and semantically review changed text.
- Review the final diff for unrelated edits.
- Confirm protected Home layout, centered bottom navigation, capture behavior, Brain Dump, Ask LUMA, and Settings categorization are not changed.

If no Android target is connected, first-launch, rotation, font-scale, screen-reader, and Settings replay smoke checks remain explicit manual verification items.
