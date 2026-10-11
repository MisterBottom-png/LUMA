package com.orbit.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChosenUserNameTest {
    @Test
    fun theStoredPlaceholderIsNotAName() {
        assertNull(AppSettings().chosenName())
        assertNull(chosenUserName("  User "))
        assertNull(chosenUserName(""))
        assertEquals("Test reader", chosenUserName(" Test reader "))
    }
}
