package com.orbit.app.ui.screens.home

import com.orbit.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeCaptureProcessingStateTest {
    @Test
    fun savingAndAnalyzingHaveDistinctVisibleStatusesAndBothBlockAnotherSubmission() {
        assertEquals(R.string.core_home_saving_capture, CaptureProcessingState.Saving.statusLabelRes())
        assertEquals(R.string.core_home_analyzing_capture, CaptureProcessingState.Analyzing.statusLabelRes())
        assertEquals(null, CaptureProcessingState.Idle.statusLabelRes())
        assertTrue(CaptureProcessingState.Saving.isInProgress)
        assertTrue(CaptureProcessingState.Analyzing.isInProgress)
        assertFalse(CaptureProcessingState.Idle.isInProgress)
    }

    @Test
    fun savingClearsEarlierFeedbackAndNeverWaitsForAnalysis() {
        val saving = HomeCaptureUiState(
            message = HomeMessage.KeptForLater,
            quickReminder = QuickReminderQuestion(captureId = 1, title = "x", reminderAt = null, phrase = null),
        ).beginCaptureSaving()

        assertEquals(CaptureProcessingState.Saving, saving.processingState)
        assertEquals(null, saving.message)
        assertEquals(null, saving.quickReminder)
    }
}
