package com.orbit.app.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.R
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureLifeSignal
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat

/**
 * The start of every Brain Dump: all thoughts on one calm list. Ticked rows are the
 * ones Tallele is sure about; the user sees type, Space and time for each before
 * saving, so "Save N" is still the user's confirmation. Unclear ones go one by one.
 */
@Composable
internal fun BrainDumpOverview(
    state: BrainDumpInteractionState,
    timeFormat: OrbitTimeFormat,
    callbacks: BrainDumpCallbacks,
    modifier: Modifier = Modifier,
) {
    val rows = state.overviewRows
    val tickedCount = rows.count { it.ticked }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.core_capture_brain_dump_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = pluralStringResource(R.plurals.brain_overview_subtitle, rows.size, rows.size),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.handledCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.brain_overview_already_sorted, state.handledCount, state.handledCount),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        ) {
            Column {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .padding(start = 60.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)),
                        )
                    }
                    OverviewRow(
                        row = row,
                        timeFormat = timeFormat,
                        enabled = !state.actionInProgress,
                        onToggle = { callbacks.onToggleRow(row.sourceKey) },
                        onOpen = { callbacks.onOpenRow(row.sourceKey) },
                        onSplit = { callbacks.onSplitRow(row.sourceKey) },
                    )
                }
            }
        }

        BrainDumpInlineStatus(
            status = state.status,
            warning = state.warning,
            onUndo = callbacks.onUndoSkip,
            onRetry = callbacks.onRetry,
            modifier = Modifier.padding(top = 8.dp),
        )

        // With nothing ticked, the main button starts with the first thought instead of
        // showing a disabled "Save 0".
        val firstRow = rows.firstOrNull()
        Button(
            onClick = {
                if (tickedCount > 0) callbacks.onSaveTicked() else firstRow?.let { callbacks.onOpenRow(it.sourceKey) }
            },
            enabled = (tickedCount > 0 || firstRow != null) && !state.actionInProgress,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .heightIn(min = 56.dp),
        ) {
            if (state.actionInProgress) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (tickedCount > 0) {
                        pluralStringResource(R.plurals.brain_overview_save, tickedCount, tickedCount)
                    } else {
                        stringResource(R.string.review_sort_one_by_one)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                )
            }
        }
        if (tickedCount in 1 until rows.size) {
            Text(
                text = stringResource(R.string.brain_overview_rest_one_by_one),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = callbacks.onKeepAsOneNote, enabled = !state.actionInProgress) {
                Text(
                    stringResource(
                        if (state.handledCount > 0) R.string.brain_overview_keep_rest else R.string.brain_overview_keep_one_note,
                    ),
                )
            }
            TextButton(onClick = callbacks.onFinishLater, enabled = !state.actionInProgress) {
                Text(
                    text = stringResource(R.string.core_brain_dump_finish_later),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewRow(
    row: BrainDumpOverviewRow,
    timeFormat: OrbitTimeFormat,
    enabled: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onSplit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tickLabel = stringResource(R.string.brain_overview_tick, row.draft.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onOpen)
            .padding(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .toggleable(
                    value = row.ticked,
                    enabled = enabled && row.canTick,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .semantics { contentDescription = tickLabel },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .then(
                        if (row.ticked) {
                            Modifier.background(colors.primary, CircleShape)
                        } else {
                            Modifier.border(1.5.dp, colors.onSurface.copy(alpha = if (row.canTick) 0.35f else 0.15f), CircleShape)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (row.ticked) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Text(
                text = row.draft.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = overviewMeta(row, timeFormat),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val hint = when {
                row.failed -> stringResource(R.string.brain_overview_failed) to colors.error
                !row.canTick -> stringResource(R.string.brain_overview_needs_time) to needsYouColor()
                !row.ticked -> stringResource(R.string.brain_overview_needs_you) to needsYouColor()
                else -> null
            }
            hint?.let { (text, color) ->
                Text(
                    text = text,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                )
            }
            if (row.isList) {
                TextButton(
                    onClick = onSplit,
                    enabled = enabled,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text(stringResource(R.string.brain_overview_split_list), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun overviewMeta(row: BrainDumpOverviewRow, timeFormat: OrbitTimeFormat): String = buildList {
    add(
        stringResource(
            when (row.draft.type) {
                SuggestedItemType.Note -> R.string.core_note
                SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.core_task
                SuggestedItemType.Reminder -> R.string.core_reminder
            },
        ),
    )
    add(row.spaceName?.let { localizedSpaceName(it) } ?: stringResource(R.string.core_inbox))
    row.draft.scheduledDateEpochDay?.let { add(timeFormat.formatDate(java.time.LocalDate.ofEpochDay(it))) }
        ?: row.draft.scheduledAt?.let { add(timeFormat.formatWeekdayDateTime(it)) }
    when (row.lifeSignal) {
        CaptureLifeSignal.Someday -> add(stringResource(R.string.core_someday))
        CaptureLifeSignal.WaitingFor -> add(stringResource(R.string.core_waiting_for))
        else -> Unit
    }
}.joinToString(stringResource(R.string.core_metadata_dot_separator))

/** "Looks like 3 thoughts · Split": offered, never done without the user. */
@Composable
internal fun SplitOffer(
    count: Int,
    enabled: Boolean,
    onSplit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CallSplit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.split_offer, count, count),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onSplit, enabled = enabled) {
                Text(stringResource(R.string.split_offer_action))
            }
        }
    }
}

/** A calm amber that stays readable on both themes. */
@Composable
private fun needsYouColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFFE6B566) else Color(0xFF8A5A0E)
