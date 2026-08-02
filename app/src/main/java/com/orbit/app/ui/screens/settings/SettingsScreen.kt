@file:OptIn(ExperimentalLayoutApi::class)

package com.orbit.app.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.orbitPressFeedback
import com.orbit.app.ui.localization.AppLanguage
import com.orbit.app.ui.theme.OrbitMotion
import com.orbit.app.ui.theme.OrbitSpacing

internal enum class SettingsSection(
    @param:StringRes val titleRes: Int,
    @param:StringRes val subtitleRes: Int,
) {
    Overview(R.string.settings_title, R.string.settings_empty_subtitle),
    Appearance(R.string.settings_appearance_title, R.string.settings_appearance_subtitle),
    System(R.string.settings_system_title, R.string.settings_system_subtitle),
}

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
    onResetAllData: () -> Unit,
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
                            onResetAllData = onResetAllData,
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
