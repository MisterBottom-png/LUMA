package com.orbit.app.data.export

import androidx.room.withTransaction
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.shouldScheduleNotification
import kotlinx.coroutines.flow.first

class LocalRestorePlan internal constructor(
    val existingCounts: LocalDataCounts,
    val restoredCounts: LocalDataCounts,
    internal val existingSnapshot: LocalDataSnapshot,
    internal val restoredSnapshot: LocalDataSnapshot,
)

data class LocalRestoreResult(
    val restoredCounts: LocalDataCounts,
    val remindersReconciled: Boolean,
)

class LocalDataRestoreException(message: String) : IllegalStateException(message)

interface LocalDataRestoreStore {
    suspend fun read(): LocalDataSnapshot
    suspend fun replace(snapshot: LocalDataSnapshot)
}

fun interface ReminderRestoreReconciler {
    suspend fun reconcile(
        previousReminderIds: Set<Long>,
        restoredReminders: List<ReminderEntity>,
    ): Boolean
}

class LocalDataRestorer(
    private val store: LocalDataRestoreStore,
    private val reminderReconciler: ReminderRestoreReconciler,
) {
    suspend fun prepare(json: String?): LocalRestorePlan? {
        if (json == null) return null
        val restored = LocalDataBackupCodec.decode(json)
        val existing = store.read()
        return LocalRestorePlan(
            existingCounts = existing.counts(),
            restoredCounts = restored.counts(),
            existingSnapshot = existing,
            restoredSnapshot = restored,
        )
    }

    suspend fun restore(plan: LocalRestorePlan): LocalRestoreResult {
        val current = store.read()
        if (current != plan.existingSnapshot) {
            throw LocalDataRestoreException(
                "Local data changed after the restore summary was prepared. Select the export again.",
            )
        }
        store.replace(plan.restoredSnapshot)
        val reconciled = reminderReconciler.reconcile(
            previousReminderIds = current.reminders.mapTo(hashSetOf()) { it.id },
            restoredReminders = plan.restoredSnapshot.reminders,
        )
        return LocalRestoreResult(
            restoredCounts = plan.restoredCounts,
            remindersReconciled = reconciled,
        )
    }
}

class RoomLocalDataRestoreStore(
    private val database: OrbitDatabase,
) : LocalDataRestoreStore {
    override suspend fun read() = LocalDataSnapshot(
        spaces = database.spaceDao().observeAll().first(),
        captures = database.captureDao().observeAll().first(),
        notes = database.noteDao().observeAll().first(),
        tasks = database.taskDao().observeAll().first(),
        reminders = database.reminderDao().observeAll().first(),
    )

    override suspend fun replace(snapshot: LocalDataSnapshot) {
        database.withTransaction {
            val aliases = database.spaceAliasMemoryDao().observeAll().first()
            val suggestionHistory = database.aiSuggestionHistoryDao().observeAll().first()

            database.reminderDao().deleteAll()
            database.noteDao().deleteAll()
            database.taskDao().deleteAll()
            database.captureDao().deleteAll()
            database.spaceDao().deleteAll()

            database.spaceDao().insertAll(snapshot.spaces)
            database.captureDao().insertAll(snapshot.captures)
            database.noteDao().insertAll(snapshot.notes)
            database.taskDao().insertAll(snapshot.tasks)
            database.reminderDao().insertAll(snapshot.reminders)

            val restoredSpaceIds = snapshot.spaces.mapTo(hashSetOf()) { it.id }
            aliases.filter { it.spaceId in restoredSpaceIds }.forEach {
                database.spaceAliasMemoryDao().insert(it)
            }
            val restoredCaptureIds = snapshot.captures.mapTo(hashSetOf()) { it.id }
            suggestionHistory.filter { it.captureId in restoredCaptureIds }.forEach {
                database.aiSuggestionHistoryDao().update(it)
            }
        }
    }
}

class LocalReminderRestoreReconciler(
    private val scheduler: ReminderScheduler,
    private val reminderDao: ReminderDao,
) : ReminderRestoreReconciler {
    override suspend fun reconcile(
        previousReminderIds: Set<Long>,
        restoredReminders: List<ReminderEntity>,
    ): Boolean {
        var reconciled = true
        (previousReminderIds + restoredReminders.map { it.id }).forEach { reminderId ->
            if (runCatching { scheduler.cancel(reminderId) }.isFailure) reconciled = false
        }
        restoredReminders.forEach { reminder ->
            val safeReminder = reminder.copy(notificationWorkId = null)
            val workId = if (safeReminder.shouldScheduleNotification()) {
                runCatching { scheduler.schedule(safeReminder) }
                    .onFailure { reconciled = false }
                    .getOrNull()
            } else {
                null
            }
            if (runCatching {
                    reminderDao.update(safeReminder.copy(notificationWorkId = workId))
                }.isFailure
            ) {
                reconciled = false
            }
        }
        return reconciled
    }
}
