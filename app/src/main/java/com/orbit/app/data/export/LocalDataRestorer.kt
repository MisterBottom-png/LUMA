package com.orbit.app.data.export

import androidx.room.withTransaction
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.dao.ReminderDao
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.reminders.ReminderDeliveryPolicy
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.notificationTimeMillis
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

fun interface LocalDataSnapshotReader {
    suspend fun read(): LocalDataSnapshot
}

interface LocalDataRestoreStore : LocalDataSnapshotReader {
    override suspend fun read(): LocalDataSnapshot
    suspend fun replace(snapshot: LocalDataSnapshot)

    /**
     * F09: check and replace in one step, so nothing can change in between. Returns the
     * data that was replaced, or null when it no longer matches [expected].
     */
    suspend fun replaceIfUnchanged(expected: LocalDataSnapshot, snapshot: LocalDataSnapshot): LocalDataSnapshot? {
        val current = read()
        if (!current.sameForTheUser(expected)) return null
        replace(snapshot)
        return current
    }
}

/**
 * N032: what the user could have changed. Delivery bookkeeping (a reminder ringing, a
 * repeat moving on, a thought's outcome following its reminder) does not block a restore.
 */
internal fun LocalDataSnapshot.sameForTheUser(other: LocalDataSnapshot): Boolean =
    userView() == other.userView()

private fun LocalDataSnapshot.userView(): LocalDataSnapshot = copy(
    reminders = reminders.map {
        it.copy(
            deliveredNotificationAt = null,
            notificationWorkId = null,
            snoozedUntil = null,
            dueAt = if (it.repeatRule != null) 0L else it.dueAt,
        )
    },
    captures = captures.map {
        it.copy(outcome = com.orbit.app.data.local.entity.CaptureOutcome.Saved,
            outcomeReason = com.orbit.app.data.local.entity.CaptureOutcomeReason.None, outcomeAt = null)
    },
)

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
        val current = store.replaceIfUnchanged(plan.existingSnapshot, plan.restoredSnapshot)
            ?: throw LocalDataRestoreException(
                "Local data changed after the restore summary was prepared. Select the export again.",
            )
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
    override suspend fun read() = database.withTransaction {
        val brainDumpSessions = database.brainDumpDao().getSessions()
        LocalDataSnapshot(
            spaces = database.spaceDao().observeAll().first(),
            captures = database.captureDao().observeAll().first(),
            notes = database.noteDao().observeAll().first(),
            tasks = database.taskDao().observeAll().first(),
            reminders = database.reminderDao().observeAll().first(),
            brainDumpSessions = brainDumpSessions,
            brainDumpItems = brainDumpSessions.flatMap { session ->
                database.brainDumpDao().getItems(session.captureId)
            },
            labels = database.labelDao().getAll(),
            noteLabels = database.labelDao().getAllNoteLabels(),
            taskLabels = database.labelDao().getAllTaskLabels(),
            reminderLabels = database.labelDao().getAllReminderLabels(),
            captureSuggestions = database.captureSuggestionDao().getAll(),
            learnedRules = database.learnedRuleDao().observeAll().first(),
        )
    }

    override suspend fun replaceIfUnchanged(expected: LocalDataSnapshot, snapshot: LocalDataSnapshot): LocalDataSnapshot? =
        database.withTransaction {
            val current = read()
            if (!current.sameForTheUser(expected)) return@withTransaction null
            replace(snapshot)
            current
        }

    override suspend fun replace(snapshot: LocalDataSnapshot) {
        database.withTransaction {
            val aliases = database.spaceAliasMemoryDao().observeAll().first()
            val suggestionHistory = database.aiSuggestionHistoryDao().observeAll().first()
            // N007: device-only learning is re-linked only to the same thought (same text
            // and time) or a Space with the same name, never to whatever reuses an id.
            val oldCaptures = database.captureDao().observeAll().first().associateBy { it.id }
            val oldSpaceNames = database.spaceDao().observeAll().first().associate { it.id to it.name.trim().lowercase() }

            database.captureSuggestionDao().deleteAll()
            database.labelDao().deleteAllNoteLabels()
            database.labelDao().deleteAllTaskLabels()
            database.labelDao().deleteAllReminderLabels()
            database.reminderDao().deleteAll()
            database.brainDumpDao().deleteAllItems()
            database.brainDumpDao().deleteAllSessions()
            database.noteDao().deleteAll()
            database.taskDao().deleteAll()
            database.captureDao().deleteAll()
            database.labelDao().deleteAllLabels()
            database.spaceDao().deleteAll()

            database.spaceDao().insertAll(snapshot.spaces)
            database.captureDao().insertAll(snapshot.captures)
            database.brainDumpDao().insertSessions(snapshot.brainDumpSessions)
            database.brainDumpDao().insertItems(snapshot.brainDumpItems)
            database.noteDao().insertAll(snapshot.notes)
            database.taskDao().insertAll(snapshot.tasks)
            database.reminderDao().insertAll(snapshot.reminders)
            database.labelDao().insertAll(snapshot.labels)
            database.labelDao().insertNoteLabels(snapshot.noteLabels)
            database.labelDao().insertTaskLabels(snapshot.taskLabels)
            database.labelDao().insertReminderLabels(snapshot.reminderLabels)
            database.captureSuggestionDao().insertAll(snapshot.captureSuggestions)

            val restoredSpaceByName = snapshot.spaces.associateBy { it.name.trim().lowercase() }
            aliases.forEach { alias ->
                val space = oldSpaceNames[alias.spaceId]?.let(restoredSpaceByName::get) ?: return@forEach
                database.spaceAliasMemoryDao().insert(alias.copy(spaceId = space.id))
            }
            // Learned rules are merged, never replaced: what this phone learned stays,
            // and rules from the backup are added unless the same rule is already here.
            val knownRules = database.learnedRuleDao().observeAll().first()
                .mapTo(hashSetOf()) { it.mergeKey() }
            snapshot.learnedRules.forEach { rule ->
                if (knownRules.add(rule.mergeKey())) {
                    database.learnedRuleDao().insert(rule.portableForBackup())
                }
            }
            val restoredCaptures = snapshot.captures.associateBy { it.id }
            suggestionHistory.filter { history ->
                val before = history.captureId?.let(oldCaptures::get)
                val after = history.captureId?.let(restoredCaptures::get)
                before != null && after != null && before.rawText == after.rawText && before.createdAt == after.createdAt
            }.forEach {
                database.aiSuggestionHistoryDao().update(it)
            }
        }
    }
}

class LocalReminderRestoreReconciler(
    private val scheduler: ReminderScheduler,
    private val reminderDao: ReminderDao,
    private val now: () -> Long = System::currentTimeMillis,
) : ReminderRestoreReconciler {
    /**
     * Cancels alarms of the replaced data and arms only reminders whose notification
     * time is still ahead. Restored reminders in the past are marked handled so a
     * restore can never set off a burst of historical reminders.
     */
    override suspend fun reconcile(
        previousReminderIds: Set<Long>,
        restoredReminders: List<ReminderEntity>,
    ): Boolean {
        var reconciled = true
        (previousReminderIds + restoredReminders.map { it.id }).forEach { reminderId ->
            if (runCatching { scheduler.cancel(reminderId) }.isFailure) reconciled = false
        }
        val currentTime = now()
        restoredReminders.forEach { restored ->
            val reminder = restored.copy(notificationWorkId = null, deliveredNotificationAt = null, snoozedUntil = null)
            val outcome = runCatching {
                val notificationTime = reminder.notificationTimeMillis()
                when {
                    !reminder.shouldScheduleNotification() || notificationTime == null ->
                        reminderDao.updateNotificationWorkId(reminder.id, null)
                    ReminderDeliveryPolicy.scheduleTime(reminder, currentTime) == null -> {
                        // A past one-off never rings late; a past repeat moves on to its
                        // next occurrence instead of staying silent (N044).
                        val next = com.orbit.app.reminders.ReminderRepeats.afterOccurrenceShown(reminder, notificationTime, currentTime)
                        if (next == null) {
                            reminderDao.markHandled(reminder.id, notificationTime)
                        } else {
                            reminderDao.update(next)
                            val token = scheduler.schedule(next)
                            reminderDao.updateNotificationWorkId(next.id, token)
                            if (token == null) reconciled = false
                        }
                    }
                    else -> {
                        val token = scheduler.schedule(reminder)
                        reminderDao.updateNotificationWorkId(reminder.id, token)
                        if (token == null) reconciled = false
                    }
                }
            }
            if (outcome.isFailure) reconciled = false
        }
        return reconciled
    }
}
