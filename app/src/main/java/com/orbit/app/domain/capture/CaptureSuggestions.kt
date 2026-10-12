package com.orbit.app.domain.capture

import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.domain.analyzer.CaptureConfidence
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
    suggestedReminderAt = reminderTime(),
    taskDateEpochDay = taskDateEpochDay(),
    reminderPhrase = reminderPhrase,
    reminderTimeStatus = reminderStatus(),
    lifeSignal = runCatching { CaptureLifeSignal.valueOf(lifeSignal) }.getOrDefault(CaptureLifeSignal.None),
    confidence = confidence,
    typeReason = typeReason,
    spaceReason = spaceReason,
    analyzerSource = runCatching { CaptureAnalyzerSource.valueOf(analyzerSource) }
        .getOrDefault(CaptureAnalyzerSource.Local),
)

/**
 * Tallele is not sure enough to offer one tap: the user picks, and "Accept all"
 * leaves it alone. Same threshold as the sort sheet, which opens on "Keep in To sort".
 */
val CaptureSuggestionEntity.isLowConfidence: Boolean
    get() = toAnalysis(suggestedTitle).confidenceLevel == CaptureConfidence.Low

fun CaptureSuggestionEntity.labelNames(): List<String> =
    suggestedLabels.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

fun CaptureSuggestionEntity.reminderStatus(): ReminderTimeStatus =
    runCatching { ReminderTimeStatus.valueOf(reminderTimeStatus) }.getOrDefault(ReminderTimeStatus.Unspecified)

/**
 * The one rule for what a suggestion's life signal means for a new task (F08): "someday"
 * and "waiting for" survive acceptance everywhere (automatic filing, one tap, the sheet,
 * Brain Dump); anything else is an open task.
 */
fun CaptureLifeSignal.taskStatus(): com.orbit.app.data.local.entity.TaskStatus = when (this) {
    CaptureLifeSignal.Someday -> com.orbit.app.data.local.entity.TaskStatus.Someday
    CaptureLifeSignal.WaitingFor -> com.orbit.app.data.local.entity.TaskStatus.WaitingFor
    CaptureLifeSignal.None, CaptureLifeSignal.Reflection -> com.orbit.app.data.local.entity.TaskStatus.Open
}

fun CaptureSuggestionEntity.taskStatus(): com.orbit.app.data.local.entity.TaskStatus =
    (runCatching { CaptureLifeSignal.valueOf(lifeSignal) }.getOrNull() ?: CaptureLifeSignal.None).taskStatus()
