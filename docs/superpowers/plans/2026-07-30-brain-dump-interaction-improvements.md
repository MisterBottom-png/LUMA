# Brain Dump Interaction Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Brain Dump's dense action stack with a progressive confirmation flow that visibly confirms type, Space, and timing; supports skip-only Undo; keeps status inside the sheet; and preserves current persistence and exactly-once behavior.

**Architecture:** Add a pure Brain Dump interaction model and a small transient skip-Undo controller, both owned by `HomeCaptureViewModel`. Move Brain Dump-specific Compose content into a focused file while leaving the ordinary single-capture suggestion path intact. Continue using `BrainDumpActions` for every durable outcome; delayed Skip is transient and requires no Room schema or export-format change.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, StateFlow, ViewModel coroutines, Room, JUnit 4, AndroidX Compose UI tests, Android instrumentation, Java 17.

## Global Constraints

- Preserve raw-capture-first persistence and finalized-only user-facing collections.
- Preserve source order and exactly-once item handling.
- Preserve Home, Review, and capture-detail resume navigation.
- Preserve optional task timing, required reminder target time, and configured 24-hour behavior.
- Do not change Room schema version 5, export format version 3, AI routing, Brain Dump detection, or reminder scheduling contracts.
- Do not add production dependencies.
- Keep ordinary root dismissal resumable; never delete pending progress except through the confirmed discard-remaining action.
- Externalize every visible and semantic string in English, Estonian, and Russian.
- Use generic role labels only; do not introduce workplace-associated person references.
- Run `python scripts/codex/check_workplace_privacy.py --strict` after text-bearing changes when a Python runtime is available.
- The current workspace has nonfunctional Git metadata. Run each planned commit only after Git metadata is restored; otherwise record the intended commit without fabricating success.

---

## File Structure

### Create

- `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpInteractionState.kt`
  - Pure stages, drafts, completion counts, inline status, dismissal decisions, and metadata presentation inputs.
- `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoController.kt`
  - Owns one transient pending skip, expiry, flush, and Undo.
- `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinator.kt`
  - Owns the interaction state machine, commit ordering, retry request, inline status, and completion counts.
- `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContent.kt`
  - Brain Dump root card, editor, setup routing, More menu, inline status, and completion summary.
- `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpInteractionStateTest.kt`
  - Pure state, dirty-draft, dismissal, metadata, and completion-count tests.
- `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoControllerTest.kt`
  - Delayed skip, Undo, flush ordering, process-loss, and final-skip tests.
- `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinatorTest.kt`
  - End-to-end interaction transitions with an injected in-memory commit function.
- `app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt`
  - Compose visibility, action hierarchy, selection semantics, status, and accessibility tests.

### Modify

- `app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt`
  - Own interaction state, integrate skip Undo, convert action results into inline status and completion state, preserve retry intent.
- `app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt`
  - Pass the Brain Dump state and callbacks; keep Brain Dump intermediate messages out of the underlying Home snackbar.
- `app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt`
  - Route Brain Dump suggestions to the focused content and retain the ordinary single-capture path.
- `app/src/main/res/values/strings_localization.xml`
- `app/src/main/res/values-et/strings_localization.xml`
- `app/src/main/res/values-ru/strings_localization.xml`
  - Add all new visible and semantic copy and retire obsolete Brain Dump action wording from active use.
- `app/src/test/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheetTest.kt`
  - Retain ordinary-capture helpers and update shared setup expectations.
- `app/src/androidTest/java/com/orbit/app/domain/usecase/BrainDumpActionsRoomTest.kt`
  - Confirm delayed Skip still commits through the existing exactly-once boundary.
- `docs/codex/LUMA_REGRESSION_CHECKLIST.md`
  - Add the new interaction-specific regression cases after behavior exists.

---

### Task 1: Pure Brain Dump Interaction Model

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpInteractionState.kt`
- Create: `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpInteractionStateTest.kt`

**Interfaces:**
- Consumes: `BrainDumpSuggestion`, `SuggestedItemType`, `CaptureSpaceOption`.
- Produces:
  - `BrainDumpStage`
  - `BrainDumpDraft`
  - `BrainDumpCompletionCounts`
  - `BrainDumpStatus`
  - `BrainDumpStatusKind`
  - `BrainDumpDismissalDecision`
  - `BrainDumpInteractionState`
  - `initialBrainDumpDraft(...)`
  - `brainDumpDismissalDecision(...)`
  - `BrainDumpCompletionCounts.record(...)`

- [ ] **Step 1: Write failing state tests**

```kotlin
class BrainDumpInteractionStateTest {
    private val item = BrainDumpSuggestion(
        id = "brain:1",
        rawText = "Plan the appointment tomorrow at 1500",
        title = "Plan the appointment",
        suggestedType = SuggestedItemType.Task,
        suggestedSpaceName = "Personal",
        confidence = 0.9f,
        tinyNextAction = "Open the calendar",
        reason = "The thought is actionable.",
        suggestedReminderAt = 1_800_000_000_000L,
    )

    @Test
    fun initialDraftCarriesVisibleConfirmationMetadata() {
        val draft = initialBrainDumpDraft(
            item = item,
            spaces = listOf(
                CaptureSpaceOption(null, "Inbox"),
                CaptureSpaceOption(7L, "Personal"),
            ),
        )

        assertEquals("Plan the appointment", draft.title)
        assertEquals(SuggestedItemType.Task, draft.type)
        assertEquals(7L, draft.spaceId)
        assertEquals(1_800_000_000_000L, draft.scheduledAt)
    }

    @Test
    fun dirtyNestedDraftRequiresConfirmationButRootCloses() {
        val initial = BrainDumpDraft("Title", SuggestedItemType.Note, null, null)
        val changed = initial.copy(title = "Changed")

        assertEquals(
            BrainDumpDismissalDecision.ConfirmDiscard,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Edit,
                initialDraft = initial,
                draft = changed,
                actionInProgress = false,
            ),
        )
        assertEquals(
            BrainDumpDismissalDecision.CloseSession,
            brainDumpDismissalDecision(
                stage = BrainDumpStage.Suggestion,
                initialDraft = initial,
                draft = initial,
                actionInProgress = false,
            ),
        )
    }

    @Test
    fun completionCountsSeparateSavedInboxAndSkipped() {
        val counts = BrainDumpCompletionCounts()
            .record(BrainDumpCompletedOutcome.Saved)
            .record(BrainDumpCompletedOutcome.Saved)
            .record(BrainDumpCompletedOutcome.KeptInInbox)
            .record(BrainDumpCompletedOutcome.Skipped)

        assertEquals(2, counts.saved)
        assertEquals(1, counts.keptInInbox)
        assertEquals(1, counts.skipped)
    }
}
```

- [ ] **Step 2: Run the state tests and verify failure**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.screens.home.BrainDumpInteractionStateTest"
```

Expected: compilation fails because the interaction types and functions do not exist.

- [ ] **Step 3: Implement the minimal pure model**

```kotlin
internal enum class BrainDumpStage {
    Suggestion,
    Edit,
    TaskSetup,
    ReminderSetup,
    Completion,
}

internal enum class BrainDumpDismissalDecision {
    CloseSession,
    StepBack,
    ConfirmDiscard,
    Blocked,
}

internal enum class BrainDumpCompletedOutcome { Saved, KeptInInbox, Skipped }

internal enum class BrainDumpStatusKind { Success, PendingSkip, Warning, Error }

internal enum class BrainDumpStatusMessage {
    NoteSaved,
    TaskCreated,
    ReminderCreated,
    KeptInInbox,
    ThoughtSkipped,
    SaveFailed,
    NotificationAttention,
}

internal data class BrainDumpStatus(
    val kind: BrainDumpStatusKind,
    val message: BrainDumpStatusMessage,
    val canUndo: Boolean = false,
    val canRetry: Boolean = false,
)

internal data class BrainDumpDraft(
    val title: String,
    val type: SuggestedItemType,
    val spaceId: Long?,
    val scheduledAt: Long?,
)

internal data class BrainDumpCompletionCounts(
    val saved: Int = 0,
    val keptInInbox: Int = 0,
    val skipped: Int = 0,
) {
    fun record(outcome: BrainDumpCompletedOutcome): BrainDumpCompletionCounts = when (outcome) {
        BrainDumpCompletedOutcome.Saved -> copy(saved = saved + 1)
        BrainDumpCompletedOutcome.KeptInInbox -> copy(keptInInbox = keptInInbox + 1)
        BrainDumpCompletedOutcome.Skipped -> copy(skipped = skipped + 1)
    }
}

internal data class BrainDumpInteractionState(
    val stage: BrainDumpStage,
    val itemId: String?,
    val itemNumber: Int,
    val totalItems: Int,
    val initialDraft: BrainDumpDraft?,
    val draft: BrainDumpDraft?,
    val completionCounts: BrainDumpCompletionCounts,
    val status: BrainDumpStatus? = null,
    val actionInProgress: Boolean = false,
)

internal fun initialBrainDumpDraft(
    item: BrainDumpSuggestion,
    spaces: List<CaptureSpaceOption>,
): BrainDumpDraft = BrainDumpDraft(
    title = item.title,
    type = item.suggestedType.brainDumpEditableType(),
    spaceId = spaces.firstOrNull {
        it.name.equals(item.suggestedSpaceName, ignoreCase = true)
    }?.id,
    scheduledAt = item.suggestedReminderAt,
)

internal fun brainDumpDismissalDecision(
    stage: BrainDumpStage,
    initialDraft: BrainDumpDraft?,
    draft: BrainDumpDraft?,
    actionInProgress: Boolean,
): BrainDumpDismissalDecision = when {
    actionInProgress -> BrainDumpDismissalDecision.Blocked
    stage == BrainDumpStage.Suggestion || stage == BrainDumpStage.Completion ->
        BrainDumpDismissalDecision.CloseSession
    initialDraft != draft -> BrainDumpDismissalDecision.ConfirmDiscard
    else -> BrainDumpDismissalDecision.StepBack
}

internal fun SuggestedItemType.brainDumpEditableType(): SuggestedItemType = when (this) {
    SuggestedItemType.Note -> SuggestedItemType.Note
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> SuggestedItemType.Task
    SuggestedItemType.Reminder -> SuggestedItemType.Reminder
}
```

- [ ] **Step 4: Run the state tests and verify pass**

Run the Step 2 command.

Expected: `BrainDumpInteractionStateTest` passes.

- [ ] **Step 5: Commit the interaction model**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpInteractionState.kt app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpInteractionStateTest.kt
git commit -m "feat: model Brain Dump interaction states"
```

Expected: one focused commit, or a recorded Git-metadata blocker if this workspace still cannot commit.

---

### Task 2: Transient Skip Undo Controller

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoController.kt`
- Create: `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoControllerTest.kt`

**Interfaces:**
- Consumes:
  - `CoroutineScope`
  - `BrainDumpActionResult`
  - injected `commitSkip: suspend (captureId: Long, sourceKey: String) -> BrainDumpActionResult`
- Produces:
  - `PendingBrainDumpSkip`
  - `BrainDumpSkipCommit`
  - `BrainDumpSkipUndoController.pending`
  - `begin(...)`
  - `undo()`
  - `flush()`
  - `cancelWithoutCommit()`

- [ ] **Step 1: Write failing controller tests**

Use an injected suspension point so the test controls expiry without `kotlinx-coroutines-test`:

```kotlin
class BrainDumpSkipUndoControllerTest {
    @Test
    fun beginExposesPendingAndUndoPreventsCommit() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val commits = mutableListOf<Pair<Long, String>>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { captureId, sourceKey ->
                commits += captureId to sourceKey
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        assertEquals("brain:1", controller.pending.value?.sourceKey)

        assertTrue(controller.undo())
        gate.complete(Unit)
        yield()

        assertTrue(commits.isEmpty())
        assertNull(controller.pending.value)
    }

    @Test
    fun flushCommitsOnceBeforeTheNextAction() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var commitCount = 0
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ ->
                commitCount += 1
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        val first = controller.flush()
        val second = controller.flush()

        assertEquals(BrainDumpActionStatus.Applied, first?.result?.status)
        assertNull(second)
        assertEquals(1, commitCount)
    }

    @Test
    fun expiryCommitsFinalSkipAndReportsFinalFlag() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val commits = mutableListOf<BrainDumpSkipCommit>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ ->
                BrainDumpActionResult(
                    status = BrainDumpActionStatus.Applied,
                    sessionCompleted = true,
                )
            },
            onCommitted = commits::add,
        )

        controller.begin(7L, "brain:1", isFinalItem = true)
        gate.complete(Unit)
        yield()

        assertTrue(commits.single().pending.isFinalItem)
        assertTrue(commits.single().result.sessionCompleted)
    }

    @Test
    fun processLossBeforeFlushLeavesSkipUncommitted() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var commitCount = 0
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ ->
                commitCount += 1
                BrainDumpActionResult(BrainDumpActionStatus.Applied)
            },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        controller.cancelWithoutCommit()

        assertEquals(0, commitCount)
        assertNull(controller.pending.value)
    }

    @Test
    fun expiryFailureKeepsPendingSkipAndReportsFailure() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val failures = mutableListOf<String>()
        val controller = BrainDumpSkipUndoController(
            scope = this,
            expiryDelay = { gate.await() },
            commitSkip = { _, _ -> error("Injected skip failure") },
            onCommitFailed = { pending, _ -> failures += pending.sourceKey },
        )

        controller.begin(7L, "brain:1", isFinalItem = false)
        gate.complete(Unit)
        yield()

        assertEquals(listOf("brain:1"), failures)
        assertEquals("brain:1", controller.pending.value?.sourceKey)
    }
}
```

- [ ] **Step 2: Run the controller tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.screens.home.BrainDumpSkipUndoControllerTest"
```

Expected: compilation fails because the controller does not exist.

- [ ] **Step 3: Implement delayed, exactly-once flush behavior**

```kotlin
internal data class PendingBrainDumpSkip(
    val captureId: Long,
    val sourceKey: String,
    val isFinalItem: Boolean,
)

internal data class BrainDumpSkipCommit(
    val pending: PendingBrainDumpSkip,
    val result: BrainDumpActionResult,
)

internal class BrainDumpSkipUndoController(
    private val scope: CoroutineScope,
    private val expiryDelay: suspend (Long) -> Unit = { delay(it) },
    private val commitSkip: suspend (Long, String) -> BrainDumpActionResult,
    private val onCommitted: (BrainDumpSkipCommit) -> Unit = {},
    private val onCommitFailed: (PendingBrainDumpSkip, Throwable) -> Unit = { _, _ -> },
) {
    private val mutex = Mutex()
    private val _pending = MutableStateFlow<PendingBrainDumpSkip?>(null)
    val pending: StateFlow<PendingBrainDumpSkip?> = _pending.asStateFlow()
    private var expiryJob: Job? = null

    fun begin(captureId: Long, sourceKey: String, isFinalItem: Boolean) {
        check(_pending.value == null) { "Only one Brain Dump skip may be pending" }
        val value = PendingBrainDumpSkip(captureId, sourceKey, isFinalItem)
        _pending.value = value
        expiryJob = scope.launch {
            expiryDelay(UndoWindowMillis)
            expiryJob = null
            runCatching { flush() }
                .onSuccess { commit -> commit?.let(onCommitted) }
                .onFailure { failure ->
                    _pending.value?.let { pending -> onCommitFailed(pending, failure) }
                }
        }
    }

    fun undo(): Boolean {
        val existed = _pending.value != null
        expiryJob?.cancel()
        expiryJob = null
        _pending.value = null
        return existed
    }

    suspend fun flush(): BrainDumpSkipCommit? = mutex.withLock {
        val value = _pending.value ?: return@withLock null
        val scheduledExpiry = expiryJob
        expiryJob = null
        scheduledExpiry?.cancel()
        val result = commitSkip(value.captureId, value.sourceKey)
        _pending.value = null
        BrainDumpSkipCommit(value, result)
    }

    fun cancelWithoutCommit() {
        expiryJob?.cancel()
        expiryJob = null
        _pending.value = null
    }

    private companion object {
        const val UndoWindowMillis = 5_000L
    }
}
```

Ensure the expiry coroutine does not invoke `onCommitted` after Undo and that concurrent expiry/flush calls are serialized through the mutex.

- [ ] **Step 4: Run the controller tests and verify pass**

Run the Step 2 command.

Expected: all skip controller tests pass without real-time waiting.

- [ ] **Step 5: Commit the skip controller**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoController.kt app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoControllerTest.kt
git commit -m "feat: add reversible Brain Dump skip"
```

---

### Task 3: Flow Coordinator and ViewModel Integration

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinator.kt`
- Create: `app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinatorTest.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt`

**Interfaces:**
- Consumes:
  - Task 1 interaction types
  - Task 2 `BrainDumpSkipUndoController`
  - injected `commit: suspend (BrainDumpCommitRequest) -> BrainDumpActionResult`
- Produces:
  - `BrainDumpCommitRequest`
  - `BrainDumpFlowCoordinator.state: StateFlow<BrainDumpInteractionState?>`
  - `start(...)`
  - `edit()`
  - `updateDraft(BrainDumpDraft)`
  - `continueFromEditor()`
  - `stepBack()`
  - `discardDraftChanges()`
  - `commitPrimary()`
  - `keepInInbox()`
  - `skip()`
  - `undoSkip()`
  - `retry()`
  - `finishLater()`
  - `closeCompletion()`

- [ ] **Step 1: Write failing coordinator tests**

Use an in-memory list of commit requests; no Room or Android context is required:

```kotlin
class BrainDumpFlowCoordinatorTest {
    @Test
    fun skipAdvancesOptimisticallyAndUndoReturnsToSkippedItem() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            commits += request
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()

        assertEquals("brain:2", fixture.coordinator.state.value?.itemId)
        assertTrue(fixture.coordinator.state.value?.status?.canUndo == true)
        assertTrue(commits.isEmpty())

        fixture.coordinator.undoSkip()

        assertEquals("brain:1", fixture.coordinator.state.value?.itemId)
        assertTrue(commits.isEmpty())
    }

    @Test
    fun nextCommittedActionFlushesSkipBeforeSavingCurrentItem() = runBlocking {
        val commits = mutableListOf<BrainDumpCommitRequest>()
        val fixture = coordinatorFixture(itemCount = 2) { request ->
            commits += request
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = commits.size == 2,
            )
        }

        fixture.coordinator.skip()
        fixture.coordinator.commitPrimary()

        assertTrue(commits[0] is BrainDumpCommitRequest.Skip)
        assertTrue(commits[1] is BrainDumpCommitRequest.SaveNote)
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
    }

    @Test
    fun failedCommitKeepsDraftAndExposesRetry() = runBlocking {
        var fail = true
        val fixture = coordinatorFixture(itemCount = 1) {
            if (fail) error("Injected write failure")
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = true,
            )
        }
        val edited = requireNotNull(fixture.coordinator.state.value?.draft)
            .copy(title = "Edited title")
        fixture.coordinator.updateDraft(edited)

        fixture.coordinator.commitPrimary()

        assertEquals("Edited title", fixture.coordinator.state.value?.draft?.title)
        assertEquals(BrainDumpStatusKind.Error, fixture.coordinator.state.value?.status?.kind)
        assertTrue(fixture.coordinator.state.value?.status?.canRetry == true)

        fail = false
        fixture.coordinator.retry()
        assertEquals(BrainDumpStage.Completion, fixture.coordinator.state.value?.stage)
    }

    @Test
    fun finishLaterFlushesPendingSkipBeforeClosingResumableSession() = runBlocking {
        val events = mutableListOf<String>()
        val fixture = coordinatorFixture(
            itemCount = 2,
            onClose = { resumable -> events += "close:$resumable" },
        ) { request ->
            events += if (request is BrainDumpCommitRequest.Skip) "Skip" else "Other"
            BrainDumpActionResult(BrainDumpActionStatus.Applied)
        }

        fixture.coordinator.skip()
        fixture.coordinator.finishLater()

        assertEquals(listOf("Skip", "close:true"), events)
    }
}

private data class CoordinatorFixture(
    val coordinator: BrainDumpFlowCoordinator,
)

private fun CoroutineScope.coordinatorFixture(
    itemCount: Int,
    onClose: (Boolean) -> Unit = {},
    commit: suspend (BrainDumpCommitRequest) -> BrainDumpActionResult,
): CoordinatorFixture {
    val items = (1..itemCount).map { ordinal ->
        BrainDumpSuggestion(
            id = "brain:$ordinal",
            rawText = "thought $ordinal",
            title = "Thought $ordinal",
            suggestedType = SuggestedItemType.Note,
            suggestedSpaceName = "Inbox",
            confidence = 0.8f,
            tinyNextAction = "Review it",
            reason = "This reads like something to keep.",
        )
    }
    val coordinator = BrainDumpFlowCoordinator(
        scope = this,
        expiryDelay = { suspendCancellableCoroutine<Unit> { } },
        commit = commit,
        onClose = onClose,
    )
    coordinator.start(
        captureId = 7L,
        items = items,
        spaces = listOf(CaptureSpaceOption(null, "Inbox")),
        storedOutcomes = items.associate { it.id to BrainDumpItemOutcome.Pending },
    )
    return CoordinatorFixture(coordinator)
}
```

- [ ] **Step 2: Run the coordinator tests and verify failure**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.orbit.app.ui.screens.home.BrainDumpFlowCoordinatorTest"
```

Expected: compilation fails because the coordinator and commit requests do not exist.

- [ ] **Step 3: Define exact commit requests**

```kotlin
internal sealed interface BrainDumpCommitRequest {
    val captureId: Long
    val sourceKey: String

    data class SaveNote(
        override val captureId: Long,
        override val sourceKey: String,
        val draft: BrainDumpDraft,
    ) : BrainDumpCommitRequest

    data class SaveTask(
        override val captureId: Long,
        override val sourceKey: String,
        val draft: BrainDumpDraft,
    ) : BrainDumpCommitRequest

    data class SaveReminder(
        override val captureId: Long,
        override val sourceKey: String,
        val draft: BrainDumpDraft,
    ) : BrainDumpCommitRequest

    data class KeepInInbox(
        override val captureId: Long,
        override val sourceKey: String,
    ) : BrainDumpCommitRequest

    data class Skip(
        override val captureId: Long,
        override val sourceKey: String,
    ) : BrainDumpCommitRequest
}
```

The request copies all user-edited values needed for exact Retry. It never contains a mutable UI reference.

- [ ] **Step 4: Implement the coordinator boundary**

```kotlin
internal class BrainDumpFlowCoordinator(
    private val scope: CoroutineScope,
    private val expiryDelay: suspend (Long) -> Unit = { delay(it) },
    private val commit: suspend (BrainDumpCommitRequest) -> BrainDumpActionResult,
    private val onClose: (resumable: Boolean) -> Unit = {},
) {
    private val _state = MutableStateFlow<BrainDumpInteractionState?>(null)
    val state: StateFlow<BrainDumpInteractionState?> = _state.asStateFlow()

    private var captureId: Long = 0L
    private var items: List<BrainDumpSuggestion> = emptyList()
    private var spaces: List<CaptureSpaceOption> = emptyList()
    private var storedOutcomes: MutableMap<String, BrainDumpItemOutcome> = linkedMapOf()
    private var retryRequest: BrainDumpCommitRequest? = null
    private var optimisticallySkippedSourceKey: String? = null
    private lateinit var skipController: BrainDumpSkipUndoController

    fun start(
        captureId: Long,
        items: List<BrainDumpSuggestion>,
        spaces: List<CaptureSpaceOption>,
        storedOutcomes: Map<String, BrainDumpItemOutcome>,
    ) {
        require(captureId > 0L)
        require(items.isNotEmpty())
        this.captureId = captureId
        this.items = items
        this.spaces = spaces
        this.storedOutcomes = storedOutcomes.toMutableMap()
        this.skipController = BrainDumpSkipUndoController(
            scope = scope,
            expiryDelay = expiryDelay,
            commitSkip = { committedCaptureId, sourceKey ->
                commit(BrainDumpCommitRequest.Skip(committedCaptureId, sourceKey))
            },
            onCommitted = ::onSkipCommitted,
            onCommitFailed = ::onSkipCommitFailed,
        )
        showFirstPendingItem()
    }
}
```

Keep all mutable flow mechanics inside this class. Expose immutable state and explicit event methods only.

- [ ] **Step 5: Implement coordinator initialization and navigation**

`start(...)` stores source-ordered items, derives completion counts from `storedOutcomes`, and selects the first pending item. Implement navigation as explicit stage changes:

```kotlin
fun continueFromEditor() {
    val current = requireNotNull(_state.value)
    val draft = requireNotNull(current.draft)
    _state.value = current.copy(
        stage = when (draft.type) {
            SuggestedItemType.Note -> BrainDumpStage.Edit
            SuggestedItemType.Task, SuggestedItemType.MondayItem -> BrainDumpStage.TaskSetup
            SuggestedItemType.Reminder -> BrainDumpStage.ReminderSetup
        },
    )
    if (draft.type == SuggestedItemType.Note) {
        scope.launch { commitPrimary() }
    }
}
```

`stepBack()` moves Task/Reminder setup to Edit and Edit to Suggestion. `discardDraftChanges()` restores `initialDraft` before moving back.

- [ ] **Step 6: Implement primary, Inbox, and Retry commits**

Build the request from the immutable current item ID and copied draft:

```kotlin
suspend fun commitPrimary() {
    if (!flushPendingSkip()) return
    val current = requireNotNull(_state.value)
    val draft = requireNotNull(current.draft)
    val request = when (draft.type) {
        SuggestedItemType.Note -> BrainDumpCommitRequest.SaveNote(captureId, currentItemId(), draft)
        SuggestedItemType.Task, SuggestedItemType.MondayItem ->
            BrainDumpCommitRequest.SaveTask(captureId, currentItemId(), draft)
        SuggestedItemType.Reminder ->
            BrainDumpCommitRequest.SaveReminder(captureId, currentItemId(), draft)
    }
    applyCommit(request)
}
```

`applyCommit` sets `actionInProgress`, catches failures, preserves the draft, stores the exact request as `retryRequest`, and exposes `BrainDumpStatus(Error, SaveFailed, canRetry = true)`. Success records the correct completion outcome and advances or enters Completion.

If `BrainDumpActionResult.notificationScheduled == false`, treat a created reminder as successful, advance exactly once, and expose `BrainDumpStatus(Warning, NotificationAttention)` without a retry action.

- [ ] **Step 7: Integrate optimistic Skip and Undo**

`skip()` starts `BrainDumpSkipUndoController`, advances locally, and sets pending-skip status without calling `commit`. `undoSkip()` cancels the controller and restores the source-keyed item. `flushPendingSkip()` commits `BrainDumpCommitRequest.Skip` before any later durable action or close.

If Skip flush fails, restore the skipped item, keep it pending, store a retryable Skip request, and do not execute the following action.

- [ ] **Step 8: Implement final-item Skip and completion**

When final Skip is pending, enter Completion immediately with Undo still visible. Expiry or Close flushes Skip, increments the skipped count, and retains Completion. Undo returns to the final item.

`finishLater()` flushes a preceding pending Skip, then invokes an injected `onClose(resumable = true)`. `closeCompletion()` flushes final Skip and invokes `onClose(resumable = false)`.

- [ ] **Step 9: Run coordinator tests and verify pass**

Run the Step 2 command.

Expected: coordinator tests pass without Android, Room, or real-time delay.

- [ ] **Step 10: Wire the coordinator into `HomeCaptureViewModel`**

Add this property to the existing `HomeCaptureUiState` primary constructor without changing its other properties:

```kotlin
val brainDumpInteraction: BrainDumpInteractionState? = null,
```

Create the coordinator with `viewModelScope`. Map commit requests to existing `BrainDumpActions`:

```kotlin
private suspend fun commitBrainDumpRequest(
    request: BrainDumpCommitRequest,
): BrainDumpActionResult = when (request) {
    is BrainDumpCommitRequest.SaveNote -> brainDumpActions.saveNote(
        request.captureId,
        request.sourceKey,
        request.draft.title,
        request.draft.spaceId,
    )
    is BrainDumpCommitRequest.SaveTask -> brainDumpActions.saveTask(
        request.captureId,
        request.sourceKey,
        request.draft.title,
        request.draft.scheduledAt,
        request.draft.spaceId,
    )
    is BrainDumpCommitRequest.SaveReminder -> brainDumpActions.saveReminder(
        request.captureId,
        request.sourceKey,
        request.draft.title,
        requireNotNull(request.draft.scheduledAt),
        request.draft.spaceId,
    )
    is BrainDumpCommitRequest.KeepInInbox -> brainDumpActions.saveOriginalLineForLater(
        request.captureId,
        request.sourceKey,
    )
    is BrainDumpCommitRequest.Skip -> brainDumpActions.skip(
        request.captureId,
        request.sourceKey,
    )
}
```

Collect coordinator state into `HomeCaptureUiState.brainDumpInteraction`. Start it from `loadBrainDumpSuggestion(...)` with every stored outcome. On close, update `ActiveBrainDumpCaptureIdKey`, `suggestion`, and `brainDumpHandledItemIds` consistently.

Keep learning-event recording after `Applied` commits. Do not record an optimistic Skip before it is durably applied.

Both Finish later and completion Close clear `ActiveBrainDumpCaptureIdKey`. Finish later leaves the Room session intact; completion Close runs only after the final outcome has been committed. A created reminder still triggers the existing notification-permission request. A missing session clears coordinator state, closes the sheet, and uses the existing user-facing unavailable message on Home.

- [ ] **Step 11: Run coordinator and existing focused tests**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.orbit.app.ui.screens.home.BrainDumpFlowCoordinatorTest" `
  --tests "com.orbit.app.ui.screens.home.BrainDumpSkipUndoControllerTest" `
  --tests "com.orbit.app.ui.screens.home.CaptureSuggestionSheetTest" `
  --tests "com.orbit.app.ui.screens.home.HomeCaptureCancellationTest"
```

Expected: all focused JVM tests pass.

- [ ] **Step 12: Commit the coordinated behavior**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinator.kt app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinatorTest.kt
git commit -m "feat: coordinate Brain Dump progress and feedback"
```

---

### Task 4: Progressive Brain Dump Compose Content

**Files:**
- Create: `app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContent.kt`
- Create: `app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt`
- Modify: `app/src/main/res/values/strings_localization.xml`
- Modify: `app/src/main/res/values-et/strings_localization.xml`
- Modify: `app/src/main/res/values-ru/strings_localization.xml`

**Interfaces:**
- Consumes:
  - `CaptureSuggestion`
  - `BrainDumpInteractionState`
  - `OrbitTimeFormat`
- Produces:
  - `BrainDumpSuggestionContent(...)`
  - `BrainDumpMetadataSummary(...)`
  - `BrainDumpEditor(...)`
  - `BrainDumpActionMenu(...)`
  - `BrainDumpInlineStatus(...)`
  - `BrainDumpCompletionSummary(...)`

- [ ] **Step 1: Add English, Estonian, and Russian copy**

Add the following English keys to `values/strings_localization.xml`:

```xml
<string name="core_brain_dump_progress">Suggestion %1$d of %2$d</string>
<string name="core_brain_dump_more">More Brain Dump actions</string>
<string name="core_brain_dump_why_this">Why this?</string>
<string name="core_brain_dump_hide_why">Hide explanation</string>
<string name="core_brain_dump_keep_thought">Keep this thought in Inbox</string>
<string name="core_brain_dump_skip_thought">Skip this thought</string>
<string name="core_brain_dump_finish_later">Finish later</string>
<string name="core_brain_dump_discard_edits_title">Discard current edits?</string>
<string name="core_brain_dump_discard_edits_body">Your changes to this suggestion will be lost. Saved Brain Dump progress will stay.</string>
<string name="core_brain_dump_discard_edits">Discard edits</string>
<string name="core_brain_dump_thoughts_sorted">Thoughts sorted</string>
<string name="core_brain_dump_saved_count">%1$d saved</string>
<string name="core_brain_dump_inbox_count">%1$d kept in Inbox</string>
<string name="core_brain_dump_skipped_count">%1$d skipped</string>
<string name="core_brain_dump_undo">Undo</string>
<string name="core_brain_dump_retry">Retry</string>
<string name="core_brain_dump_status_skipped">Skipped this thought</string>
<string name="core_brain_dump_space_value">Space: %1$s</string>
<string name="core_brain_dump_type_value">Type: %1$s</string>
```

Add the following Estonian keys to `values-et/strings_localization.xml`:

```xml
<string name="core_brain_dump_progress">Soovitus %1$d / %2$d</string>
<string name="core_brain_dump_more">Rohkem mõtete korrastamise toiminguid</string>
<string name="core_brain_dump_why_this">Miks see?</string>
<string name="core_brain_dump_hide_why">Peida selgitus</string>
<string name="core_brain_dump_keep_thought">Hoia see mõte postkastis</string>
<string name="core_brain_dump_skip_thought">Jäta see mõte vahele</string>
<string name="core_brain_dump_finish_later">Lõpeta hiljem</string>
<string name="core_brain_dump_discard_edits_title">Kas hüljata praegused muudatused?</string>
<string name="core_brain_dump_discard_edits_body">Selle soovituse muudatused lähevad kaotsi. Salvestatud mõtete korrastamise edenemine jääb alles.</string>
<string name="core_brain_dump_discard_edits">Hülga muudatused</string>
<string name="core_brain_dump_thoughts_sorted">Mõtted korrastatud</string>
<string name="core_brain_dump_saved_count">%1$d salvestatud</string>
<string name="core_brain_dump_inbox_count">%1$d postkastis</string>
<string name="core_brain_dump_skipped_count">%1$d vahele jäetud</string>
<string name="core_brain_dump_undo">Võta tagasi</string>
<string name="core_brain_dump_retry">Proovi uuesti</string>
<string name="core_brain_dump_status_skipped">Mõte jäeti vahele</string>
<string name="core_brain_dump_space_value">Koht: %1$s</string>
<string name="core_brain_dump_type_value">Tüüp: %1$s</string>
```

Add the following Russian keys to `values-ru/strings_localization.xml`:

```xml
<string name="core_brain_dump_progress">Предложение %1$d из %2$d</string>
<string name="core_brain_dump_more">Другие действия с мыслями</string>
<string name="core_brain_dump_why_this">Почему так?</string>
<string name="core_brain_dump_hide_why">Скрыть объяснение</string>
<string name="core_brain_dump_keep_thought">Сохранить эту мысль во Входящих</string>
<string name="core_brain_dump_skip_thought">Пропустить эту мысль</string>
<string name="core_brain_dump_finish_later">Продолжить позже</string>
<string name="core_brain_dump_discard_edits_title">Отменить текущие изменения?</string>
<string name="core_brain_dump_discard_edits_body">Изменения этого предложения будут потеряны. Сохранённый прогресс разбора мыслей останется.</string>
<string name="core_brain_dump_discard_edits">Отменить изменения</string>
<string name="core_brain_dump_thoughts_sorted">Мысли разобраны</string>
<string name="core_brain_dump_saved_count">Сохранено: %1$d</string>
<string name="core_brain_dump_inbox_count">Во Входящих: %1$d</string>
<string name="core_brain_dump_skipped_count">Пропущено: %1$d</string>
<string name="core_brain_dump_undo">Отменить</string>
<string name="core_brain_dump_retry">Повторить</string>
<string name="core_brain_dump_status_skipped">Мысль пропущена</string>
<string name="core_brain_dump_space_value">Место: %1$s</string>
<string name="core_brain_dump_type_value">Тип: %1$s</string>
```

- [ ] **Step 2: Write failing Compose tests for the root card and More menu**

```kotlin
@RunWith(AndroidJUnit4::class)
class BrainDumpSuggestionContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rootCardShowsConfirmationMetadataAndProgressiveActions() {
        composeRule.setContent {
            OrbitTheme {
                BrainDumpSuggestionContent(
                    suggestion = suggestion(type = SuggestedItemType.Task, spaceName = "Personal"),
                    state = interactionState(itemNumber = 2, totalItems = 6),
                    timeFormat = OrbitTimeFormat(uses24HourClock = true),
                    callbacks = recordingCallbacks(),
                )
            }
        }

        composeRule.onNodeWithText("Suggestion 2 of 6").assertIsDisplayed()
        composeRule.onNodeWithText("Task").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithText("Set up task").assertIsDisplayed()
        composeRule.onNodeWithText("Edit details").assertIsDisplayed()
        composeRule.onNodeWithText("Finish later").assertIsDisplayed()
        composeRule.onNodeWithText("Keep this thought in Inbox").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("More Brain Dump actions").performClick()
        composeRule.onNodeWithText("Keep this thought in Inbox").assertIsDisplayed()
        composeRule.onNodeWithText("Skip this thought").assertIsDisplayed()
        composeRule.onNodeWithText("Discard remaining suggestions").assertIsDisplayed()
    }
}
```

- [ ] **Step 3: Implement the root suggestion card**

Use one vertical hierarchy:

```kotlin
@Composable
internal fun BrainDumpSuggestionContent(
    suggestion: CaptureSuggestion,
    state: BrainDumpInteractionState,
    timeFormat: OrbitTimeFormat,
    callbacks: BrainDumpCallbacks,
    modifier: Modifier = Modifier,
) {
    val item = state.itemId?.let { itemId ->
        suggestion.analysis.brainDumpItems.firstOrNull { it.id == itemId }
    }
    when (state.stage) {
        BrainDumpStage.Suggestion -> BrainDumpSuggestionCard(
            item = requireNotNull(item),
            state = state,
            spaces = suggestion.spaceOptions,
            timeFormat = timeFormat,
            callbacks = callbacks,
            modifier = modifier,
        )
        BrainDumpStage.Edit -> BrainDumpEditor(
            item = requireNotNull(item),
            draft = requireNotNull(state.draft),
            spaces = suggestion.spaceOptions,
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            onDraftChanged = callbacks.onDraftChanged,
            onContinue = callbacks.onContinueFromEditor,
            onBack = callbacks.onStepBack,
        )
        BrainDumpStage.TaskSetup -> BrainDumpScheduleSetup(
            type = SuggestedItemType.Task,
            draft = requireNotNull(state.draft),
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            onDraftChanged = callbacks.onDraftChanged,
            onConfirm = callbacks.onPrimaryAction,
            onBack = callbacks.onStepBack,
        )
        BrainDumpStage.ReminderSetup -> BrainDumpScheduleSetup(
            type = SuggestedItemType.Reminder,
            draft = requireNotNull(state.draft),
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            onDraftChanged = callbacks.onDraftChanged,
            onConfirm = callbacks.onPrimaryAction,
            onBack = callbacks.onStepBack,
        )
        BrainDumpStage.Completion -> BrainDumpCompletionSummary(
            counts = state.completionCounts,
            status = state.status,
            onUndo = callbacks.onUndoSkip,
            onClose = callbacks.onCloseCompletion,
        )
    }
}
```

Define `BrainDumpCallbacks` as explicit lambdas. Do not pass the ViewModel into the composable:

```kotlin
internal data class BrainDumpCallbacks(
    val onPrimaryAction: () -> Unit,
    val onEdit: () -> Unit,
    val onDraftChanged: (BrainDumpDraft) -> Unit,
    val onContinueFromEditor: () -> Unit,
    val onStepBack: () -> Unit,
    val onKeepInInbox: () -> Unit,
    val onSkip: () -> Unit,
    val onUndoSkip: () -> Unit,
    val onRetry: () -> Unit,
    val onFinishLater: () -> Unit,
    val onDiscardRemaining: () -> Unit,
    val onCloseCompletion: () -> Unit,
)
```

- [ ] **Step 4: Implement visible metadata and Why-this disclosure**

`BrainDumpMetadataSummary` must show:

- localized type;
- localized selected Space, including Inbox;
- formatted schedule only when non-null.

The raw source line and explanation remain collapsed until `Why this?` is selected.

- [ ] **Step 5: Implement the More menu and destructive confirmation**

Use `DropdownMenu` for Keep, Skip, and Discard remaining. Keep and Skip execute directly. Discard remaining first opens the existing destructive confirmation with updated copy.

- [ ] **Step 6: Implement the nested editor with selected semantics**

Use Material 3 `FilterChip` or `SingleChoiceSegmentedButtonRow` for Note, Task, and Reminder. Assert selected state through standard Compose semantics:

```kotlin
FilterChip(
    selected = draft.type == type,
    onClick = { callbacks.onDraftChanged(draft.copy(type = type)) },
    label = { Text(stringResource(type.labelRes())) },
)
```

Use one clickable Space row showing its current value. Open a dedicated modal selection surface rather than an unbounded `FlowRow`.

- [ ] **Step 7: Implement task and reminder schedule setup**

Use the draft passed from Edit without resetting title or Space. Task setup permits a null schedule and offers Add, Change, and Remove timing actions. Reminder setup requires a valid target time, shows visible guidance when time is missing, and keeps confirmation disabled until title and time are valid. Reuse `OrbitTimeFormat` and the existing date/time picker behavior so 24-hour configuration is preserved.

- [ ] **Step 8: Implement inline status and completion summary**

Apply a polite live region:

```kotlin
Modifier.semantics {
    liveRegion = LiveRegionMode.Polite
}
```

Pending Skip shows Undo. Error shows Retry. Warning has no item-creation retry. Completion shows saved, Inbox, and skipped counts plus Close.

- [ ] **Step 9: Run Compose compilation and focused instrumentation build**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
```

Expected: the new test APK compiles. If a connected test device is available, additionally run:

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.orbit.app.ui.screens.home.BrainDumpSuggestionContentTest
```

Expected: all Brain Dump Compose tests pass.

- [ ] **Step 10: Commit the progressive content**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContent.kt app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt app/src/main/res
git commit -m "feat: simplify Brain Dump confirmation"
```

---

### Task 5: Sheet Routing, Dismissal, Scroll, and Focus

**Files:**
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt`
- Modify: `app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt`
- Modify: `app/src/test/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheetTest.kt`
- Modify: `app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt`

**Interfaces:**
- Consumes:
  - `BrainDumpInteractionState`
  - `BrainDumpCallbacks`
  - `brainDumpDismissalDecision(...)`
- Produces:
  - unified `onBrainDumpDismissRequested()`
  - scroll reset on item ID
  - focus request on item ID
  - dirty-edit confirmation

- [ ] **Step 1: Write failing dismissal and item-transition tests**

Extend pure and Compose tests:

```kotlin
@Test
fun actionInProgressBlocksEveryDismissalPath() {
    val draft = BrainDumpDraft("Title", SuggestedItemType.Note, null, null)
    assertEquals(
        BrainDumpDismissalDecision.Blocked,
        brainDumpDismissalDecision(
            stage = BrainDumpStage.Edit,
            initialDraft = draft,
            draft = draft,
            actionInProgress = true,
        ),
    )
}
```

Compose test:

```kotlin
@Test
fun changingItemRequestsFocusForTheNewProgressHeading() {
    val state = mutableStateOf(interactionState(itemId = "brain:1", itemNumber = 1))
    composeRule.setContent {
        BrainDumpSuggestionContent(
            suggestion = suggestionWithTwoItems(),
            state = state.value,
            timeFormat = OrbitTimeFormat(true),
            callbacks = recordingCallbacks(),
        )
    }

    state.value = interactionState(itemId = "brain:2", itemNumber = 2)
    composeRule.waitForIdle()

    composeRule.onNodeWithText("Suggestion 2 of 2").assertIsFocused()
}
```

- [ ] **Step 2: Route Brain Dump separately from ordinary capture**

Keep `CaptureSuggestionSheet` as the modal owner. Replace the old private `BrainDumpReview` branch with `BrainDumpSuggestionContent`, while leaving `SuggestedActions`, ordinary `TaskSetup`, and ordinary `ReminderSetup` behavior unchanged.

- [ ] **Step 3: Unify Back, swipe, and scrim through one dismissal callback**

`onDismissRequest` and `BackHandler` both call a single callback that applies:

```kotlin
when (brainDumpDismissalDecision(...)) {
    BrainDumpDismissalDecision.CloseSession -> callbacks.onFinishLater()
    BrainDumpDismissalDecision.StepBack -> callbacks.onStepBack()
    BrainDumpDismissalDecision.ConfirmDiscard -> showDiscardEditsConfirmation = true
    BrainDumpDismissalDecision.Blocked -> Unit
}
```

Confirming discard restores the initial draft and returns one stage. Cancelling the confirmation leaves the editor unchanged.

- [ ] **Step 4: Reset scroll and request focus on immutable item ID**

Create the scroll and focus objects once:

```kotlin
val scrollState = rememberScrollState()
val progressFocusRequester = remember { FocusRequester() }

LaunchedEffect(state.itemId) {
    if (state.itemId != null) {
        scrollState.scrollTo(0)
        progressFocusRequester.requestFocus()
    }
}
```

Attach `progressFocusRequester` and `heading()` semantics to the progress label. Respect reduced motion; do not animate scroll on item replacement.
Make the heading programmatically focusable with `focusRequester(progressFocusRequester).focusable()` so the request is effective without adding a click action.

- [ ] **Step 5: Keep Brain Dump feedback out of the Home snackbar**

`HomeScreen` continues displaying ordinary capture messages in its snackbar. Brain Dump intermediate success, warning, error, Undo, and Retry state is rendered only by `BrainDumpInlineStatus`. Final missing-session feedback may use Home snackbar after the modal closes.

- [ ] **Step 6: Run focused JVM and instrumentation compilation**

Run:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.orbit.app.ui.screens.home.BrainDumpInteractionStateTest" `
  --tests "com.orbit.app.ui.screens.home.BrainDumpSkipUndoControllerTest" `
  --tests "com.orbit.app.ui.screens.home.BrainDumpFlowCoordinatorTest" `
  --tests "com.orbit.app.ui.screens.home.CaptureSuggestionSheetTest"
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
```

Expected: focused unit tests pass and the instrumentation APK compiles.

- [ ] **Step 7: Commit routing and accessibility behavior**

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt app/src/test/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheetTest.kt app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt
git commit -m "fix: align Brain Dump dismissal and focus"
```

---

### Task 6: Data-Safety Regression and Documentation

**Files:**
- Modify: `app/src/androidTest/java/com/orbit/app/domain/usecase/BrainDumpActionsRoomTest.kt`
- Modify: `docs/codex/LUMA_REGRESSION_CHECKLIST.md`

**Interfaces:**
- Consumes: existing `BrainDumpActions.skip(...)` and the final ViewModel flush path.
- Produces: evidence that the new transient layer does not weaken Room exactly-once behavior.

- [ ] **Step 1: Add the Room regression for delayed repeated Skip**

```kotlin
@Test
fun delayedSkipFlushRemainsExactlyOnce() = runBlocking {
    val captureId = seedSession()

    val first = actions.skip(captureId, "brain:1")
    val repeated = actions.skip(captureId, "brain:1")

    assertEquals(BrainDumpActionStatus.Applied, first.status)
    assertEquals(BrainDumpActionStatus.AlreadyHandled, repeated.status)
    assertEquals(
        BrainDumpItemOutcome.Skipped,
        database.brainDumpDao().getItem(captureId, "brain:1")?.outcome,
    )
}
```

- [ ] **Step 2: Run instrumentation compilation and the Room test when a target is available**

Run:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
```

With a connected target:

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.orbit.app.domain.usecase.BrainDumpActionsRoomTest
```

Expected: exactly-once tests pass. If no target exists, report compilation as complete and execution as pending.

- [ ] **Step 3: Update the regression checklist**

Add:

```markdown
- The default Brain Dump card visibly confirms type, Space, and applicable timing.
- Keep in Inbox, Skip, and Discard remaining remain behind More; only Discard remaining requires destructive confirmation.
- Skip advances immediately, offers Undo, and commits at most once on expiry, next action, or close.
- Root dismissal saves progress; nested dismissal steps back and confirms before discarding modified fields.
- Each new item resets scroll and accessibility focus to `Suggestion N of M`.
- Intermediate success, warning, failure, Retry, and Undo remain visible inside the modal.
- Completion shows saved, Inbox, and skipped counts without exposing raw source text.
```

- [ ] **Step 4: Run strict workplace privacy checking**

Run:

```powershell
python scripts/codex/check_workplace_privacy.py --strict
```

Expected: PASS. If Python is still unavailable, report the tool blocker and complete a semantic review of every changed text surface without claiming the automated check ran.

- [ ] **Step 5: Commit regression evidence**

```powershell
git add app/src/androidTest/java/com/orbit/app/domain/usecase/BrainDumpActionsRoomTest.kt docs/codex/LUMA_REGRESSION_CHECKLIST.md
git commit -m "test: protect Brain Dump interaction flow"
```

---

### Task 7: Final Verification Matrix

**Files:**
- Review only: all files changed in Tasks 1–6.

**Interfaces:**
- Consumes: complete implementation.
- Produces: final automated and manual evidence with no additional feature work.

- [ ] **Step 1: Run the focused JVM suite**

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.orbit.app.ui.screens.home.BrainDumpInteractionStateTest" `
  --tests "com.orbit.app.ui.screens.home.BrainDumpSkipUndoControllerTest" `
  --tests "com.orbit.app.ui.screens.home.BrainDumpFlowCoordinatorTest" `
  --tests "com.orbit.app.ui.screens.home.CaptureSuggestionSheetTest" `
  --tests "com.orbit.app.ui.screens.home.HomeCaptureCancellationTest"
```

Expected: all focused tests pass.

- [ ] **Step 2: Run the broader build, JVM tests, and lint once**

```powershell
.\gradlew.bat --no-daemon :app:test :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Expected:

- debug and release JVM tests pass;
- debug APK builds;
- instrumentation APK builds;
- lint has zero errors;
- warnings are compared with the current documented baseline rather than silently ignored.

- [ ] **Step 3: Run connected Brain Dump checks if a target is available**

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.orbit.app.ui.screens.home.BrainDumpSuggestionContentTest,com.orbit.app.domain.usecase.BrainDumpActionsRoomTest
```

Expected: Compose interaction and Room exactly-once tests pass. If no target is available, keep device execution explicitly pending.

- [ ] **Step 4: Perform the manual interaction matrix**

Verify:

1. Note, task, and reminder proposals show type, Space, and timing before confirmation.
2. More contains Keep, Skip, and Discard remaining.
3. Skip Undo works on middle and final items.
4. Finish later, swipe, scrim, Back, Review resume, and capture-detail resume preserve progress.
5. Dirty nested dismissal confirms; pristine dismissal steps back.
6. Failed writes retain the draft and show Retry inside the sheet.
7. Reminder-notification failure advances once and shows a warning.
8. Every new item starts at the top.
9. Completion counts are correct and contain no raw source text.
10. English, Estonian, and Russian copy fits at normal and large font scales.
11. Light, Dark, Auto, preset, and custom backgrounds remain readable.
12. Screen reader announces selected type, selected Space, progress changes, inline status, Undo, and destructive actions.

- [ ] **Step 5: Review final diff and protected behavior**

Inspect:

```powershell
git diff --check
git diff -- app/src/main app/src/test app/src/androidTest docs/codex/LUMA_REGRESSION_CHECKLIST.md
```

Confirm there are no unrelated changes, schema files, export-format changes, production dependencies, raw/internal visibility changes, or identity-bearing text.

- [ ] **Step 6: Commit any verification-only corrections**

Only if verification required a focused correction, stage the bounded Brain Dump file set and inspect the staged diff before committing:

```powershell
git add app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpInteractionState.kt app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoController.kt app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinator.kt app/src/main/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContent.kt app/src/main/java/com/orbit/app/ui/screens/home/HomeCaptureViewModel.kt app/src/main/java/com/orbit/app/ui/screens/home/HomeScreen.kt app/src/main/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheet.kt app/src/main/res/values/strings_localization.xml app/src/main/res/values-et/strings_localization.xml app/src/main/res/values-ru/strings_localization.xml app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpInteractionStateTest.kt app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpSkipUndoControllerTest.kt app/src/test/java/com/orbit/app/ui/screens/home/BrainDumpFlowCoordinatorTest.kt app/src/test/java/com/orbit/app/ui/screens/home/CaptureSuggestionSheetTest.kt app/src/androidTest/java/com/orbit/app/ui/screens/home/BrainDumpSuggestionContentTest.kt app/src/androidTest/java/com/orbit/app/domain/usecase/BrainDumpActionsRoomTest.kt docs/codex/LUMA_REGRESSION_CHECKLIST.md
git diff --cached --check
git commit -m "fix: complete Brain Dump interaction verification"
```

Do not create an empty commit.
