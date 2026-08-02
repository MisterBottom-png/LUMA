package com.orbit.app.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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

        listOf("Spaces", "Review", "Settings", "Home").forEach { destination ->
            val navigationItem = requireNotNull(
                device.wait(Until.findObject(By.desc(destination)), UiTimeoutMillis),
            ) { "Navigation destination '$destination' was not available" }
            navigationItem.click()
            device.waitForIdle()
        }

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
        const val TargetPackage = "com.orbit.app"
        const val UiTimeoutMillis = 5_000L
    }
}
