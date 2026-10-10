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
import androidx.compose.foundation.layout.heightIn
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
import com.orbit.app.ui.components.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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


internal enum class WeeklyReviewPage {
    LookBack,
    LooseEnds,
    LookAhead,
}

@Composable
internal fun WeeklyReviewPager(
    uiState: ReviewUiState,
    onLookBackVisible: () -> Unit,
) {
    val pages = WeeklyReviewPage.entries
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { pages.size })
    LaunchedEffect(pagerState.currentPage) {
        if (pages[pagerState.currentPage] == WeeklyReviewPage.LookBack) onLookBackVisible()
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeading(
            stringResource(R.string.core_review_weekly_title),
            stringResource(R.string.core_review_weekly_subtitle),
        )
        // No fixed height: a long look back or large text grows the card instead of being cut off.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top,
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
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WeeklyCardMinHeight),
        shape = MaterialTheme.shapes.extraLarge,
        style = GlassSurfaceStyle.Prominent,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                WeeklyReviewPage.LookBack -> if (uiState.weeklySummaryLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.review_weekly_loading),
                            modifier = Modifier.padding(start = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    uiState.weeklySummary?.let { summary ->
                        Text(
                            text = stringResource(
                                if (summary.fromGemini) {
                                    R.string.core_situation_answered_by_gemini
                                } else {
                                    R.string.core_situation_local_answer
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = uiState.weeklySummary?.answer
                            ?: stringResource(R.string.core_review_weekly_fallback),
                        style = MaterialTheme.typography.bodyLarge,
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


private val WeeklyCardMinHeight = 300.dp

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
