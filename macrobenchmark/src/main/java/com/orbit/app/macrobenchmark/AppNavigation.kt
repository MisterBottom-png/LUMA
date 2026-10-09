package com.orbit.app.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** How benchmarks move around Tallele: labelled tabs, and Settings from Home. */
internal object AppNavigation {
    const val UiTimeoutMillis = 5_000L

    /** A fresh install opens the first-time guide; skip it so journeys start on Home. */
    fun MacrobenchmarkScope.skipFirstTimeGuideIfShown() {
        device.wait(Until.findObject(By.text("Skip")), 2_000L)?.let { skip ->
            skip.click()
            device.wait(Until.hasObject(By.text("Home")), UiTimeoutMillis)
            device.waitForIdle()
        }
    }

    /**
     * Tabs show their name as visible text. A screen title can carry the same word
     * ("Spaces"), so the match lowest on screen — the bottom bar — is used.
     */
    fun MacrobenchmarkScope.openTab(label: String) {
        val tab = requireNotNull(
            device.wait(Until.findObjects(By.text(label)), UiTimeoutMillis)
                ?.maxByOrNull { it.visibleBounds.bottom },
        ) { "Navigation destination '$label' was not available" }
        tab.click()
        device.waitForIdle()
    }

    fun MacrobenchmarkScope.openSettings() {
        openTab("Home")
        requireNotNull(device.wait(Until.findObject(By.desc("Open settings")), UiTimeoutMillis)) {
            "The Settings button on Home was not available"
        }.click()
        device.wait(Until.hasObject(By.text("Appearance")), UiTimeoutMillis)
        device.waitForIdle()
    }
}
