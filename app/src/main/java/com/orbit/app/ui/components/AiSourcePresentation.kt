package com.orbit.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.orbit.app.R
import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.ui.navigation.ItemDetailType

@Composable
internal fun AiSourceItem.userVisibleLabel(): String = userVisibleLabel(
    fallback = stringResource(
        when (type) {
            ItemDetailType.Note -> R.string.core_untitled_note
            ItemDetailType.Task -> R.string.core_untitled_task
            ItemDetailType.Reminder -> R.string.core_untitled_reminder
            ItemDetailType.Capture -> R.string.core_capture
        },
    ),
)

internal fun AiSourceItem.userVisibleLabel(fallback: String): String =
    title.trim().ifBlank { fallback }
