@file:OptIn(ExperimentalLayoutApi::class)

package com.orbit.app.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.LearnedRuleEntity
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.orbitScrollEdgeFade
import com.orbit.app.ui.localization.AppLanguage
import com.orbit.app.ui.theme.OrbitMotion
import com.orbit.app.ui.theme.OrbitSpacing

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
    onRetryReminderSetup: () -> Unit,
    onOpenFirstTimeGuide: () -> Unit,
    onSettingsSubsectionChanged: (Boolean) -> Unit,
    onClose: () -> Unit = {},
) {
    var appearanceSubsection by rememberSaveable {
        mutableStateOf<AppearanceMenuSection?>(null)
    }
    var systemSubsection by rememberSaveable {
        mutableStateOf<SystemMenuSection?>(null)
    }
    var aiSubsection by rememberSaveable {
        mutableStateOf<AiSettingsPage?>(null)
    }
    val homeScrollState = rememberScrollState()
    val appearanceSubsectionScrollState = rememberScrollState()
    val systemSubsectionScrollState = rememberScrollState()
    val aiIndexScrollState = rememberScrollState()
    val aiSubsectionScrollState = rememberScrollState()
    val activeScrollState = when {
        appearanceSubsection != null -> appearanceSubsectionScrollState
        systemSubsection == SystemMenuSection.Ai && aiSubsection == null -> aiIndexScrollState
        systemSubsection == SystemMenuSection.Ai -> aiSubsectionScrollState
        systemSubsection != null -> systemSubsectionScrollState
        else -> homeScrollState
    }
    val isSettingsSubsectionOpen = appearanceSubsection != null || systemSubsection != null

    LaunchedEffect(isSettingsSubsectionOpen) {
        onSettingsSubsectionChanged(isSettingsSubsectionOpen)
    }

    BackHandler(enabled = appearanceSubsection != null) {
        appearanceSubsection = null
    }
    BackHandler(enabled = aiSubsection != null) {
        aiSubsection = null
    }
    BackHandler(enabled = systemSubsection != null && aiSubsection == null) {
        systemSubsection = null
    }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusBarTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    var headerHeightPx by remember { mutableStateOf(0) }
    val measuredHeaderHeight = with(LocalDensity.current) { headerHeightPx.toDp() }
    val headerClearance = settingsHeaderClearance(
        statusBarTop = statusBarTopPadding,
        headerHeight = measuredHeaderHeight,
    )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(top = headerClearance)
                .verticalScroll(activeScrollState)
                .padding(horizontal = 24.dp)
                .padding(top = headerClearance),
        ) {
            AnimatedContent(
                targetState = SettingsPageState(appearanceSubsection, systemSubsection, aiSubsection),
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val movingForward = targetState.depth > initialState.depth
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
            ) { page ->
                val appearance = page.appearance
                val system = page.system
                when {
                    appearance != null -> AppearanceSettingsSection(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                        selectedMenuSection = appearance,
                    )

                    system == null -> SettingsHome(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                        applicationLanguage = applicationLanguage,
                        aiSettings = aiSettings,
                        localDataTools = localDataTools,
                        onOpenFirstTimeGuide = onOpenFirstTimeGuide,
                        onOpen = { destination ->
                            aiSubsection = null
                            when (destination) {
                                is SettingsDestination.Appearance -> appearanceSubsection = destination.section
                                is SettingsDestination.System -> systemSubsection = destination.section
                            }
                        },
                    )

                    else -> when (system) {
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
                            selectedMenuSection = page.ai,
                            onSectionSelected = { aiSubsection = it },
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
                            onRetryReminderSetup = onRetryReminderSetup,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(bottomContentPadding))
        }

        AnimatedContent(
            targetState = SettingsPageState(appearanceSubsection, systemSubsection, aiSubsection),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { headerHeightPx = it.height }
                .padding(horizontal = 24.dp)
                .padding(top = statusBarTopPadding + 30.dp),
            label = "Settings header",
        ) { page ->
            val settingsTitle = stringResource(R.string.settings_title)
            val appearance = page.appearance
            val ai = page.ai
            val system = page.system
            when {
                appearance != null -> SettingsSubsectionHeader(
                    title = stringResource(appearance.titleRes),
                    subtitle = stringResource(appearance.subtitleRes),
                    parentTitle = settingsTitle,
                    onBack = { appearanceSubsection = null },
                )

                ai != null -> SettingsSubsectionHeader(
                    title = stringResource(ai.titleRes),
                    subtitle = stringResource(ai.subtitleRes),
                    parentTitle = stringResource(SystemMenuSection.Ai.titleRes),
                    onBack = { aiSubsection = null },
                )

                system != null -> SettingsSubsectionHeader(
                    title = stringResource(system.titleRes),
                    subtitle = stringResource(system.subtitleRes),
                    parentTitle = settingsTitle,
                    onBack = { systemSubsection = null },
                )

                else -> SettingsHeader(onBack = onClose)
            }
        }
    }
}

/** Which page of Settings is showing; null everywhere means the main page. */
private data class SettingsPageState(
    val appearance: AppearanceMenuSection?,
    val system: SystemMenuSection?,
    val ai: AiSettingsPage?,
) {
    val depth: Int get() = when {
        ai != null -> 2
        appearance != null || system != null -> 1
        else -> 0
    }
}

internal fun settingsHeaderClearance(statusBarTop: Dp, headerHeight: Dp): Dp =
    maxOf(statusBarTop + 88.dp, headerHeight + 12.dp)

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Settings lives outside the tab bar, so the page gets a way back too.
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.settings_back),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
