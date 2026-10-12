package com.orbit.app.ui.screens.item

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.LabelEntity
import com.orbit.app.data.local.entity.NoteEntity
import com.orbit.app.data.local.entity.NoteLabelCrossRef
import com.orbit.app.data.local.entity.ReminderEntity
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.testing.inMemoryOrbitDatabase
import com.orbit.app.ui.navigation.ItemDetailType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Changing an item's type keeps its labels. */
@RunWith(AndroidJUnit4::class)
class ItemTypeConversionLabelsTest {
    private lateinit var database: OrbitDatabase
    private lateinit var conversion: ItemTypeConversion

    @Before
    fun setUp() {
        database = inMemoryOrbitDatabase()
        conversion = ItemTypeConversion(database, NoOpScheduler, now = { 900L })
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun labelsFollowTheItemThroughNoteTaskReminderAndBack() = runBlocking {
        val labels = database.labelDao()
        val home = labels.insert(LabelEntity(name = "Home", normalizedName = "home"))
        val errand = labels.insert(LabelEntity(name = "Errand", normalizedName = "errand"))
        database.noteDao().insert(NoteEntity(id = 7, title = "Buy bulbs", body = ""))
        labels.insertNoteLabels(listOf(NoteLabelCrossRef(7, home), NoteLabelCrossRef(7, errand)))
        val expected = setOf(home, errand)

        val taskId = (conversion.convert(ItemDetailType.Note, 7, ItemDetailType.Task) as TypeConversionOutcome.Converted).itemId
        assertEquals(expected, labels.getLabelIdsForTask(taskId).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForNote(7))

        val reminderId = (
            conversion.convert(ItemDetailType.Task, taskId, ItemDetailType.Reminder, reminderDueAt = 5_000)
                as TypeConversionOutcome.Converted
            ).itemId
        assertEquals(expected, labels.getLabelIdsForReminder(reminderId).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForTask(taskId))

        val noteId = (conversion.convert(ItemDetailType.Reminder, reminderId, ItemDetailType.Note) as TypeConversionOutcome.Converted).itemId
        assertEquals(expected, labels.getLabelIdsForNote(noteId).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForReminder(reminderId))
    }

    @Test
    fun aTaskWithTheSameIdDoesNotBlockTheChange() = runBlocking {
        // F07: ids are per table; note 7 and task 7 are different items.
        database.noteDao().insert(NoteEntity(id = 7, title = "Buy bulbs", body = ""))
        database.taskDao().insert(com.orbit.app.data.local.entity.TaskEntity(id = 7, title = "Someone else's task"))

        val outcome = conversion.convert(ItemDetailType.Note, 7, ItemDetailType.Task)

        val newId = (outcome as TypeConversionOutcome.Converted).itemId
        assertEquals("Buy bulbs", database.taskDao().getById(newId)?.title)
        assertEquals("Someone else's task", database.taskDao().getById(7)?.title)
        assertEquals(null, database.noteDao().getById(7))
    }

    @Test
    fun theThoughtFollowsAReminderThatBecomesATask() = runBlocking {
        // N008: the thought no longer says "Reminder set" for an item that is gone.
        val captureId = database.captureDao().insert(
            com.orbit.app.data.local.entity.CaptureEntity(
                rawText = "Remind me to buy bulbs",
                status = com.orbit.app.data.local.entity.CaptureStatus.Processed,
                suggestedType = com.orbit.app.data.local.entity.SuggestedItemType.Reminder,
                outcome = com.orbit.app.data.local.entity.CaptureOutcome.ReminderSet,
            ),
        )
        val reminderId = database.reminderDao().insert(ReminderEntity(title = "Buy bulbs", dueAt = 5_000, linkedCaptureId = captureId))
        database.captureDao().update(requireNotNull(database.captureDao().getById(captureId)).copy(linkedItemId = reminderId))

        val taskId = (conversion.convert(ItemDetailType.Reminder, reminderId, ItemDetailType.Task) as TypeConversionOutcome.Converted).itemId

        val capture = requireNotNull(database.captureDao().getById(captureId))
        assertEquals(taskId, capture.linkedItemId)
        assertEquals(com.orbit.app.data.local.entity.SuggestedItemType.Task, capture.suggestedType)
        assertEquals(com.orbit.app.data.local.entity.CaptureOutcome.Filed, capture.outcome)
    }

    @Test
    fun anUnsupportedConversionLeavesLabelsWhereTheyWere() = runBlocking {
        val labels = database.labelDao()
        val home = labels.insert(LabelEntity(name = "Home", normalizedName = "home"))
        database.noteDao().insert(NoteEntity(id = 8, title = "Keep", body = ""))
        labels.insertNoteLabels(listOf(NoteLabelCrossRef(8, home)))

        assertEquals(
            TypeConversionOutcome.Unsupported,
            conversion.convert(ItemDetailType.Note, 8, ItemDetailType.Reminder, reminderDueAt = null),
        )
        assertEquals(listOf(home), labels.getLabelIdsForNote(8))
    }
}

private object NoOpScheduler : ReminderScheduler {
    override fun schedule(reminder: ReminderEntity): String = "work-${reminder.id}"
    override fun cancel(reminderId: Long) = Unit
}
