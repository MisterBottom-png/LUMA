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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
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
    LaunchedEffect(timeline.date, uiState.isLoadingEntries) {
        if (timeline.rows.isNotEmpty()) {
            listState.scrollToItem(
                timeline.recommendedScrollIndex.coerceIn(0, timeline.rows.lastIndex),
            )
        }
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(
            items = timeline.rows,
            key = { index, row -> row.stableKey(index) },
        ) { _, row ->
            when (row) {
                CalendarDayRow.AnyTimeHeader -> CalendarDaySectionHeader("Any time")
                is CalendarDayRow.AnyTimeItem -> CalendarEntryCard(
                    entry = row.entry,
                    onClick = { onEntrySelected(row.entry.id) },
                )
                CalendarDayRow.TimelineHeader -> CalendarDaySectionHeader("Timeline")
                is CalendarDayRow.TimedItems -> CalendarTimedItemsRow(
                    group = row.group,
                    timeFormat = timeFormat,
                    onEntrySelected = onEntrySelected,
                )
                is CalendarDayRow.CurrentTime -> CalendarCurrentTimeRow(
                    instant = row.instant,
                    timeFormat = timeFormat,
                )
                CalendarDayRow.EmptyTimeline -> CalendarDayEmptyMessage(
                    "No items are set for a specific time today.",
                )
                CalendarDayRow.EmptyDay -> CalendarDayEmptyMessage(
                    "Nothing is scheduled for this day.",
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
                text = "Bringing this day into view...",
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
        modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun CalendarTimedItemsRow(
    group: CalendarTimedGroup,
    timeFormat: OrbitTimeFormat,
    onEntrySelected: (CalendarEntryId) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun CalendarCurrentTimeRow(
    instant: Instant,
    timeFormat: OrbitTimeFormat,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeFormat.formatTime(instant.toEpochMilli()),
            modifier = Modifier.width(TimelineTimeWidth),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.68f)),
        )
    }
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
                text = entry.cardDetails,
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

private val CalendarEntry.cardDetails: String
    get() = buildList {
        add(
            when (id.sourceType) {
                CalendarItemType.Note -> "Note"
                CalendarItemType.Task -> "Task"
                CalendarItemType.Reminder -> "Reminder"
            },
        )
        if (completedAt != null || taskStatus == TaskStatus.Done) add("Completed")
        reminderDetail?.let(::add)
    }.joinToString(" | ")

private val CalendarEntry.reminderDetail: String?
    get() = if (id.sourceType != CalendarItemType.Reminder) {
        null
    } else if (notificationEnabled == false) {
        "Notifications off"
    } else {
        notificationOffsetMinutes?.let { offset ->
            when {
                offset < 0 -> "Notification timing unavailable"
                offset == 0L -> "Notification at target time"
                else -> "Notification $offset min before"
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
private val TimelineTimeWidth = 78.dp
