package com.orbit.app.ui.screens.item

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemDetailBackNavigationTest {
    @Test
    fun backCancelsEditingBeforeNavigatingUp() {
        assertEquals(ItemDetailBackAction.CancelEditing, itemDetailBackAction(isEditing = true))
        assertEquals(ItemDetailBackAction.NavigateUp, itemDetailBackAction(isEditing = false))
    }
}
