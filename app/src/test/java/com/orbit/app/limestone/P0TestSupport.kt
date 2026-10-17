package com.orbit.app.limestone

import android.os.Looper
import org.junit.Assert.fail
import org.robolectric.Shadows.shadowOf

/**
 * Runs the main looper until [condition] holds, failing after [timeoutMillis]. View
 * models resume on the main thread after Room work, and the test runs on that thread.
 */
internal fun idleMainUntil(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (true) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        if (System.currentTimeMillis() > deadline) fail("Timed out waiting")
        Thread.sleep(20)
    }
}

/** Keeps the main looper running for [millis], so any pending main-thread work can land. */
internal fun idleMainFor(millis: Long) {
    val deadline = System.currentTimeMillis() + millis
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(20)
    }
    shadowOf(Looper.getMainLooper()).idle()
}
