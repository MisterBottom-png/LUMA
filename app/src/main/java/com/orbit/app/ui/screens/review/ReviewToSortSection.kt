package com.orbit.app.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.orbit.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat

/** One unresolved thought with LUMA's suggestion, a one-tap confirm and a way to change it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToSortRow(
    item: ToSortItem,
    timeFormat: OrbitTimeFormat,
    onAccept: () -> Unit,
    onChange: () -> Unit,
    onHideSuggestion: () -> Unit,
    onLetGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val suggestionLine = toSortSuggestionLine(item, timeFormat)
    val savedAt = stringResource(R.string.review_to_sort_saved_at, timeFormat.formatShortDateTime(item.createdAt))
    SoftGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        style = GlassSurfaceStyle.Standard,
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = listOf(item.text, suggestionLine, savedAt).joinToString(". ")
                        },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = item.suggestedTitle?.takeIf { item.state == ToSortState.Suggested } ?: item.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestionLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.review_to_sort_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (item.state == ToSortState.Suggested || item.state == ToSortState.NeedsChoice) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.review_to_sort_hide)) },
                                onClick = { menuOpen = false; onHideSuggestion() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.review_to_sort_let_go)) },
                            onClick = { menuOpen = false; onLetGo() },
                        )
                    }
                }
            }
            FlowRow(
                modifier = Modifier.padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (item.state) {
                    ToSortState.Suggested -> {
                        Button(onClick = onAccept, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(acceptLabelRes(item.suggestedType)))
                        }
                        TextButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.review_to_sort_change))
                        }
                    }
                    ToSortState.NeedsChoice -> OutlinedButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.review_to_sort_pick_time))
                    }
                    ToSortState.BrainDump -> OutlinedButton(onClick = onChange, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.review_to_sort_continue))
                    }
                    ToSortState.NoSuggestion, ToSortState.Analyzing -> OutlinedButton(
                        onClick = onChange,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.review_to_sort_sort))
                    }
                }
            }
        }
    }
}

@Composable
private fun toSortSuggestionLine(item: ToSortItem, timeFormat: OrbitTimeFormat): String = when (item.state) {
    ToSortState.Analyzing -> stringResource(R.string.review_to_sort_analyzing)
    ToSortState.NoSuggestion -> stringResource(R.string.review_to_sort_no_suggestion)
    ToSortState.BrainDump -> pluralStringResource(
        R.plurals.review_to_sort_brain_dump,
        item.brainDumpPending,
        item.brainDumpPending,
    )
    ToSortState.Suggested, ToSortState.NeedsChoice -> buildList {
        add(stringResource(typeLabelRes(item.suggestedType)))
        item.suggestedSpaceName?.let { add(localizedSpaceName(it)) }
        item.reminderAt?.let { add(timeFormat.formatWeekdayDateTime(it)) }
        item.suggestedLabels.forEach { add("#$it") }
        if (item.fromGemini) add(stringResource(R.string.review_suggestion_from_gemini))
    }.joinToString(stringResource(R.string.core_metadata_dot_separator))
}

private fun typeLabelRes(type: SuggestedItemType?): Int = when (type) {
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.review_type_task
    SuggestedItemType.Reminder -> R.string.review_type_reminder
    SuggestedItemType.Note, null -> R.string.review_type_note
}

private fun acceptLabelRes(type: SuggestedItemType?): Int = when (type) {
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.review_to_sort_accept_task
    SuggestedItemType.Reminder -> R.string.review_to_sort_accept_reminder
    SuggestedItemType.Note, null -> R.string.review_to_sort_accept_note
}
