package com.orbit.app.domain.analyzer

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SituationSnapshot(
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
    val staleLoopDays: Int,
    val use24HourClock: Boolean = false,
    val now: Long = System.currentTimeMillis(),
)

data class SituationAnalysis(
    val whereYouAre: String,
    val whatMatters: List<String>,
    val whatIsStuck: List<String>,
    val nextAction: String,
    val openLoops: List<String>,
    val tinyPlan: List<String>,
    val clearNoiseSuggestion: String,
    val sourceItemIds: List<String> = emptyList(),
)

interface SituationAnalyzer {
    fun analyze(snapshot: SituationSnapshot): SituationAnalysis
}

/** Local-only prioritisation for Situation AI. It suggests; it never changes stored items. */
class LocalRulesSituationAnalyzer(
    private val reviewAnalyzer: LocalReviewAnalyzer = LocalReviewAnalyzer(),
    private val locale: () -> Locale = { Locale.ENGLISH },
) : SituationAnalyzer {
    override fun analyze(snapshot: SituationSnapshot): SituationAnalysis {
        val currentLocale = locale()
        val guidance = SituationGuidance(currentLocale)
        val activeTasks = snapshot.tasks.filter {
            it.status == TaskStatus.Open || it.status == TaskStatus.WaitingFor
        }
        val actionableTasks = activeTasks.filter { it.status == TaskStatus.Open }
        val waitingFor = activeTasks.filter { it.status == TaskStatus.WaitingFor }
        val activeReminders = snapshot.reminders.filter { it.completedAt == null }
        val inboxCaptures = snapshot.captures.filter { it.status == CaptureStatus.Inbox }
        val activeNotes = snapshot.notes.filterNot { it.archived }
        val recentCutoff = snapshot.now - TimeUnit.DAYS.toMillis(RecentCaptureDays)
        val recentCaptures = inboxCaptures.filter { it.createdAt >= recentCutoff }
        val staleLoops = reviewAnalyzer.findStaleLoops(
            tasks = snapshot.tasks,
            captures = snapshot.captures,
            staleLoopDays = snapshot.staleLoopDays,
            now = snapshot.now,
        )

        val priorityItems = buildPriorityItems(
            tasks = actionableTasks,
            reminders = activeReminders,
            now = snapshot.now,
            use24HourClock = snapshot.use24HourClock,
            locale = currentLocale,
            guidance = guidance,
        )
        val recentItems = buildList {
            recentCaptures.sortedByDescending { it.createdAt }.forEach {
                add(SituationItem(3, it.createdAt, "capture:${it.id}", guidance.recentCapture(it.rawText.trim().shorten(guidance))))
            }
            activeNotes.filter { it.updatedAt >= recentCutoff }.sortedByDescending { it.updatedAt }.forEach {
                add(SituationItem(4, it.updatedAt, "note:${it.id}", guidance.recentNote(it.title.trim().ifEmpty { it.body.trim() }.shorten(guidance))))
            }
        }
        val attentionItems = (priorityItems + recentItems)
            .sortedWith(compareBy<SituationItem>(SituationItem::rank).thenBy { it.timestamp })
            .distinctBy { it.sourceId }
            .take(MaxItems)

        val whereYouAre = attentionItems.firstOrNull()?.let { guidance.nearestAttention(it.text) }
            ?: waitingFor.minByOrNull { it.updatedAt }
                ?.let { guidance.unresolvedWaiting(it.title.trim().shorten(guidance)) }
            ?: guidance.noActiveAttention()

        val whatMatters = buildList {
            addAll(attentionItems.map { it.text })
            if (isEmpty()) add(guidance.nothingUrgent())
        }

        val stuckEvidence = buildList {
            waitingFor.sortedBy { it.updatedAt }.take(MaxItems).forEach {
                add(SituationItem(0, it.updatedAt, "task:${it.id}", guidance.unresolved(it.title.trim().shorten(guidance))))
            }
            staleLoops.asSequence()
                .filterNot { loop ->
                    loop.type == ReviewLoopType.Task && waitingFor.any { it.id == loop.id }
                }
                .take((MaxItems - size).coerceAtLeast(0))
                .forEach { loop ->
                    val ageDays = TimeUnit.MILLISECONDS.toDays((snapshot.now - loop.updatedAt).coerceAtLeast(0L))
                    val prefix = if (loop.type == ReviewLoopType.Task) "task" else "capture"
                    add(SituationItem(1, loop.updatedAt, "$prefix:${loop.id}", guidance.stale(ageDays, loop.title.trim().shorten(guidance))))
                }
        }
        val stuckItems = stuckEvidence.map { it.text }.ifEmpty { listOf(guidance.noStuckItems()) }

        val nextAction = attentionItems.firstOrNull()?.text
            ?: inboxCaptures.minByOrNull { it.createdAt }
                ?.let { guidance.recentCapture(it.rawText.trim().shorten(guidance)) }
            ?: actionableTasks.minByOrNull { it.updatedAt }
                ?.let { guidance.unresolvedTask(it.title.trim().shorten(guidance)) }
            ?: activeNotes.maxByOrNull { it.updatedAt }
                ?.let { guidance.recentNote(it.title.trim().ifEmpty { it.body.trim() }.shorten(guidance)) }
            ?: guidance.noNextStep()

        val openLoops = (
            activeTasks.map { task ->
                if (task.status == TaskStatus.WaitingFor) {
                    guidance.waitingLoop(task.title.trim().shorten(guidance))
                } else {
                    guidance.taskLoop(task.title.trim().shorten(guidance))
                }
            } + inboxCaptures.map { guidance.inboxLoop(it.rawText.trim().shorten(guidance)) }
            ).take(MaxOpenLoops).ifEmpty { listOf(guidance.noOpenLoops()) }

        val tinyPlan = buildList {
            add(nextAction)
            inboxCaptures.minByOrNull { it.createdAt }?.let {
                val step = guidance.giveInboxHome(it.rawText.trim().shorten(guidance))
                if (step != nextAction) add(step)
            }
            stuckEvidence.firstOrNull()?.let { add(guidance.decideStuck(it.text)) }
            if (size == 1) add(guidance.stopAfterThis())
        }.take(TinyPlanSteps)

        val staleInboxCount = staleLoops.count { it.type == ReviewLoopType.Capture }
        val clearNoiseSuggestion = when {
            staleInboxCount > 0 ->
                guidance.reviewStaleInbox(staleInboxCount)

            inboxCaptures.isNotEmpty() ->
                guidance.reviewOldestInbox(inboxCaptures.size)

            staleLoops.isNotEmpty() ->
                guidance.reviewOldestStaleLoop()

            else -> guidance.noLocalNoise()
        }

        return SituationAnalysis(
            whereYouAre = whereYouAre,
            whatMatters = whatMatters,
            whatIsStuck = stuckItems,
            nextAction = nextAction,
            openLoops = openLoops,
            tinyPlan = tinyPlan,
            clearNoiseSuggestion = clearNoiseSuggestion,
            sourceItemIds = (attentionItems + stuckEvidence).map { it.sourceId }.distinct(),
        )
    }

    private fun buildPriorityItems(
        tasks: List<TaskEntity>,
        reminders: List<ReminderEntity>,
        now: Long,
        use24HourClock: Boolean,
        locale: Locale,
        guidance: SituationGuidance,
    ): List<SituationItem> {
        val dueSoonCutoff = now + TimeUnit.HOURS.toMillis(DueSoonHours)

        val candidates = buildList {
            reminders.forEach { reminder ->
                when {
                    reminder.dueAt < now -> add(
                        SituationItem(0, reminder.dueAt, "reminder:${reminder.id}", guidance.overdueReminder(reminder.title.trim().shorten(guidance), reminder.dueAt.formatLocalDateTime(use24HourClock, locale))),
                    )

                    reminder.dueAt <= dueSoonCutoff -> add(
                        SituationItem(1, reminder.dueAt, "reminder:${reminder.id}", guidance.dueSoonReminder(reminder.title.trim().shorten(guidance), reminder.dueAt.formatLocalDateTime(use24HourClock, locale))),
                    )
                }
            }
            tasks.forEach { task ->
                task.dueAt?.let { dueAt ->
                    when {
                        dueAt < now -> add(
                        SituationItem(0, dueAt, "task:${task.id}", guidance.overdueTask(task.title.trim().shorten(guidance), dueAt.formatLocalDateTime(use24HourClock, locale))),
                        )

                        dueAt <= dueSoonCutoff -> add(
                        SituationItem(1, dueAt, "task:${task.id}", guidance.dueSoonTask(task.title.trim().shorten(guidance), dueAt.formatLocalDateTime(use24HourClock, locale))),
                        )
                    }
                }
            }
        }
        return candidates.sortedWith(compareBy(SituationItem::rank, SituationItem::timestamp))
    }

    private data class SituationItem(val rank: Int, val timestamp: Long, val sourceId: String, val text: String)

    private fun String.shorten(guidance: SituationGuidance, maxLength: Int = 72): String {
        val clean = replace(Regex("\\s+"), " ").ifEmpty { guidance.untitled() }
        return if (clean.length <= maxLength) clean else clean.take(maxLength - 3).trimEnd() + "..."
    }

    private fun Long.formatLocalDateTime(use24HourClock: Boolean, locale: Locale): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (use24HourClock) "MMM d, HH:mm" else "MMM d, h:mm a", locale))

    private companion object {
        const val RecentCaptureDays = 7L
        const val DueSoonHours = 24L
        const val MaxItems = 3
        const val MaxOpenLoops = 6
        const val TinyPlanSteps = 3
    }
}

private class SituationGuidance(locale: Locale) {
    private val language = locale.language

    fun untitled(): String = when (language) {
        "et" -> "Pealkirjata"
        "ru" -> "Без названия"
        else -> "Untitled"
    }

    fun recentCapture(text: String): String = when (language) {
        "et" -> "Hiljuti salvestatud: $text — endiselt Sisendkastis."
        "ru" -> "Недавно сохранено: $text — всё ещё во Входящих."
        else -> "Recently captured: $text — still in Inbox."
    }

    fun recentNote(text: String): String = when (language) {
        "et" -> "Hiljuti uuendatud märge: $text."
        "ru" -> "Недавно обновлённая заметка: $text."
        else -> "Recently updated note: $text."
    }

    fun nearestAttention(text: String): String = when (language) {
        "et" -> "Kõige lähem tähelepanu vajav asi on: $text"
        "ru" -> "Ближайшее, что требует внимания: $text"
        else -> "Your nearest attention point is ${text.lowercaseFirst(Locale.ENGLISH)}"
    }

    fun unresolvedWaiting(title: String): String = when (language) {
        "et" -> "Üks lahendamata asi ootab: $title."
        "ru" -> "Один незавершённый пункт ожидает: $title."
        else -> "An unresolved item is waiting: $title."
    }

    fun noActiveAttention(): String = when (language) {
        "et" -> "Ükski kohalik asi ei vaja praegu kohe tähelepanu."
        "ru" -> "Сейчас ни один локальный пункт не требует немедленного внимания."
        else -> "No active local item needs immediate attention right now."
    }

    fun nothingUrgent(): String = when (language) {
        "et" -> "Praegu ei vaja miski kiiret tähelepanu."
        "ru" -> "Сейчас ничто не требует срочного внимания."
        else -> "Nothing urgent is asking for attention right now."
    }

    fun unresolved(title: String): String = when (language) {
        "et" -> "Lahendamata: $title — märgitud ootele."
        "ru" -> "Не решено: $title — отмечено как ожидающее."
        else -> "Unresolved: $title — marked Waiting For."
    }

    fun stale(days: Long, title: String): String = when (language) {
        "et" -> "Juba $days päeva lahendamata: $title."
        "ru" -> "Не решено уже $days дн.: $title."
        else -> "Stale for $days days: $title — still unresolved."
    }

    fun noStuckItems(): String = when (language) {
        "et" -> "Ükski ootav ega aegunud asi ei paista silma."
        "ru" -> "Нет заметных ожидающих или давно нерешённых пунктов."
        else -> "No waiting-for or stale local items stand out."
    }

    fun unresolvedTask(title: String): String = when (language) {
        "et" -> "Lahendamata ülesanne: $title — endiselt avatud."
        "ru" -> "Незавершённая задача: $title — всё ещё открыта."
        else -> "Unresolved task: $title — still open."
    }

    fun noNextStep(): String = when (language) {
        "et" -> "Ükski kohalik asi ei vaja praegu järgmist sammu."
        "ru" -> "Сейчас ни одному локальному пункту не нужен следующий шаг."
        else -> "No local item needs a next step right now."
    }

    fun waitingLoop(title: String): String = when (language) {
        "et" -> "Ootel — $title"
        "ru" -> "Ожидает — $title"
        else -> "Waiting for - $title"
    }

    fun taskLoop(title: String): String = when (language) {
        "et" -> "Ülesanne — $title"
        "ru" -> "Задача — $title"
        else -> "Task - $title"
    }

    fun inboxLoop(text: String): String = when (language) {
        "et" -> "Sisendkast — $text"
        "ru" -> "Входящие — $text"
        else -> "Inbox - $text"
    }

    fun noOpenLoops(): String = when (language) {
        "et" -> "Avatud ülesandeid ega sisendkasti kirjeid ei ole."
        "ru" -> "Нет открытых задач или записей во Входящих."
        else -> "No open tasks or inbox captures."
    }

    fun giveInboxHome(text: String): String = when (language) {
        "et" -> "Leia ühele sisendkasti kirjele koht: $text"
        "ru" -> "Найдите место для одной записи из Входящих: $text"
        else -> "Give one inbox capture a home: $text"
    }

    fun decideStuck(text: String): String = when (language) {
        "et" -> "Võta kaks minutit, et otsustada: $text"
        "ru" -> "Потратьте две минуты, чтобы решить: $text"
        else -> "Spend two minutes deciding what to do with: $text"
    }

    fun stopAfterThis(): String = when (language) {
        "et" -> "Seejärel peatu ja vaata, kas miski muu vajab tõesti tähelepanu."
        "ru" -> "Затем остановитесь и проверьте, действительно ли что-то ещё требует внимания."
        else -> "Then stop and check whether anything else truly needs attention."
    }

    fun reviewStaleInbox(count: Int): String = when (language) {
        "et" -> "Vaata üle $count aegunud sisendkasti kirjet. Hoia, arhiveeri või lõpeta need Ülevaates; midagi ei muudeta automaatselt."
        "ru" -> "Просмотрите $count давно лежащих записей во Входящих. Оставьте, архивируйте или завершите их в обзоре; ничего не изменится автоматически."
        else -> "Review $count stale inbox captures. Keep, archive, or complete each one in Review; nothing will be changed automatically."
    }

    fun reviewOldestInbox(count: Int): String = when (language) {
        "et" -> "Alusta vanimast $count sisendkasti kirjest. Otsusta selle koht ja peatu, kui ülejäänud võivad oodata."
        "ru" -> "Начните с самой старой из $count записей во Входящих. Решите, куда её отнести, и остановитесь, если остальные могут подождать."
        else -> "Start with the oldest of your $count inbox captures. Decide its place, then stop if the rest can wait."
    }

    fun reviewOldestStaleLoop(): String = when (language) {
        "et" -> "Vaata üle vanim aegunud asi ning vali: hoia, arhiveeri, lõpeta või tee väiksemaks. Midagi ei muudeta automaatselt."
        "ru" -> "Просмотрите самый давний незавершённый пункт и выберите: оставить, архивировать, завершить или разбить на меньший шаг. Ничего не изменится автоматически."
        else -> "Review the oldest stale loop and choose keep, archive, complete, or make smaller. Nothing will be changed automatically."
    }

    fun noLocalNoise(): String = when (language) {
        "et" -> "Praegu ei paista silma midagi kohalikku, mida oleks vaja korrastada."
        "ru" -> "Сейчас нет заметного локального шума, который нужно разобрать."
        else -> "There is no obvious local noise to clear right now."
    }

    fun overdueReminder(title: String, dueAt: String): String = when (language) {
        "et" -> "Hilinenud meeldetuletus: $title — aeg $dueAt."
        "ru" -> "Просроченное напоминание: $title — срок $dueAt."
        else -> "Overdue reminder: $title — due $dueAt."
    }

    fun dueSoonReminder(title: String, dueAt: String): String = when (language) {
        "et" -> "Peagi meeldetuletus: $title — kell $dueAt."
        "ru" -> "Скоро напоминание: $title — в $dueAt."
        else -> "Due soon: $title — reminder at $dueAt."
    }

    fun overdueTask(title: String, dueAt: String): String = when (language) {
        "et" -> "Hilinenud ülesanne: $title — tähtaeg $dueAt."
        "ru" -> "Просроченная задача: $title — срок $dueAt."
        else -> "Overdue task: $title — due $dueAt."
    }

    fun dueSoonTask(title: String, dueAt: String): String = when (language) {
        "et" -> "Peagi tähtaeg: $title — $dueAt."
        "ru" -> "Скоро срок: $title — $dueAt."
        else -> "Due soon: $title — due $dueAt."
    }

    private fun String.lowercaseFirst(locale: Locale): String =
        replaceFirstChar { it.lowercase(locale) }
}
