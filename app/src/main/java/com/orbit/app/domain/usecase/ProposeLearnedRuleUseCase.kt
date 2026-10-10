package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.LearnedRuleCategory
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.LearnedRuleRepository
import kotlinx.coroutines.flow.first

data class LearnedRuleProposal(
    val title: String,
    val ruleText: String,
    val category: LearnedRuleCategory,
    val sourceCorrectionHistoryId: Long,
)

/** Converts repeated, non-sensitive correction patterns into an explicit user choice. */
class ProposeLearnedRuleUseCase(
    private val correctionHistoryRepository: AiCorrectionHistoryRepository,
    private val learnedRuleRepository: LearnedRuleRepository,
    private val isLearningEnabled: suspend () -> Boolean = { true },
) {
    suspend fun proposalAfterCorrection(): LearnedRuleProposal? {
        if (!isLearningEnabled()) return null
        val corrections = correctionHistoryRepository.observeAll().first()
        val candidate = corrections
            .asSequence()
            .filter { it.fieldName == TypeField || it.fieldName == SpaceField }
            .groupBy(::key)
            .values
            .firstOrNull { it.size >= RepetitionThreshold }
            ?.maxByOrNull { it.createdAt }
            ?: return null
        val proposal = candidate.toProposal() ?: return null
        val existing = learnedRuleRepository.observeAll().first()
        return proposal.takeUnless { suggested -> existing.any { rule ->
            rule.ruleText.equals(suggested.ruleText, ignoreCase = true)
        } }
    }

    suspend fun save(proposal: LearnedRuleProposal) {
        if (!isLearningEnabled()) return
        learnedRuleRepository.insert(
            LearnedRuleEntity(
                title = proposal.title,
                ruleText = proposal.ruleText,
                category = proposal.category,
                sourceCorrectionHistoryId = proposal.sourceCorrectionHistoryId,
                strength = RepetitionThreshold.toFloat(),
            ),
        )
    }

    private fun key(correction: AiCorrectionHistoryEntity): String =
        listOf(correction.fieldName, correction.originalValue.orEmpty(), correction.correctedValue)
            .joinToString("|") { it.trim().lowercase() }

    private fun AiCorrectionHistoryEntity.toProposal(): LearnedRuleProposal? {
        val original = originalValue?.trim().orEmpty()
        val corrected = correctedValue.trim()
        if (original.isBlank() || corrected.isBlank()) return null
        return when (fieldName) {
            TypeField -> LearnedRuleProposal(
                title = "Item type preference",
                ruleText = "Prefer $corrected items instead of $original items when the wording is similar.",
                category = LearnedRuleCategory.Type,
                sourceCorrectionHistoryId = id,
            )
            SpaceField -> LearnedRuleProposal(
                title = "Space preference",
                ruleText = "Prefer $corrected instead of $original when the wording is similar.",
                category = LearnedRuleCategory.Space,
                sourceCorrectionHistoryId = id,
            )
            else -> null
        }
    }

    private companion object {
        const val TypeField = "type"
        const val SpaceField = "space"
        const val RepetitionThreshold = 2
    }
}
