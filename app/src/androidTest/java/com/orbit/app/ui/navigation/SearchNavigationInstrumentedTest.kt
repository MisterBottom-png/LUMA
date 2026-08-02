package com.orbit.app.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchNavigationInstrumentedTest {
    @Test
    fun poppingSearchReturnsToTheScreenThatOpenedIt() = onMainThread {
        val controller = searchNavController()
        controller.navigate(OrbitDestination.Spaces.route)
        controller.navigate(SearchDestination.Route)

        assertEquals(SearchDestination.Route, controller.currentDestination?.route)
        assertTrue(controller.popBackStack())
        assertEquals(OrbitDestination.Spaces.route, controller.currentDestination?.route)
    }

    private fun searchNavController(): NavHostController {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return NavHostController(context).apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            navigatorProvider.addNavigator(DialogNavigator())
            graph = createGraph(startDestination = OrbitDestination.Home.route) {
                composable(OrbitDestination.Home.route) {}
                composable(OrbitDestination.Spaces.route) {}
                composable(SearchDestination.Route) {}
            }
        }
    }

    private fun onMainThread(block: () -> Unit) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    }
}
