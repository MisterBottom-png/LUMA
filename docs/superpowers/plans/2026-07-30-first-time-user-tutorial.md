# First-time User Tutorial Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a seven-page, localized first-run tutorial that appears before Home once, remains replayable from Settings, and preserves LUMA's local-first and confirmation boundaries.

**Architecture:** Persist tutorial completion in the existing preferences DataStore and wait for the first settings emission before selecting the Navigation Compose start destination. Render the guide as one isolated Compose screen backed by a fixed resource descriptor list, then connect first-run and replay modes through a dedicated navigation destination.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Foundation `HorizontalPager`, Navigation Compose, Preferences DataStore, JUnit 4, Android Compose UI tests.

## Global Constraints

- Use the approved design in `docs/superpowers/specs/2026-07-30-first-time-user-tutorial-design.md`.
- Keep all user content local and create no user items, permissions, provider requests, telemetry, accounts, or backend work.
- Add no Room change, export-format change, production dependency, or raster asset.
- Keep Home capture-first and preserve Home layout, centered bottom navigation, Brain Dump, Ask LUMA, and categorized Settings behavior.
- Important actions remain confirmation-based; the tutorial may only explain behavior and record its own completion.
- Externalize every visible and semantic string in English, Estonian, and Russian.
- Use generic role language only and run the strict workplace-privacy checker after text-bearing changes.
- Respect Android animator duration scale through finite Compose animation specs.
- The workspace currently exposes no usable Git metadata. Run each commit step only if `.git/HEAD` is restored; do not initialize a replacement repository or include unrelated files.

---

## File map

- `app/src/main/java/com/orbit/app/domain/model/AppSettings.kt`: owns the persisted completion property.
- `app/src/main/java/com/orbit/app/data/repository/AppSettingsRepository.kt`: maps the property to Preferences DataStore.
- `app/src/main/java/com/orbit/app/ui/LocalDataViewModel.kt`: exposes nullable startup settings so navigation waits for real persisted state.
- `app/src/main/java/com/orbit/app/MainActivity.kt`: renders the app only after settings load.
- `app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialContent.kt`: owns the fixed seven-page resource descriptors.
- `app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreen.kt`: owns pager state, tutorial layout, semantics, illustrations, and tutorial-local Back behavior.
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt`: owns the tutorial route and pure start-destination selection.
- `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`: connects first-run completion, replay, Settings, Home, and bottom-navigation visibility.
- `app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt`: exposes the replay entry inside the existing System category.
- `app/src/main/res/values*/strings.xml`: owns localized tutorial and Settings copy.
- Focused JVM and instrumentation tests mirror the changed packages.

---

### Task 1: Persist completion and remove the startup race

**Files:**
- Modify: `app/src/main/java/com/orbit/app/domain/model/AppSettings.kt`
- Modify: `app/src/main/java/com/orbit/app/data/repository/AppSettingsRepository.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/LocalDataViewModel.kt`
- Modify: `app/src/main/java/com/orbit/app/MainActivity.kt`
- Create: `app/src/test/java/com/orbit/app/domain/model/AppSettingsTutorialTest.kt`

**Interfaces:**
- Produces: `AppSettings.hasCompletedFirstTimeTutorial: Boolean`
- Produces: `LocalDataViewModel.settings: StateFlow<AppSettings?>`
- Preserves: `LocalDataViewModel.updateSettings(settings: AppSettings)`

- [ ] **Step 1: Write the failing settings model test**

```kotlin
package com.orbit.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTutorialTest {
    @Test
    fun tutorialIsIncompleteUntilExplicitlyCompleted() {
        assertFalse(AppSettings().hasCompletedFirstTimeTutorial)
        assertTrue(
            AppSettings(hasCompletedFirstTimeTutorial = true)
                .hasCompletedFirstTimeTutorial,
        )
    }

    @Test
    fun resettingAppearancePreservesTutorialCompletion() {
        assertTrue(
            AppSettings(
                themeMode = SettingsThemeMode.Dark,
                hasCompletedFirstTimeTutorial = true,
            ).withDefaultAppearance().hasCompletedFirstTimeTutorial,
        )
    }
}
```

- [ ] **Step 2: Run the focused test and verify the new property is missing**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.domain.model.AppSettingsTutorialTest"
```

Expected: compilation fails because `hasCompletedFirstTimeTutorial` does not exist.

- [ ] **Step 3: Add the model property and DataStore mapping**

Add to `AppSettings`:

```kotlin
val hasCompletedFirstTimeTutorial: Boolean = false,
```

In `DataStoreAppSettingsRepository.update` write:

```kotlin
preferences[Keys.HAS_COMPLETED_FIRST_TIME_TUTORIAL] =
    settings.hasCompletedFirstTimeTutorial
```

In `toAppSettings` read:

```kotlin
hasCompletedFirstTimeTutorial =
    preferences[Keys.HAS_COMPLETED_FIRST_TIME_TUTORIAL]
        ?: defaults.hasCompletedFirstTimeTutorial,
```

Add the key:

```kotlin
val HAS_COMPLETED_FIRST_TIME_TUTORIAL =
    booleanPreferencesKey("has_completed_first_time_tutorial")
```

- [ ] **Step 4: Wait for persisted settings before building navigation**

Change the settings state in `LocalDataViewModel` to:

```kotlin
val settings: StateFlow<AppSettings?> =
    container.appSettingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
```

In `MainActivity.setContent`, keep collecting the nullable state, then return from the content lambda until it has emitted:

```kotlin
val settings by localDataViewModel.settings.collectAsStateWithLifecycle()
val loadedSettings = settings ?: return@setContent
```

Pass `loadedSettings` to `OrbitTheme`, system-bar selection, and `OrbitApp`. This prevents a stored `true` value from briefly constructing a false first-run navigation graph.

- [ ] **Step 5: Run the focused model tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.domain.model.AppSettingsTutorialTest" --tests "com.orbit.app.domain.model.AppSettingsAppearanceTest"
```

Expected: PASS.

- [ ] **Step 6: Commit the persistence slice when Git metadata is available**

```powershell
git add app/src/main/java/com/orbit/app/domain/model/AppSettings.kt app/src/main/java/com/orbit/app/data/repository/AppSettingsRepository.kt app/src/main/java/com/orbit/app/ui/LocalDataViewModel.kt app/src/main/java/com/orbit/app/MainActivity.kt app/src/test/java/com/orbit/app/domain/model/AppSettingsTutorialTest.kt
git commit -m "feat: persist first-time tutorial completion"
```

---

### Task 2: Define localized tutorial content

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialContent.kt`
- Create: `app/src/test/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialContentTest.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-et/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Verify: `app/src/test/java/com/orbit/app/ui/localization/LocalizationResourceParityTest.kt`

**Interfaces:**
- Produces: `internal data class FirstTimeTutorialPage(...)`
- Produces: `internal val firstTimeTutorialPages: List<FirstTimeTutorialPage>`
- Produces: seven indexed pages using only `@StringRes` identifiers

- [ ] **Step 1: Write the failing descriptor test**

```kotlin
package com.orbit.app.ui.screens.tutorial

import com.orbit.app.R
import org.junit.Assert.assertEquals
import org.junit.Test

class FirstTimeTutorialContentTest {
    @Test
    fun tutorialHasSevenOrderedPages() {
        assertEquals(7, firstTimeTutorialPages.size)
        assertEquals(R.string.tutorial_idea_title, firstTimeTutorialPages.first().titleRes)
        assertEquals(R.string.tutorial_control_title, firstTimeTutorialPages.last().titleRes)
        assertEquals(7, firstTimeTutorialPages.map { it.illustration }.distinct().size)
    }
}
```

- [ ] **Step 2: Run the focused test and verify descriptors/resources are missing**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.screens.tutorial.FirstTimeTutorialContentTest"
```

Expected: compilation fails because the tutorial content types and resources do not exist.

- [ ] **Step 3: Create the typed page descriptors**

```kotlin
package com.orbit.app.ui.screens.tutorial

import androidx.annotation.StringRes
import com.orbit.app.R

internal enum class TutorialIllustration {
    Idea, Home, Confirm, BrainDump, Spaces, Review, Control,
}

internal data class FirstTimeTutorialPage(
    @param:StringRes val stepRes: Int,
    @param:StringRes val titleRes: Int,
    @param:StringRes val bodyRes: Int,
    @param:StringRes val principleTitleRes: Int,
    @param:StringRes val principleBodyRes: Int,
    val illustration: TutorialIllustration,
)

internal val firstTimeTutorialPages = listOf(
    FirstTimeTutorialPage(
        R.string.tutorial_idea_step,
        R.string.tutorial_idea_title,
        R.string.tutorial_idea_body,
        R.string.tutorial_idea_principle_title,
        R.string.tutorial_idea_principle_body,
        TutorialIllustration.Idea,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_home_step,
        R.string.tutorial_home_title,
        R.string.tutorial_home_body,
        R.string.tutorial_home_principle_title,
        R.string.tutorial_home_principle_body,
        TutorialIllustration.Home,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_confirm_step,
        R.string.tutorial_confirm_title,
        R.string.tutorial_confirm_body,
        R.string.tutorial_confirm_principle_title,
        R.string.tutorial_confirm_principle_body,
        TutorialIllustration.Confirm,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_brain_dump_step,
        R.string.tutorial_brain_dump_title,
        R.string.tutorial_brain_dump_body,
        R.string.tutorial_brain_dump_principle_title,
        R.string.tutorial_brain_dump_principle_body,
        TutorialIllustration.BrainDump,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_spaces_step,
        R.string.tutorial_spaces_title,
        R.string.tutorial_spaces_body,
        R.string.tutorial_spaces_principle_title,
        R.string.tutorial_spaces_principle_body,
        TutorialIllustration.Spaces,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_review_step,
        R.string.tutorial_review_title,
        R.string.tutorial_review_body,
        R.string.tutorial_review_principle_title,
        R.string.tutorial_review_principle_body,
        TutorialIllustration.Review,
    ),
    FirstTimeTutorialPage(
        R.string.tutorial_control_step,
        R.string.tutorial_control_title,
        R.string.tutorial_control_body,
        R.string.tutorial_control_principle_title,
        R.string.tutorial_control_principle_body,
        TutorialIllustration.Control,
    ),
)
```

- [ ] **Step 4: Add exact English copy**

Add shared controls:

```xml
<string name="tutorial_skip">Skip</string>
<string name="tutorial_back">Back</string>
<string name="tutorial_next">Next</string>
<string name="tutorial_start">Start using LUMA</string>
<string name="tutorial_progress">Step %1$d of %2$d</string>
<string name="tutorial_go_to_step">Go to step %1$d</string>
<string name="settings_first_time_guide_title">First-time guide</string>
<string name="settings_first_time_guide_status">Replay the introduction</string>
```

For each page, add the approved `step`, `title`, `body`, `principle_title`, and `principle_body` text verbatim from the English `steps` array in `LUMA_First_Time_User_Tutorial_v5.html`. Use these prefixes in order:

```text
tutorial_idea_
tutorial_home_
tutorial_confirm_
tutorial_brain_dump_
tutorial_spaces_
tutorial_review_
tutorial_control_
```

The first and last examples must be:

```xml
<string name="tutorial_idea_step">01 · The idea</string>
<string name="tutorial_idea_title">A home for thoughts before they become plans.</string>
<string name="tutorial_idea_body">LUMA is a calm life inbox. Put a thought in first, then decide whether it belongs as a note, task, reminder, or simply in the Inbox.</string>
<string name="tutorial_idea_principle_title">Capture first. Organise second.</string>
<string name="tutorial_idea_principle_body">You do not need to choose a category, date, or Space before writing.</string>
<string name="tutorial_control_step">07 · Control</string>
<string name="tutorial_control_title">Private by default. Cloud AI only by choice.</string>
<string name="tutorial_control_body">Core LUMA features work locally. Gemini is optional, requires consent, and receives only the selected text and relevant context. Reminder permission appears when it is actually needed.</string>
<string name="tutorial_control_principle_title">Start with one real thought.</string>
<string name="tutorial_control_principle_body">Natural language is the intended interface. Write first; let structure arrive afterward.</string>
```

- [ ] **Step 5: Add exact Estonian and Russian source-aligned copy**

Copy the matching `et.steps` and `ru.steps` values from the same grounded Drive reference into identical keys in `values-et/strings.xml` and `values-ru/strings.xml`. Translate the shared controls as:

```xml
<!-- values-et -->
<string name="tutorial_skip">Jäta vahele</string>
<string name="tutorial_back">Tagasi</string>
<string name="tutorial_next">Edasi</string>
<string name="tutorial_start">Alusta LUMA kasutamist</string>
<string name="tutorial_progress">Samm %1$d/%2$d</string>
<string name="tutorial_go_to_step">Mine sammule %1$d</string>
<string name="settings_first_time_guide_title">Esmakasutaja juhend</string>
<string name="settings_first_time_guide_status">Vaata sissejuhatust uuesti</string>

<!-- values-ru -->
<string name="tutorial_skip">Пропустить</string>
<string name="tutorial_back">Назад</string>
<string name="tutorial_next">Далее</string>
<string name="tutorial_start">Начать работу с LUMA</string>
<string name="tutorial_progress">Шаг %1$d из %2$d</string>
<string name="tutorial_go_to_step">Перейти к шагу %1$d</string>
<string name="settings_first_time_guide_title">Руководство для первого запуска</string>
<string name="settings_first_time_guide_status">Повторить знакомство</string>
```

- [ ] **Step 6: Run content and resource parity tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.screens.tutorial.FirstTimeTutorialContentTest" --tests "com.orbit.app.ui.localization.LocalizationResourceParityTest"
```

Expected: PASS with all new keys present in all three locales.

- [ ] **Step 7: Commit the localized content slice when Git metadata is available**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialContent.kt app/src/test/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialContentTest.kt app/src/main/res/values/strings.xml app/src/main/res/values-et/strings.xml app/src/main/res/values-ru/strings.xml
git commit -m "feat: define localized tutorial content"
```

---

### Task 3: Build the accessible seven-page Compose screen

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreen.kt`
- Create: `app/src/androidTest/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreenTest.kt`

**Interfaces:**
- Consumes: `firstTimeTutorialPages`
- Produces:

```kotlin
@Composable
fun FirstTimeTutorialScreen(
    isReplay: Boolean,
    onFinish: () -> Unit,
    onReplayBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 1: Write failing Compose tests for page semantics and navigation**

Create a Compose test that renders `FirstTimeTutorialScreen` in `MaterialTheme` and verifies:

```kotlin
@Test
fun firstPageExposesProgressAndMovesForward() {
    composeRule.setContent {
        MaterialTheme {
            FirstTimeTutorialScreen(
                isReplay = false,
                onFinish = {},
                onReplayBack = {},
            )
        }
    }

    composeRule.onNodeWithText("A home for thoughts before they become plans.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("Step 1 of 7").assertIsDisplayed()
    composeRule.onNodeWithText("Back").assertIsNotEnabled()
    composeRule.onNodeWithText("Next").performClick()
    composeRule.onNodeWithText("Capture thoughts and orient yourself in time.")
        .assertIsDisplayed()
}
```

Add a second test that swipes to page seven, asserts "Start using LUMA," taps it twice, and verifies an `AtomicInteger` callback count remains one.

Add a replay test that invokes the system Back action on page one and verifies `onReplayBack` once.

- [ ] **Step 2: Compile the instrumentation test and verify the screen is missing**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugAndroidTestKotlin
```

Expected: compilation fails because `FirstTimeTutorialScreen` does not exist.

- [ ] **Step 3: Implement pager state and action safety**

Use:

```kotlin
val pages = firstTimeTutorialPages
val pagerState = rememberPagerState(pageCount = { pages.size })
val scope = rememberCoroutineScope()
var leaving by rememberSaveable { mutableStateOf(false) }
```

Back behavior:

```kotlin
BackHandler(enabled = pagerState.currentPage > 0 || isReplay) {
    when {
        pagerState.currentPage > 0 ->
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        isReplay -> onReplayBack()
    }
}
```

Use one guarded finish function for Skip and the final action:

```kotlin
fun finishOnce() {
    if (!leaving) {
        leaving = true
        onFinish()
    }
}
```

- [ ] **Step 4: Implement the adaptive screen structure**

Build a full-size `Column` with:

- status-bar and navigation-bar insets;
- a top row containing LUMA title treatment and Skip;
- `HorizontalPager` with `userScrollEnabled = !leaving`;
- page content in a `verticalScroll` container;
- step label, headline, body, decorative illustration, and principle card;
- a selected progress row with localized click labels;
- 48 dp minimum Back and Next/final controls.

Use `AnimatedContent` or pager animation with `OrbitMotion.StandardDurationMillis`. Do not add infinite motion. On large widths, use `BoxWithConstraints` to place text and illustration side by side; otherwise stack them and keep the bottom controls reachable.

Expose visible progress with:

```kotlin
Text(
    text = stringResource(
        R.string.tutorial_progress,
        pagerState.currentPage + 1,
        pages.size,
    ),
)
```

Give the active progress control `selected = true` semantics, and set illustration containers to `clearAndSetSemantics { }`.

- [ ] **Step 5: Implement seven code-native illustrations**

Create one private composable per `TutorialIllustration` or one exhaustive `when`. Use only existing Material icons, shapes, text-free mock cards, and theme colors:

- Idea: inbox/capture card.
- Home: compact week strip plus capture field.
- Confirm: suggestion card with confirmation affordance.
- Brain Dump: stacked suggestion card and progress indicator.
- Spaces: small set of context cards.
- Review: review card plus sparkle/assistant cue.
- Control: device/privacy rows with lock and check icons.

Keep each illustration bounded, decorative, and independent of user data.

- [ ] **Step 6: Compile the app and instrumentation tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin
```

Expected: PASS.

- [ ] **Step 7: Commit the Compose screen when Git metadata is available**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreen.kt app/src/androidTest/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreenTest.kt
git commit -m "feat: add first-time tutorial screen"
```

---

### Task 4: Connect first-run routing and Settings replay

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt`
- Create: `app/src/test/java/com/orbit/app/ui/navigation/FirstTimeTutorialNavigationTest.kt`
- Modify: `app/src/test/java/com/orbit/app/ui/navigation/BottomNavigationVisibilityTest.kt`

**Interfaces:**
- Consumes: `AppSettings.hasCompletedFirstTimeTutorial`
- Consumes: `FirstTimeTutorialScreen(...)`
- Produces:

```kotlin
object FirstTimeTutorialDestination {
    const val ReplayArgument = "replay"
    const val Route = "tutorial?$ReplayArgument={$ReplayArgument}"
    fun route(isReplay: Boolean): String = "tutorial?$ReplayArgument=$isReplay"
}

internal fun initialOrbitRoute(settings: AppSettings): String
```

- Produces: `SettingsScreen(..., onOpenFirstTimeGuide: () -> Unit, ...)`

- [ ] **Step 1: Write failing pure navigation tests**

```kotlin
package com.orbit.app.ui.navigation

import com.orbit.app.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirstTimeTutorialNavigationTest {
    @Test
    fun incompleteSettingsStartAtTutorial() {
        assertEquals(
            FirstTimeTutorialDestination.route(isReplay = false),
            initialOrbitRoute(AppSettings()),
        )
    }

    @Test
    fun completedSettingsStartAtHome() {
        assertEquals(
            OrbitDestination.Home.route,
            initialOrbitRoute(AppSettings(hasCompletedFirstTimeTutorial = true)),
        )
    }

    @Test
    fun tutorialNeverShowsBottomNavigation() {
        assertFalse(
            shouldShowFloatingBottomNavigation(
                imeVisible = false,
                selectedRoute = FirstTimeTutorialDestination.Route,
                appearanceSubsectionOpen = false,
            ),
        )
    }
}
```

- [ ] **Step 2: Run the focused navigation test and verify contracts are missing**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.navigation.FirstTimeTutorialNavigationTest"
```

Expected: compilation fails because the destination and selector do not exist.

- [ ] **Step 3: Add the tutorial route and start selector**

Add `FirstTimeTutorialDestination` exactly as declared above and:

```kotlin
internal fun initialOrbitRoute(settings: AppSettings): String =
    if (settings.hasCompletedFirstTimeTutorial) {
        OrbitDestination.Home.route
    } else {
        FirstTimeTutorialDestination.route(isReplay = false)
    }
```

- [ ] **Step 4: Register the tutorial destination in `OrbitApp`**

Set:

```kotlin
startDestination = initialOrbitRoute(settings)
```

Register the Boolean argument and render `FirstTimeTutorialScreen`. Define the completion callback as:

```kotlin
if (isReplay) {
    navController.popBackStack()
} else {
    onSettingsChanged(settings.copy(hasCompletedFirstTimeTutorial = true))
    navController.navigate(OrbitDestination.Home.route) {
        popUpTo(navController.graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
```

Pass `navController::popBackStack` as `onReplayBack`. The screen's `leaving` guard makes Skip/final completion idempotent.

- [ ] **Step 5: Hide bottom navigation and use stable glass on the tutorial**

Extend `shouldShowFloatingBottomNavigation` with:

```kotlin
selectedRoute != FirstTimeTutorialDestination.Route
```

Keep the tutorial out of the live-glass top-level allowlist so `glassRenderingPolicyForRoute` returns `SoftOnly`.

Extend `BottomNavigationVisibilityTest` to assert the tutorial is hidden while all existing top-level routes retain their current results.

- [ ] **Step 6: Add the categorized Settings replay row**

Add `onOpenFirstTimeGuide: () -> Unit` to `SettingsScreen`, pass it into the System index, and place a `SettingsMenuRow` inside `SystemMenuCard` using an existing informational icon:

```kotlin
SettingsMenuRow(
    icon = Icons.Outlined.Info,
    title = stringResource(R.string.settings_first_time_guide_title),
    status = stringResource(R.string.settings_first_time_guide_status),
    onClick = onOpenFirstTimeGuide,
)
```

In `OrbitApp`, pass:

```kotlin
onOpenFirstTimeGuide = {
    navController.navigate(FirstTimeTutorialDestination.route(isReplay = true)) {
        launchSingleTop = true
    }
}
```

Returning from replay reveals the existing System settings index and never changes the completion Boolean.

- [ ] **Step 7: Run navigation and Settings-focused checks**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.navigation.FirstTimeTutorialNavigationTest" --tests "com.orbit.app.ui.navigation.BottomNavigationVisibilityTest" --tests "com.orbit.app.ui.screens.settings.*"
```

Expected: PASS.

- [ ] **Step 8: Compile both debug APKs**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest
```

Expected: PASS.

- [ ] **Step 9: Commit navigation and replay wiring when Git metadata is available**

```powershell
git add app/src/main/java/com/orbit/app/ui/navigation/OrbitDestination.kt app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt app/src/test/java/com/orbit/app/ui/navigation/FirstTimeTutorialNavigationTest.kt app/src/test/java/com/orbit/app/ui/navigation/BottomNavigationVisibilityTest.kt
git commit -m "feat: route first launch through tutorial"
```

---

### Task 5: Regression and privacy verification

**Files:**
- Review: every file changed in Tasks 1–4
- Update only if evidence requires it: focused tests or tutorial code

**Interfaces:**
- Verifies the complete feature; produces no new product behavior.

- [ ] **Step 1: Run the complete JVM suite**

```powershell
.\gradlew.bat --no-daemon :app:test
```

Expected: all debug and release JVM tests pass.

- [ ] **Step 2: Run debug build and lint**

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Expected: all tasks pass; inspect lint output for new tutorial-related warnings.

- [ ] **Step 3: Run the strict workplace-privacy checker**

```powershell
py -3 scripts/codex/check_workplace_privacy.py --strict
```

Expected: PASS. Semantically review every changed string, test fixture, filename, comment, and document even when the heuristic passes.

- [ ] **Step 4: Review changed paths without disturbing unrelated work**

If Git metadata is available:

```powershell
git status --short
git diff --check
git diff -- app/src/main/java/com/orbit/app/domain/model/AppSettings.kt app/src/main/java/com/orbit/app/data/repository/AppSettingsRepository.kt app/src/main/java/com/orbit/app/ui/LocalDataViewModel.kt app/src/main/java/com/orbit/app/MainActivity.kt app/src/main/java/com/orbit/app/ui/screens/tutorial app/src/main/java/com/orbit/app/ui/navigation app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt app/src/main/res app/src/test app/src/androidTest docs/superpowers
```

If Git metadata remains unavailable, re-read only the explicit files in this plan, run `rg` for the new resource/property names, and report that final Git diff inspection was unavailable.

- [ ] **Step 5: Perform or record the manual device matrix**

On a connected target, verify:

1. clean install opens page 1 before Home;
2. rotation preserves the page;
3. swipe and buttons reach all seven pages;
4. Skip and final completion open Home normally;
5. relaunch goes directly to Home;
6. Settings replay returns to Settings;
7. dark/light themes and English/Estonian/Russian render;
8. large text keeps controls reachable;
9. screen-reader focus order and selected progress state are understandable;
10. Home capture, centered input, centered bottom navigation, Brain Dump, and Ask LUMA remain available.

If no target is connected, list this matrix as the remaining manual check without claiming it passed.

- [ ] **Step 6: Create the final commit when Git metadata is available**

```powershell
git add app/src/main app/src/test app/src/androidTest docs/superpowers/specs/2026-07-30-first-time-user-tutorial-design.md docs/superpowers/plans/2026-07-30-first-time-user-tutorial.md
git commit -m "feat: add first-time user tutorial"
```

Do not run this step if `.git/HEAD` remains absent.
