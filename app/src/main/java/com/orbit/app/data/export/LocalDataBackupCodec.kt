package com.orbit.app.data.export

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureSource
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.reminders.reminderNotificationTimeMillis
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.DateTimeException
import java.time.LocalDate

data class LocalDataSnapshot(
    val spaces: List<SpaceEntity>,
    val captures: List<CaptureEntity>,
    val notes: List<NoteEntity>,
    val tasks: List<TaskEntity>,
    val reminders: List<ReminderEntity>,
)

data class LocalDataCounts(
    val spaces: Int,
    val captures: Int,
    val notes: Int,
    val tasks: Int,
    val reminders: Int,
) {
    val visibleItems: Int = notes + tasks + reminders
}

fun LocalDataSnapshot.counts() = LocalDataCounts(
    spaces = spaces.size,
    captures = captures.size,
    notes = notes.size,
    tasks = tasks.size,
    reminders = reminders.size,
)

class LocalDataValidationException(message: String) : IllegalArgumentException(message)

object LocalDataBackupCodec {
    const val Product = "LUMA"
    const val Format = "luma-local-json"
    const val Version = 2

    fun encode(snapshot: LocalDataSnapshot, exportedAt: Long): String {
        require(exportedAt >= 0L)
        return JSONObject()
            .put(
                "metadata",
                JSONObject()
                    .put("product", Product)
                    .put("format", Format)
                    .put("version", Version)
                    .put("exportedAt", exportedAt),
            )
            .put("spaces", snapshot.spaces.toJsonArray { it.toJson() })
            .put("captures", snapshot.captures.toJsonArray { it.toJson() })
            .put("notes", snapshot.notes.toJsonArray { it.toJson() })
            .put("tasks", snapshot.tasks.toJsonArray { it.toJson() })
            .put("reminders", snapshot.reminders.toJsonArray { it.toJson() })
            .toString(2)
    }

    fun decode(json: String): LocalDataSnapshot {
        if (json.isBlank()) invalid("The selected file is empty.")
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            invalid("The selected file is not valid JSON.")
        }
        val metadata = root.requiredObject("metadata")
        if (metadata.requiredString("product") != Product ||
            metadata.requiredString("format") != Format
        ) {
            invalid("This file is not a supported LUMA export.")
        }
        val version = metadata.requiredLong("version")
        if (version !in 1L..Version.toLong()) {
            invalid("Export version $version is not supported.")
        }
        metadata.requiredNonNegativeLong("exportedAt")

        val snapshot = LocalDataSnapshot(
            spaces = root.requiredArray("spaces").mapObjects("spaces", ::decodeSpace),
            captures = root.requiredArray("captures").mapObjects("captures", ::decodeCapture),
            notes = root.requiredArray("notes").mapObjects("notes", ::decodeNote),
            tasks = root.requiredArray("tasks").mapObjects("tasks", ::decodeTask),
            reminders = root.requiredArray("reminders").mapObjects("reminders", ::decodeReminder),
        )
        validateRelationships(snapshot)
        return snapshot
    }

    private fun decodeSpace(json: JSONObject) = SpaceEntity(
        id = json.requiredPositiveId(),
        name = json.requiredNonBlankString("name"),
        icon = json.requiredString("icon"),
        colorAccent = json.requiredString("colorAccent"),
        sortOrder = json.requiredInt("sortOrder"),
        hidden = json.requiredBoolean("hidden"),
        archived = json.requiredBoolean("archived"),
        createdAt = json.requiredNonNegativeLong("createdAt"),
        updatedAt = json.requiredNonNegativeLong("updatedAt"),
    )

    private fun decodeCapture(json: JSONObject) = CaptureEntity(
        id = json.requiredPositiveId(),
        rawText = json.requiredNonBlankString("rawText"),
        createdAt = json.requiredNonNegativeLong("createdAt"),
        updatedAt = json.requiredNonNegativeLong("updatedAt"),
        status = json.requiredEnum("status", CaptureStatus.entries),
        suggestedType = json.optionalEnum("suggestedType", SuggestedItemType.entries),
        suggestedSpaceId = json.optionalPositiveId("suggestedSpaceId"),
        source = json.requiredEnum("source", CaptureSource.entries),
        linkedItemId = json.optionalPositiveId("linkedItemId"),
    )

    private fun decodeNote(json: JSONObject) = NoteEntity(
        id = json.requiredPositiveId(),
        title = json.requiredNonBlankString("title"),
        body = json.requiredString("body"),
        spaceId = json.optionalPositiveId("spaceId"),
        createdAt = json.requiredNonNegativeLong("createdAt"),
        updatedAt = json.requiredNonNegativeLong("updatedAt"),
        archived = json.requiredBoolean("archived"),
        scheduledDateEpochDay = json.optionalLong("scheduledDateEpochDay"),
        scheduledAt = json.optionalNonNegativeLong("scheduledAt"),
    )

    private fun decodeTask(json: JSONObject) = TaskEntity(
        id = json.requiredPositiveId(),
        title = json.requiredNonBlankString("title"),
        notes = json.requiredString("notes"),
        spaceId = json.optionalPositiveId("spaceId"),
        status = json.requiredEnum("status", TaskStatus.entries),
        dueAt = json.optionalNonNegativeLong("dueAt"),
        reminderAt = json.optionalNonNegativeLong("reminderAt"),
        createdAt = json.requiredNonNegativeLong("createdAt"),
        updatedAt = json.requiredNonNegativeLong("updatedAt"),
        completedAt = json.optionalNonNegativeLong("completedAt"),
        staleAfterDays = json.optionalNonNegativeInt("staleAfterDays"),
        mondayItemId = json.optionalString("mondayItemId"),
        scheduledDateEpochDay = json.optionalLong("scheduledDateEpochDay"),
    )

    private fun decodeReminder(json: JSONObject): ReminderEntity {
        val dueAt = json.requiredLong("dueAt")
        if (dueAt <= 0L) invalid("Reminder dueAt must be a positive timestamp.")
        val offset = json.optionalNonNegativeLong("notificationOffsetMinutes") ?: 0L
        if (reminderNotificationTimeMillis(dueAt, offset) == null) {
            invalid("Reminder notification timing is invalid.")
        }
        return ReminderEntity(
            id = json.requiredPositiveId(),
            title = json.requiredNonBlankString("title"),
            notes = json.requiredString("notes"),
            dueAt = dueAt,
            notificationOffsetMinutes = offset,
            spaceId = json.optionalPositiveId("spaceId"),
            linkedTaskId = json.optionalPositiveId("linkedTaskId"),
            linkedCaptureId = json.optionalPositiveId("linkedCaptureId"),
            notificationEnabled = json.requiredBoolean("notificationEnabled"),
            notificationWorkId = null,
            createdAt = json.requiredNonNegativeLong("createdAt"),
            updatedAt = json.requiredNonNegativeLong("updatedAt"),
            completedAt = json.optionalNonNegativeLong("completedAt"),
        )
    }

    private fun validateRelationships(snapshot: LocalDataSnapshot) {
        validateUniqueIds("spaces", snapshot.spaces.map { it.id })
        validateUniqueIds("captures", snapshot.captures.map { it.id })
        validateUniqueIds("notes", snapshot.notes.map { it.id })
        validateUniqueIds("tasks", snapshot.tasks.map { it.id })
        validateUniqueIds("reminders", snapshot.reminders.map { it.id })

        val spaceIds = snapshot.spaces.mapTo(hashSetOf()) { it.id }
        val captureIds = snapshot.captures.mapTo(hashSetOf()) { it.id }
        val noteIds = snapshot.notes.mapTo(hashSetOf()) { it.id }
        val taskIds = snapshot.tasks.mapTo(hashSetOf()) { it.id }
        val reminderIds = snapshot.reminders.mapTo(hashSetOf()) { it.id }
        snapshot.captures.forEach { capture ->
            requireReference("capture suggestedSpaceId", capture.suggestedSpaceId, spaceIds)
            capture.linkedItemId?.let { linkedId ->
                if (linkedId !in noteIds && linkedId !in taskIds && linkedId !in reminderIds) {
                    invalid("A capture links to an item that is not present in the export.")
                }
            }
        }
        snapshot.notes.forEach { requireReference("note spaceId", it.spaceId, spaceIds) }
        snapshot.notes.forEach { note ->
            validateSchedule("note", note.id, note.scheduledDateEpochDay, note.scheduledAt)
        }
        snapshot.tasks.forEach { task ->
            requireReference("task spaceId", task.spaceId, spaceIds)
            validateSchedule("task", task.id, task.scheduledDateEpochDay, task.dueAt)
        }
        snapshot.reminders.forEach { reminder ->
            requireReference("reminder spaceId", reminder.spaceId, spaceIds)
            requireReference("reminder linkedTaskId", reminder.linkedTaskId, taskIds)
            requireReference("reminder linkedCaptureId", reminder.linkedCaptureId, captureIds)
        }
    }

    private fun validateUniqueIds(label: String, ids: List<Long>) {
        if (ids.toSet().size != ids.size) invalid("The export contains duplicate $label identifiers.")
    }

    private fun requireReference(label: String, id: Long?, validIds: Set<Long>) {
        if (id != null && id !in validIds) invalid("The export contains an invalid $label relationship.")
    }

    private fun validateSchedule(
        itemType: String,
        itemId: Long,
        scheduledDateEpochDay: Long?,
        scheduledAt: Long?,
    ) {
        if (scheduledDateEpochDay != null && scheduledAt != null) {
            invalid("A restored $itemType cannot be both date-only and timed.")
        }
        if (scheduledDateEpochDay != null) {
            try {
                LocalDate.ofEpochDay(scheduledDateEpochDay)
            } catch (_: DateTimeException) {
                invalid("A restored $itemType has an invalid scheduled date for item $itemId.")
            }
        }
    }

    private fun SpaceEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("icon", icon)
        .put("colorAccent", colorAccent).put("sortOrder", sortOrder)
        .put("hidden", hidden).put("archived", archived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun CaptureEntity.toJson() = JSONObject()
        .put("id", id).put("rawText", rawText)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("status", status.name).put("suggestedType", suggestedType?.name)
        .put("suggestedSpaceId", suggestedSpaceId).put("source", source.name)
        .put("linkedItemId", linkedItemId)

    private fun NoteEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("body", body).put("spaceId", spaceId)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("archived", archived)
        .put("scheduledDateEpochDay", scheduledDateEpochDay).put("scheduledAt", scheduledAt)

    private fun TaskEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("notes", notes).put("spaceId", spaceId)
        .put("status", status.name).put("dueAt", dueAt).put("reminderAt", reminderAt)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)
        .put("completedAt", completedAt).put("staleAfterDays", staleAfterDays)
        .put("mondayItemId", mondayItemId).put("scheduledDateEpochDay", scheduledDateEpochDay)

    private fun ReminderEntity.toJson() = JSONObject()
        .put("id", id).put("title", title).put("notes", notes).put("dueAt", dueAt)
        .put("notificationOffsetMinutes", notificationOffsetMinutes).put("spaceId", spaceId)
        .put("linkedTaskId", linkedTaskId).put("linkedCaptureId", linkedCaptureId)
        .put("notificationEnabled", notificationEnabled).put("notificationWorkId", notificationWorkId)
        .put("createdAt", createdAt).put("updatedAt", updatedAt).put("completedAt", completedAt)

    private fun <T> List<T>.toJsonArray(transform: (T) -> JSONObject): JSONArray =
        JSONArray().also { array -> forEach { array.put(transform(it)) } }

    private fun JSONObject.requiredObject(name: String): JSONObject =
        value(name) as? JSONObject ?: invalid("Required object '$name' is missing or invalid.")

    private fun JSONObject.requiredArray(name: String): JSONArray =
        value(name) as? JSONArray ?: invalid("Required array '$name' is missing or invalid.")

    private fun JSONObject.requiredString(name: String): String =
        value(name) as? String ?: invalid("Required text field '$name' is missing or invalid.")

    private fun JSONObject.requiredNonBlankString(name: String): String =
        requiredString(name).takeIf { it.isNotBlank() }
            ?: invalid("Required text field '$name' cannot be blank.")

    private fun JSONObject.optionalString(name: String): String? = when (val value = value(name)) {
        null, JSONObject.NULL -> null
        is String -> value
        else -> invalid("Optional text field '$name' is invalid.")
    }

    private fun JSONObject.requiredLong(name: String): Long =
        strictLong(value(name), name)

    private fun JSONObject.optionalLong(name: String): Long? = when (val value = value(name)) {
        null, JSONObject.NULL -> null
        else -> strictLong(value, name)
    }

    private fun JSONObject.requiredNonNegativeLong(name: String): Long =
        requiredLong(name).takeIf { it >= 0L }
            ?: invalid("Timestamp '$name' cannot be negative.")

    private fun JSONObject.optionalNonNegativeLong(name: String): Long? = when (val value = value(name)) {
        null, JSONObject.NULL -> null
        else -> strictLong(value, name).takeIf { it >= 0L }
            ?: invalid("Value '$name' cannot be negative.")
    }

    private fun JSONObject.optionalPositiveId(name: String): Long? =
        optionalNonNegativeLong(name)?.takeIf { it > 0L }
            ?: if (value(name) == null || value(name) == JSONObject.NULL) null
            else invalid("Identifier '$name' must be positive.")

    private fun JSONObject.requiredPositiveId(): Long =
        requiredLong("id").takeIf { it > 0L }
            ?: invalid("Every restored identifier must be positive.")

    private fun JSONObject.requiredInt(name: String): Int {
        val value = requiredLong(name)
        if (value !in Int.MIN_VALUE..Int.MAX_VALUE) invalid("Integer '$name' is out of range.")
        return value.toInt()
    }

    private fun JSONObject.optionalNonNegativeInt(name: String): Int? =
        optionalNonNegativeLong(name)?.let {
            if (it > Int.MAX_VALUE) invalid("Integer '$name' is out of range.")
            it.toInt()
        }

    private fun JSONObject.requiredBoolean(name: String): Boolean =
        value(name) as? Boolean ?: invalid("Required boolean '$name' is missing or invalid.")

    private fun <T : Enum<T>> JSONObject.requiredEnum(name: String, entries: List<T>): T {
        val raw = requiredString(name)
        return entries.firstOrNull { it.name == raw }
            ?: invalid("Value '$name' is not supported.")
    }

    private fun <T : Enum<T>> JSONObject.optionalEnum(name: String, entries: List<T>): T? {
        val raw = optionalString(name) ?: return null
        return entries.firstOrNull { it.name == raw }
            ?: invalid("Value '$name' is not supported.")
    }

    private fun JSONObject.value(name: String): Any? =
        if (has(name)) opt(name) else null

    private fun strictLong(value: Any?, name: String): Long {
        val number = value as? Number
            ?: invalid("Required number '$name' is missing or invalid.")
        return try {
            java.math.BigDecimal(number.toString()).longValueExact()
        } catch (_: ArithmeticException) {
            invalid("Number '$name' must be a whole value within range.")
        } catch (_: NumberFormatException) {
            invalid("Number '$name' is invalid.")
        }
    }

    private fun <T> JSONArray.mapObjects(
        label: String,
        transform: (JSONObject) -> T,
    ): List<T> = buildList {
        if (length() > MaxEntriesPerType) invalid("The '$label' array is too large.")
        for (index in 0 until length()) {
            val item = opt(index) as? JSONObject
                ?: invalid("Entry ${index + 1} in '$label' is invalid.")
            add(transform(item))
        }
    }

    private fun invalid(message: String): Nothing = throw LocalDataValidationException(message)

    private const val MaxEntriesPerType = 100_000
}
