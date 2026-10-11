package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.BrainDumpSessionData
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import java.time.LocalDate

/** A carry-forward day must be today or later: an earlier day would bring the item straight back. */
internal fun carryForwardDayAllowed(epochDay: Long, today: LocalDate): Boolean = epochDay >= today.toEpochDay()

/** What a Review change did, for its Undo message. */
enum class ReviewChangeKind { MovedTomorrow, MovedToDate, KeptUndated, Completed, ThoughtKept, ThoughtHandled, ThoughtLetGo }

data class ReviewChangeToken(val operationId: Long, val kind: ReviewChangeKind, val count: Int)

/** The items one Review action touches. */
internal data class ReviewChangeTargets(
    val taskIds: List<Long> = emptyList(),
    val reminderIds: List<Long> = emptyList(),
    val captureIds: List<Long> = emptyList(),
) {
    val count: Int get() = taskIds.size + reminderIds.size + captureIds.size
}

/**
 * Undo for Review's "To tomorrow", carry-forward and thought actions: the touched items
 * are read before and after the change, and Undo puts the "before" back only while
 * they are still exactly as the change left them.
 */
internal class ReviewChangeUndo(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val captureRepository: CaptureRepository,
    private val brainDumpRepository: BrainDumpRepository?,
) {
    private data class Snapshot(
        val tasks: List<TaskEntity>,
        val reminders: List<ReminderEntity>,
        val captures: List<CaptureEntity>,
        val sessions: Map<Long, BrainDumpSessionData?>,
    )

    private data class Pending(val token: ReviewChangeToken, val targets: ReviewChangeTargets, val before: Snapshot, val after: Snapshot)

    private var nextOperationId = 1L
    private var pending: Pending? = null

    suspend fun record(kind: ReviewChangeKind, targets: ReviewChangeTargets, change: suspend () -> Unit): ReviewChangeToken {
        val before = read(targets)
        change()
        val after = read(targets)
        val token = ReviewChangeToken(nextOperationId++, kind, targets.count)
        pending = Pending(token, targets, before, after)
        return token
    }

    /** True when the change was undone; false when it is gone or the items changed since. */
    suspend fun undo(operationId: Long): Boolean {
        val current = pending?.takeIf { it.token.operationId == operationId } ?: return false
        pending = null
        if (read(current.targets) != current.after) return false
        current.before.tasks.forEach { taskRepository.update(it) }
        current.before.reminders.forEach { reminderRepository.update(it) }
        current.before.captures.forEach { capture ->
            // "Keep" made a task from the thought; Undo takes that task away again.
            val createdTaskId = current.after.captures.firstOrNull { it.id == capture.id }?.linkedItemId
            if (current.token.kind == ReviewChangeKind.ThoughtKept && capture.linkedItemId == null && createdTaskId != null) {
                taskRepository.deleteById(createdTaskId)
            }
            captureRepository.update(capture)
            val session = current.before.sessions[capture.id]
            if (session != null && brainDumpRepository?.getSession(capture.id) == null) {
                brainDumpRepository?.createSession(session.session, session.items)
            }
        }
        return true
    }

    fun expire(operationId: Long) {
        if (pending?.token?.operationId == operationId) pending = null
    }

    private suspend fun read(targets: ReviewChangeTargets) = Snapshot(
        tasks = targets.taskIds.mapNotNull { taskRepository.getById(it) },
        reminders = targets.reminderIds.mapNotNull { reminderRepository.getById(it) }
            // Alarm bookkeeping is the scheduler's, not part of what the user changed.
            .map { it.copy(notificationWorkId = null) },
        captures = targets.captureIds.mapNotNull { captureRepository.getById(it) },
        sessions = targets.captureIds.associateWith { brainDumpRepository?.getSession(it) },
    )
}
