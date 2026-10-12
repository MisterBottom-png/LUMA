package com.orbit.app.ui.time

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The current time, now and at every minute boundary (review F12, B9, N005): screens
 * that show "today", quick times or "Looking at this…" follow the real clock across
 * midnight and while the app is open, not only when their data changes. Collected
 * while the screen is visible, so a return to the app starts with a fresh time.
 */
object MinuteClock {
    fun ticks(now: () -> Long = System::currentTimeMillis): Flow<Long> = flow {
        while (true) {
            val time = now()
            emit(time)
            delay(MinuteMillis - Math.floorMod(time, MinuteMillis))
        }
    }

    private const val MinuteMillis = 60_000L
}
