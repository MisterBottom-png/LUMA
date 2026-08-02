package com.orbit.app.integrations.gemini

import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.domain.ai.SituationSourceSummary
import com.orbit.app.domain.ai.SourceLinkedAnswer

object SourceLinkedPromptBuilders {
    fun askLuma(
        question: String,
        sources: List<AiSourceItem>,
        learningProfile: String = "",
    ): String = """
        You are Ask LUMA. Answer only from the provided local source items.
        Every factual claim must be supported by sourceItemIds from the provided sources.
        $SourceLinkedLanguageInstruction
        If the sources are insufficient, set hasSufficientData to false, use an empty sourceItemIds
        array, and give a brief no-data answer in the question's language. Otherwise set it to true.
        ${learningProfile.toLearningProfileSection()}
        Return JSON only: {"answer":"short answer","sourceItemIds":["task:1"],"hasSufficientData":true}
        Question: ${question.trim()}
        Sources:
        ${sources.toPromptContext()}
    """.trimIndent()

    fun situationSummary(
        sources: List<AiSourceItem>,
        learningProfile: String = "",
    ): String = """
        You are LUMA's Situation AI. Use only the provided local source items.
        Keep the response short, calm, and actionable. Include sourceItemIds.
        $SourceLinkedLanguageInstruction
        ${learningProfile.toLearningProfileSection()}
        Return JSON only:
        {"rightNow":"short","whatMatters":"short","stuck":"short","nextTinyStep":"short","sourceItemIds":["capture:1"]}
        Sources:
        ${sources.toPromptContext()}
    """.trimIndent()

    fun reviewSummary(
        sources: List<AiSourceItem>,
        learningProfile: String = "",
    ): String = """
        You are LUMA's Review helper. Use only the provided local source items.
        Keep the response short and non-punitive. Include sourceItemIds.
        $SourceLinkedLanguageInstruction
        ${learningProfile.toLearningProfileSection()}
        Return JSON only:
        {"answer":"short weekly review summary with one small place to start","sourceItemIds":["task:1"]}
        Sources:
        ${sources.toPromptContext()}
    """.trimIndent()

    fun spaceFocus(spaceName: String, sources: List<AiSourceItem>): String = """
        You are LUMA's Space Focus helper. Use only the provided items for $spaceName.
        $SourceLinkedLanguageInstruction
        Return JSON only: {"answer":"short space focus summary","sourceItemIds":["note:1"]}
        Sources:
        ${sources.toPromptContext()}
    """.trimIndent()

    private fun List<AiSourceItem>.toPromptContext(): String =
        joinToString(separator = "\n") { item ->
            "- ${item.sourceId} | ${item.type.routeName} | ${item.spaceName ?: "Inbox"} | ${item.status} | ${item.title}: ${item.snippet}"
        }

    private fun String.toLearningProfileSection(): String =
        takeIf { it.isNotBlank() }
            ?.let {
                """
                Relevant local learning profile. Use only as preference guidance for tone, routing, or prioritization. It is not a factual source:
                $it
                """.trimIndent()
            }
            .orEmpty()

    private const val SourceLinkedLanguageInstruction =
        "Detect the source language of the user's question and source text. Keep every user-facing " +
            "output value in the source or dominant language and preserve meaningful language " +
            "switches. Translate only when the user's current request explicitly asks for translation. " +
            "Keep JSON keys exactly as specified."
}

object SourceLinkedGeminiValidator {
    fun answer(text: String, sources: List<AiSourceItem>): SourceLinkedAnswer? {
        val answer = extractString(text, "answer")?.takeIf { it.isNotBlank() } ?: return null
        val validIds = validateIds(text, sources)
        val hasSufficientData = extractBoolean(text, "hasSufficientData")
        val isNoDataAnswer = hasSufficientData == false ||
            (hasSufficientData == null && answer == LegacyNoDataAnswer)
        if (isNoDataAnswer && validIds.isNotEmpty()) return null
        if (!isNoDataAnswer && validIds.isEmpty()) return null
        return SourceLinkedAnswer(
            answer = answer.take(MaxAnswerLength),
            sourceItemIds = validIds,
            sourceItems = sources.filter { it.sourceId in validIds },
            fromGemini = true,
        )
    }

    fun situation(text: String, sources: List<AiSourceItem>): SituationSourceSummary? {
        val rightNow = extractString(text, "rightNow")?.takeIf { it.isNotBlank() } ?: return null
        val whatMatters = extractString(text, "whatMatters")?.takeIf { it.isNotBlank() } ?: return null
        val stuck = extractString(text, "stuck")?.takeIf { it.isNotBlank() } ?: return null
        val nextTinyStep = extractString(text, "nextTinyStep")?.takeIf { it.isNotBlank() } ?: return null
        val validIds = validateIds(text, sources).takeIf { it.isNotEmpty() } ?: return null
        return SituationSourceSummary(
            rightNow = rightNow.take(MaxLineLength),
            whatMatters = whatMatters.take(MaxLineLength),
            stuck = stuck.take(MaxLineLength),
            nextTinyStep = nextTinyStep.take(MaxLineLength),
            sourceItemIds = validIds,
            sourceItems = sources.filter { it.sourceId in validIds },
            fromGemini = true,
        )
    }

    private fun validateIds(text: String, sources: List<AiSourceItem>): List<String> {
        val allowed = sources.map { it.sourceId }.toSet()
        return extractStringArray(text, "sourceItemIds")
            .filter { it in allowed }
            .distinct()
            .take(MaxSourceIds)
    }

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return regex.find(json)?.groupValues?.get(1)?.unescapeJsonString()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        val body = regex.find(json)?.groupValues?.get(1) ?: return emptyList()
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(body)
            .map { it.groupValues[1].unescapeJsonString().trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
        return regex.find(json)?.groupValues?.get(1)?.toBooleanStrictOrNull()
    }

    private fun String.unescapeJsonString(): String =
        replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    private const val LegacyNoDataAnswer = "No data found."
    private const val MaxAnswerLength = 420
    private const val MaxLineLength = 180
    private const val MaxSourceIds = 5
}
