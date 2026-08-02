package com.orbit.app.integrations.gemini

import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import java.time.Instant
import java.time.ZoneId

object GeminiPromptBuilders {
    fun captureAnalysis(
        rawText: String,
        allowedSpaces: List<String> = emptyList(),
        nowEpochMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        learningProfile: String = "",
    ): String {
        val localNow = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId)
        return """
        You are LUMA's optional cloud analyzer. Suggest structure only.
        Never create tasks, notes, reminders, or external items.
        Preserve the user's raw text exactly in meaning.
        $UserFacingLanguageInstruction
        Use only these Spaces when possible: ${allowedSpaces.joinToString().ifBlank { "Work, Personal, Car, Dog, Money, Ideas, Home, Health, Learning, Inbox" }}.
        ${learningProfile.toLearningProfileSection()}
        Current time in epoch milliseconds: $nowEpochMillis.
        Current device time zone: ${zoneId.id}.
        Current local date and time: $localNow.
        Resolve reminder dates and times in that device time zone. The phrase and epoch must describe the same instant.
        Return JSON only, using this schema:
        {
          "suggestedTitle": "short editable title",
          "summary": "one sentence summary",
          "suggestedType": "note|task|reminder",
          "suggestedSpaceName": "Work|Personal|Car|Dog|Money|Ideas|Home|Health|Learning|Inbox",
          "possibleMondayItem": false,
          "suggestedNextAction": "short action",
          "relatedTopics": ["short topic"],
          "suggestionChips": ["short chip"],
          "reminderPossible": false,
          "reminderSuggestion": {"dueAtEpochMillis": null, "phrase": null},
          "lifeSignal": "none|waiting_for|someday|reflection",
          "confidence": 0.0,
          "typeReason": "short reason",
          "spaceReason": "short reason"
        }
        User capture:
        ${rawText.trim()}
    """.trimIndent()
    }

    fun tinyAction(text: String, learningProfile: String = ""): String = """
        You are LUMA's optional cloud helper. Make this smaller and kinder.
        $UserFacingLanguageInstruction
        ${learningProfile.toLearningProfileSection()}
        Return JSON only: {"tinyAction":"one physical next step under 140 characters","why":"short reason","confidence":"high|medium|low"}
        Text:
        ${text.trim()}
    """.trimIndent()

    fun brainDump(
        rawText: String,
        sourceFragments: List<BrainDumpSuggestion> = emptyList(),
        allowedSpaces: List<String> = emptyList(),
        learningProfile: String = "",
    ): String {
        val fragments = sourceFragments.joinToString(separator = "\n") { fragment ->
            "${fragment.id}: ${fragment.rawText}"
        }.ifBlank { rawText.trim() }
        return """
        You are LUMA's optional cloud analyzer. Split a messy brain dump into reviewable suggestions.
        $UserFacingLanguageInstruction
        For mixed-language dumps, apply that rule to each source fragment independently.
        Use only these Spaces when possible: ${allowedSpaces.joinToString().ifBlank { "Work, Personal, Car, Dog, Money, Ideas, Home, Health, Learning, Inbox" }}.
        ${learningProfile.toLearningProfileSection()}
        Return every source ID exactly once. Never merge, omit, duplicate, rename, or invent a source ID.
        The source fragment text is immutable; return suggestions only.
        Suggest only; never create records. Return JSON only:
        {"items":[{"sourceId":"brain:1","title":"short title","suggestedType":"note|task|reminder","suggestedSpaceName":"Space","tinyNextAction":"small step","confidence":"high|medium|low","reason":"short reason"}]}
        Source fragments:
        $fragments
    """.trimIndent()
    }

    fun situationSummary(context: String): String = """
        You are LUMA's optional cloud Situation AI. Summarize local context without inventing data.
        $UserFacingLanguageInstruction
        Return JSON only:
        {"summary":"where the user is right now","nextAction":"one calm next action","stuck":["short item"]}
        Context:
        ${context.trim()}
    """.trimIndent()

    fun reviewSummary(context: String): String = """
        You are LUMA's optional cloud Review helper. Be calm and non-punitive.
        $UserFacingLanguageInstruction
        Return JSON only:
        {"morningScan":"short scan","eveningSweep":"short sweep","tinyAction":"one small next action"}
        Context:
        ${context.trim()}
    """.trimIndent()

    private fun String.toLearningProfileSection(): String =
        takeIf { it.isNotBlank() }
            ?.let {
                """
                Relevant local learning profile. Use only as preference guidance, not as a factual source:
                $it
                """.trimIndent()
            }
            .orEmpty()

    private const val UserFacingLanguageInstruction =
        "Detect the source language of user-authored text. Keep every user-facing output value " +
            "in that language; for mixed-language text, use the dominant language while preserving " +
            "meaningful language switches. Translate only when the user's current request explicitly " +
            "asks for translation. Keep JSON keys and required enum literals exactly as specified."
}
