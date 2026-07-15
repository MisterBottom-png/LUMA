package com.orbit.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrbitDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrbitDatabase::class.java,
    )

    @Test
    fun migrate2To3PreservesReminderAndDefaultsOffsetToTargetTime() {
        helper.createDatabase(TEST_DATABASE_2_TO_3, 2).apply {
            execSQL(
                """
                INSERT INTO reminders (
                    id, title, notes, dueAt, spaceId, linkedTaskId, linkedCaptureId,
                    notificationEnabled, notificationWorkId, createdAt, updatedAt, completedAt
                ) VALUES (1, 'Existing reminder', '', 3600000, NULL, NULL, NULL, 1, NULL, 1000, 1000, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_2_TO_3,
            3,
            true,
            OrbitDatabase.Migration2To3,
        )
        val cursor = migrated.query(
            "SELECT title, dueAt, notificationOffsetMinutes FROM reminders WHERE id = 1",
        )

        cursor.use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Existing reminder", it.getString(0))
            assertEquals(3_600_000L, it.getLong(1))
            assertEquals(0L, it.getLong(2))
        }
        migrated.close()
    }

    @Test
    fun migrate3To4PreservesExistingSchedulesAndAddsExplicitDateOnlyFields() {
        helper.createDatabase(TEST_DATABASE_3_TO_4, 3).apply {
            execSQL(
                """
                INSERT INTO notes (
                    id, title, body, spaceId, createdAt, updatedAt, archived
                ) VALUES (1, 'Existing note', '', NULL, 1000, 1000, 0)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO tasks (
                    id, title, notes, spaceId, status, dueAt, reminderAt,
                    createdAt, updatedAt, completedAt, staleAfterDays, mondayItemId
                ) VALUES (2, 'Existing task', '', NULL, 'Open', 7200000, NULL, 1000, 1000, NULL, NULL, NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_3_TO_4,
            4,
            true,
            OrbitDatabase.Migration3To4,
        )

        migrated.query(
            "SELECT scheduledDateEpochDay, scheduledAt FROM notes WHERE id = 1",
        ).use {
            assertEquals(true, it.moveToFirst())
            assertEquals(true, it.isNull(0))
            assertEquals(true, it.isNull(1))
        }
        migrated.query(
            "SELECT dueAt, scheduledDateEpochDay FROM tasks WHERE id = 2",
        ).use {
            assertEquals(true, it.moveToFirst())
            assertEquals(7_200_000L, it.getLong(0))
            assertEquals(true, it.isNull(1))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE_2_TO_3 = "orbit-migration-2-to-3"
        const val TEST_DATABASE_3_TO_4 = "orbit-migration-3-to-4"
    }
}
