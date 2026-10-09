# AI Settings Appearance Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restructure AI settings into an Appearance-style overview with focused subsections while preserving all existing AI controls and safeguards.

**Architecture:** `SettingsScreen` will own an `AiMenuSection?` navigation state beneath the existing System > AI entry. The AI overview reuses `SoftGlassSurface` and `SettingsMenuRow`; focused pages retain the current callback wiring and dialogs. The existing bottom-navigation callback will receive true for both AI overview and AI detail pages.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, JUnit 4, Gradle.

## Global Constraints

- Preserve local-only, Gemini-consent, key, connection-test, feature-toggle, and local-learning behavior exactly.
- Keep all AI settings inside System settings and keep the Appearance visual language.
- Do not expose internal AI records or alter persistence, models, or callbacks.
- Do not add production dependencies.
- Run the strict workplace-privacy checker before completion when a Python runtime is available.

---

### Task 1: Cover AI detail navigation as a settings subsection

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt:shouldShowFloatingBottomNavigation`
- Modify: `app/src/test/java/com/orbit/app/ui/navigation/BottomNavigationVisibilityTest.kt`

**Interfaces:**
- Consumes: `shouldShowFloatingBottomNavigation(imeVisible: Boolean, selectedRoute: String?, appearanceSubsectionOpen: Boolean): Boolean`
- Produces: the same function with a renamed `settingsSubsectionOpen` Boolean that hides the bottom navigation for Appearance, System, and AI detail flows.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun anySettingsSubsection_hidesBottomNavigationUntilReturn() {
    assertFalse(
        shouldShowFloatingBottomNavigation(
            imeVisible = false,
            selectedRoute = OrbitDestination.Settings.route,
            settingsSubsectionOpen = true,
        ),
    )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.ui.navigation.BottomNavigationVisibilityTest.anySettingsSubsection_hidesBottomNavigationUntilReturn`

Expected: compilation failure because `settingsSubsectionOpen` does not exist yet.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
internal fun shouldShowFloatingBottomNavigation(
    imeVisible: Boolean,
    selectedRoute: String?,
    settingsSubsectionOpen: Boolean,
): Boolean = !imeVisible &&
    selectedRoute != CalendarDestination.Route &&
    selectedRoute != SearchDestination.Route &&
    selectedRoute != ItemDetailDestination.Route &&
    selectedRoute != ReminderDestination.Route &&
    !settingsSubsectionOpen
```

Rename the matching call-site argument in `OrbitApp` without changing the Boolean value passed from `SettingsScreen`.

- [ ] **Step 4: Run the focused test to verify it passes**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.ui.navigation.BottomNavigationVisibilityTest`

Expected: PASS.

### Task 2: Add the AI overview and focused subsection navigation

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt:SettingsScreen, SystemMenuSection, AiSettingsCard`

**Interfaces:**
- Consumes: existing `AppSettings`, `AiSettingsUiState`, and AI callbacks supplied to `SettingsScreen`.
- Produces: `AiMenuSection` with `Mode`, `GeminiSetup`, `Features`, and `LocalLearning`; `AiSettingsSection` renders the overview or the selected focused page.

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun anySettingsSubsection_hidesBottomNavigationUntilReturn() {
    assertFalse(
        shouldShowFloatingBottomNavigation(
            imeVisible = false,
            selectedRoute = OrbitDestination.Settings.route,
            settingsSubsectionOpen = true,
        ),
    )
}
```

This existing focused navigation test represents the new AI-detail state before it is wired into `SettingsScreen`.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.ui.navigation.BottomNavigationVisibilityTest.anySettingsSubsection_hidesBottomNavigationUntilReturn`

Expected: FAIL before Task 1 renames the function parameter; after Task 1, it passes and protects the new state wiring.

- [ ] **Step 3: Write the minimal implementation**

```kotlin
private enum class AiMenuSection(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Mode(R.string.settings_ai_mode, R.string.settings_ai_subtitle, Icons.Filled.AutoAwesome),
    GeminiSetup(R.string.settings_gemini_key, R.string.settings_ai_subtitle, Icons.Filled.Tune),
    Features(R.string.settings_use_gemini_for, R.string.settings_ai_subtitle, Icons.Filled.AutoAwesome),
    LocalLearning(R.string.settings_local_learning_title, R.string.settings_ai_subtitle, Icons.Filled.Person),
}
```

Add `aiSubsection` state, a dedicated AI overview scroll state, AI-aware back handling, and header selection. Change System > AI to render an AI overview card when `aiSubsection` is null. Move the current groups into four focused composables without changing their callbacks, enablement conditions, dialogs, or text. Reuse `SettingsMenuRow`, `AppearanceCard`, existing dividers, and the existing `SettingsSubsectionHeader` pattern.

- [ ] **Step 4: Run focused compilation and tests**

Run: `./gradlew --no-daemon :app:testDebugUnitTest --tests com.orbit.app.ui.navigation.BottomNavigationVisibilityTest --tests com.orbit.app.ui.screens.settings.GeminiSettingsMessageTest`

Expected: PASS.

### Task 3: Verify the complete focused change

**Files:**
- Modify: `docs/superpowers/specs/2026-07-30-ai-settings-appearance-parity-design.md` only if implementation differs from approved behavior.

**Interfaces:**
- Consumes: completed Settings and navigation code.
- Produces: compile and focused-test evidence for the final UI change.

- [ ] **Step 1: Inspect the final diff**

Run: `git diff -- app/src/main/java/com/orbit/app/ui/screens/settings/SettingsScreen.kt app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt app/src/test/java/com/orbit/app/ui/navigation/BottomNavigationVisibilityTest.kt`

Expected: only AI-settings navigation/layout and associated naming/test changes.

- [ ] **Step 2: Run the relevant checks**

Run: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`

Expected: PASS.

- [ ] **Step 3: Run privacy verification**

Run: `python scripts/codex/check_workplace_privacy.py --strict`

Expected: PASS. If Python is unavailable, report the unavailable runtime and manually inspect changed text-bearing files.

- [ ] **Step 4: Manually inspect visual and interaction states**

Check System > AI overview and its four pages in light and dark modes, local-only and Gemini mode, no-key and saved-key states. Confirm focused-page Back returns to AI overview; the next Back returns to System; bottom navigation is hidden while an AI detail page is open.
