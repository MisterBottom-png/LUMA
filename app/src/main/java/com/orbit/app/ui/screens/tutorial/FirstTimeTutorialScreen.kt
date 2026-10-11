package com.orbit.app.ui.screens.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.StarterSpaces
import com.orbit.app.ui.localization.localizedStarterSpaceName
import kotlinx.coroutines.launch
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.sp
import com.orbit.app.ui.components.TintedIconChip
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.screens.spaces.asColor
import com.orbit.app.ui.screens.spaces.asImageVector
import com.orbit.app.ui.localization.localizedSpaceName

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(horizontal = 24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TutorialProgress(
                    currentPage = currentPage,
                    pageCount = pages.size,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    // Skipping the guide keeps any Spaces already chosen; with none chosen
                    // the setup step finishes immediately.
                    onClick = {
                        if (spaceSetupState.canConfigure) onFinishSpaceSetup(::finishOnce) else finishOnce()
                    },
                    enabled = !leaving && !spaceSetupState.isSaving,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text(stringResource(R.string.tutorial_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Button(
                onClick = {
                    if (isLastPage) {
                        if (spaceSetupState.canConfigure) onFinishSpaceSetup(::finishOnce) else finishOnce()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(currentPage + 1) }
                    }
                },
                enabled = !leaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .heightIn(min = 56.dp),
            ) {
                Text(
                    text = stringResource(tutorialPrimaryLabel(isLastPage = isLastPage, isReplay = isReplay)),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (currentPage > 0) {
                    TextButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(currentPage - 1) } },
                        enabled = !leaving,
                    ) {
                        Text(stringResource(R.string.tutorial_back), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = 12.dp, bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 280.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (page.illustration) {
                TutorialIllustration.Write -> WriteDemo()
                TutorialIllustration.Suggest -> SuggestDemo()
                TutorialIllustration.Spaces -> if (spaceSetupState.canConfigure) {
                    TutorialSpacePicker(
                        state = spaceSetupState,
                        onToggleTemplate = onToggleSpaceTemplate,
                        onAddCustomSpace = onAddCustomSpace,
                        onRemoveCustomSpace = onRemoveCustomSpace,
                    )
                } else {
                    SpacesDemo()
                }
                TutorialIllustration.Private -> PrivateDemo()
            }
        }
        Text(
            text = stringResource(page.titleRes),
            modifier = Modifier
                .padding(top = 28.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(page.bodyRes),
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 25.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A thin bar per step; the filled part is where the user is. */
@Composable
private fun TutorialProgress(
    currentPage: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.tutorial_progress, currentPage + 1, pageCount)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(pageCount) { page ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (page <= currentPage) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                        },
                    ),
            )
        }
    }
}

/** Demo pieces are pictures of the app, so screen readers skip them. */
@Composable
private fun DemoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { },
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

@Composable
private fun WriteDemo() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DemoCard {
            Text(
                text = stringResource(R.string.tutorial_demo_thought),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        // Earlier thoughts as quiet lines, not chips: nothing here is meant to be tapped.
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .clearAndSetSemantics { },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(R.string.tutorial_demo_other_1, R.string.tutorial_demo_other_2).forEach { line ->
                Text(
                    text = stringResource(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestDemo() {
    DemoCard {
        Text(
            text = stringResource(R.string.tutorial_demo_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        // The same parts as the real sort sheet: the suggestion, the type, when, the
        // Space, and one button that names the result.
        Row(modifier = Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(
                    R.string.sort_suggests,
                    stringResource(com.orbit.app.ui.screens.home.suggestedActionLabel(com.orbit.app.data.local.entity.SuggestedItemType.Reminder)),
                ),
                modifier = Modifier.padding(start = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DemoChip(stringResource(R.string.core_task), faded = true)
            DemoChip(stringResource(R.string.core_reminder))
            DemoChip(stringResource(R.string.core_note), faded = true)
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DemoChip(stringResource(R.string.core_tomorrow))
            DemoChip(localizedSpaceName("Health"))
        }
        Box(
            modifier = Modifier
                .padding(top = 18.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.core_capture_action_set_reminder),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun SpacesDemo() {
    DemoCard {
        StarterSpaces.templates.take(4).forEachIndexed { index, template ->
            if (index > 0) Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TintedIconChip(icon = template.icon.asImageVector(), color = template.colorAccent.asColor())
                Text(
                    text = localizedStarterSpaceName(template),
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PrivateDemo() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(30.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(30.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
        ) {
            Column {
                listOf(R.string.tutorial_private_1, R.string.tutorial_private_2, R.string.tutorial_private_3)
                    .forEachIndexed { index, line ->
                        if (index > 0) GroupDivider(startInset = 36.dp)
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                            Text(
                                text = stringResource(line),
                                modifier = Modifier.padding(start = 12.dp),
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun DemoChip(text: String, faded: Boolean = false) {
    Box(
        modifier = Modifier
            .background(
                if (faded) {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                RoundedCornerShape(14.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (faded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** First run only: the real Space picker, so the guide ends with Spaces that fit. */
@Composable
private fun TutorialSpacePicker(
    state: TutorialSpaceSetupUiState,
    onToggleTemplate: (String) -> Unit,
    onAddCustomSpace: (String) -> Unit,
    onRemoveCustomSpace: (String) -> Unit,
) {
    var customName by rememberSaveable { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StarterSpaces.templates.chunked(2).forEach { rowTemplates ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowTemplates.forEach { template ->
                    val selected = template.key in state.selectedTemplateKeys
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .toggleable(
                                value = selected,
                                enabled = !state.isSaving,
                                role = Role.Checkbox,
                                onValueChange = { onToggleTemplate(template.key) },
                            ),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = if (selected) 1f else 0.6f),
                        border = if (selected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TintedIconChip(
                                icon = template.icon.asImageVector(),
                                color = template.colorAccent.asColor(),
                                size = 32.dp,
                                iconSize = 18.dp,
                            )
                            Text(
                                text = localizedStarterSpaceName(template),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .then(
                                        if (selected) {
                                            Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f), CircleShape)
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
                if (rowTemplates.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customName,
                onValueChange = { customName = it },
                modifier = Modifier.weight(1f),
                enabled = !state.isSaving,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                label = { Text(stringResource(R.string.tutorial_space_setup_name)) },
                // The keyboard's Done adds the Space, like the + button.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.Sentences),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (customName.isNotBlank()) {
                            onAddCustomSpace(customName)
                            customName = ""
                        }
                    },
                ),
            )
            IconButton(
                onClick = {
                    onAddCustomSpace(customName)
                    customName = ""
                },
                enabled = customName.isNotBlank() && !state.isSaving,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.tutorial_space_setup_add))
            }
        }
        state.customNames.forEach { name ->
            TextButton(onClick = { onRemoveCustomSpace(name) }, enabled = !state.isSaving) {
                Text(
                    text = stringResource(R.string.tutorial_space_setup_remove, name),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Says which Spaces will be made, so the choice is clear before "Start".
        val picked = StarterSpaces.templates.filter { it.key in state.selectedTemplateKeys }
            .map { localizedStarterSpaceName(it) } + state.customNames
        Text(
            text = if (picked.isEmpty()) {
                stringResource(R.string.tutorial_spaces_none_picked)
            } else {
                stringResource(R.string.tutorial_spaces_picked, picked.joinToString(", "))
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.saveFailed) {
            Text(
                text = stringResource(R.string.tutorial_space_setup_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@androidx.annotation.StringRes
internal fun tutorialPrimaryLabel(isLastPage: Boolean, isReplay: Boolean): Int = when {
    !isLastPage -> R.string.tutorial_continue
    isReplay -> R.string.tutorial_done
    else -> R.string.tutorial_start
}
