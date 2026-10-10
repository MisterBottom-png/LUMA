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

        assertEquals(TypeConversionOutcome.Converted, conversion.convert(ItemDetailType.Note, 7, ItemDetailType.Task))
        assertEquals(expected, labels.getLabelIdsForTask(7).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForNote(7))

        assertEquals(
            TypeConversionOutcome.Converted,
            conversion.convert(ItemDetailType.Task, 7, ItemDetailType.Reminder, reminderDueAt = 5_000),
        )
        assertEquals(expected, labels.getLabelIdsForReminder(7).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForTask(7))

        assertEquals(TypeConversionOutcome.Converted, conversion.convert(ItemDetailType.Reminder, 7, ItemDetailType.Note))
        assertEquals(expected, labels.getLabelIdsForNote(7).toSet())
        assertEquals(emptyList<Long>(), labels.getLabelIdsForReminder(7))
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
