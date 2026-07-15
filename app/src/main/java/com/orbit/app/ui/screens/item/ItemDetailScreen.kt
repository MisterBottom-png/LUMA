package com.orbit.app.ui.screens.item

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.data.local.entity.TaskStatus
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ItemDetailScreen(
    viewModel: ItemDetailViewModel,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.closeAfterDelete) {
        if (state.closeAfterDelete) onBack()
    }
    LaunchedEffect(state.message, state.archiveUndoOperationId, state.scheduleUndoOperationId) {
        state.message?.let { message ->
            val archiveUndoOperationId = state.archiveUndoOperationId
            val scheduleUndoOperationId = state.scheduleUndoOperationId
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (
                    archiveUndoOperationId != null || scheduleUndoOperationId != null
                ) {
                    "Undo"
                } else {
                    null
                },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && archiveUndoOperationId != null) {
                viewModel.undoArchive(archiveUndoOperationId)
            } else if (result == SnackbarResult.ActionPerformed && scheduleUndoOperationId != null) {
                viewModel.undoSchedule(scheduleUndoOperationId)
            } else {
                viewModel.messageShown(archiveUndoOperationId, scheduleUndoOperationId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ItemDetailContent(
            state = state,
            timeFormat = timeFormat,
            onBack = onBack,
            onSave = viewModel::save,
            onToggleComplete = viewModel::toggleComplete,
            onSetTaskStatus = viewModel::setTaskStatus,
            onMakeSmaller = viewModel::makeSmaller,
            onCreateTinyTask = viewModel::createTinyTask,
            onDismissTinyAction = viewModel::dismissTinyAction,
            onArchive = viewModel::archive,
            onUpdateSchedule = viewModel::updateSchedule,
            onDelete = { confirmDelete = true },
        )

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete item?") },
                text = { Text("This removes the item from local storage. Archive when you may want it later.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            viewModel.deleteProtected()
                        },
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemDetailContent(
    state: ItemDetailUiState,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onSave: (String, String, Long?) -> Unit,
    onToggleComplete: () -> Unit,
    onSetTaskStatus: (TaskStatus) -> Unit,
    onMakeSmaller: () -> Unit,
    onCreateTinyTask: () -> Unit,
    onDismissTinyAction: () -> Unit,
    onArchive: () -> Unit,
    onUpdateSchedule: (ItemSchedule) -> Unit,
    onDelete: () -> Unit,
) {
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    var title by rememberSaveable(state.type, state.itemId, state.title) {
        mutableStateOf(state.title)
    }
    var body by rememberSaveable(state.type, state.itemId, state.body) {
        mutableStateOf(state.body)
    }
    var spaceId by rememberSaveable(state.type, state.itemId, state.spaceId) {
        mutableStateOf(state.spaceId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(
                top = 26.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Item detail",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))

            state.isMissing -> SoftGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = "This item is no longer available.",
                    modifier = Modifier.padding(22.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            else -> {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    style = GlassSurfaceStyle.Prominent,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            text = state.statusLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )

                        if (state.canEditTitle) {
                            OutlinedTextField(
                                value = title,
                                onValueChange = { title = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Title") },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Rounded.Edit, contentDescription = null)
                                },
                            )
                        } else {
                            DetailBlock("Original capture", state.rawText)
                        }

                        if (state.canEditBody) {
                            OutlinedTextField(
                                value = body,
                                onValueChange = { body = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (state.type == ItemDetailType.Task) "Notes" else "Body") },
                                minLines = 3,
                            )
                        } else {
                            Text(
                                text = "Raw capture text is preserved. You can change placement or status here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        if (state.type == ItemDetailType.Note || state.type == ItemDetailType.Task) {
                            ScheduleControls(
                                state = state,
                                timeFormat = timeFormat,
                                onUpdateSchedule = onUpdateSchedule,
                            )
                        }

                        if (state.type == ItemDetailType.Task) {
                            Text(
                                text = "Life state",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TaskStatusChip(
                                    label = "Active",
                                    selected = state.taskStatus == TaskStatus.Open,
                                    onClick = { onSetTaskStatus(TaskStatus.Open) },
                                )
                                TaskStatusChip(
                                    label = "Waiting for",
                                    selected = state.taskStatus == TaskStatus.WaitingFor,
                                    onClick = { onSetTaskStatus(TaskStatus.WaitingFor) },
                                )
                                TaskStatusChip(
                                    label = "Someday",
                                    selected = state.taskStatus == TaskStatus.Someday,
                                    onClick = { onSetTaskStatus(TaskStatus.Someday) },
                                )
                            }
                        }

                        Text(
                            text = "Space",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                                FilterChip(
                                    selected = spaceId == null,
                                    onClick = { spaceId = null },
                                    label = { Text("Inbox") },
                                    leadingIcon = {
                                        Icon(Icons.Rounded.Inbox, contentDescription = null)
                                    },
                                    colors = readableChipColors(),
                                )
                            state.spaces.forEach { space ->
                                FilterChip(
                                    selected = spaceId == space.id,
                                    onClick = { spaceId = space.id },
                                    label = { Text(space.name) },
                                    colors = readableChipColors(),
                                )
                            }
                        }

                        Button(
                            onClick = { onSave(title, body, spaceId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Rounded.Save, contentDescription = null)
                            Text("Save", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                TinyActionCard(
                    state = state,
                    onMakeSmaller = onMakeSmaller,
                    onCreateTinyTask = onCreateTinyTask,
                    onDismissTinyAction = onDismissTinyAction,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.canComplete) {
                        OutlinedButton(
                            onClick = onToggleComplete,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                if (state.isComplete) Icons.Rounded.Restore else Icons.Rounded.CheckCircle,
                                contentDescription = null,
                            )
                            Text(
                                if (state.isComplete) "Reopen" else "Complete",
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    if (state.canArchive) {
                        OutlinedButton(
                            onClick = onArchive,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Rounded.Archive, contentDescription = null)
                            Text("Archive", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null)
                    Text("Delete", modifier = Modifier.padding(start = 8.dp))
                }

                val metadata = buildString {
                    state.createdAt?.let { append("Created ${timeFormat.formatDateWithYear(it)}") }
                    state.updatedAt?.let {
                        if (isNotBlank()) append(" - ")
                        append("Updated ${timeFormat.formatDateWithYear(it)}")
                    }
                }
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TinyActionCard(
    state: ItemDetailUiState,
    onMakeSmaller: () -> Unit,
    onCreateTinyTask: () -> Unit,
    onDismissTinyAction: () -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Spa,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Make smaller",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            state.tinyActionSuggestion?.let { suggestion ->
                Text(
                    text = suggestion.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = suggestion.action,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCreateTinyTask,
                        enabled = !state.isCreatingTinyTask,
                    ) {
                        if (state.isCreatingTinyTask) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text("Create task")
                    }
                    TextButton(
                        onClick = onDismissTinyAction,
                        enabled = !state.isCreatingTinyTask,
                    ) {
                        Text("Cancel")
                    }
                }
            } ?: Text(
                text = "Turn this into one tiny next action. Nothing changes until you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.tinyActionSuggestion == null) {
                OutlinedButton(
                    onClick = onMakeSmaller,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Spa, contentDescription = null)
                    Text("Make smaller", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TaskStatusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = readableChipColors(),
    )
}

@Composable
private fun readableChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun DetailBlock(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun showDateTimePicker(
    context: Context,
    initialValue: Long,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(initialValue)
        .atZone(zone)
        .toLocalDateTime()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleControls(
    state: ItemDetailUiState,
    timeFormat: OrbitTimeFormat,
    onUpdateSchedule: (ItemSchedule) -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val timedDate = state.scheduledAt?.let {
        Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
    }
    val scheduledDate = state.scheduledDateEpochDay?.let(LocalDate::ofEpochDay) ?: timedDate
    val today = LocalDate.now(zone)
    val currentLabel = when {
        state.scheduledDateEpochDay != null -> LocalDate.ofEpochDay(state.scheduledDateEpochDay)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        state.scheduledAt != null -> timeFormat.formatDateTime(state.scheduledAt)
        else -> "Not scheduled"
    }
    val pickerInitial = state.scheduledAt ?: (scheduledDate ?: today)
        .atTime(9, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    Text(
        text = "Schedule",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = currentLabel,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { onUpdateSchedule(ItemSchedule.DateOnly(today.toEpochDay())) },
        ) {
            Text("Today")
        }
        OutlinedButton(
            onClick = { onUpdateSchedule(ItemSchedule.DateOnly(today.plusDays(1).toEpochDay())) },
        ) {
            Text("Tomorrow")
        }
        OutlinedButton(
            onClick = {
                showDateOnlyPicker(context, scheduledDate ?: today) { date ->
                    onUpdateSchedule(ItemSchedule.DateOnly(date.toEpochDay()))
                }
            },
        ) {
            Text("Choose date")
        }
        OutlinedButton(
            onClick = {
                showDateTimePicker(
                    context = context,
                    initialValue = pickerInitial,
                    timeFormat = timeFormat,
                    onSelected = { onUpdateSchedule(ItemSchedule.Timed(it)) },
                )
            },
        ) {
            Text(if (state.scheduledAt == null) "Add time" else "Change time")
        }
        if (state.scheduledAt != null) {
            TextButton(
                onClick = {
                    val date = timedDate ?: today
                    onUpdateSchedule(ItemSchedule.DateOnly(date.toEpochDay()))
                },
            ) {
                Text("Date only")
            }
        }
        if (state.scheduledDateEpochDay != null || state.scheduledAt != null) {
            TextButton(onClick = { onUpdateSchedule(ItemSchedule.Unscheduled) }) {
                Text("Remove schedule")
            }
        }
    }
}

private fun showDateOnlyPicker(
    context: Context,
    initial: LocalDate,
    onSelected: (LocalDate) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day)) },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}
