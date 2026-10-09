# Shaped Press Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make press feedback respect each interactive surface's rounded or circular bounds without shrinking existing touch targets.

**Architecture:** Extend the existing `orbitPressFeedback` modifier with an optional clipping shape. The modifier retains the caller's interaction source and scale animation, and clips downstream indication drawing when a shape is supplied. Apply it only at callers whose visual surface already has an explicit rounded shape; preserve existing roles, callbacks, and layout bounds.

**Tech Stack:** Kotlin, Jetpack Compose Foundation and Material 3, Compose UI instrumentation tests, Gradle.

## Global Constraints

- Preserve the current clickable/selectable callbacks, semantics, and minimum touch targets.
- Reuse existing shape values; do not add visual tokens or production dependencies.
- Do not alter navigation, data, theme selection, or localization behavior.
- Keep calendar month cells and bottom navigation unchanged where they are already clipped to their correct shape.

---

### Task 1: Add shape-aware press-feedback coverage and implementation

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/components/MotionModifiers.kt:20-42`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt:500-580`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt:457-470,764-776,2032-2052`
- Modify: `app/src/main/java/com/orbit/app/ui/components/SourceRow.kt:70-86`
- Test: `app/src/androidTest/java/com/orbit/app/ui/screens/home/HomeWeekStripAccessibilityTest.kt`

**Interfaces:**
- Consumes: `InteractionSource`, optional `androidx.compose.ui.graphics.Shape`, and the current `OrbitMotion` scale values.
- Produces: `Modifier.orbitPressFeedback(interactionSource, enabled, clipShape)`; callers may provide their existing rounded shape to clip press feedback.

- [ ] **Step 1: Write the failing focused test**

Add a WeekStrip instrumentation test that holds a day press, captures the composable, and asserts that pixels outside the rounded day capsule are unchanged while pixels inside the capsule receive press feedback. Use a fixed `MaterialTheme`, a fixed date, `mainClock.autoAdvance = false`, and release the pointer after the image assertion.

```kotlin
@Test
fun pressedDayFeedbackIsClippedToItsRoundedCapsule() {
    composeRule.mainClock.autoAdvance = false
    // Set a fixed-width WeekStrip with a fixed HomeWeekUiState.
    // Press a day control, advance to the press-animation frame, and capture it.
    // Assert the sampled capsule corner matches the unpressed background.
    // Assert the sampled capsule centre differs from the unpressed background.
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.orbit.app.ui.screens.home.HomeWeekStripAccessibilityTest`

Expected: the new test fails because the existing clickable day cell renders its indication over the full rectangular cell.

- [ ] **Step 3: Implement shape-aware clipping and update only unbounded callers**

Change `orbitPressFeedback` so its graphics layer receives the caller's shape and clips only when that optional shape is non-null.

```kotlin
fun Modifier.orbitPressFeedback(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    clipShape: Shape? = null,
): Modifier = graphicsLayer {
    scaleX = scale
    scaleY = scale
    shape = clipShape ?: RectangleShape
    clip = clipShape != null
}
```

Pass the existing `RoundedCornerShape(22.dp)` to the Home week-strip press modifier. Pass each existing rounded control shape to the Settings preset option and `SourceRow`; give Settings rows their established grouped-surface shape so their indication cannot escape the surrounding material. Preserve all current `interactionSource`, `clickable`, `selectable`, and `semantics` calls. Do not change `CalendarMonthDayCell` or `NavIcon`, which already use `clip(shape)` and `clip(CircleShape)` before their indications.

- [ ] **Step 4: Run the focused test and verify it passes**

Run: `./gradlew --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.orbit.app.ui.screens.home.HomeWeekStripAccessibilityTest`

Expected: the new shape-clipping assertion and the existing heading/selected-state test pass.

- [ ] **Step 5: Run focused regression and compilation checks**

Run: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

Expected: exit code 0; no compilation error from the `Shape` parameter; no new lint error; affected week-strip and component semantics tests remain green.

- [ ] **Step 6: Manually verify visual states**

On an Android device or emulator, long-press Settings category rows, a Settings submenu row, a background preset, a Home week-strip day, a source row, and each bottom-navigation icon. Confirm feedback stays within its visible rounded or circular surface, all controls still navigate, and the Home day and bottom navigation keep usable touch targets.

- [ ] **Step 7: Commit the focused patch**

```bash
git add app/src/main/java/com/orbit/app/ui/components/MotionModifiers.kt app/src/main/java/com/orbit/app/ui/components/SourceRow.kt app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt app/src/androidTest/java/com/orbit/app/ui/screens/home/HomeWeekStripAccessibilityTest.kt docs/superpowers/specs/2026-07-30-shaped-press-feedback-design.md docs/superpowers/plans/2026-07-30-shaped-press-feedback.md
git commit -m "fix: clip press feedback to control shapes"
```
