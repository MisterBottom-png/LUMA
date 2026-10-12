package com.orbit.app.ui.screens.item

import androidx.room.withTransaction
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.ui.navigation.ItemDetailType

internal sealed interface TypeConversionOutcome {
    /** The item now lives under [itemId] in its new type's table. */
    data class Converted(val itemId: Long) : TypeConversionOutcome
    data object Missing : TypeConversionOutcome
    data object Unsupported : TypeConversionOutcome
}

internal class ItemTypeConversion(
    private val database: OrbitDatabase,
    private val reminderScheduler: ReminderScheduler,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /**
     * Moves the item to [targetType]'s table under a new id (review F07): ids are per
     * table, so keeping the same id could collide with another item. Labels, the thought
     * it came from and the alarm move with it; Undo restores the original id.
     */
    suspend fun convert(
        sourceType: ItemDetailType,
        itemId: Long,
        targetType: ItemDetailType,
        reminderDueAt: Long? = null,
    ): TypeConversionOutcome {
        if (sourceType == targetType) return TypeConversionOutcome.Converted(itemId)
        if (sourceType == ItemDetailType.Capture || targetType == ItemDetailType.Capture) {
            return TypeConversionOutcome.Unsupported
        }
        if (targetType == ItemDetailType.Reminder && (reminderDueAt ?: 0L) <= 0L) {
            return TypeConversionOutcome.Unsupported
        }

        val converted = database.withTransaction {
            // Read before the source row goes: deleting it cascades its label links.
            val labelIds = labelIdsOf(sourceType, itemId)
            val outcome = when (sourceType) {
                ItemDetailType.Note -> convertNote(itemId, targetType, reminderDueAt)
                ItemDetailType.Task -> convertTask(itemId, targetType, reminderDueAt)
                ItemDetailType.Reminder -> convertReminder(itemId, targetType)
                ItemDetailType.Capture -> TypeConversionOutcome.Unsupported
            }
            if (outcome is TypeConversionOutcome.Converted) {
                attachLabels(targetType, outcome.itemId, labelIds)
                moveThoughtLink(sourceType, itemId, targetType, outcome.itemId)
            }
            outcome
        }
        if (converted is TypeConversionOutcome.Converted) {
            if (sourceType == ItemDetailType.Reminder) runCatching { reminderScheduler.cancel(itemId) }
            if (targetType == ItemDetailType.Reminder) {
                database.reminderDao().getById(converted.itemId)?.let { reminder ->
                    val workId = runCatching { reminderScheduler.schedule(reminder) }.getOrNull()
                    database.reminderDao().update(reminder.copy(notificationWorkId = workId))
                }
            }
        }
        return converted
    }

    /**
     * N008: the thought this item was made from follows it, so it never points at an
     * item that is gone or says "Reminder set" for a task. A reminder's real outcome is
     * written by the caller once its alarm is set.
     */
    private suspend fun moveThoughtLink(
        sourceType: ItemDetailType,
        oldId: Long,
        targetType: ItemDetailType,
        newId: Long,
    ) {
        val sourceKinds = suggestedTypesOf(sourceType)
        val timestamp = now()
        database.captureDao().getLinkedTo(oldId)
            .filter { it.status == CaptureStatus.Processed && it.suggestedType in sourceKinds }
            .forEach { capture ->
                val targetKind = suggestedTypesOf(targetType).first()
                database.captureDao().update(
                    capture.copy(
                        linkedItemId = newId,
                        suggestedType = targetKind,
                        outcome = if (targetType == ItemDetailType.Reminder) CaptureOutcome.ReminderSet else CaptureOutcome.Filed,
                        outcomeReason = CaptureOutcomeReason.None,
                        outcomeAt = timestamp,
                        updatedAt = timestamp,
                    ),
                )
                if (targetType == ItemDetailType.Reminder) {
                    database.reminderDao().getById(newId)?.let {
                        database.reminderDao().update(it.copy(linkedCaptureId = capture.id))
                    }
                }
            }
    }

    private fun suggestedTypesOf(type: ItemDetailType): List<SuggestedItemType> = when (type) {
        ItemDetailType.Note -> listOf(SuggestedItemType.Note)
        ItemDetailType.Task -> listOf(SuggestedItemType.Task, SuggestedItemType.MondayItem)
        ItemDetailType.Reminder -> listOf(SuggestedItemType.Reminder)
        ItemDetailType.Capture -> emptyList()
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
        val newId = when (target) {
            ItemDetailType.Task -> database.taskDao().insert(
                TaskEntity(
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
        return TypeConversionOutcome.Converted(newId)
    }

    private suspend fun convertTask(
        id: Long,
        target: ItemDetailType,
        reminderDueAt: Long?,
    ): TypeConversionOutcome {
        val source = database.taskDao().getById(id) ?: return TypeConversionOutcome.Missing
        val updatedAt = now()
        val newId = when (target) {
            ItemDetailType.Note -> database.noteDao().insert(
                NoteEntity(
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
        return TypeConversionOutcome.Converted(newId)
    }

    private suspend fun convertReminder(id: Long, target: ItemDetailType): TypeConversionOutcome {
        val source = database.reminderDao().getById(id) ?: return TypeConversionOutcome.Missing
        val updatedAt = now()
        val newId = when (target) {
            ItemDetailType.Task -> database.taskDao().insert(
                TaskEntity(
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
        return TypeConversionOutcome.Converted(newId)
    }

    /** The item exactly as it is now, so a type change can be undone without loss. */
    suspend fun snapshot(type: ItemDetailType, itemId: Long): TypeConversionSnapshot? = when (type) {
        ItemDetailType.Note -> database.noteDao().getById(itemId)?.let {
            TypeConversionSnapshot(type, note = it, labelIds = labelIdsOf(type, itemId))
        }
        ItemDetailType.Task -> database.taskDao().getById(itemId)?.let {
            TypeConversionSnapshot(type, task = it, labelIds = labelIdsOf(type, itemId))
        }
        ItemDetailType.Reminder -> database.reminderDao().getById(itemId)?.let {
            TypeConversionSnapshot(type, reminder = it, labelIds = labelIdsOf(type, itemId))
        }
        ItemDetailType.Capture -> null
    }

    /** [snapshot] together with how the converted item looks right after the change. */
    suspend fun afterConversion(
        snapshot: TypeConversionSnapshot,
        convertedType: ItemDetailType,
        itemId: Long,
    ): TypeConversionSnapshot = snapshot.copy(converted = convertedStateOf(convertedType, itemId))

    private suspend fun convertedStateOf(type: ItemDetailType, id: Long): ConvertedState? {
        val updatedAt = when (type) {
            ItemDetailType.Note -> database.noteDao().getById(id)?.updatedAt
            ItemDetailType.Task -> database.taskDao().getById(id)?.updatedAt
            ItemDetailType.Reminder -> database.reminderDao().getById(id)?.updatedAt
            ItemDetailType.Capture -> null
        } ?: return null
        return ConvertedState(updatedAt, labelIdsOf(type, id).toSet())
    }

    /**
     * Undo for a type change: the converted row goes and the original comes back as it
     * was, with its labels, repeat, notification and status. False if it changed since.
     */
    suspend fun undo(snapshot: TypeConversionSnapshot, currentType: ItemDetailType, itemId: Long): Boolean {
        val originalId = snapshot.originalId ?: return false
        val restored = database.withTransaction restore@{
            // An edit made after the change would be lost by deleting the row: keep it.
            if (snapshot.converted != null && snapshot.converted != convertedStateOf(currentType, itemId)) {
                return@restore false
            }
            if (targetExists(snapshot.type, originalId)) return@restore false
            when (currentType) {
                ItemDetailType.Note -> database.noteDao().getById(itemId)?.let { database.noteDao().deleteById(itemId) }
                ItemDetailType.Task -> database.taskDao().getById(itemId)?.let { database.taskDao().deleteById(itemId) }
                ItemDetailType.Reminder -> database.reminderDao().getById(itemId)?.let { database.reminderDao().deleteById(itemId) }
                ItemDetailType.Capture -> null
            } ?: return@restore false
            snapshot.note?.let { database.noteDao().insert(it) }
            snapshot.task?.let { database.taskDao().insert(it) }
            snapshot.reminder?.let { database.reminderDao().insert(it.copy(notificationWorkId = null)) }
            attachLabels(snapshot.type, originalId, snapshot.labelIds)
            moveThoughtLink(currentType, itemId, snapshot.type, originalId)
            true
        }
        if (restored) {
            if (currentType == ItemDetailType.Reminder) runCatching { reminderScheduler.cancel(itemId) }
            if (snapshot.type == ItemDetailType.Reminder) {
                database.reminderDao().getById(originalId)?.let { reminder ->
                    val workId = runCatching { reminderScheduler.schedule(reminder) }.getOrNull()
                    database.reminderDao().update(reminder.copy(notificationWorkId = workId))
                }
            }
        }
        return restored
    }
}

internal data class TypeConversionSnapshot(
    val type: ItemDetailType,
    val note: NoteEntity? = null,
    val task: TaskEntity? = null,
    val reminder: ReminderEntity? = null,
    val labelIds: List<Long> = emptyList(),
    /** The converted item right after the change; Undo refuses once it differs. */
    val converted: ConvertedState? = null,
) {
    /** The id the item had before the change; Undo brings it back under it. */
    val originalId: Long? get() = note?.id ?: task?.id ?: reminder?.id
}

internal data class ConvertedState(val updatedAt: Long, val labelIds: Set<Long>)

/** What changing an item's type would remove, so the confirmation can name it. */
internal sealed interface TypeChangeLoss {
    data object Repeat : TypeChangeLoss
    data object Notification : TypeChangeLoss
    data class Status(val status: TaskStatus) : TypeChangeLoss
}

internal fun typeChangeLosses(
    currentType: ItemDetailType,
    targetType: ItemDetailType,
    repeat: com.orbit.app.reminders.ReminderRepeat?,
    notificationEnabled: Boolean?,
    taskStatus: TaskStatus?,
): List<TypeChangeLoss> = buildList {
    if (currentType == targetType) return@buildList
    if (currentType == ItemDetailType.Reminder) {
        if (repeat != null) add(TypeChangeLoss.Repeat)
        if (notificationEnabled == true) add(TypeChangeLoss.Notification)
    }
    if (currentType == ItemDetailType.Task && (taskStatus == TaskStatus.WaitingFor || taskStatus == TaskStatus.Someday)) {
        add(TypeChangeLoss.Status(taskStatus))
    }
}
