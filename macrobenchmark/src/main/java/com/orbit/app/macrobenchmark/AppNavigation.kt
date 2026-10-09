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
            device.wait(Until.hasObject(By.desc("Open settings")), UiTimeoutMillis)
            device.waitForIdle()
        }
    }

    /**
     * Home focuses the capture box by default, and the tab bar hides while the
     * keyboard is open. Back closes only the keyboard; it is pressed only when
     * the keyboard is really showing, so it never leaves the app.
     */
    fun MacrobenchmarkScope.hideKeyboardIfShown() {
        val inputState = device.executeShellCommand("dumpsys input_method")
        if (inputState.contains("mInputShown=true") || inputState.contains("isInputViewShown=true")) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * Tabs show their name as visible text. A screen title can carry the same word
     * ("Spaces"), so the match lowest on screen — the bottom bar — is used.
     */
    fun MacrobenchmarkScope.openTab(label: String) {
        hideKeyboardIfShown()
        val tab = requireNotNull(
            device.wait(Until.findObjects(By.text(label)), UiTimeoutMillis)
                ?.maxByOrNull { it.visibleBounds.bottom },
        ) { "Navigation destination '$label' was not available" }
        tab.click()
        device.waitForIdle()
    }

    /** Settings lives behind the button at the top right of Home. */
    fun MacrobenchmarkScope.openSettings() {
        val button = device.wait(Until.findObject(By.desc("Open settings")), 1_000L) ?: run {
            openTab("Home")
            device.wait(Until.findObject(By.desc("Open settings")), UiTimeoutMillis)
        }
        requireNotNull(button) { "The Settings button on Home was not available" }.click()
        // Settings rows read as one label ("Appearance, Theme, …"), so match the start.
        requireNotNull(device.wait(Until.findObject(By.textStartsWith("Appearance")), UiTimeoutMillis)) {
            "Settings did not open"
        }
        device.waitForIdle()
    }
}
