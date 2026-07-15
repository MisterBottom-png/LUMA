package com.orbit.app.ui.screens.situation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.domain.ai.SourceLinkedAnswer
import com.orbit.app.domain.analyzer.SituationAnalysis
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SourceRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationAiSheet(
    uiState: SituationAiUiState,
    onDismiss: () -> Unit,
    onPanelSelected: (SituationPanel) -> Unit,
    onOpenReview: () -> Unit,
    onSourceSelected: (AiSourceItem) -> Unit,
    onAskQueryChanged: (String) -> Unit,
    onAskLuma: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.18f),
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 780.dp),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            style = GlassSurfaceStyle.Sheet,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
            ) {
                SheetHeader(
                    onDismiss = onDismiss,
                    modifier = Modifier.padding(
                        start = 24.dp,
                        top = 18.dp,
                        end = 24.dp,
                    ),
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 24.dp,
                        top = 18.dp,
                        end = 24.dp,
                        bottom = 28.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (uiState.isLoading || uiState.analysis == null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        val analysis = uiState.analysis
                        item {
                            AnswerSection(
                                title = "Where am I right now?",
                                lines = listOf(uiState.sourceSummary?.rightNow ?: analysis.whereYouAre),
                            )
                        }
                        item {
                            AnswerSection(
                                title = "What matters?",
                                lines = listOfNotNull(uiState.sourceSummary?.whatMatters).ifEmpty { analysis.whatMatters },
                            )
                        }
                        item {
                            AnswerSection(
                                title = "What is stuck?",
                                lines = listOfNotNull(uiState.sourceSummary?.stuck).ifEmpty { analysis.whatIsStuck },
                            )
                        }
                        item {
                            NextActionCard(uiState.sourceSummary?.nextTinyStep ?: analysis.nextAction)
                        }
                        uiState.sourceSummary?.takeIf { it.sourceItems.isNotEmpty() }?.let { summary ->
                            item {
                                SourceList(
                                    title = if (summary.fromGemini) "Sources for Gemini summary" else "Local sources",
                                    sources = summary.sourceItems,
                                    onSourceSelected = onSourceSelected,
                                )
                            }
                        }
                        uiState.selectedPanel?.let { panel ->
                            item {
                                SelectedPanelCard(panel = panel, analysis = analysis)
                            }
                        }
                        item {
                            SituationActions(
                                selectedPanel = uiState.selectedPanel,
                                onPanelSelected = onPanelSelected,
                                onOpenReview = onOpenReview,
                            )
                        }
                        item {
                            AskLumaSection(
                                query = uiState.askQuery,
                                answer = uiState.askAnswer,
                                isAsking = uiState.isAsking,
                                onQueryChanged = onAskQueryChanged,
                                onAsk = onAskLuma,
                                onSourceSelected = onSourceSelected,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AskLumaSection(
    query: String,
    answer: SourceLinkedAnswer?,
    isAsking: Boolean,
    onQueryChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onSourceSelected: (AiSourceItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Ask LUMA",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        val composerState = askComposerState(query = query, isAsking = isAsking)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Ask about local items") },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { submitAskIfEnabled(composerState, onAsk) },
            ),
            trailingIcon = {
                IconButton(
                    onClick = { submitAskIfEnabled(composerState, onAsk) },
                    enabled = composerState.sendEnabled,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (composerState.showLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics { contentDescription = "Generating answer" },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send question",
                        )
                    }
                }
            },
        )
        answer?.let {
            Text(
                text = if (it.fromGemini) "Answered by Gemini from local sources" else "Local answer",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = it.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (it.sourceItems.isNotEmpty()) {
                SourceList(
                    title = "Sources",
                    sources = it.sourceItems,
                    onSourceSelected = onSourceSelected,
                )
            }
        }
    }
}


internal data class AskComposerState(
    val sendEnabled: Boolean,
    val showLoading: Boolean,
)

internal fun askComposerState(query: String, isAsking: Boolean): AskComposerState =
    AskComposerState(
        sendEnabled = query.trim().length >= 2 && !isAsking,
        showLoading = isAsking,
    )

internal fun isAskSendEnabled(query: String, isAsking: Boolean): Boolean =
    askComposerState(query = query, isAsking = isAsking).sendEnabled

internal fun submitAskIfEnabled(composerState: AskComposerState, onAsk: () -> Unit) {
    if (composerState.sendEnabled) onAsk()
}

@Composable
private fun SourceList(
    title: String,
    sources: List<AiSourceItem>,
    onSourceSelected: (AiSourceItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            SourceRow(
                title = source.title,
                itemType = source.type,
                onClick = { onSourceSelected(source) },
            )
        }
    }
}

@Composable
private fun SheetHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = "Situation AI",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A local view of what needs your attention",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close Situation AI")
        }
    }
}

@Composable
private fun AnswerSection(title: String, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    text = "-",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = line,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NextActionCard(nextAction: String) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "What should I do next?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = nextAction,
                modifier = Modifier.padding(top = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SelectedPanelCard(panel: SituationPanel, analysis: SituationAnalysis) {
    val (title, lines) = when (panel) {
        SituationPanel.NextAction -> "Next action" to listOf(analysis.nextAction)
        SituationPanel.OpenLoops -> "Open loops" to analysis.openLoops
        SituationPanel.TinyPlan -> "Tiny plan" to analysis.tinyPlan.mapIndexed { index, step ->
            "${index + 1}. $step"
        }
        SituationPanel.ClearNoise -> "Clear noise" to listOf(analysis.clearNoiseSuggestion)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationActions(
    selectedPanel: SituationPanel?,
    onPanelSelected: (SituationPanel) -> Unit,
    onOpenReview: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(
            text = "Actions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SituationActionChip(
                label = "Show next action",
                icon = Icons.Rounded.Lightbulb,
                selected = selectedPanel == SituationPanel.NextAction,
                onClick = { onPanelSelected(SituationPanel.NextAction) },
            )
            SituationActionChip(
                label = "Review open loops",
                icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                selected = selectedPanel == SituationPanel.OpenLoops,
                onClick = { onPanelSelected(SituationPanel.OpenLoops) },
            )
            SituationActionChip(
                label = "Make tiny plan",
                icon = Icons.Rounded.Route,
                selected = selectedPanel == SituationPanel.TinyPlan,
                onClick = { onPanelSelected(SituationPanel.TinyPlan) },
            )
            SituationActionChip(
                label = "Clear noise",
                icon = Icons.Rounded.CleaningServices,
                selected = selectedPanel == SituationPanel.ClearNoise,
                onClick = { onPanelSelected(SituationPanel.ClearNoise) },
            )
            SituationActionChip(
                label = "Open review",
                icon = Icons.AutoMirrored.Rounded.FactCheck,
                selected = false,
                onClick = onOpenReview,
            )
        }
    }
}

@Composable
private fun SituationActionChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
    )
}
