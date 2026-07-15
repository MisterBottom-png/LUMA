package com.orbit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.orbit.app.data.local.entity.AiCorrectionHistoryEntity
import com.orbit.app.data.local.entity.AiSuggestionHistoryEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceAliasMemoryEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
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

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getById(id: Long): SpaceEntity?

    @Insert
    suspend fun insert(entity: SpaceEntity): Long

    @Insert
    suspend fun insertAll(entities: List<SpaceEntity>)

    @Update
    suspend fun update(entity: SpaceEntity)

    @Delete
    suspend fun delete(entity: SpaceEntity)

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM spaces")
    suspend fun deleteAll()
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
