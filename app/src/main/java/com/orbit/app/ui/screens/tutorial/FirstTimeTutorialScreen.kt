package com.orbit.app.ui.screens.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.orbit.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.StarterSpaces
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing
import com.orbit.app.ui.localization.localizedStarterSpaceName
import kotlinx.coroutines.launch

@Composable
fun FirstTimeTutorialScreen(
    isReplay: Boolean,
    spaceSetupState: TutorialSpaceSetupUiState = TutorialSpaceSetupUiState(),
    onToggleSpaceTemplate: (String) -> Unit = {},
    onAddCustomSpace: (String) -> Unit = {},
    onRemoveCustomSpace: (String) -> Unit = {},
    onFinishSpaceSetup: ((() -> Unit) -> Unit) = { it() },
    onFinish: () -> Unit,
    onReplayBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = firstTimeTutorialPages
    val pagerState = rememberPagerState(pageCount = pages::size)
    val scope = rememberCoroutineScope()
    var leaving by rememberSaveable { mutableStateOf(false) }
    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == pages.lastIndex

    fun finishOnce() {
        if (!leaving) {
            leaving = true
            onFinish()
        }
    }

    BackHandler(enabled = currentPage > 0 || isReplay) {
        when {
            currentPage > 0 -> scope.launch {
                pagerState.animateScrollToPage(currentPage - 1)
            }

            isReplay -> onReplayBack()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = OrbitSpacing.Large),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(
                // Skipping the guide keeps any Spaces already chosen; with none chosen
                // the setup step finishes immediately.
                onClick = {
                    if (spaceSetupState.canConfigure) onFinishSpaceSetup(::finishOnce) else finishOnce()
                },
                enabled = !leaving && !spaceSetupState.isSaving,
            ) {
                Text(stringResource(R.string.tutorial_skip))
            }
        }

        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !leaving,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { pageIndex ->
            TutorialPageContent(
                page = pages[pageIndex],
                spaceSetupState = spaceSetupState,
                onToggleSpaceTemplate = onToggleSpaceTemplate,
                onAddCustomSpace = onAddCustomSpace,
                onRemoveCustomSpace = onRemoveCustomSpace,
                modifier = Modifier.fillMaxSize(),
            )
        }

        TutorialProgress(
            currentPage = currentPage,
            pageCount = pages.size,
            enabled = !leaving,
            onPageSelected = { page ->
                scope.launch { pagerState.animateScrollToPage(page) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = OrbitSpacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch { pagerState.animateScrollToPage(currentPage - 1) }
                },
                enabled = currentPage > 0 && !leaving,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.tutorial_back))
            }
            Button(
                onClick = {
                    if (isLastPage) {
                        if (spaceSetupState.canConfigure) {
                            onFinishSpaceSetup(::finishOnce)
                        } else {
                            finishOnce()
                        }
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
                enabled = !leaving,
                modifier = Modifier
                    .weight(1.35f)
                    .heightIn(min = 52.dp),
            ) {
                Text(
                    stringResource(
                        if (isLastPage) R.string.tutorial_start else R.string.tutorial_next,
                    ),
                )
            }
        }
    }
}

@Composable
private fun TutorialPageContent(
    page: FirstTimeTutorialPage,
    spaceSetupState: TutorialSpaceSetupUiState,
    onToggleSpaceTemplate: (String) -> Unit,
    onAddCustomSpace: (String) -> Unit,
    onRemoveCustomSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val wideLayout = maxWidth >= 600.dp
        if (wideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = OrbitSpacing.Medium),
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Huge),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TutorialCopy(
                    page = page,
                    spaceSetupState = spaceSetupState,
                    onToggleSpaceTemplate = onToggleSpaceTemplate,
                    onAddCustomSpace = onAddCustomSpace,
                    onRemoveCustomSpace = onRemoveCustomSpace,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                )
                TutorialIllustrationCard(
                    illustration = page.illustration,
                    modifier = Modifier
                        .weight(0.82f)
                        .heightIn(min = 260.dp, max = 430.dp),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = OrbitSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Large),
            ) {
                TutorialCopy(
                    page = page,
                    spaceSetupState = spaceSetupState,
                    onToggleSpaceTemplate = onToggleSpaceTemplate,
                    onAddCustomSpace = onAddCustomSpace,
                    onRemoveCustomSpace = onRemoveCustomSpace,
                )
                TutorialIllustrationCard(
                    illustration = page.illustration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 150.dp, max = 220.dp),
                )
                Spacer(modifier = Modifier.height(OrbitSpacing.Small))
            }
        }
    }
}

@Composable
private fun TutorialCopy(
    page: FirstTimeTutorialPage,
    spaceSetupState: TutorialSpaceSetupUiState,
    onToggleSpaceTemplate: (String) -> Unit,
    onAddCustomSpace: (String) -> Unit,
    onRemoveCustomSpace: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Large),
    ) {
        Text(
            text = stringResource(page.stepRes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoftGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = OrbitShapes.Standard,
            style = GlassSurfaceStyle.Standard,
        ) {
            Column(
                modifier = Modifier.padding(OrbitSpacing.Large),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
            ) {
                Text(
                    text = stringResource(page.principleTitleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(page.principleBodyRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (page.illustration == TutorialIllustration.Spaces && spaceSetupState.canConfigure) {
            TutorialSpaceSetupPanel(
                state = spaceSetupState,
                onToggleTemplate = onToggleSpaceTemplate,
                onAddCustomSpace = onAddCustomSpace,
                onRemoveCustomSpace = onRemoveCustomSpace,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TutorialSpaceSetupPanel(
    state: TutorialSpaceSetupUiState,
    onToggleTemplate: (String) -> Unit,
    onAddCustomSpace: (String) -> Unit,
    onRemoveCustomSpace: (String) -> Unit,
) {
    var customName by rememberSaveable { mutableStateOf("") }
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = OrbitShapes.Standard,
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(OrbitSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Medium),
        ) {
            Text(
                text = stringResource(R.string.tutorial_space_setup_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.tutorial_space_setup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Small)) {
                StarterSpaces.templates.forEach { template ->
                    FilterChip(
                        selected = template.key in state.selectedTemplateKeys,
                        onClick = { onToggleTemplate(template.key) },
                        enabled = !state.isSaving,
                        label = { Text(localizedStarterSpaceName(template)) },
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
            ) {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving,
                    singleLine = true,
                    label = { Text(stringResource(R.string.tutorial_space_setup_name)) },
                )
                IconButton(
                    onClick = {
                        onAddCustomSpace(customName)
                        customName = ""
                    },
                    enabled = customName.isNotBlank() && !state.isSaving,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.tutorial_space_setup_add),
                    )
                }
            }
            state.customNames.forEach { name ->
                TextButton(
                    onClick = { onRemoveCustomSpace(name) },
                    enabled = !state.isSaving,
                ) {
                    Text(stringResource(R.string.tutorial_space_setup_remove, name))
                }
            }
            if (state.saveFailed) {
                Text(
                    text = stringResource(R.string.tutorial_space_setup_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun TutorialProgress(
    currentPage: Int,
    pageCount: Int,
    enabled: Boolean,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.tutorial_progress, currentPage + 1, pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { page ->
                val isSelected = page == currentPage
                val stepLabel = stringResource(R.string.tutorial_go_to_step, page + 1)
                IconButton(
                    onClick = { onPageSelected(page) },
                    enabled = enabled,
                    modifier = Modifier.semantics {
                        selected = isSelected
                        contentDescription = stepLabel
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 20.dp else 12.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun TutorialIllustrationCard(
    illustration: TutorialIllustration,
    modifier: Modifier = Modifier,
) {
    val visual = tutorialIllustrationVisual(illustration)
    SoftGlassSurface(
        modifier = modifier.clearAndSetSemantics { },
        shape = OrbitShapes.Prominent,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(OrbitSpacing.ExtraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
            ) {
                repeat(visual.rowCount) { index ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (index == visual.accentRow) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                                },
                            ),
                    ) {}
                }
            }
        }
    }
}

private data class TutorialIllustrationVisual(
    val icon: ImageVector,
    val rowCount: Int,
    val accentRow: Int,
)

private fun tutorialIllustrationVisual(
    illustration: TutorialIllustration,
): TutorialIllustrationVisual = when (illustration) {
    TutorialIllustration.Idea ->
        TutorialIllustrationVisual(Icons.Rounded.Inbox, rowCount = 1, accentRow = 0)
    TutorialIllustration.Home ->
        TutorialIllustrationVisual(Icons.Rounded.CalendarMonth, rowCount = 2, accentRow = 1)
    TutorialIllustration.Confirm ->
        TutorialIllustrationVisual(Icons.Rounded.CheckCircle, rowCount = 2, accentRow = 0)
    TutorialIllustration.BrainDump ->
        TutorialIllustrationVisual(Icons.Rounded.ViewAgenda, rowCount = 3, accentRow = 1)
    TutorialIllustration.Spaces ->
        TutorialIllustrationVisual(Icons.Rounded.GridView, rowCount = 3, accentRow = 2)
    TutorialIllustration.Review ->
        TutorialIllustrationVisual(Icons.Rounded.AutoAwesome, rowCount = 2, accentRow = 0)
    TutorialIllustration.Control ->
        TutorialIllustrationVisual(Icons.Rounded.Lock, rowCount = 3, accentRow = 0)
}
