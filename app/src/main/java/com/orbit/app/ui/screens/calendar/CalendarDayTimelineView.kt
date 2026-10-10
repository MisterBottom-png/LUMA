package com.orbit.app.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.orbit.app.R
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.domain.calendar.CalendarEntry
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.components.GroupedCard
import com.orbit.app.ui.localization.labelRes
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.delay

/** The selected day as a short list. Swipe sideways for the previous or next day. */
@Composable
fun CalendarDayAgendaView(
    uiState: CalendarUiState,
    timeFormat: OrbitTimeFormat,
    spaces: Map<Long, SpaceEntity>,
    onEntrySelected: (CalendarEntryId) -> Unit,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onAddForSelectedDate: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val now by currentMinuteInstant()
    val agenda = remember(uiState.entries, uiState.selectedDate, now, zoneId) {
        buildCalendarAgenda(
            buildCalendarDayTimeline(
                entries = uiState.entries,
                selectedDate = uiState.selectedDate,
                now = now,
                zoneId = zoneId,
            ),
        )
    }
    val locale = LocalConfiguration.current.locales[0]
    val swipeThreshold = with(LocalDensity.current) { 64.dp.toPx() }
    var drag by remember(uiState.selectedDate) { mutableFloatStateOf(0f) }
    val previousDayLabel = stringResource(R.string.core_calendar_show_previous_day)
    val nextDayLabel = stringResource(R.string.core_calendar_show_next_day)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> drag += delta },
                onDragStopped = {
                    when (calendarSwipeDateDelta(drag, swipeThreshold)) {
                        -1L -> onPreviousDay()
                        1L -> onNextDay()
                    }
                    drag = 0f
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(previousDayLabel) { onPreviousDay(); true },
                    CustomAccessibilityAction(nextDayLabel) { onNextDay(); true },
                )
            },
        contentPadding = contentPadding,
    ) {
        item(key = "title") {
            CalendarDayTitle(date = uiState.selectedDate, today = uiState.today, locale = locale)
        }
        if (uiState.isLoadingEntries) return@LazyColumn
        if (agenda.isEmpty) {
            item(key = "quiet") {
                CalendarQuietDay(
                    message = stringResource(R.string.calendar_free_day),
                    title = stringResource(R.string.calendar_nothing_planned),
                    action = onAddForSelectedDate,
                )
            }
        } else {
            item(key = "agenda") {
                CalendarAgendaList(
                    agenda = agenda,
                    timeFormat = timeFormat,
                    spaces = spaces,
                    onEntrySelected = onEntrySelected,
                    compact = false,
                )
            }
        }
    }
}

/**
 * Things with no time in their own small group, then the timed list with the "now" line
 * and free stretches. [compact] (month view) leaves out the now line and free time.
 */
@Composable
internal fun CalendarAgendaList(
    agenda: CalendarAgenda,
    timeFormat: OrbitTimeFormat,
    spaces: Map<Long, SpaceEntity>,
    onEntrySelected: (CalendarEntryId) -> Unit,
    compact: Boolean,
) {
    val anyTime = stringResource(R.string.core_calendar_any_time)
    Column {
        if (agenda.anytime.isNotEmpty()) {
            GroupedCard {
                agenda.anytime.forEachIndexed { index, entry ->
                    if (index > 0) GroupDivider(startInset = 104.dp)
                    CalendarEntryRow(
                        entry = entry,
                        timeText = anyTime,
                        space = entry.spaceId?.let(spaces::get),
                        onClick = { onEntrySelected(entry.id) },
                    )
                }
            }
        }
        val rows = if (compact) agenda.rows.filterIsInstance<CalendarAgendaRow.Item>() else agenda.rows
        if (rows.any { it is CalendarAgendaRow.Item }) {
            GroupedCard(modifier = Modifier.padding(top = if (agenda.anytime.isNotEmpty()) 12.dp else 0.dp)) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    var previousWasItem = false
                    rows.forEach { row ->
                        when (row) {
                            is CalendarAgendaRow.Item -> {
                                if (previousWasItem) GroupDivider(startInset = 104.dp)
                                CalendarEntryRow(
                                    entry = row.entry,
                                    timeText = timeFormat.formatTime(row.start.toEpochMilli()),
                                    space = row.entry.spaceId?.let(spaces::get),
                                    onClick = { onEntrySelected(row.entry.id) },
                                )
                                previousWasItem = true
                            }
                            is CalendarAgendaRow.Now -> {
                                CalendarNowRow(timeFormat.formatTime(row.instant.toEpochMilli()))
                                previousWasItem = false
                            }
                            is CalendarAgendaRow.FreeUntil -> {
                                CalendarFreeRow(
                                    stringResource(R.string.calendar_free_until, timeFormat.formatTime(row.until.toEpochMilli())),
                                )
                                previousWasItem = false
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEntryRow(
    entry: CalendarEntry,
    timeText: String,
    space: SpaceEntity?,
    onClick: () -> Unit,
) {
    val isDone = entry.completedAt != null || entry.taskStatus == TaskStatus.Done
    val colors = MaterialTheme.colorScheme
    val details = listOfNotNull(
        space?.let { localizedSpaceName(it.name) },
        entry.repeat?.let { stringResource(it.labelRes()) },
    ).joinToString(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            modifier = Modifier.width(76.dp),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = if (isDone) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 8.dp),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = if (isDone) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    space?.let {
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .size(7.dp)
                                .background(it.colorAccent.asSpaceColor(), CircleShape),
                        )
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        val (icon, tint, label) = when {
            isDone -> Triple(Icons.Rounded.CheckCircle, colors.primary.copy(alpha = 0.55f), R.string.core_completed)
            entry.id.sourceType == CalendarItemType.Reminder && entry.notificationEnabled == false ->
                Triple(Icons.Rounded.NotificationsOff, colors.onSurfaceVariant, R.string.core_calendar_notifications_off)
            entry.id.sourceType == CalendarItemType.Reminder -> Triple(Icons.Rounded.NotificationsNone, colors.primary, R.string.core_reminder)
            entry.id.sourceType == CalendarItemType.Task ->
                // A plain task mark, not an empty ring that looks like a checkbox to tap.
                Triple(Icons.AutoMirrored.Rounded.Assignment, colors.onSurfaceVariant.copy(alpha = 0.7f), R.string.core_task)
            else -> Triple(Icons.Rounded.Description, colors.onSurfaceVariant, R.string.core_note)
        }
        Icon(
            imageVector = icon,
            contentDescription = stringResource(label),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CalendarNowRow(timeText: String) {
    val nowLabel = stringResource(R.string.calendar_now, timeText)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 16.dp)
            .clearAndSetSemantics { contentDescription = nowLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            modifier = Modifier.width(76.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = accent,
        )
        Box(
            modifier = Modifier
                .padding(start = 12.dp)
                .size(9.dp)
                .background(accent, CircleShape),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .background(accent, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
private fun CalendarFreeRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(start = 104.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A day with nothing on it: said kindly, with one gentle way to add something. */
@Composable
internal fun CalendarQuietDay(message: String, action: (() -> Unit)?, title: String? = null) {
    GroupedCard {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = message,
                modifier = Modifier.padding(top = if (title != null) 4.dp else 0.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            action?.let {
                FilledTonalButton(
                    onClick = it,
                    modifier = Modifier.padding(top = 14.dp).heightIn(min = 44.dp),
                ) {
                    Text(stringResource(R.string.calendar_add_to_day), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun currentMinuteInstant() = produceState(initialValue = Instant.now()) {
    while (true) {
        val millisUntilNextMinute = MillisPerMinute - (System.currentTimeMillis() % MillisPerMinute)
        delay(millisUntilNextMinute)
        value = Instant.now()
    }
}

private fun String.asSpaceColor(): Color = runCatching { Color(toColorInt()) }.getOrElse { Color(0xFF6D7CFF) }

private const val MillisPerMinute = 60_000L
