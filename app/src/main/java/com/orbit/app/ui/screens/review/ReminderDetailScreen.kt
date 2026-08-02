package com.orbit.app.ui.screens.review

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.reminders.reminderOffsetLabel
import com.orbit.app.reminders.reminderOffsetOptions
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderDetailScreen(
    viewModel: ReminderDetailViewModel,
    timeFormat: OrbitTimeFormat,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onDeleted()
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.errorShown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(
                top = 36.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.core_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        when {
            state.isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            state.reminder == null -> SoftGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Text(
                    text = stringResource(R.string.core_reminder_detail_unavailable),
                    modifier = Modifier.padding(22.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> {
                val reminder = requireNotNull(state.reminder)
                SoftGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    style = GlassSurfaceStyle.Prominent,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(if (reminder.completedAt == null) R.string.core_reminder else R.string.core_reminder_detail_done),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = reminder.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Detail(
                            label = stringResource(R.string.core_reminder_detail_target_time),
                            value = timeFormat.formatWeekdayDateTime(reminder.dueAt),
                        )
                        Detail(
                            label = stringResource(R.string.core_reminder_detail_notification),
                            value = reminderOffsetLabel(reminder.notificationOffsetMinutes),
                        )
                        Detail(
                            label = stringResource(R.string.core_reminder_detail_delivery),
                            value = stringResource(if (reminder.notificationEnabled) R.string.core_enabled else R.string.core_disabled),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            reminderOffsetOptions.forEach { option ->
                                if (option.minutes == reminder.notificationOffsetMinutes) {
                                    Surface(
                                        shape = MaterialTheme.shapes.extraLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text(
                                            text = option.label,
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = 40.dp)
                                                .padding(horizontal = 24.dp, vertical = 10.dp),
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateNotificationOffset(option.minutes)
                                        },
                                    ) {
                                        Text(option.label)
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.setNotificationEnabled(!reminder.notificationEnabled)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (reminder.notificationEnabled) {
                                    stringResource(R.string.core_reminder_detail_disable_notification)
                                } else {
                                    stringResource(R.string.core_reminder_detail_enable_notification)
                                },
                            )
                        }
                        if (reminder.notes.isNotBlank()) {
                            Detail(label = stringResource(R.string.core_item_detail_notes_field), value = reminder.notes)
                        }
                        state.relatedTaskTitle?.let { Detail(label = stringResource(R.string.core_reminder_detail_related_task), value = it) }
                        state.relatedCaptureText?.let { Detail(label = stringResource(R.string.core_reminder_detail_original_capture), value = it) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = viewModel::complete,
                                enabled = reminder.completedAt == null,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.core_reminder_detail_mark_done))
                            }
                            OutlinedButton(
                                onClick = {
                                    showReschedulePicker(
                                        context = context,
                                        currentDueAt = reminder.dueAt,
                                        timeFormat = timeFormat,
                                        onSelected = viewModel::reschedule,
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.core_reminder_detail_reschedule))
                            }
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.core_reminder_detail_delete))
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.core_reminder_detail_delete_title)) },
            text = { Text(stringResource(R.string.core_reminder_detail_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.core_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.core_action_cancel))
                }
            },
        )
    }
    SnackbarHost(hostState = snackbarHostState)
}

@Composable
private fun Detail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

private fun showReschedulePicker(
    context: Context,
    currentDueAt: Long,
    timeFormat: OrbitTimeFormat,
    onSelected: (Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(currentDueAt).atZone(zone).toLocalDateTime()
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
