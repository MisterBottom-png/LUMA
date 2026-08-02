@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.orbit.app.ui.screens.item

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.LumaModalBottomSheet
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.components.orbitScrollEdgeFade
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.reminders.reminderOffsetLabel
import com.orbit.app.reminders.reminderOffsetOptions
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class DetailSheet { Type, Schedule, LifeState, Space, Notification }

internal enum class ItemDetailBackAction { CancelEditing, NavigateUp }

internal fun itemDetailBackAction(isEditing: Boolean): ItemDetailBackAction =
    if (isEditing) ItemDetailBackAction.CancelEditing else ItemDetailBackAction.NavigateUp

@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onTypeChanged: (ItemDetailType, Long) -> Unit = { _, _ -> },
    onResumeBrainDump: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.core_action_undo)
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.closeAfterDelete) { if (state.closeAfterDelete) onBack() }
    LaunchedEffect(state.convertedToType) {
        state.convertedToType?.let { onTypeChanged(it, state.itemId) }
    }
    LaunchedEffect(state.message, state.archiveUndoOperationId, state.scheduleUndoOperationId) {
        state.message?.let { message ->
            val archiveId = state.archiveUndoOperationId
            val scheduleId = state.scheduleUndoOperationId
            val result = snackbarHostState.showSnackbar(
                message,
                actionLabel = if (archiveId != null || scheduleId != null) undoLabel else null,
                duration = SnackbarDuration.Short,
            )
            when {
                result == SnackbarResult.ActionPerformed && archiveId != null -> viewModel.undoArchive(archiveId)
                result == SnackbarResult.ActionPerformed && scheduleId != null -> viewModel.undoSchedule(scheduleId)
                else -> viewModel.messageShown(archiveId, scheduleId)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ItemDetailContent(
            state = state,
            timeFormat = timeFormat,
            onBack = onBack,
            onSave = viewModel::save,
            onToggleComplete = viewModel::toggleComplete,
            onRestore = viewModel::restore,
            onSetTaskStatus = viewModel::setTaskStatus,
            onMakeSmaller = viewModel::makeSmaller,
            onCreateTinyTask = viewModel::createTinyTask,
            onDismissTinyAction = viewModel::dismissTinyAction,
            onArchive = viewModel::archive,
            onUpdateSchedule = viewModel::updateSchedule,
            onUpdateSpace = viewModel::updateSpace,
            onUpdateNotificationOffset = viewModel::updateNotificationOffset,
            onSetNotificationEnabled = viewModel::setNotificationEnabled,
            onChangeType = viewModel::changeType,
            onDelete = { confirmDelete = true },
            onResumeBrainDump = { onResumeBrainDump(state.itemId) },
        )
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                modifier = Modifier.calmPressHaptics(),
                title = { Text(stringResource(R.string.core_item_detail_delete_item_title)) },
                text = { Text(stringResource(R.string.core_item_detail_delete_item_message)) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = false; viewModel.deleteProtected() }) {
                        Text(stringResource(R.string.core_action_delete))
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.core_action_cancel)) } },
            )
        }
        SnackbarHost(
            snackbarHostState,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}

@Composable
private fun ItemDetailContent(
    state: ItemDetailUiState,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onSave: (String, String, Long?) -> Unit,
    onToggleComplete: () -> Unit,
    onRestore: () -> Unit,
    onSetTaskStatus: (TaskStatus) -> Unit,
    onMakeSmaller: () -> Unit,
    onCreateTinyTask: () -> Unit,
    onDismissTinyAction: () -> Unit,
    onArchive: () -> Unit,
    onUpdateSchedule: (ItemSchedule) -> Unit,
    onUpdateSpace: (Long?) -> Unit,
    onUpdateNotificationOffset: (Long) -> Unit,
    onSetNotificationEnabled: (Boolean) -> Unit,
    onChangeType: (ItemDetailType, Long?) -> Unit,
    onDelete: () -> Unit,
    onResumeBrainDump: () -> Unit,
) {
    var isEditing by rememberSaveable(state.itemId) { mutableStateOf(false) }
    var title by rememberSaveable(state.itemId) { mutableStateOf(state.title) }
    var body by rememberSaveable(state.itemId) { mutableStateOf(state.body) }
    var openSheet by rememberSaveable { mutableStateOf<DetailSheet?>(null) }
    var pendingReminderConversion by rememberSaveable { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveCompletedAt) {
        if (state.saveCompletedAt != null) isEditing = false
    }
    LaunchedEffect(state.title, state.body, isEditing) {
        if (!isEditing) { title = state.title; body = state.body }
    }
    val cancelEditing = {
        title = state.title
        body = state.body
        isEditing = false
    }
    val navigateBack = {
        when (itemDetailBackAction(isEditing)) {
            ItemDetailBackAction.CancelEditing -> cancelEditing()
            ItemDetailBackAction.NavigateUp -> onBack()
        }
    }

    BackHandler(enabled = itemDetailBackAction(isEditing) == ItemDetailBackAction.CancelEditing) {
        cancelEditing()
    }

    val statusBarTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 20.dp
    }
    val headerClearance = maxOf(statusBarTopPadding + 80.dp, measuredHeaderClearance)

    Box(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(top = headerClearance)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = headerClearance, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            state.isMissing -> Text(stringResource(R.string.core_item_detail_unavailable), style = MaterialTheme.typography.titleMedium)
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(itemDetailStatusLabel(state), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (state.canEditTitle) {
                        if (isEditing) {
                            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.core_item_detail_title_field)) }, singleLine = true)
                            OutlinedTextField(body, { body = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.core_item_detail_notes_field)) }, minLines = 4)
                        } else {
                            Text(state.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            if (state.body.isNotBlank()) Text(state.body, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Text(state.rawText, style = MaterialTheme.typography.bodyLarge)
                        if (state.hasPendingBrainDump) {
                            Button(
                                onClick = onResumeBrainDump,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            ) {
                                Text(stringResource(R.string.core_review_resume_brain_dump))
                            }
                        }
                    }
                }

                if (state.type != ItemDetailType.Capture) {
                    Text(stringResource(R.string.core_item_detail_details), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SoftGlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                        Column {
                            DetailRow(stringResource(R.string.core_item_detail_type), state.type.userLabel(), !isEditing) { openSheet = DetailSheet.Type }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            DetailRow(stringResource(R.string.core_item_detail_schedule), scheduleLabel(state, timeFormat), !isEditing) { openSheet = DetailSheet.Schedule }
                            if (state.type == ItemDetailType.Reminder && state.notificationEnabled != null) {
                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                                DetailRow(
                                    stringResource(R.string.core_reminder_detail_notification),
                                    stringResource(
                                        if (state.notificationEnabled == true) {
                                            R.string.core_enabled
                                        } else {
                                            R.string.core_disabled
                                        },
                                    ) + " · " + reminderOffsetLabel(state.notificationOffsetMinutes ?: 0L),
                                    !isEditing,
                                ) { openSheet = DetailSheet.Notification }
                            }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            DetailRow(
                                stringResource(R.string.core_item_detail_life_state),
                                state.taskStatus?.lifeStateLabel() ?: stringResource(R.string.core_item_detail_not_applicable),
                                !isEditing && state.type == ItemDetailType.Task,
                            ) { openSheet = DetailSheet.LifeState }
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            DetailRow(stringResource(R.string.core_item_detail_space), state.spaceName(), !isEditing) { openSheet = DetailSheet.Space }
                        }
                    }
                }

                TinyActionCard(state, onMakeSmaller, onCreateTinyTask, onDismissTinyAction)

                if (state.isArchived || state.canComplete) {
                    Button(
                        onClick = if (state.isArchived) onRestore else onToggleComplete,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(if (state.isArchived || state.isComplete) Icons.Rounded.Restore else Icons.Rounded.CheckCircle, null)
                        Text(
                            when {
                                state.isArchived -> stringResource(R.string.core_action_restore)
                                state.isComplete -> stringResource(R.string.core_action_reopen)
                                else -> stringResource(R.string.core_action_complete)
                            },
                            Modifier.padding(start = 8.dp),
                        )
                    }
                }

                val metadata = listOfNotNull(
                    state.createdAt?.let { stringResource(R.string.core_item_detail_created, timeFormat.formatDateWithYear(it)) },
                    state.updatedAt?.let { stringResource(R.string.core_item_detail_updated, timeFormat.formatDateWithYear(it)) },
                ).joinToString(stringResource(R.string.core_metadata_dot_separator))
                if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = navigateBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.core_back))
            }
            Text(
                stringResource(R.string.core_item_detail_title),
                Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!state.isLoading && !state.isMissing && state.canEditTitle) {
                if (isEditing) {
                    TextButton(onClick = cancelEditing) { Text(stringResource(R.string.core_action_cancel)) }
                    TextButton(onClick = { onSave(title, body, state.spaceId) }) { Text(stringResource(R.string.core_action_save)) }
                } else {
                    IconButton(onClick = { isEditing = true }) { Icon(Icons.Rounded.Edit, stringResource(R.string.core_item_detail_edit)) }
                }
            }
            if (!isEditing) Box {
                IconButton(onClick = { overflowOpen = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.core_item_detail_more_actions)) }
                DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                    if (state.canArchive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.core_action_archive)) },
                            onClick = { overflowOpen = false; onArchive() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.core_action_delete)) },
                        onClick = { overflowOpen = false; onDelete() },
                    )
                }
            }
        }
    }

    when (openSheet) {
        DetailSheet.Type -> TypeSheet(state.type, onDismiss = { openSheet = null }) { target ->
            openSheet = null
            if (target == ItemDetailType.Reminder && state.scheduledAt == null) {
                pendingReminderConversion = true
                openSheet = DetailSheet.Schedule
            } else onChangeType(target, state.scheduledAt)
        }
        DetailSheet.Schedule -> ScheduleSheet(
            state = state,
            timeFormat = timeFormat,
            requiresReminderTime = pendingReminderConversion,
            onDismiss = { pendingReminderConversion = false; openSheet = null },
            onSchedule = { schedule ->
                if (pendingReminderConversion) {
                    val dueAt = (schedule as? ItemSchedule.Timed)?.epochMillis ?: return@ScheduleSheet
                    pendingReminderConversion = false
                    openSheet = null
                    onChangeType(ItemDetailType.Reminder, dueAt)
                } else {
                    openSheet = null
                    onUpdateSchedule(schedule)
                }
            },
        )
        DetailSheet.LifeState -> LifeStateSheet(state.taskStatus, { openSheet = null }) {
            openSheet = null; onSetTaskStatus(it)
        }
        DetailSheet.Notification -> NotificationSheet(
            offsetMinutes = state.notificationOffsetMinutes ?: 0L,
            notificationEnabled = state.notificationEnabled ?: true,
            onDismiss = { openSheet = null },
            onOffsetSelected = {
                openSheet = null
                onUpdateNotificationOffset(it)
            },
            onEnabledChanged = {
                onSetNotificationEnabled(it)
            },
        )
        DetailSheet.Space -> ChoiceSheet(
            title = stringResource(R.string.core_item_detail_space),
            choices = listOf(null to stringResource(R.string.core_inbox)) + state.spaces.map { it.id to it.name },
            selected = state.spaceId,
            onDismiss = { openSheet = null },
        ) { openSheet = null; onUpdateSpace(it) }
        null -> Unit
    }
}

@Composable
private fun DetailRow(label: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        if (enabled) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(R.string.core_item_detail_options, label))
    }
}

@Composable
private fun TypeSheet(selected: ItemDetailType, onDismiss: () -> Unit, onSelected: (ItemDetailType) -> Unit) {
    LumaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.core_item_detail_type))
        listOf(ItemDetailType.Task, ItemDetailType.Reminder, ItemDetailType.Note).forEach { type ->
            SelectionRow(type.userLabel(), type == selected) { onSelected(type) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun LifeStateSheet(selected: TaskStatus?, onDismiss: () -> Unit, onSelected: (TaskStatus) -> Unit) {
    LumaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.core_item_detail_life_state))
        listOf(TaskStatus.Open, TaskStatus.WaitingFor, TaskStatus.Someday, TaskStatus.Done).forEach { status ->
            SelectionRow(status.lifeStateLabel(), status == selected) { onSelected(status) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ChoiceSheet(
    title: String,
    choices: List<Pair<Long?, String>>,
    selected: Long?,
    onDismiss: () -> Unit,
    onSelected: (Long?) -> Unit,
) {
    LumaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(title)
        choices.forEach { (id, label) -> SelectionRow(label, id == selected) { onSelected(id) } }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun SelectionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { if (selected) Icon(Icons.Rounded.Check, stringResource(R.string.core_selected)) else RadioButton(false, onClick = null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick),
    )
}

@Composable
private fun SheetTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ScheduleSheet(
    state: ItemDetailUiState,
    timeFormat: OrbitTimeFormat,
    requiresReminderTime: Boolean,
    onDismiss: () -> Unit,
    onSchedule: (ItemSchedule) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val currentDate = state.scheduledDateEpochDay?.let(LocalDate::ofEpochDay)
        ?: state.scheduledAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: today
    val initialTime = state.scheduledAt ?: currentDate.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    val timedOnly = requiresReminderTime || state.type == ItemDetailType.Reminder
    LumaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(if (requiresReminderTime) R.string.core_item_detail_reminder_date_and_time else R.string.core_item_detail_schedule))
        Text(scheduleLabel(state, timeFormat), Modifier.padding(horizontal = 24.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        ListItem(
            headlineContent = { Text(stringResource(R.string.core_item_detail_choose_date_and_time)) },
            modifier = Modifier.clickable {
                showDateTimePicker(context, initialTime, timeFormat) { onSchedule(ItemSchedule.Timed(it)) }
            },
        )
        if (!timedOnly) {
            ListItem(headlineContent = { Text(stringResource(R.string.core_today)) }, modifier = Modifier.clickable { onSchedule(ItemSchedule.DateOnly(today.toEpochDay())) })
            ListItem(headlineContent = { Text(stringResource(R.string.core_tomorrow)) }, modifier = Modifier.clickable { onSchedule(ItemSchedule.DateOnly(today.plusDays(1).toEpochDay())) })
            ListItem(
                headlineContent = { Text(stringResource(R.string.core_choose_date)) },
                modifier = Modifier.clickable { showDateOnlyPicker(context, currentDate) { onSchedule(ItemSchedule.DateOnly(it.toEpochDay())) } },
            )
            if (state.scheduledAt != null) {
                ListItem(headlineContent = { Text(stringResource(R.string.core_item_detail_date_only)) }, modifier = Modifier.clickable { onSchedule(ItemSchedule.DateOnly(currentDate.toEpochDay())) })
            }
            if (state.scheduledAt != null || state.scheduledDateEpochDay != null) {
                ListItem(headlineContent = { Text(stringResource(R.string.core_item_detail_remove_schedule)) }, modifier = Modifier.clickable { onSchedule(ItemSchedule.Unscheduled) })
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotificationSheet(
    offsetMinutes: Long,
    notificationEnabled: Boolean,
    onDismiss: () -> Unit,
    onOffsetSelected: (Long) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
) {
    LumaModalBottomSheet(onDismissRequest = onDismiss) {
        SheetTitle(stringResource(R.string.core_reminder_detail_notification))
        ListItem(
            headlineContent = {
                Text(
                    stringResource(
                        if (notificationEnabled) {
                            R.string.core_reminder_detail_disable_notification
                        } else {
                            R.string.core_reminder_detail_enable_notification
                        },
                    ),
                )
            },
            trailingContent = {
                Switch(checked = notificationEnabled, onCheckedChange = onEnabledChanged)
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        )
        Text(
            stringResource(R.string.core_reminder_detail_delivery),
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reminderOffsetOptions.forEach { option ->
                val selected = option.minutes == offsetMinutes
                FilterChip(
                    selected = selected,
                    onClick = { onOffsetSelected(option.minutes) },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun TinyActionCard(
    state: ItemDetailUiState,
    onMakeSmaller: () -> Unit,
    onCreateTinyTask: () -> Unit,
    onDismissTinyAction: () -> Unit,
) {
    SoftGlassSurface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Spa, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.core_review_make_smaller), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            state.tinyActionSuggestion?.let { suggestion ->
                Text(suggestion.action, style = MaterialTheme.typography.bodyLarge)
                Row {
                    TextButton(onClick = onCreateTinyTask, enabled = !state.isCreatingTinyTask) { Text(stringResource(R.string.core_item_detail_create_task)) }
                    TextButton(onClick = onDismissTinyAction, enabled = !state.isCreatingTinyTask) { Text(stringResource(R.string.core_action_cancel)) }
                }
            } ?: run {
                Text(stringResource(R.string.core_item_detail_make_smaller_explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onMakeSmaller) { Text(stringResource(R.string.core_review_make_smaller)) }
            }
        }
    }
}

@Composable
private fun ItemDetailType.userLabel() = stringResource(when (this) {
    ItemDetailType.Note -> R.string.core_note
    ItemDetailType.Task -> R.string.core_task
    ItemDetailType.Reminder -> R.string.core_reminder
    ItemDetailType.Capture -> R.string.core_capture
})

@Composable
private fun itemDetailStatusLabel(state: ItemDetailUiState): String = when (state.type) {
    ItemDetailType.Note -> stringResource(if (state.isArchived) R.string.core_item_detail_archived_note else R.string.core_note)
    ItemDetailType.Task -> state.taskStatus?.lifeStateLabel() ?: stringResource(R.string.core_task)
    ItemDetailType.Reminder -> stringResource(if (state.isComplete) R.string.core_completed_reminder else R.string.core_reminder)
    ItemDetailType.Capture -> stringResource(R.string.core_capture)
}

@Composable
private fun TaskStatus.lifeStateLabel() = stringResource(when (this) {
    TaskStatus.Open -> R.string.core_item_detail_active
    TaskStatus.WaitingFor -> R.string.core_waiting_for
    TaskStatus.Someday -> R.string.core_someday
    TaskStatus.Done -> R.string.core_completed
    TaskStatus.Archived -> R.string.core_archived
})

@Composable
private fun ItemDetailUiState.spaceName(): String = spaceId?.let { id -> spaces.firstOrNull { it.id == id }?.name }
    ?: stringResource(R.string.core_inbox)

@Composable
private fun scheduleLabel(state: ItemDetailUiState, timeFormat: OrbitTimeFormat): String = when {
    state.type == ItemDetailType.Reminder && state.dueAt != null -> timeFormat.formatDateTime(state.dueAt)
    state.scheduledDateEpochDay != null -> LocalDate.ofEpochDay(state.scheduledDateEpochDay).format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(LocalConfiguration.current.locales[0]),
    )
    state.scheduledAt != null -> timeFormat.formatDateTime(state.scheduledAt)
    else -> stringResource(R.string.core_item_detail_not_scheduled)
}

private fun showDateTimePicker(context: Context, initialValue: Long, timeFormat: OrbitTimeFormat, onSelected: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(initialValue).atZone(zone).toLocalDateTime()
    DatePickerDialog(context, { _, year, month, day ->
        TimePickerDialog(context, { _, hour, minute ->
            onSelected(LocalDateTime.of(year, month + 1, day, hour, minute).atZone(zone).toInstant().toEpochMilli())
        }, initial.hour, initial.minute, timeFormat.uses24HourClock).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

private fun showDateOnlyPicker(context: Context, initial: LocalDate, onSelected: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}
