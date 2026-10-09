package com.orbit.app.domain.analyzer

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
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

/**
 * Reads a reminder time from English, Estonian or Russian text.
 *
 * The reader prefers asking over guessing: conflicting dates or times, an hour that
 * could be morning or evening, an explicit day whose time already passed, a period
 * such as "tonight" without an hour, or a time more than two years ahead all return
 * [ReminderTimeStatus.NeedsClarification].
 */
internal fun interpretReminderTime(
    rawText: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
): ReminderTimeInterpretation {
    val packs = reminderRulePacks(locale)
    val scan = TimeExpressionScanner(rawText.normalizedForRules(), packs).scan()
    val nowLocal = now.atZone(zoneId).toLocalDateTime().truncatedTo(ChronoUnit.MINUTES)
    val today = nowLocal.toLocalDate()

    if (scan.invalidTime) return needsClarification()
    if (scan.adjustments.isNotEmpty()) return needsClarification()

    val dates = scan.dates.map { it.resolve(today) ?: return needsClarification() }.distinctBy { it.date }
    val periods = scan.periods.map { it.period }.distinct()
    if (dates.size > 1 || periods.size > 1) return needsClarification()

    // "in 20 minutes" / "in 2 hours" is a complete instant on its own.
    val clockDuration = scan.durations.singleOrNull { it.isClockDuration }
    if (scan.durations.size > 1) return needsClarification()
    if (clockDuration != null) {
        if (scan.times.isNotEmpty() || dates.isNotEmpty()) return needsClarification()
        val target = nowLocal.plus(clockDuration.duration)
        return resolved(target, zoneId, now, packs, today, labelOverride = null)
    }

    val durationDate = scan.durations.singleOrNull()?.let { duration ->
        ResolvedDate(today.plusDays(duration.duration.toDays()), duration.label)
    }
    if (durationDate != null && dates.isNotEmpty()) return needsClarification()
    val date = dates.singleOrNull() ?: durationDate
        ?: scan.periods.firstOrNull { it.impliesToday }?.let { ResolvedDate(today, null) }
    val period = periods.singleOrNull()

    val times = scan.times.map { hit -> hit.applyPeriod(period) ?: return needsClarification() }
        .distinctBy { it.time }
    if (times.size > 1) return needsClarification()
    val time = times.singleOrNull()

    if (time == null) {
        val wantsReminder = packs.any { pack -> pack.reminderIntentSignals.any(scan.text::containsRulePhrase) }
        return if ((date != null || period != null) && wantsReminder) {
            needsClarification()
        } else {
            ReminderTimeInterpretation(ReminderTimeStatus.Unspecified)
        }
    }

    if (date != null) {
        val target = date.date.atTime(time.time)
        // An explicitly named day whose time already passed is unclear: ask.
        if (target.isBefore(nowLocal)) return needsClarification()
        return resolved(target, zoneId, now, packs, today, date.label)
    }

    // No day given: today if still ahead, otherwise the next morning/evening that fits.
    val todayAt = today.atTime(time.time)
    if (!todayAt.isBefore(nowLocal)) {
        // Still ahead today ("at 9" at 08:00 means 09:00).
        return resolved(todayAt, zoneId, now, packs, today, labelOverride = null)
    }
    if (time.ambiguousHalfDay) {
        val evening = today.atTime(time.time.plusHours(12))
        // "at 9" typed at 15:00 could be 21:00 today or 09:00 tomorrow: ask.
        if (!evening.isBefore(nowLocal)) return needsClarification()
    }
    return resolved(today.plusDays(1).atTime(time.time), zoneId, now, packs, today, labelOverride = null)
}

/**
 * Reads a due day for a task (no clock time). Returns null when no single day is named.
 */
internal fun interpretTaskDueDate(
    rawText: String,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.ENGLISH,
): TaskDueDateInterpretation? {
    val packs = reminderRulePacks(locale)
    val scan = TimeExpressionScanner(rawText.normalizedForRules(), packs).scan()
    val today = now.atZone(zoneId).toLocalDate()
    val dates = scan.dates.map { it.resolve(today) ?: return null }.distinctBy { it.date } +
        scan.durations.filterNot { it.isClockDuration }.map {
            ResolvedDate(today.plusDays(it.duration.toDays()), it.label)
        }
    val date = dates.distinctBy { it.date }.singleOrNull() ?: return null
    if (date.date.isAfter(today.plusYears(MaxYearsAhead))) return null
    return TaskDueDateInterpretation(
        epochMillis = date.date.atTime(23, 59).atZone(zoneId).toInstant().toEpochMilli(),
        phrase = date.label ?: packs.first().dateLabel(date.date, today),
    )
}

/**
 * Reads a relative change such as "1 hour earlier", "15 minutit hiljem" or
 * "на час раньше". Returns the signed shift, or null when the text has none.
 */
internal fun interpretTimeAdjustment(
    rawText: String,
    locale: Locale = Locale.ENGLISH,
): Duration? {
    val scan = TimeExpressionScanner(rawText.normalizedForRules(), reminderRulePacks(locale)).scan()
    return scan.adjustments.singleOrNull()
}

private const val MaxYearsAhead = 2L

private fun resolved(
    target: LocalDateTime,
    zoneId: ZoneId,
    now: Instant,
    packs: List<ReminderLanguageRulePack>,
    today: LocalDate,
    labelOverride: String?,
): ReminderTimeInterpretation {
    val instant = target.atZone(zoneId).toInstant()
    if (instant.isAfter(now.atZone(zoneId).plusYears(MaxYearsAhead).toInstant())) return needsClarification()
    val label = labelOverride ?: packs.first().dateLabel(target.toLocalDate(), today)
    return ReminderTimeInterpretation(
        status = ReminderTimeStatus.Resolved,
        epochMillis = instant.toEpochMilli(),
        phrase = "%02d:%02d %s".format(Locale.ROOT, target.hour, target.minute, label),
    )
}

private fun needsClarification() = ReminderTimeInterpretation(
    status = ReminderTimeStatus.NeedsClarification,
)

// region Scanner

private enum class DayPeriod { Morning, Afternoon, Evening, Night }

private data class DateHit(
    val label: String,
    val resolver: (LocalDate) -> LocalDate?,
) {
    fun resolve(today: LocalDate): ResolvedDate? = resolver(today)?.let { ResolvedDate(it, label) }
}

private data class ResolvedDate(val date: LocalDate, val label: String?)

private data class TimeHit(
    val hour: Int,
    val minute: Int,
    /** Written without am/pm or a 24-hour hint, e.g. "at 9" or "kell 9". */
    val bareHour: Boolean,
    /** Already carries its own half of the day ("9 pm", "8 вечера"). */
    val fixedPeriod: Boolean,
)

private data class ResolvedTime(val time: LocalTime, val ambiguousHalfDay: Boolean)

private fun TimeHit.applyPeriod(period: DayPeriod?): ResolvedTime? {
    if (fixedPeriod || period == null) {
        return ResolvedTime(
            time = LocalTime.of(hour, minute),
            ambiguousHalfDay = bareHour && !fixedPeriod && hour in 1..11,
        )
    }
    val adjusted = when (period) {
        DayPeriod.Morning -> when {
            hour == 12 -> 0
            hour <= 11 -> hour
            else -> return null
        }
        DayPeriod.Afternoon, DayPeriod.Evening -> when {
            hour in 1..11 -> hour + 12
            hour in 12..23 -> hour
            else -> return null
        }
        DayPeriod.Night -> when {
            hour == 12 -> 0
            hour in 1..5 -> hour
            hour in 6..11 -> hour + 12
            else -> hour
        }
    }
    return ResolvedTime(LocalTime.of(adjusted, minute), ambiguousHalfDay = false)
}

private data class PeriodHit(val period: DayPeriod, val impliesToday: Boolean)

private data class DurationHit(val duration: Duration, val label: String) {
    val isClockDuration: Boolean get() = duration < Duration.ofDays(1)
}

private class ScanResult(
    val text: String,
    val dates: List<DateHit>,
    val times: List<TimeHit>,
    val periods: List<PeriodHit>,
    val durations: List<DurationHit>,
    val adjustments: List<Duration>,
    val invalidTime: Boolean,
)

private class TimeExpressionScanner(
    val text: String,
    private val packs: List<ReminderLanguageRulePack>,
) {
    private val occupied = mutableListOf<IntRange>()
    private val dates = mutableListOf<DateHit>()
    private val times = mutableListOf<TimeHit>()
    private val periods = mutableListOf<PeriodHit>()
    private val durations = mutableListOf<DurationHit>()
    private val adjustments = mutableListOf<Duration>()
    private var invalidTime = false

    fun scan(): ScanResult {
        scanAdjustments()
        scanDurations()
        scanNumericDates()
        scanMonthNameDates()
        scanAmPmTimes()
        scanNoon()
        scanPeriodHourTimes()
        scanPeriodWords()
        scanRelativeDates()
        scanWeekdays()
        val strongContext = dates.isNotEmpty() || periods.isNotEmpty() ||
            packs.any { pack -> pack.timeContextSignals.any(text::containsRulePhrase) }
        scanSeparatedTimes(strongContext)
        scanMarkedHours()
        scanCompactTimes(strongContext)
        return ScanResult(text, dates, times, periods, durations, adjustments, invalidTime)
    }

    private fun free(range: IntRange) = occupied.none { it.first <= range.last && range.first <= it.last }

    private fun claim(range: IntRange): Boolean {
        if (!free(range)) return false
        occupied += range
        return true
    }

    private fun scanAdjustments() {
        AdjustmentRegexes.forEach { (regex, sign) ->
            regex.findAll(text).forEach { match ->
                val unitText = match.groups["u"]?.value.orEmpty()
                val amount = parseAmount(match.groups["n"]?.value) ?: implicitOne(unitText) ?: return@forEach
                val unit = unitFor(unitText) ?: return@forEach
                if (claim(match.range)) adjustments += unit.multipliedBy(amount).multipliedBy(sign)
            }
        }
    }

    private fun scanDurations() {
        HalfHourRegex.findAll(text).forEach { match ->
            if (claim(match.range)) durations += DurationHit(Duration.ofMinutes(30), match.value.trim())
        }
        DurationRegexes.forEach { regex ->
            regex.findAll(text).forEach { match ->
                val unitText = match.groups["u"]?.value.orEmpty()
                val unit = unitFor(unitText) ?: return@forEach
                val amount = parseAmount(match.groups["n"]?.value) ?: implicitOne(unitText) ?: return@forEach
                val duration = unit.multipliedBy(amount)
                if (duration.isZero || duration > Duration.ofDays(366 * MaxYearsAhead)) return@forEach
                if (claim(match.range)) durations += DurationHit(duration, match.value.trim())
            }
        }
    }

    private fun scanNumericDates() {
        NumericDateRegex.findAll(text).forEach { match ->
            val day = match.groupValues[1].toInt()
            val month = match.groupValues[2].toInt()
            val yearText = match.groupValues[3]
            // "kell 12.11" / "at 12.11" / "в 12.11" is a clock time, not a date.
            if (yearText.isEmpty() && precededByTimeMarker(match.range.first)) return@forEach
            if (yearText.isEmpty() && followedByMeridiem(match.range.last)) return@forEach
            if (month !in 1..12 || day !in 1..31) return@forEach
            // "1.2" is more often a version or a score than a date.
            if (yearText.isEmpty() && match.groupValues[1].length == 1 && match.groupValues[2].length == 1) return@forEach
            val explicitYear = yearText.takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            if (!claim(match.range)) return@forEach
            dates += DateHit(match.value) { today ->
                calendarDate(today, day, month, explicitYear)
            }
        }
    }

    private fun scanMonthNameDates() {
        packs.forEach { pack ->
            pack.monthNames.forEach { (month, names) ->
                names.forEach { name ->
                    val dayFirst = Regex("(?<![\\p{L}\\p{N}])(\\d{1,2})(?:\\.|st|nd|rd|th)?\\s+(?:of\\s+)?${Regex.escape(name)}(?![\\p{L}])")
                    val monthFirst = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}\\s+(\\d{1,2})(?:st|nd|rd|th)?(?![\\p{L}\\p{N}])")
                    (dayFirst.findAll(text) + monthFirst.findAll(text)).forEach { match ->
                        val day = match.groupValues[1].toInt()
                        if (day !in 1..31 || !claim(match.range)) return@forEach
                        dates += DateHit(match.value) { today -> calendarDate(today, day, month, null) }
                    }
                }
            }
        }
    }

    private fun scanAmPmTimes() {
        AmPmRegex.findAll(text).forEach { match ->
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].ifBlank { "0" }.toInt()
            val pm = match.groupValues[3].startsWith("p")
            if (!claim(match.range)) return@forEach
            val resolved = when {
                pm && hour != 12 -> hour + 12
                !pm && hour == 12 -> 0
                else -> hour
            }
            times += TimeHit(resolved, minute, bareHour = false, fixedPeriod = true)
        }
    }

    private fun scanNoon() {
        packs.forEach { pack ->
            pack.noonWords.forEach { word ->
                rulePhraseRegex(word).findAll(text).forEach { match ->
                    if (claim(match.range)) times += TimeHit(12, 0, bareHour = false, fixedPeriod = true)
                }
            }
        }
    }

    /** "8 in the evening", "kell 8 õhtul", "в 8 вечера", "в 3 дня". */
    private fun scanPeriodHourTimes() {
        packs.forEach { pack ->
            pack.periodsAfterHour.forEach { (phrase, period) ->
                val regex = Regex(
                    "(?<![\\p{L}\\p{N}])(?:(?:at|kell|kl\\.?|в|к)\\s+)?([01]?\\d|2[0-3])(?:[:.]([0-5]\\d))?\\s+" +
                        phrase.split(' ').joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}\\p{N}])",
                )
                regex.findAll(text).forEach { match ->
                    val hour = match.groupValues[1].toInt()
                    val minute = match.groupValues[2].ifBlank { "0" }.toInt()
                    val time = TimeHit(hour, minute, bareHour = false, fixedPeriod = false).applyPeriod(period)
                    if (!claim(match.range)) return@forEach
                    if (time == null) {
                        invalidTime = true
                    } else {
                        times += TimeHit(time.time.hour, time.time.minute, bareHour = false, fixedPeriod = true)
                        if (period == DayPeriod.Evening && phrase in pack.tonightWords) {
                            periods += PeriodHit(DayPeriod.Evening, impliesToday = true)
                        }
                    }
                }
            }
        }
    }

    /** "tonight", "this evening", "homme hommikul", "завтра утром". */
    private fun scanPeriodWords() {
        packs.forEach { pack ->
            pack.periodWords.forEach { (phrase, hit) ->
                rulePhraseRegex(phrase).findAll(text).forEach { match ->
                    if (claim(match.range)) periods += hit
                }
            }
        }
    }

    private fun scanRelativeDates() {
        packs.forEach { pack ->
            pack.relativeDates.forEach { (phrase, resolver) ->
                rulePhraseRegex(phrase).findAll(text).forEach { match ->
                    if (claim(match.range)) dates += DateHit(pack.relativeDateLabel(phrase), resolver)
                }
            }
        }
    }

    private fun scanWeekdays() {
        packs.forEach { pack ->
            pack.weekdays.forEach { (day, names) ->
                names.forEach { name ->
                    rulePhraseRegex(name).findAll(text).forEach { match ->
                        if (claim(match.range)) {
                            dates += DateHit(match.value) { today ->
                                today.with(TemporalAdjusters.next(day))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun scanSeparatedTimes(strongContext: Boolean) {
        SeparatedTimeRegex.findAll(text).forEach { match ->
            if (!free(match.range)) return@forEach
            val dot = match.groupValues[2] == "."
            val standalone = text.trim() == match.value
            if (dot && !strongContext && !standalone && !precededByTimeMarker(match.range.first)) return@forEach
            claim(match.range)
            times += TimeHit(
                match.groupValues[1].toInt(),
                match.groupValues[3].toInt(),
                bareHour = false,
                fixedPeriod = false,
            )
        }
    }

    private fun scanMarkedHours() {
        MarkedHourRegex.findAll(text).forEach { match ->
            if (!claim(match.range)) return@forEach
            val hour = match.groupValues[1].toInt()
            times += TimeHit(hour, 0, bareHour = hour in 1..12, fixedPeriod = false)
        }
    }

    private fun scanCompactTimes(strongContext: Boolean) {
        CompactTimeRegex.findAll(text).forEach { match ->
            if (!free(match.range)) return@forEach
            val standalone = text.trim() == match.value
            if (!strongContext && !standalone) return@forEach
            val hour = match.value.dropLast(2).toInt()
            val minute = match.value.takeLast(2).toInt()
            claim(match.range)
            if (hour !in 0..23 || minute !in 0..59) {
                invalidTime = true
            } else {
                times += TimeHit(hour, minute, bareHour = false, fixedPeriod = false)
            }
        }
    }

    private fun precededByTimeMarker(index: Int): Boolean {
        val before = text.substring(0, index).trimEnd()
        return TimeMarkers.any { marker -> before.endsWith(" $marker") || before == marker }
    }

    private fun followedByMeridiem(lastIndex: Int): Boolean =
        Regex("^\\s*(a\\.?m\\.?|p\\.?m\\.?)").containsMatchIn(text.substring(lastIndex + 1))

    private fun unitFor(unit: String): Duration? = when {
        unit.startsWith("min") || unit.startsWith("мин") -> Duration.ofMinutes(1)
        unit.startsWith("h") || unit.startsWith("tun") || unit.startsWith("час") || unit == "tund" ->
            Duration.ofHours(1)
        unit.startsWith("day") || unit.startsWith("päe") || unit.startsWith("ден") || unit.startsWith("дн") ||
            unit.startsWith("день") -> Duration.ofDays(1)
        unit.startsWith("week") || unit.startsWith("näd") || unit.startsWith("недел") -> Duration.ofDays(7)
        else -> null
    }

    private fun implicitOne(unit: String): Long? =
        if (unit in ImplicitSingleUnits) 1L else null
}

private fun calendarDate(today: LocalDate, day: Int, month: Int, year: Int?): LocalDate? {
    val candidate = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
    return if (year == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
}

private fun parseAmount(value: String?): Long? {
    val text = value?.trim() ?: return null
    text.toLongOrNull()?.let { return it }
    return NumberWords[text]
}

// endregion

// region Language packs

private data class ReminderLanguageRulePack(
    val language: String,
    val todayLabel: String,
    val tomorrowLabel: String,
    val relativeDates: List<Pair<String, (LocalDate) -> LocalDate?>>,
    val relativeDateLabels: Map<String, String>,
    val weekdays: Map<DayOfWeek, List<String>>,
    val monthNames: Map<Int, List<String>>,
    val periodWords: List<Pair<String, PeriodHit>>,
    val periodsAfterHour: List<Pair<String, DayPeriod>>,
    val tonightWords: Set<String>,
    val noonWords: List<String>,
    val timeContextSignals: List<String>,
    val reminderIntentSignals: List<String>,
) {
    fun relativeDateLabel(phrase: String): String = relativeDateLabels[phrase] ?: phrase

    fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> todayLabel
        today.plusDays(1) -> tomorrowLabel
        else -> "%02d.%02d".format(Locale.ROOT, date.dayOfMonth, date.monthValue)
    }
}

private val TodayResolver: (LocalDate) -> LocalDate? = { it }
private val TomorrowResolver: (LocalDate) -> LocalDate? = { it.plusDays(1) }
private val DayAfterTomorrowResolver: (LocalDate) -> LocalDate? = { it.plusDays(2) }
private val NextWeekResolver: (LocalDate) -> LocalDate? = { it.plusWeeks(1) }
private val NextMonthResolver: (LocalDate) -> LocalDate? = { it.plusMonths(1) }

private val EnglishReminderRules = ReminderLanguageRulePack(
    language = "en",
    todayLabel = "today",
    tomorrowLabel = "tomorrow",
    relativeDates = listOf(
        "day after tomorrow" to DayAfterTomorrowResolver,
        "today" to TodayResolver,
        "tomorrow" to TomorrowResolver,
        "next week" to NextWeekResolver,
        "next month" to NextMonthResolver,
    ),
    relativeDateLabels = emptyMap(),
    weekdays = mapOf(
        DayOfWeek.MONDAY to listOf("monday", "mon"),
        DayOfWeek.TUESDAY to listOf("tuesday", "tue", "tues"),
        DayOfWeek.WEDNESDAY to listOf("wednesday", "wed"),
        DayOfWeek.THURSDAY to listOf("thursday", "thu", "thurs"),
        DayOfWeek.FRIDAY to listOf("friday", "fri"),
        // "sat" and "sun" are ordinary English words, so only full names count.
        DayOfWeek.SATURDAY to listOf("saturday"),
        DayOfWeek.SUNDAY to listOf("sunday"),
    ),
    monthNames = mapOf(
        1 to listOf("january", "jan"), 2 to listOf("february", "feb"), 3 to listOf("march", "mar"),
        4 to listOf("april", "apr"), 5 to listOf("may"), 6 to listOf("june", "jun"),
        7 to listOf("july", "jul"), 8 to listOf("august", "aug"), 9 to listOf("september", "sep", "sept"),
        10 to listOf("october", "oct"), 11 to listOf("november", "nov"), 12 to listOf("december", "dec"),
    ),
    periodWords = listOf(
        "tonight" to PeriodHit(DayPeriod.Evening, impliesToday = true),
        "this evening" to PeriodHit(DayPeriod.Evening, impliesToday = true),
        "this afternoon" to PeriodHit(DayPeriod.Afternoon, impliesToday = true),
        "this morning" to PeriodHit(DayPeriod.Morning, impliesToday = true),
        "in the morning" to PeriodHit(DayPeriod.Morning, impliesToday = false),
        "in the afternoon" to PeriodHit(DayPeriod.Afternoon, impliesToday = false),
        "in the evening" to PeriodHit(DayPeriod.Evening, impliesToday = false),
        "at night" to PeriodHit(DayPeriod.Night, impliesToday = false),
        "morning" to PeriodHit(DayPeriod.Morning, impliesToday = false),
        "afternoon" to PeriodHit(DayPeriod.Afternoon, impliesToday = false),
        "evening" to PeriodHit(DayPeriod.Evening, impliesToday = false),
    ),
    periodsAfterHour = listOf(
        "in the morning" to DayPeriod.Morning,
        "in the afternoon" to DayPeriod.Afternoon,
        "in the evening" to DayPeriod.Evening,
        "at night" to DayPeriod.Night,
        "tonight" to DayPeriod.Evening,
    ),
    tonightWords = setOf("tonight"),
    noonWords = listOf("noon", "midday"),
    timeContextSignals = listOf("remind me", "reminder", "time", "at", "set for", "schedule"),
    reminderIntentSignals = listOf("remind me", "reminder"),
)

private val EstonianReminderRules = ReminderLanguageRulePack(
    language = "et",
    todayLabel = "täna",
    tomorrowLabel = "homme",
    relativeDates = listOf(
        "ülehomme" to DayAfterTomorrowResolver,
        "täna" to TodayResolver,
        "homme" to TomorrowResolver,
        "järgmisel nädalal" to NextWeekResolver,
        "järgmine nädal" to NextWeekResolver,
        "järgmisel kuul" to NextMonthResolver,
        "järgmine kuu" to NextMonthResolver,
    ),
    relativeDateLabels = mapOf(
        "järgmine nädal" to "järgmisel nädalal",
        "järgmine kuu" to "järgmisel kuul",
    ),
    weekdays = mapOf(
        DayOfWeek.MONDAY to listOf("esmaspäeval", "esmaspäev"),
        DayOfWeek.TUESDAY to listOf("teisipäeval", "teisipäev"),
        DayOfWeek.WEDNESDAY to listOf("kolmapäeval", "kolmapäev"),
        DayOfWeek.THURSDAY to listOf("neljapäeval", "neljapäev"),
        DayOfWeek.FRIDAY to listOf("reedel", "reede"),
        DayOfWeek.SATURDAY to listOf("laupäeval", "laupäev"),
        DayOfWeek.SUNDAY to listOf("pühapäeval", "pühapäev"),
    ),
    monthNames = mapOf(
        1 to listOf("jaanuaril", "jaanuar"), 2 to listOf("veebruaril", "veebruar"),
        3 to listOf("märtsil", "märts"), 4 to listOf("aprillil", "aprill"), 5 to listOf("mail", "mai"),
        6 to listOf("juunil", "juuni"), 7 to listOf("juulil", "juuli"), 8 to listOf("augustil", "august"),
        9 to listOf("septembril", "september"), 10 to listOf("oktoobril", "oktoober"),
        11 to listOf("novembril", "november"), 12 to listOf("detsembril", "detsember"),
    ),
    periodWords = listOf(
        "täna õhtul" to PeriodHit(DayPeriod.Evening, impliesToday = true),
        "täna hommikul" to PeriodHit(DayPeriod.Morning, impliesToday = true),
        "täna pärastlõunal" to PeriodHit(DayPeriod.Afternoon, impliesToday = true),
        "täna öösel" to PeriodHit(DayPeriod.Night, impliesToday = true),
        "hommikul" to PeriodHit(DayPeriod.Morning, impliesToday = false),
        "pärastlõunal" to PeriodHit(DayPeriod.Afternoon, impliesToday = false),
        "õhtul" to PeriodHit(DayPeriod.Evening, impliesToday = false),
        "öösel" to PeriodHit(DayPeriod.Night, impliesToday = false),
    ),
    periodsAfterHour = listOf(
        "hommikul" to DayPeriod.Morning,
        "pärastlõunal" to DayPeriod.Afternoon,
        "õhtul" to DayPeriod.Evening,
        "öösel" to DayPeriod.Night,
    ),
    tonightWords = emptySet(),
    noonWords = listOf("keskpäeval"),
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
    todayLabel = "сегодня",
    tomorrowLabel = "завтра",
    relativeDates = listOf(
        "послезавтра" to DayAfterTomorrowResolver,
        "сегодня" to TodayResolver,
        "завтра" to TomorrowResolver,
        "на следующей неделе" to NextWeekResolver,
        "следующая неделя" to NextWeekResolver,
        "в следующую неделю" to NextWeekResolver,
        "в следующем месяце" to NextMonthResolver,
        "следующий месяц" to NextMonthResolver,
        "на следующий месяц" to NextMonthResolver,
    ),
    relativeDateLabels = mapOf(
        "следующая неделя" to "на следующей неделе",
        "в следующую неделю" to "на следующей неделе",
        "следующий месяц" to "в следующем месяце",
        "на следующий месяц" to "в следующем месяце",
    ),
    weekdays = mapOf(
        DayOfWeek.MONDAY to listOf("понедельник"),
        DayOfWeek.TUESDAY to listOf("вторник"),
        DayOfWeek.WEDNESDAY to listOf("среду", "среда"),
        DayOfWeek.THURSDAY to listOf("четверг"),
        DayOfWeek.FRIDAY to listOf("пятницу", "пятница"),
        DayOfWeek.SATURDAY to listOf("субботу", "суббота"),
        DayOfWeek.SUNDAY to listOf("воскресенье"),
    ),
    monthNames = mapOf(
        1 to listOf("января"), 2 to listOf("февраля"), 3 to listOf("марта"), 4 to listOf("апреля"),
        5 to listOf("мая"), 6 to listOf("июня"), 7 to listOf("июля"), 8 to listOf("августа"),
        9 to listOf("сентября"), 10 to listOf("октября"), 11 to listOf("ноября"), 12 to listOf("декабря"),
    ),
    periodWords = listOf(
        "сегодня вечером" to PeriodHit(DayPeriod.Evening, impliesToday = true),
        "сегодня утром" to PeriodHit(DayPeriod.Morning, impliesToday = true),
        "сегодня днём" to PeriodHit(DayPeriod.Afternoon, impliesToday = true),
        "сегодня днем" to PeriodHit(DayPeriod.Afternoon, impliesToday = true),
        "сегодня ночью" to PeriodHit(DayPeriod.Night, impliesToday = true),
        "утром" to PeriodHit(DayPeriod.Morning, impliesToday = false),
        "днём" to PeriodHit(DayPeriod.Afternoon, impliesToday = false),
        "днем" to PeriodHit(DayPeriod.Afternoon, impliesToday = false),
        "вечером" to PeriodHit(DayPeriod.Evening, impliesToday = false),
        "ночью" to PeriodHit(DayPeriod.Night, impliesToday = false),
    ),
    periodsAfterHour = listOf(
        "утра" to DayPeriod.Morning,
        "дня" to DayPeriod.Afternoon,
        "вечера" to DayPeriod.Evening,
        "ночи" to DayPeriod.Night,
    ),
    tonightWords = emptySet(),
    noonWords = listOf("в полдень", "полдень"),
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

private fun reminderRulePacks(locale: Locale): List<ReminderLanguageRulePack> {
    val preferredLanguage = locale.language.lowercase(Locale.ROOT)
        .takeIf { language -> language in SupportedReminderRulePacks.map { it.language } }
        ?: "en"
    return SupportedReminderRulePacks.sortedBy { pack ->
        if (pack.language == preferredLanguage) 0 else 1
    }
}

// endregion

// region Shared patterns

private fun String.normalizedForRules(): String =
    lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")

private fun String.containsRulePhrase(phrase: String): Boolean =
    rulePhraseRegex(phrase).containsMatchIn(this)

private fun rulePhraseRegex(phrase: String): Regex {
    val flexiblePhrase = phrase
        .trim()
        .split(Regex("\\s+"))
        .joinToString("\\s+") { token -> Regex.escape(token) }
    return Regex("(?<![\\p{L}\\p{N}])$flexiblePhrase(?![\\p{L}\\p{N}])")
}

private val NumberWords: Map<String, Long> = mapOf(
    "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
    "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "fifteen" to 15,
    "twenty" to 20, "thirty" to 30, "a couple of" to 2, "couple of" to 2,
    "üks" to 1, "ühe" to 1, "kaks" to 2, "kahe" to 2, "kolm" to 3, "kolme" to 3, "neli" to 4,
    "nelja" to 4, "viis" to 5, "viie" to 5, "kuus" to 6, "kuue" to 6, "kümme" to 10, "kümne" to 10,
    "одну" to 1, "один" to 1, "одна" to 1, "два" to 2, "две" to 2, "пару" to 2, "три" to 3,
    "четыре" to 4, "пять" to 5, "шесть" to 6, "десять" to 10, "пятнадцать" to 15, "двадцать" to 20,
    "тридцать" to 30,
)

private val ImplicitSingleUnits = setOf("час", "минуту", "день", "неделю", "tunni", "päeva", "nädala")

private const val NumberAlternatives =
    "\\d{1,3}|a couple of|couple of|an|a|one|two|three|four|five|six|seven|eight|nine|ten|fifteen|twenty|thirty|" +
        "üks|ühe|kaks|kahe|kolm|kolme|neli|nelja|viis|viie|kuus|kuue|kümme|kümne|" +
        "одну|один|одна|два|две|пару|три|четыре|пять|шесть|десять|пятнадцать|двадцать|тридцать"

private val DurationRegexes = listOf(
    // English: "in 2 hours", "in an hour", "in 3 days".
    Regex("(?<![\\p{L}\\p{N}])in\\s+(?<n>$NumberAlternatives)\\s+(?<u>min(?:ute)?s?|hours?|hrs?|days?|weeks?)(?![\\p{L}\\p{N}])"),
    // Estonian: "2 tunni pärast", "kahe päeva pärast", "tunni pärast".
    Regex("(?<![\\p{L}\\p{N}])(?:(?<n>$NumberAlternatives)\\s+)?(?<u>minuti|min|tunni|päeva|nädala)\\s+pärast(?![\\p{L}\\p{N}])"),
    // Russian: "через 2 часа", "через час", "через 3 дня".
    Regex("(?<![\\p{L}\\p{N}])через\\s+(?:(?<n>$NumberAlternatives)\\s+)?(?<u>минуту|минуты|минут|мин|часа|часов|час|день|дня|дней|неделю|недели|недель)(?![\\p{L}\\p{N}])"),
)

/** "in half an hour", "poole tunni pärast", "через полчаса". */
private val HalfHourRegex = Regex(
    "(?<![\\p{L}\\p{N}])(?:in\\s+half\\s+an\\s+hour|poole\\s+tunni\\s+pärast|через\\s+полчаса)(?![\\p{L}\\p{N}])",
)

private val AdjustmentRegexes = listOf(
    Regex("(?<![\\p{L}\\p{N}])(?<n>$NumberAlternatives)\\s+(?<u>min(?:ute)?s?|hours?|hrs?|days?)\\s+earlier(?![\\p{L}\\p{N}])") to -1L,
    Regex("(?<![\\p{L}\\p{N}])(?<n>$NumberAlternatives)\\s+(?<u>min(?:ute)?s?|hours?|hrs?|days?)\\s+later(?![\\p{L}\\p{N}])") to 1L,
    Regex("(?<![\\p{L}\\p{N}])(?<n>$NumberAlternatives)\\s+(?<u>minutit|minuti|min|tundi|tunni|tund|päeva)\\s+(?:varem|varasemaks)(?![\\p{L}\\p{N}])") to -1L,
    Regex("(?<![\\p{L}\\p{N}])(?<n>$NumberAlternatives)\\s+(?<u>minutit|minuti|min|tundi|tunni|tund|päeva)\\s+(?:hiljem|hilisemaks)(?![\\p{L}\\p{N}])") to 1L,
    Regex("(?<![\\p{L}\\p{N}])на\\s+(?:(?<n>$NumberAlternatives)\\s+)?(?<u>минуту|минуты|минут|мин|час|часа|часов|день|дня|дней)\\s+раньше(?![\\p{L}\\p{N}])") to -1L,
    Regex("(?<![\\p{L}\\p{N}])на\\s+(?:(?<n>$NumberAlternatives)\\s+)?(?<u>минуту|минуты|минут|мин|час|часа|часов|день|дня|дней)\\s+позже(?![\\p{L}\\p{N}])") to 1L,
)

private val TimeMarkers = listOf("at", "kell", "kl", "kl.", "в", "к")

private val NumericDateRegex = Regex(
    "(?<![\\p{L}\\p{N}.:])(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{4}|\\d{2}))?\\.?(?![\\p{N}:]|\\.\\d)",
)
private val AmPmRegex = Regex(
    "(?<![\\p{L}\\p{N}])(1[0-2]|0?[1-9])(?:[:.]([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?)(?![\\p{L}\\p{N}])",
)
private val SeparatedTimeRegex = Regex(
    "(?<![\\p{L}\\p{N}])([01]?\\d|2[0-3])([:.])([0-5]\\d)(?![\\p{L}\\p{N}])",
)
private val MarkedHourRegex = Regex(
    "(?<![\\p{L}\\p{N}])(?:at|kell|kl\\.?|в|к)\\s+([01]?\\d|2[0-3])(?![\\p{L}\\p{N}:.])",
)
private val CompactTimeRegex = Regex("(?<![\\p{L}\\p{N}])\\d{3,4}(?![\\p{L}\\p{N}])")

// endregion
