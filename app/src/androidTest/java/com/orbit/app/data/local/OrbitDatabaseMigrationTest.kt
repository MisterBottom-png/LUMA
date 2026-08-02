package com.orbit.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.room.migration.Migration
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
    fun migrate1To2PreservesCaptureAndCreatesLearningTables() {
        helper.createDatabase(TEST_DATABASE_1_TO_2, 1).apply {
            insertCapture(id = 1, rawText = "Source material")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_1_TO_2,
            2,
            true,
            OrbitDatabase.Migration1To2,
        )

        migrated.query("SELECT rawText, status FROM captures WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("Source material", it.getString(0))
            assertEquals("Inbox", it.getString(1))
        }
        migrated.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'learned_rules'",
        ).use {
            assertEquals(true, it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun everySupportedStartingVersionMigratesToFiveWithoutCaptureLoss() {
        (1..4).forEach { startVersion ->
            val databaseName = "orbit-migration-$startVersion-to-5"
            helper.createDatabase(databaseName, startVersion).apply {
                insertCapture(id = startVersion.toLong(), rawText = "Version $startVersion source")
                close()
            }

            val migrated = helper.runMigrationsAndValidate(
                databaseName,
                5,
                true,
                *migrationsFrom(startVersion),
            )
            migrated.query(
                "SELECT rawText, status FROM captures WHERE id = $startVersion",
            ).use {
                assertEquals(true, it.moveToFirst())
                assertEquals("Version $startVersion source", it.getString(0))
                assertEquals("Inbox", it.getString(1))
            }
            migrated.close()
        }
    }

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

    @Test
    fun migrate4To5PreservesCapturesAndCreatesBrainDumpProgressTables() {
        helper.createDatabase(TEST_DATABASE_4_TO_5, 4).apply {
            execSQL(
                """
                INSERT INTO captures (
                    id, rawText, createdAt, updatedAt, status, suggestedType,
                    suggestedSpaceId, source, linkedItemId
                ) VALUES (1, 'first and second', 1000, 1000, 'Inbox', NULL, NULL, 'Manual', NULL)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_4_TO_5,
            5,
            true,
            OrbitDatabase.Migration4To5,
        )
        migrated.query("SELECT rawText, status FROM captures WHERE id = 1").use {
            assertEquals(true, it.moveToFirst())
            assertEquals("first and second", it.getString(0))
            assertEquals("Inbox", it.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM brain_dump_sessions").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE_1_TO_2 = "orbit-migration-1-to-2"
        const val TEST_DATABASE_2_TO_3 = "orbit-migration-2-to-3"
        const val TEST_DATABASE_3_TO_4 = "orbit-migration-3-to-4"
        const val TEST_DATABASE_4_TO_5 = "orbit-migration-4-to-5"

        fun androidx.sqlite.db.SupportSQLiteDatabase.insertCapture(id: Long, rawText: String) {
            execSQL(
                """
                INSERT INTO captures (
                    id, rawText, createdAt, updatedAt, status, suggestedType,
                    suggestedSpaceId, source, linkedItemId
                ) VALUES (?, ?, 1000, 1000, 'Inbox', NULL, NULL, 'Manual', NULL)
                """.trimIndent(),
                arrayOf<Any>(id, rawText),
            )
        }

        fun migrationsFrom(startVersion: Int): Array<Migration> = when (startVersion) {
            1 -> arrayOf(
                OrbitDatabase.Migration1To2,
                OrbitDatabase.Migration2To3,
                OrbitDatabase.Migration3To4,
                OrbitDatabase.Migration4To5,
            )
            2 -> arrayOf(
                OrbitDatabase.Migration2To3,
                OrbitDatabase.Migration3To4,
                OrbitDatabase.Migration4To5,
            )
            3 -> arrayOf(OrbitDatabase.Migration3To4, OrbitDatabase.Migration4To5)
            4 -> arrayOf(OrbitDatabase.Migration4To5)
            else -> error("Unsupported start version")
        }
    }
}
