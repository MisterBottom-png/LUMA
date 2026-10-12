package com.orbit.app.ui.screens.spaces

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import com.orbit.app.OrbitContainer
import com.orbit.app.R
import com.orbit.app.data.local.entity.CaptureOutcome
import com.orbit.app.ui.localization.localizedSpaceName
import com.orbit.app.ui.screens.home.CaptureReceipt
import com.orbit.app.ui.screens.home.CaptureReceiptReader
import com.orbit.app.ui.screens.home.ReceiptTarget
import com.orbit.app.ui.screens.home.line
import com.orbit.app.ui.screens.home.openTarget
import com.orbit.app.ui.screens.home.receiptText
import com.orbit.app.ui.time.OrbitTimeFormat
import kotlinx.coroutines.flow.first

/**
 * After the Space "+" sheet closes: one snackbar saying what the thought became, read
 * from the stored thought (the same words as Home's receipt), with Change when an item
 * was made. "Decide later" says where the thought waits.
 */
@Composable
internal fun SpaceCaptureFinishedMessage(
    finished: SpaceCaptureFinished,
    container: OrbitContainer,
    timeFormat: OrbitTimeFormat,
    host: SnackbarHostState,
    onOpen: (ReceiptTarget) -> Unit,
    onShown: () -> Unit,
) {
    val receipt by produceState<CaptureReceipt?>(initialValue = null, finished) {
        value = CaptureReceiptReader(
            captureRepository = container.captureRepository,
            noteRepository = container.noteRepository,
            taskRepository = container.taskRepository,
            reminderRepository = container.reminderRepository,
            spaceRepository = container.spaceRepository,
        ).observe(finished.captureId).first()
    }
    val spaceName by produceState(initialValue = "", finished.spaceId) {
        value = container.spaceRepository.getById(finished.spaceId)?.name.orEmpty()
    }
    val current = receipt
    val message = when {
        finished.keptForLater -> stringResource(R.string.space_capture_kept_later, localizedSpaceName(spaceName))
        current == null -> null
        current.outcome == CaptureOutcome.Saved || current.outcome == CaptureOutcome.NeedsYou && current.itemId == null ->
            stringResource(R.string.space_capture_saved, localizedSpaceName(spaceName))
        else -> receiptText(current.line(), timeFormat)
    }
    val changeLabel = stringResource(R.string.receipt_action_change)
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        val target = current?.takeIf { it.itemId != null }?.openTarget()
        val result = host.showSnackbar(
            message = message,
            actionLabel = target?.let { changeLabel },
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed && target != null) onOpen(target)
        onShown()
    }
}
