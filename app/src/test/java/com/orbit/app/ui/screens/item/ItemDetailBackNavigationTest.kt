package com.orbit.app.ui.screens.item

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemDetailBackNavigationTest {
    @Test
    fun backCancelsEditingBeforeNavigatingUp() {
        assertEquals(ItemDetailBackAction.CancelEditing, itemDetailBackAction(isEditing = true))
        assertEquals(ItemDetailBackAction.NavigateUp, itemDetailBackAction(isEditing = false))
    }

    @Test
    fun backWithChangedTextAsksBeforeDiscarding() {
        assertEquals(ItemDetailBackAction.ConfirmDiscard, itemDetailBackAction(isEditing = true, hasChanges = true))
        assertEquals(ItemDetailBackAction.CancelEditing, itemDetailBackAction(isEditing = true, hasChanges = false))
        assertEquals(ItemDetailBackAction.NavigateUp, itemDetailBackAction(isEditing = false, hasChanges = true))
    }
}
