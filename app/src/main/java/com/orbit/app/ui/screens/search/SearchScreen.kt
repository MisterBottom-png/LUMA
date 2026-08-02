package com.orbit.app.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.theme.OrbitMotion

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onResultSelected: (LocalSearchResult) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchContent(
        state = state,
        onBack = onBack,
        onQueryChanged = viewModel::updateQuery,
        onIncludeArchivedChanged = viewModel::setIncludeArchived,
        onResultSelected = onResultSelected,
    )
}

@Composable
internal fun SearchContent(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onIncludeArchivedChanged: (Boolean) -> Unit,
    onResultSelected: (LocalSearchResult) -> Unit,
) {
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val searchTitle = stringResource(R.string.core_search_title)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .semantics { paneTitle = searchTitle },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 24.dp,
            top = 26.dp,
            end = 24.dp,
            bottom = 24.dp + navigationBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.core_back),
                    )
                }
                Text(
                    text = searchTitle,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.core_search_local_data)) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            )
        }
        item {
            FilterChip(
                selected = state.includeArchived,
                onClick = { onIncludeArchivedChanged(!state.includeArchived) },
                label = { Text(stringResource(R.string.core_search_include_archived)) },
                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
            )
        }

        when {
            state.query.trim().length < 2 -> item {
                CalmSearchState(stringResource(R.string.core_search_minimum_query))
            }

            state.results.isEmpty() -> item {
                CalmSearchState(stringResource(R.string.core_search_empty))
            }

            else -> items(state.results, key = { it.key }) { result ->
                SearchResultRow(
                    result = result,
                    onClick = { onResultSelected(result) },
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                        placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                        fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                    ),
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: LocalSearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = stringResource(result.status.labelRes())
    val visibleTitle = result.title.ifBlank { stringResource(result.type.untitledLabelRes()) }
    val visibleSnippet = result.snippet.ifBlank { status }
    val metadataSeparator = stringResource(R.string.core_metadata_separator)
    SoftGlassSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = result.type.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 13.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = visibleTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = visibleSnippet,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = listOfNotNull(result.spaceName?.takeIf(String::isNotBlank), status)
                        .joinToString(metadataSeparator),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CalmSearchState(text: String) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(22.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ItemDetailType.icon(): ImageVector = when (this) {
    ItemDetailType.Note -> Icons.Rounded.Description
    ItemDetailType.Task -> Icons.Rounded.TaskAlt
    ItemDetailType.Reminder -> Icons.Rounded.Notifications
    ItemDetailType.Capture -> Icons.Rounded.PushPin
}

@StringRes
private fun ItemDetailType.untitledLabelRes(): Int = when (this) {
    ItemDetailType.Note -> R.string.core_untitled_note
    ItemDetailType.Task -> R.string.core_untitled_task
    ItemDetailType.Reminder -> R.string.core_untitled_reminder
    ItemDetailType.Capture -> R.string.core_capture
}

@StringRes
private fun LocalSearchStatus.labelRes(): Int = when (this) {
    LocalSearchStatus.Note -> R.string.core_note
    LocalSearchStatus.Task -> R.string.core_task
    LocalSearchStatus.Reminder -> R.string.core_reminder
    LocalSearchStatus.CompletedReminder -> R.string.core_completed_reminder
    LocalSearchStatus.Done -> R.string.core_done
    LocalSearchStatus.Archived -> R.string.core_archived
    LocalSearchStatus.WaitingFor -> R.string.core_waiting_for
    LocalSearchStatus.Someday -> R.string.core_someday
}
