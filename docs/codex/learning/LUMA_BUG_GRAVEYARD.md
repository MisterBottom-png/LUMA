# LUMA Bug Graveyard

Store old bugs, causes, fixes, and prevention notes.

Dramatic name. Useful file. Software deserves a cemetery.

## Format

```md
## Bug: <short name>

Date:
YYYY-MM-DD

Symptom:
<what happened>

Cause:
<why it happened>

Fix:
<what fixed it>

Prevention:
<test/checklist/rule to prevent recurrence>

Related files/areas:
- ...
```

## Starter bug-risk note: Reminder event time vs notification time

Date:
2026-07-08

Symptom:
Natural language like “send package tomorrow at 10, remind me one hour earlier” can be misunderstood if event time and notification offset are not separated.

Cause:
Reminder extraction may treat event time and reminder notification time as the same concept.

Fix:
Future implementation should preserve target event time and reminder notification offset separately.

Prevention:
Add regression checks/tests for:
- tomorrow at 10
- one hour earlier
- morning/evening wording
- 12/24 hour format
- local timezone

Related files/areas:
- reminder parser
- AI analyzer schema
- reminder entity
- notification scheduler

## Bug: App-wide appearance effects made menus sluggish

Date:
2026-07-08

Symptom:
Scrolling felt sluggish across menus after glass/background effects and custom backgrounds were available.

Cause:
Full-screen background blur, Haze source capture, high Haze blur/noise/shadow values, and large decoded custom background images all contribute to per-frame rendering cost across every screen. A later fixed-chrome change also placed the complete navigation host in an offscreen compositing layer for a bottom-edge fade, stacking that full-screen pass with live Haze capture on Review and Settings. Spaces also rebuilt sorted and formatted feed rows during lazy-list composition and calculated each Space count by rescanning every item table.

Fix:
Lowered the background blur multiplier, reduced Haze blur/noise/shadow constants, and capped custom background decode size. Background presets are no longer rendered while a custom background is active. Spaces, Review, and Settings disable the full-screen Haze source and use the same appearance-derived translucent fallback tint. The navigation host no longer owns an app-wide offscreen edge-fade layer; the required top and bottom fades stay scoped to the Spaces and Review lazy lists. Preset gradients avoid a visually redundant blur layer, custom-background overlays share one draw layer, feed preparation is cached by contents and time format, Space partitions are prepared once per state emission, and counts use one bounded pass across each finalized item list.

Prevention:
When changing appearance effects, scroll-test Settings, Spaces, Review, Search, and glass sheets with both preset and custom backgrounds. Do not apply offscreen compositing to the complete navigation host for local scroll-edge decoration; scope masks to the smallest scrolling layer. Keep lazy feed transformations outside item composition, retain stable item keys, and use grouping/count accumulation instead of per-parent repeated table scans.

Related files/areas:
- app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt
- app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt
- app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt
- Settings
- Appearance

## Bug: Situation AI composer scrolled away and ignored the IME inset

Date:
2026-07-15

Symptom:
The Ask LUMA composer originally scrolled with the body. After it was pinned, focusing the field still moved the sheet downward and left the composer behind the keyboard.

Cause:
The persistent composer first shared the body `LazyColumn`. The initial repair then applied IME padding inside the custom fixed-height content even though Material 3 already applies IME padding at the modal root, while partial expansion remained enabled as keyboard resizing changed sheet anchors.

Fix:
Keep the header fixed, render content and answer evidence in one weighted lazy body, and place the composer below it. Let Material 3 handle the platform IME at the modal root, keep navigation-bar padding inside the custom content, and disable partial expansion for this composer sheet.

Prevention:
For Material 3 modal surfaces with a persistent text composer, inspect framework inset handling before adding content-level IME padding, prevent IME resizing from selecting an unintended partial anchor, and physically verify keyboard-open layout without hardcoded keyboard dimensions.

Related files/areas:
- Situation AI sheet
- Ask LUMA composer
- Compose window insets

## Bug: Space Life Feed exposed capture processing records

Date:
2026-07-08

Symptom:
Space Life Feed could show a processed raw capture beside the finalized note/task/reminder created from it, making the feed look duplicated and exposing internal status labels.

Cause:
Space content selection counted and rendered non-archived captures by suggested Space alongside finalized entities.

Fix:
Filter captures out of Space Life Feed and Space counts while preserving capture records internally for Review, search, source history, and detail flows.

Prevention:
Regression-check that Space Life Feed renders only finalized user-facing items and never uses "Processed" as a standalone user-facing label.

Related files/areas:
- app/src/main/java/com/orbit/app/ui/screens/spaces/SpacesViewModel.kt
- app/src/main/java/com/orbit/app/ui/screens/spaces/SpacesScreen.kt
- Spaces
- Life Feed
- captures

## Bug: Review actions did not match their persisted outcomes

Date:
2026-07-13

Symptom:
Review used generic action labels even when capture and task actions produced different persisted states, and processed capture records could appear under `Done today`.

Cause:
The UI shared action names across entity types while the ViewModel implemented type-specific transitions without exposing those differences.

Fix:
Use type-specific action labels and explanations, route capture confirmation through the exactly-once finalizer, keep dismiss/archive non-finalizing, and exclude processed captures from completed final-item sections.

Prevention:
Test each Review action against repository state and verify that completed sections contain only finalized user-facing entities.

Related files/areas:
- Review ViewModel
- Review screen
- capture confirmation
- item visibility

## Bug: Parsed reminder phrase disagreed with the accepted timestamp

Date:
2026-07-14

Symptom:
The capture suggestion showed the intended local time, but opening reminder setup displayed a different time that was then persisted and scheduled.

Cause:
The suggestion chip rendered Gemini's human-readable phrase while reminder setup used a separately supplied epoch. Schema validation did not require those fields to represent the same instant, and explicit compact time forms had no deterministic local resolver to replace a conflicting AI epoch.

Fix:
Explicit supported times now resolve locally to one canonical epoch in the device timezone. The AI router preserves that local result, invalid compact values clear conflicting AI timestamps, and the unchanged epoch flows through confirmation, Room, editor display, and scheduling.

Prevention:
Test the interpreted phrase and structured epoch together, round-trip the epoch through a non-UTC timezone, and verify the repository and scheduler receive the exact accepted target while offsets remain separate.

Related files/areas:
- capture analyzer
- Gemini router and prompt
- reminder confirmation
- reminder repository
- notification scheduler

## Bug: Custom background retained preset-looking glass

Date:
2026-07-14

Symptom:
After choosing a custom background from preset mode, the image changed but shared glass surfaces remained pale and appeared to retain the preset treatment.

Cause:
Preset and custom backgrounds used different Haze source nodes while retaining one Haze state. Glass mode followed the requested custom URI before its bitmap was actually rendered, and a large custom-only tint boost masked most image color.

Fix:
Both background modes now render inside one stable Haze source. Glass follows the background actually visible on screen, and custom-mode haze and soft-surface tints remain contrast-protective without obscuring the image.

Prevention:
Test both transition directions and verify the background, standard glass, soft glass, sheets, capture surface, and bottom navigation change together after the selected image finishes loading.

Related files/areas:
- app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt
- app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt
- Settings
- Appearance

## Bug: Appearance effect sliders looked broken

Date:
2026-07-16

Symptom:
Background blur, background dim, and glass strength changed so little across much of their ranges that the controls appeared ineffective. The Appearance index also used a large technical preview that did not explain the settings.

Cause:
Image blur had a narrow cached radius, light-theme dimming was capped at a subtle overlay, and both live and soft glass opacity varied only within small ranges with relatively opaque minimums. The control names mixed background treatment with surface material.

Fix:
Expanded the bounded blur and dim ranges, gave shared surfaces a clear contrast-safe transparent-to-solid range, renamed the surface control to opacity, moved the preview next to the relevant controls, reduced index-row repetition, and added an appearance-only reset. Blur changes retain the previously processed image until the next cached result is ready.

Prevention:
Keep image blur, background dim, and surface opacity semantically separate. Test their minimum, midpoint, and maximum values in light and dark themes with preset and custom backgrounds, and ensure appearance reset preserves unrelated preferences.

Related files/areas:
- app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt
- app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt
- app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt
- Appearance

Follow-up:
The first repair still added large fixed base opacity before applying the displayed percentage, so a 30% setting rendered materially more solid than its label implied. Shared live and soft glass now derive primarily from the selected percentage, with only small bounded adjustments for surface role, theme, and custom-background contrast.

Additional prevention:
Test a named midpoint such as 30% against the effective alpha for both fixed navigation and prominent cards; endpoint-only range tests do not prove that the displayed percentage is honest.

Second follow-up:
The floating navigation still used LiveGlass, whose Haze style intentionally supplied an opaque background beneath blur and tint. At very low surface opacity the tint became lighter, but the backing plane remained visibly solid. Floating navigation now uses subtle SoftGlass so the selected opacity controls its actual translucent fill without a hidden opaque layer.

Additional prevention:
Physically verify bottom navigation at a low value such as 7% over a detailed custom background. A tint-alpha unit test cannot detect an opaque backing layer owned by the rendering effect.

## Bug: Calendar current-time line was not a timeline position

Date:
2026-07-15

Symptom:
The selected-day view showed the current-time label and line as a compact list row, so it appeared near the top regardless of the actual time and the empty-state copy competed with the indicator.

Cause:
Timed content was ordered correctly but rendered only as adjacent lazy-list rows, with no minute-of-day spatial scale. Calendar paging also existed only as arrow controls.

Fix:
Render the complete day on a compact hour scale, position timed groups and the live indicator from local minute-of-day, keep the empty message in the content column below the indicator, and add thresholded horizontal paging gestures with accessibility actions. Reuse the resolved application time formatter for hour and current-time labels.

Prevention:
Test midnight, midday, and end-of-day offsets; verify Device, 12-hour, and 24-hour labels; and confirm one horizontal swipe changes exactly one day or month while vertical scrolling and item taps remain usable.

Related files/areas:
- Calendar Day timeline
- Calendar paging gestures
- time-format presentation

## Bug: Brain Dump progress was transient and source coverage was lossy

Date:
2026-07-18

Symptom:
Closing or leaving Brain Dump discarded progress, short or repeated fragments could disappear, and an incomplete AI response could silently omit source material. Save actions also lacked a durable exactly-once boundary.

Cause:
The flow lived only in sheet memory, local parsing filtered and deduplicated input, AI output was trusted without complete source-identifier coverage, and item actions wrote through separate non-transactional paths.

Fix:
Persist sessions and source-keyed items in Room, resume them from Home, Review, and Capture detail, require strict one-to-one Gemini coverage with local fallback, and finalize each item through one transactional action boundary. Include active sessions in export and restore, and remove pending progress when its capture is dismissed or archived.

Prevention:
Test short and duplicate fragments, malformed and partial AI output, recreation and export/restore, concurrent repeated actions, rollback after a failed write, reminder scheduling after commit, calendar context, and long-list/IME reachability.

Related files/areas:
- Brain Dump analyzer and Gemini validator
- Brain Dump Room session and item tables
- Home, Review, and Capture detail resume routes
- export and restore
