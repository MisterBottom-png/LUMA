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
)

interface SituationAnalyzer {
    fun analyze(snapshot: SituationSnapshot): SituationAnalysis
}

/** Local-only prioritisation for Situation AI. It suggests; it never changes stored items. */
class LocalRulesSituationAnalyzer(
    private val reviewAnalyzer: LocalReviewAnalyzer = LocalReviewAnalyzer(),
) : SituationAnalyzer {
    override fun analyze(snapshot: SituationSnapshot): SituationAnalysis {
        val activeTasks = snapshot.tasks.filter {
            it.status == TaskStatus.Open || it.status == TaskStatus.WaitingFor
        }
        val actionableTasks = activeTasks.filter { it.status == TaskStatus.Open }
        val waitingFor = activeTasks.filter { it.status == TaskStatus.WaitingFor }
        val activeReminders = snapshot.reminders.filter { it.completedAt == null }
        val inboxCaptures = snapshot.captures.filter { it.status == CaptureStatus.Inbox }
        val activeNotes = snapshot.notes.filterNot { it.archived }
        val recentCutoff = snapshot.now - TimeUnit.DAYS.toMillis(RecentCaptureDays)
        val recentCaptures = snapshot.captures.filter { it.createdAt >= recentCutoff }
        val staleLoops = reviewAnalyzer.findStaleLoops(
            tasks = snapshot.tasks,
            captures = snapshot.captures,
            staleLoopDays = snapshot.staleLoopDays,
            now = snapshot.now,
        )

        val whereYouAre = buildString {
            append("You have ${inboxCaptures.size} ${inboxCaptures.size.itemWord("inbox capture")}, ")
            append("${activeTasks.size} ${activeTasks.size.itemWord("active task")}, ")
            append("${activeReminders.size} ${activeReminders.size.itemWord("reminder")}, ")
            append("and ${activeNotes.size} ${activeNotes.size.itemWord("note")} on device.")
            if (recentCaptures.isNotEmpty()) {
                append(" ${recentCaptures.size} ${recentCaptures.size.itemWord("capture")} arrived in the last 7 days.")
            }
        }

        val priorityItems = buildPriorityItems(
            tasks = actionableTasks,
            reminders = activeReminders,
            now = snapshot.now,
            use24HourClock = snapshot.use24HourClock,
        )
        val whatMatters = buildList {
            addAll(priorityItems.take(MaxItems))
            if (size < MaxItems) {
                inboxCaptures.sortedBy { it.createdAt }.firstOrNull()?.let {
                    add("Oldest inbox capture: ${it.rawText.trim().shorten()}")
                }
            }
            if (size < MaxItems) {
                activeNotes.maxByOrNull { it.updatedAt }?.let {
                    add("Recent note: ${it.title.trim().ifEmpty { it.body.trim() }.shorten()}")
                }
            }
            if (isEmpty()) add("Nothing urgent is asking for attention right now.")
        }

        val stuckItems = buildList {
            waitingFor.sortedBy { it.updatedAt }.take(MaxItems).forEach {
                add("Waiting for: ${it.title.trim().shorten()}")
            }
            staleLoops.asSequence()
                .filterNot { loop ->
                    loop.type == ReviewLoopType.Task && waitingFor.any { it.id == loop.id }
                }
                .take((MaxItems - size).coerceAtLeast(0))
                .forEach { add("Stale: ${it.title.trim().shorten()}") }
            if (isEmpty()) add("No waiting-for or stale loops stand out.")
        }

        val nextAction = priorityItems.firstOrNull()
            ?: inboxCaptures.minByOrNull { it.createdAt }
                ?.let { "Decide where this inbox capture belongs: ${it.rawText.trim().shorten()}" }
            ?: actionableTasks.minByOrNull { it.updatedAt }
                ?.let { "Take the first small step on: ${it.title.trim().shorten()}" }
            ?: activeNotes.maxByOrNull { it.updatedAt }
                ?.let { "Revisit your recent note: ${it.title.trim().ifEmpty { it.body.trim() }.shorten()}" }
            ?: "Your local lists are quiet. Capture what is on your mind, or take a real pause."

        val openLoops = (
            activeTasks.map { task ->
                if (task.status == TaskStatus.WaitingFor) {
                    "Waiting for - ${task.title.trim().shorten()}"
                } else {
                    "Task - ${task.title.trim().shorten()}"
                }
            } + inboxCaptures.map { "Inbox - ${it.rawText.trim().shorten()}" }
            ).take(MaxOpenLoops).ifEmpty { listOf("No open tasks or inbox captures.") }

        val tinyPlan = buildList {
            add(nextAction)
            inboxCaptures.minByOrNull { it.createdAt }?.let {
                val step = "Give one inbox capture a home: ${it.rawText.trim().shorten()}"
                if (step != nextAction) add(step)
            }
            stuckItems.firstOrNull()
                ?.takeUnless { it.startsWith("No ") }
                ?.let { add("Spend two minutes deciding what to do with: ${it.removePrefix("Stale: ").removePrefix("Waiting for: ")}") }
            if (size == 1) add("Then stop and check whether anything else truly needs attention.")
        }.take(TinyPlanSteps)

        val staleInboxCount = staleLoops.count { it.type == ReviewLoopType.Capture }
        val clearNoiseSuggestion = when {
            staleInboxCount > 0 ->
                "Review $staleInboxCount stale ${staleInboxCount.itemWord("inbox capture")}. Keep, archive, or complete each one in Review; nothing will be changed automatically."

            inboxCaptures.isNotEmpty() ->
                "Start with the oldest of your ${inboxCaptures.size} ${inboxCaptures.size.itemWord("inbox capture")}. Decide its place, then stop if the rest can wait."

            staleLoops.isNotEmpty() ->
                "Review the oldest stale loop and choose keep, archive, complete, or make smaller. Nothing will be changed automatically."

            else -> "There is no obvious local noise to clear right now."
        }

        return SituationAnalysis(
            whereYouAre = whereYouAre,
            whatMatters = whatMatters,
            whatIsStuck = stuckItems,
            nextAction = nextAction,
            openLoops = openLoops,
            tinyPlan = tinyPlan,
            clearNoiseSuggestion = clearNoiseSuggestion,
        )
    }

    private fun buildPriorityItems(
        tasks: List<TaskEntity>,
        reminders: List<ReminderEntity>,
        now: Long,
        use24HourClock: Boolean,
    ): List<String> {
        val endOfToday = Instant.ofEpochMilli(now)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val candidates = buildList {
            reminders.forEach { reminder ->
                when {
                    reminder.dueAt < now -> add(
                        PriorityItem(0, reminder.dueAt, "Overdue reminder: ${reminder.title.trim().shorten()}"),
                    )

                    reminder.dueAt < endOfToday -> add(
                        PriorityItem(1, reminder.dueAt, "Reminder at ${reminder.dueAt.formatTime(use24HourClock)}: ${reminder.title.trim().shorten()}"),
                    )
                }
            }
            tasks.forEach { task ->
                task.dueAt?.let { dueAt ->
                    when {
                        dueAt < now -> add(
                            PriorityItem(0, dueAt, "Overdue task: ${task.title.trim().shorten()}"),
                        )

                        dueAt < endOfToday -> add(
                            PriorityItem(1, dueAt, "Due today: ${task.title.trim().shorten()}"),
                        )
                    }
                }
            }
        }
        return candidates.sortedWith(compareBy(PriorityItem::rank, PriorityItem::dueAt)).map { it.text }
    }

    private data class PriorityItem(val rank: Int, val dueAt: Long, val text: String)

    private fun Int.itemWord(singular: String): String = if (this == 1) singular else "${singular}s"

    private fun String.shorten(maxLength: Int = 72): String {
        val clean = replace(Regex("\\s+"), " ").ifEmpty { "Untitled" }
        return if (clean.length <= maxLength) clean else clean.take(maxLength - 3).trimEnd() + "..."
    }

    private fun Long.formatTime(use24HourClock: Boolean): String = Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(if (use24HourClock) "HH:mm" else "h:mm a"))

    private companion object {
        const val RecentCaptureDays = 7L
        const val MaxItems = 3
        const val MaxOpenLoops = 6
        const val TinyPlanSteps = 3
    }
}
