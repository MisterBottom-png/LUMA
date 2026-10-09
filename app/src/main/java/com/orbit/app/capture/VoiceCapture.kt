package com.orbit.app.capture

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import java.util.Locale

/**
 * Dictation through the phone's own speech recognition screen. LUMA never records
 * audio itself and asks for on-device recognition where the phone supports it.
 */
object VoiceCapture {
    fun intent(locale: Locale, prompt: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

    fun isAvailable(context: Context): Boolean =
        runCatching {
            context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        }.getOrDefault(false)

    /** The best transcript, or null when the user cancelled or nothing was heard. */
    fun transcript(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(SharedText.MaxLength)
}
