package com.orbit.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.orbit.app.R
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.ui.screens.review.ReviewItem
import com.orbit.app.ui.screens.review.ReviewItemType
import java.time.LocalDate

enum class OrbitDestination(
    /** Route pattern as registered in the NavHost (what the back stack reports). */
    val route: String,
    @param:StringRes val contentDescriptionRes: Int,
    val icon: ImageVector,
    /** Main tabs in the bottom bar; Settings is reached from Home's top-right corner. */
    val inBottomBar: Boolean,
    /** Concrete route used to navigate (patterns with optional arguments resolve here). */
    val navigationRoute: String = route,
) {
    Home("home", R.string.navigation_home, Icons.Rounded.Home, inBottomBar = true),
    Spaces("spaces", R.string.navigation_spaces, Icons.Rounded.GridView, inBottomBar = true),
    Calendar(
        CalendarDestination.Route,
        R.string.navigation_calendar,
        Icons.Rounded.CalendarMonth,
        inBottomBar = true,
        navigationRoute = CalendarDestination.BaseRoute,
    ),
    Review("review", R.string.navigation_review, Icons.AutoMirrored.Rounded.FactCheck, inBottomBar = true),
    Settings("settings", R.string.navigation_settings, Icons.Rounded.Settings, inBottomBar = false),
    ;

    companion object {
        val bottomBar: List<OrbitDestination> get() = entries.filter { it.inBottomBar }
    }
}

object FirstTimeTutorialDestination {
    const val ReplayArgument = "replay"
    const val Route = "tutorial?$ReplayArgument={$ReplayArgument}"

    fun route(isReplay: Boolean): String = "tutorial?$ReplayArgument=$isReplay"
}

internal fun initialOrbitRoute(settings: AppSettings): String =
    if (settings.hasCompletedFirstTimeTutorial) {
        OrbitDestination.Home.route
    } else {
        FirstTimeTutorialDestination.route(isReplay = false)
    }

object ReminderDestination {
    const val ReminderIdArgument = "reminderId"
    const val Route = "reminder/{$ReminderIdArgument}"

    fun route(reminderId: Long): String = "reminder/$reminderId"
}

enum class ItemDetailType(val routeName: String) {
    Note("note"),
    Task("task"),
    Reminder("reminder"),
    Capture("capture"),
}

object ItemDetailDestination {
    const val TypeArgument = "type"
    const val ItemIdArgument = "itemId"
    const val Route = "item/{$TypeArgument}/{$ItemIdArgument}"

    fun route(type: ItemDetailType, itemId: Long): String = when (type) {
        ItemDetailType.Reminder -> ReminderDestination.route(itemId)
        else -> "item/${type.routeName}/$itemId"
    }
}

object SearchDestination {
    const val Route = "search"
}

object SpaceDetailDestination {
    const val SpaceIdArgument = "spaceId"
    const val Route = "spaces/{$SpaceIdArgument}"
    const val UnfiledRoute = "spaces/unfiled"

    fun route(spaceId: Long): String = "spaces/$spaceId"
}

object CalendarCaptureContext {
    const val EpochDayKey = "calendarCaptureEpochDay"

    fun date(epochDay: Long?): LocalDate? = epochDay?.let {
        runCatching { LocalDate.ofEpochDay(it) }.getOrNull()
    }
}

object SharedTextContext {
    const val TextKey = "sharedTextForHome"

    /** Set by the "New thought" shortcut and tile: focus the capture box once. */
    const val FocusCaptureKey = "focusCaptureRequest"
}

object BrainDumpResumeContext {
    const val CaptureIdKey = "brainDumpResumeCaptureId"
}

data class CalendarNavigationRequest(
    val route: String,
    val launchSingleTop: Boolean,
)

object CalendarDestination {
    const val BaseRoute = "calendar"
    const val DateArgument = "date"
    const val Route = "$BaseRoute?$DateArgument={$DateArgument}"

    fun route(date: LocalDate? = null): String = date?.let {
        "$BaseRoute?$DateArgument=${it.toEpochDay()}"
    } ?: BaseRoute

    fun navigationRequest(date: LocalDate? = null) = CalendarNavigationRequest(
        route = route(date),
        launchSingleTop = true,
    )

    fun initialDate(rawEpochDay: String?, fallback: LocalDate): LocalDate =
        rawEpochDay
            ?.toLongOrNull()
            ?.let { epochDay -> runCatching { LocalDate.ofEpochDay(epochDay) }.getOrNull() }
            ?: fallback
}

fun NavController.navigateToCalendar(date: LocalDate? = null) {
    val request = CalendarDestination.navigationRequest(date)
    navigate(request.route) {
        // Calendar is a top-level tab: opening it for a date replaces any earlier
        // Calendar entry instead of stacking another screen above Home.
        popUpTo(OrbitDestination.Home.route)
        launchSingleTop = request.launchSingleTop
    }
}

fun NavController.returnHomeWithCalendarCaptureDate(date: LocalDate): Boolean {
    getBackStackEntry(OrbitDestination.Home.route)
        .savedStateHandle[CalendarCaptureContext.EpochDayKey] = date.toEpochDay()
    return popBackStack(OrbitDestination.Home.route, inclusive = false)
}

fun NavController.returnHomeToResumeBrainDump(captureId: Long): Boolean {
    require(captureId > 0L)
    getBackStackEntry(OrbitDestination.Home.route)
        .savedStateHandle[BrainDumpResumeContext.CaptureIdKey] = captureId
    return popBackStack(OrbitDestination.Home.route, inclusive = false)
}

fun CalendarEntryId.toItemDetailRoute(): String = ItemDetailDestination.route(
    type = when (sourceType) {
        CalendarItemType.Note -> ItemDetailType.Note
        CalendarItemType.Task -> ItemDetailType.Task
        CalendarItemType.Reminder -> ItemDetailType.Reminder
    },
    itemId = sourceItemId,
)

fun ReviewItem.toItemDetailRoute(): String = ItemDetailDestination.route(
    type = when (type) {
        ReviewItemType.Task -> ItemDetailType.Task
        ReviewItemType.Capture -> ItemDetailType.Capture
        ReviewItemType.Reminder -> ItemDetailType.Reminder
    },
    itemId = id,
)

fun String.toItemDetailTypeOrNull(): ItemDetailType? =
    ItemDetailType.entries.firstOrNull { it.routeName == this }
