package com.orbit.app.domain.analyzer

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

internal data class ReminderTimeInterpretation(
    val status: ReminderTimeStatus,
    val epochMillis: Long? = null,
    val phrase: String? = null,
)

internal data class TaskDueDateInterpretation(
    val epochMillis: Long,
    val phrase: String,
)

internal fun interpretReminderTime(
    rawText: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
): ReminderTimeInterpretation {
    val normalized = rawText.normalizedForRules()
    val rulePacks = reminderRulePacks(locale)
    val dateMatches = findDateMatches(normalized, rulePacks)
    val dateRelations = dateMatches.map(DateMatch::relation).distinct()
    if (dateRelations.size > 1) return needsClarification()

    val dateRelation = dateRelations.singleOrNull()
    val dateMatch = dateRelation?.let { relation ->
        dateMatches
            .filter { it.relation == relation }
            .minByOrNull(DateMatch::startIndex)
    }
    val hasStrongTimeContext = dateRelation != null || rulePacks.any { pack ->
        pack.timeContextSignals.any { signal -> normalized.containsRulePhrase(signal) }
    }

    val occupiedRanges = mutableListOf<IntRange>()
    val times = mutableListOf<LocalTime>()
    var invalidTimeFound = false

    AmPmRegex.findAll(normalized).forEach { match ->
        occupiedRanges += match.range
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val isPm = match.groupValues[3].startsWith("p")
        val resolvedHour = when {
            isPm && hour != 12 -> hour + 12
            !isPm && hour == 12 -> 0
            else -> hour
        }
        times += LocalTime.of(resolvedHour, minute)
    }

    NamedDayPeriodTimeRegex.findAll(normalized).forEach { match ->
        if (occupiedRanges.any { it.overlaps(match.range) }) return@forEach
        occupiedRanges += match.range
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifBlank { "0" }.toInt()
        val period = match.groupValues[3]
        val resolvedHour = when (period) {
            "in the morning", "hommikul", "утра" -> if (hour == 12) 0 else hour
            else -> if (hour in 1..11) hour + 12 else hour
        }
        times += LocalTime.of(resolvedHour, minute)
    }

    SeparatedTimeRegex.findAll(normalized).forEach { match ->
        if (occupiedRanges.any { it.overlaps(match.range) }) return@forEach
        val isDotSeparated = match.groupValues[2] == "."
        val isStandalone = normalized.trim() == match.value
        if (isDotSeparated && !hasStrongTimeContext && !isStandalone) return@forEach
        occupiedRanges += match.range
        times += LocalTime.of(match.groupValues[1].toInt(), match.groupValues[3].toInt())
    }

    MarkedHourRegexes.forEach { regex ->
        regex.findAll(normalized).forEach { match ->
            if (!hasStrongTimeContext || occupiedRanges.any { it.overlaps(match.range) }) {
                return@forEach
            }
            occupiedRanges += match.range
            times += LocalTime.of(match.groupValues[1].toInt(), 0)
        }
    }

    CompactTimeRegex.findAll(normalized).forEach { match ->
        if (occupiedRanges.any { it.overlaps(match.range) }) return@forEach
        val isStandalone = normalized.trim() == match.value
        if (!hasStrongTimeContext && !isStandalone) return@forEach
        val digits = match.value
        val hour = digits.dropLast(2).toInt()
        val minute = digits.takeLast(2).toInt()
        if (hour !in 0..23 || minute !in 0..59) {
            invalidTimeFound = true
        } else {
            occupiedRanges += match.range
            times += LocalTime.of(hour, minute)
        }
    }

    if (invalidTimeFound) return needsClarification()
    val distinctTimes = times.distinct()
    if (distinctTimes.size > 1) return needsClarification()
    val time = distinctTimes.singleOrNull()
        ?: return if (dateRelation != null && rulePacks.any { pack ->
                pack.reminderIntentSignals.any { signal -> normalized.containsRulePhrase(signal) }
            }
        ) {
            needsClarification()
        } else {
            ReminderTimeInterpretation(ReminderTimeStatus.Unspecified)
        }

    val today = now.atZone(zoneId).toLocalDate()
    val date = dateFor(today, dateRelation)
    val dayLabel = dateMatch?.displayLabel
        ?: rulePacks.first().dateRules.getValue(RelativeDate.Today).displayLabel
    return ReminderTimeInterpretation(
        status = ReminderTimeStatus.Resolved,
        epochMillis = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli(),
        phrase = "%02d:%02d %s".format(Locale.ROOT, time.hour, time.minute, dayLabel),
    )
}

internal fun interpretTaskDueDate(
    rawText: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
): TaskDueDateInterpretation? {
    val normalized = rawText.normalizedForRules()
    val matches = findDateMatches(normalized, reminderRulePacks(locale))
    val relations = matches.map(DateMatch::relation).distinct()
    val relation = relations.singleOrNull() ?: return null
    val match = matches
        .filter { it.relation == relation }
        .minByOrNull(DateMatch::startIndex)
        ?: return null
    val today = now.atZone(zoneId).toLocalDate()
    return TaskDueDateInterpretation(
        epochMillis = dateFor(today, relation)
            .atTime(23, 59)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli(),
        phrase = match.displayLabel,
    )
}

private fun dateFor(today: LocalDate, relation: RelativeDate?): LocalDate = when (relation) {
    RelativeDate.Tomorrow -> today.plusDays(1)
    RelativeDate.NextWeek -> today.plusWeeks(1)
    RelativeDate.NextMonth -> today.plusMonths(1)
    RelativeDate.Today, null -> today
}

private fun needsClarification() = ReminderTimeInterpretation(
    status = ReminderTimeStatus.NeedsClarification,
)

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

private enum class RelativeDate {
    Today,
    Tomorrow,
    NextWeek,
    NextMonth,
}

private data class DateRule(
    val displayLabel: String,
    val phrases: List<String>,
)

private data class ReminderLanguageRulePack(
    val language: String,
    val dateRules: Map<RelativeDate, DateRule>,
    val timeContextSignals: List<String>,
    val reminderIntentSignals: List<String>,
)

private data class DateMatch(
    val relation: RelativeDate,
    val displayLabel: String,
    val startIndex: Int,
)

private fun findDateMatches(
    normalized: String,
    rulePacks: List<ReminderLanguageRulePack>,
): List<DateMatch> = buildList {
    rulePacks.forEach { pack ->
        pack.dateRules.forEach { (relation, rule) ->
            rule.phrases.forEach { phrase ->
                rulePhraseRegex(phrase).findAll(normalized).forEach { match ->
                    add(
                        DateMatch(
                            relation = relation,
                            displayLabel = rule.displayLabel,
                            startIndex = match.range.first,
                        ),
                    )
                }
            }
        }
    }
}

private fun reminderRulePacks(locale: Locale): List<ReminderLanguageRulePack> {
    val preferredLanguage = locale.language.lowercase(Locale.ROOT)
        .takeIf { language -> language in SupportedReminderRulePacks.map { it.language } }
        ?: "en"
    return SupportedReminderRulePacks.sortedBy { pack ->
        if (pack.language == preferredLanguage) 0 else 1
    }
}

private fun String.normalizedForRules(): String =
    lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

private fun String.containsRulePhrase(phrase: String): Boolean =
    rulePhraseRegex(phrase).containsMatchIn(this)

private fun rulePhraseRegex(phrase: String): Regex {
    val flexiblePhrase = phrase
        .trim()
        .split(Regex("\\s+"))
        .joinToString("\\s+") { token -> Regex.escape(token) }
    return Regex("(?<![\\p{L}\\p{N}])$flexiblePhrase(?![\\p{L}\\p{N}])")
}

private val EnglishReminderRules = ReminderLanguageRulePack(
    language = "en",
    dateRules = mapOf(
        RelativeDate.Today to DateRule("today", listOf("today")),
        RelativeDate.Tomorrow to DateRule("tomorrow", listOf("tomorrow")),
        RelativeDate.NextWeek to DateRule("next week", listOf("next week")),
        RelativeDate.NextMonth to DateRule("next month", listOf("next month")),
    ),
    timeContextSignals = listOf("remind me", "reminder", "time", "at", "set for", "schedule"),
    reminderIntentSignals = listOf("remind me", "reminder"),
)

private val EstonianReminderRules = ReminderLanguageRulePack(
    language = "et",
    dateRules = mapOf(
        RelativeDate.Today to DateRule("täna", listOf("täna")),
        RelativeDate.Tomorrow to DateRule("homme", listOf("homme")),
        RelativeDate.NextWeek to DateRule(
            "järgmisel nädalal",
            listOf("järgmisel nädalal", "järgmine nädal"),
        ),
        RelativeDate.NextMonth to DateRule(
            "järgmisel kuul",
            listOf("järgmisel kuul", "järgmine kuu"),
        ),
    ),
    timeContextSignals = listOf(
        "tuleta meelde",
        "tuleta mulle meelde",
        "meeldetuletus",
        "kell",
        "ajaks",
        "aeg",
        "planeeri",
    ),
    reminderIntentSignals = listOf("tuleta meelde", "tuleta mulle meelde", "meeldetuletus"),
)

private val RussianReminderRules = ReminderLanguageRulePack(
    language = "ru",
    dateRules = mapOf(
        RelativeDate.Today to DateRule("сегодня", listOf("сегодня")),
        RelativeDate.Tomorrow to DateRule("завтра", listOf("завтра")),
        RelativeDate.NextWeek to DateRule(
            "на следующей неделе",
            listOf("на следующей неделе", "следующая неделя", "в следующую неделю"),
        ),
        RelativeDate.NextMonth to DateRule(
            "в следующем месяце",
            listOf("в следующем месяце", "следующий месяц", "на следующий месяц"),
        ),
    ),
    timeContextSignals = listOf(
        "напомни",
        "напомни мне",
        "напоминание",
        "время",
        "назначить",
        "запланировать",
    ),
    reminderIntentSignals = listOf("напомни", "напомни мне", "напоминание"),
)

private val SupportedReminderRulePacks = listOf(
    EnglishReminderRules,
    EstonianReminderRules,
    RussianReminderRules,
)

private val AmPmRegex = Regex(
    "(?<![\\p{L}\\p{N}])(1[0-2]|0?[1-9])(?:[:.]([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}\\p{N}])",
)
private val NamedDayPeriodTimeRegex = Regex(
    "(?<![\\p{L}\\p{N}])([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s*(in\\s+the\\s+morning|in\\s+the\\s+afternoon|in\\s+the\\s+evening|hommikul|pärastlõunal|õhtul|утра|дня|вечера)(?![\\p{L}\\p{N}])",
)
private val SeparatedTimeRegex = Regex(
    "(?<![\\p{L}\\p{N}])([01]?\\d|2[0-3])([:.])([0-5]\\d)(?![\\p{L}\\p{N}])",
)
private val MarkedHourRegexes = listOf(
    Regex("(?<![\\p{L}\\p{N}])at\\s+([01]?\\d|2[0-3])(?![\\p{L}\\p{N}])"),
    Regex("(?<![\\p{L}\\p{N}])kell\\s+([01]?\\d|2[0-3])(?![\\p{L}\\p{N}])"),
    Regex("(?<![\\p{L}\\p{N}])(?:в|к)\\s+([01]?\\d|2[0-3])(?![\\p{L}\\p{N}])"),
)
private val CompactTimeRegex = Regex("(?<![\\p{L}\\p{N}])\\d{3,4}(?![\\p{L}\\p{N}])")
