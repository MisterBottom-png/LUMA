package com.orbit.app.ui.screens.calendar

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.R
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.theme.CalendarTypography
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Calendar: the month name is the title (tap it to jump to a date), a week strip like
 * Home's sits under it, and the day reads as a short list. One small button switches
 * between the day and the month; "Today" appears only when it would move.
 */
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onToday: () -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    timeFormat: OrbitTimeFormat,
    onEntrySelected: (CalendarEntryId) -> Unit,
    onAddForSelectedDate: () -> Unit,
    spaces: Map<Long, SpaceEntity> = emptyMap(),
) {
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current
    val calendarTitle = stringResource(R.string.core_calendar_title)
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val isMonth = uiState.activeView == CalendarViewMode.Month
    val shownMonth = if (isMonth) uiState.visibleMonth else YearMonth.from(uiState.selectedDate)
    val monthName = remember(shownMonth, locale) {
        shownMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
    }
    val showYear = isMonth || shownMonth.year != uiState.today.year
    val pickDate = {
        val date = uiState.selectedDate
        DatePickerDialog(
            context,
            { _, year, month, day -> onDateSelected(LocalDate.of(year, month + 1, day)) },
            date.year,
            date.monthValue - 1,
            date.dayOfMonth,
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .semantics { paneTitle = calendarTitle },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(
                        onClickLabel = stringResource(R.string.calendar_pick_date),
                        role = Role.Button,
                        onClick = pickDate,
                    )
                    .semantics { heading() }
                    .heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = monthName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                )
                if (showYear) {
                    Text(
                        text = shownMonth.year.toString(),
                        modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp).size(22.dp),
                )
            }
            if (isAwayFromToday(uiState)) {
                FilledTonalButton(
                    onClick = onToday,
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(stringResource(R.string.core_today), style = MaterialTheme.typography.labelLarge)
                }
            }
            // A labelled switch: the word says which view it opens.
            val toggleDescription = stringResource(if (isMonth) R.string.calendar_show_day else R.string.calendar_show_month)
            TextButton(
                onClick = { onViewSelected(if (isMonth) CalendarViewMode.Day else CalendarViewMode.Month) },
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = toggleDescription },
            ) {
                Icon(
                    imageVector = if (isMonth) Icons.Rounded.ViewAgenda else Icons.Rounded.CalendarViewMonth,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(if (isMonth) R.string.calendar_toggle_day else R.string.calendar_toggle_month),
                    modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics { },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAddForSelectedDate) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.core_calendar_add_for_day),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        val bottomPadding = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding
        if (isMonth) {
            CalendarMonthView(
                uiState = uiState,
                locale = locale,
                timeFormat = timeFormat,
                spaces = spaces,
                onDateSelected = onDateSelected,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onEntrySelected = onEntrySelected,
                bottomPadding = bottomPadding,
            )
        } else {
            CalendarWeekStrip(
                uiState = uiState,
                locale = locale,
                onDateSelected = onDateSelected,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                modifier = Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp),
            )
            CalendarDayAgendaView(
                uiState = uiState,
                timeFormat = timeFormat,
                spaces = spaces,
                onEntrySelected = onEntrySelected,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onAddForSelectedDate = onAddForSelectedDate,
                contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = bottomPadding),
            )
        }
    }
}

/** The week of the selected day, on the page like Home's week. Swipe for the next or previous week. */
@Composable
private fun CalendarWeekStrip(
    uiState: CalendarUiState,
    locale: Locale,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val weekStart = uiState.selectedDate.with(TemporalAdjusters.previousOrSame(firstDay))
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    var drag by remember(weekStart) { mutableFloatStateOf(0f) }
    val previousWeek = stringResource(R.string.core_home_show_previous_week)
    val nextWeek = stringResource(R.string.core_home_show_next_week)
    val todayLabel = stringResource(R.string.core_today)
    val hasItemsLabel = stringResource(R.string.core_has_scheduled_items)
    val selectedLabel = stringResource(R.string.core_selected)
    val dayFormatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> drag += delta },
                onDragStopped = {
                    when (calendarSwipeDateDelta(drag, swipeThreshold)) {
                        -1L -> onPreviousWeek()
                        1L -> onNextWeek()
                    }
                    drag = 0f
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousWeek) { onPreviousWeek(); true },
                    CustomAccessibilityAction(nextWeek) { onNextWeek(); true },
                )
            },
    ) {
        (0L until 7L).forEach { offset ->
            val date = weekStart.plusDays(offset)
            val isToday = date == uiState.today
            val isSelected = date == uiState.selectedDate
            val hasItems = date in uiState.datesWithItems
            val description = listOfNotNull(
                date.format(dayFormatter),
                todayLabel.takeIf { isToday },
                selectedLabel.takeIf { isSelected },
                hasItemsLabel.takeIf { hasItems },
            ).joinToString(", ")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(role = Role.Button) { onDateSelected(date) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = description
                        selected = isSelected
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.NARROW_STANDALONE, locale),
                    style = CalendarTypography.weekday,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateDisc(
                    day = date.dayOfMonth,
                    isToday = isToday,
                    isSelected = isSelected,
                    muted = false,
                    modifier = Modifier.padding(top = 4.dp),
                )
                ItemDot(visible = hasItems, isToday = isToday, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A date number: today is a filled accent disc, the selected day a thin accent ring. */
@Composable
internal fun DateDisc(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    muted: Boolean,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isToday) colors.primary else Color.Transparent)
            .then(
                if (isSelected && !isToday) Modifier.border(1.5.dp, colors.primary, CircleShape) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style = CalendarTypography.monthDate.copy(
                fontSize = 17.sp,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = when {
                isToday -> colors.onPrimary
                muted -> colors.onSurfaceVariant
                else -> colors.onSurface
            },
        )
    }
}

@Composable
internal fun ItemDot(visible: Boolean, isToday: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(4.dp)
            .background(
                color = when {
                    !visible -> Color.Transparent
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                },
                shape = CircleShape,
            ),
    )
}

/** The month as a calm grid of dates with dots, and the selected day's list under it. */
@Composable
private fun CalendarMonthView(
    uiState: CalendarUiState,
    locale: Locale,
    timeFormat: OrbitTimeFormat,
    spaces: Map<Long, SpaceEntity>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEntrySelected: (CalendarEntryId) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
) {
    val grid = remember(uiState.visibleMonth, uiState.today, uiState.datesWithItems, locale) {
        buildCalendarMonthGrid(
            visibleMonth = uiState.visibleMonth,
            today = uiState.today,
            datesWithItems = uiState.datesWithItems,
            locale = locale,
        )
    }
    val accessibilityLabels = CalendarMonthCellAccessibilityLabels(
        today = stringResource(R.string.core_today),
        outsideCurrentMonth = stringResource(R.string.core_calendar_outside_current_month),
        hasScheduledItems = stringResource(R.string.core_has_scheduled_items),
        separator = stringResource(R.string.core_accessibility_separator),
    )
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember(uiState.visibleMonth) { mutableFloatStateOf(0f) }
    val previousMonthLabel = stringResource(R.string.core_calendar_show_previous_month)
    val nextMonthLabel = stringResource(R.string.core_calendar_show_next_month)
    val selectedEntries = remember(uiState.entries, uiState.selectedDate) {
        buildCalendarAgenda(
            buildCalendarDayTimeline(
                entries = uiState.entries,
                selectedDate = uiState.selectedDate,
                now = java.time.Instant.now(),
                zoneId = java.time.ZoneId.systemDefault(),
            ),
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = bottomPadding),
    ) {
        item(key = "weekdays") {
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                grid.weekdayLabels.forEach { label ->
                    Text(
                        text = label.shortLabel,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = label.contentDescription },
                        style = CalendarTypography.weekday,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
        item(key = "grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta -> drag += delta },
                        onDragStopped = {
                            when (calendarSwipeDateDelta(drag, swipeThreshold)) {
                                -1L -> onPreviousMonth()
                                1L -> onNextMonth()
                            }
                            drag = 0f
                        },
                    )
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(previousMonthLabel) { onPreviousMonth(); true },
                            CustomAccessibilityAction(nextMonthLabel) { onNextMonth(); true },
                        )
                    },
            ) {
                grid.weeks.forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { cell ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (cell.isInVisibleMonth) {
                                    val isSelected = cell.date == uiState.selectedDate
                                    Column(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.medium)
                                            .clickable(role = Role.Button) { onDateSelected(cell.date) }
                                            .semantics(mergeDescendants = true) {
                                                contentDescription = calendarMonthCellContentDescription(
                                                    cell,
                                                    locale,
                                                    accessibilityLabels,
                                                )
                                                selected = isSelected
                                            }
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        DateDisc(
                                            day = cell.date.dayOfMonth,
                                            isToday = cell.isToday,
                                            isSelected = isSelected,
                                            muted = cell.date.isBefore(uiState.today),
                                            size = 36.dp,
                                        )
                                        ItemDot(
                                            visible = cell.hasItems,
                                            isToday = cell.isToday,
                                            modifier = Modifier.padding(top = 3.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "day_title") {
            CalendarDayTitle(
                date = uiState.selectedDate,
                today = uiState.today,
                locale = locale,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
        item(key = "day_list") {
            if (selectedEntries.isEmpty) {
                CalendarQuietDay(message = stringResource(R.string.calendar_free_day_short), action = null)
            } else {
                CalendarAgendaList(
                    agenda = selectedEntries,
                    timeFormat = timeFormat,
                    spaces = spaces,
                    onEntrySelected = onEntrySelected,
                    compact = true,
                )
            }
        }
    }
}

@Composable
internal fun CalendarDayTitle(date: LocalDate, today: LocalDate, locale: Locale, modifier: Modifier = Modifier) {
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
    val dayText = date.format(formatter).replaceFirstChar { it.titlecase(locale) }
    Text(
        text = if (date == today) stringResource(R.string.calendar_today_prefix, dayText) else dayText,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, bottom = 10.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        color = MaterialTheme.colorScheme.onBackground,
    )
}

internal fun calendarSwipeDateDelta(horizontalDrag: Float, threshold: Float): Long = when {
    horizontalDrag >= threshold -> -1L
    horizontalDrag <= -threshold -> 1L
    else -> 0L
}

internal fun isAwayFromToday(uiState: CalendarUiState): Boolean = when (uiState.activeView) {
    CalendarViewMode.Day -> uiState.selectedDate != uiState.today
    CalendarViewMode.Month ->
        uiState.visibleMonth != java.time.YearMonth.from(uiState.today) || uiState.selectedDate != uiState.today
}
