package com.orbit.app.reminders

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** The battery part of the reminder check (data only; the Settings row reads it). */
@RunWith(AndroidJUnit4::class)
class ReminderBatteryCheckTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aRestrictedBatteryIsAHintOnlyOnBrandsKnownToStopApps() {
        assertEquals(ReminderCapabilityState.MayBeStopped, status(batteryRestricted = true, aggressive = true).state)
        // Elsewhere exact alarms fire through Doze, so plain battery optimisation is fine.
        assertEquals(ReminderCapabilityState.Ready, status(batteryRestricted = true, aggressive = false).state)
        assertEquals(ReminderCapabilityState.Ready, status(batteryRestricted = false, aggressive = true).state)
    }

    @Test
    fun blockedAndLateStillComeBeforeTheBatteryHint() {
        assertEquals(
            ReminderCapabilityState.Blocked,
            status(batteryRestricted = true, aggressive = true, notifications = false).state,
        )
        assertEquals(
            ReminderCapabilityState.MayBeLate,
            status(batteryRestricted = true, aggressive = true, exact = false).state,
        )
    }

    @Test
    fun knownBrandsMatchWhateverTheCase() {
        listOf("samsung", "Xiaomi", "HUAWEI", "OnePlus", "OPPO", "vivo", "realme").forEach {
            assertTrue(it, ReminderCapabilities.isAggressiveBatteryBrand(it))
        }
        listOf("Google", "motorola", "", null).forEach {
            assertFalse("$it", ReminderCapabilities.isAggressiveBatteryBrand(it))
        }
    }

    @Test
    fun batteryRestrictionIsReadFromAndroid() {
        val power = shadowOf(context.getSystemService(PowerManager::class.java))
        power.setIgnoringBatteryOptimizations(context.packageName, false)
        assertTrue(ReminderCapabilities.batteryRestricted(context))
        assertTrue(ReminderCapabilities.status(context).batteryRestricted)

        power.setIgnoringBatteryOptimizations(context.packageName, true)
        assertFalse(ReminderCapabilities.batteryRestricted(context))
    }

    @Test
    fun theBatteryRequestNamesThisApp() {
        val intent = ReminderCapabilities.batteryOptimizationIntent(context)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun samsungGetsItsOwnScreenOnlyWhenThePhoneCanOpenIt() {
        // Not resolvable: fall back to the app's own battery request.
        assertEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ReminderCapabilities.batteryFixIntent(context, manufacturer = "samsung").action,
        )

        val samsung = ReminderCapabilities.samsungNeverSleepingIntent()
        assertEquals("com.samsung.android.lool", samsung.`package`)
        assertEquals(2, samsung.getIntExtra("activity_type", -1))
        shadowOf(context.packageManager).addResolveInfoForIntent(
            samsung,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = "com.samsung.android.lool"
                    name = "com.samsung.android.sm.ui.CheckableListActivity"
                }
            },
        )

        assertEquals(samsung.action, ReminderCapabilities.batteryFixIntent(context, manufacturer = "samsung").action)
        // Other brands never get the Samsung screen.
        assertEquals(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            ReminderCapabilities.batteryFixIntent(context, manufacturer = "Xiaomi").action,
        )
    }

    private fun status(
        batteryRestricted: Boolean,
        aggressive: Boolean,
        notifications: Boolean = true,
        exact: Boolean = true,
    ) = ReminderCapabilityStatus(
        notificationsAllowed = notifications,
        reminderChannelEnabled = true,
        exactAlarmsAllowed = exact,
        exactAlarmsAdjustable = true,
        lastBlockedDeliveryAt = null,
        batteryRestricted = batteryRestricted,
        aggressiveBatteryBrand = aggressive,
    )
}
