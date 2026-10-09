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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import android.os.Build
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
import com.orbit.app.ui.screens.home.sortedMessageRes
import com.orbit.app.domain.analyzer.ReviewLoop
import com.orbit.app.domain.analyzer.ReviewLoopType
import com.orbit.app.ui.components.GlassSurfaceStyle
import com.orbit.app.ui.components.LumaModalBottomSheet
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
    onUndoTaskMutation: (Long) -> Unit,
    onTaskUndoExpired: (Long) -> Unit,
    onCarryForwardTomorrow: (ReviewItem) -> Unit,
    onCarryForwardToDate: (ReviewItem, Long) -> Unit,
    onKeepCarryForwardUnscheduled: (ReviewItem) -> Unit,
    onCompleteCarryForward: (ReviewItem) -> Unit,
    onWeeklyLookBackVisible: () -> Unit,
    onAskLuma: (AskLumaPrompt?) -> Unit = {},
    onAcceptToSort: (ToSortItem) -> Unit = {},
    onChangeToSort: (ToSortItem) -> Unit = {},
    onHideSuggestion: (ToSortItem) -> Unit = {},
    onLetGo: (ToSortItem) -> Unit = {},
    onUndoSort: (SortUndoToken) -> Unit = {},
    onSortFeedbackShown: () -> Unit = {},
    sortHost: @Composable (SnackbarHostState) -> Unit = {},
) {
    val reviewContext by rememberReviewContext()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.core_action_undo)
    val pendingTaskUndo = uiState.pendingTaskUndo
    val pendingTaskUndoMessage = pendingTaskUndo?.let { taskUndo ->
        stringResource(taskUndo.action.undoMessageRes())
    }
    val sortUndo = uiState.pendingSortUndo
    val sortUndoMessage = sortUndo?.let { stringResource(it.messageRes()) }
    val sortMessage = uiState.sortMessage?.let { stringResource(it.messageRes()) }
    var showOpenLoops by rememberSaveable { mutableStateOf(false) }
    var showAllOpenLoops by rememberSaveable { mutableStateOf(false) }
    var showAllToSort by rememberSaveable { mutableStateOf(false) }
    var showAllToday by rememberSaveable { mutableStateOf(false) }
    var weeklyExpanded by rememberSaveable { mutableStateOf(false) }
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
    val headerClearance = maxOf(statusBarTopPadding + 96.dp, measuredHeaderClearance)
    val reviewTitle = stringResource(R.string.core_review_title)
    LaunchedEffect(pendingTaskUndo?.operationId) {
        val taskUndo = pendingTaskUndo ?: return@LaunchedEffect
        val message = pendingTaskUndoMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoTaskMutation(taskUndo.operationId)
        } else {
            onTaskUndoExpired(taskUndo.operationId)
        }
    }
    LaunchedEffect(sortUndo, sortMessage) {
        when {
            sortUndo != null && sortUndoMessage != null -> {
                onSortFeedbackShown()
                val result = snackbarHostState.showSnackbar(
                    message = sortUndoMessage,
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) onUndoSort(sortUndo)
            }
            sortMessage != null -> {
                onSortFeedbackShown()
                snackbarHostState.showSnackbar(sortMessage)
            }
        }
    }
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
            reviewSectionOrder(reviewContext.period).forEach { section ->
                when (section) {
                    ReviewSection.AskLuma -> {
                        item(key = "ask") { AskLumaCard(onAskLuma) }
                        item(key = "breathe") { BreathingMoment() }
                        if (uiState.nothingWaiting) item(key = "all_sorted") { AllSortedCard() }
                    }

                    ReviewSection.ToSort -> if (uiState.toSort.isNotEmpty()) {
                        item(key = "to_sort_heading") {
                            SectionHeading(
                                stringResource(R.string.review_to_sort_title),
                                stringResource(R.string.review_to_sort_subtitle),
                            )
                        }
                        val visible = if (showAllToSort) uiState.toSort else uiState.toSort.take(ReviewSectionPreviewSize)
                        items(visible, key = { "to_sort_${it.captureId}" }) { item ->
                            ToSortRow(
                                item = item,
                                timeFormat = timeFormat,
                                onAccept = { onAcceptToSort(item) },
                                onChange = { onChangeToSort(item) },
                                onHideSuggestion = { onHideSuggestion(item) },
                                onLetGo = { onLetGo(item) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(OrbitMotion.StandardDurationMillis),
                                    placementSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                                    fadeOutSpec = tween(OrbitMotion.QuickDurationMillis),
                                ),
                            )
                        }
                        if (uiState.toSort.size > ReviewSectionPreviewSize) {
                            item(key = "to_sort_more") {
                                TextButton(onClick = { showAllToSort = !showAllToSort }) {
                                    Text(
                                        if (showAllToSort) {
                                            stringResource(R.string.review_show_fewer)
                                        } else {
                                            stringResource(R.string.review_to_sort_show_all, uiState.toSort.size)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    ReviewSection.Today -> {
                        item(key = "today_heading") {
                            SectionHeading(
                                stringResource(R.string.review_today_title),
                                stringResource(R.string.review_today_subtitle),
                            )
                        }
                        if (uiState.dueToday.isEmpty()) {
                            item(key = "today_empty") { EmptyMessage(stringResource(R.string.review_today_empty)) }
                        } else {
                            val visible = if (showAllToday) uiState.dueToday else uiState.dueToday.take(ReviewSectionPreviewSize)
                            items(visible, key = { "due_${it.key}" }) { item ->
                                ReviewItemRow(
                                    item = item,
                                    badge = if (item.schedule == ReviewItemSchedule.DateOnly) {
                                        stringResource(R.string.core_today)
                                    } else {
                                        timeFormat.formatTime(item.timestamp)
                                    },
                                    onClick = reviewRowClick(item, onReviewItemSelected),
                                )
                            }
                            if (uiState.dueToday.size > ReviewSectionPreviewSize) {
                                item(key = "today_more") {
                                    TextButton(onClick = { showAllToday = !showAllToday }) {
                                        Text(
                                            if (showAllToday) {
                                                stringResource(R.string.review_show_fewer)
                                            } else {
                                                stringResource(
                                                    R.string.review_show_more,
                                                    uiState.dueToday.size - ReviewSectionPreviewSize,
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    ReviewSection.CarryForward -> if (uiState.carryForwardSuggestions.isNotEmpty()) {
                        item(key = "carry_heading") {
                            SectionHeading(
                                stringResource(R.string.review_carry_title),
                                stringResource(R.string.review_carry_subtitle),
                            )
                        }
                        items(uiState.carryForwardSuggestions, key = { "carry_${it.item.key}" }) { suggestion ->
                            CarryForwardDecisionCard(
                                suggestion = suggestion,
                                onOpen = { onReviewItemSelected(suggestion.item) },
                                onTomorrow = { onCarryForwardTomorrow(suggestion.item) },
                                onChooseDate = { epochDay -> onCarryForwardToDate(suggestion.item, epochDay) },
                                onKeepUnscheduled = { onKeepCarryForwardUnscheduled(suggestion.item) },
                                onMarkComplete = { onCompleteCarryForward(suggestion.item) },
                            )
                        }
                    }

                    ReviewSection.WeeklyLookBack -> {
                        item(key = "weekly_entry") {
                            WeeklyLookBackEntry(
                                expanded = weeklyExpanded,
                                suggested = reviewContext.weeklyReviewAvailable,
                                onToggle = { weeklyExpanded = !weeklyExpanded },
                            )
                        }
                        if (weeklyExpanded) {
                            item(key = "weekly_pager") { WeeklyReviewPager(uiState, onWeeklyLookBackVisible) }
                        }
                    }
                }
            }

            item(key = "open_loops_action") {
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
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = navigationBottomPadding + OrbitBottomNavigationDefaults.ContentClearance,
                ),
        )
        sortHost(snackbarHostState)
    }
}

private fun SortUndoToken.messageRes(): Int = when (this) {
    is SortUndoToken.Accepted -> itemType.sortedMessageRes()
    is SortUndoToken.LetGo -> R.string.review_sort_let_go
    is SortUndoToken.Hidden -> R.string.review_sort_hidden
}

private fun ReviewSortMessage.messageRes(): Int = when (this) {
    ReviewSortMessage.ActionFailed -> R.string.review_sort_failed
    ReviewSortMessage.NeedsChoice -> R.string.review_sort_needs_choice
    ReviewSortMessage.Undone -> R.string.core_sort_undone
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CarryForwardDecisionCard(
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
internal fun SectionHeading(title: String, subtitle: String) {
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
internal fun EmptyMessage(text: String) {
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
internal fun ReviewItemRow(
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

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ResetLoopCard(
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
    val actionPlan = reviewLoopActionPlan(loop)
    val moreActionsTitle = stringResource(R.string.core_review_more_actions)
    var showMoreActions by rememberSaveable(loop.key) { mutableStateOf(false) }

    fun perform(action: ReviewLoopAction) {
        showMoreActions = false
        when (action) {
            ReviewLoopAction.KeepActive -> onKeepTaskActive()
            ReviewLoopAction.ConfirmCapture,
            ReviewLoopAction.ResumeBrainDump -> onConfirmCapture()
            ReviewLoopAction.CompleteTask -> onCompleteTask()
            ReviewLoopAction.DeferTask -> onDeferTask()
            ReviewLoopAction.DismissCapture -> onDismissCapture()
            ReviewLoopAction.MakeSmaller -> onMakeSmaller()
            ReviewLoopAction.Archive -> onArchive()
        }
    }

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
            FilledTonalButton(
                onClick = { perform(actionPlan.primary) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(actionPlan.primary.labelRes(loop)))
            }
            AssistChip(
                onClick = { showMoreActions = true },
                modifier = Modifier.padding(top = 8.dp),
                label = { Text(moreActionsTitle) },
            )
        }
    }

    if (showMoreActions) {
        LumaModalBottomSheet(
            onDismissRequest = { showMoreActions = false },
            modifier = Modifier.semantics { paneTitle = moreActionsTitle },
        ) {
            Text(
                text = moreActionsTitle,
                modifier = Modifier
                    .padding(start = 24.dp, top = 20.dp, end = 24.dp)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            actionPlan.more.forEach { action ->
                FilledTonalButton(
                    onClick = { perform(action) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 12.dp, end = 16.dp),
                ) {
                    Text(stringResource(action.labelRes(loop)))
                }
            }
            Box(Modifier.height(20.dp))
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
private fun ReviewLoopAction.labelRes(loop: ReviewLoop): Int = when (this) {
    ReviewLoopAction.KeepActive -> R.string.core_review_keep_active
    ReviewLoopAction.ConfirmCapture -> R.string.core_review_confirm_as_someday
    ReviewLoopAction.ResumeBrainDump -> R.string.core_review_resume_brain_dump
    ReviewLoopAction.CompleteTask -> R.string.core_review_mark_task_done
    ReviewLoopAction.DeferTask -> R.string.core_review_defer_to_someday
    ReviewLoopAction.DismissCapture -> R.string.core_review_dismiss_capture
    ReviewLoopAction.MakeSmaller -> R.string.core_review_make_smaller
    ReviewLoopAction.Archive -> if (loop.type == ReviewLoopType.Task) {
        R.string.core_review_archive_task
    } else {
        R.string.core_review_archive_source
    }
}

@StringRes
private fun ReviewTaskMutationAction.undoMessageRes(): Int = when (this) {
    ReviewTaskMutationAction.Completed -> R.string.core_review_task_completed
    ReviewTaskMutationAction.Deferred -> R.string.core_review_task_deferred
    ReviewTaskMutationAction.Archived -> R.string.core_review_task_archived
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
