package com.orbit.app.domain.capture

import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import com.orbit.app.domain.analyzer.ReminderTimeStatus

/** Rebuilds the analysis shown in the sorting sheet from a stored suggestion. */
fun CaptureSuggestionEntity.toAnalysis(rawText: String): CaptureAnalysis = CaptureAnalysis(
    rawText = rawText,
    suggestedType = suggestedType,
    suggestedSpaceName = suggestedSpaceName ?: "Inbox",
    suggestedTitle = suggestedTitle,
    summary = suggestedTitle,
    suggestedNextAction = nextAction,
    relatedTopics = listOfNotNull(suggestedSpaceName),
    suggestedLabels = labelNames(),
    reminderPossible = suggestedType == SuggestedItemType.Reminder || suggestedReminderAt != null ||
        reminderStatus() != ReminderTimeStatus.Unspecified,
    suggestedReminderAt = suggestedReminderAt,
    reminderPhrase = reminderPhrase,
    reminderTimeStatus = reminderStatus(),
    lifeSignal = runCatching { CaptureLifeSignal.valueOf(lifeSignal) }.getOrDefault(CaptureLifeSignal.None),
    confidence = confidence,
    typeReason = typeReason,
    spaceReason = spaceReason,
    analyzerSource = runCatching { CaptureAnalyzerSource.valueOf(analyzerSource) }
        .getOrDefault(CaptureAnalyzerSource.Local),
)

fun CaptureSuggestionEntity.labelNames(): List<String> =
    suggestedLabels.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun CaptureSuggestionEntity.reminderStatus(): ReminderTimeStatus =
    runCatching { ReminderTimeStatus.valueOf(reminderTimeStatus) }.getOrDefault(ReminderTimeStatus.Unspecified)

/**
 * Whether LUMA should ask one quick question right after saving: only for a thought
 * that clearly asks to be reminded, so a reminder is never missed. Everything else
 * waits quietly in To sort.
 */
fun CaptureAnalysis.needsImmediateTimeQuestion(now: Long): Boolean {
    if (brainDumpItems.isNotEmpty()) return false
    if (suggestedType != SuggestedItemType.Reminder) return false
    if (confidence < ImmediateQuestionConfidence) return false
    return when (reminderTimeStatus) {
        ReminderTimeStatus.Resolved -> (suggestedReminderAt ?: 0L) > now
        ReminderTimeStatus.NeedsClarification -> true
        ReminderTimeStatus.Unspecified -> false
    }
}

private const val ImmediateQuestionConfidence = 0.66f
