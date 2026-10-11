package com.orbit.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.ui.reminders.rememberTurnOnReminderNotifications
import com.orbit.app.ui.reminders.offerTurnOnIfBlocked
import com.orbit.app.ui.reminders.messageRes
import com.orbit.app.ui.time.OrbitTimeFormat

/**
 * Hosts the sorting sheet for one capture wherever sorting happens (Review > To
 * sort, Home with "Sort right after saving", or the quick time question).
 */
@Composable
fun CaptureSortHost(
    viewModel: CaptureSortViewModel,
    timeFormat: OrbitTimeFormat,
    snackbarHostState: SnackbarHostState,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult,
    )
    val undoLabel = stringResource(R.string.core_brain_dump_undo)
    val resolvedText = uiState.lastResolved?.let { resolved ->
        stringResource(resolved.reminderOutcome?.messageRes() ?: resolved.itemType.sortedMessageRes())
    }
    val turnOnPrompt = stringResource(R.string.reminder_outcome_turn_on_prompt)
    val turnOnLabel = stringResource(R.string.settings_turn_on)
    val turnOnNotifications = rememberTurnOnReminderNotifications()

    LaunchedEffect(uiState.notificationPermissionRequestPending) {
        if (!uiState.notificationPermissionRequestPending) return@LaunchedEffect
        viewModel.notificationPermissionRequestStarted()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onNotificationPermissionResult(granted = true)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Feedback is cleared only after its snackbar is gone. Clearing first would
    // change the effect's key, cancel it and dismiss the snackbar (and its Undo)
    // almost at once.
    val message = uiState.message
    val resolved = uiState.lastResolved
    val brainDumpOpen = uiState.brainDumpInteraction != null

    // Undo for a just-sorted thought, keyed on that thought alone so a later
    // message (for example "notifications are off") waits instead of cancelling it.
    LaunchedEffect(resolved) {
        val handled = resolved ?: return@LaunchedEffect
        val text = resolvedText ?: return@LaunchedEffect
        // The success message set together with the sorted thought; the Undo snackbar
        // already says it.
        val successMessage = message
        try {
            val result = snackbarHostState.showSnackbar(
                message = text,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undo(handled)
            } else {
                snackbarHostState.offerTurnOnIfBlocked(
                    outcome = handled.reminderOutcome,
                    prompt = turnOnPrompt,
                    turnOnLabel = turnOnLabel,
                    onTurnOn = turnOnNotifications,
                )
            }
        } finally {
            viewModel.resolvedHandled(handled)
            viewModel.messageShown(successMessage)
        }
    }

    // Plain messages, once no Undo is showing and no Brain Dump is open (only whether
    // one is open matters, not each step inside it).
    LaunchedEffect(message, brainDumpOpen, resolved == null) {
        if (resolved != null || brainDumpOpen) return@LaunchedEffect
        val shown = message ?: return@LaunchedEffect
        try {
            snackbarHostState.showSnackbar(shown)
        } finally {
            viewModel.messageShown(shown)
        }
    }

    uiState.suggestion?.let { suggestion ->
        CaptureSuggestionSheet(
            suggestion = suggestion,
            timeFormat = timeFormat,
            brainDumpInteraction = uiState.brainDumpInteraction,
            brainDumpCallbacks = BrainDumpCallbacks(
                onPrimaryAction = viewModel::commitBrainDumpPrimaryAction,
                onEdit = viewModel::editBrainDumpItem,
                onDraftChanged = viewModel::updateBrainDumpDraft,
                onContinueFromEditor = viewModel::continueBrainDumpFromEditor,
                onStepBack = viewModel::stepBackBrainDump,
                onKeepInInbox = viewModel::keepBrainDumpInInbox,
                onSkip = viewModel::skipBrainDump,
                onUndoSkip = viewModel::undoBrainDumpSkip,
                onRetry = viewModel::retryBrainDumpAction,
                onFinishLater = viewModel::finishBrainDumpLater,
                onDiscardRemaining = viewModel::discardRemainingBrainDumpSuggestions,
                onCloseCompletion = viewModel::closeBrainDumpCompletion,
                onToggleRow = viewModel::toggleBrainDumpRow,
                onOpenRow = viewModel::openBrainDumpRow,
                onSaveTicked = viewModel::saveTickedBrainDumpRows,
                onKeepAsOneNote = viewModel::keepBrainDumpAsOneNote,
                onSplitRow = viewModel::splitBrainDumpRow,
            ),
            isPerformingAction = uiState.isPerformingAction,
            onSaveNote = viewModel::saveNote,
            onCreateTask = viewModel::createTask,
            onCreateReminder = viewModel::createReminder,
            onKeepInInbox = viewModel::keepInInbox,
            onCancel = viewModel::cancelSuggestion,
            onDiscardBrainDumpDraftChanges = viewModel::discardBrainDumpDraftChanges,
            onSplitIntoThoughts = { viewModel.splitIntoThoughts(suggestion.captureId) },
        )
    }
    uiState.learnedRuleProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLearnedRuleProposal,
            title = { Text(stringResource(R.string.core_learning_save_title)) },
            text = { Text(stringResource(R.string.core_learning_save_body, proposal.ruleText)) },
            confirmButton = {
                Button(onClick = viewModel::saveLearnedRuleProposal) {
                    Text(stringResource(R.string.core_learning_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLearnedRuleProposal) {
                    Text(stringResource(R.string.core_learning_not_now))
                }
            },
        )
    }
}

internal fun SuggestedItemType.sortedMessageRes(): Int = when (this) {
    SuggestedItemType.Note -> R.string.core_home_message_note_saved
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.core_home_message_task_created
    SuggestedItemType.Reminder -> R.string.reminder_outcome_saved
}
