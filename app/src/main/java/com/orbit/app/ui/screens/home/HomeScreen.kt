package com.orbit.app.ui.screens.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.orbit.app.R
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeCaptureViewModel,
    weekUiState: HomeWeekUiState,
    calendarDateContext: LocalDate?,
    onCalendarDateContextConsumed: () -> Unit,
    onCalendarDateSelected: (LocalDate) -> Unit,
    onVisibleWeekChanged: (Int) -> Unit,
    userName: String,
    timeFormat: OrbitTimeFormat,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val navigationBottomPadding = with(LocalDensity.current) {
        WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val motionSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
    val bottomContentPadding by animateDpAsState(
        targetValue = if (imeVisible) {
            16.dp
        } else {
            OrbitBottomNavigationDefaults.ContentClearance + navigationBottomPadding
        },
        animationSpec = motionSpec,
        label = "homeBottomContentPadding",
    )
    val headerOffsetY by animateDpAsState(
        targetValue = if (imeVisible) (-8).dp else 0.dp,
        animationSpec = motionSpec,
        label = "homeHeaderOffsetY",
    )
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = viewModel::onNotificationPermissionResult,
    )
    val homePaneTitle = stringResource(R.string.navigation_home)

    LaunchedEffect(uiState.notificationPermissionRequestPending) {
        if (!uiState.notificationPermissionRequestPending) return@LaunchedEffect
        viewModel.notificationPermissionRequestStarted()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.onNotificationPermissionResult(granted = true)
        } else {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    LaunchedEffect(calendarDateContext, uiState.suggestion) {
        val contextEpochDay = calendarDateContext?.toEpochDay()
        if (
            contextEpochDay != null &&
            uiState.suggestion?.calendarDateContextEpochDay == contextEpochDay
        ) {
            onCalendarDateContextConsumed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = homePaneTitle },
    ) {
        val headerTranslationY = with(LocalDensity.current) { headerOffsetY.toPx() }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .statusBarsPadding()
                .padding(horizontal = OrbitSpacing.ExtraLarge)
                .padding(top = 26.dp, bottom = bottomContentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = headerTranslationY
                    },
            ) {
                Text(
                    text = stringResource(greetingResFor(LocalTime.now().hour)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(OrbitSpacing.ExtraSmall))
                Text(
                    text = userName.ifBlank { stringResource(R.string.home_default_user_label) },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(modifier = Modifier.height(OrbitSpacing.ExtraLarge))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HomeWeekCardHeight),
                    shape = RoundedCornerShape(32.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = OrbitSpacing.Comfortable,
                            vertical = OrbitSpacing.Comfortable,
                        ),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        WeekStrip(
                            uiState = weekUiState,
                            onDateSelected = onCalendarDateSelected,
                            onVisibleWeekChanged = onVisibleWeekChanged,
                        )
                    }
                }

                calendarDateContext?.let { date ->
                    CalendarCaptureContextBanner(
                        date = date,
                        onClear = onCalendarDateContextConsumed,
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (imeVisible) OrbitSpacing.Large else HomeCaptureGap))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter,
            ) {
                val captureCardHeight by animateDpAsState(
                    targetValue = captureCardHeightFor(
                        text = uiState.inputText,
                        imeVisible = imeVisible,
                        availableHeight = maxHeight,
                    ),
                    animationSpec = motionSpec,
                    label = "captureCardHeight",
                )
                CaptureCard(
                    text = uiState.inputText,
                    isAnalyzing = uiState.isAnalyzing,
                    onTextChanged = viewModel::onInputChanged,
                    onAnalyze = {
                        viewModel.analyzeCapture(calendarDateContext?.toEpochDay())
                    },
                    height = captureCardHeight,
                    imeVisible = imeVisible,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .padding(horizontal = OrbitSpacing.ExtraLarge, vertical = bottomContentPadding),
        )
    }

    uiState.suggestion?.let { suggestion ->
        CaptureSuggestionSheet(
            suggestion = suggestion,
            timeFormat = timeFormat,
            brainDumpHandledItemIds = uiState.brainDumpHandledItemIds,
            mondayConfigured = uiState.mondayConfigured,
            isPerformingAction = uiState.isPerformingAction,
            onSaveNote = viewModel::saveNote,
            onCreateTask = viewModel::createTask,
            onCreateReminder = viewModel::createReminder,
            onSaveBrainDumpItem = viewModel::saveBrainDumpItem,
            onSaveBrainDumpReminder = viewModel::saveBrainDumpReminder,
            onSaveBrainDumpOriginalForLater = viewModel::saveBrainDumpOriginalForLater,
            onSkipBrainDumpItem = viewModel::skipBrainDumpItem,
            onKeepInInbox = viewModel::keepInInbox,
            onCancelBrainDump = viewModel::cancelBrainDump,
            onCancel = viewModel::cancelSuggestion,
        )
    }
    uiState.learnedRuleProposal?.let { proposal ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLearnedRuleProposal,
            title = { Text(stringResource(R.string.core_learning_save_title)) },
            text = {
                Text(stringResource(R.string.core_learning_save_body, proposal.ruleText))
            },
            confirmButton = {
                Button(onClick = viewModel::saveLearnedRuleProposal) {
                    Text(stringResource(R.string.core_learning_save_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissLearnedRuleProposal) {
                    Text(stringResource(R.string.core_learning_not_now))
                }
            },
        )
    }
}

@Composable
private fun CalendarCaptureContextBanner(
    date: LocalDate,
    onClear: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = OrbitSpacing.Medium)
            .clip(OrbitShapes.Standard)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f))
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.core_home_adding_for_date,
                date.format(formatter),
            ),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.core_clear))
        }
    }
}

@Composable
internal fun WeekStrip(
    uiState: HomeWeekUiState,
    onDateSelected: (LocalDate) -> Unit,
    onVisibleWeekChanged: (Int) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val swipeThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val sharedContentBounds = Modifier.fillMaxWidth()
    var horizontalDrag by remember { mutableFloatStateOf(0f) }
    val showPreviousWeek = stringResource(R.string.core_home_show_previous_week)
    val showNextWeek = stringResource(R.string.core_home_show_next_week)
    val dateAccessibilityLabels = HomeDateAccessibilityLabels(
        today = stringResource(R.string.core_today),
        selected = stringResource(R.string.core_selected),
        hasScheduledItems = stringResource(R.string.core_has_scheduled_items),
        separator = stringResource(R.string.core_accessibility_separator),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> horizontalDrag += delta },
                onDragStopped = {
                    when {
                        horizontalDrag <= -swipeThreshold -> onVisibleWeekChanged(1)
                        horizontalDrag >= swipeThreshold -> onVisibleWeekChanged(-1)
                    }
                    horizontalDrag = 0f
                },
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(showPreviousWeek) {
                        onVisibleWeekChanged(-1)
                        true
                    },
                    CustomAccessibilityAction(showNextWeek) {
                        onVisibleWeekChanged(1)
                        true
                    },
                )
            },
    ) {
        AnimatedContent(
            targetState = uiState.visibleWeekDate,
            modifier = sharedContentBounds,
            transitionSpec = {
                val movingForward = targetState.isAfter(initialState)
                val enterOffset: (Int) -> Int = { width ->
                    if (movingForward) width else -width
                }
                val exitOffset: (Int) -> Int = { width ->
                    if (movingForward) -width else width
                }
                slideInHorizontally(
                    animationSpec = tween(durationMillis = 280),
                    initialOffsetX = enterOffset,
                ) togetherWith slideOutHorizontally(
                    animationSpec = tween(durationMillis = 280),
                    targetOffsetX = exitOffset,
                )
            },
            label = "homeWeekContent",
        ) { visibleWeekDate ->
            val dates = remember(visibleWeekDate, locale) {
                homeWeekDates(visibleWeekDate, locale)
            }
            val month = remember(dates, locale) { homeVisibleWeekMonth(dates, locale) }
            val weekNumber = remember(dates, locale) { homeVisibleWeekNumber(dates, locale) }

            Column(modifier = sharedContentBounds) {
                Row(
                    modifier = sharedContentBounds,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = month,
                        modifier = Modifier
                            .weight(1f)
                            .alignByBaseline()
                            .semantics { heading() },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.core_home_week_number, weekNumber),
                        modifier = Modifier.alignByBaseline(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = sharedContentBounds,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dates.forEach { date ->
                        val isToday = date == uiState.today
                        val isSelected = date == uiState.selectedDate
                        val hasItems = date in uiState.datesWithItems
                        val backgroundColor by animateColorAsState(
                            targetValue = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 140),
                            label = "homeWeekSelection",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clickable { onDateSelected(date) }
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    selected = isSelected
                                    contentDescription = homeDateContentDescription(
                                        date = date,
                                        locale = locale,
                                        isToday = isToday,
                                        isSelected = isSelected,
                                        hasItems = hasItems,
                                        labels = dateAccessibilityLabels,
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier
                                    .size(width = 48.dp, height = 64.dp)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(backgroundColor),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = homeWeekdayLabel(date, locale),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(modifier = Modifier.height(OrbitSpacing.ExtraSmall))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        isToday -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            color = if (hasItems) {
                                                if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                }
                                            } else {
                                                Color.Transparent
                                            },
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    text: String,
    isAnalyzing: Boolean,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    height: Dp,
    imeVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    SoftGlassSurface(
        modifier = modifier
            .height(height),
        shape = RoundedCornerShape(32.dp),
        style = com.orbit.app.ui.components.GlassSurfaceStyle.HomeCapture,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            CaptureTextField(
                text = text,
                onTextChanged = onTextChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = CaptureTextActionClearance),
            )

            CaptureActionButton(
                contentDescription = stringResource(
                    if (isAnalyzing) {
                        R.string.core_home_analyzing_capture
                    } else {
                        R.string.core_home_save_and_analyze
                    },
                ),
                emphasized = true,
                enabled = text.isNotBlank() && !isAnalyzing,
                onClick = onAnalyze,
                modifier = Modifier.align(
                    if (imeVisible) Alignment.CenterEnd else Alignment.BottomEnd,
                ),
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = null,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureTextField(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureInputDescription = stringResource(R.string.core_home_capture_input)
    BasicTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = modifier.semantics {
            contentDescription = captureInputDescription
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.core_home_capture_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.90f),
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun CaptureActionButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(CaptureActionButtonSize)
            .semantics { this.contentDescription = contentDescription }
            .background(
                color = if (emphasized) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape,
            ),
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides if (emphasized) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = if (enabled) 1f else 0.55f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            ) {
                Box(
                    modifier = Modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

@StringRes
private fun greetingResFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.core_home_good_morning
    in 12..16 -> R.string.core_home_good_afternoon
    else -> R.string.core_home_good_evening
}

private fun captureCardHeightFor(
    text: String,
    imeVisible: Boolean,
    availableHeight: Dp,
): Dp {
    if (text.isBlank()) {
        return (if (imeVisible) CaptureCardCompactHeight else CaptureCardExpandedHeight)
            .coerceAtMost(availableHeight)
    }

    val estimatedLines = estimatedCaptureLineCount(text)
    val desiredHeight = CaptureCardVerticalChromeHeight +
        (estimatedLines * CaptureEstimatedLineHeight).dp
    val preferredMinHeight = if (imeVisible) {
        CaptureCardTypingMinHeight
    } else {
        CaptureCardExpandedHeight
    }
    val preferredMaxHeight = if (imeVisible) {
        CaptureCardTypingMaxHeight
    } else {
        CaptureCardExpandedTextMaxHeight
    }
    val boundedMaxHeight = availableHeight.coerceAtMost(preferredMaxHeight)
    val boundedMinHeight = preferredMinHeight.coerceAtMost(boundedMaxHeight)
    return desiredHeight.coerceIn(boundedMinHeight, boundedMaxHeight)
}

private fun estimatedCaptureLineCount(text: String): Int = text
    .lineSequence()
    .sumOf { line ->
        maxOf(
            1,
            (line.length + CaptureEstimatedCharactersPerLine - 1) / CaptureEstimatedCharactersPerLine,
        )
    }

private val HomeWeekCardHeight = 156.dp
private val HomeCaptureGap = 28.dp
private val CaptureCardExpandedHeight = 244.dp
private val CaptureCardCompactHeight = 132.dp
private val CaptureCardTypingMinHeight = 156.dp
private val CaptureCardTypingMaxHeight = 228.dp
private val CaptureCardExpandedTextMaxHeight = 392.dp
private val CaptureActionButtonSize = 56.dp
private val CaptureTextActionClearance = CaptureActionButtonSize + 16.dp
private val CaptureCardVerticalChromeHeight = 24.dp + 24.dp
private const val CaptureEstimatedCharactersPerLine = 34
private const val CaptureEstimatedLineHeight = 29
