package com.orbit.app.ui.screens.situation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.domain.ai.SourceLinkedAnswer
import com.orbit.app.ui.components.LumaModalBottomSheet
import com.orbit.app.ui.components.SourceRow
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.components.userVisibleLabel
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SituationAiSheet(
    uiState: SituationAiUiState,
    onDismiss: () -> Unit,
    onSourceSelected: (AiSourceItem) -> Unit,
    onAskQueryChanged: (String) -> Unit,
    onAskLuma: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sheetPaneTitle = stringResource(R.string.core_situation_title)
    LumaModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        surfaceModifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .semantics { paneTitle = sheetPaneTitle }
            .calmPressHaptics(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            SheetHeader(
                onDismiss = onDismiss,
                modifier = Modifier.padding(
                    start = OrbitSpacing.ExtraLarge,
                    top = 18.dp,
                    end = OrbitSpacing.ExtraLarge,
                ),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = OrbitSpacing.ExtraLarge,
                    top = OrbitSpacing.Large,
                    end = OrbitSpacing.ExtraLarge,
                    bottom = OrbitSpacing.ExtraLarge,
                ),
                verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Medium),
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
                        BriefingSurface(
                            whereYouAre = analysis.whereYouAre,
                            whatMatters = analysis.whatMatters,
                            whatIsStuck = analysis.whatIsStuck,
                        )
                    }
                    uiState.askAnswer?.let { answer ->
                        item {
                            AskLumaAnswer(
                                answer = answer,
                                onSourceSelected = onSourceSelected,
                            )
                        }
                    }
                }
            }
            if (!uiState.isLoading && uiState.analysis != null) {
                AskLumaSection(
                    query = uiState.askQuery,
                    isAsking = uiState.isAsking,
                    onQueryChanged = onAskQueryChanged,
                    onAsk = onAskLuma,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AskLumaSection(
    query: String,
    isAsking: Boolean,
    onQueryChanged: (String) -> Unit,
    onAsk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f))
            .padding(
                horizontal = OrbitSpacing.ExtraLarge,
                vertical = OrbitSpacing.Large,
            ),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f),
        )
        Text(
            text = stringResource(R.string.core_situation_ask_luma),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val composerState = askComposerState(query = query, isAsking = isAsking)
        val generatingAnswerDescription = stringResource(
            R.string.core_situation_generating_answer,
        )
        TextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge,
            placeholder = {
                Text(
                    text = stringResource(R.string.core_situation_ask_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            minLines = 1,
            maxLines = 3,
            shape = OrbitShapes.Standard,
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { submitAskIfEnabled(composerState, onAsk) },
            ),
            trailingIcon = {
                FilledTonalIconButton(
                    onClick = { submitAskIfEnabled(composerState, onAsk) },
                    enabled = composerState.sendEnabled,
                    modifier = Modifier.size(48.dp),
                ) {
                    if (composerState.showLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(20.dp)
                                .semantics {
                                    contentDescription = generatingAnswerDescription
                                },
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = stringResource(
                                R.string.core_situation_send_question,
                            ),
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun AskLumaAnswer(
    answer: SourceLinkedAnswer,
    onSourceSelected: (AiSourceItem) -> Unit,
) {
    Surface(
        shape = OrbitShapes.Standard,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
    ) {
        Column(
            modifier = Modifier.padding(OrbitSpacing.Large),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
        ) {
            Text(
                text = stringResource(
                    if (answer.fromGemini) {
                        R.string.core_situation_answered_by_gemini
                    } else {
                        R.string.core_situation_local_answer
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = answer.answer,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (answer.sourceItems.isNotEmpty()) {
                SourceList(
                    title = stringResource(R.string.core_sources),
                    sources = answer.sourceItems,
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
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            SourceRow(
                title = source.userVisibleLabel(),
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
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OrbitSpacing.Medium),
            verticalArrangement = Arrangement.spacedBy(OrbitSpacing.ExtraSmall),
        ) {
            Text(
                text = stringResource(R.string.core_situation_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.core_situation_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.core_situation_close),
            )
        }
    }
}

@Composable
private fun BriefingSurface(
    whereYouAre: String,
    whatMatters: List<String>,
    whatIsStuck: List<String>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = OrbitShapes.Standard,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)),
    ) {
        Column {
            BriefingSection(
                title = stringResource(R.string.core_situation_where_am_i),
                lines = listOf(whereYouAre),
            )
            BriefingDivider()
            BriefingSection(
                title = stringResource(R.string.core_situation_what_matters),
                lines = whatMatters,
            )
            BriefingDivider()
            BriefingSection(
                title = stringResource(R.string.core_situation_what_is_stuck),
                lines = whatIsStuck,
            )
        }
    }
}

@Composable
private fun BriefingSection(title: String, lines: List<String>) {
    Column(
        modifier = Modifier.padding(
            horizontal = OrbitSpacing.Large,
            vertical = 18.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        lines.forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(OrbitSpacing.Small),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "\u2022",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = line,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun BriefingDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = OrbitSpacing.Large),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f),
    )
}
