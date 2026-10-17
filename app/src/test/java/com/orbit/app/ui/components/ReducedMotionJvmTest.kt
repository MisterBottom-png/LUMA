package com.orbit.app.ui.components

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.testing.JvmComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** "Remove animations" is re-read every time the app comes to the front, and battery saver is live. */
@RunWith(AndroidJUnit4::class)
class ReducedMotionJvmTest {
    @get:Rule
    val composeRule = JvmComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun restoreAnimatorScale() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }

    private class TestLifecycleHost : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private fun move(host: TestLifecycleHost, vararg events: Lifecycle.Event) {
        composeRule.runOnUiThread { events.forEach { host.registry.handleLifecycleEvent(it) } }
        composeRule.waitForIdle()
    }

    @Test
    fun reducedMotionIsReadAgainOnResume_notOnEveryRecomposition() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val host = TestLifecycleHost()
        var seen = false
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides host) {
                seen = rememberReducedMotion()
            }
        }
        move(host, Lifecycle.Event.ON_CREATE, Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME)
        assertFalse(seen)

        // Animations get turned off in system settings while the app is in the background.
        move(host, Lifecycle.Event.ON_PAUSE)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        assertFalse("not re-read until the app comes back", seen)

        move(host, Lifecycle.Event.ON_RESUME)
        assertTrue("re-read on resume", seen)

        // And back on.
        move(host, Lifecycle.Event.ON_PAUSE)
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        move(host, Lifecycle.Event.ON_RESUME)
        assertFalse(seen)
    }

    @Test
    fun theOverrideStillWinsOverTheSetting() {
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        var seen = false
        composeRule.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides false) {
                seen = rememberReducedMotion()
            }
        }
        composeRule.waitForIdle()
        assertFalse(seen)
    }

    @Test
    fun batterySaverFollowsTheSystemBroadcastLive() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIsPowerSaveMode(false)
        var seen = true
        composeRule.setContent { seen = rememberBatterySaver() }
        composeRule.waitForIdle()
        assertFalse(seen)

        shadowOf(powerManager).setIsPowerSaveMode(true)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        composeRule.waitForIdle()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertTrue("battery saver on", seen)

        shadowOf(powerManager).setIsPowerSaveMode(false)
        context.sendBroadcast(Intent(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        shadowOf(android.os.Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertFalse("battery saver off", seen)
    }

    @Test
    fun theBatterySaverOverrideNeedsNoReceiver() {
        var seen = false
        composeRule.setContent {
            CompositionLocalProvider(LocalBatterySaverOverride provides true) { seen = rememberBatterySaver() }
        }
        composeRule.waitForIdle()
        assertEquals(true, seen)
    }
}
