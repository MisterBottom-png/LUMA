package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.analyzer.confidenceLevel

/**
 * What Tallele does with an analysed thought: file it ([Act]), ask one question
 * ([Ask]), or keep it quietly as a saved thought ([Keep]).
 */
sealed interface CaptureDecision {
    /** Clear enough to file as [type] without asking. */
    data class Act(val type: SuggestedItemType) : CaptureDecision

    /** One real question is needed; [reason] says which. */
    data class Ask(val reason: CaptureOutcomeReason) : CaptureDecision

    /** Nothing to do: the thought stays saved and findable, with no label and no badge. */
    data object Keep : CaptureDecision
}

/**
 * The act / ask / keep rule. Pure: no Android, no database, no clock of its own.
 *
 * - Only High confidence files anything, for every type.
 * - A reminder is set only with a clear time that is still in the future ("in 1 minute"
 *   counts). Tallele never creates a reminder for an unclear or past time; it asks.
 * - A reminder request is never left unanswered: at Medium or High confidence, a missing,
 *   unclear or past time is a question (and so is a Medium reminder with a clear time,
 *   which the user confirms rather than Tallele setting it alone).
 * - A note is filed only into a Space that was found; otherwise it is kept.
 * - A Medium task that also reads like a reminder is a real tie: one question.
 * - Low confidence never asks and never files.
 * - A failed analysis or a multi-part Brain Dump is kept for the user to sort.
 *
 * @param hasSpace whether the thought has a real Space (chosen by the user or found by analysis).
 * @param now the current time in epoch milliseconds.
 */
fun decide(analysis: CaptureAnalysis, hasSpace: Boolean, now: Long): CaptureDecision {
    if (analysis.analyzerFailed || analysis.brainDumpItems.isNotEmpty()) return CaptureDecision.Keep
    val level = analysis.confidenceLevel
    if (level == CaptureConfidence.Low) return CaptureDecision.Keep
    val high = level == CaptureConfidence.High
    return when (analysis.suggestedType) {
        SuggestedItemType.Reminder -> decideReminder(analysis, high, now)
        SuggestedItemType.Task -> when {
            high -> CaptureDecision.Act(SuggestedItemType.Task)
            analysis.reminderPossible -> CaptureDecision.Ask(CaptureOutcomeReason.TypeUnclear)
            else -> CaptureDecision.Keep
        }
        SuggestedItemType.Note -> if (high && hasSpace) CaptureDecision.Act(SuggestedItemType.Note) else CaptureDecision.Keep
        // A legacy storage token; never filed automatically.
        SuggestedItemType.MondayItem -> CaptureDecision.Keep
    }
}

private fun decideReminder(analysis: CaptureAnalysis, high: Boolean, now: Long): CaptureDecision {
    val at = analysis.suggestedReminderAt
        ?.takeIf { analysis.reminderTimeStatus == ReminderTimeStatus.Resolved }
        ?: return CaptureDecision.Ask(CaptureOutcomeReason.TimeUnclear)
    return when {
        at <= now -> CaptureDecision.Ask(CaptureOutcomeReason.TimePassed)
        high -> CaptureDecision.Act(SuggestedItemType.Reminder)
        else -> CaptureDecision.Ask(CaptureOutcomeReason.TimeUnclear)
    }
}
