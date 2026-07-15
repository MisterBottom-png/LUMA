package com.orbit.app.ui.screens.calendar

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
import com.orbit.app.ui.navigation.CalendarDestination
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CalendarViewMode {
    Day,
    Month,
}

data class CalendarUiState(
    val today: LocalDate,
    val selectedDate: LocalDate,
    val visibleMonth: YearMonth,
    val activeView: CalendarViewMode,
    val datesWithItems: Set<LocalDate> = emptySet(),
    val entries: List<CalendarEntry> = emptyList(),
    val isLoadingEntries: Boolean = false,
)

class CalendarViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
    private val calendarRepository: CalendarRepository? = null,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    observationDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ViewModel() {
    private val today: LocalDate
        get() = todayProvider()

    private val initialSelectedDate = restoredDate(SelectedDateKey)
        ?: CalendarDestination.initialDate(
            rawEpochDay = savedStateHandle[CalendarDestination.DateArgument],
            fallback = today,
        )
    private val initialVisibleMonth = restoredDate(VisibleMonthKey)
        ?.let(YearMonth::from)
        ?: YearMonth.from(initialSelectedDate)
    private val initialView = savedStateHandle.get<String>(ActiveViewKey)
        ?.let { stored -> CalendarViewMode.entries.firstOrNull { it.name == stored } }
        ?: CalendarViewMode.Day

    private val _uiState = MutableStateFlow(
        CalendarUiState(
            today = today,
            selectedDate = initialSelectedDate,
            visibleMonth = initialVisibleMonth,
            activeView = initialView,
            isLoadingEntries = calendarRepository != null,
        ),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    init {
        persist(_uiState.value)
        calendarRepository?.let { repository ->
            viewModelScope.launch(observationDispatcher) {
                _uiState
                    .map { it.visibleMonth }
                    .distinctUntilChanged()
                    .collectLatest { month ->
                        val range = CalendarDateRange(
                            startDate = month.atDay(1).minusDays(6),
                            endDateExclusive = month.plusMonths(1).atDay(1).plusDays(6),
                            zoneId = zoneId,
                        )
                        repository.observeRange(range).collectLatest { entries ->
                            _uiState.update { state ->
                                if (state.visibleMonth == month) {
                                    state.copy(
                                        datesWithItems = entries.mapTo(linkedSetOf()) { it.calendarDate(zoneId) },
                                        entries = entries,
                                        isLoadingEntries = false,
                                    )
                                } else {
                                    state
                                }
                            }
                        }
                    }
            }
        }
    }

    fun showPreviousDay() = moveSelectedDateBy(days = -1)

    fun showNextDay() = moveSelectedDateBy(days = 1)

    fun showPreviousMonth() = moveVisibleMonthBy(months = -1)

    fun showNextMonth() = moveVisibleMonthBy(months = 1)

    fun showToday() {
        val date = today
        updateState(
            _uiState.value.copy(
                today = date,
                selectedDate = date,
                visibleMonth = YearMonth.from(date),
            ),
        )
    }

    fun setActiveView(view: CalendarViewMode) {
        updateState(_uiState.value.copy(activeView = view))
    }

    fun selectDate(date: LocalDate) {
        updateState(
            _uiState.value.copy(
                selectedDate = date,
                visibleMonth = YearMonth.from(date),
            ),
        )
    }

    private fun moveSelectedDateBy(days: Long) {
        val selectedDate = _uiState.value.selectedDate.plusDays(days)
        updateState(
            _uiState.value.copy(
                selectedDate = selectedDate,
                visibleMonth = YearMonth.from(selectedDate),
            ),
        )
    }

    private fun moveVisibleMonthBy(months: Long) {
        val targetMonth = _uiState.value.visibleMonth.plusMonths(months)
        val targetDay = _uiState.value.selectedDate.dayOfMonth.coerceAtMost(targetMonth.lengthOfMonth())
        updateState(
            _uiState.value.copy(
                selectedDate = targetMonth.atDay(targetDay),
                visibleMonth = targetMonth,
            ),
        )
    }

    private fun updateState(state: CalendarUiState) {
        val updatedState = if (state.visibleMonth != _uiState.value.visibleMonth) {
            state.copy(
                datesWithItems = emptySet(),
                entries = emptyList(),
                isLoadingEntries = calendarRepository != null,
            )
        } else {
            state
        }
        _uiState.value = updatedState
        persist(updatedState)
    }

    private fun persist(state: CalendarUiState) {
        savedStateHandle[SelectedDateKey] = state.selectedDate.toEpochDay()
        savedStateHandle[VisibleMonthKey] = state.visibleMonth.atDay(1).toEpochDay()
        savedStateHandle[ActiveViewKey] = state.activeView.name
    }

    private fun restoredDate(key: String): LocalDate? = savedStateHandle.get<Long>(key)
        ?.let { epochDay -> runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() }

    private companion object {
        const val SelectedDateKey = "calendar.selectedDate"
        const val VisibleMonthKey = "calendar.visibleMonth"
        const val ActiveViewKey = "calendar.activeView"
    }

    class Factory(
        private val calendarRepository: CalendarRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(CalendarViewModel::class.java))
            return CalendarViewModel(
                savedStateHandle = extras.createSavedStateHandle(),
                calendarRepository = calendarRepository,
            ) as T
        }
    }
}
