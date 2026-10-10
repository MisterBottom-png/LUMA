package com.orbit.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.SchemaDatabases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the full Room migration chain on the JVM (Robolectric) for every supported
 * starting version. Room validates the migrated schema against the current
 * entities when the database opens, so a wrong migration fails here.
 */
@RunWith(AndroidJUnit4::class)
class OrbitDatabaseMigrationJvmTest {
    @Test
    fun everySupportedVersionMigratesToTheCurrentSchemaWithoutLosingCaptures() = runBlocking {
        (1 until CurrentVersion).forEach { start ->
            val name = "jvm-migration-$start.db"
            SchemaDatabases.createAtVersion(name, start) { db ->
                db.execSQL(
                    "INSERT INTO captures (id, rawText, createdAt, updatedAt, status, suggestedType, " +
                        "suggestedSpaceId, source, linkedItemId) VALUES " +
                        "($start, 'Version $start thought', 1, 1, 'Inbox', NULL, NULL, 'Manual', NULL)",
                )
            }
            val database = SchemaDatabases.openMigrated(name)
            val capture = database.captureDao().getById(start.toLong())
            assertEquals("Version $start thought", capture?.rawText)
            assertEquals(CurrentVersion, database.openHelper.readableDatabase.version)
            database.close()
        }
    }

    @Test
    fun migrate6To7MarksOnlyPastRemindersAsDeliveredAndKeepsLabels() = runBlocking {
        val name = "jvm-migration-6-7.db"
        val future = System.currentTimeMillis() + 3L * 24L * 60L * 60_000L
        SchemaDatabases.createAtVersion(name, 6) { db ->
            db.execSQL(
                """
                INSERT INTO reminders (id, title, notes, dueAt, notificationOffsetMinutes, spaceId,
                    linkedTaskId, linkedCaptureId, notificationEnabled, notificationWorkId,
                    createdAt, updatedAt, completedAt)
                VALUES
                    (1, 'Past', '', 1000000, 0, NULL, NULL, NULL, 1, 'old', 1, 7, NULL),
                    (2, 'Future', '', $future, 10, NULL, NULL, NULL, 1, 'new', 1, 8, NULL)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO labels (id, name, normalizedName, createdAt, updatedAt) VALUES (1, 'Errands', 'errands', 1, 1)")
            db.execSQL("INSERT INTO reminder_labels (reminderId, labelId) VALUES (2, 1)")
        }

        val database = SchemaDatabases.openMigrated(name)
        val past = requireNotNull(database.reminderDao().getById(1))
        val upcoming = requireNotNull(database.reminderDao().getById(2))
        assertEquals(1_000_000L, past.deliveredNotificationAt)
        assertEquals(7L, past.updatedAt)
        assertNull("a future reminder must stay deliverable", upcoming.deliveredNotificationAt)
        assertNull(upcoming.snoozedUntil)
        assertEquals(8L, upcoming.updatedAt)
        assertEquals(1, database.labelDao().getAllReminderLabels().size)
        assertTrue(database.labelDao().getAll().any { it.name == "Errands" })
        database.close()
    }

    private companion object {
        const val CurrentVersion = 9
    }

    @Test
    fun migrate7To8AddsCaptureSuggestionsWithoutTouchingCaptures() = runBlocking {
        val name = "jvm-migration-7-8.db"
        SchemaDatabases.createAtVersion(name, 7) { db ->
            db.execSQL(
                "INSERT INTO captures (id, rawText, createdAt, updatedAt, status, suggestedType, " +
                    "suggestedSpaceId, source, linkedItemId) VALUES (5, 'call the bank', 1, 1, 'Inbox', 'Task', NULL, 'Manual', NULL)",
            )
        }
        val database = SchemaDatabases.openMigrated(name)
        assertEquals("call the bank", database.captureDao().getById(5)?.rawText)
        assertTrue(database.captureSuggestionDao().getAll().isEmpty())
        database.close()
    }

    @Test
    fun migrate8To9KeepsEveryReminderAndMakesThemOneOff() = runBlocking {
        val name = "jvm-migration-8-9.db"
        val future = System.currentTimeMillis() + 86_400_000L
        SchemaDatabases.createAtVersion(name, 8) { db ->
            db.execSQL(
                """
                INSERT INTO reminders (id, title, notes, dueAt, notificationOffsetMinutes, spaceId,
                    linkedTaskId, linkedCaptureId, notificationEnabled, notificationWorkId,
                    createdAt, updatedAt, completedAt, deliveredNotificationAt, snoozedUntil)
                VALUES (3, 'Water plants', 'balcony', $future, 15, NULL, NULL, NULL, 1, 'w', 1, 9, NULL, NULL, NULL)
                """.trimIndent(),
            )
        }
        val database = SchemaDatabases.openMigrated(name)
        val reminder = requireNotNull(database.reminderDao().getById(3))
        assertEquals("Water plants", reminder.title)
        assertEquals(future, reminder.dueAt)
        assertEquals(15L, reminder.notificationOffsetMinutes)
        assertEquals(9L, reminder.updatedAt)
        assertNull(reminder.repeatRule)
        database.close()
    }
}
