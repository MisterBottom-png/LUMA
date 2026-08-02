package com.orbit.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.delay

@Composable
fun CalendarDayTimelineView(
    uiState: CalendarUiState,
    timeFormat: OrbitTimeFormat,
    onEntrySelected: (CalendarEntryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now by currentMinuteInstant()
    val timeline = remember(uiState.entries, uiState.selectedDate, now, zoneId) {
        buildCalendarDayTimeline(
            entries = uiState.entries,
            selectedDate = uiState.selectedDate,
            now = now,
            zoneId = zoneId,
        )
    }

    if (uiState.isLoadingEntries) {
        CalendarDayLoading(modifier)
        return
    }

    val listState = rememberLazyListState()
    val anyTimeItems = timeline.rows.filterIsInstance<CalendarDayRow.AnyTimeItem>()
    val timedGroups = timeline.rows.filterIsInstance<CalendarDayRow.TimedItems>().map { it.group }
    val currentTime = timeline.rows.filterIsInstance<CalendarDayRow.CurrentTime>().singleOrNull()
    val hasTimeline = timedGroups.isNotEmpty() || currentTime != null
    val timelineCanvasIndex = (if (anyTimeItems.isEmpty()) 0 else anyTimeItems.size + 1) + 1
    val density = LocalDensity.current
    LaunchedEffect(timeline.date, uiState.isLoadingEntries, timelineCanvasIndex) {
        if (hasTimeline) {
            val relevantMinute = currentTime?.minuteOfDay ?: timedGroups.first().minuteOfDay
            val desiredOffset = with(density) {
                (TimelineTopPadding + TimelineDayHeight * calendarTimelineOffsetFraction(relevantMinute))
                    .roundToPx() - 24.dp.roundToPx()
            }
            listState.scrollToItem(timelineCanvasIndex, desiredOffset.coerceAtLeast(0))
        } else {
            listState.scrollToItem(0)
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (anyTimeItems.isNotEmpty()) {
            item(key = "any-time-header") {
                CalendarDaySectionHeader(stringResource(R.string.core_calendar_any_time))
            }
            items(
                count = anyTimeItems.size,
                key = { index -> anyTimeItems[index].stableKey(index) },
            ) { index ->
                val row = anyTimeItems[index]
                CalendarEntryCard(
                    entry = row.entry,
                    onClick = { onEntrySelected(row.entry.id) },
                )
            }
        }
        if (hasTimeline) {
            item(key = "timeline-header") {
                CalendarDaySectionHeader(stringResource(R.string.core_calendar_timeline))
            }
            item(key = "timeline-canvas") {
                CalendarTimelineCanvas(
                    timedGroups = timedGroups,
                    currentTime = currentTime,
                    timeFormat = timeFormat,
                    showEmptyMessage = timedGroups.isEmpty(),
                    onEntrySelected = onEntrySelected,
                )
            }
        } else if (anyTimeItems.isEmpty()) {
            item(key = "empty-day") {
                CalendarDayEmptyMessage(
                    stringResource(R.string.core_calendar_nothing_scheduled),
                )
            }
        }
    }
}

@Composable
private fun CalendarDayLoading(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.core_calendar_loading_day),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarDaySectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .padding(start = 4.dp, top = 6.dp, bottom = 2.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CalendarTimelineCanvas(
    timedGroups: List<CalendarTimedGroup>,
    currentTime: CalendarDayRow.CurrentTime?,
    timeFormat: OrbitTimeFormat,
    showEmptyMessage: Boolean,
    onEntrySelected: (CalendarEntryId) -> Unit,
) {
    val timedMinutes = timedGroups.map(CalendarTimedGroup::minuteOfDay)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimelineTopPadding + TimelineDayHeight + TimelineBottomPadding),
    ) {
        repeat(HoursPerDay + 1) { hour ->
            val y = TimelineTopPadding + TimelineHourHeight * hour
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = y),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (
                    shouldShowTimelineHourLabel(
                        hour = hour,
                        timedMinutes = timedMinutes,
                        currentMinute = currentTime?.minuteOfDay,
                    )
                ) {
                    Text(
                        text = if (hour < HoursPerDay) {
                            formatTimelineHour(hour, timeFormat)
                        } else {
                            timeFormat.formatEndOfDay()
                        },
                        modifier = Modifier.width(TimelineTimeWidth),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1,
                    )
                } else {
                    Box(modifier = Modifier.width(TimelineTimeWidth))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f)),
                )
            }
        }
        currentTime?.let { current ->
            val y = TimelineTopPadding + TimelineDayHeight * calendarTimelineOffsetFraction(current.minuteOfDay)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = y),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shouldShowCurrentTimeLabel(current.minuteOfDay, timedMinutes)) {
                    Text(
                        text = timeFormat.formatTime(current.instant.toEpochMilli()),
                        modifier = Modifier.width(TimelineTimeWidth),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                } else {
                    Box(modifier = Modifier.width(TimelineTimeWidth))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)),
                )
            }
        }
        timedGroups.forEach { group ->
            val y = TimelineTopPadding + TimelineDayHeight * calendarTimelineOffsetFraction(group.minuteOfDay)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = y),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = timeFormat.formatTime(group.start.toEpochMilli()),
                    modifier = Modifier
                        .width(TimelineTimeWidth)
                        .padding(top = 12.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    group.entries.forEach { entry ->
                        CalendarEntryCard(
                            entry = entry,
                            onClick = { onEntrySelected(entry.id) },
                        )
                    }
                }
            }
        }
        if (showEmptyMessage) currentTime?.let { current ->
            val y = TimelineTopPadding + TimelineDayHeight * calendarTimelineOffsetFraction(current.minuteOfDay)
            Text(
                text = stringResource(R.string.core_calendar_no_timed_items_today),
                modifier = Modifier
                    .padding(start = TimelineTimeWidth + 8.dp, end = 6.dp)
                    .offset(y = y + 18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimelineHour(hour: Int, timeFormat: OrbitTimeFormat): String {
    return timeFormat.formatTime(LocalTime.of(hour, 0))
}

@Composable
private fun CalendarEntryCard(
    entry: CalendarEntry,
    onClick: () -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.cardDetails(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalendarDayEmptyMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun currentMinuteInstant() = produceState(initialValue = Instant.now()) {
    while (true) {
        val millisUntilNextMinute = MillisPerMinute - (System.currentTimeMillis() % MillisPerMinute)
        delay(millisUntilNextMinute)
        value = Instant.now()
    }
}

@Composable
private fun CalendarEntry.cardDetails(): String {
    val typeLabel = stringResource(
        when (id.sourceType) {
            CalendarItemType.Note -> R.string.core_note
            CalendarItemType.Task -> R.string.core_task
            CalendarItemType.Reminder -> R.string.core_reminder
        },
    )
    val completedLabel = stringResource(R.string.core_completed)
    val reminderDetail = reminderDetail()
    val separator = stringResource(R.string.core_metadata_pipe_separator)
    return buildList {
        add(typeLabel)
        if (completedAt != null || taskStatus == TaskStatus.Done) {
            add(completedLabel)
        }
        reminderDetail?.let(::add)
    }.joinToString(separator)
}

@Composable
private fun CalendarEntry.reminderDetail(): String? =
    if (id.sourceType != CalendarItemType.Reminder) {
        null
    } else if (notificationEnabled == false) {
        stringResource(R.string.core_calendar_notifications_off)
    } else {
        notificationOffsetMinutes?.let { offset ->
            when {
                offset < 0 -> stringResource(R.string.core_calendar_notification_timing_unavailable)
                offset == 0L -> stringResource(R.string.core_calendar_notification_at_target)
                else -> pluralStringResource(
                    R.plurals.core_calendar_notification_minutes_before,
                    offset.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    offset,
                )
            }
        }
    }

private fun CalendarDayRow.stableKey(index: Int): String = when (this) {
    CalendarDayRow.AnyTimeHeader -> "any-time-header"
    is CalendarDayRow.AnyTimeItem -> "any-${entry.id.sourceType}-${entry.id.sourceItemId}"
    CalendarDayRow.TimelineHeader -> "timeline-header"
    is CalendarDayRow.TimedItems -> "time-${group.minuteOfDay}"
    is CalendarDayRow.CurrentTime -> "current-time"
    CalendarDayRow.EmptyTimeline -> "empty-timeline"
    CalendarDayRow.EmptyDay -> "empty-day-$index"
}

private const val MillisPerMinute = 60_000L
private const val HoursPerDay = 24
private val TimelineTimeWidth = 78.dp
private val TimelineHourHeight = 48.dp
private val TimelineDayHeight = TimelineHourHeight * HoursPerDay
private val TimelineTopPadding = 12.dp
private val TimelineBottomPadding = 24.dp
