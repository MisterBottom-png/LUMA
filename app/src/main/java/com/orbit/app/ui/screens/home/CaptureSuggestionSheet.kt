package com.orbit.app.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.R
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.ui.components.LumaModalBottomSheet
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal enum class ActionSetup { Task, Reminder }

internal fun hasNestedCaptureSetup(actionSetup: ActionSetup?): Boolean = actionSetup != null

internal fun hasNestedBrainDumpSetup(
    showTaskSetup: Boolean,
    showReminderSetup: Boolean,
): Boolean = showTaskSetup || showReminderSetup

internal enum class CaptureDecisionAction(
    val labelRes: Int,
    val primaryLabelRes: Int,
) {
    SaveNote(R.string.core_capture_action_save_note, R.string.core_capture_action_save_as_note),
    CreateTask(R.string.core_capture_action_create_task, R.string.core_capture_action_create_task),
    CreateReminder(R.string.core_capture_action_remind_me, R.string.core_capture_action_set_reminder),
    KeepInbox(R.string.core_capture_action_keep_in_inbox, R.string.core_capture_action_keep_in_inbox),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSuggestionSheet(
    suggestion: CaptureSuggestion,
    timeFormat: OrbitTimeFormat,
    brainDumpHandledItemIds: Set<String>,
    isPerformingAction: Boolean,
    onSaveNote: (title: String, spaceId: Long?) -> Unit,
    onCreateTask: (title: String, dueAt: Long?, spaceId: Long?) -> Unit,
    onCreateReminder: (title: String, dueAt: Long, spaceId: Long?, linkedTaskId: Long?) -> Unit,
    onSaveBrainDumpItem: (BrainDumpSuggestion, String, SuggestedItemType, Long?, Long?) -> Unit,
    onSaveBrainDumpReminder: (BrainDumpSuggestion, String, Long, Long?) -> Unit,
    onSaveBrainDumpOriginalForLater: (BrainDumpSuggestion) -> Unit,
    onSkipBrainDumpItem: (BrainDumpSuggestion) -> Unit,
    onKeepInInbox: () -> Unit,
    onCancelBrainDump: () -> Unit,
    onCancel: () -> Unit,
) {
    val analysis = suggestion.analysis
    val calendarDateContext = suggestion.calendarDateContextEpochDay?.let {
        runCatching { LocalDate.ofEpochDay(it) }.getOrNull()
    }
    var actionSetup by rememberSaveable(suggestion.captureId) {
        mutableStateOf<ActionSetup?>(null)
    }
    var selectedActionName by rememberSaveable(suggestion.captureId) {
        mutableStateOf(defaultDecisionAction(analysis).name)
    }
    var selectedSpaceId by rememberSaveable(suggestion.captureId) {
        mutableStateOf(suggestion.suggestedSpaceId)
    }
    val noteTitle = analysis.suggestedTitle.ifBlank { analysis.rawText }
    var taskTitle by rememberSaveable(suggestion.captureId) {
        mutableStateOf(
            analysis.suggestedTitle
                .ifBlank { analysis.suggestedNextAction.removePrefix("Choose a time, then ") }
                .ifBlank { analysis.rawText },
        )
    }
    var taskDueAt by rememberSaveable(suggestion.captureId) {
        mutableStateOf(
            analysis.suggestedReminderAt ?: calendarDateContext
                ?.atTime(23, 59)
                ?.atZone(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli(),
        )
    }
    var reminderTitle by rememberSaveable(suggestion.captureId) {
        mutableStateOf(analysis.suggestedTitle.ifBlank { analysis.rawText })
    }
    var reminderAt by rememberSaveable(suggestion.captureId) {
        mutableStateOf(analysis.suggestedReminderAt)
    }
    val confirmAction: (() -> Unit) -> Unit = { action -> action() }
    val selectedAction = CaptureDecisionAction.valueOf(selectedActionName)

    BackHandler(enabled = hasNestedCaptureSetup(actionSetup)) {
        actionSetup = null
    }

    LumaModalBottomSheet(
        onDismissRequest = {
            if (!isPerformingAction) {
                if (hasNestedCaptureSetup(actionSetup)) {
                    actionSetup = null
                } else {
                    onCancel()
                }
            }
        },
        surfaceModifier = Modifier
            .fillMaxWidth()
            .calmPressHaptics(),
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp),
            ) {
                if (analysis.brainDumpItems.isEmpty()) {
                    SheetHeading()
                }
                calendarDateContext?.let { date ->
                    CalendarDateContextLabel(date)
                }
                Spacer(modifier = Modifier.height(22.dp))

                when (actionSetup) {
                    ActionSetup.Task -> TaskSetup(
                        title = taskTitle,
                        dueAt = taskDueAt,
                        timeFormat = timeFormat,
                        isPerformingAction = isPerformingAction,
                        onTitleChanged = { taskTitle = it },
                        onDueAtChanged = { taskDueAt = it },
                        onConfirm = {
                            confirmAction {
                                onCreateTask(taskTitle, taskDueAt, selectedSpaceId)
                            }
                        },
                        onBack = { actionSetup = null },
                    )

                    ActionSetup.Reminder -> ReminderSetup(
                        title = reminderTitle,
                        reminderAt = reminderAt,
                        initialDate = calendarDateContext,
                        timeFormat = timeFormat,
                        isPerformingAction = isPerformingAction,
                        onTitleChanged = { reminderTitle = it },
                        onReminderAtChanged = { reminderAt = it },
                        onConfirm = {
                            confirmAction {
                                reminderAt?.let { dueAt ->
                                    onCreateReminder(reminderTitle, dueAt, selectedSpaceId, null)
                                }
                            }
                        },
                        onBack = { actionSetup = null },
                    )

                    null -> if (analysis.brainDumpItems.isNotEmpty()) {
                        BrainDumpReview(
                            suggestion = suggestion,
                            timeFormat = timeFormat,
                            handledItemIds = brainDumpHandledItemIds,
                            isPerformingAction = isPerformingAction,
                            onSaveItem = { item, title, type, dueAt, spaceId ->
                                confirmAction {
                                    onSaveBrainDumpItem(item, title, type, dueAt, spaceId)
                                }
                            },
                            onSaveReminder = { item, title, dueAt, spaceId ->
                                confirmAction {
                                    onSaveBrainDumpReminder(item, title, dueAt, spaceId)
                                }
                            },
                            onSaveOriginalForLater = { item ->
                                confirmAction {
                                    onSaveBrainDumpOriginalForLater(item)
                                }
                            },
                            onSkipItem = { item ->
                                confirmAction {
                                    onSkipBrainDumpItem(item)
                                }
                            },
                            onFinishLater = onCancel,
                            onCancel = onCancelBrainDump,
                        )
                    } else {
                        SuggestedActions(
                            suggestion = suggestion,
                            isPerformingAction = isPerformingAction,
                            selectedAction = selectedAction,
                            selectedSpaceId = selectedSpaceId,
                            onActionSelected = { selectedActionName = it.name },
                            onSpaceSelected = { selectedSpaceId = it },
                            onSaveNote = {
                                confirmAction {
                                    onSaveNote(noteTitle, selectedSpaceId)
                                }
                            },
                            onCreateTask = {
                                selectedActionName = CaptureDecisionAction.CreateTask.name
                                actionSetup = ActionSetup.Task
                            },
                            onCreateReminder = {
                                selectedActionName = CaptureDecisionAction.CreateReminder.name
                                actionSetup = ActionSetup.Reminder
                            },
                            onKeepInInbox = {
                                confirmAction(onKeepInInbox)
                            },
                            onCancel = onCancel,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
    }
}

@Composable
private fun SheetHeading() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column {
            Text(
                text = stringResource(R.string.core_capture_suggestion_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.core_capture_suggestion_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarDateContextLabel(date: LocalDate) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
    ) {
        Text(
            text = stringResource(R.string.core_capture_for_date, date.format(formatter)),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrainDumpReview(
    suggestion: CaptureSuggestion,
    timeFormat: OrbitTimeFormat,
    handledItemIds: Set<String>,
    isPerformingAction: Boolean,
    onSaveItem: (BrainDumpSuggestion, String, SuggestedItemType, Long?, Long?) -> Unit,
    onSaveReminder: (BrainDumpSuggestion, String, Long, Long?) -> Unit,
    onSaveOriginalForLater: (BrainDumpSuggestion) -> Unit,
    onSkipItem: (BrainDumpSuggestion) -> Unit,
    onFinishLater: () -> Unit,
    onCancel: () -> Unit,
) {
    val pendingItems = suggestion.analysis.brainDumpItems.filterNot { it.id in handledItemIds }
    val item = pendingItems.firstOrNull()

    Text(
        text = stringResource(R.string.core_capture_brain_dump_title),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = stringResource(R.string.core_capture_brain_dump_subtitle),
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (item == null) {
        Text(
            text = stringResource(R.string.core_capture_all_suggestions_handled),
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onFinishLater,
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_close))
        }
        return
    }

    var title by rememberSaveable(item.id) { mutableStateOf(item.title) }
    var selectedTypeName by rememberSaveable(item.id) {
        mutableStateOf(item.suggestedType.brainDumpType().name)
    }
    var selectedSpaceId by rememberSaveable(item.id) {
        mutableStateOf(
            suggestion.spaceOptions
                .firstOrNull { it.name.equals(item.suggestedSpaceName, ignoreCase = true) }
                ?.id,
        )
    }
    val selectedType = SuggestedItemType.valueOf(selectedTypeName)
    val handledCount = handledItemIds.size
    var showTaskSetup by rememberSaveable(item.id) { mutableStateOf(false) }
    var showReminderSetup by rememberSaveable(item.id) { mutableStateOf(false) }
    var showEditDetails by rememberSaveable(item.id) { mutableStateOf(false) }
    var showDiscardRemainingConfirmation by rememberSaveable(item.id) { mutableStateOf(false) }
    var taskDueAt by rememberSaveable(item.id) { mutableStateOf(brainDumpTaskInitialDueAt(item)) }
    var reminderAt by rememberSaveable(item.id) { mutableStateOf(item.suggestedReminderAt) }

    BackHandler(enabled = showEditDetails || hasNestedBrainDumpSetup(showTaskSetup, showReminderSetup)) {
        when {
            showTaskSetup || showReminderSetup -> {
                showTaskSetup = false
                showReminderSetup = false
            }
            else -> showEditDetails = false
        }
    }

    if (showTaskSetup) {
        TaskSetup(
            title = title,
            dueAt = taskDueAt,
            timeFormat = timeFormat,
            isPerformingAction = isPerformingAction,
            onTitleChanged = { title = it },
            onDueAtChanged = { taskDueAt = it },
            onConfirm = {
                onSaveItem(item, title, SuggestedItemType.Task, taskDueAt, selectedSpaceId)
            },
            onBack = { showTaskSetup = false },
        )
        return
    }

    if (showReminderSetup) {
        ReminderSetup(
            title = title,
            reminderAt = reminderAt,
            initialDate = suggestion.calendarDateContextEpochDay?.let { epochDay ->
                runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull()
            },
            timeFormat = timeFormat,
            isPerformingAction = isPerformingAction,
            onTitleChanged = { title = it },
            onReminderAtChanged = { reminderAt = it },
            onConfirm = { reminderAt?.let { dueAt -> onSaveReminder(item, title, dueAt, selectedSpaceId) } },
            onBack = { showReminderSetup = false },
        )
        return
    }

    val saveSelectedItem = {
        when (selectedType) {
            SuggestedItemType.Task -> showTaskSetup = true
            SuggestedItemType.Reminder -> showReminderSetup = true
            else -> onSaveItem(item, title, selectedType, null, selectedSpaceId)
        }
    }
    val primaryActionLabel = when (selectedType) {
        SuggestedItemType.Task -> R.string.core_capture_set_up_task
        SuggestedItemType.Reminder -> R.string.core_capture_set_up_reminder
        else -> R.string.core_capture_action_save_as_note
    }

    Text(
        text = stringResource(
            R.string.core_capture_brain_dump_progress,
            handledCount + 1,
            suggestion.analysis.brainDumpItems.size,
        ),
        modifier = Modifier.padding(top = 18.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )

    if (!showEditDetails) {
        Text(
            text = title,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.core_capture_suggested_type, selectedType.displayName()),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = saveSelectedItem,
            enabled = title.isNotBlank() && !isPerformingAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        ) {
            if (isPerformingAction) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(primaryActionLabel))
            }
        }
        OutlinedButton(
            onClick = { showEditDetails = true },
            enabled = !isPerformingAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.core_capture_edit_details))
        }
        TextButton(
            onClick = { onSaveOriginalForLater(item) },
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_capture_save_original_for_later))
        }
        TextButton(
            onClick = { onSkipItem(item) },
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_capture_discard_suggestion))
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        TextButton(
            onClick = onFinishLater,
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.core_capture_save_progress_and_close))
        }
        TextButton(
            onClick = { showDiscardRemainingConfirmation = true },
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.core_capture_discard_remaining),
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (showDiscardRemainingConfirmation) {
            AlertDialog(
                onDismissRequest = { showDiscardRemainingConfirmation = false },
                title = { Text(stringResource(R.string.core_capture_discard_remaining_title)) },
                text = { Text(stringResource(R.string.core_capture_discard_remaining_message)) },
                confirmButton = {
                    TextButton(onClick = onCancel) {
                        Text(
                            text = stringResource(R.string.core_capture_discard_remaining),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDiscardRemainingConfirmation = false }) {
                        Text(stringResource(R.string.core_cancel))
                    }
                },
            )
        }
        return
    }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        enabled = !isPerformingAction,
        label = { Text(stringResource(R.string.core_capture_suggested_item)) },
        minLines = 2,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
    )
    Text(
        text = item.rawText,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.core_capture_one_small_step, item.tinyNextAction),
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        text = item.reason,
        modifier = Modifier.padding(top = 5.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Text(
        text = stringResource(R.string.core_type),
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(SuggestedItemType.Note, SuggestedItemType.Task, SuggestedItemType.Reminder).forEach { type ->
            ChoiceButton(
                text = type.displayName(),
                selected = selectedType == type,
                enabled = !isPerformingAction,
                onClick = { selectedTypeName = type.name },
            )
        }
    }

    Text(
        text = stringResource(R.string.core_place),
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestion.spaceOptions.forEach { space ->
            ChoiceButton(
                text = localizedSpaceName(space.name),
                selected = selectedSpaceId == space.id,
                enabled = !isPerformingAction,
                onClick = { selectedSpaceId = space.id },
            )
        }
    }

    Button(
        onClick = saveSelectedItem,
        enabled = title.isNotBlank() && !isPerformingAction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        if (isPerformingAction) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(primaryActionLabel))
        }
    }
    TextButton(
        onClick = { showEditDetails = false },
        enabled = !isPerformingAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.core_capture_back_to_suggestion))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedActions(
    suggestion: CaptureSuggestion,
    isPerformingAction: Boolean,
    selectedAction: CaptureDecisionAction,
    selectedSpaceId: Long?,
    onActionSelected: (CaptureDecisionAction) -> Unit,
    onSpaceSelected: (Long?) -> Unit,
    onSaveNote: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateReminder: () -> Unit,
    onKeepInInbox: () -> Unit,
    onCancel: () -> Unit,
) {
    val analysis = suggestion.analysis
    var showWhy by rememberSaveable(suggestion.captureId) { mutableStateOf(false) }
    var showAlternatives by rememberSaveable(suggestion.captureId) { mutableStateOf(false) }
    val selectedSpaceName = suggestion.spaceOptions
        .firstOrNull { it.id == selectedSpaceId }
        ?.name
        ?: stringResource(R.string.core_inbox)

    Text(
        text = analysis.suggestedTitle.ifBlank { analysis.rawText },
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (analysis.summary.isNotBlank() && analysis.summary != analysis.suggestedTitle) {
        Text(
            text = analysis.summary,
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (analysis.analyzerFailed) {
        Text(
            text = stringResource(R.string.core_capture_analysis_paused),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (analysis.confidenceLevel == CaptureConfidence.Low) {
        Text(
            text = stringResource(R.string.core_capture_keep_until_clearer),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = stringResource(R.string.core_capture_suggestion),
        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(analysis.analyzerSource.label)
        SuggestionChip(analysis.suggestedType.displayName())
        SuggestionChip(selectedSpaceName)
        SuggestionChip(stringResource(R.string.core_capture_confidence, analysis.confidenceLevel.label))
        if (analysis.lifeSignal != com.orbit.app.domain.analyzer.CaptureLifeSignal.None) {
            SuggestionChip(analysis.lifeSignal.label)
        }
        if (analysis.suggestedReminderAt != null) {
            SuggestionChip(analysis.reminderPhrase ?: stringResource(R.string.core_capture_time_suggested))
        }
        analysis.suggestionChips
            .filterNot { chip ->
                chip.equals(analysis.suggestedType.displayName(), ignoreCase = true) ||
                    chip.equals(selectedSpaceName, ignoreCase = true)
            }
            .take(3)
            .forEach { chip -> SuggestionChip(chip) }
    }

    TextButton(
        onClick = { showWhy = !showWhy },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Text(stringResource(if (showWhy) R.string.core_capture_hide_why else R.string.core_capture_why_this))
    }
    if (showWhy) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = analysis.typeReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = analysis.spaceReason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Button(
        onClick = {
            when (selectedAction) {
                CaptureDecisionAction.SaveNote -> onSaveNote()
                CaptureDecisionAction.CreateTask -> onCreateTask()
                CaptureDecisionAction.CreateReminder -> onCreateReminder()
                CaptureDecisionAction.KeepInbox -> onKeepInInbox()
            }
        },
        enabled = !isPerformingAction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        if (isPerformingAction) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(stringResource(selectedAction.primaryLabelRes))
        }
    }

    TextButton(onClick = { showAlternatives = !showAlternatives }) {
        Text(stringResource(if (showAlternatives) R.string.core_capture_hide_choices else R.string.core_capture_change_action))
    }

    if (showAlternatives) {
        Text(
            text = stringResource(R.string.core_action),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            decisionActions().forEach { action ->
                ChoiceButton(
                    text = stringResource(action.labelRes),
                    selected = selectedAction == action,
                    enabled = !isPerformingAction,
                    onClick = { onActionSelected(action) },
                )
            }
        }

        Text(
            text = stringResource(R.string.core_place),
            modifier = Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestion.spaceOptions.forEach { space ->
                ChoiceButton(
                    text = localizedSpaceName(space.name),
                    selected = selectedSpaceId == space.id,
                    enabled = !isPerformingAction,
                    onClick = { onSpaceSelected(space.id) },
                )
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(top = 14.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
    TextButton(
        onClick = onCancel,
        enabled = !isPerformingAction,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(stringResource(R.string.core_cancel))
    }
}

@Composable
private fun SuggestionChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(text)
        }
    }
}

private fun defaultDecisionAction(analysis: com.orbit.app.domain.analyzer.CaptureAnalysis): CaptureDecisionAction =
    when {
        analysis.confidenceLevel == CaptureConfidence.Low -> CaptureDecisionAction.KeepInbox
        analysis.suggestedType == SuggestedItemType.Task -> CaptureDecisionAction.CreateTask
        analysis.suggestedType == SuggestedItemType.Reminder -> CaptureDecisionAction.CreateReminder
        else -> CaptureDecisionAction.SaveNote
    }

internal fun decisionActions(): List<CaptureDecisionAction> = listOf(
    CaptureDecisionAction.SaveNote,
    CaptureDecisionAction.CreateTask,
    CaptureDecisionAction.CreateReminder,
    CaptureDecisionAction.KeepInbox,
)

internal fun brainDumpTaskInitialDueAt(item: BrainDumpSuggestion): Long? = item.suggestedReminderAt

internal fun taskDueAtLabel(dueAt: Long?, timeFormat: OrbitTimeFormat): String? =
    dueAt?.let(timeFormat::formatWeekdayDateTime)

@Composable
private fun TaskSetup(
    title: String,
    dueAt: Long?,
    timeFormat: OrbitTimeFormat,
    isPerformingAction: Boolean,
    onTitleChanged: (String) -> Unit,
    onDueAtChanged: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.core_capture_action_create_task),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChanged,
        enabled = !isPerformingAction,
        label = { Text(stringResource(R.string.core_task)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    )
    Text(
        text = taskDueAtLabel(dueAt, timeFormat) ?: stringResource(R.string.core_capture_no_due_date),
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = {
                showTaskDateTimePicker(context, dueAt, timeFormat, onDueAtChanged)
            },
            enabled = !isPerformingAction,
        ) {
            Text(stringResource(if (dueAt == null) R.string.core_capture_add_due_date_time else R.string.core_capture_change_date_time))
        }
        if (dueAt != null) {
            TextButton(
                onClick = { onDueAtChanged(null) },
                enabled = !isPerformingAction,
            ) {
                Text(stringResource(R.string.core_remove))
            }
        }
    }
    ConfirmAndBackButtons(
        confirmText = stringResource(R.string.core_capture_action_create_task),
        confirmEnabled = title.isNotBlank() && !isPerformingAction,
        isPerformingAction = isPerformingAction,
        onConfirm = onConfirm,
        onBack = onBack,
    )
}

@Composable
private fun ReminderSetup(
    title: String,
    reminderAt: Long?,
    initialDate: LocalDate?,
    timeFormat: OrbitTimeFormat,
    isPerformingAction: Boolean,
    onTitleChanged: (String) -> Unit,
    onReminderAtChanged: (Long) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.core_capture_action_remind_me),
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChanged,
        enabled = !isPerformingAction,
        label = { Text(stringResource(R.string.core_reminder)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    )
    Text(
        text = reminderAt?.let(timeFormat::formatWeekdayDateTime)
            ?: stringResource(R.string.core_capture_choose_date_time),
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    TextButton(
        onClick = {
            showReminderDateTimePicker(
                context,
                reminderAt,
                initialDate,
                timeFormat,
                onReminderAtChanged,
            )
        },
        enabled = !isPerformingAction,
    ) {
        Text(stringResource(if (reminderAt == null) R.string.core_capture_choose_date_time else R.string.core_capture_change_date_time))
    }
    ConfirmAndBackButtons(
        confirmText = stringResource(R.string.core_capture_create_reminder),
        confirmEnabled = title.isNotBlank() && reminderAt != null && !isPerformingAction,
        isPerformingAction = isPerformingAction,
        onConfirm = onConfirm,
        onBack = onBack,
    )
}

@Composable
private fun ConfirmAndBackButtons(
    confirmText: String,
    confirmEnabled: Boolean,
    isPerformingAction: Boolean,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    Button(
        onClick = onConfirm,
        enabled = confirmEnabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        if (isPerformingAction) {
            CircularProgressIndicator(
                modifier = Modifier.height(20.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Text(confirmText)
        }
    }
    TextButton(
        onClick = onBack,
        enabled = !isPerformingAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.core_back))
    }
}

private fun showTaskDateTimePicker(
    context: Context,
    initialDueAt: Long?,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = initialDueAt
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

private fun showReminderDateTimePicker(
    context: Context,
    initialReminderAt: Long?,
    initialDate: LocalDate?,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = initialReminderAt
        ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }
        ?: initialDate?.atTime(9, 0)
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

@Composable
private fun SuggestedItemType.displayName(): String = stringResource(
    when (this) {
        SuggestedItemType.Note -> R.string.core_note
        SuggestedItemType.Task -> R.string.core_task
        SuggestedItemType.Reminder -> R.string.core_reminder
        SuggestedItemType.MondayItem -> R.string.core_capture_monday_item
    },
)

private fun SuggestedItemType.brainDumpType(): SuggestedItemType = when (this) {
    SuggestedItemType.Note -> SuggestedItemType.Note
    SuggestedItemType.Task -> SuggestedItemType.Task
    SuggestedItemType.Reminder -> SuggestedItemType.Reminder
    SuggestedItemType.MondayItem -> SuggestedItemType.Task
}
