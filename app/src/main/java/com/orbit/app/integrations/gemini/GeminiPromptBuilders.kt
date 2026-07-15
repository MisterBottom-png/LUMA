package com.orbit.app.integrations.gemini

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
        Normalize all output fields to calm English, even when the capture is multilingual.
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
        ${learningProfile.toLearningProfileSection()}
        Return JSON only: {"tinyAction":"one physical next step under 140 characters","why":"short reason","confidence":"high|medium|low"}
        Text:
        ${text.trim()}
    """.trimIndent()

    fun brainDump(
        rawText: String,
        allowedSpaces: List<String> = emptyList(),
        learningProfile: String = "",
    ): String = """
        You are LUMA's optional cloud analyzer. Split a messy brain dump into reviewable suggestions.
        Normalize all titles, reasons, and tiny next actions to calm English.
        Use only these Spaces when possible: ${allowedSpaces.joinToString().ifBlank { "Work, Personal, Car, Dog, Money, Ideas, Home, Health, Learning, Inbox" }}.
        ${learningProfile.toLearningProfileSection()}
        Suggest only; never create records. Return JSON only:
        {"items":[{"rawText":"original fragment","title":"short title","suggestedType":"note|task|reminder","suggestedSpaceName":"Space","tinyNextAction":"small step","confidence":"high|medium|low","reason":"short reason"}]}
        Brain dump:
        ${rawText.trim()}
    """.trimIndent()

    fun situationSummary(context: String): String = """
        You are LUMA's optional cloud Situation AI. Summarize local context without inventing data.
        Return JSON only:
        {"summary":"where the user is right now","nextAction":"one calm next action","stuck":["short item"]}
        Context:
        ${context.trim()}
    """.trimIndent()

    fun reviewSummary(context: String): String = """
        You are LUMA's optional cloud Review helper. Be calm and non-punitive.
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
}
