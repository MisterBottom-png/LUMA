package com.orbit.app.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.BrainDumpSuggestion
import com.orbit.app.domain.analyzer.CaptureConfidence
import com.orbit.app.domain.analyzer.confidenceLevel
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class ActionSetup { Task, Reminder }

internal enum class CaptureDecisionAction(val label: String, val primaryLabel: String) {
    SaveNote("Save note", "Save as note"),
    CreateTask("Create task", "Create task"),
    CreateReminder("Remind me", "Set reminder"),
    KeepInbox("Keep in Inbox", "Keep in Inbox"),
    SendMonday("Send to Monday", "Send to Monday"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CaptureSuggestionSheet(
    suggestion: CaptureSuggestion,
    timeFormat: OrbitTimeFormat,
    brainDumpHandledItemIds: Set<String>,
    mondayConfigured: Boolean,
    isPerformingAction: Boolean,
    onSaveNote: (title: String, spaceId: Long?) -> Unit,
    onCreateTask: (title: String, dueAt: Long?, spaceId: Long?) -> Unit,
    onCreateReminder: (title: String, dueAt: Long, spaceId: Long?, linkedTaskId: Long?) -> Unit,
    onSaveBrainDumpItem: (BrainDumpSuggestion, String, SuggestedItemType, Long?) -> Unit,
    onKeepBrainDumpItemInInbox: (BrainDumpSuggestion, String) -> Unit,
    onSkipBrainDumpItem: (BrainDumpSuggestion) -> Unit,
    onKeepInInbox: () -> Unit,
    onCancel: () -> Unit,
    onSendToMonday: (() -> Unit)? = null,
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
    val hapticFeedback = LocalHapticFeedback.current
    val confirmAction: (() -> Unit) -> Unit = { action ->
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        action()
    }
    val selectedAction = CaptureDecisionAction.valueOf(selectedActionName)

    ModalBottomSheet(
        onDismissRequest = { if (!isPerformingAction) onCancel() },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        scrimColor = Color.Black.copy(alpha = 0.18f),
        tonalElevation = 0.dp,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            style = GlassSurfaceStyle.Sheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp),
            ) {
                SheetHeading()
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
                            handledItemIds = brainDumpHandledItemIds,
                            isPerformingAction = isPerformingAction,
                            onSaveItem = { item, title, type, spaceId ->
                                confirmAction {
                                    onSaveBrainDumpItem(item, title, type, spaceId)
                                }
                            },
                            onKeepItemInInbox = { item, text ->
                                confirmAction {
                                    onKeepBrainDumpItemInInbox(item, text)
                                }
                            },
                            onSkipItem = { item ->
                                confirmAction {
                                    onSkipBrainDumpItem(item)
                                }
                            },
                            onCancel = onCancel,
                        )
                    } else {
                        SuggestedActions(
                            suggestion = suggestion,
                            mondayConfigured = mondayConfigured,
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
                            onSendToMonday = onSendToMonday?.let { sendToMonday ->
                                {
                                    confirmAction(sendToMonday)
                                }
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
                text = "A place for this",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Nothing changes until you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarDateContextLabel(date: LocalDate) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
    ) {
        Text(
            text = "For ${date.format(formatter)}",
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
    handledItemIds: Set<String>,
    isPerformingAction: Boolean,
    onSaveItem: (BrainDumpSuggestion, String, SuggestedItemType, Long?) -> Unit,
    onKeepItemInInbox: (BrainDumpSuggestion, String) -> Unit,
    onSkipItem: (BrainDumpSuggestion) -> Unit,
    onCancel: () -> Unit,
) {
    val pendingItems = suggestion.analysis.brainDumpItems.filterNot { it.id in handledItemIds }
    val item = pendingItems.firstOrNull()

    Text(
        text = "Brain Dump",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "The full dump is saved. Review one suggested item at a time.",
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (item == null) {
        Text(
            text = "All suggestions have been handled.",
            modifier = Modifier.padding(top = 18.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
        TextButton(
            onClick = onCancel,
            enabled = !isPerformingAction,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Close")
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

    Text(
        text = "${handledCount + 1} of ${suggestion.analysis.brainDumpItems.size}",
        modifier = Modifier.padding(top = 18.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        enabled = !isPerformingAction,
        label = { Text("Suggested item") },
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
        text = "One small step: ${item.tinyNextAction}",
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
        text = "Type",
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(SuggestedItemType.Note, SuggestedItemType.Task).forEach { type ->
            ChoiceButton(
                text = type.displayName(),
                selected = selectedType == type,
                enabled = !isPerformingAction,
                onClick = { selectedTypeName = type.name },
            )
        }
    }

    Text(
        text = "Place",
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
                text = space.name,
                selected = selectedSpaceId == space.id,
                enabled = !isPerformingAction,
                onClick = { selectedSpaceId = space.id },
            )
        }
    }

    Button(
        onClick = { onSaveItem(item, title, selectedType, selectedSpaceId) },
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
            Text("Save item")
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onKeepItemInInbox(item, title) },
            enabled = !isPerformingAction,
            modifier = Modifier.weight(1f),
        ) {
            Text("Keep in Inbox")
        }
        TextButton(
            onClick = { onSkipItem(item) },
            enabled = !isPerformingAction,
            modifier = Modifier.weight(1f),
        ) {
            Text("Skip")
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(top = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
    TextButton(
        onClick = onCancel,
        enabled = !isPerformingAction,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Finish later")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedActions(
    suggestion: CaptureSuggestion,
    mondayConfigured: Boolean,
    isPerformingAction: Boolean,
    selectedAction: CaptureDecisionAction,
    selectedSpaceId: Long?,
    onActionSelected: (CaptureDecisionAction) -> Unit,
    onSpaceSelected: (Long?) -> Unit,
    onSaveNote: () -> Unit,
    onCreateTask: () -> Unit,
    onCreateReminder: () -> Unit,
    onSendToMonday: (() -> Unit)?,
    onKeepInInbox: () -> Unit,
    onCancel: () -> Unit,
) {
    val analysis = suggestion.analysis
    var showWhy by rememberSaveable(suggestion.captureId) { mutableStateOf(false) }
    var showAlternatives by rememberSaveable(suggestion.captureId) { mutableStateOf(false) }
    val selectedSpaceName = suggestion.spaceOptions
        .firstOrNull { it.id == selectedSpaceId }
        ?.name
        ?: "Inbox"

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
            text = "Analysis paused. The raw capture is saved locally.",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else if (analysis.confidenceLevel == CaptureConfidence.Low) {
        Text(
            text = "This can stay in Inbox until it becomes clearer.",
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Text(
        text = "Suggestion",
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
        SuggestionChip("${analysis.confidenceLevel.label} confidence")
        if (analysis.lifeSignal != com.orbit.app.domain.analyzer.CaptureLifeSignal.None) {
            SuggestionChip(analysis.lifeSignal.label)
        }
        if (analysis.suggestedReminderAt != null) {
            SuggestionChip(analysis.reminderPhrase ?: "Time suggested")
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
        Text(if (showWhy) "Hide why" else "Why this?")
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
                CaptureDecisionAction.SendMonday -> onSendToMonday?.invoke()
            }
        },
        enabled = !isPerformingAction &&
            (selectedAction != CaptureDecisionAction.SendMonday || (mondayConfigured && onSendToMonday != null)),
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
            Text(selectedAction.primaryLabel)
        }
    }

    TextButton(onClick = { showAlternatives = !showAlternatives }) {
        Text(if (showAlternatives) "Hide choices" else "Change action")
    }

    if (showAlternatives) {
        Text(
            text = "Action",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            decisionActions(mondayConfigured, onSendToMonday != null).forEach { action ->
                ChoiceButton(
                    text = action.label,
                    selected = selectedAction == action,
                    enabled = !isPerformingAction,
                    onClick = { onActionSelected(action) },
                )
            }
        }

        Text(
            text = "Place",
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
                    text = space.name,
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
        Text("Cancel")
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

internal fun decisionActions(
    mondayConfigured: Boolean,
    sendToMondayAvailable: Boolean,
): List<CaptureDecisionAction> = buildList {
    add(CaptureDecisionAction.SaveNote)
    add(CaptureDecisionAction.CreateTask)
    add(CaptureDecisionAction.CreateReminder)
    if (mondayConfigured && sendToMondayAvailable) {
        add(CaptureDecisionAction.SendMonday)
    }
    add(CaptureDecisionAction.KeepInbox)
}

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
        text = "Create task",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChanged,
        enabled = !isPerformingAction,
        label = { Text("Task") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    )
    Text(
        text = dueAt?.let(timeFormat::formatDate) ?: "No due date",
        modifier = Modifier.padding(top = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = {
                showTaskDatePicker(context, dueAt, onDueAtChanged)
            },
            enabled = !isPerformingAction,
        ) {
            Text(if (dueAt == null) "Add due date" else "Change due date")
        }
        if (dueAt != null) {
            TextButton(
                onClick = { onDueAtChanged(null) },
                enabled = !isPerformingAction,
            ) {
                Text("Remove")
            }
        }
    }
    ConfirmAndBackButtons(
        confirmText = "Create task",
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
        text = "Remind me",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
    )
    OutlinedTextField(
        value = title,
        onValueChange = onTitleChanged,
        enabled = !isPerformingAction,
        label = { Text("Reminder") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    )
    Text(
        text = reminderAt?.let(timeFormat::formatWeekdayDateTime) ?: "Choose a date and time",
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
        Text(if (reminderAt == null) "Choose date and time" else "Change date and time")
    }
    ConfirmAndBackButtons(
        confirmText = "Create reminder",
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
        Text("Back")
    }
}

private fun showTaskDatePicker(
    context: Context,
    initialDueAt: Long?,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = initialDueAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: LocalDate.now(zone)
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onSelected(
                LocalDate.of(year, month + 1, day)
                    .atTime(23, 59)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli(),
            )
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

private fun SuggestedItemType.displayName(): String = when (this) {
    SuggestedItemType.Note -> "Note"
    SuggestedItemType.Task -> "Task"
    SuggestedItemType.Reminder -> "Reminder"
    SuggestedItemType.MondayItem -> "Monday item"
}

private fun SuggestedItemType.brainDumpType(): SuggestedItemType = when (this) {
    SuggestedItemType.Note -> SuggestedItemType.Note
    SuggestedItemType.Task,
    SuggestedItemType.Reminder,
    SuggestedItemType.MondayItem,
    -> SuggestedItemType.Task
}
