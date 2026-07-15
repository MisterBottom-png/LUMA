package com.orbit.app.data.export

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskEntity
import com.orbit.app.data.local.entity.TaskStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataRestoreTest {
    @Test
    fun validExportRoundTripPreservesSupportedDataAndRelationships() {
        val original = completeSnapshot()

        val decoded = LocalDataBackupCodec.decode(
            LocalDataBackupCodec.encode(original, exportedAt = 900L),
        )

        assertEquals(original.spaces, decoded.spaces)
        assertEquals(original.captures, decoded.captures)
        assertEquals(original.notes, decoded.notes)
        assertEquals(original.tasks, decoded.tasks)
        assertEquals(setOf(TaskStatus.Done, TaskStatus.Archived), decoded.tasks.map { it.status }.toSet())
        assertTrue(decoded.notes.single().archived)
        assertEquals(
            original.reminders.map { it.copy(notificationWorkId = null) },
            decoded.reminders,
        )
        assertEquals(45L, decoded.reminders.single().notificationOffsetMinutes)
        assertEquals(2L, decoded.reminders.single().linkedTaskId)
        assertEquals(3L, decoded.reminders.single().linkedCaptureId)
    }

    @Test
    fun emptyExportIsSupported() {
        val decoded = LocalDataBackupCodec.decode(
            LocalDataBackupCodec.encode(emptySnapshot(), exportedAt = 1L),
        )

        assertEquals(LocalDataCounts(0, 0, 0, 0, 0), decoded.counts())
    }

    @Test
    fun malformedJsonFailsBeforeReadingExistingData() = runBlocking {
        val store = FakeRestoreStore(completeSnapshot())
        val restorer = restorer(store)

        assertThrows(LocalDataValidationException::class.java) {
            runBlocking { restorer.prepare("not json") }
        }
        assertEquals(0, store.readCount)
        assertEquals(0, store.replaceCount)
    }

    @Test
    fun incompleteRequiredDataFails() {
        val incomplete = JSONObject()
            .put(
                "metadata",
                JSONObject()
                    .put("product", LocalDataBackupCodec.Product)
                    .put("format", LocalDataBackupCodec.Format)
                    .put("version", LocalDataBackupCodec.Version)
                    .put("exportedAt", 1),
            )
            .put("spaces", org.json.JSONArray())
            .toString()

        assertThrows(LocalDataValidationException::class.java) {
            LocalDataBackupCodec.decode(incomplete)
        }
    }

    @Test
    fun unsupportedVersionFails() {
        val root = JSONObject(
            LocalDataBackupCodec.encode(emptySnapshot(), exportedAt = 1L),
        )
        root.getJSONObject("metadata").put("version", 99)

        val exception = assertThrows(LocalDataValidationException::class.java) {
            LocalDataBackupCodec.decode(root.toString())
        }
        assertTrue(exception.message.orEmpty().contains("not supported"))
    }

    @Test
    fun cancelledSelectionMakesNoChanges() = runBlocking {
        val existing = completeSnapshot()
        val store = FakeRestoreStore(existing)

        val plan = restorer(store).prepare(null)

        assertNull(plan)
        assertEquals(existing, store.snapshot)
        assertEquals(0, store.readCount)
        assertEquals(0, store.replaceCount)
    }

    @Test
    fun repeatedReplacementDoesNotCreateDuplicates() = runBlocking {
        val target = completeSnapshot()
        val json = LocalDataBackupCodec.encode(target, exportedAt = 10L)
        val store = FakeRestoreStore(emptySnapshot())
        val restorer = restorer(store)

        restorer.restore(requireNotNull(restorer.prepare(json)))
        restorer.restore(requireNotNull(restorer.prepare(json)))

        assertEquals(target.spaces.size, store.snapshot.spaces.size)
        assertEquals(target.notes.size, store.snapshot.notes.size)
        assertEquals(target.tasks.size, store.snapshot.tasks.size)
        assertEquals(target.reminders.size, store.snapshot.reminders.size)
        assertEquals(target.tasks.map { it.id }.toSet().size, store.snapshot.tasks.size)
    }

    @Test
    fun preparingRestoreDoesNotOverwriteExistingData() = runBlocking {
        val existing = completeSnapshot()
        val store = FakeRestoreStore(existing)

        val plan = restorer(store).prepare(
            LocalDataBackupCodec.encode(emptySnapshot(), exportedAt = 1L),
        )

        assertTrue(plan != null)
        assertEquals(existing, store.snapshot)
        assertEquals(0, store.replaceCount)
    }

    @Test
    fun dataChangedAfterSummaryBlocksReplacement() = runBlocking {
        val existing = completeSnapshot()
        val store = FakeRestoreStore(existing)
        val restorer = restorer(store)
        val plan = requireNotNull(
            restorer.prepare(LocalDataBackupCodec.encode(emptySnapshot(), exportedAt = 1L)),
        )
        store.snapshot = existing.copy(notes = existing.notes + NoteEntity(id = 8, title = "New", body = "New"))

        assertThrows(LocalDataRestoreException::class.java) {
            runBlocking { restorer.restore(plan) }
        }
        assertEquals(0, store.replaceCount)
        assertEquals(2, store.snapshot.notes.size)
    }

    @Test
    fun transactionalFailureKeepsExistingDataAndSkipsReminderReconciliation() = runBlocking {
        val existing = completeSnapshot()
        val store = FakeRestoreStore(existing, failReplacement = true)
        val reconciler = RecordingReconciler()
        val restorer = LocalDataRestorer(store, reconciler)
        val plan = requireNotNull(
            restorer.prepare(LocalDataBackupCodec.encode(emptySnapshot(), exportedAt = 1L)),
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking { restorer.restore(plan) }
        }
        assertEquals(existing, store.snapshot)
        assertFalse(reconciler.called)
    }

    @Test
    fun missingRelationshipFailsValidation() {
        val root = JSONObject(
            LocalDataBackupCodec.encode(completeSnapshot(), exportedAt = 1L),
        )
        root.getJSONArray("notes").getJSONObject(0).put("spaceId", 999)

        assertThrows(LocalDataValidationException::class.java) {
            LocalDataBackupCodec.decode(root.toString())
        }
    }

    @Test
    fun duplicateIdentifiersFailValidation() {
        val root = JSONObject(
            LocalDataBackupCodec.encode(completeSnapshot(), exportedAt = 1L),
        )
        val notes = root.getJSONArray("notes")
        notes.put(JSONObject(notes.getJSONObject(0).toString()))

        assertThrows(LocalDataValidationException::class.java) {
            LocalDataBackupCodec.decode(root.toString())
        }
    }

    @Test
    fun sourceCapturesStaySeparateFromVisibleItemCounts() {
        val snapshot = completeSnapshot()

        assertEquals(4, snapshot.counts().visibleItems)
        assertEquals(1, snapshot.counts().captures)
        assertEquals(CaptureStatus.Processed, snapshot.captures.single().status)
    }

    @Test
    fun versionOneExportDefaultsNewScheduleFieldsAndReminderOffsetSafely() {
        val root = JSONObject(
            LocalDataBackupCodec.encode(completeSnapshot(), exportedAt = 1L),
        )
        root.getJSONObject("metadata").put("version", 1)
        root.getJSONArray("notes").getJSONObject(0)
            .remove("scheduledDateEpochDay")
        root.getJSONArray("notes").getJSONObject(0)
            .remove("scheduledAt")
        root.getJSONArray("tasks").getJSONObject(0)
            .remove("scheduledDateEpochDay")
        root.getJSONArray("reminders").getJSONObject(0).remove("notificationOffsetMinutes")

        val decoded = LocalDataBackupCodec.decode(root.toString())

        assertNull(decoded.notes.single().scheduledDateEpochDay)
        assertNull(decoded.notes.single().scheduledAt)
        assertNull(decoded.tasks.first().scheduledDateEpochDay)
        assertEquals(0L, decoded.reminders.single().notificationOffsetMinutes)
        assertEquals(5_000_000L, decoded.reminders.single().dueAt)
    }

    @Test
    fun conflictingDateOnlyAndTimedScheduleFailsValidation() {
        val root = JSONObject(
            LocalDataBackupCodec.encode(completeSnapshot(), exportedAt = 1L),
        )
        root.getJSONArray("notes").getJSONObject(0)
            .put("scheduledDateEpochDay", 20_000)
            .put("scheduledAt", 5_000_000)

        assertThrows(LocalDataValidationException::class.java) {
            LocalDataBackupCodec.decode(root.toString())
        }
    }

    @Test
    fun successfulRestoreReconcilesPreviousAndRestoredReminderIds() = runBlocking {
        val existing = completeSnapshot().copy(
            reminders = listOf(completeSnapshot().reminders.single().copy(id = 9)),
        )
        val target = completeSnapshot()
        val store = FakeRestoreStore(existing)
        val reconciler = RecordingReconciler()
        val restorer = LocalDataRestorer(store, reconciler)

        val result = restorer.restore(
            requireNotNull(
                restorer.prepare(LocalDataBackupCodec.encode(target, exportedAt = 1L)),
            ),
        )

        assertEquals(setOf(9L), reconciler.previousReminderIds)
        assertEquals(listOf(5L), reconciler.restoredReminderIds)
        assertTrue(result.remindersReconciled)
    }

    private fun restorer(store: FakeRestoreStore) =
        LocalDataRestorer(store, RecordingReconciler())
}

private class FakeRestoreStore(
    var snapshot: LocalDataSnapshot,
    private val failReplacement: Boolean = false,
) : LocalDataRestoreStore {
    var readCount = 0
    var replaceCount = 0

    override suspend fun read(): LocalDataSnapshot {
        readCount += 1
        return snapshot
    }

    override suspend fun replace(snapshot: LocalDataSnapshot) {
        replaceCount += 1
        if (failReplacement) throw IllegalStateException("transaction failed")
        this.snapshot = snapshot
    }
}

private class RecordingReconciler : ReminderRestoreReconciler {
    var called = false
    var previousReminderIds: Set<Long> = emptySet()
    var restoredReminderIds: List<Long> = emptyList()

    override suspend fun reconcile(
        previousReminderIds: Set<Long>,
        restoredReminders: List<ReminderEntity>,
    ): Boolean {
        called = true
        this.previousReminderIds = previousReminderIds
        this.restoredReminderIds = restoredReminders.map { it.id }
        return true
    }
}

private fun emptySnapshot() = LocalDataSnapshot(
    spaces = emptyList(),
    captures = emptyList(),
    notes = emptyList(),
    tasks = emptyList(),
    reminders = emptyList(),
)

private fun completeSnapshot(): LocalDataSnapshot {
    val space = SpaceEntity(
        id = 1,
        name = "Personal",
        icon = "home",
        colorAccent = "violet",
        sortOrder = 0,
        hidden = true,
        archived = false,
        createdAt = 100,
        updatedAt = 200,
    )
    val task = TaskEntity(
        id = 2,
        title = "Prepare supplies",
        notes = "Checklist",
        spaceId = space.id,
        status = TaskStatus.Done,
        dueAt = 4_000_000,
        reminderAt = 3_900_000,
        createdAt = 300,
        updatedAt = 500,
        completedAt = 500,
        staleAfterDays = 6,
    )
    val capture = CaptureEntity(
        id = 3,
        rawText = "Raw source material",
        createdAt = 250,
        updatedAt = 400,
        status = CaptureStatus.Processed,
        suggestedSpaceId = space.id,
        linkedItemId = task.id,
    )
    return LocalDataSnapshot(
        spaces = listOf(space),
        captures = listOf(capture),
        notes = listOf(
            NoteEntity(
                id = 4,
                title = "Reference",
                body = "Details",
                spaceId = space.id,
                createdAt = 350,
                updatedAt = 450,
                archived = true,
                scheduledDateEpochDay = 20_000,
            ),
        ),
        tasks = listOf(
            task,
            TaskEntity(
                id = 6,
                title = "Archived task",
                spaceId = space.id,
                status = TaskStatus.Archived,
                createdAt = 310,
                updatedAt = 510,
            ),
        ),
        reminders = listOf(
            ReminderEntity(
                id = 5,
                title = "Check supplies",
                notes = "Before leaving",
                dueAt = 5_000_000,
                notificationOffsetMinutes = 45,
                spaceId = space.id,
                linkedTaskId = task.id,
                linkedCaptureId = capture.id,
                notificationEnabled = true,
                notificationWorkId = "device-specific-work",
                createdAt = 600,
                updatedAt = 700,
                completedAt = 800,
            ),
        ),
    )
}
