# Optional Space Setup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a first-time user optionally choose a small set of suggested Spaces and add a custom Space during the existing tutorial, without changing any existing install's Spaces.

**Architecture:** A tutorial-owned ViewModel will expose selection state only when the tutorial is not replayed and the database currently has no Spaces. It will persist selected templates and custom names using the existing SpaceRepository, then report completion only after the inserts succeed. The existing database creation callback will be removed, so an empty new database remains empty until the user selects a Space.

**Tech Stack:** Kotlin, Jetpack Compose, Room, StateFlow, AndroidX lifecycle, Android resources.

## Global Constraints

- Existing Spaces are preserved; replay never creates or changes Spaces.
- The setup is optional: Skip and an empty selection complete the tutorial without creating a Space.
- Suggestions are localized at rendering time; persisted names are the user-visible selected names.
- No raw capture or internal processing record is shown or moved by this feature.
- Keep the existing seven tutorial pages and Back/Skip behavior.
- Add English, Estonian, and Russian resource values for new visible copy.

---

### Task 1: Replace database seeding with selectable templates

**Files:**
- Modify: `app/src/main/java/com/orbit/app/data/local/StarterSpaces.kt`
- Modify: `app/src/main/java/com/orbit/app/data/local/OrbitDatabase.kt`
- Modify: `app/src/main/java/com/orbit/app/OrbitApplication.kt`
- Test: `app/src/test/java/com/orbit/app/data/local/StarterSpacesTest.kt`

**Interfaces:**
- Produces: `StarterSpaces.templates: List<StarterSpaceTemplate>` with stable `key`, `icon`, and `colorAccent` values.
- Produces: `StarterSpaces.spaceFor(template, name, sortOrder, now): SpaceEntity`.

- [x] **Step 1: Write the failing template test**

```kotlin
assertEquals(listOf("personal", "work", "home", "health", "money", "learning"),
    StarterSpaces.templates.map { it.key })
assertEquals(StarterSpaces.templates.size, StarterSpaces.templates.map { it.icon }.size)
```

- [x] **Step 2: Run the focused test and verify it fails**

Run: `:app:testDebugUnitTest --tests com.orbit.app.data.local.StarterSpacesTest`

- [x] **Step 3: Implement templates and remove automatic insertion**

```kotlin
data class StarterSpaceTemplate(val key: String, val icon: String, val colorAccent: String)

fun spaceFor(template: StarterSpaceTemplate, name: String, sortOrder: Int, now: Long) =
    SpaceEntity(name = name, icon = template.icon, colorAccent = template.colorAccent,
        sortOrder = sortOrder, createdAt = now, updatedAt = now)
```

Remove `SeedStarterSpacesCallback` from the Room builder and remove the application startup query that only existed to trigger it.

- [x] **Step 4: Run the focused test and compile**

Run: `:app:testDebugUnitTest --tests com.orbit.app.data.local.StarterSpacesTest :app:compileDebugKotlin`

### Task 2: Add tutorial setup state and persistence

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/tutorial/TutorialSpaceSetupViewModel.kt`
- Test: `app/src/test/java/com/orbit/app/ui/screens/tutorial/TutorialSpaceSetupViewModelTest.kt`

**Interfaces:**
- Consumes: `SpaceRepository`, `StarterSpaces.templates`.
- Produces: `TutorialSpaceSetupUiState(canConfigure: Boolean, selectedTemplateKeys: Set<String>, customNames: List<String>, isSaving: Boolean)`.
- Produces: `toggleTemplate(key)`, `addCustomName(name)`, `removeCustomName(name)`, and `finish(onComplete)`.

- [x] **Step 1: Write failing behavior tests**

```kotlin
assertTrue(state.canConfigure) // empty database, first-time route
viewModel.toggleTemplate("home")
viewModel.addCustomName("Garden")
viewModel.finish { finished = true }
assertEquals(listOf("Home", "Garden"), storedSpaces.map { it.name })
assertTrue(finished)
```

Also assert that replay or a non-empty repository yields `canConfigure == false`, and that blank/duplicate custom names are ignored.

- [x] **Step 2: Run the focused test and verify it fails**

Run: `:app:testDebugUnitTest --tests com.orbit.app.ui.screens.tutorial.TutorialSpaceSetupViewModelTest`

- [x] **Step 3: Implement the ViewModel**

Observe SpaceRepository. Gate setup on `!isReplay && spaces.isEmpty()`. Keep selection and custom input in ViewModel state. On finish, use the current list, normalize custom names by trim/collapsed whitespace/case-insensitive deduplication, insert selected templates followed by custom folder Spaces with sequential sort order, then invoke `onComplete`. Empty selections invoke `onComplete` immediately. Do not invoke completion when an insert throws.

- [x] **Step 4: Run the focused test**

Run: `:app:testDebugUnitTest --tests com.orbit.app.ui.screens.tutorial.TutorialSpaceSetupViewModelTest`

### Task 3: Render the optional selector and wire navigation

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreen.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/navigation/OrbitApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-et/strings.xml`
- Modify: `app/src/main/res/values-ru/strings.xml`
- Test: `app/src/androidTest/java/com/orbit/app/ui/screens/tutorial/FirstTimeTutorialScreenTest.kt`

**Interfaces:**
- Consumes: `TutorialSpaceSetupUiState` and its callbacks.
- Produces: selectable Space suggestions and a custom-name text field only on the Spaces tutorial page when `canConfigure` is true.

- [x] **Step 1: Write the failing Compose semantics test**

```kotlin
composeRule.onNodeWithText("Choose a few Spaces").assertExists()
composeRule.onNodeWithText("Home").performClick()
composeRule.onNodeWithContentDescription("Add Space").performClick()
```

- [x] **Step 2: Run instrumentation assembly and verify the new test does not compile before implementation**

Run: `:app:assembleDebugAndroidTest`

- [x] **Step 3: Implement the selector**

Use the existing tutorial copy and glass surface. Render high-contrast selectable buttons with selected semantics, a labeled custom name field, and an Add button with a content description. Keep global Skip immediate and keep the final primary action disabled only while saving. Instantiate the ViewModel from the tutorial navigation entry and delegate final completion through its `finish` method.

- [x] **Step 4: Add localized copy and run affected checks**

Run: `:app:testDebugUnitTest --tests com.orbit.app.ui.screens.tutorial.TutorialSpaceSetupViewModelTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`

### Task 4: Review and document evidence

**Files:**
- Modify: `docs/codex/PROJECT_STATE.md` only when new evidence is actually collected.

- [x] **Step 1: Inspect the final changed paths; Git metadata is unavailable in this workspace.**

Confirm that no migration, export format, existing Space row, capture visibility, or replay behavior changed.

- [x] **Step 2: Run the strict privacy check**

Run: `python scripts/codex/check_workplace_privacy.py --strict`

- [x] **Step 3: Record exact evidence**

Document only commands that completed successfully, and retain connected-device tutorial interaction as a manual check if no target is available.
