package com.orbit.app.ui.navigation

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
import com.orbit.app.ui.components.GlassRenderingPolicy
import com.orbit.app.ui.components.OrbitBackground
import com.orbit.app.ui.components.calmPressHaptics
import com.orbit.app.ui.screens.calendar.CalendarScreen
import com.orbit.app.ui.screens.calendar.CalendarViewModel
import com.orbit.app.ui.screens.home.HomeScreen
import com.orbit.app.ui.screens.home.HomeWeekViewModel
import com.orbit.app.ui.screens.item.ItemDetailScreen
import com.orbit.app.ui.screens.item.ItemDetailViewModel
import com.orbit.app.ui.screens.review.ReviewScreen
import com.orbit.app.ui.screens.review.ReviewViewModel
import com.orbit.app.ui.screens.search.SearchScreen
import com.orbit.app.ui.screens.search.SearchViewModel
import com.orbit.app.ui.screens.settings.AiSettingsViewModel
import com.orbit.app.ui.screens.settings.LocalDataToolsViewModel
import com.orbit.app.ui.screens.settings.SettingsScreen
import com.orbit.app.ui.screens.situation.SituationAiSheet
import com.orbit.app.ui.screens.situation.SituationAiViewModel
import com.orbit.app.ui.screens.spaces.SpacesScreen
import com.orbit.app.ui.screens.spaces.SpacesViewModel
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.ui.screens.home.HomeCaptureViewModel
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
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route
    var showSituationAi by rememberSaveable { mutableStateOf(false) }
    var restoreSituationAiFocus by rememberSaveable { mutableStateOf(false) }
    var appearanceSubsectionOpen by rememberSaveable { mutableStateOf(false) }
    val situationAiFocusRequester = remember { FocusRequester() }
    val timeFormat = currentOrbitTimeFormat(settings.timeFormatMode)
    val contentMaxWidth = portraitContentMaxWidth(LocalConfiguration.current.screenWidthDp.dp)
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }
    val showBottomNavigation = shouldShowFloatingBottomNavigation(
        imeVisible = imeVisible,
        selectedRoute = selectedRoute,
        appearanceSubsectionOpen = appearanceSubsectionOpen,
    )

    LaunchedEffect(reminderToOpen) {
        reminderToOpen?.let { reminderId ->
            navController.navigate(ReminderDestination.route(reminderId)) {
                launchSingleTop = true
            }
            onReminderOpened()
        }
    }

    LaunchedEffect(showSituationAi, imeVisible, restoreSituationAiFocus) {
        if (!showSituationAi && !imeVisible && restoreSituationAiFocus) {
            situationAiFocusRequester.requestFocus()
            restoreSituationAiFocus = false
        }
    }

    LaunchedEffect(selectedRoute) {
        if (selectedRoute != OrbitDestination.Settings.route) {
            appearanceSubsectionOpen = false
        }
    }

    OrbitBackground(
        settings = settings,
        glassRenderingPolicy = glassRenderingPolicyForRoute(selectedRoute),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .calmPressHaptics(),
        ) {
            NavHost(
                navController = navController,
                startDestination = OrbitDestination.Home.route,
                modifier = Modifier
                    .widthIn(max = contentMaxWidth)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                enterTransition = { orbitEnterTransition() },
                exitTransition = { orbitExitTransition() },
                popEnterTransition = { orbitPopEnterTransition() },
                popExitTransition = { orbitPopExitTransition() },
            ) {
                composable(OrbitDestination.Home.route) { entry ->
                    val homeViewModel: HomeCaptureViewModel = viewModel(
                        factory = HomeCaptureViewModel.Factory(
                            context = container.applicationContext,
                            captureRepository = container.captureRepository,
                            brainDumpRepository = container.brainDumpRepository,
                            spaceRepository = container.spaceRepository,
                            appSettingsRepository = container.appSettingsRepository,
                            aiRouter = container.aiRouter,
                            confirmCaptureAction = container.confirmCaptureAction,
                            brainDumpActions = container.brainDumpActions,
                            reminderRepository = container.reminderRepository,
                            recordAiLearningEvent = container.recordAiLearningEvent,
                            proposeLearnedRule = container.proposeLearnedRule,
                            savedStateHandle = entry.savedStateHandle,
                        ),
                    )
                    val homeWeekViewModel: HomeWeekViewModel = viewModel(
                        factory = HomeWeekViewModel.Factory(container.calendarRepository),
                    )
                    val homeWeekUiState by homeWeekViewModel.uiState.collectAsStateWithLifecycle()
                    val calendarCaptureEpochDay by entry.savedStateHandle
                        .getStateFlow<Long?>(CalendarCaptureContext.EpochDayKey, null)
                        .collectAsStateWithLifecycle()
                    val brainDumpResumeCaptureId by entry.savedStateHandle
                        .getStateFlow<Long?>(BrainDumpResumeContext.CaptureIdKey, null)
                        .collectAsStateWithLifecycle()
                    LaunchedEffect(brainDumpResumeCaptureId) {
                        brainDumpResumeCaptureId?.let { captureId ->
                            homeViewModel.resumeBrainDump(captureId)
                            entry.savedStateHandle[BrainDumpResumeContext.CaptureIdKey] = null
                        }
                    }
                    HomeScreen(
                        viewModel = homeViewModel,
                        weekUiState = homeWeekUiState,
                        calendarDateContext = CalendarCaptureContext.date(calendarCaptureEpochDay),
                        onCalendarDateContextConsumed = {
                            entry.savedStateHandle[CalendarCaptureContext.EpochDayKey] = null
                        },
                        onCalendarDateSelected = { date ->
                            homeWeekViewModel.selectDate(date)
                            navController.navigateToCalendar(date)
                        },
                        onVisibleWeekChanged = homeWeekViewModel::moveVisibleWeek,
                        userName = settings.userName,
                        timeFormat = timeFormat,
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
                        onSpaceSelected = spacesViewModel::selectSpace,
                        onCreateSpace = spacesViewModel::createSpace,
                        onUpdateSpace = spacesViewModel::updateSpace,
                        onHideSpace = spacesViewModel::hideSpace,
                        onArchiveSpace = spacesViewModel::archiveSpace,
                        onRestoreSpace = spacesViewModel::restoreSpace,
                        onMoveSpace = spacesViewModel::moveSpace,
                        onMoveItem = spacesViewModel::moveItem,
                        onOpenSearch = {
                            navController.navigate(SearchDestination.Route) {
                                launchSingleTop = true
                            }
                        },
                        onItemSelected = { item ->
                            navController.navigate(item.route())
                        },
                    )
                }
                composable(OrbitDestination.Review.route) {
                    val reviewViewModel: ReviewViewModel = viewModel(
                        factory = ReviewViewModel.Factory(container),
                    )
                    val reviewUiState by reviewViewModel.uiState.collectAsStateWithLifecycle()
                    ReviewScreen(
                        uiState = reviewUiState,
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
                        onCarryForwardTomorrow = reviewViewModel::carryForwardTomorrow,
                        onCarryForwardToDate = reviewViewModel::carryForwardToDate,
                        onKeepCarryForwardUnscheduled =
                            reviewViewModel::keepCarryForwardUnscheduled,
                        onCompleteCarryForward = reviewViewModel::completeCarryForward,
                        onWeeklyLookBackVisible = reviewViewModel::loadWeeklySummary,
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
                        onAppearanceSubsectionChanged = { appearanceSubsectionOpen = it },
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
                    CalendarScreen(
                        uiState = calendarUiState,
                        onBack = { navController.popBackStack() },
                        onPreviousDay = calendarViewModel::showPreviousDay,
                        onNextDay = calendarViewModel::showNextDay,
                        onPreviousMonth = calendarViewModel::showPreviousMonth,
                        onNextMonth = calendarViewModel::showNextMonth,
                        onToday = calendarViewModel::showToday,
                        onViewSelected = calendarViewModel::setActiveView,
                        onDateSelected = calendarViewModel::selectDate,
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
                ) {
                    val searchViewModel: SearchViewModel = viewModel(
                        factory = SearchViewModel.Factory(container),
                    )
                    SearchScreen(
                        viewModel = searchViewModel,
                        onBack = { navController.popBackStack() },
                        onResultSelected = { result ->
                            navController.navigate(ItemDetailDestination.route(result.type, result.id))
                        },
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
                    onDestinationSelected = { destination ->
                        navController.navigate(destination.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(OrbitDestination.Home.route) {
                                saveState = true
                            }
                        }
                    },
                    onSituationAiSelected = { showSituationAi = true },
                    situationAiFocusRequester = situationAiFocusRequester,
                )
            }

            if (showSituationAi) {
                val situationViewModel: SituationAiViewModel = viewModel(
                    factory = SituationAiViewModel.Factory(container),
                )
                val situationUiState by situationViewModel.uiState.collectAsStateWithLifecycle()
                SituationAiSheet(
                    uiState = situationUiState,
                    onDismiss = {
                        restoreSituationAiFocus = true
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
                )
            }
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitEnterTransition(): EnterTransition {
    val direction = navigationSlideDirection(
        initialRoute = initialState.destination.route,
        targetRoute = targetState.destination.route,
    )
    return slideIntoContainer(
        towards = direction,
        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
        initialOffset = { it / 7 },
    ) + fadeIn(tween(OrbitMotion.StandardDurationMillis))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitExitTransition(): ExitTransition {
    val direction = navigationSlideDirection(
        initialRoute = initialState.destination.route,
        targetRoute = targetState.destination.route,
    )
    return slideOutOfContainer(
        towards = direction,
        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
        targetOffset = { it / 10 },
    ) + fadeOut(tween(OrbitMotion.StandardDurationMillis))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitPopEnterTransition(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
        initialOffset = { it / 7 },
    ) + fadeIn(tween(OrbitMotion.StandardDurationMillis))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.orbitPopExitTransition(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Right,
        animationSpec = tween(OrbitMotion.EmphasizedDurationMillis),
        targetOffset = { it / 10 },
    ) + fadeOut(tween(OrbitMotion.StandardDurationMillis))

private fun navigationSlideDirection(
    initialRoute: String?,
    targetRoute: String?,
): AnimatedContentTransitionScope.SlideDirection {
    val topLevelRoutes = listOf(
        OrbitDestination.Home.route,
        OrbitDestination.Spaces.route,
        OrbitDestination.Review.route,
        OrbitDestination.Settings.route,
    )
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
): Boolean = !imeVisible &&
    selectedRoute != CalendarDestination.Route &&
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
        com.orbit.app.ui.screens.spaces.SpaceItemType.Capture -> ItemDetailType.Capture
    }
    return ItemDetailDestination.route(detailType, id)
}
