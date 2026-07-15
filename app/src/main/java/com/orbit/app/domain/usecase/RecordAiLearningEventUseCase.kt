package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.AiSuggestionHistoryRepository
import com.orbit.app.domain.analyzer.CaptureAnalysis

data class CaptureSuggestionLearningContext(
    val captureId: Long,
    val analysis: CaptureAnalysis,
    val suggestedSpaceId: Long?,
)

data class CaptureSuggestionLearningDecision(
    val surface: AiSuggestionSurface,
    val userAction: String,
    val finalType: SuggestedItemType? = null,
    val finalSpaceId: Long? = null,
    val finalSpaceName: String? = null,
    val finalTitle: String? = null,
    val finalDueAt: Long? = null,
    val sourceItemId: String? = null,
    val sourceText: String? = null,
)

class RecordAiLearningEventUseCase(
    private val suggestionHistoryRepository: AiSuggestionHistoryRepository,
    private val correctionHistoryRepository: AiCorrectionHistoryRepository,
) {
    suspend fun recordAccepted(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
    ): Long = record(context, decision, AiSuggestionOutcome.Accepted)

    suspend fun recordRejected(
        context: CaptureSuggestionLearningContext,
        userAction: String,
        surface: AiSuggestionSurface = AiSuggestionSurface.Capture,
        sourceItemId: String? = null,
        sourceText: String = context.analysis.rawText,
    ): Long = suggestionHistoryRepository.insert(
        context.toHistory(
            outcome = AiSuggestionOutcome.Rejected,
            surface = surface,
            userAction = userAction,
            finalType = null,
            finalSpaceId = null,
            finalSpaceName = null,
            finalTitle = null,
            sourceItemId = sourceItemId,
            sourceText = sourceText,
        ),
    )

    suspend fun recordBrainDumpRejected(
        context: CaptureSuggestionLearningContext,
        itemId: String,
        sourceText: String,
        suggestedType: SuggestedItemType,
        suggestedSpaceName: String,
    ): Long = suggestionHistoryRepository.insert(
        context.toHistory(
            outcome = AiSuggestionOutcome.Rejected,
            surface = AiSuggestionSurface.BrainDump,
            userAction = "skip_brain_dump_item",
            finalType = suggestedType,
            finalSpaceId = null,
            finalSpaceName = suggestedSpaceName,
            finalTitle = null,
            sourceItemId = itemId,
            sourceText = sourceText,
        ),
    )

    suspend fun recordCorrected(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
    ): Long = record(context, decision, AiSuggestionOutcome.Corrected)

    private suspend fun record(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
        outcome: AiSuggestionOutcome,
    ): Long {
        val historyId = suggestionHistoryRepository.insert(
            context.toHistory(
                outcome = outcome,
                surface = decision.surface,
                userAction = decision.userAction,
                finalType = decision.finalType,
                finalSpaceId = decision.finalSpaceId,
                finalSpaceName = decision.finalSpaceName,
                finalTitle = decision.finalTitle,
                sourceItemId = decision.sourceItemId,
                sourceText = decision.sourceText ?: context.analysis.rawText,
            ),
        )

        context.correctionsFor(decision).forEach { correction ->
            correctionHistoryRepository.insert(correction.copy(suggestionHistoryId = historyId))
        }
        return historyId
    }

    fun hasCorrections(
        context: CaptureSuggestionLearningContext,
        decision: CaptureSuggestionLearningDecision,
    ): Boolean = context.correctionsFor(decision).isNotEmpty()

    private fun CaptureSuggestionLearningContext.toHistory(
        outcome: AiSuggestionOutcome,
        surface: AiSuggestionSurface,
        userAction: String,
        finalType: SuggestedItemType?,
        finalSpaceId: Long?,
        finalSpaceName: String?,
        finalTitle: String?,
        sourceItemId: String?,
        sourceText: String?,
    ): AiSuggestionHistoryEntity = AiSuggestionHistoryEntity(
        surface = surface,
        outcome = outcome,
        analyzerSource = analysis.analyzerSource.name,
        captureId = captureId,
        sourceItemType = sourceItemId?.substringBefore(":", missingDelimiterValue = sourceItemId),
        sourceItemId = sourceItemId?.substringAfter(":", missingDelimiterValue = "")?.toLongOrNull(),
        suggestedType = finalType ?: analysis.suggestedType,
        suggestedSpaceId = finalSpaceId ?: suggestedSpaceId,
        suggestedSpaceName = finalSpaceName ?: analysis.suggestedSpaceName,
        suggestedTitle = finalTitle ?: analysis.suggestedTitle,
        suggestedAction = analysis.suggestedNextAction,
        confidence = analysis.confidence,
        sourceTextSnippet = (sourceText ?: analysis.rawText).learningSnippet(),
        userAction = userAction,
    )

    private fun CaptureSuggestionLearningContext.correctionsFor(
        decision: CaptureSuggestionLearningDecision,
    ): List<AiCorrectionHistoryEntity> = buildList {
        val finalType = decision.finalType
        if (finalType != null && finalType != analysis.suggestedType) {
            add(correction("type", analysis.suggestedType.name, finalType.name, decision.sourceText))
        }

        val finalSpaceName = decision.finalSpaceName
        if (
            finalSpaceName != null &&
            !finalSpaceName.equals(analysis.suggestedSpaceName, ignoreCase = true)
        ) {
            add(correction("space", analysis.suggestedSpaceName, finalSpaceName, decision.sourceText))
        } else if (decision.finalSpaceId != null && decision.finalSpaceId != suggestedSpaceId) {
            add(
                correction(
                    fieldName = "space",
                    originalValue = suggestedSpaceId?.toString(),
                    correctedValue = decision.finalSpaceId.toString(),
                    sourceText = decision.sourceText,
                ),
            )
        }

        val finalTitle = decision.finalTitle?.trim()
        val suggestedTitle = analysis.suggestedTitle.trim()
        if (!finalTitle.isNullOrBlank() && finalTitle != suggestedTitle) {
            add(correction("title", suggestedTitle, finalTitle, decision.sourceText))
        }

        val finalDueAt = decision.finalDueAt
        val suggestedDueAt = analysis.suggestedReminderAt
        if (finalDueAt != null && finalDueAt != suggestedDueAt) {
            add(
                correction(
                    fieldName = "reminder_time",
                    originalValue = suggestedDueAt?.toString(),
                    correctedValue = finalDueAt.toString(),
                    sourceText = decision.sourceText,
                ),
            )
        }
    }

    private fun correction(
        fieldName: String,
        originalValue: String?,
        correctedValue: String,
        sourceText: String?,
    ): AiCorrectionHistoryEntity = AiCorrectionHistoryEntity(
        fieldName = fieldName,
        originalValue = originalValue,
        correctedValue = correctedValue,
        sourceTextSnippet = sourceText?.learningSnippet(),
    )

    private fun String.learningSnippet(): String =
        replace(Regex("\\s+"), " ").trim().take(MaxSnippetLength)

    private companion object {
        const val MaxSnippetLength = 220
    }
}
