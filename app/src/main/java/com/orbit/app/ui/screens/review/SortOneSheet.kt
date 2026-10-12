package com.orbit.app.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.data.local.entity.SpaceEntity
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.ui.components.LumaModalBottomSheet
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.screens.spaces.PickTimeStep
import com.orbit.app.ui.screens.spaces.StepTitle
import com.orbit.app.ui.screens.spaces.TileShape
import com.orbit.app.ui.screens.spaces.TimeQuestionStep
import com.orbit.app.ui.screens.spaces.TypeQuestionStep
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.LocalDate
import java.time.ZoneId

/**
 * The Needs you one-item sheet (review §9.3): "Save it like this?" when Tallele has a
 * suggestion, "What should this be?" with the Space when it isn't sure, and the time
 * question for a reminder. Nothing is filed until the user chooses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOneSheet(
    state: SortOneUiState,
    spaces: List<SpaceEntity>,
    timeFormat: OrbitTimeFormat,
    onTitleChange: (String) -> Unit,
    onSelectSpace: (Long?) -> Unit,
    onSaveSuggested: () -> Unit,
    onChooseSomethingElse: () -> Unit,
    onChooseNote: () -> Unit,
    onChooseTask: () -> Unit,
    onChooseReminder: () -> Unit,
    onRemindAt: (Long) -> Unit,
    onPickTime: () -> Unit,
    onBackToTime: () -> Unit,
    onBackFromTime: () -> Unit,
    onKeepAsTask: () -> Unit,
    onDecideLater: () -> Unit,
    onMoreOptions: () -> Unit,
    onDismiss: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    val busy by rememberUpdatedState(state.busy)
    val spaceName = spaces.firstOrNull { it.id == state.spaceId }?.name?.let { localizedSpaceName(it) }
        ?: stringResource(R.string.core_inbox)
    LumaModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> target != SheetValue.Hidden || !busy },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            when (state.step) {
                SortOneStep.Confirm -> ConfirmStep(state, spaceName, timeFormat, onTitleChange, onSaveSuggested, onChooseSomethingElse, onDismiss)
                SortOneStep.Type -> TypeQuestionStep(
                    header = {
                        StatusLine(stringResource(R.string.sort_one_unsure))
                        EditableThought(state, onTitleChange)
                    },
                    busy = state.busy,
                    canDecideLater = true,
                    onChooseNote = onChooseNote,
                    onChooseTask = onChooseTask,
                    onChooseReminder = onChooseReminder,
                    onDecideLater = onDecideLater,
                    footnote = stringResource(R.string.sort_one_stays),
                    beforeTypes = { SpaceChips(spaces, state.spaceId, !state.busy, onSelectSpace) },
                )
                SortOneStep.Time -> TimeQuestionStep(
                    header = { BackHeader(state.title, kindLine(SuggestedItemType.Reminder, spaceName), onBackFromTime, !state.busy) },
                    busy = state.busy,
                    quickTimes = state.quickTimes,
                    timeFormat = timeFormat,
                    zoneId = zoneId,
                    onRemindAt = onRemindAt,
                    onPickTime = onPickTime,
                    onKeepAsTask = onKeepAsTask,
                    footnote = null,
                )
                SortOneStep.PickTime -> PickTimeStep(state.title, state.busy, timeFormat, now, zoneId, onRemindAt, onBackToTime)
            }
            if (state.step == SortOneStep.Type) {
                TextButton(onClick = onMoreOptions, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.sort_one_more_options))
                }
            }
            if (state.failed) {
                Text(
                    text = stringResource(R.string.review_sort_failed),
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    state: SortOneUiState,
    spaceName: String,
    timeFormat: OrbitTimeFormat,
    onTitleChange: (String) -> Unit,
    onSave: () -> Unit,
    onChooseSomethingElse: () -> Unit,
    onNotNow: () -> Unit,
) {
    val type = state.suggestedType ?: SuggestedItemType.Note
    StatusLine(stringResource(R.string.sort_one_in_to_sort, savedWhen(state.createdAt, timeFormat)))
    EditableThought(state, onTitleChange)
    StepTitle(stringResource(R.string.sort_one_save_like_this))
    Surface(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = TileShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(type.icon(), contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(kindLine(type, spaceName), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    text = when {
                        state.reminderAt != null -> timeFormat.formatWeekdayDateTime(state.reminderAt)
                        state.taskDateEpochDay != null -> timeFormat.formatDate(LocalDate.ofEpochDay(state.taskDateEpochDay))
                        else -> stringResource(R.string.spaces_section_no_date)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
    Button(
        onClick = onSave,
        enabled = !state.busy && state.title.isNotBlank(),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
    ) {
        Text(
            stringResource(
                when (type) {
                    SuggestedItemType.Note -> R.string.sort_one_save_note
                    SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.sort_one_save_task
                    SuggestedItemType.Reminder -> R.string.sort_one_save_reminder
                },
            ),
            style = MaterialTheme.typography.titleSmall,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onChooseSomethingElse, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sort_one_choose_else), style = MaterialTheme.typography.titleSmall)
        }
        TextButton(onClick = onNotNow, enabled = !state.busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.core_learning_not_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(text: String) {
    Row(modifier = Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Rounded.AutoAwesome,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The thought's words; changing them changes the item's title. */
@Composable
private fun EditableThought(state: SortOneUiState, onTitleChange: (String) -> Unit) {
    val label = stringResource(R.string.sort_one_edit_text)
    OutlinedTextField(
        value = state.title,
        onValueChange = onTitleChange,
        enabled = !state.busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        maxLines = 4,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpaceChips(spaces: List<SpaceEntity>, selected: Long?, enabled: Boolean, onSelect: (Long?) -> Unit) {
    Text(
        text = stringResource(R.string.core_item_detail_space),
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val choices = listOf<Pair<Long?, String>>(null to stringResource(R.string.core_inbox)) +
            spaces.filterNot { it.archived || it.hidden }.map { it.id to localizedSpaceName(it.name) }
        choices.forEach { (id, name) ->
            FilterChip(
                selected = id == selected,
                onClick = { onSelect(id) },
                enabled = enabled,
                label = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingIcon = if (id == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else {
                    null
                },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun BackHeader(title: String, subtitle: String, onBack: () -> Unit, enabled: Boolean) {
    Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, enabled = enabled) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.core_back))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun kindLine(type: SuggestedItemType, spaceName: String): String = stringResource(
    R.string.sort_one_kind_in_space,
    stringResource(
        when (type) {
            SuggestedItemType.Note -> R.string.core_note
            SuggestedItemType.Task, SuggestedItemType.MondayItem -> R.string.core_task
            SuggestedItemType.Reminder -> R.string.core_reminder
        },
    ),
    spaceName,
)

private fun SuggestedItemType.icon() = when (this) {
    SuggestedItemType.Note -> Icons.Rounded.Description
    SuggestedItemType.Task, SuggestedItemType.MondayItem -> Icons.Rounded.RadioButtonUnchecked
    SuggestedItemType.Reminder -> Icons.Rounded.Notifications
}

/** "at 08:40" today, otherwise the day and time. */
@Composable
private fun savedWhen(createdAt: Long, timeFormat: OrbitTimeFormat): String {
    val zone = ZoneId.systemDefault()
    val day = java.time.Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
    return if (day == LocalDate.now(zone)) timeFormat.formatTime(createdAt) else timeFormat.formatWeekdayDateTime(createdAt)
}
