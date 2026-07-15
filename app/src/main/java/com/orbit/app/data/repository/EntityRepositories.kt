package com.orbit.app.data.repository

import com.orbit.app.data.local.dao.CaptureDao
import com.orbit.app.data.local.dao.AiCorrectionHistoryDao
import com.orbit.app.data.local.dao.AiSuggestionHistoryDao
import com.orbit.app.data.local.dao.LearnedRuleDao
import com.orbit.app.data.local.dao.NoteDao
import com.orbit.app.data.local.dao.PersonMemoryDao
import com.orbit.app.data.local.dao.ProjectMemoryDao
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.dao.SpaceAliasMemoryDao
import com.orbit.app.data.local.dao.SpaceDao
import com.orbit.app.data.local.dao.TaskDao
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
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.shouldScheduleNotification
import kotlinx.coroutines.flow.Flow

interface EntityRepository<T> {
    fun observeAll(): Flow<List<T>>
    suspend fun getById(id: Long): T?
    suspend fun insert(entity: T): Long
    suspend fun update(entity: T)
    suspend fun delete(entity: T)
    suspend fun deleteById(id: Long)
}

interface ResettableRepository<T> : EntityRepository<T> {
    suspend fun deleteAll()
}

interface ToggleableMemoryRepository<T> : ResettableRepository<T> {
    fun observeEnabled(): Flow<List<T>>
}

interface CaptureRepository : EntityRepository<CaptureEntity>
interface SpaceRepository : EntityRepository<SpaceEntity>
interface NoteRepository : EntityRepository<NoteEntity>
interface TaskRepository : EntityRepository<TaskEntity>
interface ReminderRepository : EntityRepository<ReminderEntity>
interface AiSuggestionHistoryRepository : ResettableRepository<AiSuggestionHistoryEntity>
interface AiCorrectionHistoryRepository : ResettableRepository<AiCorrectionHistoryEntity>
interface LearnedRuleRepository : ToggleableMemoryRepository<LearnedRuleEntity>
interface PersonMemoryRepository : ToggleableMemoryRepository<PersonMemoryEntity>
interface ProjectMemoryRepository : ToggleableMemoryRepository<ProjectMemoryEntity>
interface SpaceAliasMemoryRepository : ToggleableMemoryRepository<SpaceAliasMemoryEntity>

class RoomCaptureRepository(private val dao: CaptureDao) : CaptureRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: CaptureEntity) = dao.insert(entity)
    override suspend fun update(entity: CaptureEntity) = dao.update(entity)
    override suspend fun delete(entity: CaptureEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

class RoomSpaceRepository(private val dao: SpaceDao) : SpaceRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: SpaceEntity) = dao.insert(entity)
    override suspend fun update(entity: SpaceEntity) = dao.update(entity)
    override suspend fun delete(entity: SpaceEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: NoteEntity) = dao.insert(entity.requireValidSchedule())
    override suspend fun update(entity: NoteEntity) = dao.update(entity.requireValidSchedule())
    override suspend fun delete(entity: NoteEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

class RoomTaskRepository(private val dao: TaskDao) : TaskRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: TaskEntity) = dao.insert(entity.requireValidSchedule())
    override suspend fun update(entity: TaskEntity) = dao.update(entity.requireValidSchedule())
    override suspend fun delete(entity: TaskEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

private fun NoteEntity.requireValidSchedule(): NoteEntity = apply {
    require(scheduledDateEpochDay == null || scheduledAt == null) {
        "A note cannot be both date-only and timed"
    }
}

private fun TaskEntity.requireValidSchedule(): TaskEntity = apply {
    require(scheduledDateEpochDay == null || dueAt == null) {
        "A task cannot be both date-only and timed"
    }
}

class RoomReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
) : ReminderRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)

    override suspend fun insert(entity: ReminderEntity): Long {
        val id = dao.insert(entity)
        val stored = entity.copy(id = id)
        if (stored.shouldScheduleNotification()) {
            val workId = runCatching { scheduler.schedule(stored) }.getOrNull()
            dao.update(stored.copy(notificationWorkId = workId))
        }
        return id
    }

    override suspend fun update(entity: ReminderEntity) {
        require(entity.id != 0L) { "A stored reminder is required" }
        dao.update(entity)
        if (entity.shouldScheduleNotification()) {
            val workId = runCatching { scheduler.reschedule(entity) }.getOrNull()
            dao.update(entity.copy(notificationWorkId = workId))
        } else {
            runCatching { scheduler.cancel(entity.id) }
            if (entity.notificationWorkId != null) {
                dao.update(entity.copy(notificationWorkId = null))
            }
        }
    }

    override suspend fun delete(entity: ReminderEntity) {
        runCatching { scheduler.cancel(entity.id) }
        dao.delete(entity)
    }

    override suspend fun deleteById(id: Long) {
        runCatching { scheduler.cancel(id) }
        dao.deleteById(id)
    }
}

class RoomAiSuggestionHistoryRepository(
    private val dao: AiSuggestionHistoryDao,
) : AiSuggestionHistoryRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: AiSuggestionHistoryEntity) = dao.insert(entity)
    override suspend fun update(entity: AiSuggestionHistoryEntity) = dao.update(entity)
    override suspend fun delete(entity: AiSuggestionHistoryEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomAiCorrectionHistoryRepository(
    private val dao: AiCorrectionHistoryDao,
) : AiCorrectionHistoryRepository {
    override fun observeAll() = dao.observeAll()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: AiCorrectionHistoryEntity) = dao.insert(entity)
    override suspend fun update(entity: AiCorrectionHistoryEntity) = dao.update(entity)
    override suspend fun delete(entity: AiCorrectionHistoryEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomLearnedRuleRepository(private val dao: LearnedRuleDao) : LearnedRuleRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeEnabled() = dao.observeEnabled()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: LearnedRuleEntity) = dao.insert(entity)
    override suspend fun update(entity: LearnedRuleEntity) = dao.update(entity)
    override suspend fun delete(entity: LearnedRuleEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomPersonMemoryRepository(private val dao: PersonMemoryDao) : PersonMemoryRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeEnabled() = dao.observeEnabled()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: PersonMemoryEntity) = dao.insert(entity)
    override suspend fun update(entity: PersonMemoryEntity) = dao.update(entity)
    override suspend fun delete(entity: PersonMemoryEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomProjectMemoryRepository(private val dao: ProjectMemoryDao) : ProjectMemoryRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeEnabled() = dao.observeEnabled()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: ProjectMemoryEntity) = dao.insert(entity)
    override suspend fun update(entity: ProjectMemoryEntity) = dao.update(entity)
    override suspend fun delete(entity: ProjectMemoryEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}

class RoomSpaceAliasMemoryRepository(
    private val dao: SpaceAliasMemoryDao,
) : SpaceAliasMemoryRepository {
    override fun observeAll() = dao.observeAll()
    override fun observeEnabled() = dao.observeEnabled()
    override suspend fun getById(id: Long) = dao.getById(id)
    override suspend fun insert(entity: SpaceAliasMemoryEntity) = dao.insert(entity)
    override suspend fun update(entity: SpaceAliasMemoryEntity) = dao.update(entity)
    override suspend fun delete(entity: SpaceAliasMemoryEntity) = dao.delete(entity)
    override suspend fun deleteById(id: Long) = dao.deleteById(id)
    override suspend fun deleteAll() = dao.deleteAll()
}
