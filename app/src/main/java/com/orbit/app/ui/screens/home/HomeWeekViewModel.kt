package com.orbit.app.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.orbit.app.data.repository.CalendarRepository
import com.orbit.app.domain.calendar.CalendarDateRange
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.calendarDate
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeWeekUiState(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val datesWithItems: Set<LocalDate> = emptySet(),
)

class HomeWeekViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    calendarRepository: CalendarRepository,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    todayProvider: () -> LocalDate = { LocalDate.now(zoneId) },
    observationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val today = todayProvider()
    private val _uiState = MutableStateFlow(
        HomeWeekUiState(
            today = today,
            selectedDate = restoredDate(savedStateHandle[SelectedDateKey]) ?: today,
        ),
    )
    val uiState: StateFlow<HomeWeekUiState> = _uiState.asStateFlow()

    init {
        persistSelectedDate(_uiState.value.selectedDate)
        val range = CalendarDateRange(
            startDate = today.minusDays(6),
            endDateExclusive = today.plusDays(7),
            zoneId = zoneId,
        )
        viewModelScope.launch(observationDispatcher) {
            calendarRepository.observeRange(range).collectLatest { entries ->
                _uiState.update { state ->
                    state.copy(datesWithItems = entries.calendarDates(zoneId))
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
        persistSelectedDate(date)
    }

    private fun persistSelectedDate(date: LocalDate) {
        savedStateHandle[SelectedDateKey] = date.toEpochDay()
    }

    class Factory(
        private val calendarRepository: CalendarRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(HomeWeekViewModel::class.java))
            return HomeWeekViewModel(
                savedStateHandle = extras.createSavedStateHandle(),
                calendarRepository = calendarRepository,
            ) as T
        }
    }

    private companion object {
        const val SelectedDateKey = "home.week.selectedDate"
    }
}

internal fun homeWeekDates(today: LocalDate, locale: Locale): List<LocalDate> {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val daysFromStart = (7 + today.dayOfWeek.value - firstDay.value) % 7
    val startDate = today.minusDays(daysFromStart.toLong())
    return List(7) { offset -> startDate.plusDays(offset.toLong()) }
}

internal fun homeWeekdayLabel(date: LocalDate, locale: Locale): String =
    date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT_STANDALONE, locale)

internal fun homeDateContentDescription(
    date: LocalDate,
    locale: Locale,
    isToday: Boolean,
    isSelected: Boolean,
    hasItems: Boolean,
): String = buildList {
    if (isToday) add("Today")
    add(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale).format(date))
    if (isSelected) add("selected")
    if (hasItems) add("has scheduled items")
}.joinToString(", ")

internal fun List<CalendarEntry>.calendarDates(zoneId: ZoneId): Set<LocalDate> = mapTo(linkedSetOf()) { entry ->
    entry.calendarDate(zoneId)
}

private fun restoredDate(epochDay: Long?): LocalDate? = epochDay?.let {
    runCatching { LocalDate.ofEpochDay(it) }.getOrNull()
}
