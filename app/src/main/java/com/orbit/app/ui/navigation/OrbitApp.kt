package com.orbit.app.ui.navigation

import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.produceState
import com.orbit.app.ui.screens.review.needsYouSomethingFailed
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbit.app.ui.components.FloatingBottomNavigation
import com.orbit.app.ui.components.OrbitBottomNavigationDefaults
import com.orbit.app.ui.components.GlassRenderingPolicy
import com.orbit.app.ui.components.OrbitBackground
import com.orbit.app.ui.screens.calendar.CalendarScreen
import com.orbit.app.ui.screens.calendar.CalendarViewModel
import com.orbit.app.ui.screens.home.CaptureReceiptReader
import com.orbit.app.ui.screens.home.ReceiptItemType
import com.orbit.app.ui.screens.home.rearmReceiptReminder
import com.orbit.app.ui.screens.home.HomeScreen
import com.orbit.app.ui.screens.home.HomeWeekViewModel
import com.orbit.app.ui.screens.item.ItemDetailScreen
import com.orbit.app.ui.screens.item.ItemDetailViewModel
import com.orbit.app.ui.screens.review.ReviewScreen
import com.orbit.app.ui.screens.review.ReviewViewModel
import com.orbit.app.ui.screens.search.SearchScreen
import com.orbit.app.ui.screens.search.SearchViewModel
import com.orbit.app.ui.screens.settings.AiSettingsViewModel
import com.orbit.app.ui.screens.settings.LocalDataToolsMessage
import com.orbit.app.ui.screens.settings.LocalDataToolsViewModel
import com.orbit.app.ui.screens.settings.SettingsScreen
import com.orbit.app.ui.screens.situation.SituationAiSheet
import com.orbit.app.ui.screens.situation.SituationAiViewModel
import com.orbit.app.ui.screens.spaces.SpacesScreen
import com.orbit.app.ui.screens.spaces.SpacesViewModel
import com.orbit.app.ui.screens.tutorial.FirstTimeTutorialScreen
import com.orbit.app.ui.screens.tutorial.TutorialSpaceSetupViewModel
import com.orbit.app.ui.screens.tutorial.seedStarterSpacesSilently
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.homeSavesUseGemini
import com.orbit.app.domain.model.homeSavesUseGeminiOnlyForBrainDumps
import com.orbit.app.ui.screens.home.HomeCaptureViewModel
import com.orbit.app.ui.screens.home.CaptureSortViewModel
import com.orbit.app.ui.screens.home.CaptureSortHost
import com.orbit.app.domain.ai.AskLumaQuestion
import com.orbit.app.ui.time.currentOrbitTimeFormat
import com.orbit.app.ui.theme.OrbitMotion
import com.orbit.app.ui.localization.AppLanguage

@Composable
fun OrbitApp(
    container: OrbitContainer,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    applicationLanguage: AppLanguage,
    onApplicationLanguageChanged: (AppLanguage) -> Unit,
    reminderToOpen: Long?,
    onReminderOpened: () -> Unit,
    openReviewRequested: Boolean = false,
    onOpenReviewHandled: () -> Unit = {},
    sharedText: String? = null,
    onSharedTextHandled: () -> Unit = {},
    newThoughtRequested: Boolean = false,
    onNewThoughtHandled: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route
    var showSituationAi by rememberSaveable { mutableStateOf(false) }
    var situationAiQuestion by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSubsectionOpen by rememberSaveable { mutableStateOf(false) }
    var focusedSortOpen by rememberSaveable { mutableStateOf(false) }
    // A Space's "+" writes the thought over the Space; the user stays where they are.
    // The Space "+" sheet: one flow for every Space, drafts kept per Space (review §9.2).
    val spaceCaptureViewModel: com.orbit.app.ui.screens.spaces.SpaceCaptureViewModel = viewModel(
        key = "spaceCapture",
        factory = com.orbit.app.ui.screens.spaces.SpaceCaptureViewModel.Factory(container),
    )
    val spaceCaptureState by spaceCaptureViewModel.uiState.collectAsStateWithLifecycle()
    val spaceSavedMessages = remember { androidx.compose.material3.SnackbarHostState() }
    val timeFormat = currentOrbitTimeFormat(settings.timeFormatMode)
    // Needs you gets a dot only when something failed; never a count.
    val needsYouFailed by remember(container) {
        container.captureRepository.observeAll()
            .map(::needsYouSomethingFailed)
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val contentMaxWidth = portraitContentMaxWidth(LocalConfiguration.current.screenWidthDp.dp)
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val showBottomNavigation = shouldShowFloatingBottomNavigation(
        imeVisible = imeVisible,
        selectedRoute = selectedRoute,
        appearanceSubsectionOpen = settingsSubsectionOpen,
        focusedSortOpen = focusedSortOpen && selectedRoute == OrbitDestination.Review.route,
    )

    // A first launch opens straight on Home with the starter Spaces already there.
    LaunchedEffect(settings.hasCompletedFirstTimeTutorial) {
        if (!settings.hasCompletedFirstTimeTutorial) {
            runCatching { seedStarterSpacesSilently(container.spaceRepository) }
        }
    }

    LaunchedEffect(reminderToOpen) {
        reminderToOpen?.let { reminderId ->
            navController.navigate(ReminderDestination.route(reminderId)) {
                launchSingleTop = true
            }
            onReminderOpened()
        }
    }

    // An item with unsaved edits is never closed under the user: incoming text and the
    // "New thought" shortcut wait until the edit is saved or discarded (B1).
    var unsavedItemEdits by remember { mutableStateOf(false) }

    // Shared text waits until Home exists (e.g. after the first-time guide), then
    // lands in the Home draft.
    LaunchedEffect(sharedText, selectedRoute, unsavedItemEdits) {
        val text = sharedText ?: return@LaunchedEffect
        if (unsavedItemEdits) return@LaunchedEffect
        val home = runCatching { navController.getBackStackEntry(OrbitDestination.Home.route) }.getOrNull()
            ?: return@LaunchedEffect
        home.savedStateHandle[SharedTextContext.TextKey] = text
        if (selectedRoute != OrbitDestination.Home.route) {
            navController.popBackStack(OrbitDestination.Home.route, inclusive = false)
        }
        onSharedTextHandled()
    }

    LaunchedEffect(newThoughtRequested, selectedRoute, unsavedItemEdits) {
        if (!newThoughtRequested || unsavedItemEdits) return@LaunchedEffect
        val home = runCatching { navController.getBackStackEntry(OrbitDestination.Home.route) }.getOrNull()
            ?: return@LaunchedEffect
        home.savedStateHandle[SharedTextContext.FocusCaptureKey] = System.nanoTime()
        if (selectedRoute != OrbitDestination.Home.route) {
            navController.popBackStack(OrbitDestination.Home.route, inclusive = false)
        }
        onNewThoughtHandled()
    }

    LaunchedEffect(openReviewRequested) {
        if (openReviewRequested) {
            navController.navigate(OrbitDestination.Review.route) {
                launchSingleTop = true
            }
            onOpenReviewHandled()
        }
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute != OrbitDestination.Settings.route) {
            settingsSubsectionOpen = false
        }
    }

    OrbitBackground(
        settings = settings,
        glassRenderingPolicy = glassRenderingPolicyForRoute(selectedRoute),
    ) {
        // No haptic on every tap: haptics are kept for meaningful moments (send, done, undo),
        // so they still mean something when they happen.
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            NavHost(
                navController = navController,
                startDestination = initialOrbitRoute(),
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                enterTransition = { orbitEnterTransition() },
                exitTransition = { orbitExitTransition() },
                popEnterTransition = { orbitPopEnterTransition() },
                popExitTransition = { orbitPopExitTransition() },
            ) {
                composable(
                    route = FirstTimeTutorialDestination.Route,
                    arguments = listOf(
                        navArgument(FirstTimeTutorialDestination.ReplayArgument) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    val isReplay = entry.arguments
                        ?.getBoolean(FirstTimeTutorialDestination.ReplayArgument)
                        ?: false
                    val tutorialSpaceSetupViewModel: TutorialSpaceSetupViewModel = viewModel(
                        factory = TutorialSpaceSetupViewModel.Factory(container),
                    )
                    val tutorialSpaceSetupState by tutorialSpaceSetupViewModel.uiState
                        .collectAsStateWithLifecycle()
                    FirstTimeTutorialScreen(
                        isReplay = isReplay,
                        spaceSetupState = tutorialSpaceSetupState,
                        onToggleSpaceTemplate = tutorialSpaceSetupViewModel::toggleTemplate,
                        onAddCustomSpace = tutorialSpaceSetupViewModel::addCustomName,
                        onRemoveCustomSpace = tutorialSpaceSetupViewModel::removeCustomName,
                        onFinishSpaceSetup = tutorialSpaceSetupViewModel::finish,
                        onFinish = {
                            if (isReplay) {
                                navController.popBackStack()
                            } else {
                                onSettingsChanged(
                                    settings.copy(hasCompletedFirstTimeTutorial = true),
                                )
                                navController.navigate(OrbitDestination.Home.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        inclusive = true
                                    }
                                    launchSingleTop = true
                                }
                            }
                        },
                        onReplayBack = { navController.popBackStack() },
                    )
                }
                composable(OrbitDestination.Home.route) { entry ->
                    val homeViewModel: HomeCaptureViewModel = viewModel(
                        factory = HomeCaptureViewModel.Factory(
                            captureInbox = container.captureInbox,
                            appSettingsRepository = container.appSettingsRepository,
                            savedStateHandle = entry.savedStateHandle,
                            receipts = CaptureReceiptReader(
                                captureRepository = container.captureRepository,
                                noteRepository = container.noteRepository,
                                taskRepository = container.taskRepository,
                                reminderRepository = container.reminderRepository,
                                spaceRepository = container.spaceRepository,
                            )::observe,
                            retryReminder = { captureId, reminderId ->
                                rearmReceiptReminder(
                                    reminderRepository = container.reminderRepository,
                                    outcomes = container.reminderSaveOutcomes,
                                    resolution = container.captureResolution,
                                    captureId = captureId,
                                    reminderId = reminderId,
                                )
                            },
                            keepThought = container.captureResolution::keep,
                        ),
                    )
                    val homeSortViewModel: CaptureSortViewModel = viewModel(
                        factory = CaptureSortViewModel.factory(container, entry.savedStateHandle),
                    )
                    val homeWeekViewModel: HomeWeekViewModel = viewModel(
                        factory = HomeWeekViewModel.Factory(container.calendarRepository),
                    )
                    LifecycleResumeEffect(homeWeekViewModel) {
                        homeWeekViewModel.refreshToday()
                        onPauseOrDispose { }
                    }
                    // Home left open across midnight moves to the new day too.
                    LaunchedEffect(homeWeekViewModel) {
                        while (true) {
                            kotlinx.coroutines.delay(millisUntilNextLocalMidnight(java.time.ZonedDateTime.now()))
                            homeWeekViewModel.refreshToday()
                        }
                    }
                    val homeWeekUiState by homeWeekViewModel.uiState.collectAsStateWithLifecycle()
                    val calendarCaptureEpochDay by entry.savedStateHandle
                        .getStateFlow<Long?>(CalendarCaptureContext.EpochDayKey, null)
                        .collectAsStateWithLifecycle()
                    val brainDumpResumeCaptureId by entry.savedStateHandle
                        .getStateFlow<Long?>(BrainDumpResumeContext.CaptureIdKey, null)
                        .collectAsStateWithLifecycle()
                    val focusCaptureRequest by entry.savedStateHandle
                        .getStateFlow(SharedTextContext.FocusCaptureKey, 0L)
                        .collectAsStateWithLifecycle()
                    val sharedTextForHome by entry.savedStateHandle
                        .getStateFlow<String?>(SharedTextContext.TextKey, null)
                        .collectAsStateWithLifecycle()
                    LaunchedEffect(sharedTextForHome) {
                        sharedTextForHome?.let { text ->
                            homeViewModel.receiveSharedText(text)
                            entry.savedStateHandle[SharedTextContext.TextKey] = null
                        }
                    }
                    LaunchedEffect(brainDumpResumeCaptureId) {
                        brainDumpResumeCaptureId?.let { captureId ->
                            homeSortViewModel.open(captureId)
                            entry.savedStateHandle[BrainDumpResumeContext.CaptureIdKey] = null
                        }
                    }
                    // The first-run line under the box must say where a save goes. Until the
                    // key is read, assume Gemini when the switches say so: never claim
                    // "only on this phone" for a save that would leave it.
                    val firstRun = !settings.hasCompletedFirstTimeTutorial
                    val homeSavesCouldUseGemini = settings.homeSavesUseGemini(hasGeminiKey = true)
                    val hasGeminiKey by produceState(
                        initialValue = true,
                        firstRun,
                        homeSavesCouldUseGemini,
                    ) {
                        value = !(firstRun && homeSavesCouldUseGemini) ||
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                container.geminiApiKeyStore.hasKey()
                            }
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        sortViewModel = homeSortViewModel,
                        weekUiState = homeWeekUiState,
                        calendarDateContext = CalendarCaptureContext.date(calendarCaptureEpochDay),
                        onCalendarDateContextConsumed = {
                            entry.savedStateHandle[CalendarCaptureContext.EpochDayKey] = null
                        },
                        onCalendarDateSelected = { date ->
                            navController.navigateToCalendar(date)
                        },
                        onVisibleWeekChanged = homeWeekViewModel::moveVisibleWeek,
                        onShowToday = homeWeekViewModel::showToday,
                        // The same "Adding for …" banner as Calendar's "+", with the keyboard open.
                        onAddForDate = { date ->
                            entry.savedStateHandle[CalendarCaptureContext.EpochDayKey] = date.toEpochDay()
                            entry.savedStateHandle[SharedTextContext.FocusCaptureKey] = System.nanoTime()
                        },
                        userName = settings.userName,
                        timeFormat = timeFormat,
                        onOpenSettings = {
                            navController.navigate(OrbitDestination.Settings.route) {
                                launchSingleTop = true
                            }
                        },
                        onOpenSearch = {
                            navController.navigate(SearchDestination.BaseRoute) { launchSingleTop = true }
                        },
                        focusCaptureOnOpen = settings.focusCaptureOnOpen,
                        focusRequest = focusCaptureRequest,
                        firstRun = firstRun,
                        firstRunSavesUseGemini = settings.homeSavesUseGemini(hasGeminiKey),
                        firstRunGeminiOnlyForBrainDumps = settings.homeSavesUseGeminiOnlyForBrainDumps(hasGeminiKey),
                        voiceCloudNoticeSeen = settings.hasSeenVoiceCloudNotice,
                        onVoiceCloudNoticeSeen = {
                            onSettingsChanged(settings.copy(hasSeenVoiceCloudNotice = true))
                        },
                        onOpenReceiptTarget = { target ->
                            navController.navigate(ItemDetailDestination.route(target.type.toItemDetailType(), target.id))
                        },
                    )
                }
                composable(OrbitDestination.Spaces.route) {
                    val spacesViewModel: SpacesViewModel = viewModel(
                        factory = SpacesViewModel.Factory(container),
                    )
                    val spacesUiState by spacesViewModel.uiState.collectAsStateWithLifecycle()
                    SpacesScreen(
                        uiState = spacesUiState,
                        timeFormat = timeFormat,
                        onSpaceSelected = { spaceId ->
                            if (spaceId != null) navController.navigate(SpaceDetailDestination.route(spaceId))
                        },
                        onCreateSpace = spacesViewModel::createSpace,
                        onUpdateSpace = spacesViewModel::updateSpace,
                        onHideSpace = spacesViewModel::hideSpace,
                        onArchiveSpace = spacesViewModel::archiveSpace,
                        onRestoreSpace = spacesViewModel::restoreSpace,
                        onMoveSpace = spacesViewModel::moveSpace,
                        onMoveItem = spacesViewModel::moveItem,
                        onUndoMove = spacesViewModel::undoLastMove,
                        onRetryMove = spacesViewModel::retryFailedMove,
                        onUnfiledSelected = {
                            navController.navigate(SpaceDetailDestination.UnfiledRoute)
                        },
                        onOpenToSort = {
                            navController.navigate(OrbitDestination.Review.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(OrbitDestination.Home.route) { saveState = true }
                            }
                        },
                        onOpenSearch = {
                            navController.navigate(SearchDestination.BaseRoute) {
                                launchSingleTop = true
                            }
                        },
                        onItemSelected = { item ->
                            navController.navigate(item.route())
                        },
                        onToggleDone = spacesViewModel::toggleDone,
                        onAddThought = spaceCaptureViewModel::open,
                        onSortThought = spaceCaptureViewModel::openSort,
                    )
                }
                composable(SpaceDetailDestination.UnfiledRoute) {
                    val spacesViewModel: SpacesViewModel = viewModel(
                        factory = SpacesViewModel.Factory(container),
                    )
                    LaunchedEffect(Unit) { spacesViewModel.selectUnfiled() }
                    val spacesUiState by spacesViewModel.uiState.collectAsStateWithLifecycle()
                    SpacesScreen(
                        uiState = spacesUiState,
                        timeFormat = timeFormat,
                        onSpaceSelected = { navController.popBackStack() },
                        onCreateSpace = spacesViewModel::createSpace,
                        onUpdateSpace = spacesViewModel::updateSpace,
                        onHideSpace = spacesViewModel::hideSpace,
                        onArchiveSpace = spacesViewModel::archiveSpace,
                        onRestoreSpace = spacesViewModel::restoreSpace,
                        onMoveSpace = spacesViewModel::moveSpace,
                        onMoveItem = spacesViewModel::moveItem,
                        onUndoMove = spacesViewModel::undoLastMove,
                        onRetryMove = spacesViewModel::retryFailedMove,
                        onUnfiledSelected = {},
                        onOpenSearch = { navController.navigate(SearchDestination.BaseRoute) },
                        onItemSelected = { item -> navController.navigate(item.route()) },
                        onToggleDone = spacesViewModel::toggleDone,
                        onAddThought = spaceCaptureViewModel::open,
                        onSortThought = spaceCaptureViewModel::openSort,
                        onOpenToSort = {
                            navController.navigate(OrbitDestination.Review.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(OrbitDestination.Home.route) { saveState = true }
                            }
                        },
                        onOpenThought = { captureId ->
                            navController.navigate(ItemDetailDestination.route(ItemDetailType.Capture, captureId))
                        },
                    )
                }
                composable(
                    route = SpaceDetailDestination.Route,
                    arguments = listOf(navArgument(SpaceDetailDestination.SpaceIdArgument) {
                        type = NavType.LongType
                    }),
                ) { entry ->
                    val spaceId = entry.arguments?.getLong(SpaceDetailDestination.SpaceIdArgument) ?: return@composable
                    val spacesViewModel: SpacesViewModel = viewModel(
                        factory = SpacesViewModel.Factory(container),
                    )
                    LaunchedEffect(spaceId) { spacesViewModel.selectSpace(spaceId) }
                    val spacesUiState by spacesViewModel.uiState.collectAsStateWithLifecycle()
                    SpacesScreen(
                        uiState = spacesUiState,
                        timeFormat = timeFormat,
                        onSpaceSelected = { navController.popBackStack() },
                        onCreateSpace = spacesViewModel::createSpace,
                        onUpdateSpace = spacesViewModel::updateSpace,
                        onHideSpace = spacesViewModel::hideSpace,
                        onArchiveSpace = spacesViewModel::archiveSpace,
                        onRestoreSpace = spacesViewModel::restoreSpace,
                        onMoveSpace = spacesViewModel::moveSpace,
                        onMoveItem = spacesViewModel::moveItem,
                        onUndoMove = spacesViewModel::undoLastMove,
                        onRetryMove = spacesViewModel::retryFailedMove,
                        onUnfiledSelected = {},
                        onOpenSearch = { navController.navigate(SearchDestination.BaseRoute) },
                        onItemSelected = { item -> navController.navigate(item.route()) },
                        onToggleDone = spacesViewModel::toggleDone,
                        onAddThought = spaceCaptureViewModel::open,
                        onSortThought = spaceCaptureViewModel::openSort,
                        onOpenToSort = {
                            navController.navigate(OrbitDestination.Review.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(OrbitDestination.Home.route) { saveState = true }
                            }
                        },
                        onOpenThought = { captureId ->
                            navController.navigate(ItemDetailDestination.route(ItemDetailType.Capture, captureId))
                        },
                    )
                }
                composable(OrbitDestination.Review.route) { entry ->
                    val reviewViewModel: ReviewViewModel = viewModel(
                        factory = ReviewViewModel.Factory(container),
                    )
                    val reviewSortViewModel: CaptureSortViewModel = viewModel(
                        factory = CaptureSortViewModel.factory(container, entry.savedStateHandle),
                    )
                    val reviewUiState by reviewViewModel.uiState.collectAsStateWithLifecycle()
                    // The one-item sheet for a thought in To sort (review §9.3).
                    val sortOneViewModel: com.orbit.app.ui.screens.review.SortOneViewModel = viewModel(
                        factory = com.orbit.app.ui.screens.review.SortOneViewModel.Factory(container),
                    )
                    val sortOneState by sortOneViewModel.uiState.collectAsStateWithLifecycle()
                    val sortOneSpaces by remember { container.spaceRepository.observeAll() }
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    LaunchedEffect(sortOneState.finished) {
                        sortOneState.finished?.let { message ->
                            reviewViewModel.showSortMessage(message)
                            sortOneViewModel.finishedShown()
                        }
                    }
                    if (sortOneState.captureId != null) {
                        com.orbit.app.ui.screens.review.SortOneSheet(
                            state = sortOneState,
                            spaces = sortOneSpaces,
                            timeFormat = timeFormat,
                            onTitleChange = sortOneViewModel::onTitleChange,
                            onSelectSpace = sortOneViewModel::selectSpace,
                            onSaveSuggested = sortOneViewModel::saveSuggested,
                            onChooseSomethingElse = sortOneViewModel::chooseSomethingElse,
                            onChooseNote = sortOneViewModel::chooseNote,
                            onChooseTask = sortOneViewModel::chooseTask,
                            onChooseReminder = sortOneViewModel::chooseReminder,
                            onRemindAt = sortOneViewModel::remindAt,
                            onPickTime = sortOneViewModel::pickTime,
                            onBackToTime = sortOneViewModel::backToTime,
                            onBackFromTime = sortOneViewModel::backFromTime,
                            onKeepAsTask = sortOneViewModel::keepAsTask,
                            onDecideLater = sortOneViewModel::decideLater,
                            // Labels, dates and splitting stay one step away in the full sheet.
                            onMoreOptions = {
                                val captureId = sortOneState.captureId
                                sortOneViewModel.dismiss()
                                captureId?.let(reviewSortViewModel::open)
                            },
                            onDismiss = sortOneViewModel::dismiss,
                        )
                    }
                    ReviewScreen(
                        uiState = reviewUiState,
                        onFocusedSortChanged = { focusedSortOpen = it },
                        timeFormat = timeFormat,
                        onReviewItemSelected = { item ->
                            navController.navigate(item.toItemDetailRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onKeepTaskActive = reviewViewModel::keepTaskActive,
                        onConfirmCapture = { loop ->
                            if (loop.hasPendingBrainDump) {
                                navController.returnHomeToResumeBrainDump(loop.id)
                            } else {
                                reviewViewModel.confirmCapture(loop)
                            }
                        },
                        onArchive = reviewViewModel::archive,
                        onCompleteTask = reviewViewModel::completeTask,
                        onDeferTask = reviewViewModel::deferTask,
                        onDismissCapture = reviewViewModel::dismissCapture,
                        onMakeSmaller = reviewViewModel::makeSmaller,
                        onUndoTaskMutation = reviewViewModel::undoTaskMutation,
                        onTaskUndoExpired = reviewViewModel::expireTaskUndo,
                        onCarryForwardTomorrow = reviewViewModel::carryForwardTomorrow,
                        onCarryForwardToDate = reviewViewModel::carryForwardToDate,
                        onKeepCarryForwardUnscheduled =
                            reviewViewModel::keepCarryForwardUnscheduled,
                        onCompleteCarryForward = reviewViewModel::completeCarryForward,
                        onWeeklyLookBackVisible = reviewViewModel::loadWeeklySummary,
                        onCarryForwardAllTomorrow = reviewViewModel::carryForwardAllTomorrow,
                        onUndoChange = reviewViewModel::undoChange,
                        onChangeUndoExpired = reviewViewModel::changeUndoExpired,
                        onAskLuma = { prompt ->
                            situationAiQuestion = prompt?.name
                            showSituationAi = true
                        },
                        onAcceptToSort = reviewViewModel::acceptSuggestion,
                        onChangeToSort = { item ->
                            // A Brain Dump continues in its own flow; one thought gets the one-item sheet.
                            if (item.state == com.orbit.app.ui.screens.review.ToSortState.BrainDump) {
                                reviewSortViewModel.open(item.captureId)
                            } else {
                                sortOneViewModel.open(item)
                            }
                        },
                        onSplitToSort = { item -> reviewSortViewModel.splitIntoThoughts(item.captureId) },
                        onOpenToSort = { item ->
                            navController.navigate(ItemDetailDestination.route(ItemDetailType.Capture, item.captureId))
                        },
                        onHideSuggestion = reviewViewModel::hideSuggestion,
                        onLetGo = reviewViewModel::letGo,
                        onUndoSort = reviewViewModel::undoSort,
                        onAcceptAllToSort = reviewViewModel::acceptAllSuggestions,
                        onSortFeedbackShown = reviewViewModel::sortFeedbackShown,
                        sortHost = { snackbarHostState ->
                            CaptureSortHost(
                                viewModel = reviewSortViewModel,
                                timeFormat = timeFormat,
                                snackbarHostState = snackbarHostState,
                            )
                        },
                        onNeedsYouRemindAt = reviewViewModel::remindAt,
                        // A reminder that exists gets its new time on its own screen; a thought
                        // still saved opens the sort sheet on the reminder setup.
                        onNeedsYouPickTime = { item ->
                            val reminderId = item.reminderId
                            if (reminderId != null) {
                                navController.navigate(ItemDetailDestination.route(ItemDetailType.Reminder, reminderId))
                            } else {
                                reviewSortViewModel.open(item.captureId, startWithReminderSetup = true)
                            }
                        },
                        onNeedsYouMakeTask = reviewViewModel::makeTask,
                        onNeedsYouTryAgain = reviewViewModel::retryReminder,
                        onNeedsYouKeep = reviewViewModel::keepThought,
                    )
                }
                composable(OrbitDestination.Settings.route) {
                    val localDataToolsViewModel: LocalDataToolsViewModel = viewModel(
                        factory = LocalDataToolsViewModel.Factory(container),
                    )
                    val aiSettingsViewModel: AiSettingsViewModel = viewModel(
                        factory = AiSettingsViewModel.Factory(container),
                    )
                    val localDataToolsUiState by localDataToolsViewModel.uiState.collectAsStateWithLifecycle()
                    val aiSettingsUiState by aiSettingsViewModel.uiState.collectAsStateWithLifecycle()
                    // F10: after a reset Tallele opens as on its first day: the phone's
                    // language, Home with the starter Spaces and the first-time guide.
                    val resetActivity = androidx.activity.compose.LocalActivity.current
                    LaunchedEffect(localDataToolsUiState.restoreMessage) {
                        if (localDataToolsUiState.restoreMessage == LocalDataToolsMessage.ResetCompleted) {
                            localDataToolsViewModel.messageShown()
                            navController.popBackStack(OrbitDestination.Home.route, inclusive = false)
                            if (applicationLanguage != AppLanguage.SystemDefault) {
                                onApplicationLanguageChanged(AppLanguage.SystemDefault)
                            } else {
                                resetActivity?.recreate()
                            }
                        }
                    }
                    SettingsScreen(
                        settings = settings,
                        onSettingsChanged = onSettingsChanged,
                        applicationLanguage = applicationLanguage,
                        onApplicationLanguageChanged = onApplicationLanguageChanged,
                        aiSettings = aiSettingsUiState,
                        onSaveGeminiKey = aiSettingsViewModel::saveKey,
                        onDeleteGeminiKey = aiSettingsViewModel::deleteKey,
                        onClearAiLearningData = aiSettingsViewModel::clearLearningData,
                        onUpdateLearnedRule = aiSettingsViewModel::updateLearnedRule,
                        onDeleteLearnedRule = aiSettingsViewModel::deleteLearnedRule,
                        onTestGeminiConnection = aiSettingsViewModel::testConnection,
                        localDataTools = localDataToolsUiState,
                        onExportJson = localDataToolsViewModel::exportJson,
                        onRestoreFileSelected = localDataToolsViewModel::restoreFileSelected,
                        onConfirmRestore = localDataToolsViewModel::confirmRestore,
                        onCancelRestore = localDataToolsViewModel::cancelRestore,
                        onResetAllData = localDataToolsViewModel::resetAllData,
                        onRetryReminderSetup = localDataToolsViewModel::retryReminderSetup,
                        onOpenFirstTimeGuide = {
                            navController.navigate(
                                FirstTimeTutorialDestination.route(isReplay = true),
                            ) {
                                launchSingleTop = true
                            }
                        },
                        onSettingsSubsectionChanged = { settingsSubsectionOpen = it },
                        onClose = { navController.popBackStack() },
                    )
                }
                composable(
                    route = CalendarDestination.Route,
                    arguments = listOf(
                        navArgument(CalendarDestination.DateArgument) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    val calendarViewModel: CalendarViewModel = viewModel(
                        factory = CalendarViewModel.Factory(container.calendarRepository),
                    )
                    val calendarUiState by calendarViewModel.uiState.collectAsStateWithLifecycle()
                    val calendarSpaces by remember { container.spaceRepository.observeAll() }
                        .collectAsStateWithLifecycle(initialValue = emptyList())
                    CalendarScreen(
                        uiState = calendarUiState,
                        onPreviousDay = calendarViewModel::showPreviousDay,
                        onNextDay = calendarViewModel::showNextDay,
                        onPreviousMonth = calendarViewModel::showPreviousMonth,
                        onNextMonth = calendarViewModel::showNextMonth,
                        onPreviousWeek = calendarViewModel::showPreviousWeek,
                        onNextWeek = calendarViewModel::showNextWeek,
                        onToday = calendarViewModel::showToday,
                        onViewSelected = calendarViewModel::setActiveView,
                        onDateSelected = calendarViewModel::focusDate,
                        spaces = remember(calendarSpaces) { calendarSpaces.associateBy { it.id } },
                        timeFormat = timeFormat,
                        onEntrySelected = { entryId ->
                            navController.navigate(entryId.toItemDetailRoute()) {
                                launchSingleTop = true
                            }
                        },
                        onAddForSelectedDate = {
                            navController.returnHomeWithCalendarCaptureDate(calendarUiState.selectedDate)
                        },
                    )
                }
                composable(
                    route = SearchDestination.Route,
                    arguments = listOf(
                        navArgument(SearchDestination.QueryArgument) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    val searchViewModel: SearchViewModel = viewModel(
                        factory = SearchViewModel.Factory(container),
                    )
                    SearchScreen(
                        viewModel = searchViewModel,
                        initialQuery = entry.arguments?.getString(SearchDestination.QueryArgument),
                        onBack = { navController.popBackStack() },
                        onResultSelected = { result ->
                            navController.navigate(ItemDetailDestination.route(result.type, result.id))
                        },
                        onOpenSpace = { spaceId -> navController.navigate(SpaceDetailDestination.route(spaceId)) },
                    )
                }
                composable(
                    route = ReminderDestination.Route,
                    arguments = listOf(
                        navArgument(ReminderDestination.ReminderIdArgument) {
                            type = NavType.LongType
                        },
                    ),
                ) { entry ->
                    val reminderId = entry.arguments
                        ?.getLong(ReminderDestination.ReminderIdArgument)
                        ?: return@composable
                    val itemDetailViewModel: ItemDetailViewModel = viewModel(
                        key = "item_reminder_$reminderId",
                        factory = ItemDetailViewModel.Factory(
                            type = ItemDetailType.Reminder,
                            itemId = reminderId,
                            container = container,
                        ),
                    )
                    ItemDetailScreen(
                        viewModel = itemDetailViewModel,
                        timeFormat = timeFormat,
                        onBack = { navController.popBackStack() },
                        onTypeChanged = { changedType, changedId ->
                            navController.replaceCurrentItemDetail(entry, changedType, changedId)
                        },
                        onUnsavedEditsChanged = { unsavedItemEdits = it },
                        onResumeBrainDump = { captureId ->
                            navController.returnHomeToResumeBrainDump(captureId)
                        },
                    )
                }
                composable(
                    route = ItemDetailDestination.Route,
                    arguments = listOf(
                        navArgument(ItemDetailDestination.TypeArgument) {
                            type = NavType.StringType
                        },
                        navArgument(ItemDetailDestination.ItemIdArgument) {
                            type = NavType.LongType
                        },
                    ),
                ) { entry ->
                    val type = entry.arguments
                        ?.getString(ItemDetailDestination.TypeArgument)
                        ?.toItemDetailTypeOrNull()
                        ?: return@composable
                    val itemId = entry.arguments
                        ?.getLong(ItemDetailDestination.ItemIdArgument)
                        ?: return@composable
                    val itemDetailViewModel: ItemDetailViewModel = viewModel(
                        key = "item_${type.routeName}_$itemId",
                        factory = ItemDetailViewModel.Factory(
                            type = type,
                            itemId = itemId,
                            container = container,
                        ),
                    )
                    ItemDetailScreen(
                        viewModel = itemDetailViewModel,
                        timeFormat = timeFormat,
                        onBack = { navController.popBackStack() },
                        onTypeChanged = { changedType, changedId ->
                            navController.replaceCurrentItemDetail(entry, changedType, changedId)
                        },
                        onUnsavedEditsChanged = { unsavedItemEdits = it },
                        onResumeBrainDump = { captureId ->
                            navController.returnHomeToResumeBrainDump(captureId)
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = showBottomNavigation,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(OrbitMotion.StandardDurationMillis)) +
                    slideInVertically(
                        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
                        initialOffsetY = { it / 3 },
                    ),
                exit = fadeOut(tween(OrbitMotion.QuickDurationMillis)) +
                    slideOutVertically(
                        animationSpec = tween(OrbitMotion.StandardDurationMillis),
                        targetOffsetY = { it / 3 },
                    ),
            ) {
                FloatingBottomNavigation(
                    selectedRoute = selectedRoute,
                    alertDestinations = if (needsYouFailed) setOf(OrbitDestination.Review) else emptySet(),
                    onDestinationSelected = { destination ->
                        // Home is the root of every tab: going back to it is a pop that saves the
                        // tab being left (its tab keeps its place). Navigating to Home with
                        // restoreState could bring back the screen just left (Calendar opened from
                        // a day on Home), so the Home tab seemed to do nothing.
                        val backAtHome = destination == OrbitDestination.Home &&
                            navController.popBackStack(OrbitDestination.Home.route, inclusive = false, saveState = true)
                        if (!backAtHome) {
                            navController.navigate(destination.navigationRoute) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(OrbitDestination.Home.route) {
                                    saveState = true
                                }
                            }
                        }
                    },
                )
            }

            spaceCaptureState.spaceId?.let { spaceId ->
                val spaceName by produceState(initialValue = "", spaceId) {
                    value = container.spaceRepository.getById(spaceId)?.name.orEmpty()
                }
                val draft by remember(spaceId) { spaceCaptureViewModel.draftFlow(spaceId) }.collectAsStateWithLifecycle()
                com.orbit.app.ui.screens.spaces.SpaceCaptureFlowSheet(
                    state = spaceCaptureState,
                    spaceName = if (spaceName.isEmpty()) "" else com.orbit.app.ui.localization.localizedSpaceName(spaceName),
                    draft = draft,
                    timeFormat = timeFormat,
                    onDraftChange = { spaceCaptureViewModel.onDraftChanged(spaceId, it) },
                    onSaveAsChange = spaceCaptureViewModel::onSaveAsChanged,
                    onSave = spaceCaptureViewModel::save,
                    onDismiss = spaceCaptureViewModel::dismiss,
                    onRemindAt = spaceCaptureViewModel::remindAt,
                    onPickTime = spaceCaptureViewModel::pickTime,
                    onBackToTimeQuestion = spaceCaptureViewModel::backToTimeQuestion,
                    onKeepAsTask = spaceCaptureViewModel::keepAsTask,
                    onChooseNote = spaceCaptureViewModel::chooseNote,
                    onChooseTask = spaceCaptureViewModel::chooseTask,
                    onChooseReminder = spaceCaptureViewModel::chooseReminder,
                    onDecideLater = spaceCaptureViewModel::decideLater,
                )
            }
            spaceCaptureState.finished?.let { finished ->
                com.orbit.app.ui.screens.spaces.SpaceCaptureFinishedMessage(
                    finished = finished,
                    container = container,
                    timeFormat = timeFormat,
                    host = spaceSavedMessages,
                    onOpen = { target ->
                        navController.navigate(
                            ItemDetailDestination.route(
                                when (target.type) {
                                    com.orbit.app.ui.screens.home.ReceiptItemType.Note -> ItemDetailType.Note
                                    com.orbit.app.ui.screens.home.ReceiptItemType.Task -> ItemDetailType.Task
                                    com.orbit.app.ui.screens.home.ReceiptItemType.Reminder -> ItemDetailType.Reminder
                                    com.orbit.app.ui.screens.home.ReceiptItemType.Capture -> ItemDetailType.Capture
                                },
                                target.id,
                            ),
                        )
                    },
                    onShown = spaceCaptureViewModel::finishedShown,
                )
            }
            androidx.compose.material3.SnackbarHost(
                hostState = spaceSavedMessages,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = OrbitBottomNavigationDefaults.ContentClearance),
            )
            if (showSituationAi) {
                val situationViewModel: SituationAiViewModel = viewModel(
                    factory = SituationAiViewModel.Factory(container),
                )
                val situationUiState by situationViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(situationAiQuestion) {
                    situationAiQuestion
                        ?.let { name -> runCatching { AskLumaQuestion.valueOf(name) }.getOrNull() }
                        ?.let(situationViewModel::askQuestion)
                    situationAiQuestion = null
                }
                SituationAiSheet(
                    uiState = situationUiState,
                    onDismiss = {
                        showSituationAi = false
                    },
                    onSourceSelected = { source ->
                        showSituationAi = false
                        navController.navigate(ItemDetailDestination.route(source.type, source.itemId)) {
                            launchSingleTop = true
                        }
                    },
                    onAskQueryChanged = situationViewModel::updateAskQuery,
                    onAskLuma = situationViewModel::askLuma,
                    onSearch = { query ->
                        showSituationAi = false
                        navController.navigate(SearchDestination.route(query)) { launchSingleTop = true }
                    },
                )
            }
        }
    }
}

/*
 * Tabs use a quiet "fade through": the old screen fades out quickly, the new one fades in
 * and settles from 98% scale. Nothing slides sideways between tabs, because tabs are
 * siblings, not a sequence. Pushed screens (Settings, item details) slide a short way in
 * with a decelerating curve and leave faster than they arrive.
 */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
private const val FadeThroughOutMillis = 90
private const val FadeThroughInMillis = 220
private const val PushInMillis = 380
private const val PushOutMillis = 200

private fun isTabSwitch(initialRoute: String?, targetRoute: String?): Boolean {
    val tabs = OrbitDestination.bottomBar.map { it.route }
    return initialRoute in tabs && targetRoute in tabs
}

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(FadeThroughInMillis, delayMillis = FadeThroughOutMillis, easing = LinearOutSlowInEasing)) +
        scaleIn(
            animationSpec = tween(FadeThroughInMillis, delayMillis = FadeThroughOutMillis, easing = EmphasizedDecelerate),
            initialScale = 0.98f,
        )

private fun fadeThroughOut(): ExitTransition =
    fadeOut(tween(FadeThroughOutMillis, easing = FastOutLinearInEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitEnterTransition(): EnterTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    if (isTabSwitch(from, to)) return fadeThroughIn()
    return slideIntoContainer(
        towards = navigationSlideDirection(initialRoute = from, targetRoute = to),
        animationSpec = tween(PushInMillis, easing = EmphasizedDecelerate),
        initialOffset = { it / 10 },
    ) + fadeIn(tween(OrbitMotion.StandardDurationMillis, easing = LinearOutSlowInEasing))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitExitTransition(): ExitTransition {
    val from = initialState.destination.route
    val to = targetState.destination.route
    if (isTabSwitch(from, to)) return fadeThroughOut()
    return slideOutOfContainer(
        towards = navigationSlideDirection(initialRoute = from, targetRoute = to),
        animationSpec = tween(PushOutMillis, easing = EmphasizedAccelerate),
        targetOffset = { it / 16 },
    ) + fadeOut(tween(PushOutMillis, easing = FastOutLinearInEasing))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitPopEnterTransition(): EnterTransition {
    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) return fadeThroughIn()
    return slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(PushInMillis, easing = EmphasizedDecelerate),
        initialOffset = { it / 16 },
    ) + fadeIn(tween(OrbitMotion.StandardDurationMillis, easing = LinearOutSlowInEasing))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitPopExitTransition(): ExitTransition {
    if (isTabSwitch(initialState.destination.route, targetState.destination.route)) return fadeThroughOut()
    return slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(PushOutMillis, easing = EmphasizedAccelerate),
        targetOffset = { it / 10 },
    ) + fadeOut(tween(PushOutMillis, easing = FastOutLinearInEasing))
}

private fun navigationSlideDirection(
    initialRoute: String?,
    targetRoute: String?,
): AnimatedContentTransitionScope.SlideDirection {
    val topLevelRoutes = OrbitDestination.bottomBar.map { it.route } + OrbitDestination.Settings.route
    val initialIndex = topLevelRoutes.indexOf(initialRoute)
    val targetIndex = topLevelRoutes.indexOf(targetRoute)
    return if (initialIndex >= 0 && targetIndex >= 0 && targetIndex < initialIndex) {
        AnimatedContentTransitionScope.SlideDirection.Right
    } else {
        AnimatedContentTransitionScope.SlideDirection.Left
    }
}

internal fun shouldShowFloatingBottomNavigation(
    imeVisible: Boolean,
    selectedRoute: String?,
    appearanceSubsectionOpen: Boolean,
    focusedSortOpen: Boolean = false,
): Boolean = !imeVisible && !focusedSortOpen &&
    selectedRoute != FirstTimeTutorialDestination.Route &&
    selectedRoute != OrbitDestination.Settings.route &&
    selectedRoute != SearchDestination.Route &&
    selectedRoute != ItemDetailDestination.Route &&
    selectedRoute != ReminderDestination.Route &&
    !appearanceSubsectionOpen

internal fun portraitContentMaxWidth(availableWidth: Dp): Dp = when {
    availableWidth < 600.dp -> availableWidth
    availableWidth < 840.dp -> 720.dp
    else -> 840.dp
}.coerceAtMost(availableWidth)

private fun NavController.replaceCurrentItemDetail(
    entry: NavBackStackEntry,
    type: ItemDetailType,
    itemId: Long,
) {
    navigate(ItemDetailDestination.route(type, itemId)) {
        popUpTo(entry.destination.id) { inclusive = true }
        launchSingleTop = true
    }
}

private fun ReceiptItemType.toItemDetailType(): ItemDetailType = when (this) {
    ReceiptItemType.Capture -> ItemDetailType.Capture
    ReceiptItemType.Note -> ItemDetailType.Note
    ReceiptItemType.Task -> ItemDetailType.Task
    ReceiptItemType.Reminder -> ItemDetailType.Reminder
}

internal fun glassRenderingPolicyForRoute(selectedRoute: String?): GlassRenderingPolicy =
    when (selectedRoute) {
        OrbitDestination.Home.route,
        OrbitDestination.Spaces.route,
        OrbitDestination.Review.route,
        OrbitDestination.Settings.route,
        -> GlassRenderingPolicy.LiveAllowed

        else -> GlassRenderingPolicy.SoftOnly
    }

internal fun shouldEnableHazeCapture(selectedRoute: String?): Boolean =
    glassRenderingPolicyForRoute(selectedRoute) == GlassRenderingPolicy.LiveAllowed

private fun com.orbit.app.ui.screens.spaces.SpaceItemReference.route(): String {
    val detailType = when (type) {
        com.orbit.app.ui.screens.spaces.SpaceItemType.Note -> ItemDetailType.Note
        com.orbit.app.ui.screens.spaces.SpaceItemType.Task -> ItemDetailType.Task
        com.orbit.app.ui.screens.spaces.SpaceItemType.Reminder -> ItemDetailType.Reminder
    }
    return ItemDetailDestination.route(detailType, id)
}

/** Time until the next local midnight, plus a second so the new day has begun. */
internal fun millisUntilNextLocalMidnight(now: java.time.ZonedDateTime): Long =
    java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis()
        .coerceAtLeast(0L) + 1_000L
