package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.usecase.BrainDumpActionResult
import com.orbit.app.domain.usecase.BrainDumpActionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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
        val targetAt: Long,
    ) : BrainDumpCommitRequest

    data class KeepInInbox(
        override val captureId: Long,
        override val sourceKey: String,
    ) : BrainDumpCommitRequest

    data class Skip(
        override val captureId: Long,
        override val sourceKey: String,
    ) : BrainDumpCommitRequest

    /** Every thought still waiting becomes one note ("Keep as one note"). */
    data class KeepRestAsOneNote(
        override val captureId: Long,
        val title: String,
    ) : BrainDumpCommitRequest {
        override val sourceKey: String = ""
    }
}

private sealed interface BrainDumpRetryIntent {
    data class Commit(val request: BrainDumpCommitRequest) : BrainDumpRetryIntent
    data class DiscardRemaining(val captureId: Long) : BrainDumpRetryIntent
}

internal class BrainDumpFlowCoordinator(
    private val scope: CoroutineScope,
    private val expiryDelay: suspend (Long) -> Unit = { delay(it) },
    private val commit: suspend (BrainDumpCommitRequest) -> BrainDumpActionResult,
    private val discardRemaining: suspend (Long) -> Unit = {},
    private val onClose: (resumable: Boolean) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<BrainDumpInteractionState?>(null)
    val state: StateFlow<BrainDumpInteractionState?> = _state.asStateFlow()

    private var captureId: Long = 0L
    private var items: List<BrainDumpSuggestion> = emptyList()
    private var spaces: List<CaptureSpaceOption> = emptyList()
    private var storedOutcomes: MutableMap<String, BrainDumpItemOutcome> = linkedMapOf()
    private var retryIntent: BrainDumpRetryIntent? = null
    private var optimisticallySkippedSourceKey: String? = null
    private var sessionWarning: BrainDumpStatus? = null
    private var overviewEnabled = false
    private var ticked: MutableMap<String, Boolean> = linkedMapOf()
    private var failedKeys: MutableSet<String> = linkedSetOf()
    /** A single row opened from the overview goes back to it after saving. */
    private var singleRowOpen = false
    private lateinit var skipController: BrainDumpSkipUndoController
    private val durableActionGate = Mutex()
    private var generation = 0L

    fun start(
        captureId: Long,
        items: List<BrainDumpSuggestion>,
        spaces: List<CaptureSpaceOption>,
        storedOutcomes: Map<String, BrainDumpItemOutcome>,
        startWithOverview: Boolean = false,
    ) {
        require(captureId > 0L)
        require(items.isNotEmpty())
        check(!durableActionGate.isLocked) { "A Brain Dump action is still in progress" }
        if (::skipController.isInitialized) {
            check(skipController.pending.value == null) { "A Brain Dump skip is still pending" }
        }
        val startGeneration = ++generation
        this.captureId = captureId
        this.items = items
        this.spaces = spaces
        this.storedOutcomes = storedOutcomes.toMutableMap()
        retryIntent = null
        optimisticallySkippedSourceKey = null
        sessionWarning = null
        overviewEnabled = startWithOverview
        singleRowOpen = false
        failedKeys = linkedSetOf()
        val currentTime = now()
        ticked = items.associate { item ->
            item.id to brainDumpRowIsSure(item, initialBrainDumpDraft(item, spaces), currentTime)
        }.toMutableMap()
        skipController = BrainDumpSkipUndoController(
            scope = scope,
            expiryDelay = expiryDelay,
            commitSkip = { committedCaptureId, sourceKey ->
                commit(BrainDumpCommitRequest.Skip(committedCaptureId, sourceKey))
            },
            onCommitted = { committed ->
                if (startGeneration == generation) onSkipCommitted(committed)
            },
            onCommitFailed = { pending, failure ->
                if (startGeneration == generation) onSkipCommitFailed(pending, failure)
            },
        )
        if (overviewEnabled && pendingItems().isNotEmpty()) showOverview() else showFirstPendingItem()
    }

    /** Ticks or unticks one row of the overview. */
    fun toggleRow(sourceKey: String) {
        val current = _state.value ?: return
        if (current.stage != BrainDumpStage.Overview || isDurableActionInProgress()) return
        val item = items.firstOrNull { it.id == sourceKey } ?: return
        if (!brainDumpRowCanTick(initialBrainDumpDraft(item, spaces), now())) return
        ticked[sourceKey] = !(ticked[sourceKey] ?: false)
        showOverview(status = current.status)
    }

    /** Opens one row as a card, to change it before saving. */
    fun openRow(sourceKey: String) {
        val current = _state.value ?: return
        if (current.stage != BrainDumpStage.Overview || isDurableActionInProgress()) return
        singleRowOpen = true
        showItem(sourceKey)
    }

    fun backToOverview() {
        if (!overviewEnabled || isDurableActionInProgress()) return
        singleRowOpen = false
        if (pendingItems().isEmpty()) showFirstPendingItem() else showOverview()
    }

    /**
     * Saves every ticked row, each in its own exactly-once transaction, in order. A row
     * that fails stays waiting and is marked; the rest go on. Afterwards the thoughts
     * that still need the user go one by one.
     */
    suspend fun saveTicked() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        val toSave = pendingItems().filter { ticked[it.id] == true }
        if (toSave.isEmpty()) return@runDurableAction
        failedKeys.clear()
        var completed = false
        for (item in toSave) {
            val draft = initialBrainDumpDraft(item, spaces)
            val request = when (draft.type) {
                SuggestedItemType.Note -> BrainDumpCommitRequest.SaveNote(captureId, item.id, draft)
                SuggestedItemType.Task, SuggestedItemType.MondayItem ->
                    BrainDumpCommitRequest.SaveTask(captureId, item.id, draft)
                SuggestedItemType.Reminder -> {
                    val at = draft.scheduledAt?.takeIf { it > now() }
                    if (at == null) {
                        failedKeys += item.id
                        continue
                    }
                    BrainDumpCommitRequest.SaveReminder(captureId, item.id, draft, at)
                }
            }
            try {
                val result = commit(request)
                when (result.status) {
                    BrainDumpActionStatus.Missing -> {
                        _state.value = null
                        onClose(false)
                        return@runDurableAction
                    }
                    BrainDumpActionStatus.Applied, BrainDumpActionStatus.AlreadyHandled -> {
                        if (request is BrainDumpCommitRequest.SaveReminder && result.notificationScheduled == false) {
                            sessionWarning = BrainDumpStatus(
                                kind = BrainDumpStatusKind.Warning,
                                message = BrainDumpStatusMessage.NotificationAttention,
                            )
                        }
                        recordOutcome(request)
                        if (result.sessionCompleted) completed = true
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                failedKeys += item.id
            }
        }
        val failedStatus = if (failedKeys.isNotEmpty()) {
            BrainDumpStatus(BrainDumpStatusKind.Error, BrainDumpStatusMessage.SaveFailed)
        } else {
            null
        }
        when {
            completed || pendingItems().isEmpty() -> showCompletion(status = failedStatus)
            failedStatus != null -> showOverview(status = failedStatus)
            else -> {
                singleRowOpen = false
                showFirstPendingItem()
            }
        }
    }

    /** "Keep as one note" (or "Keep the rest as one note"): one transaction for all waiting thoughts. */
    suspend fun keepRestAsOneNote(title: String) = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        val request = BrainDumpCommitRequest.KeepRestAsOneNote(captureId, title)
        try {
            val result = commit(request)
            if (result.status == BrainDumpActionStatus.Missing) {
                _state.value = null
                onClose(false)
                return@runDurableAction
            }
            recordOutcome(request)
            showCompletion(status = BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.NoteSaved))
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            _state.value = _state.value?.copy(
                actionInProgress = false,
                status = BrainDumpStatus(BrainDumpStatusKind.Error, BrainDumpStatusMessage.SaveFailed),
            )
        }
    }

    private fun pendingItems(): List<BrainDumpSuggestion> = items.filter {
        it.id != optimisticallySkippedSourceKey && storedOutcomes[it.id] == BrainDumpItemOutcome.Pending
    }

    private fun showOverview(status: BrainDumpStatus? = null) {
        val currentTime = now()
        val rows = pendingItems().map { item ->
            val draft = initialBrainDumpDraft(item, spaces)
            BrainDumpOverviewRow(
                sourceKey = item.id,
                draft = draft,
                spaceName = spaces.firstOrNull { it.id == draft.spaceId }?.name,
                lifeSignal = item.lifeSignal,
                ticked = ticked[item.id] == true && brainDumpRowCanTick(draft, currentTime),
                canTick = brainDumpRowCanTick(draft, currentTime),
                isList = item.rawText.lines().size > 1 && item.rawText.lines().drop(1).all { it.trimStart().startsWith("- ") },
                failed = item.id in failedKeys,
            )
        }
        _state.value = BrainDumpInteractionState(
            stage = BrainDumpStage.Overview,
            itemId = null,
            itemNumber = 0,
            totalItems = items.size,
            initialDraft = null,
            draft = null,
            completionCounts = completionCounts(),
            status = status,
            warning = sessionWarning,
            overviewRows = rows,
            handledCount = items.size - rows.size,
        )
    }

    private fun showCompletion(status: BrainDumpStatus?) {
        _state.value = BrainDumpInteractionState(
            stage = BrainDumpStage.Completion,
            itemId = null,
            itemNumber = items.size,
            totalItems = items.size,
            initialDraft = null,
            draft = null,
            completionCounts = completionCounts(),
            status = status,
            warning = sessionWarning,
        )
    }

    fun edit() {
        val current = _state.value ?: return
        if (current.stage == BrainDumpStage.Suggestion && !isDurableActionInProgress()) {
            _state.value = current.copy(stage = BrainDumpStage.Edit, status = null)
        }
    }

    fun updateDraft(draft: BrainDumpDraft) {
        val current = _state.value ?: return
        if (!isDurableActionInProgress() && current.draft != null) {
            _state.value = current.copy(draft = draft, status = null)
        }
    }

    fun continueFromEditor() {
        val current = _state.value ?: return
        val draft = current.draft ?: return
        if (isDurableActionInProgress()) return
        _state.value = current.copy(
            stage = when (draft.type) {
                SuggestedItemType.Note -> BrainDumpStage.Edit
                SuggestedItemType.Task, SuggestedItemType.MondayItem -> BrainDumpStage.TaskSetup
                SuggestedItemType.Reminder -> BrainDumpStage.ReminderSetup
            },
            status = null,
        )
        if (draft.type == SuggestedItemType.Note) {
            scope.launch { commitPrimary() }
        }
    }

    fun stepBack() {
        val current = _state.value ?: return
        if (isDurableActionInProgress()) return
        val stage = when (current.stage) {
            BrainDumpStage.TaskSetup, BrainDumpStage.ReminderSetup -> BrainDumpStage.Edit
            BrainDumpStage.Edit -> BrainDumpStage.Suggestion
            BrainDumpStage.Suggestion -> {
                if (current.openedFromOverview) backToOverview()
                return
            }
            else -> return
        }
        _state.value = current.copy(stage = stage, status = null)
    }

    fun discardDraftChanges() {
        val current = _state.value ?: return
        if (isDurableActionInProgress()) return
        if (current.stage == BrainDumpStage.Suggestion) {
            // Changes made on the card are dropped; from the list, go back to the list.
            if (current.openedFromOverview) {
                backToOverview()
            } else {
                _state.value = current.copy(draft = current.initialDraft, status = null)
            }
            return
        }
        val stage = when (current.stage) {
            BrainDumpStage.TaskSetup, BrainDumpStage.ReminderSetup -> BrainDumpStage.Edit
            BrainDumpStage.Edit -> BrainDumpStage.Suggestion
            else -> return
        }
        _state.value = current.copy(
            stage = stage,
            draft = current.initialDraft,
            status = null,
        )
    }

    suspend fun commitPrimary() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        val current = _state.value ?: return@runDurableAction
        val draft = current.draft ?: return@runDurableAction
        if (draft.type == SuggestedItemType.Reminder && draft.scheduledAt == null) {
            _state.value = current.copy(
                stage = BrainDumpStage.ReminderSetup,
                status = null,
            )
            return@runDurableAction
        }
        val request = when (draft.type) {
            SuggestedItemType.Note -> BrainDumpCommitRequest.SaveNote(captureId, currentItemId(), draft)
            SuggestedItemType.Task, SuggestedItemType.MondayItem ->
                BrainDumpCommitRequest.SaveTask(captureId, currentItemId(), draft)
            SuggestedItemType.Reminder ->
                BrainDumpCommitRequest.SaveReminder(
                    captureId = captureId,
                    sourceKey = currentItemId(),
                    draft = draft,
                    targetAt = requireNotNull(draft.scheduledAt),
                )
        }
        applyCommit(request)
    }

    suspend fun keepInInbox() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        applyCommit(BrainDumpCommitRequest.KeepInInbox(captureId, currentItemId()))
    }

    suspend fun skip() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        val current = _state.value ?: return@runDurableAction
        val sourceKey = current.itemId ?: return@runDurableAction
        val index = items.indexOfFirst { it.id == sourceKey }
        if (index < 0) return@runDurableAction

        optimisticallySkippedSourceKey = sourceKey
        retryIntent = null
        skipController.begin(
            captureId = captureId,
            sourceKey = sourceKey,
            isFinalItem = nextPendingIndex(index) == null,
        )
        showNextPendingItem(
            afterIndex = index,
            status = BrainDumpStatus(
                kind = BrainDumpStatusKind.PendingSkip,
                message = BrainDumpStatusMessage.ThoughtSkipped,
                canUndo = true,
            ),
        )
    }

    fun undoSkip() {
        val sourceKey = optimisticallySkippedSourceKey ?: return
        if (_state.value?.status?.canUndo != true) return
        if (!skipController.undo()) return
        optimisticallySkippedSourceKey = null
        retryIntent = null
        showItem(sourceKey)
    }

    suspend fun retry() = runDurableAction {
        val intent = retryIntent ?: return@runDurableAction
        markDurableActionInProgress()
        when (intent) {
            is BrainDumpRetryIntent.Commit -> {
                val request = intent.request
                if (request is BrainDumpCommitRequest.Skip) {
                    flushPendingSkip()
                } else {
                    applyCommit(request)
                }
            }
            is BrainDumpRetryIntent.DiscardRemaining -> applyDiscardRemaining(intent.captureId)
        }
    }

    suspend fun discardRemaining() = runDurableAction {
        markDurableActionInProgress()
        val cancelledSkipSourceKey = optimisticallySkippedSourceKey
        if (::skipController.isInitialized) {
            skipController.cancelWithoutCommit()
        }
        optimisticallySkippedSourceKey = null
        retryIntent = null
        applyDiscardRemaining(captureId, cancelledSkipSourceKey)
    }

    suspend fun finishLater() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        _state.value = null
        onClose(true)
    }

    suspend fun closeCompletion() = runDurableAction {
        markDurableActionInProgress()
        if (!flushPendingSkip()) return@runDurableAction
        _state.value = null
        onClose(false)
    }

    private suspend fun flushPendingSkip(): Boolean {
        if (!::skipController.isInitialized || skipController.pending.value == null) return true
        return try {
            skipController.flush()?.let(::onSkipCommitted)
            true
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun applyCommit(request: BrainDumpCommitRequest) {
        val current = _state.value ?: return
        _state.value = current.copy(actionInProgress = true, status = null)
        try {
            val result = commit(request)
            if (result.status == BrainDumpActionStatus.Missing) {
                _state.value = null
                onClose(false)
                return
            }
            if (request is BrainDumpCommitRequest.SaveReminder && result.notificationScheduled == false) {
                sessionWarning = BrainDumpStatus(
                    kind = BrainDumpStatusKind.Warning,
                    message = BrainDumpStatusMessage.NotificationAttention,
                )
            }
            recordOutcome(request)
            retryIntent = null
            advanceAfterCommit(request, result)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            retryIntent = BrainDumpRetryIntent.Commit(request)
            val failed = _state.value ?: return
            _state.value = failed.copy(
                actionInProgress = false,
                status = BrainDumpStatus(
                    kind = BrainDumpStatusKind.Error,
                    message = BrainDumpStatusMessage.SaveFailed,
                    canRetry = true,
                ),
            )
        }
    }

    private suspend fun applyDiscardRemaining(
        requestedCaptureId: Long,
        restoreSourceKey: String? = null,
    ) {
        try {
            discardRemaining(requestedCaptureId)
            retryIntent = null
            _state.value = null
            onClose(false)
        } catch (failure: CancellationException) {
            restoreAfterDiscardFailure(restoreSourceKey, retryable = false)
            throw failure
        } catch (_: Throwable) {
            retryIntent = BrainDumpRetryIntent.DiscardRemaining(requestedCaptureId)
            restoreAfterDiscardFailure(restoreSourceKey, retryable = true)
        }
    }

    private fun restoreAfterDiscardFailure(restoreSourceKey: String?, retryable: Boolean) {
        val status = BrainDumpStatus(
            kind = BrainDumpStatusKind.Error,
            message = BrainDumpStatusMessage.SaveFailed,
            canRetry = retryable,
        )
        if (restoreSourceKey != null) {
            showItem(restoreSourceKey, status)
        } else {
            _state.value = _state.value?.copy(actionInProgress = false, status = status)
        }
    }

    private fun advanceAfterCommit(request: BrainDumpCommitRequest, result: BrainDumpActionResult) {
        val status = successStatus(request, result)
        val currentIndex = items.indexOfFirst { it.id == request.sourceKey }
        if (overviewEnabled && singleRowOpen && !result.sessionCompleted && pendingItems().isNotEmpty()) {
            singleRowOpen = false
            showOverview(status)
            return
        }
        if (result.sessionCompleted || currentIndex < 0 || nextPendingIndex(currentIndex) == null) {
            _state.value = BrainDumpInteractionState(
                stage = BrainDumpStage.Completion,
                itemId = null,
                itemNumber = items.size,
                totalItems = items.size,
                initialDraft = null,
                draft = null,
                completionCounts = completionCounts(),
                status = status,
                warning = sessionWarning,
            )
        } else {
            showNextPendingItem(currentIndex, status)
        }
    }

    private fun onSkipCommitted(commit: BrainDumpSkipCommit) {
        if (commit.result.status == BrainDumpActionStatus.Missing) {
            _state.value = null
            onClose(false)
            return
        }
        if (storedOutcomes[commit.pending.sourceKey] == BrainDumpItemOutcome.Pending) {
            storedOutcomes[commit.pending.sourceKey] = BrainDumpItemOutcome.Skipped
        }
        optimisticallySkippedSourceKey = null
        retryIntent = null
        val status = successStatus(BrainDumpCommitRequest.Skip(captureId, commit.pending.sourceKey), commit.result)
        val current = _state.value
        val committedIndex = items.indexOfFirst { it.id == commit.pending.sourceKey }
        if (current?.itemId == commit.pending.sourceKey && committedIndex >= 0) {
            showNextPendingItem(committedIndex, status)
        } else if (current?.stage == BrainDumpStage.Completion) {
            _state.value = current.copy(completionCounts = completionCounts(), status = status)
        } else if (current != null) {
            _state.value = current.copy(
                completionCounts = completionCounts(),
                status = status,
                actionInProgress = false,
            )
        }
    }

    private fun onSkipCommitFailed(pending: PendingBrainDumpSkip, failure: Throwable) {
        optimisticallySkippedSourceKey = pending.sourceKey
        retryIntent = BrainDumpRetryIntent.Commit(
            BrainDumpCommitRequest.Skip(pending.captureId, pending.sourceKey),
        )
        showItem(
            sourceKey = pending.sourceKey,
            status = BrainDumpStatus(
                kind = BrainDumpStatusKind.Error,
                message = BrainDumpStatusMessage.SaveFailed,
                canRetry = true,
            ),
        )
    }

    private fun showFirstPendingItem() {
        val index = items.indexOfFirst { storedOutcomes[it.id] == BrainDumpItemOutcome.Pending }
        if (index >= 0) {
            showItem(items[index].id)
        } else {
            _state.value = BrainDumpInteractionState(
                stage = BrainDumpStage.Completion,
                itemId = null,
                itemNumber = items.size,
                totalItems = items.size,
                initialDraft = null,
                draft = null,
                completionCounts = completionCounts(),
                warning = sessionWarning,
            )
        }
    }

    private fun showNextPendingItem(afterIndex: Int, status: BrainDumpStatus?) {
        val index = nextPendingIndex(afterIndex)
        if (index == null) {
            _state.value = BrainDumpInteractionState(
                stage = BrainDumpStage.Completion,
                itemId = null,
                itemNumber = items.size,
                totalItems = items.size,
                initialDraft = null,
                draft = null,
                completionCounts = completionCounts(),
                status = status,
                warning = sessionWarning,
            )
        } else {
            showItem(items[index].id, status)
        }
    }

    private fun showItem(sourceKey: String, status: BrainDumpStatus? = null) {
        val index = items.indexOfFirst { it.id == sourceKey }
        if (index < 0) return
        val draft = initialBrainDumpDraft(items[index], spaces)
        _state.value = BrainDumpInteractionState(
            stage = BrainDumpStage.Suggestion,
            itemId = sourceKey,
            itemNumber = index + 1,
            totalItems = items.size,
            initialDraft = draft,
            draft = draft,
            completionCounts = completionCounts(),
            status = status,
            warning = sessionWarning,
            openedFromOverview = overviewEnabled,
        )
    }

    private fun nextPendingIndex(afterIndex: Int): Int? =
        (afterIndex + 1 until items.size).firstOrNull { index ->
            items[index].id != optimisticallySkippedSourceKey &&
                storedOutcomes[items[index].id] == BrainDumpItemOutcome.Pending
        }

    private fun currentItemId(): String = requireNotNull(_state.value?.itemId)

    private fun isDurableActionInProgress(): Boolean = durableActionGate.isLocked

    private fun markDurableActionInProgress() {
        _state.value = _state.value?.copy(actionInProgress = true)
    }

    private suspend fun runDurableAction(action: suspend () -> Unit) {
        if (!durableActionGate.tryLock()) return
        try {
            action()
        } finally {
            _state.value = _state.value?.copy(actionInProgress = false)
            durableActionGate.unlock()
        }
    }

    private fun recordOutcome(request: BrainDumpCommitRequest) {
        if (request is BrainDumpCommitRequest.KeepRestAsOneNote) {
            storedOutcomes.keys.toList().forEach { key ->
                if (storedOutcomes[key] == BrainDumpItemOutcome.Pending) storedOutcomes[key] = BrainDumpItemOutcome.Saved
            }
            return
        }
        val outcome = when (request) {
            is BrainDumpCommitRequest.SaveNote,
            is BrainDumpCommitRequest.SaveTask,
            is BrainDumpCommitRequest.SaveReminder -> BrainDumpItemOutcome.Saved
            is BrainDumpCommitRequest.KeepInInbox -> BrainDumpItemOutcome.KeptInInbox
            is BrainDumpCommitRequest.Skip -> BrainDumpItemOutcome.Skipped
            is BrainDumpCommitRequest.KeepRestAsOneNote -> BrainDumpItemOutcome.Saved
        }
        if (storedOutcomes[request.sourceKey] == BrainDumpItemOutcome.Pending) {
            storedOutcomes[request.sourceKey] = outcome
        }
    }

    private fun completionCounts(): BrainDumpCompletionCounts =
        storedOutcomes.values.fold(BrainDumpCompletionCounts()) { counts, outcome ->
            when (outcome) {
                BrainDumpItemOutcome.Pending -> counts
                BrainDumpItemOutcome.Saved -> counts.record(BrainDumpCompletedOutcome.Saved)
                BrainDumpItemOutcome.KeptInInbox -> counts.record(BrainDumpCompletedOutcome.KeptInInbox)
                BrainDumpItemOutcome.Skipped -> counts.record(BrainDumpCompletedOutcome.Skipped)
            }
        }

    private fun successStatus(
        request: BrainDumpCommitRequest,
        result: BrainDumpActionResult,
    ): BrainDumpStatus = when {
        request is BrainDumpCommitRequest.SaveNote ->
            BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.NoteSaved)
        request is BrainDumpCommitRequest.SaveTask ->
            BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.TaskCreated)
        request is BrainDumpCommitRequest.SaveReminder ->
            BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.ReminderCreated)
        request is BrainDumpCommitRequest.KeepInInbox ->
            BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.KeptInInbox)
        request is BrainDumpCommitRequest.KeepRestAsOneNote ->
            BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.NoteSaved)
        else -> BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.ThoughtSkipped)
    }
}
