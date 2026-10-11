package com.orbit.app.capture

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun onDeviceRecognitionIsUsedOnAndroid12AndLaterWhenThePhoneHasIt() {
        assertEquals(VoiceCapture.Route.OnDevice, VoiceCapture.route(sdkInt = 31, onDeviceAvailable = true))
        assertEquals(VoiceCapture.Route.OnDevice, VoiceCapture.route(sdkInt = 35, onDeviceAvailable = true))
        assertEquals(VoiceCapture.Route.SystemScreen, VoiceCapture.route(sdkInt = 35, onDeviceAvailable = false))
        assertEquals(VoiceCapture.Route.SystemScreen, VoiceCapture.route(sdkInt = 30, onDeviceAvailable = true))
    }

    @Test
    fun theServersNoticeShowsOnce_andOnlyForThePhonesSpeechScreen() {
        assertTrue(VoiceCapture.shouldShowCloudNotice(VoiceCapture.Route.SystemScreen, alreadySeen = false))
        assertFalse(VoiceCapture.shouldShowCloudNotice(VoiceCapture.Route.SystemScreen, alreadySeen = true))
        assertFalse(VoiceCapture.shouldShowCloudNotice(VoiceCapture.Route.OnDevice, alreadySeen = false))
    }

    @Test
    fun onDeviceResultsGiveTheBestTranscript() {
        val results = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("  buy milk ", "by milk"))
        }
        assertEquals("buy milk", VoiceCapture.recognizerTranscript(results))
        assertNull(VoiceCapture.recognizerTranscript(Bundle()))
        assertNull(VoiceCapture.recognizerTranscript(null))
    }

    @Test
    fun aFailedListenSaysWhyInWordsTheUserCanActOn() {
        assertEquals(VoiceCapture.Problem.NothingHeard, VoiceCapture.problemFor(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(VoiceCapture.Problem.NothingHeard, VoiceCapture.problemFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(VoiceCapture.Problem.LanguageMissing, VoiceCapture.problemFor(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals(VoiceCapture.Problem.LanguageMissing, VoiceCapture.problemFor(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))
        assertEquals(VoiceCapture.Problem.Unavailable, VoiceCapture.problemFor(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(VoiceCapture.Problem.Unavailable, VoiceCapture.problemFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
    }
}
