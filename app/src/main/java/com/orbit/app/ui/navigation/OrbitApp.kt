package com.orbit.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.orbit.app.ui.components.FloatingBottomNavigation
import com.orbit.app.ui.components.OrbitBackground
import com.orbit.app.ui.screens.calendar.CalendarScreen
import com.orbit.app.ui.screens.calendar.CalendarViewModel
import com.orbit.app.ui.screens.home.HomeScreen
import com.orbit.app.ui.screens.home.HomeWeekViewModel
import com.orbit.app.ui.screens.item.ItemDetailScreen
import com.orbit.app.ui.screens.item.ItemDetailViewModel
import com.orbit.app.ui.screens.review.ReviewScreen
import com.orbit.app.ui.screens.review.ReviewViewModel
import com.orbit.app.ui.screens.review.ReminderDetailScreen
import com.orbit.app.ui.screens.review.ReminderDetailViewModel
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

@Composable
fun OrbitApp(
    container: OrbitContainer,
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    reminderToOpen: Long?,
    onReminderOpened: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedRoute = backStackEntry?.destination?.route
    var showSituationAi by rememberSaveable { mutableStateOf(false) }
    var restoreSituationAiFocus by rememberSaveable { mutableStateOf(false) }
    val situationAiFocusRequester = remember { FocusRequester() }
    val timeFormat = currentOrbitTimeFormat(settings.timeFormatMode)
    val imeVisible = with(LocalDensity.current) {
        WindowInsets.ime.getBottom(this) > 0
    }

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

    OrbitBackground(settings = settings) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = OrbitDestination.Home.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(OrbitDestination.Home.route) { entry ->
                    val homeViewModel: HomeCaptureViewModel = viewModel(
                        factory = HomeCaptureViewModel.Factory(
                            captureRepository = container.captureRepository,
                            spaceRepository = container.spaceRepository,
                            appSettingsRepository = container.appSettingsRepository,
                            aiRouter = container.aiRouter,
                            confirmCaptureAction = container.confirmCaptureAction,
                            recordAiLearningEvent = container.recordAiLearningEvent,
                        ),
                    )
                    val homeWeekViewModel: HomeWeekViewModel = viewModel(
                        factory = HomeWeekViewModel.Factory(container.calendarRepository),
                    )
                    val homeWeekUiState by homeWeekViewModel.uiState.collectAsStateWithLifecycle()
                    val calendarCaptureEpochDay by entry.savedStateHandle
                        .getStateFlow<Long?>(CalendarCaptureContext.EpochDayKey, null)
                        .collectAsStateWithLifecycle()
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
                        onReminderSelected = { reminderId ->
                            navController.navigate(ReminderDestination.route(reminderId))
                        },
                        onKeepTaskActive = reviewViewModel::keepTaskActive,
                        onConfirmCapture = reviewViewModel::confirmCapture,
                        onArchive = reviewViewModel::archive,
                        onCompleteTask = reviewViewModel::completeTask,
                        onDeferTask = reviewViewModel::deferTask,
                        onDismissCapture = reviewViewModel::dismissCapture,
                        onMakeSmaller = reviewViewModel::makeSmaller,
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
                        aiSettings = aiSettingsUiState,
                        onSaveGeminiKey = aiSettingsViewModel::saveKey,
                        onDeleteGeminiKey = aiSettingsViewModel::deleteKey,
                        onTestGeminiConnection = aiSettingsViewModel::testConnection,
                        localDataTools = localDataToolsUiState,
                        onExportJson = localDataToolsViewModel::exportJson,
                        onRestoreFileSelected = localDataToolsViewModel::restoreFileSelected,
                        onConfirmRestore = localDataToolsViewModel::confirmRestore,
                        onCancelRestore = localDataToolsViewModel::cancelRestore,
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
                    val reminderDetailViewModel: ReminderDetailViewModel = viewModel(
                        key = "reminder_$reminderId",
                        factory = ReminderDetailViewModel.Factory(
                            reminderId = reminderId,
                            reminderRepository = container.reminderRepository,
                            captureRepository = container.captureRepository,
                            taskRepository = container.taskRepository,
                        ),
                    )
                    ReminderDetailScreen(
                        viewModel = reminderDetailViewModel,
                        timeFormat = timeFormat,
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
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
                    )
                }
            }

            if (!imeVisible && selectedRoute != CalendarDestination.Route) {
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
                    modifier = Modifier.align(Alignment.BottomCenter),
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
                        situationViewModel.clearPanel()
                        restoreSituationAiFocus = true
                        showSituationAi = false
                    },
                    onPanelSelected = situationViewModel::show,
                    onOpenReview = {
                        situationViewModel.clearPanel()
                        showSituationAi = false
                        navController.navigate(OrbitDestination.Review.route) {
                            launchSingleTop = true
                            restoreState = true
                            popUpTo(OrbitDestination.Home.route) { saveState = true }
                        }
                    },
                    onSourceSelected = { source ->
                        situationViewModel.clearPanel()
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

private fun com.orbit.app.ui.screens.spaces.SpaceItemReference.route(): String {
    val detailType = when (type) {
        com.orbit.app.ui.screens.spaces.SpaceItemType.Note -> ItemDetailType.Note
        com.orbit.app.ui.screens.spaces.SpaceItemType.Task -> ItemDetailType.Task
        com.orbit.app.ui.screens.spaces.SpaceItemType.Reminder -> ItemDetailType.Reminder
        com.orbit.app.ui.screens.spaces.SpaceItemType.Capture -> ItemDetailType.Capture
    }
    return ItemDetailDestination.route(detailType, id)
}
