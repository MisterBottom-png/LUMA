package com.orbit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orbit.app.data.local.dao.AiCorrectionHistoryDao
import com.orbit.app.data.local.dao.AiSuggestionHistoryDao
import com.orbit.app.data.local.dao.BrainDumpDao
import com.orbit.app.data.local.dao.CaptureDao
import com.orbit.app.data.local.dao.CaptureSuggestionDao
import com.orbit.app.data.local.dao.LabelDao
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
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureSuggestionEntity
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.PersonMemoryEntity
import com.orbit.app.data.local.entity.ProjectMemoryEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.ReminderLabelCrossRef
import com.orbit.app.data.local.entity.SpaceAliasMemoryEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskLabelCrossRef

@Database(
    entities = [
        CaptureEntity::class,
        SpaceEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        AiSuggestionHistoryEntity::class,
        AiCorrectionHistoryEntity::class,
        LearnedRuleEntity::class,
        PersonMemoryEntity::class,
        ProjectMemoryEntity::class,
        SpaceAliasMemoryEntity::class,
        BrainDumpSessionEntity::class,
        BrainDumpItemEntity::class,
        LabelEntity::class,
        NoteLabelCrossRef::class,
        TaskLabelCrossRef::class,
        ReminderLabelCrossRef::class,
        CaptureSuggestionEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
@TypeConverters(OrbitTypeConverters::class)
abstract class OrbitDatabase : RoomDatabase() {
    abstract fun captureDao(): CaptureDao
    abstract fun spaceDao(): SpaceDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun aiSuggestionHistoryDao(): AiSuggestionHistoryDao
    abstract fun aiCorrectionHistoryDao(): AiCorrectionHistoryDao
    abstract fun learnedRuleDao(): LearnedRuleDao
    abstract fun personMemoryDao(): PersonMemoryDao
    abstract fun projectMemoryDao(): ProjectMemoryDao
    abstract fun spaceAliasMemoryDao(): SpaceAliasMemoryDao
    abstract fun brainDumpDao(): BrainDumpDao
    abstract fun labelDao(): LabelDao
    abstract fun captureSuggestionDao(): CaptureSuggestionDao

    companion object {
        private const val DATABASE_NAME = "orbit.db"

        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_suggestion_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `surface` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `analyzerSource` TEXT NOT NULL,
                        `captureId` INTEGER,
                        `sourceItemType` TEXT,
                        `sourceItemId` INTEGER,
                        `suggestedType` TEXT,
                        `suggestedSpaceId` INTEGER,
                        `suggestedSpaceName` TEXT,
                        `suggestedTitle` TEXT,
                        `suggestedAction` TEXT,
                        `confidence` REAL,
                        `sourceTextSnippet` TEXT,
                        `userAction` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`captureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_suggestion_history_captureId` " +
                        "ON `ai_suggestion_history` (`captureId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_suggestion_history_outcome` " +
                        "ON `ai_suggestion_history` (`outcome`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_suggestion_history_createdAt` " +
                        "ON `ai_suggestion_history` (`createdAt`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ai_correction_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `suggestionHistoryId` INTEGER,
                        `fieldName` TEXT NOT NULL,
                        `originalValue` TEXT,
                        `correctedValue` TEXT NOT NULL,
                        `correctionReason` TEXT,
                        `sourceTextSnippet` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        FOREIGN KEY(`suggestionHistoryId`) REFERENCES `ai_suggestion_history`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_correction_history_suggestionHistoryId` " +
                        "ON `ai_correction_history` (`suggestionHistoryId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ai_correction_history_createdAt` " +
                        "ON `ai_correction_history` (`createdAt`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `learned_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `title` TEXT NOT NULL,
                        `ruleText` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `strength` REAL NOT NULL,
                        `sourceSuggestionHistoryId` INTEGER,
                        `sourceCorrectionHistoryId` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`sourceSuggestionHistoryId`) REFERENCES `ai_suggestion_history`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL,
                        FOREIGN KEY(`sourceCorrectionHistoryId`) REFERENCES `ai_correction_history`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learned_rules_category` ON `learned_rules` (`category`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_learned_rules_enabled` ON `learned_rules` (`enabled`)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_learned_rules_sourceSuggestionHistoryId` " +
                        "ON `learned_rules` (`sourceSuggestionHistoryId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_learned_rules_sourceCorrectionHistoryId` " +
                        "ON `learned_rules` (`sourceCorrectionHistoryId`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `person_memory` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `notes` TEXT,
                        `aliases` TEXT,
                        `enabled` INTEGER NOT NULL,
                        `strength` REAL NOT NULL,
                        `sourceSuggestionHistoryId` INTEGER,
                        `sourceTextSnippet` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_person_memory_displayName` " +
                        "ON `person_memory` (`displayName`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_person_memory_enabled` ON `person_memory` (`enabled`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `project_memory` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `notes` TEXT,
                        `enabled` INTEGER NOT NULL,
                        `strength` REAL NOT NULL,
                        `sourceSuggestionHistoryId` INTEGER,
                        `sourceTextSnippet` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_project_memory_name` " +
                        "ON `project_memory` (`name`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_memory_enabled` ON `project_memory` (`enabled`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `space_alias_memory` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `spaceId` INTEGER NOT NULL,
                        `alias` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `strength` REAL NOT NULL,
                        `sourceSuggestionHistoryId` INTEGER,
                        `sourceTextSnippet` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`spaceId`) REFERENCES `spaces`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_space_alias_memory_spaceId` ON `space_alias_memory` (`spaceId`)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_space_alias_memory_alias` " +
                        "ON `space_alias_memory` (`alias`)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_space_alias_memory_enabled` ON `space_alias_memory` (`enabled`)")
            }
        }

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `reminders` ADD COLUMN `notificationOffsetMinutes` " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `scheduledDateEpochDay` INTEGER")
                db.execSQL("ALTER TABLE `notes` ADD COLUMN `scheduledAt` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_scheduledDateEpochDay` " +
                        "ON `notes` (`scheduledDateEpochDay`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_notes_scheduledAt` " +
                        "ON `notes` (`scheduledAt`)",
                )
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `scheduledDateEpochDay` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_scheduledDateEpochDay` " +
                        "ON `tasks` (`scheduledDateEpochDay`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminders_dueAt` ON `reminders` (`dueAt`)",
                )
            }
        }

        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `brain_dump_sessions` (
                        `captureId` INTEGER NOT NULL,
                        `analyzerSource` TEXT NOT NULL,
                        `calendarDateContextEpochDay` INTEGER,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`captureId`),
                        FOREIGN KEY(`captureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_brain_dump_sessions_captureId` " +
                        "ON `brain_dump_sessions` (`captureId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `brain_dump_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `captureId` INTEGER NOT NULL,
                        `sourceKey` TEXT NOT NULL,
                        `ordinal` INTEGER NOT NULL,
                        `rawText` TEXT NOT NULL,
                        `suggestedTitle` TEXT NOT NULL,
                        `suggestedType` TEXT NOT NULL,
                        `suggestedSpaceName` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `tinyNextAction` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `reminderStatus` TEXT NOT NULL,
                        `suggestedReminderAt` INTEGER,
                        `reminderPhrase` TEXT,
                        `outcome` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`captureId`) REFERENCES `brain_dump_sessions`(`captureId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_brain_dump_items_captureId` " +
                        "ON `brain_dump_items` (`captureId`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_brain_dump_items_captureId_sourceKey` " +
                        "ON `brain_dump_items` (`captureId`, `sourceKey`)",
                )
            }
        }

        val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `labels` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `normalizedName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_labels_normalizedName` " +
                        "ON `labels` (`normalizedName`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_labels` (
                        `noteId` INTEGER NOT NULL,
                        `labelId` INTEGER NOT NULL,
                        PRIMARY KEY(`noteId`, `labelId`),
                        FOREIGN KEY(`noteId`) REFERENCES `notes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_note_labels_labelId` " +
                        "ON `note_labels` (`labelId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `task_labels` (
                        `taskId` INTEGER NOT NULL,
                        `labelId` INTEGER NOT NULL,
                        PRIMARY KEY(`taskId`, `labelId`),
                        FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_task_labels_labelId` " +
                        "ON `task_labels` (`labelId`)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminder_labels` (
                        `reminderId` INTEGER NOT NULL,
                        `labelId` INTEGER NOT NULL,
                        PRIMARY KEY(`reminderId`, `labelId`),
                        FOREIGN KEY(`reminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`labelId`) REFERENCES `labels`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_reminder_labels_labelId` " +
                        "ON `reminder_labels` (`labelId`)",
                )
            }
        }

        /**
         * Adds device-local reminder delivery state. Reminders whose notification
         * time has already passed are marked as delivered so that an upgrade never
         * rings an old reminder a second time.
         */
        val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `deliveredNotificationAt` INTEGER")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `snoozedUntil` INTEGER")
                db.execSQL(
                    """
                    UPDATE `reminders`
                    SET `deliveredNotificationAt` = `dueAt` - (`notificationOffsetMinutes` * 60000)
                    WHERE `dueAt` - (`notificationOffsetMinutes` * 60000) <= (CAST(strftime('%s','now') AS INTEGER) * 1000)
                    """.trimIndent(),
                )
            }
        }

        /**
         * Stores LUMA's suggestion next to each unresolved capture so sorting can
         * happen later in Review (To sort) instead of right after saving.
         */
        val Migration7To8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capture_suggestions` (
                        `captureId` INTEGER NOT NULL,
                        `suggestedType` TEXT NOT NULL,
                        `suggestedTitle` TEXT NOT NULL,
                        `suggestedSpaceName` TEXT,
                        `suggestedLabels` TEXT NOT NULL,
                        `suggestedDueAt` INTEGER,
                        `suggestedReminderAt` INTEGER,
                        `reminderTimeStatus` TEXT NOT NULL,
                        `reminderPhrase` TEXT,
                        `lifeSignal` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `analyzerSource` TEXT NOT NULL,
                        `typeReason` TEXT NOT NULL,
                        `spaceReason` TEXT NOT NULL,
                        `nextAction` TEXT NOT NULL,
                        `contextDateEpochDay` INTEGER,
                        `dismissed` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`captureId`),
                        FOREIGN KEY(`captureId`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
            }
        }

        val AllMigrations: Array<Migration> = arrayOf(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To8,
        )

        @Volatile
        private var instance: OrbitDatabase? = null

        fun getInstance(context: Context): OrbitDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                OrbitDatabase::class.java,
                DATABASE_NAME,
            )
                .addMigrations(*AllMigrations)
                .build()
                .also { instance = it }
        }
    }
}
