package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion

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
    val warning: BrainDumpStatus? = null,
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
