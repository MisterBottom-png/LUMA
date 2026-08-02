package com.orbit.app.ui.navigation

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class PortraitLayoutPolicyTest {
    @Test
    fun compactPortraitUsesAvailableWidth() {
        assertEquals(400.dp, portraitContentMaxWidth(400.dp))
        assertEquals(599.dp, portraitContentMaxWidth(599.dp))
    }

    @Test
    fun mediumPortraitStopsStretchingAtReadableWidth() {
        assertEquals(600.dp, portraitContentMaxWidth(600.dp))
        assertEquals(720.dp, portraitContentMaxWidth(720.dp))
        assertEquals(720.dp, portraitContentMaxWidth(839.dp))
    }

    @Test
    fun expandedPortraitUsesBoundedTabletCanvas() {
        assertEquals(840.dp, portraitContentMaxWidth(840.dp))
        assertEquals(840.dp, portraitContentMaxWidth(1_200.dp))
    }
}
