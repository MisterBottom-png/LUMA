package com.orbit.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.repository.RoomReminderRepository
import kotlinx.coroutines.flow.first

class ReminderRescheduleWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val database = OrbitDatabase.getInstance(applicationContext)
        val repository = RoomReminderRepository(
            dao = database.reminderDao(),
            scheduler = WorkManagerReminderScheduler(applicationContext),
        )
        val now = System.currentTimeMillis()
        val futureReminders = repository.observeAll()
            .first()
            .filter { it.shouldScheduleNotification() && it.dueAt > now }

        futureReminders.forEach { reminder ->
            repository.update(reminder.copy(notificationWorkId = null, updatedAt = now))
        }
        return Result.success()
    }
}
