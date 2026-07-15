package com.orbit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CaptureStatus { Inbox, Processed, Archived }

enum class CaptureSource { Manual, Voice, Monday, Calendar }

enum class SuggestedItemType { Note, Task, Reminder, MondayItem }

enum class TaskStatus { Open, Done, Archived, WaitingFor, Someday }

enum class AiSuggestionOutcome { Accepted, Rejected, Corrected }

enum class AiSuggestionSurface { Capture, BrainDump, Review, Situation, AskLuma, ItemDetail }

enum class LearnedRuleCategory { Type, Space, Person, Project, Alias, Tone, Other }

@Entity(tableName = "spaces")
data class SpaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val colorAccent: String,
    val sortOrder: Int,
    val hidden: Boolean = false,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestedSpaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("suggestedSpaceId")],
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val status: CaptureStatus = CaptureStatus.Inbox,
    val suggestedType: SuggestedItemType? = null,
    val suggestedSpaceId: Long? = null,
    val source: CaptureSource = CaptureSource.Manual,
    val linkedItemId: Long? = null,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("spaceId"), Index("scheduledDateEpochDay"), Index("scheduledAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val spaceId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val archived: Boolean = false,
    val scheduledDateEpochDay: Long? = null,
    val scheduledAt: Long? = null,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("spaceId"), Index("scheduledDateEpochDay"), Index("dueAt")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val spaceId: Long? = null,
    val status: TaskStatus = TaskStatus.Open,
    val dueAt: Long? = null,
    val reminderAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
    val staleAfterDays: Int? = null,
    val mondayItemId: String? = null,
    val scheduledDateEpochDay: Long? = null,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedTaskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedCaptureId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("spaceId"), Index("linkedTaskId"), Index("linkedCaptureId"), Index("dueAt")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val dueAt: Long,
    val notificationOffsetMinutes: Long = 0L,
    val spaceId: Long? = null,
    val linkedTaskId: Long? = null,
    val linkedCaptureId: Long? = null,
    val notificationEnabled: Boolean = true,
    val notificationWorkId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val completedAt: Long? = null,
)

@Entity(
    tableName = "ai_suggestion_history",
    foreignKeys = [
        ForeignKey(
            entity = CaptureEntity::class,
            parentColumns = ["id"],
            childColumns = ["captureId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("captureId"), Index("outcome"), Index("createdAt")],
)
data class AiSuggestionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val surface: AiSuggestionSurface,
    val outcome: AiSuggestionOutcome,
    val analyzerSource: String,
    val captureId: Long? = null,
    val sourceItemType: String? = null,
    val sourceItemId: Long? = null,
    val suggestedType: SuggestedItemType? = null,
    val suggestedSpaceId: Long? = null,
    val suggestedSpaceName: String? = null,
    val suggestedTitle: String? = null,
    val suggestedAction: String? = null,
    val confidence: Float? = null,
    val sourceTextSnippet: String? = null,
    val userAction: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "ai_correction_history",
    foreignKeys = [
        ForeignKey(
            entity = AiSuggestionHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["suggestionHistoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("suggestionHistoryId"), Index("createdAt")],
)
data class AiCorrectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val suggestionHistoryId: Long? = null,
    val fieldName: String,
    val originalValue: String? = null,
    val correctedValue: String,
    val correctionReason: String? = null,
    val sourceTextSnippet: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "learned_rules",
    foreignKeys = [
        ForeignKey(
            entity = AiSuggestionHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceSuggestionHistoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = AiCorrectionHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceCorrectionHistoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("category"),
        Index("enabled"),
        Index("sourceSuggestionHistoryId"),
        Index("sourceCorrectionHistoryId"),
    ],
)
data class LearnedRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val ruleText: String,
    val category: LearnedRuleCategory = LearnedRuleCategory.Other,
    val enabled: Boolean = true,
    val strength: Float = 0.5f,
    val sourceSuggestionHistoryId: Long? = null,
    val sourceCorrectionHistoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "person_memory",
    indices = [Index(value = ["displayName"], unique = true), Index("enabled")],
)
data class PersonMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val notes: String? = null,
    val aliases: String? = null,
    val enabled: Boolean = true,
    val strength: Float = 0.5f,
    val sourceSuggestionHistoryId: Long? = null,
    val sourceTextSnippet: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "project_memory",
    indices = [Index(value = ["name"], unique = true), Index("enabled")],
)
data class ProjectMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notes: String? = null,
    val enabled: Boolean = true,
    val strength: Float = 0.5f,
    val sourceSuggestionHistoryId: Long? = null,
    val sourceTextSnippet: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "space_alias_memory",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("spaceId"), Index(value = ["alias"], unique = true), Index("enabled")],
)
data class SpaceAliasMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spaceId: Long,
    val alias: String,
    val enabled: Boolean = true,
    val strength: Float = 0.5f,
    val sourceSuggestionHistoryId: Long? = null,
    val sourceTextSnippet: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)
