package com.orbit.app.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.R
import com.orbit.app.domain.analyzer.CaptureAnalyzerSource
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** A task with only a day keeps the end of that day as its time, as elsewhere in the app. */
internal fun morningMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

/**
 * When a task is due: a day without a time ([dayEpochDay]), or a moment the user
 * picked ([at]). Never both, and never a made-up end-of-day time.
 */
internal data class TaskDue(val dayEpochDay: Long? = null, val at: Long? = null) {
    init {
        require(dayEpochDay == null || at == null) { "A task is due on a day or at a time, not both" }
    }

    companion object {
        fun day(date: LocalDate) = TaskDue(dayEpochDay = date.toEpochDay())
    }
}

/** Which quick "When" choice a task's due day matches. */
internal enum class TaskWhen { None, Today, Tomorrow, Other }

internal fun taskWhenFor(due: TaskDue, today: LocalDate): TaskWhen = when {
    due.dayEpochDay == null && due.at == null -> TaskWhen.None
    due.dayEpochDay == today.toEpochDay() -> TaskWhen.Today
    due.dayEpochDay == today.plusDays(1).toEpochDay() -> TaskWhen.Tomorrow
    else -> TaskWhen.Other
}

/**
 * The sort sheet for one thought: the thought as the title, four type tiles, the Space,
 * "When" for tasks and reminders, and one button that says exactly what will happen.
 * Nothing is saved until that button is pressed.
 */
@Composable
internal fun CaptureSortContent(
    suggestion: CaptureSuggestion,
    timeFormat: OrbitTimeFormat,
    isPerformingAction: Boolean,
    title: String,
    onTitleChanged: (String) -> Unit,
    selectedAction: CaptureDecisionAction,
    selectedSpaceId: Long?,
    selectedLabels: List<String>,
    taskDue: TaskDue,
    onTaskDueChanged: (TaskDue) -> Unit,
    reminderAt: Long?,
    onReminderAtChanged: (Long) -> Unit,
    initialDate: LocalDate?,
    onActionSelected: (CaptureDecisionAction) -> Unit,
    onSpaceSelected: (Long?) -> Unit,
    onRemoveLabel: (String) -> Unit,
    onConfirm: () -> Unit,
    onNotNow: () -> Unit,
) {
    val analysis = suggestion.analysis
    SortForm(
        key = suggestion.captureId,
        timeFormat = timeFormat,
        isPerformingAction = isPerformingAction,
        title = title,
        onTitleChanged = onTitleChanged,
        suggestionLine = when {
            analysis.analyzerFailed -> stringResource(R.string.core_capture_analysis_paused)
            analysis.confidenceLevel == CaptureConfidence.Low -> stringResource(R.string.core_capture_keep_until_clearer)
            else -> stringResource(R.string.sort_suggests, stringResource(suggestedActionLabel(analysis.suggestedType)))
        },
        why = if (analysis.analyzerFailed) {
            null
        } else {
            listOf(
                analysis.typeReason,
                analysis.spaceReason,
                stringResource(
                    if (analysis.analyzerSource == CaptureAnalyzerSource.Gemini) {
                        R.string.sort_source_gemini
                    } else {
                        R.string.sort_source_phone
                    },
                ),
            ).filter { it.isNotBlank() }.joinToString(" ")
        },
        selectedAction = selectedAction,
        onActionSelected = onActionSelected,
        spaces = suggestion.spaceOptions,
        selectedSpaceId = selectedSpaceId,
        onSpaceSelected = onSpaceSelected,
        selectedLabels = selectedLabels,
        onRemoveLabel = onRemoveLabel,
        taskDue = taskDue,
        onTaskDueChanged = onTaskDueChanged,
        reminderAt = reminderAt,
        onReminderAtChanged = onReminderAtChanged,
        initialDate = initialDate,
        onConfirm = onConfirm,
    ) {
        TextButton(
            onClick = onNotNow,
            enabled = !isPerformingAction,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.sort_not_now),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The shared body of every sort screen, used for a single thought and for one thought
 * from a Brain Dump, so both look and work the same. [aboveButton] holds status lines;
 * [below] holds the quiet way out ("Not now", "Back to the list").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SortForm(
    key: Any,
    timeFormat: OrbitTimeFormat,
    isPerformingAction: Boolean,
    title: String,
    onTitleChanged: (String) -> Unit,
    suggestionLine: String,
    why: String?,
    selectedAction: CaptureDecisionAction,
    onActionSelected: (CaptureDecisionAction) -> Unit,
    spaces: List<CaptureSpaceOption>,
    selectedSpaceId: Long?,
    onSpaceSelected: (Long?) -> Unit,
    taskDue: TaskDue,
    onTaskDueChanged: (TaskDue) -> Unit,
    reminderAt: Long?,
    onReminderAtChanged: (Long) -> Unit,
    initialDate: LocalDate?,
    onConfirm: () -> Unit,
    selectedLabels: List<String> = emptyList(),
    onRemoveLabel: (String) -> Unit = {},
    aboveButton: @Composable () -> Unit = {},
    below: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var editingTitle by rememberSaveable(key) { mutableStateOf(false) }
    var showWhy by rememberSaveable(key) { mutableStateOf(false) }

    // The thought itself is the title. A small pencil makes it editable.
    if (editingTitle) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChanged,
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.titleLarge,
            shape = RoundedCornerShape(16.dp),
            trailingIcon = {
                IconButton(onClick = { editingTitle = false }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.core_done))
                }
            },
        )
    } else {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 6.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = { editingTitle = true }, enabled = !isPerformingAction) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.sort_edit_title),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    // One plain line about the suggestion, with "Why?" for the reasons.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = suggestionLine,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!why.isNullOrBlank()) {
            TextButton(
                onClick = { showWhy = !showWhy },
                contentPadding = PaddingValues(horizontal = 10.dp),
            ) {
                Text(stringResource(if (showWhy) R.string.sort_why_hide else R.string.sort_why))
            }
        }
    }
    if (showWhy && !why.isNullOrBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                text = why,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    // What is it?
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        decisionActions().forEach { action ->
            SortTypeTile(
                label = stringResource(action.tileLabelRes()),
                icon = action.tileIcon(),
                selected = action == selectedAction,
                enabled = !isPerformingAction,
                onClick = { onActionSelected(action) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (selectedAction != CaptureDecisionAction.KeepInbox && spaces.isNotEmpty()) {
        SortLabel(stringResource(R.string.sort_space))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            spaces.forEach { space ->
                SortChip(
                    label = if (space.id == null) stringResource(R.string.core_inbox) else localizedSpaceName(space.name),
                    selected = selectedSpaceId == space.id,
                    enabled = !isPerformingAction,
                    onClick = { onSpaceSelected(if (selectedSpaceId == space.id) null else space.id) },
                )
            }
        }
    }

    if (selectedLabels.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            selectedLabels.forEach { label ->
                SortChip(
                    label = "#$label  ×",
                    selected = false,
                    enabled = !isPerformingAction,
                    onClick = { onRemoveLabel(label) },
                )
            }
        }
    }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    when (selectedAction) {
        CaptureDecisionAction.CreateTask -> {
            SortLabel(stringResource(R.string.sort_when))
            val current = taskWhenFor(taskDue, today)
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChip(stringResource(R.string.sort_no_date), current == TaskWhen.None, !isPerformingAction) {
                    onTaskDueChanged(TaskDue())
                }
                SortChip(stringResource(R.string.core_today), current == TaskWhen.Today, !isPerformingAction) {
                    onTaskDueChanged(TaskDue.day(today))
                }
                SortChip(stringResource(R.string.core_tomorrow), current == TaskWhen.Tomorrow, !isPerformingAction) {
                    onTaskDueChanged(TaskDue.day(today.plusDays(1)))
                }
                SortChip(
                    label = when {
                        current != TaskWhen.Other -> stringResource(R.string.sort_pick_date)
                        taskDue.dayEpochDay != null -> timeFormat.formatDate(LocalDate.ofEpochDay(taskDue.dayEpochDay))
                        else -> timeFormat.formatWeekdayDateTime(requireNotNull(taskDue.at))
                    },
                    selected = current == TaskWhen.Other,
                    enabled = !isPerformingAction,
                ) {
                    val initial = taskDue.dayEpochDay?.let(LocalDate::ofEpochDay)
                        ?: taskDue.at?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                        ?: initialDate
                        ?: today
                    pickDate(context, initial) { onTaskDueChanged(TaskDue.day(it)) }
                }
            }
        }

        CaptureDecisionAction.CreateReminder -> {
            SortLabel(stringResource(R.string.sort_remind_me))
            val tomorrowMorning = morningMillis(today.plusDays(1), zone)
            FlowRow(
                modifier = Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reminderAt != null && reminderAt != tomorrowMorning) {
                    SortChip(timeFormat.formatWeekdayDateTime(reminderAt), selected = true, enabled = !isPerformingAction) {
                        pickDateTime(context, reminderAt, initialDate, timeFormat, onReminderAtChanged)
                    }
                }
                SortChip(
                    label = stringResource(R.string.sort_tomorrow_at, timeFormat.formatTime(LocalTime.of(9, 0))),
                    selected = reminderAt == tomorrowMorning,
                    enabled = !isPerformingAction,
                ) { onReminderAtChanged(tomorrowMorning) }
                SortChip(stringResource(R.string.sort_pick_time), selected = false, enabled = !isPerformingAction) {
                    pickDateTime(context, reminderAt, initialDate, timeFormat, onReminderAtChanged)
                }
            }
        }

        else -> Unit
    }

    aboveButton()

    // The button always names the result and waits until the choice is complete.
    val needsTime = selectedAction == CaptureDecisionAction.CreateReminder && reminderAt == null
    if (needsTime) {
        Text(
            text = stringResource(R.string.sort_pick_time_first),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Button(
        onClick = onConfirm,
        enabled = sortPrimaryEnabled(isPerformingAction, title, needsTime),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp)
            .heightIn(min = 56.dp),
    ) {
        if (isPerformingAction) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(selectedAction.primaryLabelRes),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
            )
        }
    }
    below()
}

internal fun sortPrimaryEnabled(isPerformingAction: Boolean, title: String, needsTime: Boolean): Boolean =
    !isPerformingAction && title.isNotBlank() && !needsTime

@Composable
private fun SortLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SortTypeTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(72.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.10f)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun SortChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.12f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp), maxLines = 1)
        }
    }
}

private fun CaptureDecisionAction.tileLabelRes(): Int = when (this) {
    CaptureDecisionAction.CreateTask -> R.string.core_task
    CaptureDecisionAction.CreateReminder -> R.string.core_reminder
    CaptureDecisionAction.SaveNote -> R.string.core_note
    CaptureDecisionAction.KeepInbox -> R.string.sort_type_later
}

private fun CaptureDecisionAction.tileIcon(): ImageVector = when (this) {
    CaptureDecisionAction.CreateTask -> Icons.Rounded.TaskAlt
    CaptureDecisionAction.CreateReminder -> Icons.Rounded.NotificationsNone
    CaptureDecisionAction.SaveNote -> Icons.AutoMirrored.Rounded.Notes
    CaptureDecisionAction.KeepInbox -> Icons.Rounded.Inbox
}

internal fun suggestedActionLabel(type: com.orbit.app.data.local.entity.SuggestedItemType): Int = when (type) {
    com.orbit.app.data.local.entity.SuggestedItemType.Task,
    com.orbit.app.data.local.entity.SuggestedItemType.MondayItem,
    -> R.string.sort_kind_task
    com.orbit.app.data.local.entity.SuggestedItemType.Reminder -> R.string.sort_kind_reminder
    com.orbit.app.data.local.entity.SuggestedItemType.Note -> R.string.sort_kind_note
}

/** Date, then time. A reminder starts from the day the user came from, if any. */
/** A day for a task: a date picker only, no time. */
private fun pickDate(context: Context, initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

private fun pickDateTime(
    context: Context,
    initialMillis: Long?,
    initialDate: LocalDate?,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = initialMillis
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: initialDate?.atTime(9, 0)
        ?: LocalDateTime.now(zone).plusHours(1).withMinute(0).withSecond(0).withNano(0)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(zone).toInstant().toEpochMilli())
                },
                initial.hour,
                initial.minute,
                timeFormat.uses24HourClock,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
