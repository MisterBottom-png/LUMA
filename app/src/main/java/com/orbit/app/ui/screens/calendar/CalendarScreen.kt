package com.orbit.app.ui.screens.calendar

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.domain.calendar.CalendarEntryId
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
        DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    }
    val selectedDateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
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
                    contentDescription = "Back",
                )
            }
            Text(
                text = "Calendar",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(onClick = onToday) {
                Text("Today")
            }
        }

        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            style = GlassSurfaceStyle.Prominent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CalendarStepButton(
                        contentDescription = "Previous month",
                        onClick = onPreviousMonth,
                    )
                    Text(
                        text = uiState.visibleMonth.format(monthFormatter),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    CalendarStepButton(
                        contentDescription = "Next month",
                        forward = true,
                        onClick = onNextMonth,
                    )
                }

                Text(
                    text = uiState.selectedDate.format(selectedDateFormatter),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CalendarStepButton(
                        contentDescription = "Previous day",
                        onClick = onPreviousDay,
                    )
                    Text(
                        text = uiState.selectedDate.dayOfMonth.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    CalendarStepButton(
                        contentDescription = "Next day",
                        forward = true,
                        onClick = onNextDay,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    CalendarViewMode.entries.forEach { view ->
                        FilterChip(
                            selected = uiState.activeView == view,
                            onClick = { onViewSelected(view) },
                            label = { Text(view.label) },
                        )
                    }
                }

                TextButton(
                    onClick = onAddForSelectedDate,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Add for this day")
                }
            }
        }

        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
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
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                selected = cell.isSelected
                contentDescription = calendarMonthCellContentDescription(cell, locale)
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

private val CalendarViewMode.label: String
    get() = when (this) {
        CalendarViewMode.Day -> "Day"
        CalendarViewMode.Month -> "Month"
    }
