package com.orbit.app.ui.screens.review

import android.app.DatePickerDialog
import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import android.animation.ValueAnimator
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.app.R
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.orbitScrollEdgeFade
import com.orbit.app.ui.components.userVisibleLabel
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.theme.OrbitMotion
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

/**
 * Backdrop alpha used when Review draws a local surface behind its content.
 * Custom backgrounds get strong protection in both modes; preset backgrounds
 * keep a quieter backdrop. Contract covered by [ReviewContrastTest].
 */
internal fun reviewBackdropAlpha(hasCustomBackground: Boolean, isDark: Boolean): Float = when {
    hasCustomBackground -> 0.96f
    isDark -> 0.78f
    else -> 0.74f
}

internal enum class ReviewPeriod {
    Morning,
    Midday,
    Evening,
}

internal data class ReviewContext(
    val period: ReviewPeriod,
    val weeklyReviewAvailable: Boolean,
)

internal object ReviewSchedule {
    const val MIDDAY_START_HOUR = 12
    const val EVENING_START_HOUR = 17

    fun resolve(localDateTime: LocalDateTime): ReviewContext = ReviewContext(
        period = when (localDateTime.hour) {
            in 0 until MIDDAY_START_HOUR -> ReviewPeriod.Morning
            in MIDDAY_START_HOUR until EVENING_START_HOUR -> ReviewPeriod.Midday
            else -> ReviewPeriod.Evening
        },
        weeklyReviewAvailable = localDateTime.dayOfWeek.value >= 6,
    )
}

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    timeFormat: OrbitTimeFormat,
    onReviewItemSelected: (ReviewItem) -> Unit,
    onKeepTaskActive: (ReviewLoop) -> Unit,
    onConfirmCapture: (ReviewLoop) -> Unit,
    onArchive: (ReviewLoop) -> Unit,
    onCompleteTask: (ReviewLoop) -> Unit,
    onDeferTask: (ReviewLoop) -> Unit,
    onDismissCapture: (ReviewLoop) -> Unit,
    onMakeSmaller: (ReviewLoop) -> Unit,
    onCarryForwardTomorrow: (ReviewItem) -> Unit,
    onCarryForwardToDate: (ReviewItem, Long) -> Unit,
    onKeepCarryForwardUnscheduled: (ReviewItem) -> Unit,
    onCompleteCarryForward: (ReviewItem) -> Unit,
    onWeeklyLookBackVisible: () -> Unit,
) {
    val reviewContext by rememberReviewContext()
    var showOpenLoops by rememberSaveable { mutableStateOf(false) }
    var showAllOpenLoops by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(reviewContext.period) {
        if (reviewContext.period == ReviewPeriod.Midday) {
            showOpenLoops = false
            showAllOpenLoops = false
        }
    }
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val statusBarTopPadding = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }

    var headerHeightPx by remember { mutableIntStateOf(0) }
    val measuredHeaderClearance = with(LocalDensity.current) {
        headerHeightPx.toDp() + 20.dp
    }
    val headerClearance = maxOf(statusBarTopPadding + 128.dp, measuredHeaderClearance)
    val reviewTitle = stringResource(R.string.core_review_title)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = reviewTitle },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .orbitScrollEdgeFade(
                    top = headerClearance,
                    bottom = OrbitBottomNavigationDefaults.ContentClearance,
                ),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = headerClearance,
                end = 24.dp,
                bottom = OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (reviewContext.period) {
                ReviewPeriod.Morning -> morningScan(uiState, timeFormat, onReviewItemSelected)
                ReviewPeriod.Midday -> item { MiddayBreathingSpace() }
                ReviewPeriod.Evening -> eveningSweep(
                    uiState = uiState,
                    onReviewItemSelected = onReviewItemSelected,
                    onCarryForwardTomorrow = onCarryForwardTomorrow,
                    onCarryForwardToDate = onCarryForwardToDate,
                    onKeepCarryForwardUnscheduled = onKeepCarryForwardUnscheduled,
                    onCompleteCarryForward = onCompleteCarryForward,
                )
            }

            if (
                reviewContext.period != ReviewPeriod.Midday &&
                reviewContext.weeklyReviewAvailable
            ) {
                item { WeeklyReviewPager(uiState, onWeeklyLookBackVisible) }
            }

            if (reviewContext.period != ReviewPeriod.Midday) {
                item {
                    OpenLoopsAction(
                        expanded = showOpenLoops,
                        onClick = { showOpenLoops = !showOpenLoops },
                    )
                }
                if (showOpenLoops) {
                    openLoopsWorkflow(
                        uiState = uiState,
                        onKeepTaskActive = onKeepTaskActive,
                        onConfirmCapture = onConfirmCapture,
                        onArchive = onArchive,
                        onCompleteTask = onCompleteTask,
                        onDeferTask = onDeferTask,
                        onDismissCapture = onDismissCapture,
                        onMakeSmaller = onMakeSmaller,
                        showAll = showAllOpenLoops,
                        onShowAllChanged = { showAllOpenLoops = it },
                    )
                }
                supportingReviewSections(
                    uiState = uiState,
                    onReviewItemSelected = onReviewItemSelected,
                )
            }
        }

        Column(
            modifier = Modifier
                .onSizeChanged { headerHeightPx = it.height }
                .padding(
                    start = 24.dp,
                    top = statusBarTopPadding + 26.dp,
                    end = 24.dp,
                ),
        ) {
            Text(
                text = reviewTitle,
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.core_review_subtitle),
                modifier = Modifier.padding(top = 5.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun rememberReviewContext() = produceState(
    initialValue = ReviewSchedule.resolve(LocalDateTime.now()),
) {
    while (true) {
        val now = LocalDateTime.now()
        value = ReviewSchedule.resolve(now)
        delay(reviewContextRefreshDelayMillis(now))
    }
}

internal fun reviewContextRefreshDelayMillis(now: LocalDateTime): Long {
    val nextBoundary = when {
        now.hour < ReviewSchedule.MIDDAY_START_HOUR ->
            now.withHour(ReviewSchedule.MIDDAY_START_HOUR).withMinute(0).withSecond(0).withNano(0)
        now.hour < ReviewSchedule.EVENING_START_HOUR ->
            now.withHour(ReviewSchedule.EVENING_START_HOUR).withMinute(0).withSecond(0).withNano(0)
        else -> now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
    }
    return java.time.Duration.between(now, nextBoundary).toMillis().coerceAtLeast(1L)
}

@Composable
private fun MiddayBreathingSpace() {
    val motionEnabled = remember { ValueAnimator.getDurationScale() > 0f }
    var inhaling by remember { mutableStateOf(false) }
    LaunchedEffect(motionEnabled) {
        if (motionEnabled) {
            kotlinx.coroutines.delay(250L)
            inhaling = true
            while (true) {
                kotlinx.coroutines.delay(if (inhaling) 4_000L else 6_000L)
                inhaling = !inhaling
            }
        } else {
            inhaling = true
        }
    }
    val breathScale by animateFloatAsState(
        targetValue = if (motionEnabled && inhaling) 1f else 0.7f,
        animationSpec = tween(durationMillis = if (inhaling) 4_000 else 6_000),
        label = "middayBreathScale",
    )
    val breathAlpha by animateFloatAsState(
        targetValue = if (motionEnabled && inhaling) 0.62f else 0.34f,
        animationSpec = tween(durationMillis = if (inhaling) 4_000 else 6_000),
        label = "middayBreathAlpha",
    )
    SoftGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .graphicsLayer { scaleX = breathScale; scaleY = breathScale }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = breathAlpha)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(154.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Spa,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = stringResource(
                    if (motionEnabled) {
                        if (inhaling) R.string.core_review_breathe_in else R.string.core_review_breathe_out
                    } else {
                        R.string.core_review_breathe_gently
                    },
                ),
                modifier = Modifier
                    .padding(top = 24.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.core_review_midday_subtitle),
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class WeeklyReviewPage {
    LookBack,
    LooseEnds,
    LookAhead,
}

@Composable
private fun WeeklyReviewPager(
    uiState: ReviewUiState,
    onLookBackVisible: () -> Unit,
) {
    val pages = WeeklyReviewPage.entries
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { pages.size })
    LaunchedEffect(pagerState.currentPage) {
        if (pages[pagerState.currentPage] == WeeklyReviewPage.LookBack) onLookBackVisible()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            stringResource(R.string.core_review_weekly_title),
            stringResource(R.string.core_review_weekly_subtitle),
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            pageSpacing = 12.dp,
        ) { page ->
            WeeklyReviewPageCard(page = pages[page], uiState = uiState)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pages.forEachIndexed { index, _ ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (pagerState.currentPage == index) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (pagerState.currentPage == index) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                            },
                        ),
                )
            }
        }
        Text(
            text = stringResource(
                R.string.core_review_page_position,
                stringResource(pages[pagerState.currentPage].titleRes()),
                pagerState.currentPage + 1,
                pages.size,
            ),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeeklyReviewPageCard(page: WeeklyReviewPage, uiState: ReviewUiState) {
    SoftGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = MaterialTheme.shapes.extraLarge,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(page.titleRes()),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(page.subtitleRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (page) {
                WeeklyReviewPage.LookBack -> {
                    Text(
                        text = uiState.weeklySummary?.answer
                            ?: stringResource(R.string.core_review_weekly_fallback),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                    WeeklyMetric(
                        label = stringResource(R.string.core_review_local_sources_considered),
                        count = uiState.weeklySummary?.sourceItemIds?.size ?: 0,
                    )
                    uiState.weeklySummary?.sourceItems?.firstOrNull()?.let { source ->
                        Text(
                            text = source.userVisibleLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                WeeklyReviewPage.LooseEnds -> {
                    WeeklyMetric(stringResource(R.string.core_review_open_loops), uiState.openLoops.size)
                    WeeklyMetric(stringResource(R.string.core_review_quiet_for_a_while), uiState.staleLoops.size)
                    WeeklyMetric(stringResource(R.string.core_review_waiting), uiState.waitingFor.size)
                    Text(
                        text = stringResource(R.string.core_review_loose_ends_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                WeeklyReviewPage.LookAhead -> {
                    WeeklyMetric(stringResource(R.string.core_review_due_today), uiState.dueToday.size)
                    WeeklyMetric(
                        stringResource(R.string.core_review_possible_carry_forwards),
                        uiState.carryForwardSuggestions.size,
                    )
                    WeeklyMetric(stringResource(R.string.core_someday), uiState.someday.size)
                    Text(
                        text = stringResource(R.string.core_review_look_ahead_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyMetric(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.morningScan(
    uiState: ReviewUiState,
    timeFormat: OrbitTimeFormat,
    onReviewItemSelected: (ReviewItem) -> Unit,
) {
    item {
        val summaryParts = buildList {
            add(
                pluralStringResource(
                    R.plurals.core_review_due_count,
                    uiState.dueToday.size,
                    uiState.dueToday.size,
                ),
            )
            add(
                pluralStringResource(
                    R.plurals.core_review_recent_capture_count,
                    uiState.recentInboxCaptures.size,
                    uiState.recentInboxCaptures.size,
                ),
            )
            if (uiState.waitingFor.isNotEmpty()) {
                add(
                    pluralStringResource(
                        R.plurals.core_review_waiting_count,
                        uiState.waitingFor.size,
                        uiState.waitingFor.size,
                    ),
                )
            }
        }
        ReviewSummaryCard(
            title = stringResource(R.string.core_review_morning_review),
            text = summaryParts.joinToString(
                stringResource(R.string.core_metadata_dot_separator),
            ),
            reviewState = reviewProgressLabel(uiState.unresolvedCaptures.size),
        )
    }
    item {
        SectionHeading(
            stringResource(R.string.core_review_morning_scan),
            stringResource(R.string.core_review_morning_scan_subtitle),
        )
    }
    if (uiState.dueToday.isNotEmpty()) {
        item { Subheading(stringResource(R.string.core_review_due_today)) }
        items(uiState.dueToday, key = { "due_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = if (item.schedule == ReviewItemSchedule.DateOnly) {
                    stringResource(R.string.core_today)
                } else {
                    timeFormat.formatTime(item.timestamp)
                },
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
    if (uiState.recentInboxCaptures.isNotEmpty()) {
        item { Subheading(stringResource(R.string.core_review_recent_inbox_captures)) }
        items(uiState.recentInboxCaptures, key = { "recent_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = item.reviewReason?.let { stringResource(it.labelRes()) },
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
    if (uiState.morningSuggestion != null) item {
        SmallActionCard(
            title = stringResource(R.string.core_review_one_small_place),
            suggestion = uiState.morningSuggestion,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.eveningSweep(
    uiState: ReviewUiState,
    onReviewItemSelected: (ReviewItem) -> Unit,
    onCarryForwardTomorrow: (ReviewItem) -> Unit,
    onCarryForwardToDate: (ReviewItem, Long) -> Unit,
    onKeepCarryForwardUnscheduled: (ReviewItem) -> Unit,
    onCompleteCarryForward: (ReviewItem) -> Unit,
) {
    item {
        val unresolvedCount = uiState.unresolvedCaptures.size
        val completedCount = uiState.completedToday.size
        val carryCount = uiState.carryForwardSuggestions.size
        ReviewSummaryCard(
            title = stringResource(R.string.core_review_evening_review),
            text = listOf(
                pluralStringResource(
                    R.plurals.core_review_unresolved_capture_count,
                    unresolvedCount,
                    unresolvedCount,
                ),
                pluralStringResource(
                    R.plurals.core_review_done_today_count,
                    completedCount,
                    completedCount,
                ),
                pluralStringResource(
                    R.plurals.core_review_carry_forward_count,
                    carryCount,
                    carryCount,
                ),
            ).joinToString(stringResource(R.string.core_metadata_dot_separator)),
            reviewState = reviewProgressLabel(
                uiState.unresolvedCaptures.size + uiState.carryForwardSuggestions.size,
            ),
        )
    }
    item {
        SectionHeading(
            stringResource(R.string.core_review_evening_review),
            stringResource(R.string.core_review_evening_subtitle),
        )
    }
    if (uiState.unresolvedCaptures.isNotEmpty()) {
        item { Subheading(stringResource(R.string.core_review_unresolved_captures)) }
        items(uiState.unresolvedCaptures, key = { "unresolved_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = item.reviewReason?.let { stringResource(it.labelRes()) },
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
    if (uiState.completedToday.isNotEmpty()) {
        item { Subheading(stringResource(R.string.core_review_done_today)) }
        items(uiState.completedToday, key = { "completed_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = stringResource(R.string.core_review_done_today),
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
    if (uiState.carryForwardSuggestions.isNotEmpty()) {
        item { Subheading(stringResource(R.string.core_review_carry_forward)) }
        items(
            uiState.carryForwardSuggestions,
            key = { "carry_${it.item.key}" },
        ) { suggestion ->
            CarryForwardDecisionCard(
                suggestion = suggestion,
                onOpen = { onReviewItemSelected(suggestion.item) },
                onTomorrow = { onCarryForwardTomorrow(suggestion.item) },
                onChooseDate = { epochDay ->
                    onCarryForwardToDate(suggestion.item, epochDay)
                },
                onKeepUnscheduled = { onKeepCarryForwardUnscheduled(suggestion.item) },
                onMarkComplete = { onCompleteCarryForward(suggestion.item) },
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CarryForwardDecisionCard(
    suggestion: CarryForwardSuggestion,
    onOpen: () -> Unit,
    onTomorrow: () -> Unit,
    onChooseDate: (Long) -> Unit,
    onKeepUnscheduled: () -> Unit,
    onMarkComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zoneId = ZoneId.systemDefault()
    val initialDate = Instant.ofEpochMilli(suggestion.item.timestamp)
        .atZone(zoneId)
        .toLocalDate()

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReviewItemRow(
            item = suggestion.item,
            badge = stringResource(suggestion.guidance.labelRes()),
            onClick = onOpen,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AssistChip(
                onClick = onTomorrow,
                label = { Text(stringResource(R.string.core_tomorrow)) },
            )
            AssistChip(
                onClick = {
                    showCarryForwardDatePicker(context, initialDate, onChooseDate)
                },
                label = { Text(stringResource(R.string.core_choose_date)) },
            )
            if (suggestion.item.type == ReviewItemType.Task) {
                AssistChip(
                    onClick = onKeepUnscheduled,
                    label = { Text(stringResource(R.string.core_review_keep_unscheduled)) },
                )
            }
            AssistChip(
                onClick = onMarkComplete,
                label = { Text(stringResource(R.string.core_mark_complete)) },
                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
            )
        }
    }
}

private fun showCarryForwardDatePicker(
    context: android.content.Context,
    initialDate: LocalDate,
    onSelected: (Long) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, day ->
            onSelected(LocalDate.of(year, month + 1, day).toEpochDay())
        },
        initialDate.year,
        initialDate.monthValue - 1,
        initialDate.dayOfMonth,
    ).show()
}

private fun androidx.compose.foundation.lazy.LazyListScope.openLoopsWorkflow(
    uiState: ReviewUiState,
    onKeepTaskActive: (ReviewLoop) -> Unit,
    onConfirmCapture: (ReviewLoop) -> Unit,
    onArchive: (ReviewLoop) -> Unit,
    onCompleteTask: (ReviewLoop) -> Unit,
    onDeferTask: (ReviewLoop) -> Unit,
    onDismissCapture: (ReviewLoop) -> Unit,
    onMakeSmaller: (ReviewLoop) -> Unit,
    showAll: Boolean,
    onShowAllChanged: (Boolean) -> Unit,
) {
    item {
        SectionHeading(
            stringResource(R.string.core_review_open_loops),
            stringResource(R.string.core_review_open_loops_subtitle),
        )
    }
    if (uiState.openLoops.isEmpty()) {
        item { EmptyMessage(stringResource(R.string.core_review_no_open_loops)) }
    } else {
        items(if (showAll) uiState.openLoops else uiState.openLoops.take(3), key = { "reset_${it.key}" }) { loop ->
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
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
        if (uiState.openLoops.size > 3) {
            item {
                FilledTonalButton(
                    onClick = { onShowAllChanged(!showAll) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(if (showAll) R.string.core_review_show_fewer_open_loops else R.string.core_review_show_all_open_loops))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.supportingReviewSections(
    uiState: ReviewUiState,
    onReviewItemSelected: (ReviewItem) -> Unit,
) {
    if (uiState.staleLoops.isNotEmpty()) {
        item {
            SectionHeading(
                title = stringResource(R.string.core_review_stale_loops),
                subtitle = pluralStringResource(
                    R.plurals.core_review_quiet_days_or_more,
                    uiState.staleLoopDays,
                    uiState.staleLoopDays,
                ),
            )
        }
        items(uiState.staleLoops, key = { "stale_${it.key}" }) { loop ->
            LoopRow(
                loop = loop,
                badge = pluralStringResource(
                    R.plurals.core_review_no_update_days,
                    uiState.staleLoopDays,
                    uiState.staleLoopDays,
                ),
                onClick = onReviewItemSelected,
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }

    if (uiState.waitingFor.isNotEmpty()) {
        item {
            SectionHeading(
                title = stringResource(R.string.core_waiting_for),
                subtitle = stringResource(R.string.core_review_waiting_for_subtitle),
            )
        }
        items(uiState.waitingFor, key = { "waiting_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = stringResource(R.string.core_waiting_for),
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }

    if (uiState.someday.isNotEmpty()) {
        item {
            SectionHeading(
                title = stringResource(R.string.core_someday),
                subtitle = stringResource(R.string.core_review_someday_subtitle),
            )
        }
        items(uiState.someday, key = { "someday_${it.key}" }) { item ->
            ReviewItemRow(
                item = item,
                badge = stringResource(R.string.core_someday),
                onClick = reviewRowClick(item, onReviewItemSelected),
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                ),
            )
        }
    }
}

@Composable
private fun OpenLoopsAction(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.58f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Icon(
            Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(
                if (expanded) {
                    R.string.core_review_close_open_loops
                } else {
                    R.string.core_review_sort_open_loops
                },
            ),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 14.dp, bottom = 2.dp)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
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
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
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
                modifier = Modifier.semantics { heading() },
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
    onClick: () -> Unit,
    badge: String?,
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { role = Role.Button },
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
private fun LoopRow(
    loop: ReviewLoop,
    badge: String,
    onClick: (ReviewItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = ReviewItem(
        id = loop.id,
        type = if (loop.type == ReviewLoopType.Task) {
            ReviewItemType.Task
        } else {
            ReviewItemType.Capture
        },
        title = loop.title,
        timestamp = loop.updatedAt,
    )
    ReviewItemRow(
        item = item,
        badge = badge,
        onClick = reviewRowClick(item, onClick),
        modifier = modifier,
    )
}

internal fun reviewRowClick(
    item: ReviewItem,
    onReviewItemSelected: (ReviewItem) -> Unit,
): () -> Unit = { onReviewItemSelected(item) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResetLoopCard(
    loop: ReviewLoop,
    smallerAction: ReviewSuggestion?,
    onKeepTaskActive: () -> Unit,
    onConfirmCapture: () -> Unit,
    onArchive: () -> Unit,
    onCompleteTask: () -> Unit,
    onDeferTask: () -> Unit,
    onDismissCapture: () -> Unit,
    onMakeSmaller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Text(
                text = loop.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(loop.reviewReasonRes()),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(loop.actionExplanationRes()),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            smallerAction?.let {
                Text(
                    text = stringResource(it.source.labelRes()),
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
                    AssistChip(
                        onClick = onKeepTaskActive,
                        label = { Text(stringResource(R.string.core_review_keep_active)) },
                    )
                    AssistChip(
                        onClick = onCompleteTask,
                        label = { Text(stringResource(R.string.core_review_mark_task_done)) },
                        leadingIcon = {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        },
                    )
                    AssistChip(
                        onClick = onDeferTask,
                        label = { Text(stringResource(R.string.core_review_defer_to_someday)) },
                        leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                    )
                } else {
                    AssistChip(
                        onClick = onConfirmCapture,
                        label = {
                            Text(
                                stringResource(
                                    if (loop.hasPendingBrainDump) {
                                        R.string.core_review_resume_brain_dump
                                    } else {
                                        R.string.core_review_confirm_as_someday
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        },
                    )
                    AssistChip(
                        onClick = onDismissCapture,
                        label = { Text(stringResource(R.string.core_review_dismiss_capture)) },
                    )
                }
                AssistChip(
                    onClick = onArchive,
                    label = {
                        Text(
                            if (loop.type == ReviewLoopType.Task) {
                                stringResource(R.string.core_review_archive_task)
                            } else {
                                stringResource(R.string.core_review_archive_source)
                            },
                        )
                    },
                    leadingIcon = { Icon(Icons.Rounded.Archive, contentDescription = null) },
                )
                AssistChip(
                    onClick = onMakeSmaller,
                    label = { Text(stringResource(R.string.core_review_make_smaller)) },
                    leadingIcon = { Icon(Icons.Rounded.Spa, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun SmallActionCard(
    title: String,
    suggestion: ReviewSuggestion?,
) {
    SoftGlassSurface(
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
                    text = stringResource(it.source.labelRes()),
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
private fun reviewProgressLabel(pendingCount: Int): String = if (pendingCount == 0) {
    stringResource(R.string.core_review_clear)
} else {
    pluralStringResource(
        R.plurals.core_review_pending_decisions,
        pendingCount,
        pendingCount,
    )
}

@StringRes
private fun ReviewLoop.reviewReasonRes(): Int = when (type) {
    ReviewLoopType.Task -> R.string.core_review_reason_open_task
    ReviewLoopType.Capture -> if (hasPendingBrainDump) {
        R.string.core_review_reason_brain_dump
    } else {
        R.string.core_review_reason_unfinalized_capture
    }
}

@StringRes
private fun ReviewLoop.actionExplanationRes(): Int = when (type) {
    ReviewLoopType.Task -> R.string.core_review_task_action_explanation

    ReviewLoopType.Capture -> if (hasPendingBrainDump) {
        R.string.core_review_brain_dump_action_explanation
    } else {
        R.string.core_review_capture_action_explanation
    }
}

@StringRes
private fun ReviewReason.labelRes(): Int = when (this) {
    ReviewReason.UnfinalizedCapture -> R.string.core_review_reason_unfinalized_capture
}

@StringRes
private fun CarryForwardGuidance.labelRes(): Int = when (this) {
    CarryForwardGuidance.ChooseNewDayOrSmallerStep ->
        R.string.core_review_choose_day_or_smaller

    CarryForwardGuidance.RescheduleIfRelevant ->
        R.string.core_review_reschedule_if_relevant
}

@StringRes
private fun ReviewSuggestionSource.labelRes(): Int = when (this) {
    ReviewSuggestionSource.Gemini -> R.string.core_review_suggested_by_gemini
    ReviewSuggestionSource.Local -> R.string.core_review_local_suggestion
    ReviewSuggestionSource.LocalFallback -> R.string.core_review_local_fallback
}

@StringRes
private fun WeeklyReviewPage.titleRes(): Int = when (this) {
    WeeklyReviewPage.LookBack -> R.string.core_review_look_back
    WeeklyReviewPage.LooseEnds -> R.string.core_review_loose_ends
    WeeklyReviewPage.LookAhead -> R.string.core_review_look_ahead
}

@StringRes
private fun WeeklyReviewPage.subtitleRes(): Int = when (this) {
    WeeklyReviewPage.LookBack -> R.string.core_review_look_back_subtitle
    WeeklyReviewPage.LooseEnds -> R.string.core_review_loose_ends_subtitle
    WeeklyReviewPage.LookAhead -> R.string.core_review_look_ahead_subtitle
}
