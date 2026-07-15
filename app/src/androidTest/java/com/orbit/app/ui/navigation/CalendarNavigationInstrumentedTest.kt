package com.orbit.app.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarNavigationInstrumentedTest {
    @Test
    fun dateRoute_andBackStack_preserveArgumentAndReturnToPreviousDestination() = onMainThread {
        val controller = calendarNavController()
        val date = LocalDate.of(2026, 8, 3)

        controller.navigateToCalendar(date)

        assertEquals(CalendarDestination.Route, controller.currentDestination?.route)
        assertEquals(
            date.toEpochDay().toString(),
            controller.currentBackStackEntry?.arguments?.getString(CalendarDestination.DateArgument),
        )
        assertTrue(controller.popBackStack())
        assertEquals(OrbitDestination.Home.route, controller.currentDestination?.route)
    }

    @Test
    fun repeatedNavigation_doesNotAddDuplicateTopDestination() = onMainThread {
        val controller = calendarNavController()
        val date = LocalDate.of(2026, 8, 3)

        controller.navigateToCalendar(date)
        controller.navigateToCalendar(date)

        assertTrue(controller.popBackStack())
        assertEquals(OrbitDestination.Home.route, controller.currentDestination?.route)
    }

    @Test
    fun addForDay_returnsHomeWithSelectedDateContext() = onMainThread {
        val controller = calendarNavController()
        val date = LocalDate.of(2026, 9, 12)
        controller.navigateToCalendar(date)

        assertTrue(controller.returnHomeWithCalendarCaptureDate(date))

        assertEquals(OrbitDestination.Home.route, controller.currentDestination?.route)
        assertEquals(
            date.toEpochDay(),
            controller.currentBackStackEntry?.savedStateHandle
                ?.get<Long>(CalendarCaptureContext.EpochDayKey),
        )
    }

    @Test
    fun returningFromItemDetailKeepsCalendarDateOnBackStack() = onMainThread {
        val controller = calendarNavController()
        val date = LocalDate.of(2026, 10, 4)
        controller.navigateToCalendar(date)
        controller.navigate(ItemDetailRoute)

        assertTrue(controller.popBackStack())

        assertEquals(CalendarDestination.Route, controller.currentDestination?.route)
        assertEquals(
            date.toEpochDay().toString(),
            controller.currentBackStackEntry?.arguments?.getString(CalendarDestination.DateArgument),
        )
    }

    private fun calendarNavController(): NavHostController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
            graph = createGraph(startDestination = OrbitDestination.Home.route) {
                composable(OrbitDestination.Home.route) {}
                composable(
                    route = CalendarDestination.Route,
                    arguments = listOf(
                        navArgument(CalendarDestination.DateArgument) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {}
                composable(ItemDetailRoute) {}
            }
        }
    }

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }

    private companion object {
        const val ItemDetailRoute = "calendar-test-item-detail"
    }
}
