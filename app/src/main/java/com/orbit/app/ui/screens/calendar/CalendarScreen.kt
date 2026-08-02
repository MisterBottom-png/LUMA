package com.orbit.app.ui.screens.calendar

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.orbitPressFeedback
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    onBack: () -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToday: () -> Unit,
    onViewSelected: (CalendarViewMode) -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    timeFormat: OrbitTimeFormat,
    onEntrySelected: (CalendarEntryId) -> Unit,
    onAddForSelectedDate: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val monthFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    val selectedDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val swipeThresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
    var horizontalDrag by remember(uiState.activeView, uiState.selectedDate, uiState.visibleMonth) {
        mutableFloatStateOf(0f)
    }
    val calendarDragState = rememberDraggableState { delta -> horizontalDrag += delta }
    val previousPage = {
        if (uiState.activeView == CalendarViewMode.Day) onPreviousDay() else onPreviousMonth()
    }
    val nextPage = {
        if (uiState.activeView == CalendarViewMode.Day) onNextDay() else onNextMonth()
    }
    val calendarTitle = stringResource(R.string.core_calendar_title)
    val showPreviousDay = stringResource(R.string.core_calendar_show_previous_day)
    val showPreviousMonth = stringResource(R.string.core_calendar_show_previous_month)
    val showNextDay = stringResource(R.string.core_calendar_show_next_day)
    val showNextMonth = stringResource(R.string.core_calendar_show_next_month)
    val addForThisDay = stringResource(R.string.core_calendar_add_for_day)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .semantics { paneTitle = calendarTitle },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.core_back),
                )
            }
            Text(
                text = calendarTitle,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onToday) {
                Text(stringResource(R.string.core_today))
            }
        }

        SoftGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = OrbitShapes.Prominent,
            style = GlassSurfaceStyle.Prominent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OrbitSpacing.Large, vertical = OrbitSpacing.Medium),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CalendarStepButton(
                        contentDescription = stringResource(R.string.core_calendar_previous_month),
                        onClick = onPreviousMonth,
                    )
                    Text(
                        text = uiState.visibleMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CalendarStepButton(
                        contentDescription = stringResource(R.string.core_calendar_next_month),
                        forward = true,
                        onClick = onNextMonth,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalendarStepButton(
                        contentDescription = stringResource(R.string.core_calendar_previous_day),
                        onClick = onPreviousDay,
                    )
                    Text(
                        text = uiState.selectedDate.format(selectedDateFormatter),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    CalendarStepButton(
                        contentDescription = stringResource(R.string.core_calendar_next_day),
                        forward = true,
                        onClick = onNextDay,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Small)) {
                        CalendarViewMode.entries.forEach { view ->
                            FilterChip(
                                selected = uiState.activeView == view,
                                onClick = { onViewSelected(view) },
                                label = { Text(stringResource(view.labelRes())) },
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = onAddForSelectedDate,
                        modifier = Modifier.semantics {
                            contentDescription = addForThisDay
                        },
                        shape = OrbitShapes.Standard,
                        contentPadding = PaddingValues(
                            horizontal = OrbitSpacing.Medium,
                            vertical = OrbitSpacing.Small,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.core_add),
                            modifier = Modifier.padding(start = OrbitSpacing.Small),
                        )
                    }
                }
            }
        }

        SoftGlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .draggable(
                    state = calendarDragState,
                    orientation = Orientation.Horizontal,
                    onDragStarted = { horizontalDrag = 0f },
                    onDragStopped = {
                        when (calendarSwipeDateDelta(horizontalDrag, swipeThresholdPx)) {
                            -1L -> previousPage()
                            1L -> nextPage()
                        }
                        horizontalDrag = 0f
                    },
                )
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(
                            label = if (uiState.activeView == CalendarViewMode.Day) {
                                showPreviousDay
                            } else {
                                showPreviousMonth
                            },
                            action = { previousPage(); true },
                        ),
                        CustomAccessibilityAction(
                            label = if (uiState.activeView == CalendarViewMode.Day) {
                                showNextDay
                            } else {
                                showNextMonth
                            },
                            action = { nextPage(); true },
                        ),
                    )
                },
            shape = RoundedCornerShape(28.dp),
            style = GlassSurfaceStyle.Standard,
        ) {
            if (uiState.activeView == CalendarViewMode.Month) {
                CalendarMonthOverview(
                    uiState = uiState,
                    locale = locale,
                    onDateSelected = onDateSelected,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CalendarDayTimelineView(
                    uiState = uiState,
                    timeFormat = timeFormat,
                    onEntrySelected = onEntrySelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthOverview(
    uiState: CalendarUiState,
    locale: Locale,
    onDateSelected: (java.time.LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekdayLabels = remember(locale) {
        buildCalendarMonthGrid(
            visibleMonth = uiState.visibleMonth,
            selectedDate = uiState.selectedDate,
            today = uiState.today,
            datesWithItems = emptySet(),
            locale = locale,
        ).weekdayLabels
    }

    Column(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            weekdayLabels.forEach { label ->
                Text(
                    text = label.shortLabel,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = label.contentDescription },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }

        Crossfade(
            targetState = uiState.visibleMonth,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            animationSpec = tween(durationMillis = 160),
            label = "calendarMonthMovement",
        ) { visibleMonth ->
            val grid = remember(
                visibleMonth,
                uiState.selectedDate,
                uiState.today,
                uiState.datesWithItems,
                locale,
            ) {
                buildCalendarMonthGrid(
                    visibleMonth = visibleMonth,
                    selectedDate = uiState.selectedDate,
                    today = uiState.today,
                    datesWithItems = uiState.datesWithItems,
                    locale = locale,
                )
            }
            CalendarMonthGridContent(
                grid = grid,
                locale = locale,
                onDateSelected = onDateSelected,
            )
        }
    }
}

@Composable
private fun CalendarMonthGridContent(
    grid: CalendarMonthGrid,
    locale: Locale,
    onDateSelected: (java.time.LocalDate) -> Unit,
) {
    val accessibilityLabels = CalendarMonthCellAccessibilityLabels(
        today = stringResource(R.string.core_today),
        selected = stringResource(R.string.core_selected),
        outsideCurrentMonth = stringResource(R.string.core_calendar_outside_current_month),
        hasScheduledItems = stringResource(R.string.core_has_scheduled_items),
        separator = stringResource(R.string.core_accessibility_separator),
    )
    Column(modifier = Modifier.fillMaxSize()) {
        grid.weeks.forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                week.forEach { cell ->
                    CalendarMonthDayCell(
                        cell = cell,
                        locale = locale,
                        accessibilityLabels = accessibilityLabels,
                        onClick = { onDateSelected(cell.date) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarMonthDayCell(
    cell: CalendarMonthCell,
    locale: Locale,
    accessibilityLabels: CalendarMonthCellAccessibilityLabels,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val backgroundColor by animateColorAsState(
        targetValue = if (cell.isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "calendarDateSelection",
    )
    val dateColor = when {
        cell.isSelected || cell.isToday -> MaterialTheme.colorScheme.primary
        cell.isInVisibleMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.52f)
    }

    Column(
        modifier = modifier
            .padding(1.dp)
            .clip(shape)
            .then(
                if (cell.isToday) {
                    Modifier.border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .background(backgroundColor)
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = cell.isSelected
                contentDescription = calendarMonthCellContentDescription(
                    cell,
                    locale,
                    accessibilityLabels,
                )
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = cell.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (cell.isSelected || cell.isToday) FontWeight.SemiBold else FontWeight.Normal,
            color = dateColor,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(
                    color = if (cell.hasItems) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun CalendarStepButton(
    contentDescription: String,
    forward: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
    ) {
        Icon(
            imageVector = if (forward) Icons.Rounded.ChevronRight else Icons.Rounded.ChevronLeft,
            contentDescription = contentDescription,
        )
    }
}

@StringRes
private fun CalendarViewMode.labelRes(): Int = when (this) {
    CalendarViewMode.Day -> R.string.core_calendar_day
    CalendarViewMode.Month -> R.string.core_calendar_month
}

internal fun calendarSwipeDateDelta(horizontalDrag: Float, threshold: Float): Long = when {
    horizontalDrag >= threshold -> -1L
    horizontalDrag <= -threshold -> 1L
    else -> 0L
}
