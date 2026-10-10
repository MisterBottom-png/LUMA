package com.orbit.app.capture

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedTextTest {
    private fun send(type: String?, text: String? = null, subject: String? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type?.let(::setType)
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    @Test
    fun plainTextShareBecomesDraftText() {
        assertEquals("Buy stamps", SharedText.from(send("text/plain", text = "  Buy stamps \n")))
    }

    @Test
    fun subjectAndBodyAreJoinedWithoutRepeatingTheSubject() {
        assertEquals("Article\nhttps://example.org", SharedText.from(send("text/plain", "https://example.org", "Article")))
        assertEquals("Article: more", SharedText.combine(subject = "Article", text = "Article: more"))
    }

    @Test
    fun nonTextOtherActionsAndEmptySharesAreIgnored() {
        assertNull(SharedText.from(send("image/png", text = "x")))
        assertNull(SharedText.from(Intent(Intent.ACTION_VIEW).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x")))
        assertNull(SharedText.from(send("text/plain", text = "   ")))
        assertNull(SharedText.from(null))
    }

    @Test
    fun veryLongSharesAreCut() {
        assertEquals(SharedText.MaxLength, SharedText.combine(null, "a".repeat(SharedText.MaxLength + 50))!!.length)
    }

    @Test
    fun sharedTextIsAppendedToAnUnsentDraft() {
        assertEquals("half typed\nshared", SharedText.mergeIntoDraft("half typed  ", "shared"))
        assertEquals("shared", SharedText.mergeIntoDraft("  ", "shared"))
    }
}
