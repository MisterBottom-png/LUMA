package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.SuggestedItemType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

data class CaptureAnalysis(
    val rawText: String,
    val suggestedType: SuggestedItemType,
    val suggestedSpaceName: String,
    val suggestedTitle: String = rawText.toSuggestedTitle(),
    val summary: String = rawText.toSuggestedTitle(),
    val possibleMondayItem: Boolean,
    val suggestedNextAction: String,
    val relatedTopics: List<String>,
    val suggestionChips: List<String> = emptyList(),
    val reminderPossible: Boolean,
    val suggestedReminderAt: Long? = null,
    val reminderPhrase: String? = null,
    val reminderTimeStatus: ReminderTimeStatus = ReminderTimeStatus.Unspecified,
    val lifeSignal: CaptureLifeSignal = CaptureLifeSignal.None,
    val confidence: Float,
    val typeReason: String = defaultTypeReason(suggestedType),
    val spaceReason: String = defaultSpaceReason(suggestedSpaceName),
    val analyzerFailed: Boolean = false,
    val analyzerSource: CaptureAnalyzerSource = CaptureAnalyzerSource.Local,
    val brainDumpItems: List<BrainDumpSuggestion> = emptyList(),
)

enum class ReminderTimeStatus {
    Unspecified,
    Resolved,
    NeedsClarification,
}

data class BrainDumpSuggestion(
    val id: String,
    val rawText: String,
    val title: String,
    val suggestedType: SuggestedItemType,
    val suggestedSpaceName: String,
    val confidence: Float,
    val tinyNextAction: String,
    val reason: String,
)

enum class CaptureAnalyzerSource(val label: String) {
    Local("Local suggestion"),
    Gemini("Suggested by Gemini"),
    GeminiFallback("Local suggestion"),
}

enum class CaptureLifeSignal(val label: String) {
    None("Open loop"),
    WaitingFor("Waiting for"),
    Someday("Someday"),
    Reflection("Reflection"),
}

enum class CaptureConfidence(val label: String) {
    High("High"),
    Medium("Medium"),
    Low("Low"),
}

val CaptureAnalysis.confidenceLevel: CaptureConfidence
    get() = when {
        confidence >= 0.78f -> CaptureConfidence.High
        confidence >= 0.62f -> CaptureConfidence.Medium
        else -> CaptureConfidence.Low
    }

interface CaptureAnalyzer {
    fun analyze(rawText: String): CaptureAnalysis
}

class LocalRulesCaptureAnalyzer(
    private val now: () -> Instant = Instant::now,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) : CaptureAnalyzer {
    override fun analyze(rawText: String): CaptureAnalysis {
        require(rawText.isNotBlank()) { "Capture text cannot be blank" }

        val brainDumpItems = splitBrainDump(rawText).mapIndexed { index, line ->
            val lineAnalysis = analyzeSingle(line)
            val suggestedType = when {
                lineAnalysis.confidenceLevel == CaptureConfidence.Low -> SuggestedItemType.Note
                lineAnalysis.suggestedType == SuggestedItemType.Reminder -> SuggestedItemType.Task
                else -> lineAnalysis.suggestedType
            }
            BrainDumpSuggestion(
                id = "dump_${index + 1}",
                rawText = line,
                title = line.toSuggestedTitle(),
                suggestedType = suggestedType,
                suggestedSpaceName = lineAnalysis.suggestedSpaceName,
                confidence = lineAnalysis.confidence,
                tinyNextAction = LocalReviewAnalyzer.makeSmallerText(line),
                reason = if (lineAnalysis.confidenceLevel == CaptureConfidence.Low) {
                    "This fragment is gentle enough to keep as a note or Inbox item."
                } else {
                    lineAnalysis.typeReason
                },
            )
        }

        if (brainDumpItems.isNotEmpty()) {
            return analyzeSingle(rawText).copy(
                suggestedType = SuggestedItemType.Note,
                suggestedSpaceName = "Inbox",
                possibleMondayItem = brainDumpItems.any { it.suggestedSpaceName == "Work" },
                suggestedNextAction = "Review the split suggestions one at a time",
                relatedTopics = brainDumpItems.map { it.suggestedSpaceName }.distinct(),
                reminderPossible = brainDumpItems.any { it.rawText.lowercase(Locale.ROOT).hasReminderSignal() },
                confidence = 0.74f,
                typeReason = "Multiple lines look like separate thoughts.",
                spaceReason = "The original dump stays in Inbox while you review each suggestion.",
                brainDumpItems = brainDumpItems,
            )
        }

        return analyzeSingle(rawText)
    }

    private fun analyzeSingle(rawText: String): CaptureAnalysis {
        val normalized = rawText.lowercase(Locale.ROOT)
        val reminderTime = interpretReminderTime(rawText, now(), zoneId())
        val taskSuggested = taskSignals.any { signal -> normalized.containsSignal(signal) } ||
            taskStartSignals.any { signal -> normalized.startsWith(signal) }
        val reminderPossible = normalized.hasReminderSignal() ||
            reminderTime.status != ReminderTimeStatus.Unspecified
        val spaceRule = spaceRules.firstOrNull { rule ->
            rule.signals.any { signal -> normalized.containsSignal(signal) }
        }
        val suggestedSpace = spaceRule?.name ?: "Personal"
        val suggestedType = when {
            reminderPossible && !taskSuggested -> SuggestedItemType.Reminder
            taskSuggested -> SuggestedItemType.Task
            else -> SuggestedItemType.Note
        }
        val relatedTopics = topicRules
            .filter { topic -> topic.signals.any { signal -> normalized.containsSignal(signal) } }
            .map(TopicRule::label)
            .distinct()
            .ifEmpty { listOf(suggestedSpace) }

        val signalCount = listOf(
            taskSuggested,
            reminderPossible,
            spaceRule != null,
        ).count { it }
        val confidence = (0.55f + signalCount * 0.12f).coerceAtMost(0.91f)

        return CaptureAnalysis(
            rawText = rawText,
            suggestedType = suggestedType,
            suggestedSpaceName = suggestedSpace,
            suggestedTitle = rawText.toSuggestedTitle(),
            summary = rawText.toSuggestedTitle(),
            possibleMondayItem = normalized.containsSignal("monday") ||
                (suggestedSpace == "Work" && suggestedType == SuggestedItemType.Task),
            suggestedNextAction = nextActionFor(
                rawText = rawText,
                type = suggestedType,
                reminderPossible = reminderPossible,
            ),
            relatedTopics = relatedTopics,
            suggestionChips = localChips(
                type = suggestedType,
                spaceName = suggestedSpace,
                reminderPossible = reminderPossible,
                lifeSignal = lifeSignalFor(normalized),
            ),
            reminderPossible = reminderPossible,
            suggestedReminderAt = reminderTime.epochMillis,
            reminderPhrase = reminderTime.phrase,
            reminderTimeStatus = reminderTime.status,
            lifeSignal = lifeSignalFor(normalized),
            confidence = confidence,
            typeReason = typeReasonFor(
                type = suggestedType,
                taskSuggested = taskSuggested,
                reminderPossible = reminderPossible,
            ),
            spaceReason = spaceReasonFor(
                spaceName = suggestedSpace,
                matchedSignals = spaceRule?.signals.orEmpty(),
            ),
        )
    }

    private fun nextActionFor(
        rawText: String,
        type: SuggestedItemType,
        reminderPossible: Boolean,
    ): String {
        val trimmedText = rawText.trim()
        val cleaned = actionPrefix.replace(trimmedText, "").trim().ifBlank { trimmedText }
        val action = cleaned.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(Locale.getDefault()) else character.toString()
        }
        return when {
            reminderPossible -> "Choose a time, then $action"
            type == SuggestedItemType.Task -> action
            else -> "Review and file this capture"
        }
    }

    private data class SpaceRule(val name: String, val signals: List<String>)
    private data class TopicRule(val label: String, val signals: List<String>)

    private companion object {
        val taskSignals = listOf("ask", "call", "send", "need to", "must", "remind")
        val taskStartSignals = listOf("need ", "fix ", "sort ", "prepare ", "remember ", "buy ", "get ")
        val reminderSignals = ReminderSignalWords
        val spaceRules = listOf(
            SpaceRule("Work", listOf("manager", "stakeholder", "data governance", "change management", "monday")),
            SpaceRule("Car", listOf("car", "audi", "lexus", "mazda")),
            SpaceRule("Dog", listOf("dog")),
            SpaceRule("Money", listOf("money", "pay", "salary", "budget")),
            SpaceRule("Ideas", listOf("idea", "maybe", "app", "concept")),
            SpaceRule("Learning", listOf("learn", "learning", "course", "study")),
        )
        val topicRules = listOf(
            TopicRule("manager", listOf("manager")),
            TopicRule("stakeholder", listOf("stakeholder")),
            TopicRule("Data governance", listOf("data governance")),
            TopicRule("Change management", listOf("change management")),
            TopicRule("Monday", listOf("monday")),
            TopicRule("Car", listOf("car", "audi", "lexus", "mazda")),
            TopicRule("Dog", listOf("dog")),
            TopicRule("Money", listOf("money", "pay", "salary", "budget")),
            TopicRule("Ideas", listOf("idea", "maybe", "app", "concept")),
            TopicRule("Learning", listOf("learn", "learning", "course", "study")),
        )
        val actionPrefix = Regex(
            pattern = "^(please\\s+)?(i\\s+)?(need to|must|remember to|remind me to)\\s+",
            option = RegexOption.IGNORE_CASE,
        )
    }
}

private fun lifeSignalFor(normalized: String): CaptureLifeSignal = when {
    listOf("waiting for", "wait for", "waiting on", "blocked by").any { normalized.containsSignal(it) } ->
        CaptureLifeSignal.WaitingFor

    listOf("someday", "one day", "later maybe", "maybe later").any { normalized.containsSignal(it) } ->
        CaptureLifeSignal.Someday

    listOf("feel", "reflection", "thinking about", "worry", "concern").any { normalized.containsSignal(it) } ->
        CaptureLifeSignal.Reflection

    else -> CaptureLifeSignal.None
}

private fun localChips(
    type: SuggestedItemType,
    spaceName: String,
    reminderPossible: Boolean,
    lifeSignal: CaptureLifeSignal,
): List<String> = buildList {
    add(type.displayName())
    add(spaceName)
    if (reminderPossible) add("Time hint")
    if (lifeSignal != CaptureLifeSignal.None) add(lifeSignal.label)
}.distinct().take(5)

private fun String.hasReminderSignal(): Boolean =
    ReminderSignalWords.any { signal -> containsSignal(signal) }

private val ReminderSignalWords = listOf("today", "tomorrow", "next week")

private fun String.containsSignal(signal: String): Boolean = Regex(
    pattern = "(?<![\\p{L}\\p{N}])${Regex.escape(signal)}(?![\\p{L}\\p{N}])",
).containsMatchIn(this)

private fun splitBrainDump(rawText: String): List<String> {
    val lines = rawText
        .lineSequence()
        .map { it.trim().removePrefix("-").removePrefix("*").trim() }
        .filter { it.length >= 3 }
        .toList()
    return lines.takeIf { it.size >= 2 }.orEmpty()
}

private fun String.toSuggestedTitle(): String =
    trim()
        .replace(Regex("\\s+"), " ")
        .take(90)

private fun defaultTypeReason(type: SuggestedItemType): String = when (type) {
    SuggestedItemType.Task -> "This sounds actionable."
    SuggestedItemType.Reminder -> "This sounds time-related."
    SuggestedItemType.Note -> "This reads like something to keep for later."
    SuggestedItemType.MondayItem -> "This looks work-related."
}

private fun defaultSpaceReason(spaceName: String): String = if (spaceName == "Inbox") {
    "This can stay in Inbox until it becomes clearer."
} else {
    "This seems related to $spaceName."
}

private fun typeReasonFor(
    type: SuggestedItemType,
    taskSuggested: Boolean,
    reminderPossible: Boolean,
): String = when {
    reminderPossible -> "Time words suggest a reminder may help."
    taskSuggested -> "Action words suggest this may be a task."
    type == SuggestedItemType.Note -> "No strong action words appeared, so a note is safest."
    else -> defaultTypeReason(type)
}

private fun spaceReasonFor(spaceName: String, matchedSignals: List<String>): String {
    val signal = matchedSignals.firstOrNull()
    return when {
        spaceName == "Inbox" -> "This can stay in Inbox until it becomes clearer."
        signal != null -> "\"$signal\" points toward $spaceName."
        else -> "No specific life area stood out, so Personal is the gentlest fit."
    }
}

private fun SuggestedItemType.displayName(): String = when (this) {
    SuggestedItemType.Note -> "Note"
    SuggestedItemType.Task -> "Task"
    SuggestedItemType.Reminder -> "Reminder"
    SuggestedItemType.MondayItem -> "Monday item"
}
