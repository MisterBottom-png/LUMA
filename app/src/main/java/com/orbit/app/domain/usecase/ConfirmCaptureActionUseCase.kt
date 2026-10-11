package com.orbit.app.domain.usecase

import androidx.room.withTransaction
import com.orbit.app.data.export.deriveItemTitle
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.LabelRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.TaskRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface CaptureFinalizationTransaction {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomCaptureFinalizationTransaction(
    private val database: OrbitDatabase,
) : CaptureFinalizationTransaction {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction { block() }
}

class ConfirmCaptureActionUseCase(
    private val captureRepository: CaptureRepository,
    private val noteRepository: NoteRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    private val transaction: CaptureFinalizationTransaction,
    private val labelRepository: LabelRepository? = null,
) {
    private val finalizationMutex = Mutex()

    suspend fun saveNote(
        captureId: Long,
        spaceId: Long?,
        title: String,
        scheduledDateEpochDay: Long? = null,
        labelNames: List<String> = emptyList(),
        onlyIfUndecided: Boolean = false,
    ): Long =
        finalizationMutex.withLock {
            transaction.run {
                val capture = requireInboxCapture(captureId, onlyIfUndecided)
                val noteId = noteRepository.insert(
                    NoteEntity(
                        title = title.ifBlank { capture.rawText.toNoteTitle() },
                        body = capture.rawText,
                        spaceId = spaceId,
                        scheduledDateEpochDay = scheduledDateEpochDay,
                    ),
                )
                try {
                    labelRepository?.replaceNoteLabels(
                        noteId,
                        labelNames.map { labelRepository.findOrCreate(it).id }.toSet(),
                    )
                    markProcessed(capture, noteId, CaptureOutcome.Filed)
                } catch (exception: Exception) {
                    noteRepository.deleteById(noteId)
                    throw exception
                }
                noteId
            }
        }

    suspend fun createTask(
        captureId: Long,
        spaceId: Long?,
        title: String,
        dueAt: Long?,
        scheduledDateEpochDay: Long? = null,
        status: TaskStatus = TaskStatus.Open,
        labelNames: List<String> = emptyList(),
        onlyIfUndecided: Boolean = false,
    ): Long = finalizationMutex.withLock {
        transaction.run {
            val capture = requireInboxCapture(captureId, onlyIfUndecided)
            val taskTitle = title.trim().ifBlank { capture.rawText.toNoteTitle() }
            val taskId = taskRepository.insert(
                TaskEntity(
                    title = taskTitle,
                    notes = capture.rawText.takeUnless { it == taskTitle }.orEmpty(),
                    spaceId = spaceId,
                    status = status,
                    dueAt = dueAt,
                    scheduledDateEpochDay = scheduledDateEpochDay,
                ),
            )
            try {
                labelRepository?.replaceTaskLabels(
                    taskId,
                    labelNames.map { labelRepository.findOrCreate(it).id }.toSet(),
                )
                markProcessed(capture, taskId, CaptureOutcome.Filed)
            } catch (exception: Exception) {
                taskRepository.deleteById(taskId)
                throw exception
            }
            taskId
        }
    }

    suspend fun createReminder(
        captureId: Long,
        spaceId: Long?,
        title: String,
        dueAt: Long,
        linkedTaskId: Long? = null,
        labelNames: List<String> = emptyList(),
        onlyIfUndecided: Boolean = false,
    ): Long = finalizationMutex.withLock {
        val reminderId = transaction.run {
            val capture = requireInboxCapture(captureId, onlyIfUndecided)
            val reminderId = reminderRepository.insertUnscheduled(
                ReminderEntity(
                    title = title.trim().ifBlank { capture.rawText.toNoteTitle() },
                    dueAt = dueAt,
                    spaceId = spaceId,
                    linkedTaskId = linkedTaskId,
                    linkedCaptureId = capture.id,
                ),
            )
            try {
                labelRepository?.replaceReminderLabels(
                    reminderId,
                    labelNames.map { labelRepository.findOrCreate(it).id }.toSet(),
                )
                // "A reminder row exists and is open"; whether it can ring is recorded
                // after scheduling (see CaptureResolution.recordReminderOutcome).
                markProcessed(capture, reminderId, CaptureOutcome.ReminderSet)
            } catch (exception: Exception) {
                reminderRepository.deleteById(reminderId)
                throw exception
            }
            reminderId
        }
        // Armed only after the commit, so a rolled-back reminder never gets an alarm.
        reminderRepository.scheduleStored(reminderId)
        reminderId
    }

    suspend fun saveBrainDumpNote(
        title: String,
        body: String,
        spaceId: Long?,
    ): Long = noteRepository.insert(
        NoteEntity(
            title = title.trim().ifBlank { body.toNoteTitle() },
            body = body,
            spaceId = spaceId,
        ),
    )

    suspend fun saveBrainDumpTask(
        title: String,
        notes: String,
        spaceId: Long?,
    ): Long = taskRepository.insert(
        TaskEntity(
            title = title.trim().ifBlank { notes.toNoteTitle() },
            notes = notes,
            spaceId = spaceId,
        ),
    )

    suspend fun markCaptureReviewed(captureId: Long) {
        captureRepository.getById(captureId)?.let { capture ->
            if (capture.status != CaptureStatus.Archived) {
                captureRepository.update(
                    capture.copy(
                        status = CaptureStatus.Processed,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    /**
     * [onlyIfUndecided] is set by automatic filing: a thought the user already decided
     * about (Undo, archive, ...) is refused here, inside the same transaction.
     */
    private suspend fun requireInboxCapture(captureId: Long, onlyIfUndecided: Boolean = false): CaptureEntity {
        val capture = requireNotNull(captureRepository.getById(captureId)) {
            "Capture $captureId no longer exists"
        }
        check(capture.status == CaptureStatus.Inbox) {
            "Capture $captureId has already been processed"
        }
        check(!onlyIfUndecided || capture.decidedAt == null) {
            "Capture $captureId has already been decided"
        }
        return capture
    }

    /**
     * Written in the same transaction as the new item, so the thought's stored outcome
     * can never disagree with what exists. Sorting is a decision: [CaptureEntity.decidedAt]
     * is stamped, so a pending automatic decision can no longer touch this thought.
     */
    private suspend fun markProcessed(capture: CaptureEntity, linkedItemId: Long, outcome: CaptureOutcome) {
        val timestamp = System.currentTimeMillis()
        captureRepository.update(
            capture.copy(
                status = CaptureStatus.Processed,
                linkedItemId = linkedItemId,
                updatedAt = timestamp,
                outcome = outcome,
                outcomeReason = CaptureOutcomeReason.None,
                outcomeAt = timestamp,
                decidedAt = capture.decidedAt ?: timestamp,
            ),
        )
    }

    private fun String.toNoteTitle(): String =
        deriveItemTitle(this) ?: trim().take(80)
}
