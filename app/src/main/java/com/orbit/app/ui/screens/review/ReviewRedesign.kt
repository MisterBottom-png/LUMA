package com.orbit.app.ui.screens.review

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.app.R
import com.orbit.app.ui.components.GroupDivider
import com.orbit.app.ui.components.GroupedCard
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.TintedIconChip
import com.orbit.app.ui.components.rememberReducedMotion
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.time.OrbitTimeFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import com.orbit.app.ui.components.LumaMenu
import com.orbit.app.ui.components.LumaMenuItem
import com.orbit.app.ui.components.LumaMenuGap
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew

/*
 * Review, redesigned: a title with a small Ask button and a breath button, one plain
 * summary line, then To sort, Today and From earlier as quiet grouped lists, each row with
 * at most one visible action. "Sort one by one" turns To sort into a focused flow with a
 * clear end.
 */

/** Title row: Review, a breath button and an Ask button whose questions open as a menu. */
@Composable
internal fun ReviewHeader(
    title: String,
    summary: String,
    onAsk: (AskLumaPrompt?) -> Unit,
    onBreathe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var askOpen by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = onBreathe) {
                Icon(
                    imageVector = Icons.Rounded.Spa,
                    contentDescription = stringResource(R.string.review_breathe_start),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                FilledTonalButton(
                    onClick = { askOpen = true },
                    contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.review_ask_button), style = MaterialTheme.typography.labelLarge)
                }
                LumaMenu(expanded = askOpen, onDismissRequest = { askOpen = false }) {
                    AskLumaPrompt.entries.forEach { prompt ->
                        LumaMenuItem(
                            text = {
                                Text(
                                    stringResource(prompt.labelRes),
                                    fontWeight = if (prompt == AskLumaPrompt.WhatNow) FontWeight.SemiBold else null,
                                )
                            },
                            onClick = { askOpen = false; onAsk(prompt) },
                        )
                    }
                    LumaMenuGap()
                    Text(
                        text = stringResource(R.string.review_ask_subtitle),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Text(
            text = summary,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun reviewSummary(uiState: ReviewUiState): String {
    val parts = buildList {
        if (uiState.toSort.isNotEmpty()) add(stringResource(R.string.review_summary_to_sort, uiState.toSort.size))
        if (uiState.dueToday.isNotEmpty()) add(stringResource(R.string.review_summary_today, uiState.dueToday.size))
        if (uiState.carryForwardSuggestions.isNotEmpty()) {
            add(stringResource(R.string.review_summary_earlier, uiState.carryForwardSuggestions.size))
        }
    }
    return if (parts.isEmpty()) stringResource(R.string.review_summary_nothing) else parts.joinToString(" · ")
}

/** A short breath: the circle grows for two seconds and settles for three, then the panel closes. */
@Composable
internal fun BreathingPanel(onFinished: () -> Unit) {
    val reduceMotion = rememberReducedMotion()
    var inhaling by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(BreathPanelInMillis.toLong())
        inhaling = false
        delay(BreathPanelOutMillis.toLong())
        onFinished()
    }
    val scale by animateFloatAsState(
        targetValue = if (inhaling && !reduceMotion) 1f else 0.72f,
        animationSpec = tween(if (inhaling) BreathPanelInMillis else BreathPanelOutMillis),
        label = "reviewBreathPanel",
    )
    GroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Text(
                text = stringResource(if (inhaling) R.string.core_review_breathe_in else R.string.core_review_breathe_out),
                modifier = Modifier.padding(start = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private const val BreathPanelInMillis = 2_000
private const val BreathPanelOutMillis = 3_000

/** To sort as one grouped list, with "Show more" and "Accept all" under it. */
@Composable
internal fun ToSortGroup(
    items: List<ToSortItem>,
    showAll: Boolean,
    timeFormat: OrbitTimeFormat,
    onShowAllChanged: (Boolean) -> Unit,
    onAccept: (ToSortItem) -> Unit,
    onAcceptAll: () -> Unit,
    onChange: (ToSortItem) -> Unit,
    onHideSuggestion: (ToSortItem) -> Unit,
    onLetGo: (ToSortItem) -> Unit,
    onOpen: (ToSortItem) -> Unit = {},
) {
    val visible = if (showAll) items else items.take(ReviewSectionPreviewSize)
    val readyCount = items.count { it.state == ToSortState.Suggested }
    GroupedCard {
        visible.forEachIndexed { index, item ->
            if (index > 0) GroupDivider()
            ToSortRow(
                item = item,
                timeFormat = timeFormat,
                onAccept = { onAccept(item) },
                onChange = { onChange(item) },
                onHideSuggestion = { onHideSuggestion(item) },
                onLetGo = { onLetGo(item) },
                onOpen = { onOpen(item) },
            )
        }
        val hasMore = items.size > ReviewSectionPreviewSize
        if (hasMore || readyCount > 1) {
            GroupDivider(startInset = 0.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasMore) {
                    TextButton(onClick = { onShowAllChanged(!showAll) }) {
                        Text(
                            text = if (showAll) {
                                stringResource(R.string.review_show_fewer)
                            } else {
                                stringResource(R.string.review_show_more, items.size - ReviewSectionPreviewSize)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (readyCount > 1) {
                    TextButton(onClick = onAcceptAll) {
                        Text(stringResource(R.string.review_accept_all), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** Today as one list: an icon for the kind of thing, the title, and when. */
@Composable
internal fun TodayGroup(
    items: List<ReviewItem>,
    showAll: Boolean,
    timeFormat: OrbitTimeFormat,
    onShowAllChanged: (Boolean) -> Unit,
    onOpen: (ReviewItem) -> Unit,
) {
    val anyTime = stringResource(R.string.core_calendar_any_time)
    val visible = if (showAll) items else items.take(ReviewSectionPreviewSize)
    GroupedCard {
        if (items.isEmpty()) {
            Text(
                text = stringResource(R.string.review_today_empty),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        visible.forEachIndexed { index, item ->
            if (index > 0) GroupDivider(startInset = 52.dp)
            ReviewListRow(
                title = item.title,
                trailing = if (item.schedule == ReviewItemSchedule.Timed) timeFormat.formatTime(item.timestamp) else anyTime,
                isReminder = item.type == ReviewItemType.Reminder,
                onClick = { onOpen(item) },
            )
        }
        if (items.size > ReviewSectionPreviewSize) {
            GroupDivider(startInset = 0.dp)
            TextButton(
                onClick = { onShowAllChanged(!showAll) },
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Text(
                    text = if (showAll) {
                        stringResource(R.string.review_show_fewer)
                    } else {
                        stringResource(R.string.review_show_more, items.size - ReviewSectionPreviewSize)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReviewListRow(
    title: String,
    trailing: String?,
    isReminder: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isReminder) Icons.Rounded.NotificationsNone else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = stringResource(if (isReminder) R.string.core_reminder else R.string.core_task),
            tint = if (isReminder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Things whose day has passed, folded into one calm row: how many, which, and one action
 * that moves them all to tomorrow. Open it to decide one by one.
 */
@Composable
internal fun FromEarlierGroup(
    suggestions: List<CarryForwardSuggestion>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpen: (ReviewItem) -> Unit,
    onTomorrow: (ReviewItem) -> Unit,
    onChooseDate: (ReviewItem, Long) -> Unit,
    onKeepUnscheduled: (ReviewItem) -> Unit,
    onComplete: (ReviewItem) -> Unit,
) {
    val context = LocalContext.current
    GroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .heightIn(min = 64.dp)
                .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintedIconChip(icon = Icons.Rounded.History, color = MaterialTheme.colorScheme.onSurfaceVariant, size = 34.dp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_summary_earlier, suggestions.size),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = suggestions.joinToString(", ") { it.item.title },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (expanded) {
                Icon(
                    imageVector = Icons.Rounded.ExpandLess,
                    contentDescription = stringResource(R.string.core_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                CompactTonalButton(stringResource(R.string.review_move_all_tomorrow)) {
                    suggestions.forEach { onTomorrow(it.item) }
                }
            }
        }
        if (expanded) {
            suggestions.forEach { suggestion ->
                GroupDivider(startInset = 62.dp)
                EarlierRow(
                    suggestion = suggestion,
                    onOpen = { onOpen(suggestion.item) },
                    onTomorrow = { onTomorrow(suggestion.item) },
                    onChooseDate = {
                        val initial = Instant.ofEpochMilli(suggestion.item.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        showEarlierDatePicker(context, initial) { epochDay -> onChooseDate(suggestion.item, epochDay) }
                    },
                    onKeepUnscheduled = { onKeepUnscheduled(suggestion.item) },
                    onComplete = { onComplete(suggestion.item) },
                )
            }
        }
    }
}

@Composable
private fun EarlierRow(
    suggestion: CarryForwardSuggestion,
    onOpen: () -> Unit,
    onTomorrow: () -> Unit,
    onChooseDate: () -> Unit,
    onKeepUnscheduled: () -> Unit,
    onComplete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { menuOpen = true }
                .heightIn(min = 56.dp)
                .padding(start = 62.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.item.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(suggestion.guidance.labelRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        LumaMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            LumaMenuItem(
                text = { Text(stringResource(R.string.core_tomorrow)) },
                leadingIcon = { Icon(Icons.Rounded.WbTwilight, contentDescription = null) },
                onClick = { menuOpen = false; onTomorrow() },
            )
            LumaMenuItem(
                text = { Text(stringResource(R.string.core_choose_date)) },
                leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null) },
                onClick = { menuOpen = false; onChooseDate() },
            )
            if (suggestion.item.type == ReviewItemType.Task) {
                LumaMenuItem(
                    text = { Text(stringResource(R.string.core_review_keep_unscheduled)) },
                    leadingIcon = { Icon(Icons.Rounded.EventBusy, contentDescription = null) },
                    onClick = { menuOpen = false; onKeepUnscheduled() },
                )
            }
            LumaMenuItem(
                text = { Text(stringResource(R.string.core_mark_complete)) },
                leadingIcon = { Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null) },
                onClick = { menuOpen = false; onComplete() },
            )
            LumaMenuGap()
            LumaMenuItem(
                text = { Text(stringResource(R.string.review_open_item)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = { menuOpen = false; onOpen() },
            )
        }
    }
}

private fun showEarlierDatePicker(
    context: android.content.Context,
    initialDate: LocalDate,
    onSelected: (Long) -> Unit,
) {
    android.app.DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(LocalDate.of(year, month + 1, day).toEpochDay()) },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).show()
}

/** Weekly look back as a quiet row; on the weekend a short hint says now is a good time. */
@Composable
internal fun WeeklyLookBackRow(expanded: Boolean, suggested: Boolean, onToggle: () -> Unit) {
    GroupedCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (expanded) R.string.review_weekly_close else R.string.review_weekly_button),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                )
                if (suggested && !expanded) {
                    Text(
                        text = stringResource(R.string.review_weekly_weekend_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * Sort one by one: a single thought on screen, LUMA's suggestion under it, one clear choice
 * and a way to skip. It ends with "All sorted", so there is a finish line.
 */
@Composable
internal fun SortOneByOne(
    items: List<ToSortItem>,
    timeFormat: OrbitTimeFormat,
    onAccept: (ToSortItem) -> Unit,
    onChange: (ToSortItem) -> Unit,
    onLetGo: (ToSortItem) -> Unit,
    onOpen: (ToSortItem) -> Unit = {},
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val skipped = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { it.value.toList() },
        restore = { mutableStateOf(it.toSet()) },
    )) { mutableStateOf(emptySet<Long>()) }
    val total = rememberSaveable { items.size }
    val waiting = items.filter { it.captureId !in skipped.value }
    val current = waiting.firstOrNull()
    val position = (total - waiting.size + 1).coerceIn(1, total.coerceAtLeast(1))

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.padding(start = 0.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.core_close))
                }
                Text(
                    text = if (current != null) stringResource(R.string.review_one_progress, position, total) else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.width(48.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val done = if (current == null) total else position - 1
                repeat(total.coerceAtMost(MaxProgressSegments)) { index ->
                    val scaledIndex = if (total > MaxProgressSegments) index * total / MaxProgressSegments else index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                when {
                                    scaledIndex < done -> MaterialTheme.colorScheme.primary
                                    scaledIndex == done && current != null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                },
                            ),
                    )
                }
            }
            if (current == null) {
                SortOneByOneDone(skippedSome = skipped.value.isNotEmpty() && items.isNotEmpty(), onClose = onClose)
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 24.dp),
                ) {
                    SortOneByOneCard(item = current, timeFormat = timeFormat, onChange = { onChange(current) })
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(bottom = OrbitBottomNavigationDefaults.ContentClearance - 24.dp),
                ) {
                    if (current.state == ToSortState.Suggested) {
                        Button(
                            onClick = { onAccept(current) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(stringResource(acceptLabelRes(current.suggestedType)), style = MaterialTheme.typography.titleSmall)
                        }
                    } else {
                        Button(
                            onClick = { onChange(current) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) {
                            Text(
                                stringResource(
                                    when (current.state) {
                                        ToSortState.NeedsChoice -> R.string.review_to_sort_pick_time
                                        ToSortState.BrainDump -> R.string.review_to_sort_continue
                                        else -> R.string.review_to_sort_sort
                                    },
                                ),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilledTonalButton(
                            onClick = { skipped.value = skipped.value + current.captureId },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Text(stringResource(R.string.review_one_skip), style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(
                            onClick = { onLetGo(current) },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) {
                            Text(
                                stringResource(R.string.review_to_sort_let_go),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MaxProgressSegments = 12

@Composable
private fun SortOneByOneCard(item: ToSortItem, timeFormat: OrbitTimeFormat, onChange: () -> Unit) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        style = GlassSurfaceStyle.HomeCapture,
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, top = 22.dp, end = 22.dp, bottom = 8.dp)) {
            Text(
                text = stringResource(R.string.review_to_sort_saved_at, timeFormat.formatShortDateTime(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.text,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium, fontSize = 26.sp, lineHeight = 32.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (item.state) {
                ToSortState.Suggested, ToSortState.NeedsChoice -> {
                    Row(
                        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = stringResource(R.string.review_one_suggests),
                            modifier = Modifier.padding(start = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SuggestionLine(stringResource(R.string.review_one_type), stringResource(typeLabelRes(item.suggestedType)), onChange)
                    GroupDivider(startInset = 0.dp)
                    SuggestionLine(
                        stringResource(R.string.review_one_space),
                        item.suggestedSpaceName?.let { localizedSpaceName(it) } ?: stringResource(R.string.core_spaces_unfiled),
                        onChange,
                    )
                    GroupDivider(startInset = 0.dp)
                    SuggestionLine(
                        stringResource(R.string.review_one_when),
                        item.reminderAt?.let { timeFormat.formatWeekdayDateTime(it) } ?: stringResource(R.string.spaces_section_no_date),
                        onChange,
                    )
                }
                else -> Text(
                    text = toSortSuggestionLine(item, timeFormat),
                    modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionLine(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(start = 4.dp).size(18.dp),
        )
    }
}

@Composable
private fun SortOneByOneDone(skippedSome: Boolean, onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            text = stringResource(R.string.review_all_sorted_title),
            modifier = Modifier
                .padding(top = 20.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(if (skippedSome) R.string.review_one_skipped_body else R.string.review_one_done_body),
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(
            onClick = onClose,
            modifier = Modifier
                .padding(top = 28.dp)
                .heightIn(min = 52.dp),
        ) {
            Text(stringResource(R.string.review_one_back), style = MaterialTheme.typography.labelLarge)
        }
    }
}
