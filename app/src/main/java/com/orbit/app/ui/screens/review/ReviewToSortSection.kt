package com.orbit.app.ui.screens.review

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Check
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.components.LumaMenu
import com.orbit.app.ui.components.LumaMenuItem
import com.orbit.app.ui.components.LumaMenuGap
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Archive

/**
 * One unresolved thought in the To sort list: the text, LUMA's suggestion in one quiet line,
 * and one action on the right. A ready suggestion is a single check; a thought that needs a
 * choice says so in words. Tap the row to change the suggestion; long-press for more.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ToSortRow(
    item: ToSortItem,
    timeFormat: OrbitTimeFormat,
    onAccept: () -> Unit,
    onChange: () -> Unit,
    onHideSuggestion: () -> Unit,
    onLetGo: () -> Unit,
    onOpen: () -> Unit = {},
    onSplit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val offerSplit = item.possibleThoughts >= 2 &&
        (item.state == ToSortState.Suggested || item.state == ToSortState.NoSuggestion)
    val suggestionLine = if (offerSplit) {
        pluralStringResource(R.plurals.split_offer, item.possibleThoughts, item.possibleThoughts)
    } else {
        toSortSuggestionLine(item, timeFormat)
    }
    val savedAt = stringResource(R.string.review_to_sort_saved_at, timeFormat.formatShortDateTime(item.createdAt))
    val moreLabel = stringResource(R.string.review_to_sort_more)
    val changeLabel = stringResource(R.string.review_to_sort_change)
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onChange,
                    onClickLabel = changeLabel,
                    onLongClick = { menuOpen = true },
                    onLongClickLabel = moreLabel,
                )
                .heightIn(min = 68.dp)
                .padding(start = 16.dp, top = 10.dp, end = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOf(item.text, suggestionLine, savedAt).joinToString(". ")
                    },
            ) {
                Text(
                    text = item.suggestedTitle?.takeIf { item.state == ToSortState.Suggested } ?: item.text,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = suggestionLine,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (offerSplit) {
                CompactTonalButton(stringResource(R.string.split_offer_action), onSplit)
            } else when (item.state) {
                ToSortState.Suggested -> {
                    // A short visible word; TalkBack hears what it will save, e.g. "Make it a task".
                    val acceptLabel = stringResource(acceptLabelRes(item.suggestedType))
                    FilledTonalButton(
                        onClick = onAccept,
                        contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = acceptLabel },
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.review_to_sort_accept_short),
                            modifier = Modifier.clearAndSetSemantics { },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                    }
                }
                ToSortState.NeedsChoice -> CompactTonalButton(
                    stringResource(if (item.lowConfidence) R.string.review_to_sort_sort else R.string.review_to_sort_pick_time),
                    onChange,
                )
                ToSortState.BrainDump -> CompactTonalButton(stringResource(R.string.review_to_sort_continue), onChange)
                ToSortState.NoSuggestion, ToSortState.Analyzing ->
                    CompactTonalButton(stringResource(R.string.review_to_sort_sort), onChange)
            }
        }
        LumaMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            LumaMenuItem(
                text = { Text(changeLabel) },
                leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                onClick = { menuOpen = false; onChange() },
            )
            LumaMenuItem(
                text = { Text(stringResource(R.string.review_to_sort_open)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null) },
                onClick = { menuOpen = false; onOpen() },
            )
            if (item.state == ToSortState.Suggested || item.state == ToSortState.NeedsChoice) {
                LumaMenuItem(
                    text = { Text(stringResource(R.string.review_to_sort_hide)) },
                    leadingIcon = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
                    onClick = { menuOpen = false; onHideSuggestion() },
                )
            }
            LumaMenuGap()
            LumaMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.review_menu_let_go))
                        Text(
                            text = stringResource(R.string.review_to_sort_let_go_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                onClick = { menuOpen = false; onLetGo() },
            )
        }
    }
}

@Composable
internal fun CompactTonalButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
internal fun toSortSuggestionLine(item: ToSortItem, timeFormat: OrbitTimeFormat): String = when (item.state) {
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
        item.taskDateEpochDay?.let { add(timeFormat.formatDate(java.time.LocalDate.ofEpochDay(it))) }
        item.suggestedLabels.forEach { add("#$it") }
        if (item.fromGemini) add(stringResource(R.string.review_suggestion_from_gemini))
    }.joinToString(stringResource(R.string.core_metadata_dot_separator))
}

internal fun typeLabelRes(type: SuggestedItemType?): Int = when (type) {
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.review_type_task
    SuggestedItemType.Reminder -> R.string.review_type_reminder
    SuggestedItemType.Note, null -> R.string.review_type_note
}

internal fun acceptLabelRes(type: SuggestedItemType?): Int = when (type) {
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.review_to_sort_accept_task
    SuggestedItemType.Reminder -> R.string.review_to_sort_accept_reminder
    SuggestedItemType.Note, null -> R.string.review_to_sort_accept_note
}
