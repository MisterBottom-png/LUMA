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
    private lateinit var skipController: BrainDumpSkipUndoController
    private val durableActionGate = Mutex()
    private var generation = 0L

    fun start(
        captureId: Long,
        items: List<BrainDumpSuggestion>,
        spaces: List<CaptureSpaceOption>,
        storedOutcomes: Map<String, BrainDumpItemOutcome>,
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
        showFirstPendingItem()
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
            else -> return
        }
        _state.value = current.copy(stage = stage, status = null)
    }

    fun discardDraftChanges() {
        val current = _state.value ?: return
        if (isDurableActionInProgress()) return
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
        val outcome = when (request) {
            is BrainDumpCommitRequest.SaveNote,
            is BrainDumpCommitRequest.SaveTask,
            is BrainDumpCommitRequest.SaveReminder -> BrainDumpItemOutcome.Saved
            is BrainDumpCommitRequest.KeepInInbox -> BrainDumpItemOutcome.KeptInInbox
            is BrainDumpCommitRequest.Skip -> BrainDumpItemOutcome.Skipped
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
        else -> BrainDumpStatus(BrainDumpStatusKind.Success, BrainDumpStatusMessage.ThoughtSkipped)
    }
}
