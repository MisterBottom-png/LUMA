package com.orbit.app.reminders

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.orbit.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/** B6: turning notifications on or off while LUMA is away reconciles reminders on return. */
@RunWith(AndroidJUnit4::class)
class ReconcileOnResumeTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    // Every reconcile run ever queued; the test WorkManager may already have finished one.
    private fun runs(): Set<java.util.UUID> = WorkManager.getInstance(application)
        .getWorkInfosForUniqueWork("luma_reschedule_reminders").get()
        .map { it.id }
        .toSet()

    @Test
    fun aChangedPermissionReconcilesOnResume_anUnchangedOneDoesNot() {
        WorkManagerTestInitHelper.initializeTestWorkManager(application)
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val atStart = runs()

        controller.pause().stop().start().resume()
        assertEquals(atStart, runs())

        controller.pause().stop()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        controller.start().resume()
        assertTrue((runs() - atStart).isNotEmpty())
        controller.pause().stop().destroy()
    }
}
