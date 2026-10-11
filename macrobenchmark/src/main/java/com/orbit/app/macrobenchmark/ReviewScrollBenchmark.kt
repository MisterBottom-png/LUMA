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
import androidx.test.uiautomator.Until
import com.orbit.app.macrobenchmark.AppNavigation.hideKeyboardIfShown
import com.orbit.app.macrobenchmark.AppNavigation.skipFirstTimeGuideIfShown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while the review tab scrolls under the live glass bottom bar.
 *
 * Pass: P90 of `frameDurationCpuMs` below 16 ms on a real phone (Android 13+, Glass set to
 * Clear glass, default background, a populated database). If it fails, set
 * `inputScale = HazeInputScale.Fixed(0.5f)` for the NavigationBar style only, then re-run.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ReviewScrollBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollReview() = benchmarkRule.measureRepeated(
        packageName = TargetPackage,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            startActivityAndWait()
            skipFirstTimeGuideIfShown()
            openReviewTab()
        },
    ) {
        val scrollable = requireNotNull(device.findObject(By.scrollable(true))) {
            "The review tab did not expose a scrollable surface"
        }
        scrollable.fling(Direction.DOWN)
        device.waitForIdle()
        scrollable.fling(Direction.UP)
        device.waitForIdle()
    }

    /** The fourth tab: labelled "Needs you", or "Review" on older builds. */
    private fun MacrobenchmarkScope.openReviewTab() {
        hideKeyboardIfShown()
        val tab = ReviewTabLabels.firstNotNullOfOrNull { label ->
            device.wait(Until.findObjects(By.text(label)), LabelWaitMillis)
                ?.takeIf { it.isNotEmpty() }
                ?.maxByOrNull { it.visibleBounds.bottom }
        }
        requireNotNull(tab) { "The review tab was not available" }.click()
        device.waitForIdle()
    }

    private companion object {
        const val TargetPackage = "com.tallele.app"
        const val LabelWaitMillis = 2_000L
        val ReviewTabLabels = listOf("Needs you", "Review")
    }
}
