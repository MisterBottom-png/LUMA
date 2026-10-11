package com.orbit.app.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import com.orbit.app.macrobenchmark.AppNavigation.hideKeyboardIfShown
import com.orbit.app.macrobenchmark.AppNavigation.skipFirstTimeGuideIfShown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing on Home with the live glass capture box and bottom bar on screen.
 *
 * Pass: P90 of `frameDurationCpuMs` below 16 ms on a real phone (Android 13+, Glass set to
 * Clear glass, default background). Home scrolls only when the screen is shorter than its content,
 * so on a tall phone the swipes measure the cost of redrawing the glass rather than a scroll.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class HomeScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollHome() = benchmarkRule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            skipFirstTimeGuideIfShown()
            hideKeyboardIfShown()
        },
    ) {
        scrollHome()
    }

    private fun MacrobenchmarkScope.scrollHome() {
        val scrollable = device.findObject(By.scrollable(true))
        if (scrollable != null) {
            scrollable.fling(Direction.DOWN)
            device.waitForIdle()
            scrollable.fling(Direction.UP)
        } else {
            val x = device.displayWidth / 2
            val top = device.displayHeight / 4
            val bottom = device.displayHeight * 3 / 5
            device.swipe(x, bottom, x, top, SwipeSteps)
            device.waitForIdle()
            device.swipe(x, top, x, bottom, SwipeSteps)
        }
        device.waitForIdle()
    }

    private companion object {
        const val TargetPackage = "com.tallele.app"
        const val SwipeSteps = 20
    }
}
