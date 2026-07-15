package com.orbit.app.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import com.orbit.app.data.repository.CalendarRepository
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.domain.calendar.CalendarSchedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWeekViewModelTest {
    private val today = LocalDate.of(2026, 7, 14)
    private val zoneId = ZoneId.of("Europe/Tallinn")

    @Test
    fun weekDates_followLocaleFirstDayConvention() {
        assertEquals(
            LocalDate.of(2026, 7, 12),
            homeWeekDates(today, Locale.US).first(),
        )
        assertEquals(
            LocalDate.of(2026, 7, 13),
            homeWeekDates(today, Locale.GERMANY).first(),
        )
        assertEquals(7, homeWeekDates(today, Locale.US).distinct().size)
    }

    @Test
    fun selection_usesExactDateAndRestoresFromSavedState() {
        val handle = SavedStateHandle()
        val repository = FakeCalendarRepository()
        val original = viewModel(handle, repository)
        val selectedDate = LocalDate.of(2026, 7, 17)

        original.selectDate(selectedDate)
        val restored = viewModel(handle, repository)

        assertEquals(selectedDate, original.uiState.value.selectedDate)
        assertEquals(selectedDate, restored.uiState.value.selectedDate)
    }

    @Test
    fun today_isTheInitialSelectedDate() {
        val viewModel = viewModel(SavedStateHandle(), FakeCalendarRepository())

        assertEquals(today, viewModel.uiState.value.today)
        assertEquals(today, viewModel.uiState.value.selectedDate)
    }

    @Test
    fun calendarEntries_createOnePresenceStatePerLocalDate() {
        val entries = MutableStateFlow(
            listOf(
                entry(
                    id = 1,
                    schedule = CalendarSchedule.DateOnly(LocalDate.of(2026, 7, 15)),
                ),
                entry(
                    id = 2,
                    schedule = CalendarSchedule.Timed(Instant.parse("2026-07-15T22:30:00Z")),
                ),
                entry(
                    id = 3,
                    schedule = CalendarSchedule.DateOnly(LocalDate.of(2026, 7, 15)),
                ),
            ),
        )

        val viewModel = viewModel(SavedStateHandle(), FakeCalendarRepository(entries))

        assertEquals(
            setOf(LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 16)),
            viewModel.uiState.value.datesWithItems,
        )
    }

    @Test
    fun semanticLabel_describesTodaySelectionAndItemPresence() {
        val label = homeDateContentDescription(
            date = today,
            locale = Locale.US,
            isToday = true,
            isSelected = true,
            hasItems = true,
        )

        assertTrue(label.startsWith("Today,"))
        assertTrue(label.contains("July 14"))
        assertTrue(label.contains("selected"))
        assertTrue(label.contains("has scheduled items"))
        assertFalse(label.contains("null"))
    }

    private fun viewModel(
        handle: SavedStateHandle,
        repository: CalendarRepository,
    ) = HomeWeekViewModel(
        savedStateHandle = handle,
        calendarRepository = repository,
        zoneId = zoneId,
        todayProvider = { today },
        observationDispatcher = Dispatchers.Unconfined,
    )

    private fun entry(id: Long, schedule: CalendarSchedule) = CalendarEntry(
        id = CalendarEntryId(CalendarItemType.Note, id),
        title = "Scheduled item",
        spaceId = null,
        schedule = schedule,
    )

    private class FakeCalendarRepository(
        private val entries: Flow<List<CalendarEntry>> = MutableStateFlow(emptyList()),
    ) : CalendarRepository {
        override fun observeRange(range: CalendarDateRange): Flow<List<CalendarEntry>> = entries
    }
}
