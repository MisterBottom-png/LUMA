package com.orbit.app.ui.screens.item

import androidx.room.withTransaction
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.ui.navigation.ItemDetailType

internal sealed interface TypeConversionOutcome {
    data object Converted : TypeConversionOutcome
    data object Missing : TypeConversionOutcome
    data object Conflict : TypeConversionOutcome
    data object Unsupported : TypeConversionOutcome
}

internal class ItemTypeConversion(
    private val database: OrbitDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun convert(
        sourceType: ItemDetailType,
        itemId: Long,
        targetType: ItemDetailType,
        reminderDueAt: Long? = null,
    ): TypeConversionOutcome {
        if (sourceType == targetType) return TypeConversionOutcome.Converted
        if (sourceType == ItemDetailType.Capture || targetType == ItemDetailType.Capture) {
            return TypeConversionOutcome.Unsupported
        }
        if (targetType == ItemDetailType.Reminder && (reminderDueAt ?: 0L) <= 0L) {
            return TypeConversionOutcome.Unsupported
        }

        val converted = database.withTransaction {
            if (targetExists(targetType, itemId)) return@withTransaction TypeConversionOutcome.Conflict
            // Read before the source row goes: deleting it cascades its label links.
            val labelIds = labelIdsOf(sourceType, itemId)
            val outcome = when (sourceType) {
                ItemDetailType.Note -> convertNote(itemId, targetType, reminderDueAt)
                ItemDetailType.Task -> convertTask(itemId, targetType, reminderDueAt)
                ItemDetailType.Reminder -> convertReminder(itemId, targetType)
                ItemDetailType.Capture -> TypeConversionOutcome.Unsupported
            }
            if (outcome == TypeConversionOutcome.Converted) attachLabels(targetType, itemId, labelIds)
            outcome
        }
        if (converted == TypeConversionOutcome.Converted) {
            if (sourceType == ItemDetailType.Reminder) runCatching { reminderScheduler.cancel(itemId) }
            if (targetType == ItemDetailType.Reminder) {
                database.reminderDao().getById(itemId)?.let { reminder ->
                    val workId = runCatching { reminderScheduler.schedule(reminder) }.getOrNull()
                    database.reminderDao().update(reminder.copy(notificationWorkId = workId))
                }
            }
        }
        return converted
    }

    private suspend fun labelIdsOf(type: ItemDetailType, id: Long): List<Long> {
        val labels = database.labelDao()
        return when (type) {
            ItemDetailType.Note -> labels.getLabelIdsForNote(id)
            ItemDetailType.Task -> labels.getLabelIdsForTask(id)
            ItemDetailType.Reminder -> labels.getLabelIdsForReminder(id)
            ItemDetailType.Capture -> emptyList()
        }
    }

    private suspend fun attachLabels(type: ItemDetailType, id: Long, labelIds: List<Long>) {
        if (labelIds.isEmpty()) return
        val labels = database.labelDao()
        when (type) {
            ItemDetailType.Note -> labels.insertNoteLabels(labelIds.map { NoteLabelCrossRef(id, it) })
            ItemDetailType.Task -> labels.insertTaskLabels(labelIds.map { TaskLabelCrossRef(id, it) })
            ItemDetailType.Reminder -> labels.insertReminderLabels(labelIds.map { ReminderLabelCrossRef(id, it) })
            ItemDetailType.Capture -> Unit
        }
    }

    private suspend fun targetExists(type: ItemDetailType, id: Long): Boolean = when (type) {
        ItemDetailType.Note -> database.noteDao().getById(id) != null
        ItemDetailType.Task -> database.taskDao().getById(id) != null
        ItemDetailType.Reminder -> database.reminderDao().getById(id) != null
        ItemDetailType.Capture -> true
    }

    private suspend fun convertNote(
        id: Long,
        target: ItemDetailType,
        reminderDueAt: Long?,
    ): TypeConversionOutcome {
        val source = database.noteDao().getById(id) ?: return TypeConversionOutcome.Missing
        val updatedAt = now()
        when (target) {
            ItemDetailType.Task -> database.taskDao().insert(
                TaskEntity(
                    id = id,
                    title = source.title,
                    notes = source.body,
                    spaceId = source.spaceId,
                    status = if (source.archived) TaskStatus.Archived else TaskStatus.Open,
                    dueAt = source.scheduledAt,
                    scheduledDateEpochDay = source.scheduledDateEpochDay,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                ),
            )
            ItemDetailType.Reminder -> database.reminderDao().insert(
                ReminderEntity(
                    id = id,
                    title = source.title,
                    notes = source.body,
                    dueAt = requireNotNull(reminderDueAt),
                    spaceId = source.spaceId,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                ),
            )
            else -> return TypeConversionOutcome.Unsupported
        }
        database.noteDao().deleteById(id)
        return TypeConversionOutcome.Converted
    }

    private suspend fun convertTask(
        id: Long,
        target: ItemDetailType,
        reminderDueAt: Long?,
    ): TypeConversionOutcome {
        val source = database.taskDao().getById(id) ?: return TypeConversionOutcome.Missing
        val updatedAt = now()
        when (target) {
            ItemDetailType.Note -> database.noteDao().insert(
                NoteEntity(
                    id = id,
                    title = source.title,
                    body = source.notes,
                    spaceId = source.spaceId,
                    archived = source.status == TaskStatus.Archived,
                    scheduledAt = source.dueAt,
                    scheduledDateEpochDay = source.scheduledDateEpochDay,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                ),
            )
            ItemDetailType.Reminder -> database.reminderDao().insert(
                ReminderEntity(
                    id = id,
                    title = source.title,
                    notes = source.notes,
                    dueAt = requireNotNull(reminderDueAt),
                    spaceId = source.spaceId,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                    completedAt = source.completedAt,
                ),
            )
            else -> return TypeConversionOutcome.Unsupported
        }
        database.taskDao().deleteById(id)
        return TypeConversionOutcome.Converted
    }

    private suspend fun convertReminder(id: Long, target: ItemDetailType): TypeConversionOutcome {
        val source = database.reminderDao().getById(id) ?: return TypeConversionOutcome.Missing
        val updatedAt = now()
        when (target) {
            ItemDetailType.Task -> database.taskDao().insert(
                TaskEntity(
                    id = id,
                    title = source.title,
                    notes = source.notes,
                    spaceId = source.spaceId,
                    status = if (source.completedAt == null) TaskStatus.Open else TaskStatus.Done,
                    dueAt = source.dueAt,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                    completedAt = source.completedAt,
                ),
            )
            ItemDetailType.Note -> database.noteDao().insert(
                NoteEntity(
                    id = id,
                    title = source.title,
                    body = source.notes,
                    spaceId = source.spaceId,
                    scheduledAt = source.dueAt,
                    createdAt = source.createdAt,
                    updatedAt = updatedAt,
                ),
            )
            else -> return TypeConversionOutcome.Unsupported
        }
        database.reminderDao().deleteById(id)
        return TypeConversionOutcome.Converted
    }
}
