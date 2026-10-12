package com.orbit.app.ui.screens.spaces

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.components.LumaModalBottomSheet
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private val TileShape = RoundedCornerShape(20.dp)
private val FieldShape = RoundedCornerShape(20.dp)

/**
 * The Space "+" sheet (review §9.2). Write → it is saved into the Space at once → the
 * sheet asks only for what is missing. Closing keeps the draft; nothing typed is lost,
 * and the sheet can't be closed while it is saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceCaptureFlowSheet(
    state: SpaceCaptureUiState,
    spaceName: String,
    draft: String,
    timeFormat: OrbitTimeFormat,
    onDraftChange: (String) -> Unit,
    onSaveAsChange: (SpaceSaveAs) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onRemindAt: (Long) -> Unit,
    onPickTime: () -> Unit,
    onBackToTimeQuestion: () -> Unit,
    onKeepAsTask: () -> Unit,
    onChooseNote: () -> Unit,
    onChooseTask: () -> Unit,
    onChooseReminder: () -> Unit,
    onDecideLater: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val busy by rememberUpdatedState(state.busy)
    LumaModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> target != SheetValue.Hidden || !busy },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            when (state.step) {
                SpaceCaptureStep.Write -> WriteStep(state, spaceName, draft, onDraftChange, onSaveAsChange, onSave, onDismiss)
                SpaceCaptureStep.Looking -> LookingStep(state, spaceName)
                SpaceCaptureStep.TimeQuestion -> TimeQuestionStep(state, spaceName, timeFormat, zoneId, onRemindAt, onPickTime, onKeepAsTask)
                SpaceCaptureStep.PickTime -> PickTimeStep(state, timeFormat, now, zoneId, onRemindAt, onBackToTimeQuestion)
                SpaceCaptureStep.TypeQuestion -> TypeQuestionStep(state, spaceName, onChooseNote, onChooseTask, onChooseReminder, onDecideLater)
            }
            if (state.failed) {
                Text(
                    text = stringResource(R.string.core_home_message_capture_save_failed),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WriteStep(
    state: SpaceCaptureUiState,
    spaceName: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSaveAsChange: (SpaceSaveAs) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .heightIn(min = 48.dp),
    ) {
        TextButton(onClick = onDismiss, enabled = !state.busy, modifier = Modifier.align(Alignment.CenterStart)) {
            Text(stringResource(R.string.core_cancel))
        }
        Text(
            text = stringResource(R.string.space_capture_title, spaceName),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 88.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    OutlinedTextField(
        value = draft,
        onValueChange = onDraftChange,
        enabled = !state.busy,
        shape = FieldShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .heightIn(min = 112.dp)
            .focusRequester(focus),
        placeholder = { Text(stringResource(R.string.space_capture_hint)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Text(
        text = stringResource(R.string.space_capture_save_as),
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SpaceSaveAs.Decide to R.string.space_capture_let_decide,
            SpaceSaveAs.Note to R.string.core_note,
            SpaceSaveAs.Task to R.string.core_task,
            SpaceSaveAs.Reminder to R.string.core_reminder,
        ).forEach { (option, label) ->
            val selected = state.saveAs == option
            FilterChip(
                selected = selected,
                onClick = { onSaveAsChange(option) },
                enabled = !state.busy,
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                border = null,
            )
        }
    }
    Text(
        text = stringResource(R.string.space_capture_explain, spaceName),
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(
        onClick = onSave,
        enabled = draft.isNotBlank() && !state.busy,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 56.dp),
    ) {
        Text(stringResource(R.string.space_capture_save_to, spaceName), style = MaterialTheme.typography.titleSmall)
    }
}

/** "✓ Saved to Work" and the thought itself, shown on every step after saving. */
@Composable
private fun SavedHeader(state: SpaceCaptureUiState, spaceName: String) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CheckCircleOutline,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.space_capture_saved_to, spaceName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ThoughtCard(state.thought, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun ThoughtCard(thought: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Text(
            text = thought,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StepTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .padding(top = 20.dp, bottom = 12.dp)
            .semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LookingStep(state: SpaceCaptureUiState, spaceName: String) {
    SavedHeader(state, spaceName)
    Row(
        modifier = Modifier.padding(vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.review_to_sort_analyzing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeQuestionStep(
    state: SpaceCaptureUiState,
    spaceName: String,
    timeFormat: OrbitTimeFormat,
    zoneId: ZoneId,
    onRemindAt: (Long) -> Unit,
    onPickTime: () -> Unit,
    onKeepAsTask: () -> Unit,
) {
    SavedHeader(state, spaceName)
    StepTitle(stringResource(R.string.needs_you_q_time))
    // Quick times first, then "Pick a time", two to a row.
    val tiles: List<SpaceQuickTime?> = state.quickTimes + listOf(null)
    tiles.chunked(2).forEach { row ->
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { quick ->
                if (quick == null) {
                    PickTimeTile(enabled = !state.busy, onClick = onPickTime, modifier = Modifier.weight(1f))
                } else {
                    TimeTile(
                        title = quickDayLabel(quick, zoneId),
                        subtitle = timeFormat.formatTime(quick.at),
                        enabled = !state.busy,
                        onClick = { onRemindAt(quick.at) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    TextButton(onClick = onKeepAsTask, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.space_capture_keep_as_task), style = MaterialTheme.typography.titleSmall)
    }
    Text(
        text = stringResource(R.string.space_capture_time_footnote, spaceName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun quickDayLabel(quick: SpaceQuickTime, zoneId: ZoneId): String = when (quick.day) {
    QuickDay.Today -> stringResource(R.string.core_today)
    QuickDay.Tomorrow -> stringResource(R.string.core_tomorrow)
    QuickDay.Weekday -> Instant.ofEpochMilli(quick.at).atZone(zoneId).dayOfWeek
        .getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

@Composable
private fun TimeTile(title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = TileShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PickTimeTile(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 64.dp),
        shape = TileShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.core_home_quick_reminder_pick), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private val QuickClockTimes = listOf(
    LocalTime.of(8, 0), LocalTime.of(9, 0), LocalTime.of(12, 0),
    LocalTime.of(15, 0), LocalTime.of(18, 0), LocalTime.of(20, 0),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PickTimeStep(
    state: SpaceCaptureUiState,
    timeFormat: OrbitTimeFormat,
    now: () -> Long,
    zoneId: ZoneId,
    onRemindAt: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val today = remember { Instant.ofEpochMilli(now()).atZone(zoneId).toLocalDate() }
    var day by rememberSaveable { mutableStateOf(today.plusDays(1).toEpochDay()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(9, 0).toSecondOfDay()) }
    val chosenDay = LocalDate.ofEpochDay(day)
    val chosenTime = LocalTime.ofSecondOfDay(time.toLong())
    val chosenAt = chosenDay.atTime(chosenTime).atZone(zoneId).toInstant().toEpochMilli()
    val isFuture = chosenAt > now()

    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).heightIn(min = 48.dp)) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.core_back))
        }
        Text(
            text = stringResource(R.string.core_home_quick_reminder_pick),
            modifier = Modifier.align(Alignment.Center).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
    Text(
        text = state.thought,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    SectionLabel(stringResource(R.string.space_capture_day))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L..5L).map { today.plusDays(it) }.forEach { date ->
            val selected = date == chosenDay
            DayChip(
                weekday = if (date == today) {
                    stringResource(R.string.core_today)
                } else {
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, Locale.getDefault())
                },
                dayNumber = date.dayOfMonth.toString(),
                selected = selected,
                onClick = { day = date.toEpochDay() },
                modifier = Modifier.weight(1f),
            )
        }
    }
    TextButton(
        onClick = {
            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                day = LocalDate.of(year, month + 1, dayOfMonth).toEpochDay()
            }, chosenDay.year, chosenDay.monthValue - 1, chosenDay.dayOfMonth).apply {
                datePicker.minDate = today.atStartOfDay(zoneId).toInstant().toEpochMilli()
            }.show()
        },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.space_capture_another_day)) }
    SectionLabel(stringResource(R.string.space_capture_time))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        QuickClockTimes.forEach { clock ->
            val at = chosenDay.atTime(clock).atZone(zoneId).toInstant().toEpochMilli()
            TimeChip(
                label = timeFormat.formatTime(clock),
                selected = clock == chosenTime,
                enabled = at > now(),
                onClick = { time = clock.toSecondOfDay() },
                modifier = Modifier.weight(1f),
            )
        }
    }
    TextButton(
        onClick = {
            TimePickerDialog(context, { _, hour, minute ->
                time = LocalTime.of(hour, minute).toSecondOfDay()
            }, chosenTime.hour, chosenTime.minute, timeFormat.uses24HourClock).show()
        },
        modifier = Modifier.heightIn(min = 48.dp),
    ) { Text(stringResource(R.string.space_capture_another_time)) }
    if (!isFuture) {
        Text(
            text = stringResource(R.string.space_capture_time_passed),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
    val dayWord = when (chosenDay) {
        today -> stringResource(R.string.core_today)
        today.plusDays(1) -> stringResource(R.string.core_tomorrow)
        else -> timeFormat.formatDate(chosenDay)
    }
    Button(
        onClick = { onRemindAt(chosenAt) },
        enabled = isFuture && !state.busy,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .heightIn(min = 56.dp),
    ) {
        Text(
            stringResource(R.string.space_capture_remind_me, "$dayWord ${timeFormat.formatTime(chosenTime)}"),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DayChip(weekday: String, dayNumber: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(weekday, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dayNumber, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TimeChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .heightIn(min = 52.dp)
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        shape = RoundedCornerShape(16.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.6f else 0.25f)
        },
        contentColor = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            enabled -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 14.dp)) {
            // A time is never cut short: it gets its own line at any text size.
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun TypeQuestionStep(
    state: SpaceCaptureUiState,
    spaceName: String,
    onChooseNote: () -> Unit,
    onChooseTask: () -> Unit,
    onChooseReminder: () -> Unit,
    onDecideLater: () -> Unit,
) {
    SavedHeader(state, spaceName)
    StepTitle(stringResource(R.string.space_capture_what))
    TypeRow(Icons.Rounded.Description, stringResource(R.string.core_note), stringResource(R.string.space_capture_note_hint), !state.busy, onChooseNote)
    TypeRow(Icons.Rounded.RadioButtonUnchecked, stringResource(R.string.core_task), stringResource(R.string.space_capture_task_hint), !state.busy, onChooseTask)
    TypeRow(Icons.Rounded.Notifications, stringResource(R.string.core_reminder), stringResource(R.string.space_capture_reminder_hint), !state.busy, onChooseReminder)
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    TextButton(onClick = onDecideLater, enabled = !state.busy && state.captureId != null, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.space_capture_decide_later), style = MaterialTheme.typography.titleSmall)
    }
    Text(
        text = stringResource(R.string.space_capture_type_footnote, spaceName),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TypeRow(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .heightIn(min = 64.dp),
        shape = TileShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
