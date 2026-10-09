package com.orbit.app.integrations.gemini

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import org.json.JSONObject

object GeminiJsonValidator {
    /** Gemini reminder times further ahead than this are treated as invalid. */
    private const val MaxReminderHorizonMillis = 2L * 366L * 24L * 60L * 60_000L
    private const val PastToleranceMillis = 60_000L

    fun isConnectionJson(text: String): Boolean = GeminiJson.parseObject(text) != null

    fun captureAnalysis(
        text: String,
        fallbackRawText: String,
        allowedSpaces: List<String> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): CaptureAnalysis? {
        val json = GeminiJson.parseObject(text) ?: return null
        val suggestedType = json.stringValue("suggestedType")
            ?.toSuggestedItemType()
            ?: return null
        val rawSpaceName = json.stringValue("suggestedSpaceName")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val suggestedSpaceName = rawSpaceName.validSpaceOrInbox(allowedSpaces)
        val suggestedNextAction = json.stringValue("suggestedNextAction")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val confidence = json.numberValue("confidence")
            ?.toFloat()
            ?.takeIf { it in 0f..1f }
            ?: return null

        return CaptureAnalysis(
            rawText = fallbackRawText,
            suggestedType = suggestedType,
            suggestedTitle = json.stringValue("suggestedTitle")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                ?: fallbackRawText.toSafeTitle(),
            summary = json.stringValue("summary")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                ?: fallbackRawText.toSafeTitle(),
            suggestedSpaceName = suggestedSpaceName,
            suggestedNextAction = suggestedNextAction.take(MaxTextFieldLength),
            relatedTopics = json.stringList("relatedTopics", MaxTopics)
                .map { it.take(MaxTextFieldLength) }
                .ifEmpty { listOf(suggestedSpaceName.take(MaxSpaceNameLength)) },
            suggestedLabels = json.stringList("suggestedLabels", MaxTopics)
                .map { it.replace(Regex("\\s+"), " ").take(MaxChipLength) }
                .filter { it.isNotBlank() && !it.equals(suggestedSpaceName, ignoreCase = true) }
                .distinctBy { it.lowercase() }
                .take(3),
            suggestionChips = json.stringList("suggestionChips", MaxTopics)
                .map { it.take(MaxChipLength) }
                .distinct(),
            reminderPossible = json.booleanValue("reminderPossible") ?: false,
            suggestedReminderAt = json.numberValue("dueAtEpochMillis")
                ?.toLong()
                ?.takeIf { it.isPlausibleReminderTime(now) },
            reminderPhrase = json.stringValue("phrase")?.take(MaxTextFieldLength),
            lifeSignal = json.stringValue("lifeSignal").toLifeSignal(),
            confidence = confidence,
            typeReason = json.stringValue("typeReason")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                .orEmpty(),
            spaceReason = json.stringValue("spaceReason")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                .orEmpty(),
            analyzerSource = CaptureAnalyzerSource.Gemini,
        ).let { analysis ->
            // A rejected time must not leave a phrase that claims a time was understood.
            if (analysis.suggestedReminderAt == null) analysis.copy(reminderPhrase = null) else analysis
        }
    }

    /** Past or implausibly distant times (for example 1970 or 2099) are rejected. */
    internal fun Long.isPlausibleReminderTime(now: Long): Boolean =
        this > 0L && this >= now - PastToleranceMillis && this <= now + MaxReminderHorizonMillis

    fun tinyAction(text: String): String? {
        val json = GeminiJson.parseObject(text) ?: return null
        return listOf("tinyAction", "tinyStep", "action", "nextAction", "tiny_action")
            .firstNotNullOfOrNull { json.stringValue(it) }
            ?.takeIf { it.length in 3..MaxTextFieldLength }
    }

    fun brainDumpSuggestions(
        text: String,
        allowedSpaces: List<String> = emptyList(),
        expectedItems: List<BrainDumpSuggestion> = emptyList(),
    ): List<BrainDumpSuggestion>? {
        val json = GeminiJson.parseObject(text) ?: return null
        val array = json.optJSONArray("items") ?: return null
        val itemJsonObjects = (0 until minOf(array.length(), MaxBrainDumpItems + 1))
            .map { array.optJSONObject(it) ?: return null }
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

    private fun JSONObject.toBrainDumpSuggestion(
        index: Int,
        allowedSpaces: List<String>,
        expectedItems: List<BrainDumpSuggestion>,
    ): BrainDumpSuggestion? {
        val sourceId = stringValue("sourceId")
            ?.takeIf { id -> expectedItems.any { it.id == id } }
            ?: if (expectedItems.isEmpty()) "brain:${index + 1}" else return null
        val expected = expectedItems.firstOrNull { it.id == sourceId }
        val rawText = expected?.rawText ?: stringValue("rawText")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val title = stringValue("title")
            ?.takeIf { it.isNotBlank() }
            ?.take(MaxTextFieldLength)
            ?: return null
        val suggestedType = (stringValue("suggestedType") ?: stringValue("type"))
            ?.toSuggestedItemType()
            ?: return null
        val confidence = confidenceFromStringOrNumber(this) ?: return null
        return BrainDumpSuggestion(
            id = sourceId,
            rawText = rawText,
            title = title,
            suggestedType = suggestedType,
            suggestedSpaceName = stringValue("suggestedSpaceName")
                ?.validSpaceOrInbox(allowedSpaces)
                ?: "Inbox",
            confidence = confidence,
            tinyNextAction = stringValue("tinyNextAction")
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxTextFieldLength)
                .orEmpty(),
            reason = (stringValue("reason") ?: stringValue("why"))
                ?.takeIf { it.isNotBlank() }
                ?.take(MaxReasonLength)
                .orEmpty(),
            reminderTimeStatus = expected?.reminderTimeStatus ?: ReminderTimeStatus.Unspecified,
            suggestedReminderAt = expected?.suggestedReminderAt,
            reminderPhrase = expected?.reminderPhrase,
        )
    }

    private fun confidenceFromStringOrNumber(json: JSONObject): Float? =
        json.numberValue("confidence")?.toFloat()?.takeIf { it in 0f..1f }
            ?: when (json.stringValue("confidence")?.lowercase()) {
                "high" -> 0.84f
                "medium" -> 0.68f
                "low" -> 0.52f
                else -> null
            }

    private fun String.toSuggestedItemType(): SuggestedItemType? = when (trim().lowercase()) {
        "note" -> SuggestedItemType.Note
        "task" -> SuggestedItemType.Task
        "reminder" -> SuggestedItemType.Reminder
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

    private fun String.toSafeTitle(): String =
        lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty().take(MaxTextFieldLength)

    private const val MaxTopics = 5
    private const val MaxBrainDumpItems = 20
    private const val MaxSpaceNameLength = 40
    private const val MaxChipLength = 28
    private const val MaxTextFieldLength = 160
    private const val MaxReasonLength = 220
}
