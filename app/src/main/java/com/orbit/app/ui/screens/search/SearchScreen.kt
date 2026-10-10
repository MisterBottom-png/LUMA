package com.orbit.app.ui.screens.search

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.domain.search.LocalSearchResult
import com.orbit.app.domain.search.LocalSearchStatus
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.components.GroupedCard
import com.orbit.app.ui.components.SectionHeader
import com.orbit.app.ui.components.TintedIconChip
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.navigation.ItemDetailType
import com.orbit.app.ui.screens.spaces.asColor
import com.orbit.app.ui.screens.spaces.asImageVector
import com.orbit.app.ui.theme.OrbitMotion

internal enum class SearchFeedbackState {
    StartTyping,
    MinimumQuery,
    Searching,
    EmptyResults,
    Results,
}

internal fun SearchUiState.feedbackState(): SearchFeedbackState = when {
    query.isBlank() -> SearchFeedbackState.StartTyping
    query.trim().length < 2 -> SearchFeedbackState.MinimumQuery
    results.isEmpty() && searching -> SearchFeedbackState.Searching
    results.isEmpty() -> SearchFeedbackState.EmptyResults
    else -> SearchFeedbackState.Results
}

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBack: () -> Unit,
    onResultSelected: (LocalSearchResult) -> Unit,
    onOpenSpace: (Long) -> Unit = {},
    initialQuery: String? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Words handed over (for example by Ask) are typed in once; later edits are the user's.
    var initialQueryApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialQuery) {
        if (!initialQueryApplied && !initialQuery.isNullOrBlank()) viewModel.updateQuery(initialQuery)
        initialQueryApplied = true
    }
    SearchContent(
        state = state,
        onBack = onBack,
        onQueryChanged = viewModel::updateQuery,
        onIncludeArchivedChanged = viewModel::setIncludeArchived,
        onResultSelected = onResultSelected,
        onFilterChanged = viewModel::setFilter,
        onOpenSpace = onOpenSpace,
    )
}

/**
 * Search: a rounded bar with Back inside it, filter chips under it, results grouped by
 * kind with the typed word marked. Before typing, the Spaces are one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SearchContent(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onIncludeArchivedChanged: (Boolean) -> Unit,
    onResultSelected: (LocalSearchResult) -> Unit,
    onFilterChanged: (SearchFilter) -> Unit = { onIncludeArchivedChanged(it == SearchFilter.Archived) },
    onOpenSpace: (Long) -> Unit = {},
) {
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val searchTitle = stringResource(R.string.core_search_title)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    val groups = remember(state.results) {
        state.results.groupBy { it.groupKey() }.toSortedMap(compareBy<SearchGroup> { it.ordinal })
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
            .semantics { paneTitle = searchTitle },
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 24.dp + navigationBottomPadding,
        ),
    ) {
        item(key = "bar") {
            SearchBarField(
                query = state.query,
                onQueryChanged = onQueryChanged,
                onBack = onBack,
                focusRequester = focusRequester,
            )
        }
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchFilter.entries.forEach { filter ->
                    FilterPill(
                        label = stringResource(filter.labelRes()),
                        selected = state.filter == filter,
                        onClick = { onFilterChanged(filter) },
                    )
                }
            }
        }

        when (state.feedbackState()) {
            SearchFeedbackState.StartTyping -> {
                if (state.spaces.isNotEmpty()) {
                    item(key = "spaces") {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            SectionHeader(title = stringResource(R.string.search_look_in_space))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 2,
                            ) {
                                state.spaces.forEach { space ->
                                    SpaceShortcut(
                                        space = space,
                                        onClick = { onOpenSpace(space.id) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "hint") { StartTypingHint() }
            }

            SearchFeedbackState.MinimumQuery -> item(key = "min") {
                QuietLine(stringResource(R.string.core_search_minimum_query))
            }

            // A short pause while the words are looked up: show nothing rather than "nothing found".
            SearchFeedbackState.Searching -> Unit

            SearchFeedbackState.EmptyResults -> item(key = "empty") {
                CalmSearchEmptyState(onClearSearch = { onQueryChanged("") })
            }

            SearchFeedbackState.Results -> {
                item(key = "count") {
                    QuietLine(pluralStringResource(R.plurals.search_result_count, state.results.size, state.results.size))
                }
                groups.forEach { (group, results) ->
                    item(key = "group-${group.name}") {
                        Column(
                            modifier = Modifier
                                .padding(top = 16.dp)
                                .animateItem(
                                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                                ),
                        ) {
                            SectionHeader(title = stringResource(group.labelRes))
                            GroupedCard {
                                results.forEachIndexed { index, result ->
                                    if (index > 0) GroupDivider(startInset = 64.dp)
                                    SearchResultRow(
                                        result = result,
                                        query = state.query.trim(),
                                        onClick = { onResultSelected(result) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.core_search_local_data)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = CircleShape,
        color = colors.surfaceContainerLowest,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.core_back))
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChanged,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = description },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp),
                                color = colors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.core_search_clear_query))
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = CircleShape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.12f)),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp))
        }
    }
}

@Composable
private fun SpaceShortcut(space: SearchSpace, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintedIconChip(icon = space.icon.asImageVector(), color = space.colorAccent.asColor(), size = 32.dp, iconSize = 18.dp)
            Text(
                text = localizedSpaceName(space.name),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StartTypingHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = stringResource(R.string.core_search_start_typing),
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_stays_on_phone),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuietLine(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchResultRow(
    result: LocalSearchResult,
    query: String,
    onClick: () -> Unit,
) {
    val status = stringResource(result.status.labelRes())
    val visibleTitle = result.title.ifBlank { stringResource(result.type.untitledLabelRes()) }
    val metadataSeparator = stringResource(R.string.core_metadata_separator)
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val meta = listOfNotNull(
        result.spaceName?.takeIf(String::isNotBlank)?.let { localizedSpaceName(it) },
        result.snippet.takeIf { it.isNotBlank() && it != result.title } ?: status,
    ).joinToString(metadataSeparator)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIconChip(icon = result.type.icon(), color = MaterialTheme.colorScheme.primary)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 14.dp),
        ) {
            Text(
                text = remember(visibleTitle, query, highlight) { visibleTitle.withMatch(query, highlight) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = remember(meta, query, highlight) { meta.withMatch(query, highlight) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Marks every place the typed word appears, ignoring case. */
internal fun String.withMatch(query: String, highlight: Color): AnnotatedString {
    val source = this
    return buildAnnotatedString {
        append(source)
        if (query.length < 2) return@buildAnnotatedString
        var start = source.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            addStyle(SpanStyle(background = highlight), start, start + query.length)
            start = source.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
}

@Composable
private fun CalmSearchEmptyState(
    onClearSearch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.core_search_empty),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.core_search_empty_recovery),
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        TextButton(
            onClick = onClearSearch,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(stringResource(R.string.core_search_clear_query))
        }
    }
}

/** Result groups, in the order they are shown. */
internal enum class SearchGroup(@StringRes val labelRes: Int) {
    ToSort(R.string.search_group_to_sort),
    Tasks(R.string.search_group_tasks),
    Reminders(R.string.search_group_reminders),
    Notes(R.string.search_group_notes),
    Archived(R.string.search_group_archived),
}

private fun LocalSearchResult.groupKey(): SearchGroup = when {
    status == LocalSearchStatus.Archived -> SearchGroup.Archived
    type == ItemDetailType.Capture -> SearchGroup.ToSort
    type == ItemDetailType.Task -> SearchGroup.Tasks
    type == ItemDetailType.Reminder -> SearchGroup.Reminders
    else -> SearchGroup.Notes
}

@StringRes
private fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.All -> R.string.search_filter_all
    SearchFilter.Tasks -> R.string.search_group_tasks
    SearchFilter.Reminders -> R.string.search_group_reminders
    SearchFilter.Notes -> R.string.search_group_notes
    SearchFilter.Archived -> R.string.search_group_archived
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
