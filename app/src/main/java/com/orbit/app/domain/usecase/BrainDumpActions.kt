package com.orbit.app.domain.usecase

import androidx.room.withTransaction
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.shouldScheduleNotification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class BrainDumpActionStatus { Applied, AlreadyHandled, Missing }

data class BrainDumpActionResult(
    val status: BrainDumpActionStatus,
    val sessionCompleted: Boolean = false,
    val reminderCreated: Boolean = false,
    val notificationScheduled: Boolean? = null,
)

class BrainDumpActions(
    private val database: OrbitDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun saveNote(
        captureId: Long,
        sourceKey: String,
        title: String,
        spaceId: Long?,
    ): BrainDumpActionResult = apply(captureId, sourceKey, BrainDumpItemOutcome.Saved) { session, item ->
        database.noteDao().insert(
            NoteEntity(
                title = title.trim().ifBlank { item.suggestedTitle.ifBlank { item.rawText } },
                body = item.rawText,
                spaceId = spaceId,
                scheduledDateEpochDay = session.calendarDateContextEpochDay,
            ),
        )
        false
    }

    suspend fun saveTask(
        captureId: Long,
        sourceKey: String,
        title: String,
        dueAt: Long?,
        spaceId: Long?,
    ): BrainDumpActionResult = apply(captureId, sourceKey, BrainDumpItemOutcome.Saved) { session, item ->
        val schedule = taskSchedule(dueAt, session.calendarDateContextEpochDay)
        val cleanTitle = title.trim().ifBlank { item.suggestedTitle.ifBlank { item.rawText } }
        database.taskDao().insert(
            TaskEntity(
                title = cleanTitle,
                notes = item.rawText.takeUnless { it == cleanTitle }.orEmpty(),
                spaceId = spaceId,
                dueAt = schedule.dueAt,
                scheduledDateEpochDay = schedule.scheduledDateEpochDay,
            ),
        )
        false
    }

    suspend fun saveReminder(
        captureId: Long,
        sourceKey: String,
        title: String,
        dueAt: Long,
        spaceId: Long?,
    ): BrainDumpActionResult {
        require(dueAt > 0L) { "A reminder target time is required" }
        var reminderId: Long? = null
        val result = apply(captureId, sourceKey, BrainDumpItemOutcome.Saved) { _, item ->
            val cleanTitle = title.trim().ifBlank { item.suggestedTitle.ifBlank { item.rawText } }
            reminderId = database.reminderDao().insert(
                ReminderEntity(
                    title = cleanTitle,
                    notes = item.rawText.takeUnless { it == cleanTitle }.orEmpty(),
                    dueAt = dueAt,
                    spaceId = spaceId,
                    linkedCaptureId = captureId,
                ),
            )
            true
        }
        if (result.status != BrainDumpActionStatus.Applied || reminderId == null) return result

        val reminder = database.reminderDao().getById(checkNotNull(reminderId))
        val scheduled = if (reminder?.shouldScheduleNotification() == true) {
            runCatching {
                val workId = reminderScheduler.schedule(reminder)
                database.reminderDao().update(reminder.copy(notificationWorkId = workId))
                workId != null
            }.getOrDefault(false)
        } else {
            false
        }
        return result.copy(reminderCreated = true, notificationScheduled = scheduled)
    }

    suspend fun saveOriginalLineForLater(
        captureId: Long,
        sourceKey: String,
    ): BrainDumpActionResult = apply(captureId, sourceKey, BrainDumpItemOutcome.KeptInInbox) { _, item ->
        database.captureDao().insert(
            CaptureEntity(
                rawText = item.rawText,
                status = CaptureStatus.Inbox,
            ),
        )
        false
    }

    suspend fun skip(captureId: Long, sourceKey: String): BrainDumpActionResult =
        apply(captureId, sourceKey, BrainDumpItemOutcome.Skipped) { _, _ -> false }

    suspend fun dismissCapture(captureId: Long, archive: Boolean) {
        database.withTransaction {
            val capture = database.captureDao().getById(captureId) ?: return@withTransaction
            database.brainDumpDao().deleteSession(captureId)
            database.captureDao().update(
                capture.copy(
                    status = if (archive) CaptureStatus.Archived else CaptureStatus.Processed,
                    linkedItemId = null,
                    updatedAt = now(),
                ),
            )
        }
    }

    private suspend fun apply(
        captureId: Long,
        sourceKey: String,
        outcome: BrainDumpItemOutcome,
        create: suspend (
            com.orbit.app.data.local.entity.BrainDumpSessionEntity,
            com.orbit.app.data.local.entity.BrainDumpItemEntity,
        ) -> Boolean,
    ): BrainDumpActionResult {
        var reminderCreated = false
        return database.withTransaction {
            val session = database.brainDumpDao().getSession(captureId)
                ?: return@withTransaction BrainDumpActionResult(BrainDumpActionStatus.Missing)
            val item = database.brainDumpDao().getItem(captureId, sourceKey)
                ?: return@withTransaction BrainDumpActionResult(BrainDumpActionStatus.Missing)
            if (item.outcome != BrainDumpItemOutcome.Pending) {
                return@withTransaction BrainDumpActionResult(BrainDumpActionStatus.AlreadyHandled)
            }

            reminderCreated = create(session, item)
            val changed = database.brainDumpDao().markPendingItem(item.id, outcome, now())
            if (changed != 1) {
                error("Brain Dump item changed while it was being handled")
            }
            val completed = database.brainDumpDao().pendingCount(captureId) == 0
            if (completed) {
                val capture = requireNotNull(database.captureDao().getById(captureId))
                database.captureDao().update(
                    capture.copy(
                        status = CaptureStatus.Processed,
                        linkedItemId = null,
                        updatedAt = now(),
                    ),
                )
                database.brainDumpDao().deleteSession(captureId)
            }
            BrainDumpActionResult(
                status = BrainDumpActionStatus.Applied,
                sessionCompleted = completed,
                reminderCreated = reminderCreated,
            )
        }
    }

    private fun taskSchedule(dueAt: Long?, calendarEpochDay: Long?): TaskSchedule {
        val contextDate = calendarEpochDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
        val dueDate = dueAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        return if (contextDate != null && (dueAt == null || dueDate == contextDate)) {
            TaskSchedule(dueAt = null, scheduledDateEpochDay = contextDate.toEpochDay())
        } else {
            TaskSchedule(dueAt = dueAt, scheduledDateEpochDay = null)
        }
    }

    private data class TaskSchedule(val dueAt: Long?, val scheduledDateEpochDay: Long?)
}
