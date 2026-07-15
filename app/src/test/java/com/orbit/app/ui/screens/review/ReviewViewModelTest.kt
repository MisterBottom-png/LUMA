package com.orbit.app.ui.screens.review

import com.orbit.app.data.local.entity.CaptureEntity
import com.orbit.app.data.local.entity.CaptureStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewViewModelTest {
    @Test
    fun onlyInboxCapturesRemainUnresolvedReviewItems() {
        assertTrue(isUnresolvedReviewCapture(CaptureEntity(rawText = "Needs a decision")))
        assertFalse(
            isUnresolvedReviewCapture(
                CaptureEntity(rawText = "Finalized source", status = CaptureStatus.Processed),
            ),
        )
        assertFalse(
            isUnresolvedReviewCapture(
                CaptureEntity(rawText = "Archived source", status = CaptureStatus.Archived),
            ),
        )
    }

    @Test
    fun unresolvedCaptureExplainsWhyReviewIsRequired() {
        val reason = CaptureEntity(rawText = "Needs context").reviewReason()

        assertTrue(reason.contains("not been finalized"))
        assertTrue(reason.contains("dismissed"))
    }

    @Test
    fun reviewProgressUsesReviewDecisionLanguage() {
        assertEquals("Review clear", reviewProgressLabel(0))
        assertEquals("1 item still needs a review decision", reviewProgressLabel(1))
        assertEquals("3 items still need a review decision", reviewProgressLabel(3))
    }
}
