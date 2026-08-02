package com.orbit.app.ui.screens.settings

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.orbit.app.R
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.GeminiConsent
import com.orbit.app.domain.model.hasCurrentGeminiConsent
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.AppearancePaletteMode
import com.orbit.app.domain.model.BackgroundBlur
import com.orbit.app.domain.model.BackgroundDimmingMode
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.GlassPreference
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.SettingsThemeMode
import com.orbit.app.domain.model.withDefaultAppearance
import com.orbit.app.ui.components.GlassRolePreview
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.components.orbitPressFeedback
import com.orbit.app.ui.localization.AppLanguage
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing
import com.orbit.app.ui.theme.OrbitMotion
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    applicationLanguage: AppLanguage,
    onApplicationLanguageChanged: (AppLanguage) -> Unit,
    aiSettings: AiSettingsUiState,
    onSaveGeminiKey: (String) -> Unit,
    onDeleteGeminiKey: () -> Unit,
    onClearAiLearningData: () -> Unit,
    onUpdateLearnedRule: (LearnedRuleEntity) -> Unit,
    onDeleteLearnedRule: (LearnedRuleEntity) -> Unit,
    onTestGeminiConnection: (String, String) -> Unit,
    localDataTools: LocalDataToolsUiState,
    onExportJson: (android.net.Uri?) -> Unit,
    onRestoreFileSelected: (android.net.Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onAppearanceSubsectionChanged: (Boolean) -> Unit,
) {
    var currentSection by rememberSaveable { mutableStateOf(SettingsSection.Overview) }
    var appearanceSubsection by rememberSaveable {
        mutableStateOf<AppearanceMenuSection?>(null)
    }
    var systemSubsection by rememberSaveable {
        mutableStateOf<SystemMenuSection?>(null)
    }
    val defaultScrollState = rememberScrollState()
    val appearanceIndexScrollState = rememberScrollState()
    val appearanceSubsectionScrollState = rememberScrollState()
    val systemIndexScrollState = rememberScrollState()
    val systemSubsectionScrollState = rememberScrollState()
    val activeScrollState = when {
        currentSection == SettingsSection.Appearance && appearanceSubsection == null ->
            appearanceIndexScrollState
        currentSection == SettingsSection.Appearance -> appearanceSubsectionScrollState
        currentSection == SettingsSection.System && systemSubsection == null -> systemIndexScrollState
        currentSection == SettingsSection.System -> systemSubsectionScrollState
        else -> defaultScrollState
    }
    val isSettingsSubsectionOpen = appearanceSubsection != null || systemSubsection != null

    LaunchedEffect(isSettingsSubsectionOpen) {
        onAppearanceSubsectionChanged(isSettingsSubsectionOpen)
    }

    BackHandler(enabled = appearanceSubsection != null) {
        appearanceSubsection = null
    }
    BackHandler(enabled = systemSubsection != null) {
        systemSubsection = null
    }
    BackHandler(
        enabled = currentSection != SettingsSection.Overview && !isSettingsSubsectionOpen,
    ) {
        currentSection = SettingsSection.Overview
    }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val bottomContentPadding = if (imeVisible) {
        28.dp
    } else if (isSettingsSubsectionOpen) {
        OrbitSpacing.Large + navigationBottomPadding
    } else {
        OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(activeScrollState)
            .padding(horizontal = 24.dp)
            .padding(top = 30.dp),
    ) {
        AnimatedContent(
            targetState = Triple(currentSection, appearanceSubsection, systemSubsection),
            modifier = Modifier.fillMaxWidth(),
            transitionSpec = {
                val movingForward = when {
                    initialState.first == targetState.first ->
                        initialState.second == null && initialState.third == null &&
                            (targetState.second != null || targetState.third != null)
                    targetState.first == SettingsSection.Overview -> false
                    else -> true
                }
                val enterOffset: (Int) -> Int = { width ->
                    if (movingForward) width / 10 else -width / 10
                }
                val exitOffset: (Int) -> Int = { width ->
                    if (movingForward) -width / 12 else width / 12
                }
                (fadeIn(tween(OrbitMotion.StandardDurationMillis)) +
                    slideInHorizontally(
                        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                        initialOffsetX = enterOffset,
                    )) togetherWith
                    (fadeOut(tween(OrbitMotion.QuickDurationMillis)) +
                        slideOutHorizontally(
                            animationSpec = tween(OrbitMotion.StandardDurationMillis),
                            targetOffsetX = exitOffset,
                        ))
            },
            contentKey = { it },
            label = "Settings section",
        ) { (section, appearanceMenuSection, systemMenuSection) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                if (appearanceMenuSection != null) {
                    SettingsSubsectionHeader(
                        title = stringResource(appearanceMenuSection.titleRes),
                        subtitle = stringResource(appearanceMenuSection.subtitleRes),
                        parentTitle = stringResource(SettingsSection.Appearance.titleRes),
                        onBack = { appearanceSubsection = null },
                    )
                } else if (systemMenuSection != null) {
                    SettingsSubsectionHeader(
                        title = stringResource(systemMenuSection.titleRes),
                        subtitle = stringResource(systemMenuSection.subtitleRes),
                        parentTitle = stringResource(SettingsSection.System.titleRes),
                        onBack = { systemSubsection = null },
                    )
                } else {
                    SettingsHeader(
                        section = section,
                        onBack = { currentSection = SettingsSection.Overview },
                    )
                }

                when (section) {
                    SettingsSection.Overview -> SettingsOverview(
                        settings = settings,
                        aiSettings = aiSettings,
                        onSectionSelected = {
                            appearanceSubsection = null
                            systemSubsection = null
                            currentSection = it
                        },
                    )

                    SettingsSection.Appearance -> AppearanceSettingsSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                        selectedMenuSection = appearanceMenuSection,
                        onSectionSelected = { appearanceSubsection = it },
                    )

                    SettingsSection.System -> when (systemMenuSection) {
                        null -> SystemMenuCard(
                            settings = settings,
                            applicationLanguage = applicationLanguage,
                            aiSettings = aiSettings,
                            localDataTools = localDataTools,
                            onSectionSelected = { systemSubsection = it },
                        )

                        SystemMenuSection.Time -> Column(
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            TimeSettingsSection(
                                settings = settings,
                                onSettingsChanged = onSettingsChanged,
                            )
                        }

                        SystemMenuSection.Language -> Column(
                            modifier = Modifier.padding(top = 18.dp),
                        ) {
                            LanguageSettingsSection(
                                applicationLanguage = applicationLanguage,
                                onApplicationLanguageChanged = onApplicationLanguageChanged,
                            )
                        }

                        SystemMenuSection.Ai -> AiSettingsCard(
                            settings = settings,
                            onSettingsChanged = onSettingsChanged,
                            aiSettings = aiSettings,
                            onSaveGeminiKey = onSaveGeminiKey,
                            onDeleteGeminiKey = onDeleteGeminiKey,
                            onClearLearningData = onClearAiLearningData,
                            onUpdateLearnedRule = onUpdateLearnedRule,
                            onDeleteLearnedRule = onDeleteLearnedRule,
                            onTestGeminiConnection = onTestGeminiConnection,
                        )

                        SystemMenuSection.LocalData -> LocalDataSettingsSection(
                            localDataTools = localDataTools,
                            onExportJson = onExportJson,
                            onRestoreFileSelected = onRestoreFileSelected,
                            onConfirmRestore = onConfirmRestore,
                            onCancelRestore = onCancelRestore,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(bottomContentPadding))
    }
}

@Composable
private fun SettingsHeader(
    section: SettingsSection,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (section != SettingsSection.Overview) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.settings_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column {
            Text(
                text = stringResource(section.titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (section != SettingsSection.Overview) {
                Text(
                    text = stringResource(section.subtitleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsOverview(
    settings: AppSettings,
    aiSettings: AiSettingsUiState,
    onSectionSelected: (SettingsSection) -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsCategoryRow(
                icon = Icons.Filled.Palette,
                title = stringResource(SettingsSection.Appearance.titleRes),
                subtitle = stringResource(SettingsSection.Appearance.subtitleRes),
                status = if (settings.customBackgroundUri != null) {
                    stringResource(
                        R.string.settings_status_custom_background,
                        stringResource(settings.themeMode.labelRes()),
                    )
                } else {
                    stringResource(
                        R.string.settings_status_preset_background,
                        stringResource(settings.themeMode.labelRes()),
                        stringResource(settings.backgroundPreset.labelRes()),
                    )
                },
                onClick = { onSectionSelected(SettingsSection.Appearance) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            SettingsCategoryRow(
                icon = Icons.Filled.Tune,
                title = stringResource(SettingsSection.System.titleRes),
                subtitle = stringResource(SettingsSection.System.subtitleRes),
                status = if (settings.aiMode == AiMode.GeminiApi && aiSettings.hasKey) {
                    stringResource(
                        R.string.settings_status_time_ai_ready,
                        stringResource(settings.timeFormatMode.labelRes()),
                    )
                } else {
                    stringResource(
                        R.string.settings_status_time_ai_mode,
                        stringResource(settings.timeFormatMode.labelRes()),
                        stringResource(settings.aiMode.labelRes()),
                    )
                },
                onClick = { onSectionSelected(SettingsSection.System) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    status: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                    RoundedCornerShape(15.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppearanceSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    selectedMenuSection: AppearanceMenuSection?,
    onSectionSelected: (AppearanceMenuSection) -> Unit,
) {
    val context = LocalContext.current
    var showResetConfirmation by rememberSaveable { mutableStateOf(false) }
    val customBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        onSettingsChanged(settings.copy(customBackgroundUri = uri.toString()))
    }

    Column(
        modifier = Modifier.padding(top = 22.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selectedMenuSection == null) {
            AppearanceMenuCard(
                settings = settings,
                onSectionSelected = onSectionSelected,
            )
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_reset_appearance))
            }
            if (showResetConfirmation) {
                AlertDialog(
                    onDismissRequest = { showResetConfirmation = false },
                    title = { Text(stringResource(R.string.settings_reset_appearance)) },
                    confirmButton = {
                        TextButton(onClick = {
                            onSettingsChanged(settings.withDefaultAppearance())
                            showResetConfirmation = false
                        }) { Text(stringResource(R.string.settings_reset_appearance)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showResetConfirmation = false }) {
                            Text(stringResource(R.string.settings_cancel))
                        }
                    },
                )
            }
        } else {
            when (selectedMenuSection) {
                AppearanceMenuSection.Profile -> AppearanceProfileSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )

                AppearanceMenuSection.Colors -> AppearanceColorsSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )

                AppearanceMenuSection.Background -> AppearanceBackgroundSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                    onChooseCustomBackground = {
                        customBackgroundPicker.launch(arrayOf("image/*"))
                    },
                )

                AppearanceMenuSection.Glass -> AppearanceGlassSection(
                    settings = settings,
                    onSettingsChanged = onSettingsChanged,
                )
            }
        }
    }
}

private enum class AppearanceMenuSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Profile(R.string.settings_profile_title, R.string.settings_profile_subtitle, Icons.Filled.Person),
    Colors(R.string.settings_colors_title, R.string.settings_colors_subtitle, Icons.Filled.Palette),
    Background(
        R.string.settings_background_title,
        R.string.settings_background_subtitle,
        Icons.Filled.Image,
    ),
    Glass(
        R.string.settings_transparency_title,
        R.string.settings_transparency_subtitle,
        Icons.Filled.Tune,
    ),
}

@Composable
private fun AppearanceMenuCard(
    settings: AppSettings,
    onSectionSelected: (AppearanceMenuSection) -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            AppearanceMenuRow(
                section = AppearanceMenuSection.Profile,
                status = settings.userName.ifBlank { stringResource(R.string.settings_no_name) },
                onClick = { onSectionSelected(AppearanceMenuSection.Profile) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Colors,
                status = stringResource(
                    R.string.settings_status_colors,
                    stringResource(settings.themeMode.labelRes()),
                    stringResource(settings.accentColor.labelRes()),
                    stringResource(settings.textColor.labelRes()),
                ),
                onClick = { onSectionSelected(AppearanceMenuSection.Colors) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Background,
                status = if (settings.customBackgroundUri != null) {
                    stringResource(R.string.settings_custom_image)
                } else {
                    stringResource(settings.backgroundPreset.labelRes())
                },
                onClick = { onSectionSelected(AppearanceMenuSection.Background) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            AppearanceMenuRow(
                section = AppearanceMenuSection.Glass,
                status = stringResource(
                    R.string.settings_status_surface_opacity,
                    stringResource(settings.glassPreference.labelRes()),
                ),
                onClick = { onSectionSelected(AppearanceMenuSection.Glass) },
            )
        }
    }
}

@Composable
private fun AppearanceMenuRow(
    section: AppearanceMenuSection,
    status: String,
    onClick: () -> Unit,
) = SettingsMenuRow(
    icon = section.icon,
    title = stringResource(section.titleRes),
    status = status,
    onClick = onClick,
)

private enum class SystemMenuSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    Time(R.string.settings_time_title, R.string.settings_time_subtitle, Icons.Filled.AccessTime),
    Language(R.string.settings_language_title, R.string.settings_language_subtitle, Icons.Filled.Language),
    Ai(R.string.settings_ai_title, R.string.settings_ai_subtitle, Icons.Filled.AutoAwesome),
    LocalData(R.string.settings_local_data_title, R.string.settings_local_data_subtitle, Icons.Filled.Storage),
}

@Composable
private fun SystemMenuCard(
    settings: AppSettings,
    applicationLanguage: AppLanguage,
    aiSettings: AiSettingsUiState,
    localDataTools: LocalDataToolsUiState,
    onSectionSelected: (SystemMenuSection) -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SettingsMenuRow(
                icon = SystemMenuSection.Time.icon,
                title = stringResource(SystemMenuSection.Time.titleRes),
                status = stringResource(settings.timeFormatMode.labelRes()),
                onClick = { onSectionSelected(SystemMenuSection.Time) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            SettingsMenuRow(
                icon = SystemMenuSection.Language.icon,
                title = stringResource(SystemMenuSection.Language.titleRes),
                status = stringResource(applicationLanguage.labelRes()),
                onClick = { onSectionSelected(SystemMenuSection.Language) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            SettingsMenuRow(
                icon = SystemMenuSection.Ai.icon,
                title = stringResource(SystemMenuSection.Ai.titleRes),
                status = if (settings.aiMode == AiMode.GeminiApi && aiSettings.hasKey) {
                    stringResource(R.string.settings_status_gemini_ready)
                } else {
                    stringResource(
                        R.string.settings_status_ai_key,
                        stringResource(settings.aiMode.labelRes()),
                        stringResource(
                            if (aiSettings.hasKey) {
                                R.string.settings_status_key_saved
                            } else {
                                R.string.settings_status_key_not_saved
                            },
                        ),
                    )
                },
                onClick = { onSectionSelected(SystemMenuSection.Ai) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
            SettingsMenuRow(
                icon = SystemMenuSection.LocalData.icon,
                title = stringResource(SystemMenuSection.LocalData.titleRes),
                status = stringResource(
                    if (localDataTools.exportCompleted) {
                        R.string.settings_status_last_export
                    } else {
                        R.string.settings_export_json
                    },
                ),
                onClick = { onSectionSelected(SystemMenuSection.LocalData) },
            )
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    title: String,
    status: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    OrbitShapes.Small,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.ExtraSmall),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsSubsectionHeader(
    title: String,
    subtitle: String,
    parentTitle: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back_to_menu, parentTitle),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceProfileSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        OutlinedTextField(
            value = settings.userName,
            onValueChange = { value ->
                onSettingsChanged(settings.copy(userName = value.take(MaxUserNameLength)))
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_your_name)) },
            supportingText = { Text(stringResource(R.string.settings_name_supporting)) },
            singleLine = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceColorsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_theme)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = {
                            onSettingsChanged(settings.copy(themeMode = mode))
                        },
                        label = { Text(stringResource(mode.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_theme_auto_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_overall_color)) {
            AppAccentColor.entries.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowChoices.forEach { choice ->
                        AccentColorOption(
                            choice = choice,
                            selected = settings.accentColor == choice,
                            onSelected = {
                                onSettingsChanged(settings.copy(accentColor = choice))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            FilterChip(
                selected = settings.paletteMode == AppearancePaletteMode.FullPalette,
                onClick = {
                    onSettingsChanged(
                        settings.copy(
                            paletteMode = if (settings.paletteMode == AppearancePaletteMode.FullPalette) {
                                AppearancePaletteMode.Standard
                            } else {
                                AppearancePaletteMode.FullPalette
                            },
                        ),
                    )
                },
                label = { Text(stringResource(R.string.settings_advanced_full_palette)) },
                colors = readableFilterChipColors(),
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_text_color)) {
            AppTextColor.entries.chunked(2).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowChoices.forEach { choice ->
                        TextColorOption(
                            choice = choice,
                            selected = settings.textColor == choice,
                            onSelected = {
                                onSettingsChanged(settings.copy(textColor = choice))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowChoices.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeSettingsSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_time_format)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SettingsTimeFormatMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.timeFormatMode == mode,
                        onClick = {
                            onSettingsChanged(settings.copy(timeFormatMode = mode))
                        },
                        label = { Text(stringResource(mode.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_time_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSettingsSection(
    applicationLanguage: AppLanguage,
    onApplicationLanguageChanged: (AppLanguage) -> Unit,
) {
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_language_title)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FilterChip(
                        selected = applicationLanguage == language,
                        onClick = { onApplicationLanguageChanged(language) },
                        label = { Text(stringResource(language.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            Text(
                text = stringResource(R.string.language_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppearanceBackgroundSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onChooseCustomBackground: () -> Unit,
) {
    AppearanceCard {
        val backgroundMode = if (settings.customBackgroundUri != null) {
            BackgroundMode.Custom
        } else {
            BackgroundMode.Preset
        }
        BackgroundModeButtons(
            selectedMode = backgroundMode,
            onCustomSelected = {
                if (settings.customBackgroundUri == null) {
                    onChooseCustomBackground()
                }
            },
            onPresetSelected = {
                if (settings.customBackgroundUri != null) {
                    onSettingsChanged(settings.copy(customBackgroundUri = null))
                }
            },
        )

        if (backgroundMode == BackgroundMode.Custom) {
            CustomBackgroundOption(
                selected = true,
                onChoose = onChooseCustomBackground,
                onRemove = { onSettingsChanged(settings.copy(customBackgroundUri = null)) },
            )
        } else {
            BackgroundPreset.entries.chunked(2).forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowPresets.forEach { preset ->
                        BackgroundPresetOption(
                            preset = preset,
                            selected = settings.backgroundPreset == preset,
                            onSelected = {
                                onSettingsChanged(
                                    settings.copy(
                                        backgroundPreset = preset,
                                        customBackgroundUri = null,
                                    ),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceGlassSection(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
) {
    GlassRolePreview(
        modifier = Modifier.fillMaxWidth(),
        title = stringResource(R.string.settings_surface_preview),
    )
    AppearanceCard {
        SettingsGroup(title = stringResource(R.string.settings_image_blur)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundBlur.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.backgroundBlur == choice,
                        enabled = settings.customBackgroundUri != null,
                        onClick = { onSettingsChanged(settings.copy(backgroundBlur = choice)) },
                        label = { Text(stringResource(choice.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
        }
        AppearanceSlider(
            title = stringResource(R.string.settings_background_dim),
            value = settings.backgroundDim,
            supportingText = stringResource(R.string.settings_dim_explanation),
            rangeStartLabel = stringResource(R.string.settings_bright),
            rangeEndLabel = stringResource(R.string.settings_dim),
            onValueChange = { value ->
                onSettingsChanged(settings.copy(backgroundDim = value))
            },
        )
        SettingsGroup(title = stringResource(R.string.settings_surface_opacity)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassPreference.entries.forEach { choice ->
                    FilterChip(
                        selected = settings.glassPreference == choice,
                        onClick = { onSettingsChanged(settings.copy(glassPreference = choice)) },
                        label = { Text(stringResource(choice.labelRes())) },
                        colors = readableFilterChipColors(),
                    )
                }
            }
            FilterChip(
                selected = settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive,
                onClick = {
                    onSettingsChanged(settings.copy(backgroundDimmingMode = if (settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive) BackgroundDimmingMode.Manual else BackgroundDimmingMode.Adaptive))
                },
                label = {
                    Text(
                        stringResource(
                            if (settings.backgroundDimmingMode == BackgroundDimmingMode.Adaptive) {
                                R.string.settings_adaptive_dimming
                            } else {
                                R.string.settings_manual_dimming
                            },
                        ),
                    )
                },
                colors = readableFilterChipColors(),
            )
        }
    }
}

private enum class BackgroundMode {
    Custom,
    Preset,
}

@Composable
private fun AppearanceCard(content: @Composable ColumnScope.() -> Unit) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun AccentColorOption(
    choice: AppAccentColor,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = accentSwatchColors(choice)
    ColorSwatchOption(
        label = stringResource(choice.labelRes()),
        selected = selected,
        onSelected = onSelected,
        colors = colors,
        modifier = modifier,
    )
}

@Composable
private fun TextColorOption(
    choice: AppTextColor,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ColorSwatchOption(
        label = stringResource(choice.labelRes()),
        selected = selected,
        onSelected = onSelected,
        colors = listOf(textSwatchColor(choice)),
        modifier = modifier,
    )
}

@Composable
private fun ColorSwatchOption(
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val selectedDescription = stringResource(
        if (selected) R.string.settings_selected else R.string.settings_not_selected,
    )
    Column(
        modifier = modifier
            .height(88.dp)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.28f)
                },
                shape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
                },
                shape = shape,
            )
            .selectable(
                selected = selected,
                onClick = onSelected,
                role = Role.RadioButton,
            )
            .semantics {
                stateDescription = selectedDescription
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(color, CircleShape)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                CircleShape,
                            ),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun BackgroundModeButtons(
    selectedMode: BackgroundMode,
    onCustomSelected: () -> Unit,
    onPresetSelected: () -> Unit,
) {
    val modes = BackgroundMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = when (mode) {
                    BackgroundMode.Custom -> onCustomSelected
                    BackgroundMode.Preset -> onPresetSelected
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = when (mode) {
                        BackgroundMode.Custom -> stringResource(R.string.settings_custom)
                        BackgroundMode.Preset -> stringResource(R.string.settings_preset)
                    },
                )
            }
        }
    }
}

@Composable
private fun LocalDataSettingsSection(
    localDataTools: LocalDataToolsUiState,
    onExportJson: (android.net.Uri?) -> Unit,
    onRestoreFileSelected: (android.net.Uri?) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
) {
    var showExportWarning by rememberSaveable { mutableStateOf(false) }
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
        }
    }
}

private enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    Overview(R.string.settings_title, R.string.settings_empty_subtitle),
    Appearance(R.string.settings_appearance_title, R.string.settings_appearance_subtitle),
    System(R.string.settings_system_title, R.string.settings_system_subtitle),
}

@StringRes
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SystemDefault -> R.string.language_system_default
    AppLanguage.English -> R.string.language_english
    AppLanguage.Estonian -> R.string.language_estonian
    AppLanguage.Russian -> R.string.language_russian
}

@StringRes
private fun SettingsThemeMode.labelRes(): Int = when (this) {
    SettingsThemeMode.Light -> R.string.theme_light
    SettingsThemeMode.Dark -> R.string.theme_dark
    SettingsThemeMode.Auto -> R.string.theme_auto
}

@StringRes
private fun SettingsTimeFormatMode.labelRes(): Int = when (this) {
    SettingsTimeFormatMode.Device -> R.string.time_device_default
    SettingsTimeFormatMode.TwelveHour -> R.string.time_twelve_hour
    SettingsTimeFormatMode.TwentyFourHour -> R.string.time_twenty_four_hour
}

@StringRes
private fun BackgroundPreset.labelRes(): Int = when (this) {
    BackgroundPreset.InkPaper -> R.string.background_ink_paper
    BackgroundPreset.SoftDawn -> R.string.background_soft_dawn
    BackgroundPreset.VioletMist -> R.string.background_violet_mist
    BackgroundPreset.CalmSky -> R.string.background_calm_sky
    BackgroundPreset.NightOrbit -> R.string.background_night_glow
}

@StringRes
private fun AppAccentColor.labelRes(): Int = when (this) {
    AppAccentColor.InkPaper -> R.string.accent_ink_paper
    AppAccentColor.LumaViolet -> R.string.accent_luma_violet
    AppAccentColor.Sage -> R.string.accent_sage
    AppAccentColor.Rose -> R.string.accent_rose
    AppAccentColor.Amber -> R.string.accent_amber
    AppAccentColor.Ocean -> R.string.accent_ocean
}

@StringRes
private fun AppTextColor.labelRes(): Int = when (this) {
    AppTextColor.Neutral -> R.string.text_neutral
    AppTextColor.Plum -> R.string.text_plum
    AppTextColor.Forest -> R.string.text_forest
    AppTextColor.WarmIvory -> R.string.text_warm_ivory
}

@StringRes
private fun AiMode.labelRes(): Int = when (this) {
    AiMode.LocalOnly -> R.string.ai_local_only
    AiMode.GeminiApi -> R.string.ai_gemini_api
}

@StringRes
private fun BackgroundBlur.labelRes(): Int = when (this) {
    BackgroundBlur.None -> R.string.settings_sharp
    BackgroundBlur.Soft -> R.string.settings_soft
    BackgroundBlur.Medium -> R.string.settings_blur_medium
    BackgroundBlur.Strong -> R.string.settings_blur_strong
}

@StringRes
private fun GlassPreference.labelRes(): Int = when (this) {
    GlassPreference.Subtle -> R.string.settings_opacity_subtle
    GlassPreference.Standard -> R.string.settings_opacity_standard
    GlassPreference.Prominent -> R.string.settings_opacity_prominent
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiSettingsCard(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    aiSettings: AiSettingsUiState,
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

    SoftGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
        shape = RoundedCornerShape(26.dp),
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_ai_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

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
                                        showGeminiConsent = true
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
                            showGeminiConsent = true
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
                        TextButton(onClick = { ruleBeingEdited = rule }) {
                            Text(stringResource(R.string.settings_edit))
                        }
                        TextButton(onClick = { onDeleteLearnedRule(rule) }) {
                            Text(stringResource(R.string.settings_remove))
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showClearLearningConfirmation = true },
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

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun BackgroundPresetOption(
    preset: BackgroundPreset,
    selected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val previewColors = presetPreviewColors(preset)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .height(76.dp)
            .background(Brush.linearGradient(previewColors), shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                },
                shape = shape,
            )
            .orbitPressFeedback(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onSelected,
            )
            .padding(12.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = stringResource(preset.labelRes()),
            modifier = Modifier
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                    RoundedCornerShape(11.dp),
                )
                .padding(horizontal = 10.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CustomBackgroundOption(
    selected: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
                .background(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.56f),
                        ),
                    ),
                    shape = shape,
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                    shape = shape,
                )
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                            RoundedCornerShape(15.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_custom_image),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (selected) {
                                R.string.settings_custom_image_selected
                            } else {
                                R.string.settings_custom_image_available
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onChoose,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (selected) {
                            R.string.settings_change_image
                        } else {
                            R.string.settings_choose_image
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = onRemove,
                enabled = selected,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.settings_remove))
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    enabled: Boolean = true,
    supportingText: String? = null,
    rangeStartLabel: String? = null,
    rangeEndLabel: String? = null,
    onValueChange: (Float) -> Unit,
) {
    val safeValue = value.coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${(safeValue * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = safeValue,
            onValueChange = onValueChange,
            enabled = enabled,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
                inactiveTrackColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.34f),
                thumbColor = MaterialTheme.colorScheme.primary,
            ),
        )
        if (rangeStartLabel != null && rangeEndLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = rangeStartLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = rangeEndLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        supportingText?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun readableFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedTrailingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

private fun presetPreviewColors(preset: BackgroundPreset): List<Color> = when (preset) {
    BackgroundPreset.InkPaper -> listOf(Color(0xFFFBF9F5), Color(0xFFEEF2F0))
    BackgroundPreset.SoftDawn -> listOf(Color(0xFFFFD8CA), Color(0xFFDCCAF1))
    BackgroundPreset.VioletMist -> listOf(Color(0xFFDCCAF4), Color(0xFF9D86CD))
    BackgroundPreset.CalmSky -> listOf(Color(0xFFBCE3EE), Color(0xFFBBD4F0))
    BackgroundPreset.NightOrbit -> listOf(Color(0xFF34315D), Color(0xFF163C4C))
}

private fun accentSwatchColors(choice: AppAccentColor): List<Color> = when (choice) {
    AppAccentColor.InkPaper -> listOf(Color(0xFF3D5962), Color(0xFF705D4A))
    AppAccentColor.LumaViolet -> listOf(Color(0xFF6550C8), Color(0xFF3F7479))
    AppAccentColor.Sage -> listOf(Color(0xFF3E6F45), Color(0xFF74642F))
    AppAccentColor.Rose -> listOf(Color(0xFF99415E), Color(0xFF725A42))
    AppAccentColor.Amber -> listOf(Color(0xFF865400), Color(0xFF5D6F47))
    AppAccentColor.Ocean -> listOf(Color(0xFF2D6684), Color(0xFF5C6090))
}

private fun textSwatchColor(choice: AppTextColor): Color = when (choice) {
    AppTextColor.Neutral -> Color(0xFF1B1C19)
    AppTextColor.Plum -> Color(0xFF2A173C)
    AppTextColor.Forest -> Color(0xFF152A1D)
    AppTextColor.WarmIvory -> Color(0xFFFFF1DB)
}

private fun Float.percentLabel(): String = "${(coerceIn(0f, 1f) * 100).roundToInt()}%"

private const val MaxUserNameLength = 40
