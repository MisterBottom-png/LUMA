package com.orbit.app.ui.components

import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.ui.navigation.ItemDetailType

internal fun AiSourceItem.userVisibleLabel(): String = title.trim().ifBlank {
    when (type) {
        ItemDetailType.Note -> "Untitled note"
        ItemDetailType.Task -> "Untitled task"
        ItemDetailType.Reminder -> "Untitled reminder"
        ItemDetailType.Capture -> "Capture"
    }
}
