package com.orbit.app.testing

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.reminders.ReminderDeliveryPolicy
import com.orbit.app.reminders.ReminderScheduler

/** In-memory Room database for Robolectric JVM tests. */
fun inMemoryOrbitDatabase(): OrbitDatabase = Room
    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), OrbitDatabase::class.java)
    .allowMainThreadQueries()
    .build()

/**
 * Scheduler double that applies the real [ReminderDeliveryPolicy] (as the production
 * scheduler does) and records what would have been armed.
 */
class PolicyRecordingScheduler(private val now: () -> Long) : ReminderScheduler {
    val scheduled = mutableListOf<Pair<Long, Long>>()
    val cancelled = mutableListOf<Long>()
    private var counter = 0

    override fun schedule(reminder: ReminderEntity): String? {
        val time = ReminderDeliveryPolicy.scheduleTime(reminder, now()) ?: return null
        scheduled += reminder.id to time
        counter += 1
        return "token-$counter"
    }

    override fun cancel(reminderId: Long) {
        cancelled += reminderId
    }
}
