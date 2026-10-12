package com.orbit.app.domain.usecase

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.LearnedRuleCategory
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.LearnedRuleRepository
import kotlinx.coroutines.flow.first

data class LearnedRuleProposal(
    val title: String,
    /** Stored and given to Gemini as written; the question shown is built from [from] and [to]. */
    val ruleText: String,
    val category: LearnedRuleCategory,
    val sourceCorrectionHistoryId: Long,
    /** What Tallele suggested and what the user chose: a type name or a Space name. */
    val from: String = "",
    val to: String = "",
    /** Identifies the correction pattern, so "Not now" is remembered. */
    val key: String = "",
)

/** Converts repeated, non-sensitive correction patterns into an explicit user choice. */
class ProposeLearnedRuleUseCase(
    private val correctionHistoryRepository: AiCorrectionHistoryRepository,
    private val learnedRuleRepository: LearnedRuleRepository,
    private val isLearningEnabled: suspend () -> Boolean = { true },
    /**
     * Whether a saved rule would be used at all. Learned rules only reach Gemini
     * prompts, so with Gemini or sharing off, Tallele does not offer to save one.
     */
    private val rulesAreUsed: suspend () -> Boolean = isLearningEnabled,
    private val declined: com.orbit.app.data.local.DeclinedRuleMemory = com.orbit.app.data.local.DeclinedRuleMemory.None,
) {
    suspend fun proposalAfterCorrection(): LearnedRuleProposal? {
        if (!isLearningEnabled() || !rulesAreUsed()) return null
        val corrections = correctionHistoryRepository.observeAll().first()
        val existing = learnedRuleRepository.observeAll().first()
        // N016: every repeated pattern is considered, not only the first; one already
        // saved as a rule, or answered "Not now", is skipped.
        return corrections
            .asSequence()
            .filter { it.fieldName == TypeField || it.fieldName == SpaceField }
            .groupBy(::key)
            .entries
            .filter { (key, group) -> group.size >= RepetitionThreshold && !declined.isDeclined(key) }
            .mapNotNull { (key, group) -> group.maxByOrNull { it.createdAt }?.toProposal()?.copy(key = key) }
            .firstOrNull { suggested ->
                existing.none { rule -> rule.ruleText.equals(suggested.ruleText, ignoreCase = true) }
            }
    }

    /** "Not now": this pattern is not offered again. */
    fun decline(proposal: LearnedRuleProposal) {
        if (proposal.key.isNotBlank()) declined.decline(proposal.key)
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
        // A Space recorded by its number cannot be put into words for the user.
        if (fieldName == SpaceField && (original.all(Char::isDigit) || corrected.all(Char::isDigit))) return null
        return when (fieldName) {
            TypeField -> LearnedRuleProposal(
                title = "Item type preference",
                ruleText = "Prefer $corrected items instead of $original items when the wording is similar.",
                category = LearnedRuleCategory.Type,
                sourceCorrectionHistoryId = id,
                from = original,
                to = corrected,
            )
            SpaceField -> LearnedRuleProposal(
                title = "Space preference",
                ruleText = "Prefer $corrected instead of $original when the wording is similar.",
                category = LearnedRuleCategory.Space,
                sourceCorrectionHistoryId = id,
                from = original,
                to = corrected,
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
