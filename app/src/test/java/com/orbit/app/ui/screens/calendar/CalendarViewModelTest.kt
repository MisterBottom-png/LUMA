package com.orbit.app.ui.screens.calendar

import androidx.lifecycle.SavedStateHandle
import com.orbit.app.data.repository.CalendarRepository
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import com.orbit.app.ui.navigation.CalendarDestination
import com.orbit.app.data.local.entity.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarViewModelTest {
    private val today = LocalDate.of(2026, 7, 14)

    @Test
    fun requestedDate_initializesSelectionAndVisibleMonth() {
        val requested = LocalDate.of(2026, 10, 5)
        val viewModel = viewModel(requested.toEpochDay().toString())

        assertEquals(requested, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 10), viewModel.uiState.value.visibleMonth)
        assertEquals(CalendarViewMode.Day, viewModel.uiState.value.activeView)
    }

    @Test
    fun missingOrInvalidDate_fallsBackToToday() {
        assertEquals(today, viewModel(null).uiState.value.selectedDate)
        assertEquals(today, viewModel("invalid").uiState.value.selectedDate)
    }

    @Test
    fun dayNavigation_keepsVisibleMonthAlignedWithSelection() {
        val viewModel = viewModel(LocalDate.of(2026, 7, 31).toEpochDay().toString())

        viewModel.showNextDay()

        assertEquals(LocalDate.of(2026, 8, 1), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.visibleMonth)
        viewModel.showPreviousDay()
        assertEquals(LocalDate.of(2026, 7, 31), viewModel.uiState.value.selectedDate)
    }

    @Test
    fun monthNavigation_clampsSelectionToValidDay() {
        val viewModel = viewModel(LocalDate.of(2026, 1, 31).toEpochDay().toString())

        viewModel.showNextMonth()

        assertEquals(LocalDate.of(2026, 2, 28), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 2), viewModel.uiState.value.visibleMonth)
    }

    @Test
    fun todayAndViewSelection_updateState() {
        val viewModel = viewModel(LocalDate.of(2025, 1, 1).toEpochDay().toString())

        viewModel.setActiveView(CalendarViewMode.Month)
        viewModel.showToday()

        assertEquals(today, viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.from(today), viewModel.uiState.value.visibleMonth)
        assertEquals(CalendarViewMode.Month, viewModel.uiState.value.activeView)
    }

    @Test
    fun savedState_restoresSelectionMonthAndActiveView() {
        val handle = SavedStateHandle(
            mapOf(CalendarDestination.DateArgument to LocalDate.of(2026, 1, 31).toEpochDay().toString()),
        )
        val original = CalendarViewModel(handle, todayProvider = { today })
        original.showNextMonth()
        original.setActiveView(CalendarViewMode.Month)

        val restored = CalendarViewModel(
            handle,
            todayProvider = { LocalDate.of(2030, 1, 1) },
        )

        assertEquals(original.uiState.value.selectedDate, restored.uiState.value.selectedDate)
        assertEquals(original.uiState.value.visibleMonth, restored.uiState.value.visibleMonth)
        assertEquals(original.uiState.value.activeView, restored.uiState.value.activeView)
        assertEquals(LocalDate.of(2030, 1, 1), restored.uiState.value.today)
    }

    @Test
    fun adjacentDateSelection_changesVisibleMonthAndPreservesMonthView() {
        val viewModel = viewModel(LocalDate.of(2026, 7, 14).toEpochDay().toString())
        viewModel.setActiveView(CalendarViewMode.Month)

        viewModel.selectDate(LocalDate.of(2026, 8, 1))

        assertEquals(LocalDate.of(2026, 8, 1), viewModel.uiState.value.selectedDate)
        assertEquals(YearMonth.of(2026, 8), viewModel.uiState.value.visibleMonth)
        assertEquals(CalendarViewMode.Month, viewModel.uiState.value.activeView)
    }

    @Test
    fun calendarProjection_updatesMonthItemDatesInLocalZone() {
        val zoneId = ZoneId.of("Europe/Tallinn")
        val entries = MutableStateFlow(
            listOf(
                CalendarEntry(
                    id = CalendarEntryId(CalendarItemType.Reminder, 7),
                    title = "Scheduled item",
                    spaceId = null,
                    schedule = CalendarSchedule.Timed(Instant.parse("2026-07-14T22:30:00Z")),
                ),
            ),
        )
        val viewModel = CalendarViewModel(
            savedStateHandle = SavedStateHandle(),
            todayProvider = { today },
            calendarRepository = FakeCalendarRepository(entries),
            zoneId = zoneId,
            observationDispatcher = Dispatchers.Unconfined,
        )

        assertEquals(setOf(LocalDate.of(2026, 7, 15)), viewModel.uiState.value.datesWithItems)
        assertEquals(entries.value, viewModel.uiState.value.entries)
        assertEquals(false, viewModel.uiState.value.isLoadingEntries)
    }

    @Test
    fun sharedProjectionReflectsEditCompletionRemovalAndUndoOnSameItem() {
        val id = CalendarEntryId(CalendarItemType.Task, 42)
        val original = CalendarEntry(
            id = id,
            title = "Original title",
            spaceId = null,
            schedule = CalendarSchedule.DateOnly(today),
            taskStatus = TaskStatus.Open,
        )
        val entries = MutableStateFlow(listOf(original))
        val viewModel = CalendarViewModel(
            savedStateHandle = SavedStateHandle(),
            todayProvider = { today },
            calendarRepository = FakeCalendarRepository(entries),
            zoneId = ZoneId.of("UTC"),
            observationDispatcher = Dispatchers.Unconfined,
        )

        entries.value = listOf(original.copy(title = "Edited title", taskStatus = TaskStatus.Done))
        assertEquals("Edited title", viewModel.uiState.value.entries.single().title)
        assertEquals(TaskStatus.Done, viewModel.uiState.value.entries.single().taskStatus)
        assertEquals(id, viewModel.uiState.value.entries.single().id)

        entries.value = emptyList()
        assertEquals(emptyList<CalendarEntry>(), viewModel.uiState.value.entries)

        entries.value = listOf(original)
        assertEquals(listOf(original), viewModel.uiState.value.entries)
    }

    private fun viewModel(rawDate: String?): CalendarViewModel = CalendarViewModel(
        savedStateHandle = SavedStateHandle(
            rawDate?.let { mapOf(CalendarDestination.DateArgument to it) } ?: emptyMap(),
        ),
        todayProvider = { today },
    )

    private class FakeCalendarRepository(
        private val entries: Flow<List<CalendarEntry>>,
    ) : CalendarRepository {
        override fun observeRange(range: CalendarDateRange): Flow<List<CalendarEntry>> = entries
    }
}
