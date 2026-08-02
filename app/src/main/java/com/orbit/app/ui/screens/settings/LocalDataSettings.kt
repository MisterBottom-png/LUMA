package com.orbit.app.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics

@Composable
internal fun LocalDataSettingsSection(
    localDataTools: LocalDataToolsUiState,
    onExportJson: (android.net.Uri?) -> Unit,
    onRestoreFileSelected: (android.net.Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onResetAllData: () -> Unit,
) {
    var showExportWarning by rememberSaveable { mutableStateOf(false) }
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = onExportJson,
    )
    val restorePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = onRestoreFileSelected,
    )
    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.settings_export_unencrypted_title)) },
            text = { Text(stringResource(R.string.settings_export_unencrypted_warning)) },
            confirmButton = {
                Button(
                    onClick = {
                        showExportWarning = false
                        exportPicker.launch("luma-export.json")
                    },
                ) {
                    Text(stringResource(R.string.settings_export_choose_location))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    localDataTools.restorePlan?.let { plan ->
        AlertDialog(
            onDismissRequest = onCancelRestore,
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.settings_restore_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_restore_summary,
                        plan.restoredCounts.notes,
                        plan.restoredCounts.tasks,
                        plan.restoredCounts.reminders,
                        plan.restoredCounts.captures,
                        plan.restoredCounts.spaces,
                        plan.existingCounts.notes,
                        plan.existingCounts.tasks,
                        plan.existingCounts.reminders,
                        plan.existingCounts.captures,
                        plan.existingCounts.spaces,
                    ),
                )
            },
            confirmButton = {
                Button(onClick = onConfirmRestore, enabled = !localDataTools.isRestoring) {
                    Text(
                        stringResource(
                            if (localDataTools.isRestoring) {
                                R.string.settings_restoring
                            } else {
                                R.string.settings_replace_local_data
                            },
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onCancelRestore, enabled = !localDataTools.isRestoring) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_export_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { showExportWarning = true },
                enabled = !localDataTools.isExporting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (localDataTools.isExporting) {
                            R.string.settings_exporting
                        } else {
                            R.string.settings_export_json
                        },
                    ),
                )
            }
            if (localDataTools.exportCompleted) {
                Text(
                    text = stringResource(R.string.settings_export_complete),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            localDataTools.errorMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.settings_restore_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { restorePicker.launch(arrayOf("application/json", "text/json")) },
                enabled = !localDataTools.isPreparingRestore && !localDataTools.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (localDataTools.isPreparingRestore) {
                            R.string.settings_validating
                        } else {
                            R.string.settings_choose_export
                        },
                    ),
                )
            }
            localDataTools.restoreMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                text = stringResource(R.string.settings_reset_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                enabled = !localDataTools.isResetting && !localDataTools.isRestoring,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (localDataTools.isResetting) {
                            R.string.settings_resetting
                        } else {
                            R.string.settings_reset_local_data
                        },
                    ),
                )
            }
        }
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.settings_reset_data_title)) },
            text = { Text(stringResource(R.string.settings_reset_data_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmation = false
                        onResetAllData()
                    },
                ) {
                    Text(stringResource(R.string.settings_reset_data_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}
