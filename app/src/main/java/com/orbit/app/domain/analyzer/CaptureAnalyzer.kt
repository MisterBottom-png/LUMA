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
    val reminderTimeStatus: ReminderTimeStatus = ReminderTimeStatus.Unspecified,
    val suggestedReminderAt: Long? = null,
    val reminderPhrase: String? = null,
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
    private val locale: () -> Locale = { Locale.ENGLISH },
) : CaptureAnalyzer {
    override fun analyze(rawText: String): CaptureAnalysis {
        require(rawText.isNotBlank()) { "Capture text cannot be blank" }

        val currentLocale = locale()
        val rulePacks = captureRulePacks(currentLocale)

        val brainDumpItems = splitBrainDump(rawText).mapIndexed { index, line ->
            val lineAnalysis = analyzeSingle(line)
            val suggestedType = if (lineAnalysis.confidenceLevel == CaptureConfidence.Low) {
                SuggestedItemType.Note
            } else {
                lineAnalysis.suggestedType
            }
            BrainDumpSuggestion(
                id = "brain:${index + 1}",
                rawText = line,
                title = line.toSuggestedTitle(),
                suggestedType = suggestedType,
                suggestedSpaceName = lineAnalysis.suggestedSpaceName,
                confidence = lineAnalysis.confidence,
                tinyNextAction = LocalReviewAnalyzer.makeSmallerText(line, currentLocale),
                reason = if (lineAnalysis.confidenceLevel == CaptureConfidence.Low) {
                    "This fragment is gentle enough to keep as a note or Inbox item."
                } else {
                    lineAnalysis.typeReason
                },
                reminderTimeStatus = lineAnalysis.reminderTimeStatus,
                suggestedReminderAt = lineAnalysis.suggestedReminderAt,
                reminderPhrase = lineAnalysis.reminderPhrase,
            )
        }

        if (brainDumpItems.isNotEmpty()) {
            return analyzeSingle(rawText).copy(
                suggestedType = SuggestedItemType.Note,
                suggestedSpaceName = "Inbox",
                suggestedNextAction = "Review the split suggestions one at a time",
                relatedTopics = brainDumpItems.map { it.suggestedSpaceName }.distinct(),
                reminderPossible = brainDumpItems.any {
                    it.rawText.lowercase(Locale.ROOT).hasReminderSignal(rulePacks)
                },
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
        val rulePacks = captureRulePacks(locale())
        val taskSuggested = rulePacks.any { pack ->
            pack.taskSignals.any { signal -> normalized.containsSignal(signal) } ||
                pack.taskStartSignals.any { signal -> normalized.startsWithSignal(signal) }
        }
        val currentInstant = now()
        val currentZone = zoneId()
        val currentLocale = locale()
        val reminderTime = interpretReminderTime(rawText, currentInstant, currentZone, currentLocale)
        val taskDueDate = if (
            taskSuggested && reminderTime.status == ReminderTimeStatus.Unspecified
        ) {
            interpretTaskDueDate(rawText, currentInstant, currentZone, currentLocale)
        } else {
            null
        }
        val reminderPossible = normalized.hasReminderSignal(rulePacks) ||
            reminderTime.status != ReminderTimeStatus.Unspecified
        val explicitReminder = rulePacks.any { pack ->
            pack.explicitReminderSignals.any { signal -> normalized.containsSignal(signal) }
        }
        val spaceRule = rulePacks
            .flatMap(CaptureRulePack::spaceRules)
            .map { rule ->
                rule to rule.signals.count { signal -> normalized.containsSignal(signal) }
            }
            .filter { (_, matchCount) -> matchCount > 0 }
            .maxByOrNull { (_, matchCount) -> matchCount }
            ?.first
        val suggestedSpace = spaceRule?.name ?: "Personal"
        val suggestedType = when {
            explicitReminder -> SuggestedItemType.Reminder
            taskSuggested -> SuggestedItemType.Task
            reminderPossible -> SuggestedItemType.Reminder
            else -> SuggestedItemType.Note
        }
        val relatedTopics = rulePacks
            .flatMap(CaptureRulePack::topicRules)
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
            suggestedNextAction = nextActionFor(
                rawText = rawText,
                type = suggestedType,
                reminderPossible = reminderPossible,
                rulePacks = rulePacks,
                locale = currentLocale,
            ),
            relatedTopics = relatedTopics,
            suggestionChips = localChips(
                type = suggestedType,
                spaceName = suggestedSpace,
                reminderPossible = reminderPossible,
                lifeSignal = lifeSignalFor(normalized, rulePacks),
            ),
            reminderPossible = reminderPossible,
            suggestedReminderAt = reminderTime.epochMillis ?: taskDueDate?.epochMillis,
            reminderPhrase = reminderTime.phrase ?: taskDueDate?.phrase,
            reminderTimeStatus = reminderTime.status,
            lifeSignal = lifeSignalFor(normalized, rulePacks),
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
        rulePacks: List<CaptureRulePack>,
        locale: Locale,
    ): String {
        val trimmedText = rawText.trim()
        val cleaned = rulePacks
            .asSequence()
            .map { pack -> pack.actionPrefix.replace(trimmedText, "").trim() }
            .firstOrNull { candidate -> candidate != trimmedText }
            .orEmpty()
            .ifBlank { trimmedText }
        val action = cleaned.replaceFirstChar { character ->
            if (character.isLowerCase()) character.titlecase(locale) else character.toString()
        }
        return when {
            reminderPossible -> "Choose a time, then $action"
            type == SuggestedItemType.Task -> action
            else -> "Review and file this capture"
        }
    }

}

private data class SpaceRule(val name: String, val signals: List<String>)

private data class TopicRule(val label: String, val signals: List<String>)

private data class CaptureRulePack(
    val language: String,
    val taskSignals: List<String>,
    val taskStartSignals: List<String>,
    val explicitReminderSignals: List<String>,
    val reminderSignals: List<String>,
    val spaceRules: List<SpaceRule>,
    val topicRules: List<TopicRule>,
    val waitingSignals: List<String>,
    val somedaySignals: List<String>,
    val reflectionSignals: List<String>,
    val actionPrefix: Regex,
)

private fun captureRulePacks(locale: Locale): List<CaptureRulePack> {
    val preferredLanguage = locale.language.lowercase(Locale.ROOT)
        .takeIf { language -> language in SupportedCaptureRulePacks.map { it.language } }
        ?: "en"
    return SupportedCaptureRulePacks.sortedBy { pack ->
        if (pack.language == preferredLanguage) 0 else 1
    }
}

private val EnglishCaptureRules = CaptureRulePack(
    language = "en",
    taskSignals = listOf("task", "ask", "call", "send", "need to", "must", "remind"),
    taskStartSignals = listOf("need", "fix", "sort", "prepare", "remember", "buy", "get"),
    explicitReminderSignals = listOf("remind me", "reminder"),
    reminderSignals = listOf("today", "tomorrow", "next week", "next month"),
    spaceRules = listOf(
        SpaceRule(
            "Work",
            listOf("manager", "stakeholder", "data governance", "change management"),
        ),
        SpaceRule("Car", listOf("car", "audi", "lexus", "mazda")),
        SpaceRule("Dog", listOf("dog")),
        SpaceRule("Money", listOf("money", "pay", "salary", "budget")),
        SpaceRule("Ideas", listOf("idea", "maybe", "app", "concept")),
        SpaceRule("Learning", listOf("learn", "learning", "course", "study")),
    ),
    topicRules = listOf(
        TopicRule("manager", listOf("manager")),
        TopicRule("stakeholder", listOf("stakeholder")),
        TopicRule("Data governance", listOf("data governance")),
        TopicRule("Change management", listOf("change management")),
        TopicRule("Car", listOf("car", "audi", "lexus", "mazda")),
        TopicRule("Dog", listOf("dog")),
        TopicRule("Money", listOf("money", "pay", "salary", "budget")),
        TopicRule("Ideas", listOf("idea", "maybe", "app", "concept")),
        TopicRule("Learning", listOf("learn", "learning", "course", "study")),
    ),
    waitingSignals = listOf("waiting for", "wait for", "waiting on", "blocked by"),
    somedaySignals = listOf("someday", "one day", "later maybe", "maybe later"),
    reflectionSignals = listOf("feel", "reflection", "thinking about", "worry", "concern"),
    actionPrefix = Regex(
        pattern = "^(please\\s+)?(i\\s+)?(need to|must|remember to|remind me to)\\s+",
        option = RegexOption.IGNORE_CASE,
    ),
)

private val EstonianCaptureRules = CaptureRulePack(
    language = "et",
    taskSignals = listOf(
        "ülesanne",
        "küsi",
        "küsida",
        "helista",
        "helistada",
        "saada",
        "saatma",
        "vaja",
        "pean",
        "osta",
        "ostma",
        "paranda",
        "parandada",
        "valmista",
        "valmistada",
    ),
    taskStartSignals = listOf(
        "vaja",
        "pean",
        "paranda",
        "korralda",
        "valmista",
        "mäleta",
        "osta",
        "helista",
        "saada",
    ),
    explicitReminderSignals = listOf("tuleta meelde", "tuleta mulle meelde", "meeldetuletus"),
    reminderSignals = listOf(
        "täna",
        "homme",
        "järgmisel nädalal",
        "järgmine nädal",
        "järgmisel kuul",
        "järgmine kuu",
    ),
    spaceRules = listOf(
        SpaceRule(
            "Work",
            listOf(
                "juht",
                "sidusrühm",
                "andmehaldus",
                "muudatuste juhtimine",
                "töö",
            ),
        ),
        SpaceRule("Car", listOf("auto", "audi", "lexus", "mazda")),
        SpaceRule("Dog", listOf("koer", "koera", "koerale", "koeraga")),
        SpaceRule("Money", listOf("raha", "maksa", "maksma", "palk", "eelarve")),
        SpaceRule("Ideas", listOf("idee", "võib-olla", "rakendus", "kontseptsioon")),
        SpaceRule("Learning", listOf("õpi", "õppida", "õppimine", "kursus")),
    ),
    topicRules = listOf(
        TopicRule("Work", listOf("juht", "sidusrühm", "töö")),
        TopicRule("Data governance", listOf("andmehaldus")),
        TopicRule("Change management", listOf("muudatuste juhtimine")),
        TopicRule("Car", listOf("auto", "audi", "lexus", "mazda")),
        TopicRule("Dog", listOf("koer", "koera", "koerale", "koeraga")),
        TopicRule("Money", listOf("raha", "maksa", "maksma", "palk", "eelarve")),
        TopicRule("Ideas", listOf("idee", "võib-olla", "rakendus", "kontseptsioon")),
        TopicRule("Learning", listOf("õpi", "õppida", "õppimine", "kursus")),
    ),
    waitingSignals = listOf("ootan", "ootab", "sõltub", "blokeeritud"),
    somedaySignals = listOf("kunagi", "ühel päeval", "võib-olla hiljem"),
    reflectionSignals = listOf("tunnen", "mõtisklus", "mõtlen", "mure", "muretsen"),
    actionPrefix = Regex(
        pattern = "^(palun\\s+)?(mul\\s+on\\s+)?(vaja|pean|tuleta(?:\\s+mulle)?\\s+meelde)\\s+",
        option = RegexOption.IGNORE_CASE,
    ),
)

private val RussianCaptureRules = CaptureRulePack(
    language = "ru",
    taskSignals = listOf(
        "задача",
        "спросить",
        "позвонить",
        "позвони",
        "отправить",
        "нужно",
        "надо",
        "должен",
        "должна",
        "должны",
        "купить",
        "сделать",
        "подготовить",
        "исправить",
        "напомни",
    ),
    taskStartSignals = listOf(
        "нужно",
        "надо",
        "исправить",
        "подготовить",
        "купить",
        "позвонить",
        "позвони",
        "отправить",
        "сделать",
    ),
    explicitReminderSignals = listOf("напомни", "напомни мне", "напоминание"),
    reminderSignals = listOf(
        "сегодня",
        "завтра",
        "на следующей неделе",
        "следующая неделя",
        "в следующем месяце",
        "следующий месяц",
    ),
    spaceRules = listOf(
        SpaceRule(
            "Work",
            listOf(
                "руководитель",
                "заинтересованная сторона",
                "управление данными",
                "управление изменениями",
                "работа",
            ),
        ),
        SpaceRule("Car", listOf("машина", "авто", "audi", "lexus", "mazda")),
        SpaceRule("Dog", listOf("собака", "собаку", "собаке", "пёс", "пса")),
        SpaceRule("Money", listOf("деньги", "оплатить", "заплатить", "зарплата", "бюджет")),
        SpaceRule("Ideas", listOf("идея", "может быть", "приложение", "концепция")),
        SpaceRule("Learning", listOf("учиться", "обучение", "курс", "изучить")),
    ),
    topicRules = listOf(
        TopicRule("Work", listOf("руководитель", "заинтересованная сторона", "работа")),
        TopicRule("Data governance", listOf("управление данными")),
        TopicRule("Change management", listOf("управление изменениями")),
        TopicRule("Car", listOf("машина", "авто", "audi", "lexus", "mazda")),
        TopicRule("Dog", listOf("собака", "собаку", "собаке", "пёс", "пса")),
        TopicRule("Money", listOf("деньги", "оплатить", "заплатить", "зарплата", "бюджет")),
        TopicRule("Ideas", listOf("идея", "может быть", "приложение", "концепция")),
        TopicRule("Learning", listOf("учиться", "обучение", "курс", "изучить")),
    ),
    waitingSignals = listOf("жду", "ожидаю", "зависит от", "заблокировано"),
    somedaySignals = listOf("когда-нибудь", "однажды", "может быть позже"),
    reflectionSignals = listOf("чувствую", "размышление", "думаю", "беспокоюсь"),
    actionPrefix = Regex(
        pattern = "^(пожалуйста[,\\s]+)?(мне\\s+)?(нужно|надо|должен|должна|напомни(?:\\s+мне)?)\\s+",
        option = RegexOption.IGNORE_CASE,
    ),
)

private val SupportedCaptureRulePacks = listOf(
    EnglishCaptureRules,
    EstonianCaptureRules,
    RussianCaptureRules,
)

private fun lifeSignalFor(
    normalized: String,
    rulePacks: List<CaptureRulePack>,
): CaptureLifeSignal = when {
    rulePacks.any { pack ->
        pack.waitingSignals.any { signal -> normalized.containsSignal(signal) }
    } ->
        CaptureLifeSignal.WaitingFor

    rulePacks.any { pack ->
        pack.somedaySignals.any { signal -> normalized.containsSignal(signal) }
    } ->
        CaptureLifeSignal.Someday

    rulePacks.any { pack ->
        pack.reflectionSignals.any { signal -> normalized.containsSignal(signal) }
    } ->
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

private fun String.hasReminderSignal(rulePacks: List<CaptureRulePack>): Boolean =
    rulePacks.any { pack ->
        (pack.reminderSignals + pack.explicitReminderSignals).any { signal -> containsSignal(signal) }
    }

private fun String.containsSignal(signal: String): Boolean = Regex(
    pattern = "(?<![\\p{L}\\p{N}])${Regex.escape(signal)}(?![\\p{L}\\p{N}])",
).containsMatchIn(this)

private fun String.startsWithSignal(signal: String): Boolean = Regex(
    pattern = "^${Regex.escape(signal)}(?![\\p{L}\\p{N}])",
).containsMatchIn(this)

private fun splitBrainDump(rawText: String): List<String> {
    val lines = rawText
        .lineSequence()
        .map { it.trim().removePrefix("-").removePrefix("*").trim() }
        .filter { it.isNotBlank() }
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
