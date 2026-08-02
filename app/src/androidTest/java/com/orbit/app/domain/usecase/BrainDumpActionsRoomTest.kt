package com.orbit.app.domain.usecase

import androidx.room.Room
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.local.entity.BrainDumpItemEntity
import com.orbit.app.data.local.entity.BrainDumpItemOutcome
import com.orbit.app.data.local.entity.BrainDumpSessionEntity
import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrainDumpActionsRoomTest {
    private lateinit var database: OrbitDatabase
    private lateinit var actions: BrainDumpActions
    private lateinit var scheduler: RecordingScheduler
    private var persistentDatabaseName: String? = null

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OrbitDatabase::class.java,
        ).build()
        scheduler = RecordingScheduler()
        actions = BrainDumpActions(database, scheduler, now = { 5_000L })
    }

    @After
    fun tearDown() {
        database.close()
        persistentDatabaseName?.let {
            ApplicationProvider.getApplicationContext<Context>().deleteDatabase(it)
        }
    }

    @Test
    fun confirmedItemsAreExactlyOnceAndLastItemCompletesSource() = runBlocking {
        val captureId = seedSession(calendarEpochDay = 21_000L)

        val first = actions.saveNote(captureId, "brain:1", "First", null)
        val repeated = actions.saveNote(captureId, "brain:1", "First again", null)

        assertEquals(BrainDumpActionStatus.Applied, first.status)
        assertEquals(BrainDumpActionStatus.AlreadyHandled, repeated.status)
        assertEquals(1, database.noteDao().observeAll().first().size)
        assertEquals(21_000L, database.noteDao().observeAll().first().single().scheduledDateEpochDay)
        assertNotNull(database.brainDumpDao().getSession(captureId))

        val last = actions.saveTask(captureId, "brain:2", "Second", null, null)

        assertEquals(true, last.sessionCompleted)
        assertEquals(1, database.taskDao().observeAll().first().size)
        assertEquals(21_000L, database.taskDao().observeAll().first().single().scheduledDateEpochDay)
        assertNull(database.brainDumpDao().getSession(captureId))
        assertEquals(CaptureStatus.Processed, database.captureDao().getById(captureId)?.status)
    }

    @Test
    fun failedFinalItemCreationLeavesProgressPendingAndCreatesNothing() = runBlocking {
        val captureId = seedSession(itemCount = 1)

        runCatching { actions.saveNote(captureId, "brain:1", "First", 999L) }

        assertEquals(0, database.noteDao().observeAll().first().size)
        assertEquals(
            BrainDumpItemOutcome.Pending,
            database.brainDumpDao().getItem(captureId, "brain:1")?.outcome,
        )
        assertEquals(CaptureStatus.Inbox, database.captureDao().getById(captureId)?.status)
    }

    @Test
    fun saveOriginalForLaterSkipAndReminderOutcomesAreExactlyOnce() = runBlocking {
        val inboxCaptureId = seedSession()
        val firstInbox = actions.saveOriginalLineForLater(
            inboxCaptureId,
            "brain:1",
        )
        val repeatedInbox = actions.saveOriginalLineForLater(
            inboxCaptureId,
            "brain:1",
        )
        assertEquals(BrainDumpActionStatus.Applied, firstInbox.status)
        assertEquals(BrainDumpActionStatus.AlreadyHandled, repeatedInbox.status)
        assertEquals(
            1,
            database.captureDao().observeAll().first().count { it.rawText == "first" },
        )
        val deferred = database.captureDao().observeAll().first().single { it.rawText == "first" }
        assertEquals(CaptureStatus.Inbox, deferred.status)
        assertEquals(null, deferred.suggestedType)
        assertEquals(null, deferred.suggestedSpaceId)

        val skippedCaptureId = seedSession()
        val firstSkip = actions.skip(skippedCaptureId, "brain:1")
        val repeatedSkip = actions.skip(skippedCaptureId, "brain:1")
        assertEquals(BrainDumpActionStatus.Applied, firstSkip.status)
        assertEquals(BrainDumpActionStatus.AlreadyHandled, repeatedSkip.status)
        assertEquals(
            BrainDumpItemOutcome.Skipped,
            database.brainDumpDao().getItem(skippedCaptureId, "brain:1")?.outcome,
        )

        val reminderCaptureId = seedSession()
        val firstReminder = actions.saveReminder(
            reminderCaptureId,
            "brain:1",
            "Check locally",
            60_000L,
            null,
        )
        val repeatedReminder = actions.saveReminder(
            reminderCaptureId,
            "brain:1",
            "Duplicate reminder",
            60_000L,
            null,
        )
        assertEquals(BrainDumpActionStatus.Applied, firstReminder.status)
        assertEquals(BrainDumpActionStatus.AlreadyHandled, repeatedReminder.status)
        assertEquals(1, database.reminderDao().observeAll().first().size)
        assertEquals(1, scheduler.scheduledIds.size)
    }

    @Test
    fun concurrentConfirmationCreatesOneItem() = runBlocking {
        val captureId = seedSession()

        val results = coroutineScope {
            listOf(
                async { actions.saveNote(captureId, "brain:1", "First", null) },
                async { actions.saveNote(captureId, "brain:1", "Second", null) },
            ).awaitAll()
        }

        assertEquals(1, results.count { it.status == BrainDumpActionStatus.Applied })
        assertEquals(1, results.count { it.status == BrainDumpActionStatus.AlreadyHandled })
        assertEquals(1, database.noteDao().observeAll().first().size)
    }

    @Test
    fun cancellingArchivesSourceAndRemovesResumableSession() = runBlocking {
        val captureId = seedSession()
        actions.saveNote(captureId, "brain:1", "Saved before cancellation", null)

        actions.dismissCapture(captureId, archive = true)

        assertEquals(CaptureStatus.Archived, database.captureDao().getById(captureId)?.status)
        assertNull(database.brainDumpDao().getSession(captureId))
        assertEquals(1, database.noteDao().observeAll().first().size)
    }

    @Test
    fun handledOutcomeSurvivesDatabaseReopenWithoutDuplication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "brain-dump-actions-restart"
        database.close()
        context.deleteDatabase(name)
        persistentDatabaseName = name
        database = Room.databaseBuilder(context, OrbitDatabase::class.java, name).build()
        actions = BrainDumpActions(database, scheduler, now = { 5_000L })
        val captureId = seedSession()

        assertEquals(
            BrainDumpActionStatus.Applied,
            actions.saveNote(captureId, "brain:1", "First", null).status,
        )
        database.close()
        database = Room.databaseBuilder(context, OrbitDatabase::class.java, name).build()
        actions = BrainDumpActions(database, scheduler, now = { 6_000L })

        val repeated = actions.saveNote(captureId, "brain:1", "Duplicate", null)

        assertEquals(BrainDumpActionStatus.AlreadyHandled, repeated.status)
        assertEquals(1, database.noteDao().observeAll().first().size)
        assertTrue(database.brainDumpDao().getSession(captureId) != null)
    }

    private suspend fun seedSession(
        itemCount: Int = 2,
        calendarEpochDay: Long? = null,
    ): Long {
        val captureId = database.captureDao().insert(CaptureEntity(rawText = "first\nsecond"))
        database.brainDumpDao().insertSessionWithItems(
            BrainDumpSessionEntity(
                captureId = captureId,
                analyzerSource = "Local",
                calendarDateContextEpochDay = calendarEpochDay,
            ),
            (1..itemCount).map { ordinal ->
                BrainDumpItemEntity(
                    captureId = captureId,
                    sourceKey = "brain:$ordinal",
                    ordinal = ordinal,
                    rawText = if (ordinal == 1) "first" else "second",
                    suggestedTitle = if (ordinal == 1) "First" else "Second",
                    suggestedType = SuggestedItemType.Note,
                    suggestedSpaceName = "Inbox",
                    confidence = 0.7f,
                    tinyNextAction = "Choose",
                    reason = "Safe local suggestion",
                )
            },
        )
        return captureId
    }

    private class RecordingScheduler : ReminderScheduler {
        val scheduledIds = mutableListOf<Long>()

        override fun schedule(reminder: com.orbit.app.data.local.entity.ReminderEntity): String {
            scheduledIds += reminder.id
            return "scheduled-${reminder.id}"
        }

        override fun cancel(reminderId: Long) = Unit
    }
}
