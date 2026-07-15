package com.orbit.app.ui.screens.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.domain.analyzer.TinyActionSuggestion
import com.orbit.app.ui.components.GlassSurface
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.LocalOrbitUsesCustomBackground
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.userVisibleLabel
import com.orbit.app.ui.time.OrbitTimeFormat

private enum class ReviewMode(val label: String, val icon: ImageVector) {
    Morning("Morning", Icons.Rounded.LightMode),
    Evening("Evening", Icons.Rounded.NightsStay),
    Weekly("Weekly", Icons.Rounded.Schedule),
    Reset("Reset", Icons.Rounded.Refresh),
}

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    timeFormat: OrbitTimeFormat,
    onReminderSelected: (Long) -> Unit,
    onKeepTaskActive: (ReviewLoop) -> Unit,
    onConfirmCapture: (ReviewLoop) -> Unit,
    onArchive: (ReviewLoop) -> Unit,
    onCompleteTask: (ReviewLoop) -> Unit,
    onDeferTask: (ReviewLoop) -> Unit,
    onDismissCapture: (ReviewLoop) -> Unit,
    onMakeSmaller: (ReviewLoop) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(ReviewMode.Morning) }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusBarTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }
    val usesCustomBackground = LocalOrbitUsesCustomBackground.current
    val themeBackground = MaterialTheme.colorScheme.background
    val backdropColor = themeBackground.copy(
        alpha = reviewBackdropAlpha(
            hasCustomBackground = usesCustomBackground,
            isDark = themeBackground.luminance() < 0.5f,
        ),
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(backdropColor)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 24.dp,
            top = (64.dp - statusBarTopPadding).coerceAtLeast(24.dp),
            end = 24.dp,
            bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Review",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "A quiet check-in. Nothing here is a score.",
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReviewModeSelector(mode = mode, onModeSelected = { mode = it })
        }

        when (mode) {
            ReviewMode.Morning -> morningScan(uiState, timeFormat, onReminderSelected)
            ReviewMode.Evening -> eveningSweep(uiState, onReminderSelected)
            ReviewMode.Weekly -> weeklyReview(uiState)
            ReviewMode.Reset -> resetMode(
                uiState = uiState,
                onKeepTaskActive = onKeepTaskActive,
                onConfirmCapture = onConfirmCapture,
                onArchive = onArchive,
                onCompleteTask = onCompleteTask,
                onDeferTask = onDeferTask,
                onDismissCapture = onDismissCapture,
                onMakeSmaller = onMakeSmaller,
            )
        }

        if (uiState.staleLoops.isNotEmpty()) item {
            SectionHeading(
                title = "Stale loops",
                subtitle = "Quiet for ${uiState.staleLoopDays} days or more",
            )
        }
        if (uiState.staleLoops.isNotEmpty()) {
            items(uiState.staleLoops, key = { "stale_${it.key}" }) { loop ->
                LoopRow(
                    loop = loop,
                    badge = "Needs review: no update for ${uiState.staleLoopDays} days.",
                )
            }
        }

        if (uiState.waitingFor.isNotEmpty()) item {
            SectionHeading(
                title = "Waiting for",
                subtitle = "Deferred tasks waiting on an external response",
            )
        }
        if (uiState.waitingFor.isNotEmpty()) {
            items(uiState.waitingFor, key = { "waiting_${it.key}" }) { item ->
                ReviewItemRow(item = item, badge = "Waiting for")
            }
        }

        if (uiState.someday.isNotEmpty()) item {
            SectionHeading(
                title = "Someday",
                subtitle = "Quiet ideas, not urgent tasks",
            )
        }
        if (uiState.someday.isNotEmpty()) {
            items(uiState.someday, key = { "someday_${it.key}" }) { item ->
                ReviewItemRow(item = item, badge = "Someday")
            }
        }
    }
}

internal fun reviewBackdropAlpha(hasCustomBackground: Boolean, isDark: Boolean): Float = when {
    hasCustomBackground -> 0.96f
    isDark -> 0.78f
    else -> 0.74f
}

private fun androidx.compose.foundation.lazy.LazyListScope.weeklyReview(
    uiState: ReviewUiState,
) {
    item {
        ReviewSummaryCard(
            title = "Weekly review",
            text = buildList {
                val sourceCount = uiState.weeklySummary?.sourceItemIds?.size ?: 0
                add("$sourceCount local sources")
                if (uiState.staleLoops.isNotEmpty()) add("${uiState.staleLoops.size} stale loops")
            }.joinToString(" · "),
            reviewState = reviewProgressLabel(uiState.staleLoops.size),
        )
    }
    item {
        SectionHeading("Weekly review", "A short sourced summary from local items")
    }
    item {
        SmallActionCard(
            title = if (uiState.weeklySummary?.fromGemini == true) {
                "Gemini weekly summary"
            } else {
                "Local weekly summary"
            },
            suggestion = uiState.weeklySummary?.let {
                TinyActionSuggestion(
                    sourceKey = "weekly",
                    sourceTitle = it.sourceItems.joinToString { source -> source.userVisibleLabel() },
                    action = it.answer,
                    sourceLabel = if (it.fromGemini) "Sources validated" else "Local sources",
                )
            },
        )
    }
    uiState.weeklySummary?.sourceItems?.takeIf { it.isNotEmpty() }?.let { sources ->
        item {
            SourceList(title = "Sources", sources = sources)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.morningScan(
    uiState: ReviewUiState,
    timeFormat: OrbitTimeFormat,
    onReminderSelected: (Long) -> Unit,
) {
    item {
        ReviewSummaryCard(
            title = "Morning review",
            text = buildList {
                add("${uiState.dueToday.size} due")
                add("${uiState.recentInboxCaptures.size} recent captures")
                if (uiState.waitingFor.isNotEmpty()) add("${uiState.waitingFor.size} waiting")
            }.joinToString(" · "),
            reviewState = reviewProgressLabel(uiState.unresolvedCaptures.size),
        )
    }
    item {
        SectionHeading("Morning scan", "See the shape of today, then begin gently")
    }
    if (uiState.dueToday.isNotEmpty()) {
        item { Subheading("Due today") }
        items(uiState.dueToday, key = { "due_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = timeFormat.formatTime(item.timestamp),
                onClick = item.takeIf { it.type == ReviewItemType.Reminder }
                    ?.let { { onReminderSelected(it.id) } },
            )
        }
    }
    if (uiState.recentInboxCaptures.isNotEmpty()) {
        item { Subheading("Recent inbox captures") }
        items(uiState.recentInboxCaptures, key = { "recent_${it.key}" }) { item ->
            ReviewItemRow(item = item, badge = item.reviewReason)
        }
    }
    if (uiState.morningSuggestion != null) item {
        SmallActionCard(
            title = "One small place to start",
            suggestion = uiState.morningSuggestion,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.eveningSweep(
    uiState: ReviewUiState,
    onReminderSelected: (Long) -> Unit,
) {
    item {
        ReviewSummaryCard(
            title = "Evening review",
            text = buildList {
                add("${uiState.unresolvedCaptures.size} unresolved captures")
                add("${uiState.completedToday.size} done today")
                add("${uiState.carryForwardSuggestions.size} to carry forward")
            }.joinToString(" · "),
            reviewState = reviewProgressLabel(
                uiState.unresolvedCaptures.size + uiState.carryForwardSuggestions.size,
            ),
        )
    }
    item {
        SectionHeading("Evening review", "Close what you can; carry the rest without pressure")
    }
    if (uiState.unresolvedCaptures.isNotEmpty()) {
        item { Subheading("Unresolved captures") }
        items(uiState.unresolvedCaptures, key = { "unresolved_${it.key}" }) { item ->
            ReviewItemRow(item = item, badge = item.reviewReason)
        }
    }
    if (uiState.completedToday.isNotEmpty()) {
        item { Subheading("Done today") }
        items(uiState.completedToday, key = { "completed_${it.key}" }) { item ->
            ReviewItemRow(item = item, badge = "Done today")
        }
    }
    if (uiState.carryForwardSuggestions.isNotEmpty()) {
        item { Subheading("Carry forward") }
        items(
            uiState.carryForwardSuggestions,
            key = { "carry_${it.item.key}" },
        ) { suggestion ->
            ReviewItemRow(
                item = suggestion.item,
                badge = suggestion.suggestion,
                onClick = suggestion.item.takeIf { it.type == ReviewItemType.Reminder }
                    ?.let { { onReminderSelected(it.id) } },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.resetMode(
    uiState: ReviewUiState,
    onKeepTaskActive: (ReviewLoop) -> Unit,
    onConfirmCapture: (ReviewLoop) -> Unit,
    onArchive: (ReviewLoop) -> Unit,
    onCompleteTask: (ReviewLoop) -> Unit,
    onDeferTask: (ReviewLoop) -> Unit,
    onDismissCapture: (ReviewLoop) -> Unit,
    onMakeSmaller: (ReviewLoop) -> Unit,
) {
    item {
        SectionHeading("Reset mode", "Give each open loop a kind, deliberate decision")
    }
    if (uiState.openLoops.isEmpty()) {
        item { EmptyMessage("No open loops need a decision.") }
    } else {
        items(uiState.openLoops.take(3), key = { "reset_${it.key}" }) { loop ->
            ResetLoopCard(
                loop = loop,
                smallerAction = uiState.smallerAction?.takeIf { it.sourceKey == loop.key },
                onKeepTaskActive = { onKeepTaskActive(loop) },
                onConfirmCapture = { onConfirmCapture(loop) },
                onArchive = { onArchive(loop) },
                onCompleteTask = { onCompleteTask(loop) },
                onDeferTask = { onDeferTask(loop) },
                onDismissCapture = { onDismissCapture(loop) },
                onMakeSmaller = { onMakeSmaller(loop) },
            )
        }
        if (uiState.openLoops.size > 3) {
            item { EmptyMessage("${uiState.openLoops.size - 3} more will wait for the next small pass.") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewModeSelector(
    mode: ReviewMode,
    onModeSelected: (ReviewMode) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReviewMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeSelected(option) },
                label = { Text(option.label, maxLines = 1) },
                leadingIcon = {
                    Icon(
                        option.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                colors = reviewModeChipColors(),
            )
        }
    }
}

@Composable
private fun reviewModeChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.24f),
    labelColor = MaterialTheme.colorScheme.onSurface,
    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
)

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Subheading(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 7.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ReviewSummaryCard(title: String, text: String, reviewState: String) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = reviewState,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewItemRow(
    item: ReviewItem,
    badge: String? = item.supportingText,
    onClick: (() -> Unit)? = null,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = item.title,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            badge?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LoopRow(loop: ReviewLoop, badge: String) {
    ReviewItemRow(
        item = ReviewItem(
            id = loop.id,
            type = if (loop.type == ReviewLoopType.Task) {
                ReviewItemType.Task
            } else {
                ReviewItemType.Capture
            },
            title = loop.title,
            timestamp = loop.updatedAt,
        ),
        badge = badge,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResetLoopCard(
    loop: ReviewLoop,
    smallerAction: TinyActionSuggestion?,
    onKeepTaskActive: () -> Unit,
    onConfirmCapture: () -> Unit,
    onArchive: () -> Unit,
    onCompleteTask: () -> Unit,
    onDeferTask: () -> Unit,
    onDismissCapture: () -> Unit,
    onMakeSmaller: () -> Unit,
) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Text(
                text = loop.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = loop.reviewReason(),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = loop.actionExplanation(),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            smallerAction?.let {
                Text(
                    text = it.sourceLabel,
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = it.action,
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            FlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (loop.type == ReviewLoopType.Task) {
                    AssistChip(onClick = onKeepTaskActive, label = { Text("Keep active") })
                    AssistChip(
                        onClick = onCompleteTask,
                        label = { Text("Mark task done") },
                        leadingIcon = {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        },
                    )
                    AssistChip(
                        onClick = onDeferTask,
                        label = { Text("Defer to Someday") },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    )
                } else {
                    AssistChip(
                        onClick = onConfirmCapture,
                        label = { Text("Confirm as Someday task") },
                        leadingIcon = {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        },
                    )
                    AssistChip(onClick = onDismissCapture, label = { Text("Dismiss capture") })
                }
                AssistChip(
                    onClick = onArchive,
                    label = {
                        Text(
                            if (loop.type == ReviewLoopType.Task) {
                                "Archive task"
                            } else {
                                "Archive source"
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                )
                AssistChip(
                    onClick = onMakeSmaller,
                    label = { Text("Make smaller") },
                    leadingIcon = { Icon(Icons.Rounded.Spa, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun SmallActionCard(
    title: String,
    suggestion: TinyActionSuggestion?,
) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 7.dp),
        shape = MaterialTheme.shapes.extraLarge,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(
                    Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = suggestion?.action.orEmpty(),
                modifier = Modifier.padding(top = 9.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
            suggestion?.let {
                Text(
                    text = it.sourceLabel,
                    modifier = Modifier.padding(top = 5.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceList(title: String, sources: List<AiSourceItem>) {
    Column(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sources.forEach { source ->
            Text(
                text = source.userVisibleLabel(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun reviewProgressLabel(pendingCount: Int): String = when (pendingCount) {
    0 -> "Review clear"
    1 -> "1 item still needs a review decision"
    else -> "$pendingCount items still need a review decision"
}

private fun ReviewLoop.reviewReason(): String = when (type) {
    ReviewLoopType.Task -> "Needs review because this task is still open."
    ReviewLoopType.Capture -> "Needs review because this capture is not finalized or dismissed."
}

private fun ReviewLoop.actionExplanation(): String = when (type) {
    ReviewLoopType.Task ->
        "Keep active leaves it open; Someday defers it; Done completes it; Archive hides it."

    ReviewLoopType.Capture ->
        "Confirm creates one task; Dismiss creates no item; Archive stores the source away."
}
