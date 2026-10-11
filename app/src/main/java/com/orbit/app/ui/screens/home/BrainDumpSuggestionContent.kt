package com.orbit.app.ui.screens.home

import com.orbit.app.ui.reminders.rememberTurnOnReminderNotifications
import com.orbit.app.ui.reminders.messageRes
import com.orbit.app.reminders.ReminderSaveOutcome
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.orbit.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import com.orbit.app.ui.components.LumaMenu
import com.orbit.app.ui.components.LumaMenuItem
import com.orbit.app.ui.components.LumaMenuGap
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.DeleteSweep

internal data class BrainDumpCallbacks(
    val onPrimaryAction: () -> Unit,
    val onEdit: () -> Unit,
    val onDraftChanged: (BrainDumpDraft) -> Unit,
    val onContinueFromEditor: () -> Unit,
    val onStepBack: () -> Unit,
    val onKeepInInbox: () -> Unit,
    val onSkip: () -> Unit,
    val onUndoSkip: () -> Unit,
    val onRetry: () -> Unit,
    val onFinishLater: () -> Unit,
    val onDiscardRemaining: () -> Unit,
    val onCloseCompletion: () -> Unit,
    val onToggleRow: (String) -> Unit = {},
    val onOpenRow: (String) -> Unit = {},
    val onSaveTicked: () -> Unit = {},
    val onKeepAsOneNote: () -> Unit = {},
    val onSplitRow: (String) -> Unit = {},
)

@Composable
internal fun BrainDumpSuggestionContent(
    suggestion: CaptureSuggestion,
    state: BrainDumpInteractionState,
    timeFormat: OrbitTimeFormat,
    callbacks: BrainDumpCallbacks,
    modifier: Modifier = Modifier,
) {
    val progressFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.itemId) {
        if (state.itemId != null) {
            progressFocusRequester.requestFocus()
        }
    }
    val item = state.itemId?.let { itemId ->
        suggestion.analysis.brainDumpItems.firstOrNull { it.id == itemId }
    }
    when (state.stage) {
        BrainDumpStage.Overview -> BrainDumpOverview(
            state = state,
            timeFormat = timeFormat,
            callbacks = callbacks,
            modifier = modifier,
        )

        BrainDumpStage.Suggestion -> BrainDumpSuggestionCard(
            item = requireNotNull(item),
            state = state,
            spaces = suggestion.spaceOptions,
            timeFormat = timeFormat,
            callbacks = callbacks,
            progressFocusRequester = progressFocusRequester,
            modifier = modifier,
        )

        BrainDumpStage.Edit -> BrainDumpEditor(
            item = requireNotNull(item),
            draft = requireNotNull(state.draft),
            spaces = suggestion.spaceOptions,
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            status = state.status,
            warning = state.warning,
            onDraftChanged = callbacks.onDraftChanged,
            onContinue = callbacks.onContinueFromEditor,
            onBack = callbacks.onStepBack,
            onRetry = callbacks.onRetry,
            modifier = modifier,
        )

        BrainDumpStage.TaskSetup -> BrainDumpScheduleSetup(
            type = SuggestedItemType.Task,
            draft = requireNotNull(state.draft),
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            status = state.status,
            warning = state.warning,
            onDraftChanged = callbacks.onDraftChanged,
            onConfirm = callbacks.onPrimaryAction,
            onBack = callbacks.onStepBack,
            onRetry = callbacks.onRetry,
            modifier = modifier,
        )

        BrainDumpStage.ReminderSetup -> BrainDumpScheduleSetup(
            type = SuggestedItemType.Reminder,
            draft = requireNotNull(state.draft),
            timeFormat = timeFormat,
            actionInProgress = state.actionInProgress,
            status = state.status,
            warning = state.warning,
            onDraftChanged = callbacks.onDraftChanged,
            onConfirm = callbacks.onPrimaryAction,
            onBack = callbacks.onStepBack,
            onRetry = callbacks.onRetry,
            modifier = modifier,
        )

        BrainDumpStage.Completion -> BrainDumpCompletionSummary(
            counts = state.completionCounts,
            status = state.status,
            warning = state.warning,
            actionInProgress = state.actionInProgress,
            onUndo = callbacks.onUndoSkip,
            onRetry = callbacks.onRetry,
            onClose = callbacks.onCloseCompletion,
            modifier = modifier,
        )
    }
}

/**
 * One thought from a Brain Dump, sorted with the same sheet as a single thought: the
 * thought as the title, type tiles, Space and When chips, and one clear button. Only
 * that button saves; "Back to the list" (or "Finish later") leaves it waiting.
 */
@Composable
private fun BrainDumpSuggestionCard(
    item: BrainDumpSuggestion,
    state: BrainDumpInteractionState,
    spaces: List<CaptureSpaceOption>,
    timeFormat: OrbitTimeFormat,
    callbacks: BrainDumpCallbacks,
    progressFocusRequester: FocusRequester,
    modifier: Modifier,
) {
    val draft = requireNotNull(state.draft)
    var keepInInbox by rememberSaveable(item.id) { mutableStateOf(false) }
    var showMore by rememberSaveable(item.id) { mutableStateOf(false) }
    var showDiscardConfirmation by rememberSaveable(item.id) { mutableStateOf(false) }
    val selectedAction = if (keepInInbox) CaptureDecisionAction.KeepInbox else draft.type.sortAction()

    Column(modifier = modifier.fillMaxWidth()) {
        // The thought's actions sit beside its heading, where they are found.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.core_brain_dump_progress, state.itemNumber, state.totalItems),
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 6.dp)
                    .focusRequester(progressFocusRequester)
                    .focusable()
                    .semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Box {
                IconButton(
                    onClick = { showMore = true },
                    enabled = !state.actionInProgress,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.core_brain_dump_more),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BrainDumpActionMenu(
                    expanded = showMore,
                    onDismiss = { showMore = false },
                    onKeepInInbox = {
                        showMore = false
                        callbacks.onKeepInInbox()
                    },
                    onSkip = {
                        showMore = false
                        callbacks.onSkip()
                    },
                    onDiscardRemaining = {
                        showMore = false
                        showDiscardConfirmation = true
                    },
                )
            }
        }
        SortForm(
            key = item.id,
            timeFormat = timeFormat,
            isPerformingAction = state.actionInProgress,
            title = draft.title,
            onTitleChanged = { callbacks.onDraftChanged(draft.copy(title = it)) },
            suggestionLine = stringResource(R.string.sort_suggests, stringResource(suggestedActionLabel(item.suggestedType))),
            why = listOf(item.reason, item.tinyNextAction).filter { it.isNotBlank() }.joinToString(" "),
            selectedAction = selectedAction,
            onActionSelected = { action ->
                keepInInbox = action == CaptureDecisionAction.KeepInbox
                action.itemType()?.let { type -> callbacks.onDraftChanged(draft.copy(type = type)) }
            },
            spaces = spaces,
            selectedSpaceId = draft.spaceId,
            onSpaceSelected = { callbacks.onDraftChanged(draft.copy(spaceId = it)) },
            taskDue = TaskDue(
                dayEpochDay = draft.scheduledDateEpochDay,
                at = draft.scheduledAt.takeIf { draft.scheduledDateEpochDay == null },
            ),
            onTaskDueChanged = { due ->
                callbacks.onDraftChanged(draft.copy(scheduledAt = due.at, scheduledDateEpochDay = due.dayEpochDay))
            },
            reminderAt = draft.scheduledAt,
            onReminderAtChanged = { callbacks.onDraftChanged(draft.copy(scheduledAt = it)) },
            initialDate = null,
            onConfirm = if (keepInInbox) callbacks.onKeepInInbox else callbacks.onPrimaryAction,
            aboveButton = {
                BrainDumpInlineStatus(
                    status = state.status,
                    warning = state.warning,
                    onUndo = callbacks.onUndoSkip,
                    onRetry = callbacks.onRetry,
                    modifier = Modifier.padding(top = 12.dp),
                )
            },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = if (state.openedFromOverview) callbacks.onStepBack else callbacks.onFinishLater,
                    enabled = !state.actionInProgress,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.openedFromOverview) R.string.brain_overview_back else R.string.core_brain_dump_finish_later,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text(stringResource(R.string.core_capture_discard_remaining_title)) },
            text = { Text(stringResource(R.string.core_capture_discard_remaining_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        callbacks.onDiscardRemaining()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.core_capture_discard_remaining),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.core_cancel))
                }
            },
        )
    }
}

private fun SuggestedItemType.sortAction(): CaptureDecisionAction = when (this) {
    SuggestedItemType.Note -> CaptureDecisionAction.SaveNote
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> CaptureDecisionAction.CreateTask
    SuggestedItemType.Reminder -> CaptureDecisionAction.CreateReminder
}

private fun CaptureDecisionAction.itemType(): SuggestedItemType? = when (this) {
    CaptureDecisionAction.SaveNote -> SuggestedItemType.Note
    CaptureDecisionAction.CreateTask -> SuggestedItemType.Task
    CaptureDecisionAction.CreateReminder -> SuggestedItemType.Reminder
    CaptureDecisionAction.KeepInbox -> null
}

@Composable
internal fun BrainDumpActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onKeepInInbox: () -> Unit,
    onSkip: () -> Unit,
    onDiscardRemaining: () -> Unit,
) {
    LumaMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LumaMenuItem(
            text = { Text(stringResource(R.string.core_brain_dump_keep_thought)) },
            leadingIcon = { Icon(Icons.Rounded.Inbox, contentDescription = null) },
            onClick = onKeepInInbox,
        )
        LumaMenuItem(
            text = { Text(stringResource(R.string.core_brain_dump_skip_thought)) },
            leadingIcon = { Icon(Icons.Rounded.SkipNext, contentDescription = null) },
            onClick = onSkip,
        )
        LumaMenuGap()
        LumaMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.core_capture_discard_remaining),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = onDiscardRemaining,
        )
    }
}

@Composable
internal fun BrainDumpEditor(
    item: BrainDumpSuggestion,
    draft: BrainDumpDraft,
    spaces: List<CaptureSpaceOption>,
    timeFormat: OrbitTimeFormat,
    actionInProgress: Boolean,
    status: BrainDumpStatus?,
    warning: BrainDumpStatus?,
    onDraftChanged: (BrainDumpDraft) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSpacePicker by rememberSaveable(item.id) { mutableStateOf(false) }
    val selectedSpaceName = brainDumpSpaceName(spaces.firstOrNull { it.id == draft.spaceId })
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.core_capture_edit_details),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChanged(draft.copy(title = it)) },
            enabled = !actionInProgress,
            label = { Text(stringResource(R.string.core_capture_suggested_item)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.core_type),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(SuggestedItemType.Note, SuggestedItemType.Task, SuggestedItemType.Reminder).forEach { type ->
                FilterChip(
                    selected = draft.type == type,
                    onClick = { onDraftChanged(draft.copy(type = type)) },
                    enabled = !actionInProgress,
                    label = { Text(stringResource(type.labelRes())) },
                )
            }
        }
        OutlinedButton(
            onClick = { showSpacePicker = true },
            enabled = !actionInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.core_brain_dump_space_value, selectedSpaceName))
        }
        BrainDumpInlineStatus(
            status = status,
            warning = warning,
            onUndo = {},
            onRetry = onRetry,
            modifier = Modifier.padding(top = 8.dp),
        )
        draft.scheduledAt?.let { scheduledAt ->
            Text(
                text = timeFormat.formatWeekdayDateTime(scheduledAt),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = item.rawText,
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.reason,
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = onContinue,
            enabled = draft.title.isNotBlank() && !actionInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Text(stringResource(draft.type.primaryActionLabelRes()))
        }
        TextButton(
            onClick = onBack,
            enabled = !actionInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_back))
        }
    }
    if (showSpacePicker) {
        BrainDumpSpacePicker(
            spaces = spaces,
            selectedSpaceId = draft.spaceId,
            onSelected = {
                onDraftChanged(draft.copy(spaceId = it))
                showSpacePicker = false
            },
            onDismiss = { showSpacePicker = false },
        )
    }
}

@Composable
private fun BrainDumpSpacePicker(
    spaces: List<CaptureSpaceOption>,
    selectedSpaceId: Long?,
    onSelected: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.core_place)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items(spaces, key = { space -> "${space.id}:${space.name}" }) { space ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RadioButton(
                            selected = space.id == selectedSpaceId,
                            onClick = { onSelected(space.id) },
                        )
                        TextButton(onClick = { onSelected(space.id) }) {
                            Text(brainDumpSpaceName(space))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_close)) }
        },
    )
}

@Composable
private fun BrainDumpScheduleSetup(
    type: SuggestedItemType,
    draft: BrainDumpDraft,
    timeFormat: OrbitTimeFormat,
    actionInProgress: Boolean,
    status: BrainDumpStatus?,
    warning: BrainDumpStatus?,
    onDraftChanged: (BrainDumpDraft) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isReminder = type == SuggestedItemType.Reminder
    val scheduleLabel = draft.scheduledAt?.let(timeFormat::formatWeekdayDateTime)
        ?: stringResource(if (isReminder) R.string.core_capture_choose_date_time else R.string.core_capture_no_due_date)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(type.primaryActionLabelRes()),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedTextField(
            value = draft.title,
            onValueChange = { onDraftChanged(draft.copy(title = it)) },
            enabled = !actionInProgress,
            label = { Text(stringResource(R.string.core_capture_suggested_item)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        )
        Text(
            text = scheduleLabel,
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (isReminder && draft.scheduledAt == null) {
            Text(
                text = stringResource(R.string.core_item_detail_reminder_time_required),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BrainDumpInlineStatus(
            status = status,
            warning = warning,
            onUndo = {},
            onRetry = onRetry,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    showBrainDumpDateTimePicker(
                        context = context,
                        initialValue = draft.scheduledAt,
                        timeFormat = timeFormat,
                    ) { onDraftChanged(draft.copy(scheduledAt = it)) }
                },
                enabled = !actionInProgress,
            ) {
                val label = if (draft.scheduledAt == null) {
                    if (isReminder) R.string.core_capture_choose_date_time else R.string.core_capture_add_due_date_time
                } else {
                    R.string.core_capture_change_date_time
                }
                Text(stringResource(label))
            }
            if (!isReminder && draft.scheduledAt != null) {
                TextButton(
                    onClick = { onDraftChanged(draft.copy(scheduledAt = null)) },
                    enabled = !actionInProgress,
                ) {
                    Text(stringResource(R.string.core_remove))
                }
            }
        }
        Button(
            onClick = onConfirm,
            enabled = draft.title.isNotBlank() && (!isReminder || draft.scheduledAt != null) && !actionInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(if (isReminder) R.string.core_capture_create_reminder else R.string.core_capture_action_create_task))
        }
        TextButton(
            onClick = onBack,
            enabled = !actionInProgress,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_back))
        }
    }
}

@Composable
internal fun BrainDumpInlineStatus(
    status: BrainDumpStatus?,
    warning: BrainDumpStatus? = null,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statuses = listOfNotNull(warning, status).distinct()
    if (statuses.isEmpty()) return
    val turnOnNotifications = rememberTurnOnReminderNotifications()
    Column(modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        statuses.forEach { visibleStatus ->
            Text(
                text = stringResource(visibleStatus.message.labelRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = when (visibleStatus.kind) {
                    BrainDumpStatusKind.Error -> MaterialTheme.colorScheme.error
                    BrainDumpStatusKind.Warning -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (visibleStatus.canUndo) {
                TextButton(onClick = onUndo) { Text(stringResource(R.string.core_brain_dump_undo)) }
            }
            if (visibleStatus.canRetry) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.core_brain_dump_retry)) }
            }
            if (visibleStatus.message == BrainDumpStatusMessage.NotificationsBlocked) {
                TextButton(onClick = turnOnNotifications) { Text(stringResource(R.string.settings_turn_on)) }
            }
        }
    }
}

@Composable
internal fun BrainDumpCompletionSummary(
    counts: BrainDumpCompletionCounts,
    status: BrainDumpStatus?,
    warning: BrainDumpStatus?,
    actionInProgress: Boolean,
    onUndo: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.core_brain_dump_thoughts_sorted),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.core_brain_dump_saved_count, counts.saved),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.core_brain_dump_inbox_count, counts.keptInInbox),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.core_brain_dump_skipped_count, counts.skipped),
            style = MaterialTheme.typography.bodyMedium,
        )
        BrainDumpInlineStatus(
            status = status,
            warning = warning,
            onUndo = onUndo,
            onRetry = onRetry,
            modifier = Modifier.padding(top = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp))
        Button(
            onClick = onClose,
            enabled = !actionInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            Text(stringResource(R.string.core_close))
        }
    }
}

@Composable
private fun SuggestedItemType.labelRes(): Int = when (this) {
    SuggestedItemType.Note -> R.string.core_note
    SuggestedItemType.Task -> R.string.core_task
    SuggestedItemType.Reminder -> R.string.core_reminder
    SuggestedItemType.MondayItem -> R.string.core_capture_monday_item
}

private fun SuggestedItemType.primaryActionLabelRes(): Int = when (this) {
    SuggestedItemType.Note -> R.string.core_capture_action_save_as_note
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.core_capture_set_up_task
    SuggestedItemType.Reminder -> R.string.core_capture_set_up_reminder
}

@Composable
private fun brainDumpSpaceName(space: CaptureSpaceOption?): String = when (space?.name) {
    "Inbox", null -> stringResource(R.string.core_inbox)
    else -> localizedSpaceName(requireNotNull(space).name)
}

private fun BrainDumpStatusMessage.labelRes(): Int = when (this) {
    BrainDumpStatusMessage.NoteSaved -> R.string.core_home_message_note_saved
    BrainDumpStatusMessage.TaskCreated -> R.string.core_home_message_task_created
    BrainDumpStatusMessage.ReminderCreated -> ReminderSaveOutcome.Saved.messageRes()
    BrainDumpStatusMessage.KeptInInbox -> R.string.core_home_message_kept_in_inbox
    BrainDumpStatusMessage.ThoughtSkipped -> R.string.core_brain_dump_status_skipped
    BrainDumpStatusMessage.SaveFailed -> R.string.core_home_message_brain_dump_item_save_failed
    BrainDumpStatusMessage.NotificationAttention -> ReminderSaveOutcome.SavedNotScheduled.messageRes()
    BrainDumpStatusMessage.NotificationsBlocked -> ReminderSaveOutcome.SavedNotificationsBlocked.messageRes()
    BrainDumpStatusMessage.NeedsTime -> R.string.brain_dump_needs_time_status
}

private fun showBrainDumpDateTimePicker(
    context: Context,
    initialValue: Long?,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = initialValue
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: LocalDateTime.now(zone).plusHours(1).withSecond(0).withNano(0)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    onSelected(
                        LocalDateTime.of(year, month + 1, day, hour, minute)
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli(),
                    )
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
