package com.orbit.app.ui.screens.home

import kotlinx.coroutines.launch
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.Animatable
import com.orbit.app.ui.components.rememberReducedMotion
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalView
import android.view.HapticFeedbackConstants
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
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orbit.app.R
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.reminders.rememberTurnOnReminderNotifications
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import com.orbit.app.capture.VoiceCapture
import com.orbit.app.ui.components.SoftGlassSurface
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.orbitPressFeedback
import com.orbit.app.ui.time.OrbitTimeFormat
import com.orbit.app.ui.theme.HomeTypography
import com.orbit.app.ui.theme.OrbitShapes
import com.orbit.app.ui.theme.OrbitSpacing
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.collect

@Composable
fun HomeScreen(
    viewModel: HomeCaptureViewModel,
    sortViewModel: CaptureSortViewModel,
    weekUiState: HomeWeekUiState,
    calendarDateContext: LocalDate?,
    onCalendarDateContextConsumed: () -> Unit,
    onCalendarDateSelected: (LocalDate) -> Unit,
    spaceContext: SpaceCaptureTarget? = null,
    onSpaceContextConsumed: () -> Unit = {},
    onVisibleWeekChanged: (Int) -> Unit,
    userName: String,
    timeFormat: OrbitTimeFormat,
    onOpenSettings: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    focusCaptureOnOpen: Boolean = false,
    focusRequest: Long = 0L,
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
    val homePaneTitle = stringResource(R.string.navigation_home)
    val view = LocalView.current
    val reduceMotion = rememberReducedMotion()
    var savedConfirmationVisible by remember { mutableStateOf(false) }
    // The send moment: the sent words lift a little and fade, so letting go is something
    // you see, not just an empty box.
    var pendingSentText by remember { mutableStateOf("") }
    var sentGhostText by remember { mutableStateOf<String?>(null) }
    val sentGhostProgress = remember { Animatable(0f) }
    val pendingMessage = uiState.message?.takeIf { it != HomeMessage.Saved }
    val messageText = pendingMessage?.let { stringResource(it.textRes) }
    val voiceAvailable = remember(context) { VoiceCapture.isAvailable(context) }
    val voicePrompt = stringResource(R.string.home_voice_prompt)
    val voiceLocale = LocalConfiguration.current.locales[0]
    // After a spoken thought, a second mic tap adds the next one on its own line, so a
    // spoken dump splits into thoughts with no guessing.
    var spokeLast by rememberSaveable { mutableStateOf(false) }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        VoiceCapture.transcript(result.data)?.let { transcript ->
            viewModel.receiveSharedText(transcript)
            spokeLast = true
        }
    }
    LaunchedEffect(uiState.inputText.isBlank()) {
        if (uiState.inputText.isBlank()) spokeLast = false
    }
    val voiceAction: (() -> Unit)? = if (voiceAvailable) {
        {
            runCatching {
                voiceLauncher.launch(VoiceCapture.intent(voiceLocale, voicePrompt))
            }
        }
    } else {
        null
    }

    LaunchedEffect(uiState.savedPulse) {
        if (uiState.savedPulse == 0) return@LaunchedEffect
        // One soft buzz for the meaningful moment of letting a thought go.
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.KEYBOARD_TAP
            },
        )
        if (calendarDateContext != null) onCalendarDateContextConsumed()
        if (spaceContext != null) onSpaceContextConsumed()
        if (!reduceMotion && pendingSentText.isNotBlank()) {
            sentGhostText = pendingSentText
            launch {
                sentGhostProgress.snapTo(0f)
                sentGhostProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 340, easing = FastOutLinearInEasing),
                )
                sentGhostText = null
            }
        }
        pendingSentText = ""
        savedConfirmationVisible = true
        viewModel.messageShown()
        kotlinx.coroutines.delay(SavedConfirmationMillis)
        savedConfirmationVisible = false
    }

    // Cleared only after the snackbar is gone; clearing first would cancel this
    // effect and dismiss the message almost at once.
    val turnOnLabel = stringResource(R.string.settings_turn_on)
    val turnOnNotifications = rememberTurnOnReminderNotifications()
    LaunchedEffect(pendingMessage) {
        val shown = pendingMessage ?: return@LaunchedEffect
        val text = messageText ?: return@LaunchedEffect
        try {
            if (shown == HomeMessage.ReminderNotificationsOff) {
                val result = snackbarHostState.showSnackbar(
                    message = text,
                    actionLabel = turnOnLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) turnOnNotifications()
            } else {
                snackbarHostState.showSnackbar(text)
            }
        } finally {
            viewModel.messageShown(shown)
        }
    }

    LaunchedEffect(uiState.sortRequest) {
        uiState.sortRequest?.let { request ->
            sortViewModel.open(request.captureId, request.startWithReminderSetup)
            viewModel.sortRequestHandled()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { paneTitle = homePaneTitle },
    ) {
        val headerTranslationY = with(LocalDensity.current) { headerOffsetY.toPx() }
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .statusBarsPadding()
                .padding(horizontal = OrbitSpacing.ExtraLarge)
                .padding(top = 26.dp, bottom = bottomContentPadding),
        ) {
            val requiresVerticalScroll = maxHeight < homeMinimumContentHeightFor(
                fontScale = fontScale,
                imeVisible = imeVisible,
                hasCalendarCaptureContext = calendarDateContext != null,
            )
            val scrollState = rememberScrollState()
            LaunchedEffect(imeVisible, requiresVerticalScroll, scrollState) {
                if (shouldRevealHomeCapture(imeVisible, requiresVerticalScroll)) {
                    snapshotFlow { scrollState.maxValue }
                        .collect(scrollState::scrollTo)
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (requiresVerticalScroll) {
                            Modifier.verticalScroll(scrollState)
                        } else {
                            Modifier
                        },
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        translationY = headerTranslationY
                    },
            ) {
                HomeHeader(
                    greeting = homeGreeting(LocalTime.now().hour, userName),
                    onOpenSettings = onOpenSettings,
                    onOpenSearch = onOpenSearch,
                )

                Spacer(modifier = Modifier.height(OrbitSpacing.Large))
                // The week sits directly on the page, without a card: Home has one surface,
                // the capture box, so the eye goes straight to it.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(homeWeekCardHeightFor(fontScale)),
                    verticalArrangement = Arrangement.Center,
                ) {
                    WeekStrip(
                        uiState = weekUiState,
                        onDateSelected = onCalendarDateSelected,
                        onVisibleWeekChanged = onVisibleWeekChanged,
                    )
                }

                calendarDateContext?.let { date ->
                    CalendarCaptureContextBanner(
                        date = date,
                        onClear = onCalendarDateContextConsumed,
                    )
                }
                spaceContext?.let { space ->
                    CaptureContextBanner(
                        text = stringResource(R.string.core_home_adding_to_space, localizedSpaceName(space.name)),
                        onClear = onSpaceContextConsumed,
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (imeVisible) OrbitSpacing.Large else HomeCaptureGap))

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (requiresVerticalScroll) {
                            Modifier
                        } else {
                            Modifier.weight(1f)
                        },
                    ),
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
                    processingState = uiState.processingState,
                    onTextChanged = viewModel::onInputChanged,
                    onAnalyze = {
                        pendingSentText = uiState.inputText
                        viewModel.send(calendarDateContext?.toEpochDay(), spaceContext?.id)
                    },
                    height = captureCardHeight,
                    imeVisible = imeVisible,
                    requestFocusOnOpen = focusCaptureOnOpen,
                    focusRequest = focusRequest,
                    onVoice = voiceAction,
                    showVoiceHint = spokeLast,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
                sentGhostText?.let { ghost ->
                    val lift = with(LocalDensity.current) { 28.dp.toPx() }
                    Text(
                        text = ghost,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 24.dp, top = 24.dp, end = 96.dp)
                            .graphicsLayer {
                                val p = sentGhostProgress.value
                                alpha = 1f - p
                                translationY = -lift * p
                            }
                            .clearAndSetSemantics {},
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            SavedConfirmation(visible = savedConfirmationVisible, reduceMotion = reduceMotion)
            uiState.quickReminder?.let { question ->
                QuickReminderCard(
                    question = question,
                    timeFormat = timeFormat,
                    isWorking = uiState.isSettingReminder,
                    onConfirm = viewModel::confirmQuickReminder,
                    onChangeTime = viewModel::changeQuickReminderTime,
                    onNotNow = viewModel::dismissQuickReminder,
                )
            }
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

    CaptureSortHost(
        viewModel = sortViewModel,
        timeFormat = timeFormat,
        snackbarHostState = snackbarHostState,
    )
}

private const val SavedConfirmationMillis = 2_000L

@Composable
private fun CalendarCaptureContextBanner(
    date: LocalDate,
    onClear: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    CaptureContextBanner(
        text = stringResource(R.string.core_home_adding_for_date, date.format(formatter)),
        onClear = onClear,
    )
}

/** The Space a Space's "+" asked the next thought to go into. */
data class SpaceCaptureTarget(val id: Long, val name: String)

@Composable
private fun CaptureContextBanner(
    text: String,
    onClear: () -> Unit,
) {
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
            text = text,
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
                        style = HomeTypography.calendarMonth,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.core_home_week_number, weekNumber),
                        modifier = Modifier.alignByBaseline(),
                        style = HomeTypography.calendarWeek,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                BoxWithConstraints(modifier = sharedContentBounds) {
                    // Every day is at least a 48 dp touch target. On a narrow phone the
                    // row reaches a little into the page margins to make room.
                    val rowWidth = maxOf(maxWidth, minOf(HomeDayMinTouch * dates.size, maxWidth + HomeWeekMarginBleed * 2))
                    val dayCapsuleWidth = minOf(HomeDayCapsuleMaxWidth, rowWidth / dates.size)
                    val dayCapsuleHeight = HomeDayCapsuleHeight *
                        LocalDensity.current.fontScale.coerceAtLeast(1f)
                    val dayCapsuleShape = RoundedCornerShape(22.dp)
                    Row(
                        modifier = Modifier.requiredWidth(rowWidth),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dates.forEach { date ->
                        val isToday = date == uiState.today
                        val interactionSource = remember(date) { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val hasItems = date in uiState.datesWithItems
                        val backgroundColor by animateColorAsState(
                            targetValue = when {
                                isPressed -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 140),
                            label = "homeWeekSelection",
                        )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(dayCapsuleHeight)
                                    .orbitPressFeedback(
                                        interactionSource = interactionSource,
                                        clipShape = dayCapsuleShape,
                                    )
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = LocalIndication.current,
                                    ) { onDateSelected(date) }
                                    .semantics(mergeDescendants = true) {
                                        role = Role.Button
                                        selected = false
                                        contentDescription = homeDateContentDescription(
                                            date = date,
                                            locale = locale,
                                            isToday = isToday,
                                            isSelected = false,
                                            hasItems = hasItems,
                                            labels = dateAccessibilityLabels,
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(
                                    modifier = Modifier
                                        .size(width = dayCapsuleWidth, height = dayCapsuleHeight)
                                        .clip(dayCapsuleShape)
                                        .background(backgroundColor),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                Text(
                                    text = homeWeekdayLabel(date, locale),
                                    style = HomeTypography.calendarWeekday,
                                    color = when {
                                        isPressed -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Spacer(modifier = Modifier.height(OrbitSpacing.ExtraSmall))
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = if (isPressed) {
                                        HomeTypography.calendarDate.copy(fontWeight = FontWeight.SemiBold)
                                    } else {
                                        HomeTypography.calendarDate
                                    },
                                    color = when {
                                        isPressed -> MaterialTheme.colorScheme.onPrimary
                                        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            color = if (hasItems) {
                                                if (isPressed) {
                                                    MaterialTheme.colorScheme.onPrimary
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
}

@Composable
private fun CaptureCard(
    text: String,
    processingState: CaptureProcessingState,
    onTextChanged: (String) -> Unit,
    onAnalyze: () -> Unit,
    height: Dp,
    imeVisible: Boolean,
    requestFocusOnOpen: Boolean,
    focusRequest: Long,
    onVoice: (() -> Unit)?,
    showVoiceHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isProcessing = processingState.isInProgress
    val offersNextVoice = onVoice != null && showVoiceHint && text.isNotBlank() && !isProcessing
    val focusRequester = remember { FocusRequester() }
    // Focus once per visit to Home, only when the user asked for it in Settings.
    LaunchedEffect(requestFocusOnOpen) {
        if (requestFocusOnOpen) runCatching { focusRequester.requestFocus() }
    }
    // The "New thought" shortcut or tile always focuses, whatever the setting.
    LaunchedEffect(focusRequest) {
        if (focusRequest != 0L) runCatching { focusRequester.requestFocus() }
    }
    val processingStatusRes = processingState.statusLabelRes()
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
                enabled = !isProcessing,
                onTextChanged = onTextChanged,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .padding(
                        end = CaptureTextActionClearance + if (offersNextVoice) 48.dp else 0.dp,
                        bottom = if (isProcessing || offersNextVoice) 34.dp else 0.dp,
                    ),
            )

            if (offersNextVoice) {
                Text(
                    text = stringResource(R.string.home_voice_next_hint),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(end = CaptureTextActionClearance + 48.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick = { onVoice?.invoke() },
                    modifier = Modifier
                        .align(if (imeVisible) Alignment.CenterEnd else Alignment.BottomEnd)
                        .padding(end = CaptureActionButtonSize + 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = stringResource(R.string.home_voice_next),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            processingStatusRes?.let { statusRes ->
                Text(
                    text = stringResource(statusRes),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // An empty box offers dictation; as soon as there is text the button sends.
            val offersVoice = onVoice != null && text.isBlank() && !isProcessing
            CaptureActionButton(
                contentDescription = stringResource(
                    when {
                        isProcessing -> processingStatusRes ?: R.string.core_home_analyzing_capture
                        offersVoice -> R.string.home_voice_capture
                        else -> R.string.core_home_save_and_analyze
                    },
                ),
                emphasized = text.isNotBlank() || isProcessing,
                enabled = offersVoice || (text.isNotBlank() && !isProcessing),
                onClick = { if (offersVoice) onVoice?.invoke() else onAnalyze() },
                modifier = Modifier.align(
                    if (imeVisible) Alignment.CenterEnd else Alignment.BottomEnd,
                ),
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (offersVoice) Icons.Rounded.Mic else Icons.Rounded.ArrowUpward,
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
    enabled: Boolean,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val captureInputDescription = stringResource(R.string.core_home_capture_input)
    // A different gentle hint each visit; never animated while the user reads it.
    val hintRes = rememberSaveable { CaptureHints.random() }
    BasicTextField(
        value = text,
        onValueChange = onTextChanged,
        enabled = enabled,
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
                        text = stringResource(hintRes),
                        style = HomeTypography.capturePlaceholder,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
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

private val PlaceholderUserName = com.orbit.app.domain.model.AppSettings().userName

private val CaptureHints = listOf(
    R.string.core_home_capture_placeholder,
    R.string.home_capture_hint_remind,
    R.string.home_capture_hint_dump,
    R.string.home_capture_hint_small,
)

/** "Good afternoon, Name", or just the greeting when no name is set. */
@Composable
private fun homeGreeting(hour: Int, userName: String): String {
    val name = userName.trim()
    // "user" is the stored placeholder until the person sets a name in Settings.
    return if (name.isEmpty() || name.equals(PlaceholderUserName, ignoreCase = true)) {
        stringResource(greetingResFor(hour))
    } else {
        stringResource(namedGreetingResFor(hour), name)
    }
}

@Composable
private fun HomeHeader(
    greeting: String,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // A name that does not fit after the greeting moves to its own line instead
        // of being cut off ("Good afternoon," / "Vitautas").
        Text(
            text = greeting,
            modifier = Modifier
                .weight(1f)
                .padding(top = 6.dp)
                .semantics { heading() },
            style = HomeTypography.userName.copy(fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.45).sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = onOpenSearch,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.home_open_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.home_open_settings),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
internal fun greetingResFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.core_home_good_morning
    in 12..16 -> R.string.core_home_good_afternoon
    else -> R.string.core_home_good_evening
}

@StringRes
internal fun namedGreetingResFor(hour: Int): Int = when (hour) {
    in 5..11 -> R.string.home_greeting_morning_named
    in 12..16 -> R.string.home_greeting_afternoon_named
    else -> R.string.home_greeting_evening_named
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

private val HomeWeekCardHeight = 104.dp
private val HomeDayCapsuleMaxWidth = 52.dp
private val HomeDayCapsuleHeight = 64.dp
private val HomeDayMinTouch = 48.dp
private val HomeWeekMarginBleed = 16.dp
private val HomeWeekCardScaledContentGrowth = 112.dp
private val HomeCaptureGap = 28.dp
private val CalendarCaptureContextBannerActionMinimumHeight = 48.dp
private val CalendarCaptureContextBannerBodyLineHeight = 22.dp
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

private fun homeWeekCardHeightFor(fontScale: Float): Dp =
    HomeWeekCardHeight + (HomeWeekCardScaledContentGrowth * (fontScale - 1f))

private fun homeMinimumContentHeightFor(
    fontScale: Float,
    imeVisible: Boolean,
    hasCalendarCaptureContext: Boolean,
): Dp =
    26.dp +
        maxOf(48.dp, 40.dp * fontScale) +
        OrbitSpacing.Large +
        homeWeekCardHeightFor(fontScale) +
        (if (hasCalendarCaptureContext) calendarCaptureContextBannerMinimumHeightFor(fontScale) else 0.dp) +
        (if (imeVisible) OrbitSpacing.Large else HomeCaptureGap) +
        (if (imeVisible) CaptureCardCompactHeight else CaptureCardExpandedHeight)

internal fun shouldRevealHomeCapture(
    imeVisible: Boolean,
    requiresVerticalScroll: Boolean,
): Boolean = imeVisible && requiresVerticalScroll

private fun calendarCaptureContextBannerMinimumHeightFor(fontScale: Float): Dp =
    OrbitSpacing.Medium + maxOf(
        CalendarCaptureContextBannerActionMinimumHeight,
        CalendarCaptureContextBannerBodyLineHeight * 2 * fontScale,
    )
