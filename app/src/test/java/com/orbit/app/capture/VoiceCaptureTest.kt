package com.orbit.app.capture

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VoiceCaptureTest {
    @Test
    fun asksForOnDeviceFreeFormRecognitionInTheAppLanguage() {
        val intent = VoiceCapture.intent(Locale.forLanguageTag("et-EE"), "prompt")

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, intent.action)
        assertEquals("et-EE", intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun takesTheBestTranscript_andIgnoresCancelOrSilence() {
        val heard = Intent().putStringArrayListExtra(
            RecognizerIntent.EXTRA_RESULTS,
            arrayListOf("  call the plumber tomorrow ", "call the plumber to Morrow"),
        )
        assertEquals("call the plumber tomorrow", VoiceCapture.transcript(heard))
        assertNull(VoiceCapture.transcript(null))
        assertNull(VoiceCapture.transcript(Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS, arrayListOf(" "))))
    }
}
