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
Full-screen background blur, high Haze blur/noise/shadow values, and large decoded custom background images all contribute to per-frame rendering cost across every screen.

Fix:
Lowered the background blur multiplier, reduced Haze blur/noise/shadow constants, and capped custom background decode size. Background presets are no longer rendered while a custom background is active.

Prevention:
When changing appearance effects, scroll-test Settings, Spaces, Review, Search, and glass sheets with both preset and custom backgrounds.

Related files/areas:
- app/src/main/java/com/orbit/app/ui/components/OrbitBackground.kt
- app/src/main/java/com/orbit/app/ui/components/GlassSurface.kt
- app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt
- Settings
- Appearance

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
