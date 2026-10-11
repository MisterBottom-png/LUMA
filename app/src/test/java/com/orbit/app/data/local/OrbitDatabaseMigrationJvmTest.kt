package com.orbit.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.data.local.entity.CaptureOutcomeReason
import com.orbit.app.testing.SchemaDatabases
import kotlinx.coroutines.flow.first
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
            // An old thought still in the Inbox stays a plain saved thought, still undecided.
            assertEquals(CaptureOutcome.Saved, capture?.outcome)
            assertEquals(CaptureOutcomeReason.None, capture?.outcomeReason)
            assertNull(capture?.decidedAt)
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

    @Test
    fun migrate9To10KeepsEveryThoughtWithoutACalendarDay() = runBlocking {
        val name = "jvm-migration-9-10.db"
        SchemaDatabases.createAtVersion(name, 9) { db ->
            db.execSQL(
                "INSERT INTO captures (id, rawText, createdAt, updatedAt, status, suggestedType, " +
                    "suggestedSpaceId, source, linkedItemId) VALUES " +
                    "(4, 'Plan the weekend', 2, 3, 'Inbox', 'Task', NULL, 'Manual', NULL)",
            )
        }
        val database = SchemaDatabases.openMigrated(name)
        val capture = requireNotNull(database.captureDao().getById(4))
        assertEquals("Plan the weekend", capture.rawText)
        assertEquals(3L, capture.updatedAt)
        assertNull(capture.contextDateEpochDay)
        database.captureDao().update(capture.copy(contextDateEpochDay = 20_650))
        assertEquals(20_650L, database.captureDao().getById(4)?.contextDateEpochDay)
        database.close()
    }

    @Test
    fun migrate10To11BackfillsOutcomesFromRealRemindersAndItemsNotFromSuggestedType() = runBlocking {
        val name = "jvm-migration-10-11.db"
        SchemaDatabases.createAtVersion(name, 10) { db ->
            db.execSQL(
                """
                INSERT INTO captures (id, rawText, createdAt, updatedAt, status, suggestedType,
                    suggestedSpaceId, source, linkedItemId, contextDateEpochDay)
                VALUES
                    (1, 'water plants at nine', 1, 11, 'Processed', 'Task', NULL, 'Manual', 100, NULL),
                    (2, 'recipe idea', 2, 12, 'Processed', 'Reminder', NULL, 'Manual', 200, NULL),
                    (3, 'remind me about the car', 3, 13, 'Inbox', 'Reminder', NULL, 'Manual', NULL, NULL),
                    (4, 'pay rent', 4, 14, 'Processed', 'Reminder', NULL, 'Manual', 101, NULL),
                    (5, 'old thought', 5, 15, 'Archived', 'Reminder', NULL, 'Manual', NULL, NULL),
                    (6, 'looked at in review', 6, 16, 'Processed', 'Note', NULL, 'Manual', NULL, NULL),
                    (7, 'unsorted with a stray reminder', 7, 17, 'Inbox', NULL, NULL, 'Manual', NULL, 20650)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO reminders (id, title, notes, dueAt, notificationOffsetMinutes, spaceId,
                    linkedTaskId, linkedCaptureId, notificationEnabled, notificationWorkId,
                    createdAt, updatedAt, completedAt, deliveredNotificationAt, snoozedUntil, repeatRule)
                VALUES
                    (100, 'Water plants', '', 9000000000000, 0, NULL, NULL, 1, 1, NULL, 1, 1, NULL, NULL, NULL, NULL),
                    (101, 'Pay rent', '', 1000, 0, NULL, NULL, 4, 1, NULL, 1, 1, 50, NULL, NULL, NULL),
                    (102, 'Stray', '', 9000000000000, 0, NULL, NULL, 7, 1, NULL, 1, 1, NULL, NULL, NULL, NULL)
                """.trimIndent(),
            )
        }
        val database = SchemaDatabases.openMigrated(name)
        fun capture(id: Long) = requireNotNull(runBlocking { database.captureDao().getById(id) })

        // An open reminder that points back at the thought: a reminder row exists.
        assertEquals(CaptureOutcome.ReminderSet, capture(1).outcome)
        assertEquals(11L, capture(1).outcomeAt)
        assertEquals(11L, capture(1).decidedAt)
        // Sorted into an item, even though its suggested type was Reminder.
        assertEquals(CaptureOutcome.Filed, capture(2).outcome)
        assertEquals(12L, capture(2).decidedAt)
        // A completed reminder is no longer an open reminder: the thought was filed.
        assertEquals(CaptureOutcome.Filed, capture(4).outcome)
        // Everything else stays Saved; nothing becomes NeedsYou on upgrade.
        listOf(3L, 5L, 6L, 7L).forEach { id ->
            assertEquals("capture $id", CaptureOutcome.Saved, capture(id).outcome)
            assertEquals("capture $id", CaptureOutcomeReason.None, capture(id).outcomeReason)
            assertNull("capture $id", capture(id).outcomeAt)
        }
        // Thoughts still in the Inbox are looked at once more on the next start.
        assertNull(capture(3).decidedAt)
        assertNull(capture(7).decidedAt)
        assertEquals(20_650L, capture(7).contextDateEpochDay)
        assertTrue(
            database.captureDao().observeAll().first().none { it.outcome == CaptureOutcome.NeedsYou },
        )
        // Nothing else moved.
        assertEquals(17L, capture(7).updatedAt)
        assertEquals(3, database.reminderDao().getAll().size)
        database.close()
    }

    @Test
    fun theFullChainFromVersionOneBackfillsASortedReminderThought() = runBlocking {
        val name = "jvm-migration-1-11.db"
        SchemaDatabases.createAtVersion(name, 1) { db ->
            db.execSQL(
                "INSERT INTO captures (id, rawText, createdAt, updatedAt, status, suggestedType, " +
                    "suggestedSpaceId, source, linkedItemId) VALUES " +
                    "(1, 'call the dentist', 1, 5, 'Processed', 'Reminder', NULL, 'Manual', 9), " +
                    "(2, 'garden idea', 1, 6, 'Processed', 'Note', NULL, 'Manual', 3), " +
                    "(3, 'not sorted yet', 1, 7, 'Inbox', 'Reminder', NULL, 'Manual', NULL)",
            )
            db.execSQL(
                "INSERT INTO reminders (id, title, notes, dueAt, spaceId, linkedTaskId, linkedCaptureId, " +
                    "notificationEnabled, notificationWorkId, createdAt, updatedAt, completedAt) VALUES " +
                    "(9, 'Dentist', '', 9000000000000, NULL, NULL, 1, 1, NULL, 1, 1, NULL)",
            )
        }
        val database = SchemaDatabases.openMigrated(name)
        assertEquals(CurrentVersion, database.openHelper.readableDatabase.version)
        assertEquals(CaptureOutcome.ReminderSet, database.captureDao().getById(1)?.outcome)
        assertEquals(5L, database.captureDao().getById(1)?.decidedAt)
        assertEquals(CaptureOutcome.Filed, database.captureDao().getById(2)?.outcome)
        val unsorted = requireNotNull(database.captureDao().getById(3))
        assertEquals(CaptureOutcome.Saved, unsorted.outcome)
        assertNull(unsorted.decidedAt)
        database.close()
    }

    private companion object {
        const val CurrentVersion = 11
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
