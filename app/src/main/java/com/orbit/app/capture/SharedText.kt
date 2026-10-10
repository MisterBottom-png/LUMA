package com.orbit.app.capture

import android.content.Intent

/**
 * Text another app shares into LUMA. It only fills the Home draft; the user still
 * sends it, so nothing is saved without them seeing it first.
 */
object SharedText {
    /** Longer shares are cut so a pasted book cannot freeze the capture box. */
    const val MaxLength = 20_000

    fun from(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("text/")) return null
        return combine(
            subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
            text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
    }

    /** Subject and body on separate lines, without repeating a subject the body already starts with. */
    fun combine(subject: String?, text: String?): String? {
        val cleanSubject = subject?.trim().orEmpty()
        val cleanText = text?.trim().orEmpty()
        val combined = when {
            cleanSubject.isEmpty() -> cleanText
            cleanText.isEmpty() -> cleanSubject
            cleanText.startsWith(cleanSubject) -> cleanText
            else -> "$cleanSubject\n$cleanText"
        }
        return combined.take(MaxLength).takeIf { it.isNotBlank() }
    }

    /** Appends to an unsent draft instead of replacing what the user was typing. */
    fun mergeIntoDraft(draft: String, shared: String): String =
        if (draft.isBlank()) shared else draft.trimEnd() + "\n" + shared
}
