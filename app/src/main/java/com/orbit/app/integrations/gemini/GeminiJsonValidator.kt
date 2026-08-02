package com.orbit.app.integrations.gemini

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import org.json.JSONObject

object GeminiJsonValidator {
    fun isConnectionJson(text: String): Boolean =
        runCatching { JSONObject(text) }.isSuccess

    fun captureAnalysis(
        text: String,
        fallbackRawText: String,
        allowedSpaces: List<String> = emptyList(),
    ): CaptureAnalysis? {
        val suggestedType = extractString(text, "suggestedType")
            ?.toSuggestedItemType()
            ?: return null
        val rawSpaceName = extractString(text, "suggestedSpaceName")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val suggestedSpaceName = rawSpaceName.validSpaceOrInbox(allowedSpaces)
        val suggestedNextAction = extractString(text, "suggestedNextAction")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val confidence = extractNumber(text, "confidence")
            ?.toFloat()
            ?.takeIf { it in 0f..1f }
            ?: return null

        return CaptureAnalysis(
            rawText = fallbackRawText,
            suggestedType = suggestedType,
            suggestedTitle = extractString(text, "suggestedTitle")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                ?: fallbackRawText.toSafeTitle(),
            summary = extractString(text, "summary")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                ?: fallbackRawText.toSafeTitle(),
            suggestedSpaceName = suggestedSpaceName,
            possibleMondayItem = extractBoolean(text, "possibleMondayItem") ?: false,
            suggestedNextAction = suggestedNextAction.take(MaxTextFieldLength),
            relatedTopics = extractStringArray(text, "relatedTopics")
                .map { it.take(MaxTextFieldLength) }
                .ifEmpty { listOf(suggestedSpaceName.take(MaxSpaceNameLength)) },
            suggestionChips = extractStringArray(text, "suggestionChips")
                .map { it.take(MaxChipLength) }
                .ifEmpty { listOf(suggestedType.displayName(), suggestedSpaceName) }
                .distinct()
                .take(MaxTopics),
            reminderPossible = extractBoolean(text, "reminderPossible") ?: false,
            suggestedReminderAt = extractNumber(text, "dueAtEpochMillis")?.toLong(),
            reminderPhrase = extractString(text, "phrase")?.take(MaxTextFieldLength),
            lifeSignal = extractString(text, "lifeSignal").toLifeSignal(),
            confidence = confidence,
            typeReason = extractString(text, "typeReason")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                ?: "Gemini suggested this type.",
            spaceReason = extractString(text, "spaceReason")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                ?: "Gemini suggested this Space.",
            analyzerSource = CaptureAnalyzerSource.Gemini,
        )
    }

    fun tinyAction(text: String): String? =
        (
            extractString(text, "tinyAction")
                ?: extractString(text, "tinyStep")
                ?: extractString(text, "action")
                ?: extractString(text, "nextAction")
                ?: extractString(text, "tiny_action")
            )
            ?.trim()
            ?.takeIf { it.length in 3..MaxTextFieldLength }

    fun brainDumpSuggestions(
        text: String,
        allowedSpaces: List<String> = emptyList(),
        expectedItems: List<BrainDumpSuggestion> = emptyList(),
    ): List<BrainDumpSuggestion>? {
        val itemsBody = extractArrayBody(text, "items") ?: return null
        val itemJsonObjects = Regex("\\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL)
            .findAll(itemsBody)
            .map { match -> "{${match.groupValues[1]}}" }
            .take(MaxBrainDumpItems + 1)
            .toList()
        if (expectedItems.isNotEmpty() && itemJsonObjects.size != expectedItems.size) return null
        val items = if (expectedItems.isEmpty()) {
            itemJsonObjects.mapIndexedNotNull { index, itemJson ->
                itemJson.toBrainDumpSuggestion(index, allowedSpaces, expectedItems)
            }.take(MaxBrainDumpItems)
        } else {
            buildList {
                itemJsonObjects.forEachIndexed { index, itemJson ->
                    add(itemJson.toBrainDumpSuggestion(index, allowedSpaces, expectedItems) ?: return null)
                }
            }
        }
        if (expectedItems.isEmpty()) return items.takeIf { it.isNotEmpty() }
        if (expectedItems.size > MaxBrainDumpItems) return null
        val byId = items.associateBy { it.id }
        if (byId.size != items.size || byId.keys != expectedItems.mapTo(linkedSetOf()) { it.id }) return null
        return expectedItems.map { expected ->
            val enriched = checkNotNull(byId[expected.id])
            enriched.copy(
                rawText = expected.rawText,
                suggestedType = when {
                    expected.suggestedType == SuggestedItemType.Reminder &&
                        expected.reminderTimeStatus == ReminderTimeStatus.Resolved -> SuggestedItemType.Reminder
                    expected.suggestedType == SuggestedItemType.Task &&
                        expected.suggestedReminderAt != null -> SuggestedItemType.Task
                    else -> enriched.suggestedType
                },
                reminderTimeStatus = expected.reminderTimeStatus,
                suggestedReminderAt = expected.suggestedReminderAt,
                reminderPhrase = expected.reminderPhrase,
            )
        }
    }

    private fun String.toBrainDumpSuggestion(
        index: Int,
        allowedSpaces: List<String>,
        expectedItems: List<BrainDumpSuggestion>,
    ): BrainDumpSuggestion? {
        val sourceId = extractString(this, "sourceId")
            ?.takeIf { id -> expectedItems.any { it.id == id } }
            ?: if (expectedItems.isEmpty()) "brain:${index + 1}" else return null
        val expected = expectedItems.firstOrNull { it.id == sourceId }
        val rawText = expected?.rawText ?: extractString(this, "rawText")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val title = extractString(this, "title")
            ?.takeIf { it.isNotBlank() }
            ?.take(MaxTextFieldLength)
            ?: return null
        val suggestedType = (extractString(this, "suggestedType") ?: extractString(this, "type"))
            ?.toSuggestedItemType()
            ?: return null
        val confidence = confidenceFromStringOrNumber(this)
            ?: return null
        return BrainDumpSuggestion(
            id = sourceId,
            rawText = rawText,
            title = title,
            suggestedType = suggestedType,
            suggestedSpaceName = extractString(this, "suggestedSpaceName")
                ?.validSpaceOrInbox(allowedSpaces)
                ?: "Inbox",
            confidence = confidence,
            tinyNextAction = extractString(this, "tinyNextAction")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                ?: "Choose what this should become.",
            reason = (extractString(this, "reason") ?: extractString(this, "why"))
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                ?: "Gemini suggested this split.",
            reminderTimeStatus = expected?.reminderTimeStatus ?: ReminderTimeStatus.Unspecified,
            suggestedReminderAt = expected?.suggestedReminderAt,
            reminderPhrase = expected?.reminderPhrase,
        )
    }

    private fun confidenceFromStringOrNumber(json: String): Float? =
        extractNumber(json, "confidence")?.toFloat()?.takeIf { it in 0f..1f }
            ?: when (extractString(json, "confidence")?.lowercase()) {
                "high" -> 0.84f
                "medium" -> 0.68f
                "low" -> 0.52f
                else -> null
            }

    private fun String.toSuggestedItemType(): SuggestedItemType? = when (trim().lowercase()) {
        "note" -> SuggestedItemType.Note
        "task" -> SuggestedItemType.Task
        "reminder" -> SuggestedItemType.Reminder
        "mondayitem", "monday_item", "monday" -> SuggestedItemType.MondayItem
        else -> null
    }

    private fun String?.toLifeSignal(): CaptureLifeSignal = when (this?.trim()?.lowercase()) {
        "waiting_for", "waiting for", "waiting" -> CaptureLifeSignal.WaitingFor
        "someday", "later" -> CaptureLifeSignal.Someday
        "reflection", "concern", "open_reflection" -> CaptureLifeSignal.Reflection
        else -> CaptureLifeSignal.None
    }

    private fun String.validSpaceOrInbox(allowedSpaces: List<String>): String {
        val trimmed = trim().take(MaxSpaceNameLength)
        if (trimmed.equals("Inbox", ignoreCase = true)) return "Inbox"
        if (allowedSpaces.isEmpty()) return trimmed.ifBlank { "Inbox" }
        return allowedSpaces.firstOrNull { it.equals(trimmed, ignoreCase = true) } ?: "Inbox"
    }

    private fun SuggestedItemType.displayName(): String = when (this) {
        SuggestedItemType.Note -> "Note"
        SuggestedItemType.Task -> "Task"
        SuggestedItemType.Reminder -> "Reminder"
        SuggestedItemType.MondayItem -> "Monday item"
    }

    private fun String.toSafeTitle(): String =
        lineSequence().firstOrNull().orEmpty().trim().ifBlank { "Untitled" }.take(MaxTextFieldLength)

    private fun extractString(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return regex.find(json)?.groupValues?.get(1)?.unescapeJsonString()
    }

    private fun extractBoolean(json: String, key: String): Boolean? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return regex.find(json)?.groupValues?.get(1)?.lowercase()?.toBooleanStrictOrNull()
    }

    private fun extractNumber(json: String, key: String): Double? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
        return regex.find(json)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun extractStringArray(json: String, key: String): List<String> {
        val body = extractArrayBody(json, key) ?: return emptyList()
        return Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
            .findAll(body)
            .map { it.groupValues[1].unescapeJsonString().trim() }
            .filter { it.isNotBlank() }
            .take(MaxTopics)
            .toList()
    }

    private fun extractArrayBody(json: String, key: String): String? {
        val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL)
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun String.unescapeJsonString(): String =
        replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    private const val MaxTopics = 5
    private const val MaxBrainDumpItems = 20
    private const val MaxSpaceNameLength = 40
    private const val MaxChipLength = 28
    private const val MaxTextFieldLength = 160
    private const val MaxReasonLength = 220
}
