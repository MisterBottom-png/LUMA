package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.LearnedRuleCategory
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.SpaceAliasMemoryEntity
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.LearnedRuleRepository
import com.orbit.app.data.repository.PersonMemoryRepository
import com.orbit.app.data.repository.ProjectMemoryRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.SpaceAliasMemoryRepository
import kotlinx.coroutines.flow.first

interface LearningProfileProvider {
    suspend fun profileFor(input: String): String
}

object EmptyLearningProfileProvider : LearningProfileProvider {
    override suspend fun profileFor(input: String): String = ""
}

class LocalLearningProfileProvider(
    private val learnedRuleRepository: LearnedRuleRepository,
    private val personMemoryRepository: PersonMemoryRepository,
    private val projectMemoryRepository: ProjectMemoryRepository,
    private val spaceRepository: SpaceRepository,
    private val spaceAliasMemoryRepository: SpaceAliasMemoryRepository,
    private val correctionHistoryRepository: AiCorrectionHistoryRepository,
) : LearningProfileProvider {
    override suspend fun profileFor(input: String): String {
        val queryTokens = input.profileTokens()
        if (queryTokens.isEmpty()) return ""
        val spacesById = spaceRepository.observeAll().first().associateBy { it.id }

        val explicitLines = buildList {
            addAll(
                learnedRuleRepository.observeEnabled().first()
                    .asSequence()
                    .sortedWith(compareByDescending<LearnedRuleEntity> { it.strength }.thenByDescending { it.updatedAt })
                    .mapNotNull { rule -> rule.toProfileLine() }
                    .filter { it.isRelevantTo(queryTokens) }
                    .take(MaxRuleLines)
                    .toList(),
            )
            addAll(
                personMemoryRepository.observeEnabled().first()
                    .asSequence()
                    .sortedWith(compareByDescending<PersonMemoryEntity> { it.strength }.thenByDescending { it.updatedAt })
                    .mapNotNull { person -> person.toProfileLine() }
                    .filter { it.isRelevantTo(queryTokens) }
                    .take(MaxPeopleLines)
                    .toList(),
            )
            addAll(
                projectMemoryRepository.observeEnabled().first()
                    .asSequence()
                    .sortedWith(compareByDescending<ProjectMemoryEntity> { it.strength }.thenByDescending { it.updatedAt })
                    .mapNotNull { project -> project.toProfileLine() }
                    .filter { it.isRelevantTo(queryTokens) }
                    .take(MaxProjectLines)
                    .toList(),
            )
            addAll(
                spaceAliasMemoryRepository.observeEnabled().first()
                    .asSequence()
                    .sortedWith(compareByDescending<SpaceAliasMemoryEntity> { it.strength }.thenByDescending { it.updatedAt })
                    .mapNotNull { alias ->
                        val aliasText = alias.alias.cleanProfileText().ifBlank { return@mapNotNull null }
                        val spaceName = spacesById[alias.spaceId]?.name?.cleanProfileText()
                            ?.takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        "alias '$aliasText' usually maps to $spaceName"
                    }
                    .filter { it.isRelevantTo(queryTokens) }
                    .take(MaxAliasLines)
                    .toList(),
            )
        }

        val correctionLines = correctionHistoryRepository.observeAll().first()
            .asSequence()
            .take(MaxCorrectionRows)
            .groupBy { correction -> correction.compactPatternKey() }
            .filter { (_, corrections) -> corrections.size >= MinRepeatedCorrections }
            .mapNotNull { (_, corrections) -> corrections.toCorrectionProfileLine(queryTokens) }
            .take(MaxCorrectionLines)
            .toList()

        return (explicitLines + correctionLines)
            .distinct()
            .take(MaxProfileLines)
            .joinToString(separator = "\n") { "- $it" }
    }

    private fun LearnedRuleEntity.toProfileLine(): String? {
        val text = ruleText.cleanProfileText().ifBlank { return null }
        val prefix = when (category) {
            LearnedRuleCategory.Type -> "type preference"
            LearnedRuleCategory.Space -> "Space preference"
            LearnedRuleCategory.Person -> "person preference"
            LearnedRuleCategory.Project -> "project preference"
            LearnedRuleCategory.Alias -> "alias preference"
            LearnedRuleCategory.Tone -> "tone preference"
            LearnedRuleCategory.Other -> "preference"
        }
        return "$prefix: $text"
    }

    private fun PersonMemoryEntity.toProfileLine(): String? {
        val name = displayName.cleanProfileText().ifBlank { return null }
        val aliasText = aliases?.cleanProfileText()?.takeIf { it.isNotBlank() }
        val noteText = notes?.cleanProfileText()?.takeIf { it.isNotBlank() }
        return buildString {
            append("$name is a learned person")
            if (aliasText != null) append(" also known as $aliasText")
            if (noteText != null) append("; $noteText")
        }
    }

    private fun ProjectMemoryEntity.toProfileLine(): String? {
        val projectName = name.cleanProfileText().ifBlank { return null }
        val noteText = notes?.cleanProfileText()?.takeIf { it.isNotBlank() }
        return if (noteText == null) {
            "$projectName is a learned project"
        } else {
            "$projectName is a learned project; $noteText"
        }
    }

    private fun AiCorrectionHistoryEntity.compactPatternKey(): String =
        listOf(fieldName, originalValue.orEmpty(), correctedValue)
            .joinToString("|") { it.cleanProfileText().lowercase() }

    private fun List<AiCorrectionHistoryEntity>.toCorrectionProfileLine(
        queryTokens: Set<String>,
    ): String? {
        val sample = maxByOrNull { it.createdAt } ?: return null
        val fieldName = sample.fieldName.cleanProfileText().ifBlank { return null }
        val original = sample.originalValue?.cleanProfileText()?.takeIf { it.isNotBlank() } ?: "unspecified"
        val corrected = sample.correctedValue.cleanProfileText().ifBlank { return null }
        val triggers = asSequence()
            .flatMap { it.sourceTextSnippet.orEmpty().profileTokens().asSequence() }
            .filter { it in queryTokens }
            .distinct()
            .take(MaxCorrectionTriggerTokens)
            .toList()
        return if (triggers.isEmpty()) {
            val line = "repeated correction: $fieldName $original -> $corrected"
            line.takeIf { it.isRelevantTo(queryTokens) }
        } else {
            "for ${triggers.joinToString(" / ")}, repeated correction: $fieldName $original -> $corrected"
        }
    }

    private fun String.isRelevantTo(queryTokens: Set<String>): Boolean {
        val lineTokens = profileTokens()
        return lineTokens.any { it in queryTokens }
    }

    private fun String.profileTokens(): Set<String> =
        lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 && it !in StopWords }
            .toSet()

    private fun String.cleanProfileText(): String =
        replace(Regex("\\s+"), " ")
            .trim()
            .take(MaxLineChars)

    private companion object {
        const val MaxProfileLines = 6
        const val MaxRuleLines = 3
        const val MaxPeopleLines = 2
        const val MaxProjectLines = 2
        const val MaxAliasLines = 2
        const val MaxCorrectionLines = 2
        const val MaxCorrectionRows = 80
        const val MaxCorrectionTriggerTokens = 3
        const val MinRepeatedCorrections = 2
        const val MaxLineChars = 140
        val StopWords = setOf(
            "about",
            "after",
            "before",
            "from",
            "into",
            "this",
            "that",
            "the",
            "and",
            "with",
            "usually",
            "preference",
            "learned",
        )
    }
}
