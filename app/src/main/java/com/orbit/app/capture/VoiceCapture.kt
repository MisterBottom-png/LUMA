package com.orbit.app.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import java.util.Locale

/**
 * Dictation. Where the phone can recognise speech on the device itself (Android 12+),
 * Tallele listens through that recogniser, so the words never leave the phone. Otherwise
 * it opens the phone's own speech recognition screen, which may send audio to the
 * provider's servers; the user is told that once, before the first use.
 */
object VoiceCapture {
    /** How a spoken thought is turned into text on this phone. */
    enum class Route { OnDevice, SystemScreen }

    /** On-device recognition when the phone has it (API 31+), the system screen otherwise. */
    fun route(sdkInt: Int, onDeviceAvailable: Boolean): Route =
        if (sdkInt >= Build.VERSION_CODES.S && onDeviceAvailable) Route.OnDevice else Route.SystemScreen

    /** The "may use Google's servers" notice: once, and only for the system screen. */
    fun shouldShowCloudNotice(route: Route, alreadySeen: Boolean): Boolean =
        route == Route.SystemScreen && !alreadySeen

    fun intent(locale: Locale, prompt: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    fun hasSystemScreen(context: Context): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        }.getOrDefault(false)

    fun isOnDeviceAvailable(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)

    /** The best transcript, or null when the user cancelled or nothing was heard. */
    fun transcript(data: Intent?): String? = cleanTranscript(data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS))

    /** The best transcript from recogniser results, or null when nothing was heard. */
    fun recognizerTranscript(results: Bundle?): String? =
        cleanTranscript(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))

    private fun cleanTranscript(results: List<String>?): String? =
        results
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(SharedText.MaxLength)

    /** Why on-device listening ended without words, in terms the user can act on. */
    enum class Problem { NothingHeard, LanguageMissing, Unavailable }

    fun problemFor(error: Int): Problem = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Problem.NothingHeard
        ErrorLanguageNotSupported, ErrorLanguageUnavailable -> Problem.LanguageMissing
        else -> Problem.Unavailable
    }

    // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / ERROR_LANGUAGE_UNAVAILABLE (API 31).
    private const val ErrorLanguageNotSupported = 12
    private const val ErrorLanguageUnavailable = 13
}

/**
 * One on-device listening turn. The recogniser runs in the phone's own on-device
 * service; Tallele keeps only the words, never the audio.
 */
@RequiresApi(Build.VERSION_CODES.S)
class OnDeviceVoiceSession(
    context: Context,
    private val locale: Locale,
    private val onTranscript: (String) -> Unit,
    private val onProblem: (VoiceCapture.Problem) -> Unit,
    private val onFinished: () -> Unit,
) {
    private val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)

    fun start() {
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = VoiceCapture.recognizerTranscript(results)
                    if (text != null) onTranscript(text) else onProblem(VoiceCapture.Problem.NothingHeard)
                    onFinished()
                }

                override fun onError(error: Int) {
                    onProblem(VoiceCapture.problemFor(error))
                    onFinished()
                }

                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )
        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1),
        )
    }

    /** Stops listening; the words heard so far still arrive as a result. */
    fun stop() {
        runCatching { recognizer.stopListening() }
    }

    fun destroy() {
        runCatching { recognizer.destroy() }
    }
}
