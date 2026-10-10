package com.orbit.app.data.local.dao

import com.orbit.app.data.local.DuplicateSpaceNameException
import com.orbit.app.data.local.SpaceNames
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.SpaceAliasMemoryEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<CaptureEntity>>

    @Query("SELECT * FROM captures WHERE id = :id")
    suspend fun getById(id: Long): CaptureEntity?

    @Insert
    suspend fun insert(entity: CaptureEntity): Long

    @Insert
    suspend fun insertAll(entities: List<CaptureEntity>)

    @Update
    suspend fun update(entity: CaptureEntity)

    @Delete
    suspend fun delete(entity: CaptureEntity)

    @Query("DELETE FROM captures WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM captures")
    suspend fun deleteAll()
}

data class BrainDumpPendingCount(val captureId: Long, val pending: Int)

@Dao
interface CaptureSuggestionDao {
    @Query("SELECT * FROM capture_suggestions")
    fun observeAll(): Flow<List<CaptureSuggestionEntity>>

    @Query("SELECT * FROM capture_suggestions")
    suspend fun getAll(): List<CaptureSuggestionEntity>

    @Query("SELECT * FROM capture_suggestions WHERE captureId = :captureId")
    suspend fun getByCaptureId(captureId: Long): CaptureSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CaptureSuggestionEntity)

    @Insert
    suspend fun insertAll(entities: List<CaptureSuggestionEntity>)

    @Query("UPDATE capture_suggestions SET dismissed = :dismissed, updatedAt = :updatedAt WHERE captureId = :captureId")
    suspend fun setDismissed(captureId: Long, dismissed: Boolean, updatedAt: Long)

    @Query("DELETE FROM capture_suggestions WHERE captureId = :captureId")
    suspend fun deleteByCaptureId(captureId: Long)

    @Query("DELETE FROM capture_suggestions")
    suspend fun deleteAll()
}

@Dao
interface BrainDumpDao {
    @Query("SELECT * FROM brain_dump_sessions ORDER BY updatedAt DESC")
    fun observeSessions(): Flow<List<BrainDumpSessionEntity>>

    @Query("SELECT * FROM brain_dump_sessions ORDER BY updatedAt DESC")
    suspend fun getSessions(): List<BrainDumpSessionEntity>

    @Query("SELECT * FROM brain_dump_sessions WHERE captureId = :captureId")
    suspend fun getSession(captureId: Long): BrainDumpSessionEntity?

    @Query("SELECT * FROM brain_dump_items WHERE captureId = :captureId ORDER BY ordinal")
    suspend fun getItems(captureId: Long): List<BrainDumpItemEntity>

    @Query("SELECT * FROM brain_dump_items WHERE captureId = :captureId AND sourceKey = :sourceKey")
    suspend fun getItem(captureId: Long, sourceKey: String): BrainDumpItemEntity?

    @Query("SELECT COUNT(*) FROM brain_dump_items WHERE captureId = :captureId AND outcome = 'Pending'")
    suspend fun pendingCount(captureId: Long): Int

    @Insert
    suspend fun insertSession(entity: BrainDumpSessionEntity)

    @Insert
    suspend fun insertItems(entities: List<BrainDumpItemEntity>)

    @Transaction
    suspend fun insertSessionWithItems(
        session: BrainDumpSessionEntity,
        items: List<BrainDumpItemEntity>,
    ) {
        insertSession(session)
        insertItems(items)
    }

    @Insert
    suspend fun insertSessions(entities: List<BrainDumpSessionEntity>)

    @Update
    suspend fun updateItem(entity: BrainDumpItemEntity)

    @Query("UPDATE brain_dump_items SET outcome = :outcome, updatedAt = :updatedAt WHERE id = :itemId AND outcome = 'Pending'")
    suspend fun markPendingItem(itemId: Long, outcome: BrainDumpItemOutcome, updatedAt: Long): Int

    @Query("DELETE FROM brain_dump_sessions WHERE captureId = :captureId")
    suspend fun deleteSession(captureId: Long)

    @Query("DELETE FROM brain_dump_items")
    suspend fun deleteAllItems()

    /** Pending Brain Dump pieces per capture, for the To sort list. */
    @Query(
        "SELECT captureId, COUNT(*) AS pending FROM brain_dump_items " +
            "WHERE outcome = 'Pending' GROUP BY captureId",
    )
    fun observePendingCounts(): Flow<List<BrainDumpPendingCount>>

    @Query("DELETE FROM brain_dump_sessions")
    suspend fun deleteAllSessions()
}

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getById(id: Long): SpaceEntity?

    @Query("SELECT * FROM spaces ORDER BY sortOrder, id")
    suspend fun getAll(): List<SpaceEntity>

    /** Checks and inserts in one transaction so two quick creates cannot both win. */
    @Transaction
    suspend fun insertWithUniqueName(entity: SpaceEntity): Long {
        require(SpaceNames.clean(entity.name).isNotEmpty()) { "A Space name cannot be blank" }
        SpaceNames.conflict(entity.name, getAll())?.let { throw DuplicateSpaceNameException(it) }
        return insert(entity.copy(name = SpaceNames.clean(entity.name)))
    }

    /**
     * Only a real rename is checked, so archiving or hiding a Space restored from an
     * older backup that already holds duplicate names never fails.
     */
    @Transaction
    suspend fun updateWithUniqueName(entity: SpaceEntity) {
        val stored = getById(entity.id)
        val renamed = stored == null || SpaceNames.normalize(stored.name) != SpaceNames.normalize(entity.name)
        if (renamed) {
            require(SpaceNames.clean(entity.name).isNotEmpty()) { "A Space name cannot be blank" }
            SpaceNames.conflict(entity.name, getAll(), excludingSpaceId = entity.id)
                ?.let { throw DuplicateSpaceNameException(it) }
        }
        update(if (renamed) entity.copy(name = SpaceNames.clean(entity.name)) else entity)
    }

    /** Starter Spaces in one transaction; names already in use are skipped, not duplicated. */
    @Transaction
    suspend fun insertAllSkippingTakenNames(entities: List<SpaceEntity>): Int {
        val known = getAll().toMutableList()
        var inserted = 0
        entities.forEach { entity ->
            if (SpaceNames.clean(entity.name).isEmpty() || SpaceNames.conflict(entity.name, known) != null) return@forEach
            val clean = entity.copy(name = SpaceNames.clean(entity.name))
            known += clean.copy(id = insert(clean))
            inserted++
        }
        return inserted
    }

    @Insert
    suspend fun insert(entity: SpaceEntity): Long

    @Insert
    suspend fun insertAll(entities: List<SpaceEntity>)

    @Update
    suspend fun update(entity: SpaceEntity)

    @Transaction
    suspend fun swapSortOrder(firstId: Long, secondId: Long, updatedAt: Long) {
        val first = getById(firstId) ?: return
        val second = getById(secondId) ?: return
        update(first.copy(sortOrder = second.sortOrder, updatedAt = updatedAt))
        update(second.copy(sortOrder = first.sortOrder, updatedAt = updatedAt))
    }

    @Delete
    suspend fun delete(entity: SpaceEntity)

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM spaces")
    suspend fun deleteAll()
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels ORDER BY normalizedName, id")
    fun observeAll(): Flow<List<LabelEntity>>

    @Query("SELECT * FROM labels ORDER BY normalizedName, id")
    suspend fun getAll(): List<LabelEntity>

    @Query("SELECT * FROM labels WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): LabelEntity?

    @Insert
    suspend fun insert(entity: LabelEntity): Long

    @Insert
    suspend fun insertAll(entities: List<LabelEntity>)

    @Update
    suspend fun update(entity: LabelEntity)

    @Delete
    suspend fun delete(entity: LabelEntity)

    @Query("SELECT * FROM note_labels ORDER BY noteId, labelId")
    suspend fun getAllNoteLabels(): List<NoteLabelCrossRef>

    @Query("SELECT * FROM task_labels ORDER BY taskId, labelId")
    suspend fun getAllTaskLabels(): List<TaskLabelCrossRef>

    @Query("SELECT * FROM reminder_labels ORDER BY reminderId, labelId")
    suspend fun getAllReminderLabels(): List<ReminderLabelCrossRef>

    @Insert
    suspend fun insertNoteLabels(relations: List<NoteLabelCrossRef>)

    @Insert
    suspend fun insertTaskLabels(relations: List<TaskLabelCrossRef>)

    @Insert
    suspend fun insertReminderLabels(relations: List<ReminderLabelCrossRef>)

    @Query("DELETE FROM note_labels WHERE noteId = :noteId")
    suspend fun deleteNoteLabels(noteId: Long)

    @Query("DELETE FROM task_labels WHERE taskId = :taskId")
    suspend fun deleteTaskLabels(taskId: Long)

    @Query("DELETE FROM reminder_labels WHERE reminderId = :reminderId")
    suspend fun deleteReminderLabels(reminderId: Long)

    @Query("DELETE FROM note_labels")
    suspend fun deleteAllNoteLabels()

    @Query("DELETE FROM task_labels")
    suspend fun deleteAllTaskLabels()

    @Query("DELETE FROM reminder_labels")
    suspend fun deleteAllReminderLabels()

    @Query("DELETE FROM labels")
    suspend fun deleteAllLabels()

    @Transaction
    suspend fun replaceNoteLabels(noteId: Long, labelIds: Set<Long>) {
        deleteNoteLabels(noteId)
        insertNoteLabels(labelIds.sorted().map { labelId -> NoteLabelCrossRef(noteId, labelId) })
    }

    @Transaction
    suspend fun replaceTaskLabels(taskId: Long, labelIds: Set<Long>) {
        deleteTaskLabels(taskId)
        insertTaskLabels(labelIds.sorted().map { labelId -> TaskLabelCrossRef(taskId, labelId) })
    }

    @Transaction
    suspend fun replaceReminderLabels(reminderId: Long, labelIds: Set<Long>) {
        deleteReminderLabels(reminderId)
        insertReminderLabels(
            labelIds.sorted().map { labelId -> ReminderLabelCrossRef(reminderId, labelId) },
        )
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query(
        """
        SELECT * FROM notes
        WHERE archived = 0 AND (
            (scheduledDateEpochDay >= :startEpochDay AND scheduledDateEpochDay < :endEpochDay)
            OR (scheduledAt >= :startMillis AND scheduledAt < :endMillis)
        )
        """,
    )
    fun observeCalendarRange(
        startEpochDay: Long,
        endEpochDay: Long,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(entity: NoteEntity): Long

    @Insert
    suspend fun insertAll(entities: List<NoteEntity>)

    @Update
    suspend fun update(entity: NoteEntity)

    @Delete
    suspend fun delete(entity: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks
        WHERE status != 'Archived' AND (
            (scheduledDateEpochDay >= :startEpochDay AND scheduledDateEpochDay < :endEpochDay)
            OR (dueAt >= :startMillis AND dueAt < :endMillis)
        )
        """,
    )
    fun observeCalendarRange(
        startEpochDay: Long,
        endEpochDay: Long,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<TaskEntity>>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Insert
    suspend fun insertAll(entities: List<TaskEntity>)

    @Update
    suspend fun update(entity: TaskEntity)

    @Delete
    suspend fun delete(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueAt")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE dueAt >= :startMillis AND dueAt < :endMillis")
    fun observeCalendarRange(startMillis: Long, endMillis: Long): Flow<List<ReminderEntity>>

    /** Active repeating reminders whose current occurrence is before [endMillis]. */
    @Query("SELECT * FROM reminders WHERE repeatRule IS NOT NULL AND completedAt IS NULL AND dueAt < :endMillis")
    fun observeRepeatingBefore(endMillis: Long): Flow<List<ReminderEntity>>

    @Insert
    suspend fun insert(entity: ReminderEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ReminderEntity>)

    @Update
    suspend fun update(entity: ReminderEntity)

    @Delete
    suspend fun delete(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    /** Stores the scheduling token without touching user-visible fields such as updatedAt. */
    @Query("UPDATE reminders SET notificationWorkId = :workId WHERE id = :id")
    suspend fun updateNotificationWorkId(id: Long, workId: String?)

    /**
     * Atomically claims delivery of [notificationTime] for one reminder. Returns 1 for
     * the first caller (alarm or worker) and 0 for every later or stale attempt, so a
     * reminder is shown at most once per scheduled time.
     */
    @Query(
        """
        UPDATE reminders SET deliveredNotificationAt = :notificationTime
        WHERE id = :id
          AND completedAt IS NULL
          AND notificationEnabled = 1
          AND (deliveredNotificationAt IS NULL OR deliveredNotificationAt != :notificationTime)
          AND COALESCE(snoozedUntil, dueAt - (notificationOffsetMinutes * 60000)) = :notificationTime
        """,
    )
    suspend fun claimDelivery(id: Long, notificationTime: Long): Int

    /** Releases a claim when the notification could not actually be posted. */
    @Query(
        "UPDATE reminders SET deliveredNotificationAt = NULL " +
            "WHERE id = :id AND deliveredNotificationAt = :notificationTime",
    )
    suspend fun releaseDelivery(id: Long, notificationTime: Long)

    /** Marks a past notification time as handled so it never rings (restore, old misses). */
    @Query(
        "UPDATE reminders SET deliveredNotificationAt = :notificationTime, notificationWorkId = NULL " +
            "WHERE id = :id",
    )
    suspend fun markHandled(id: Long, notificationTime: Long)
}

@Dao
interface AiSuggestionHistoryDao {
    @Query("SELECT * FROM ai_suggestion_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiSuggestionHistoryEntity>>

    @Query("SELECT * FROM ai_suggestion_history WHERE id = :id")
    suspend fun getById(id: Long): AiSuggestionHistoryEntity?

    @Insert
    suspend fun insert(entity: AiSuggestionHistoryEntity): Long

    @Update
    suspend fun update(entity: AiSuggestionHistoryEntity)

    @Delete
    suspend fun delete(entity: AiSuggestionHistoryEntity)

    @Query("DELETE FROM ai_suggestion_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_suggestion_history")
    suspend fun deleteAll()

    @Query("DELETE FROM ai_suggestion_history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}

@Dao
interface AiCorrectionHistoryDao {
    @Query("SELECT * FROM ai_correction_history ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiCorrectionHistoryEntity>>

    @Query("SELECT * FROM ai_correction_history WHERE id = :id")
    suspend fun getById(id: Long): AiCorrectionHistoryEntity?

    @Insert
    suspend fun insert(entity: AiCorrectionHistoryEntity): Long

    @Update
    suspend fun update(entity: AiCorrectionHistoryEntity)

    @Delete
    suspend fun delete(entity: AiCorrectionHistoryEntity)

    @Query("DELETE FROM ai_correction_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ai_correction_history WHERE createdAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM ai_correction_history")
    suspend fun deleteAll()
}

@Dao
interface LearnedRuleDao {
    @Query("SELECT * FROM learned_rules ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LearnedRuleEntity>>

    @Query("SELECT * FROM learned_rules WHERE enabled = 1 ORDER BY strength DESC, updatedAt DESC")
    fun observeEnabled(): Flow<List<LearnedRuleEntity>>

    @Query("SELECT * FROM learned_rules WHERE id = :id")
    suspend fun getById(id: Long): LearnedRuleEntity?

    @Insert
    suspend fun insert(entity: LearnedRuleEntity): Long

    @Update
    suspend fun update(entity: LearnedRuleEntity)

    @Delete
    suspend fun delete(entity: LearnedRuleEntity)

    @Query("DELETE FROM learned_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM learned_rules")
    suspend fun deleteAll()
}

@Dao
interface PersonMemoryDao {
    @Query("SELECT * FROM person_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<PersonMemoryEntity>>

    @Query("SELECT * FROM person_memory WHERE enabled = 1 ORDER BY strength DESC, updatedAt DESC")
    fun observeEnabled(): Flow<List<PersonMemoryEntity>>

    @Query("SELECT * FROM person_memory WHERE id = :id")
    suspend fun getById(id: Long): PersonMemoryEntity?

    @Insert
    suspend fun insert(entity: PersonMemoryEntity): Long

    @Update
    suspend fun update(entity: PersonMemoryEntity)

    @Delete
    suspend fun delete(entity: PersonMemoryEntity)

    @Query("DELETE FROM person_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM person_memory")
    suspend fun deleteAll()
}

@Dao
interface ProjectMemoryDao {
    @Query("SELECT * FROM project_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectMemoryEntity>>

    @Query("SELECT * FROM project_memory WHERE enabled = 1 ORDER BY strength DESC, updatedAt DESC")
    fun observeEnabled(): Flow<List<ProjectMemoryEntity>>

    @Query("SELECT * FROM project_memory WHERE id = :id")
    suspend fun getById(id: Long): ProjectMemoryEntity?

    @Insert
    suspend fun insert(entity: ProjectMemoryEntity): Long

    @Update
    suspend fun update(entity: ProjectMemoryEntity)

    @Delete
    suspend fun delete(entity: ProjectMemoryEntity)

    @Query("DELETE FROM project_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM project_memory")
    suspend fun deleteAll()
}

@Dao
interface SpaceAliasMemoryDao {
    @Query("SELECT * FROM space_alias_memory ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SpaceAliasMemoryEntity>>

    @Query("SELECT * FROM space_alias_memory WHERE enabled = 1 ORDER BY strength DESC, updatedAt DESC")
    fun observeEnabled(): Flow<List<SpaceAliasMemoryEntity>>

    @Query("SELECT * FROM space_alias_memory WHERE id = :id")
    suspend fun getById(id: Long): SpaceAliasMemoryEntity?

    @Insert
    suspend fun insert(entity: SpaceAliasMemoryEntity): Long

    @Update
    suspend fun update(entity: SpaceAliasMemoryEntity)

    @Delete
    suspend fun delete(entity: SpaceAliasMemoryEntity)

    @Query("DELETE FROM space_alias_memory WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM space_alias_memory")
    suspend fun deleteAll()
}
