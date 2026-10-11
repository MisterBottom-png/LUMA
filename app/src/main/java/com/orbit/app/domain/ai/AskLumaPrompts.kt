package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.search.SearchCorpus
import com.orbit.app.ui.navigation.ItemDetailType
import java.time.Instant
import java.time.ZoneId

/** The calm questions offered at the top of Review. */
enum class AskLumaQuestion { WhatNow, WhatCanWait, DependsOnOthers, SmallestStep, AnythingUrgent }

/** What the answer says, before it is put into words in the user's language. */
enum class AskLumaAnswerKind {
    StartWith,
    SortThoughts,
    /** Nothing new today, but [AskLumaPromptAnswer.count] open items from earlier. */
    FromEarlier,
    NothingNeeded,
    CanWait,
    NothingCanWait,
    WaitingOnOthers,
    NothingWaiting,
    SmallestStep,
    NothingToBreakDown,
    Urgent,
    NothingUrgent,
}

data class AskLumaPromptAnswer(
    val kind: AskLumaAnswerKind,
    val items: List<AiSourceItem> = emptyList(),
    /** Number of unresolved thoughts (SortThoughts) or earlier open items (FromEarlier). */
    val count: Int = 0,
    /** For CanWait: open tasks with no date, said separately under "No date set". */
    val undated: List<AiSourceItem> = emptyList(),
) {
    /** Everything the answer cites. */
    val sources: List<AiSourceItem> get() = items + undated
}

/**
 * Answers Review's Ask LUMA questions from the user's own items, deterministically
 * and locally. "Nothing needed" is a complete, valid answer; there is no scoring and
 * no overdue counting.
 */
object AskLumaPromptAnswerer {
    private const val UrgentWindowMillis = 3L * 60L * 60_000L
    private const val MaxItems = 3

    fun answer(
        question: AskLumaQuestion,
        corpus: SearchCorpus,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): AskLumaPromptAnswer {
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val endOfToday = today.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val startOfToday = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val openTasks = corpus.tasks.filter { it.status == TaskStatus.Open }
        val openReminders = corpus.reminders.filter { it.completedAt == null }
        val dueTodayReminders = openReminders.filter { it.dueAt in startOfToday until endOfToday }.sortedBy { it.dueAt }
        val dueTodayTasks = openTasks.filter {
            (it.dueAt != null && it.dueAt in startOfToday until endOfToday) ||
                it.scheduledDateEpochDay == today.toEpochDay()
        }.sortedBy { it.dueAt ?: endOfToday }
        val toSort = corpus.captures.count { it.status == CaptureStatus.Inbox }
        // Still open, with a time or day that has already passed (not counted, only mentioned).
        val earlierTasks = openTasks.filter { task ->
            (task.dueAt != null && task.dueAt < now) ||
                (task.scheduledDateEpochDay != null && task.scheduledDateEpochDay < today.toEpochDay())
        }.sortedBy { it.dueAt ?: Long.MIN_VALUE }
        val earlierReminders = openReminders.filter { it.dueAt < now - UrgentWindowMillis }.sortedBy { it.dueAt }
        val earlierCount = earlierTasks.size + earlierReminders.size

        return when (question) {
            AskLumaQuestion.WhatNow -> {
                val next = dueTodayReminders.firstOrNull { it.dueAt >= now - UrgentWindowMillis }?.let(::reminderSource)
                    ?: dueTodayTasks.firstOrNull()?.let(::taskSource)
                when {
                    next != null -> AskLumaPromptAnswer(AskLumaAnswerKind.StartWith, listOf(next))
                    toSort > 0 -> AskLumaPromptAnswer(AskLumaAnswerKind.SortThoughts, count = toSort)
                    // Never "nothing needs you" while something from earlier is still open.
                    earlierCount > 0 -> AskLumaPromptAnswer(
                        AskLumaAnswerKind.FromEarlier,
                        items = (earlierTasks.map(::taskSource) + earlierReminders.map(::reminderSource)).take(MaxItems),
                        count = earlierCount,
                    )
                    else -> AskLumaPromptAnswer(AskLumaAnswerKind.NothingNeeded)
                }
            }
            AskLumaQuestion.WhatCanWait -> {
                val later = (
                    openTasks.filter { task ->
                        (task.dueAt ?: 0L) > endOfToday ||
                            (task.scheduledDateEpochDay ?: Long.MIN_VALUE) > today.toEpochDay()
                    } + corpus.tasks.filter { it.status == TaskStatus.Someday }
                    ).sortedBy { it.updatedAt }.take(MaxItems).map(::taskSource)
                val undated = openTasks.filter { it.dueAt == null && it.scheduledDateEpochDay == null }
                    .sortedBy { it.updatedAt }.take(MaxItems).map(::taskSource)
                if (later.isEmpty() && undated.isEmpty()) {
                    AskLumaPromptAnswer(AskLumaAnswerKind.NothingCanWait)
                } else {
                    AskLumaPromptAnswer(AskLumaAnswerKind.CanWait, items = later, undated = undated)
                }
            }
            AskLumaQuestion.DependsOnOthers -> {
                val waiting = corpus.tasks.filter { it.status == TaskStatus.WaitingFor }
                    .sortedBy { it.updatedAt }.take(MaxItems).map(::taskSource)
                if (waiting.isEmpty()) {
                    AskLumaPromptAnswer(AskLumaAnswerKind.NothingWaiting)
                } else {
                    AskLumaPromptAnswer(AskLumaAnswerKind.WaitingOnOthers, waiting)
                }
            }
            AskLumaQuestion.SmallestStep -> {
                val candidate = (dueTodayTasks + openTasks.sortedBy { it.dueAt ?: Long.MAX_VALUE }).firstOrNull()
                if (candidate == null) {
                    AskLumaPromptAnswer(AskLumaAnswerKind.NothingToBreakDown)
                } else {
                    AskLumaPromptAnswer(AskLumaAnswerKind.SmallestStep, listOf(taskSource(candidate)))
                }
            }
            AskLumaQuestion.AnythingUrgent -> {
                val urgent = openReminders
                    .filter { it.dueAt in (now - UrgentWindowMillis)..(now + UrgentWindowMillis) }
                    .sortedBy { it.dueAt }
                    .map(::reminderSource) +
                    // The same window as reminders: a task due an hour ago is as urgent as a reminder.
                    openTasks.filter { it.dueAt != null && it.dueAt in (now - UrgentWindowMillis)..(now + UrgentWindowMillis) }
                        .sortedBy { it.dueAt }
                        .map(::taskSource)
                if (urgent.isEmpty()) {
                    AskLumaPromptAnswer(AskLumaAnswerKind.NothingUrgent)
                } else {
                    AskLumaPromptAnswer(AskLumaAnswerKind.Urgent, urgent.take(MaxItems))
                }
            }
        }
    }

    private fun taskSource(task: com.orbit.app.data.local.entity.TaskEntity) = AiSourceItem(
        sourceId = "task:${task.id}",
        type = ItemDetailType.Task,
        itemId = task.id,
        title = task.title,
        snippet = task.notes,
        spaceName = null,
        status = task.status.name,
        timestamp = task.updatedAt,
        createdAt = task.createdAt,
        dueAt = task.dueAt,
    )

    private fun reminderSource(reminder: com.orbit.app.data.local.entity.ReminderEntity) = AiSourceItem(
        sourceId = "reminder:${reminder.id}",
        type = ItemDetailType.Reminder,
        itemId = reminder.id,
        title = reminder.title,
        snippet = reminder.notes,
        spaceName = null,
        status = if (reminder.completedAt == null) "Open" else "Done",
        timestamp = reminder.updatedAt,
        createdAt = reminder.createdAt,
        dueAt = reminder.dueAt,
    )
}
