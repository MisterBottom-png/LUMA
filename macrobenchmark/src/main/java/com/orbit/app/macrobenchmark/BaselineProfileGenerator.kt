package com.orbit.app.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.orbit.app.macrobenchmark.AppNavigation.openSettings
import com.orbit.app.macrobenchmark.AppNavigation.openTab
import com.orbit.app.macrobenchmark.AppNavigation.skipFirstTimeGuideIfShown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateCriticalUserJourneyProfile() = baselineProfileRule.collect(
        packageName = TargetPackage,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        skipFirstTimeGuideIfShown()
        listOf("Spaces", "Calendar", "Review", "Home").forEach { tab -> openTab(tab) }
        openSettings()
        device.pressBack()
        device.waitForIdle()

        val captureField = device.wait(
            Until.findObject(By.clazz("android.widget.EditText")),
            UiTimeoutMillis,
        )
        if (captureField != null) {
            captureField.click()
            captureField.text = "Profile the local capture flow"
            device.waitForIdle()
            captureField.clear()
        }
    }

    private companion object {
        const val TargetPackage = "com.tallele.app"
        const val UiTimeoutMillis = AppNavigation.UiTimeoutMillis
    }
}
