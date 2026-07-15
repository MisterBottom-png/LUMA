package com.orbit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsTimeFormatModeTest {
    @Test
    fun deviceModeFollowsResolvedDeviceClock() {
        assertEquals(true, SettingsTimeFormatMode.Device.uses24HourClock(true))
        assertEquals(false, SettingsTimeFormatMode.Device.uses24HourClock(false))
    }

    @Test
    fun explicitModesIgnoreDeviceClock() {
        assertEquals(false, SettingsTimeFormatMode.TwelveHour.uses24HourClock(true))
        assertEquals(false, SettingsTimeFormatMode.TwelveHour.uses24HourClock(false))
        assertEquals(true, SettingsTimeFormatMode.TwentyFourHour.uses24HourClock(true))
        assertEquals(true, SettingsTimeFormatMode.TwentyFourHour.uses24HourClock(false))
    }
}
