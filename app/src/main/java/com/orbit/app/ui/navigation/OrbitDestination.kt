package com.orbit.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.annotation.StringRes
import androidx.navigation.NavController
import com.orbit.app.R
import com.orbit.app.domain.calendar.CalendarEntryId
import com.orbit.app.domain.calendar.CalendarItemType
import com.orbit.app.ui.screens.review.ReviewItem
import com.orbit.app.ui.screens.review.ReviewItemType
import java.time.LocalDate

enum class OrbitDestination(
    val route: String,
    @param:StringRes val contentDescriptionRes: Int,
    val icon: ImageVector,
) {
    Home("home", R.string.navigation_home, Icons.Rounded.Home),
    Spaces("spaces", R.string.navigation_spaces, Icons.Rounded.GridView),
    Review("review", R.string.navigation_review, Icons.AutoMirrored.Rounded.FactCheck),
    Settings("settings", R.string.navigation_settings, Icons.Rounded.Settings),
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

object CalendarCaptureContext {
    const val EpochDayKey = "calendarCaptureEpochDay"

    fun date(epochDay: Long?): LocalDate? = epochDay?.let {
        runCatching { LocalDate.ofEpochDay(it) }.getOrNull()
    }
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
