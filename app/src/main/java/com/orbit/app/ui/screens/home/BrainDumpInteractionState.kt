package com.orbit.app.ui.screens.home

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion

internal enum class BrainDumpStage {
    Overview,
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
    /** Rows of the overview list; filled only in [BrainDumpStage.Overview]. */
    val overviewRows: List<BrainDumpOverviewRow> = emptyList(),
    /** Thoughts already handled before this overview (saved, kept or skipped). */
    val handledCount: Int = 0,
    /** A card opened from the overview returns to it on Back or after saving. */
    val openedFromOverview: Boolean = false,
)

/** One thought in the overview: what will be saved if it stays ticked. */
internal data class BrainDumpOverviewRow(
    val sourceKey: String,
    val draft: BrainDumpDraft,
    val spaceName: String?,
    val lifeSignal: com.orbit.app.domain.analyzer.CaptureLifeSignal,
    val ticked: Boolean,
    /** A reminder without a future time cannot be saved from the list. */
    val canTick: Boolean,
    /** A heading with a list under it, which the user may split after all. */
    val isList: Boolean,
    val failed: Boolean = false,
)

/**
 * Ticked by default only when Tallele is sure: high confidence, a real Space (never
 * Inbox), and for a reminder a resolved time in the future.
 */
internal fun brainDumpRowIsSure(item: BrainDumpSuggestion, draft: BrainDumpDraft, now: Long): Boolean =
    item.confidence >= SureConfidence &&
        draft.spaceId != null &&
        !item.suggestedSpaceName.equals("Inbox", ignoreCase = true) &&
        brainDumpRowCanTick(draft, now)

internal fun brainDumpRowCanTick(draft: BrainDumpDraft, now: Long): Boolean =
    draft.type != SuggestedItemType.Reminder || (draft.scheduledAt != null && draft.scheduledAt > now)

private const val SureConfidence = 0.78f

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
    openedFromOverview: Boolean = false,
): BrainDumpDismissalDecision = when {
    actionInProgress -> BrainDumpDismissalDecision.Blocked
    stage == BrainDumpStage.Overview || stage == BrainDumpStage.Completion ->
        BrainDumpDismissalDecision.CloseSession
    // A thought is now changed right on its card, so leaving it with changes asks first.
    initialDraft != draft -> BrainDumpDismissalDecision.ConfirmDiscard
    stage == BrainDumpStage.Suggestion -> if (openedFromOverview) {
        BrainDumpDismissalDecision.StepBack
    } else {
        BrainDumpDismissalDecision.CloseSession
    }
    else -> BrainDumpDismissalDecision.StepBack
}

internal fun SuggestedItemType.brainDumpEditableType(): SuggestedItemType = when (this) {
    SuggestedItemType.Note -> SuggestedItemType.Note
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> SuggestedItemType.Task
    SuggestedItemType.Reminder -> SuggestedItemType.Reminder
}
