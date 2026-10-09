package com.orbit.app.ui.screens.settings

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.GeminiConsent
import com.orbit.app.domain.model.hasCurrentGeminiConsent
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics

internal enum class AiSettingsPage(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Mode(R.string.settings_ai_mode, R.string.settings_ai_mode_subtitle, Icons.Filled.AutoAwesome),
    GeminiSetup(R.string.settings_gemini_key, R.string.settings_ai_setup_subtitle, Icons.Filled.Tune),
    Features(R.string.settings_use_gemini_for, R.string.settings_ai_features_subtitle, Icons.Filled.AutoAwesome),
    LocalLearning(
        R.string.settings_local_learning_title,
        R.string.settings_ai_learning_subtitle,
        Icons.Filled.Person,
    ),
}

internal enum class AiSettingsContent {
    Mode,
    GeminiKey,
    Models,
    Features,
    LocalLearning,
}

internal fun aiSettingsContentFor(page: AiSettingsPage): List<AiSettingsContent> = when (page) {
    AiSettingsPage.Mode -> listOf(AiSettingsContent.Mode)
    AiSettingsPage.GeminiSetup -> listOf(AiSettingsContent.GeminiKey, AiSettingsContent.Models)
    AiSettingsPage.Features -> listOf(AiSettingsContent.Features)
    AiSettingsPage.LocalLearning -> listOf(AiSettingsContent.LocalLearning)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiSettingsCard(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aiSettings: AiSettingsUiState,
    selectedMenuSection: AiSettingsPage?,
    onSectionSelected: (AiSettingsPage) -> Unit,
    onSaveGeminiKey: (String) -> Unit,
    onDeleteGeminiKey: () -> Unit,
    onClearLearningData: () -> Unit,
    onUpdateLearnedRule: (LearnedRuleEntity) -> Unit,
    onDeleteLearnedRule: (LearnedRuleEntity) -> Unit,
    onTestGeminiConnection: (String, String) -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    var showGeminiConsent by rememberSaveable { mutableStateOf(false) }
    var showClearLearningConfirmation by rememberSaveable { mutableStateOf(false) }
    var ruleBeingEdited by remember { mutableStateOf<LearnedRuleEntity?>(null) }
    val canUseGeminiFeatures = settings.aiMode == AiMode.GeminiApi &&
        settings.hasCurrentGeminiConsent && aiSettings.hasKey

    if (showGeminiConsent) {
        AlertDialog(
            onDismissRequest = { showGeminiConsent = false },
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.settings_gemini_consent_title)) },
            text = { Text(stringResource(R.string.settings_gemini_consent_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showGeminiConsent = false
                        onSettingsChanged(
                            settings.copy(
                                aiMode = AiMode.GeminiApi,
                                geminiConsentVersion = GeminiConsent.CurrentVersion,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.settings_gemini_consent_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGeminiConsent = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    if (showClearLearningConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearLearningConfirmation = false },
            modifier = Modifier.calmPressHaptics(),
            title = { Text(stringResource(R.string.settings_clear_learning_title)) },
            text = { Text(stringResource(R.string.settings_clear_learning_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showClearLearningConfirmation = false
                        onClearLearningData()
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_learning_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLearningConfirmation = false }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
    ruleBeingEdited?.let { rule ->
        var text by remember(rule.id) { mutableStateOf(rule.ruleText) }
        AlertDialog(
            onDismissRequest = { ruleBeingEdited = null },
            title = { Text(stringResource(R.string.settings_learning_edit_rule)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.settings_learning_rule)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateLearnedRule(rule.copy(ruleText = text.trim(), updatedAt = System.currentTimeMillis()))
                        ruleBeingEdited = null
                    },
                    enabled = text.isNotBlank(),
                ) { Text(stringResource(R.string.settings_save)) }
            },
            dismissButton = {
                TextButton(onClick = { ruleBeingEdited = null }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }

    if (selectedMenuSection == null) {
        AiSettingsMenuCard(
            settings = settings,
            aiSettings = aiSettings,
            onSectionSelected = onSectionSelected,
        )
    } else {
        AiSettingsDetailsCard(
            page = selectedMenuSection,
            settings = settings,
            onSettingsChanged = onSettingsChanged,
            aiSettings = aiSettings,
            onSaveGeminiKey = onSaveGeminiKey,
            onDeleteGeminiKey = onDeleteGeminiKey,
            onClearLearningData = onClearLearningData,
            onUpdateLearnedRule = onUpdateLearnedRule,
            onDeleteLearnedRule = onDeleteLearnedRule,
            onTestGeminiConnection = onTestGeminiConnection,
            onRequestGeminiConsent = { showGeminiConsent = true },
            onRequestClearLearning = { showClearLearningConfirmation = true },
            onEditLearnedRule = { ruleBeingEdited = it },
        )
    }
}

@Composable
private fun AiSettingsMenuCard(
    settings: AppSettings,
    aiSettings: AiSettingsUiState,
    onSectionSelected: (AiSettingsPage) -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            AiSettingsPage.entries.forEachIndexed { index, section ->
                SettingsMenuRow(
                    icon = section.icon,
                    title = stringResource(section.titleRes),
                    status = when (section) {
                        AiSettingsPage.Mode -> stringResource(settings.aiMode.labelRes())
                        AiSettingsPage.GeminiSetup -> stringResource(
                            if (aiSettings.hasKey) R.string.settings_status_key_saved
                            else R.string.settings_status_key_not_saved,
                        )
                        AiSettingsPage.Features -> stringResource(settings.aiMode.labelRes())
                        AiSettingsPage.LocalLearning -> stringResource(
                            if (settings.enableLocalAiLearning) R.string.settings_selected
                            else R.string.settings_not_selected,
                        )
                    },
                    onClick = { onSectionSelected(section) },
                )
                if (index < AiSettingsPage.entries.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                }
            }
        }
    }
}

@Composable
private fun AiSettingsDetailsCard(
    page: AiSettingsPage,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aiSettings: AiSettingsUiState,
    onSaveGeminiKey: (String) -> Unit,
    onDeleteGeminiKey: () -> Unit,
    onClearLearningData: () -> Unit,
    onUpdateLearnedRule: (LearnedRuleEntity) -> Unit,
    onDeleteLearnedRule: (LearnedRuleEntity) -> Unit,
    onTestGeminiConnection: (String, String) -> Unit,
    onRequestGeminiConsent: () -> Unit,
    onRequestClearLearning: () -> Unit,
    onEditLearnedRule: (LearnedRuleEntity) -> Unit,
) {
    var apiKey by rememberSaveable { mutableStateOf("") }
    val canUseGeminiFeatures = settings.aiMode == AiMode.GeminiApi &&
        settings.hasCurrentGeminiConsent && aiSettings.hasKey
    val visibleContent = aiSettingsContentFor(page)
    Column(modifier = Modifier.padding(top = 22.dp)) {
        AppearanceCard {
            if (AiSettingsContent.Mode in visibleContent) {
            SettingsGroup(title = stringResource(R.string.settings_ai_mode)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    AiMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.aiMode == mode,
                            onClick = {
                                when (mode) {
                                    AiMode.LocalOnly -> onSettingsChanged(
                                        settings.copy(
                                            aiMode = AiMode.LocalOnly,
                                            geminiConsentVersion = 0,
                                            useGeminiForCapture = false,
                                            useGeminiForMakeSmaller = false,
                                            useGeminiForBrainDump = false,
                                            useGeminiForSituation = false,
                                            useGeminiForReview = false,
                                        ),
                                    )
                                    AiMode.GeminiApi -> if (settings.hasCurrentGeminiConsent) {
                                        onSettingsChanged(settings.copy(aiMode = AiMode.GeminiApi))
                                    } else {
                                        onRequestGeminiConsent()
                                    }
                                }
                            },
                            label = { Text(stringResource(mode.labelRes())) },
                            colors = readableFilterChipColors(),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.settings_ai_mode_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (AiSettingsContent.GeminiKey in visibleContent) {
            SettingsGroup(title = stringResource(R.string.settings_gemini_key)) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_paste_key)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = {
                        Text(
                            stringResource(
                                if (aiSettings.hasKey) {
                                    R.string.settings_key_saved
                                } else {
                                    R.string.settings_no_key_saved
                                },
                            ),
                        )
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = {
                            onSaveGeminiKey(apiKey)
                            apiKey = ""
                        },
                        enabled = apiKey.isNotBlank() && !aiSettings.isSavingKey,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (aiSettings.isSavingKey) {
                                    R.string.settings_saving
                                } else {
                                    R.string.settings_save_key
                                },
                            ),
                        )
                    }
                    OutlinedButton(
                        onClick = onDeleteGeminiKey,
                        enabled = aiSettings.hasKey,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.settings_remove))
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (settings.hasCurrentGeminiConsent) {
                            onTestGeminiConnection(
                                settings.geminiFastModelId,
                                settings.geminiReasoningModelId,
                            )
                        } else {
                            onRequestGeminiConsent()
                        }
                    },
                    enabled = aiSettings.hasKey && !aiSettings.isTestingConnection,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (aiSettings.isTestingConnection) {
                                R.string.settings_testing
                            } else {
                                R.string.settings_test_connection
                            },
                        ),
                    )
                }
                aiSettings.connectionMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (aiSettings.connectionSucceeded == false) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        if (AiSettingsContent.Models in visibleContent) {
            SettingsGroup(title = stringResource(R.string.settings_models)) {
                OutlinedTextField(
                    value = settings.geminiFastModelId,
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(geminiFastModelId = value.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_fast_model)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = settings.geminiReasoningModelId,
                    onValueChange = { value ->
                        onSettingsChanged(settings.copy(geminiReasoningModelId = value.trim()))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_reasoning_model)) },
                    singleLine = true,
                )
            }
        }

        if (AiSettingsContent.Features in visibleContent) {
            SettingsGroup(title = stringResource(R.string.settings_use_gemini_for)) {
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_capture_suggestions),
                    checked = settings.useGeminiForCapture,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForCapture = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_make_smaller),
                    checked = settings.useGeminiForMakeSmaller,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForMakeSmaller = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_brain_dump),
                    checked = settings.useGeminiForBrainDump,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForBrainDump = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_situation_ai),
                    checked = settings.useGeminiForSituation,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForSituation = enabled))
                    },
                )
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_review),
                    checked = settings.useGeminiForReview,
                    enabled = canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(useGeminiForReview = enabled))
                    },
                )
            }
        }

        if (AiSettingsContent.LocalLearning in visibleContent) {
            SettingsGroup(title = stringResource(R.string.settings_local_learning_title)) {
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_local_learning_toggle),
                    checked = settings.enableLocalAiLearning,
                    enabled = !aiSettings.isClearingLearning,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(enableLocalAiLearning = enabled))
                    },
                )
                Text(
                    text = stringResource(R.string.settings_local_learning_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AiFeatureSwitch(
                    title = stringResource(R.string.settings_share_learning_with_gemini),
                    checked = settings.shareLocalLearningWithGemini,
                    enabled = settings.enableLocalAiLearning && canUseGeminiFeatures,
                    onCheckedChange = { enabled ->
                        onSettingsChanged(settings.copy(shareLocalLearningWithGemini = enabled))
                    },
                )
                Text(
                    text = stringResource(R.string.settings_share_learning_with_gemini_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                aiSettings.learnedRules.forEach { rule ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                rule.ruleText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { enabled -> onUpdateLearnedRule(rule.copy(enabled = enabled)) },
                        )
                        TextButton(onClick = { onEditLearnedRule(rule) }) {
                            Text(stringResource(R.string.settings_edit))
                        }
                        TextButton(onClick = { onDeleteLearnedRule(rule) }) {
                            Text(stringResource(R.string.settings_remove))
                        }
                    }
                }
                OutlinedButton(
                    onClick = onRequestClearLearning,
                    enabled = !aiSettings.isClearingLearning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            if (aiSettings.isClearingLearning) {
                                R.string.settings_clearing_learning
                            } else {
                                R.string.settings_clear_learning
                            },
                        ),
                    )
                }
                aiSettings.learningClearSucceeded?.let { succeeded ->
                    Text(
                        text = stringResource(
                            if (succeeded) {
                                R.string.settings_learning_cleared
                            } else {
                                R.string.settings_learning_clear_failed
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (succeeded) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

            Text(
                text = stringResource(R.string.settings_ai_privacy_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AiFeatureSwitch(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}
