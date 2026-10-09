package com.orbit.app.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.orbit.app.macrobenchmark.AppNavigation.openSettings
import com.orbit.app.macrobenchmark.AppNavigation.openTab
import com.orbit.app.macrobenchmark.AppNavigation.skipFirstTimeGuideIfShown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class GlassSurfaceMacrobenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = measureStartup(StartupMode.COLD)

    @Test
    fun warmStartup() = measureStartup(StartupMode.WARM)

    @Test
    fun scrollSpaces() = measureRoute("Spaces") { scrollContent() }

    @Test
    fun scrollReview() = measureRoute("Review") { scrollContent() }

    @Test
    fun scrollSettings() = measureSettings { scrollContent() }

    @Test
    fun navigateHomeToCalendar() = measureHome {
        openTab("Calendar")
    }

    @Test
    fun navigateHomeToSpaces() = measureHome {
        openTab("Spaces")
    }

    @Test
    fun renderAppearanceGlassPreview() = measureSettings {
        device.findObject(By.text("Appearance")).click()
        device.wait(Until.hasObject(By.text("Transparency")), UiTimeoutMillis)
    }

    private fun measureRoute(routeLabel: String, measuredBlock: MacrobenchmarkScope.() -> Unit) {
        measure(
            setupBlock = {
                startActivityAndWait()
                skipFirstTimeGuideIfShown()
                openTab(routeLabel)
            },
            measuredBlock = measuredBlock,
        )
    }

    private fun measureSettings(measuredBlock: MacrobenchmarkScope.() -> Unit) {
        measure(
            setupBlock = {
                startActivityAndWait()
                skipFirstTimeGuideIfShown()
                openSettings()
            },
            measuredBlock = measuredBlock,
        )
    }

    private fun measureStartup(startupMode: StartupMode) {
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = startupMode,
            iterations = 5,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    private fun measureHome(measuredBlock: MacrobenchmarkScope.() -> Unit) {
        measure(
            setupBlock = {
                startActivityAndWait()
                skipFirstTimeGuideIfShown()
                openTab("Home")
            },
            measuredBlock = measuredBlock,
        )
    }

    private fun measure(
        setupBlock: MacrobenchmarkScope.() -> Unit,
        measuredBlock: MacrobenchmarkScope.() -> Unit,
    ) {
        benchmarkRule.measureRepeated(
            packageName = TargetPackage,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = setupBlock,
            measureBlock = measuredBlock,
        )
    }

    private fun MacrobenchmarkScope.scrollContent() {
        val scrollable = requireNotNull(device.findObject(By.scrollable(true))) {
            "The measured route did not expose a scrollable surface"
        }
        scrollable.fling(Direction.DOWN)
        device.waitForIdle()
        scrollable.fling(Direction.UP)
        device.waitForIdle()
    }

    private companion object {
        const val TargetPackage = "com.tallele.app"
        const val UiTimeoutMillis = AppNavigation.UiTimeoutMillis
    }
}
