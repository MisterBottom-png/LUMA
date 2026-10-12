package com.orbit.app.domain.capture

import java.util.Locale

/**
 * Words that make a thought something other than a plain request, in English, Estonian
 * and Russian: a negation ("don't remind me…", "ära tuleta meelde…", "не напоминай…"),
 * a condition ("if it rains…", "kui…", "если…") or quoted words. Such a thought is never
 * filed automatically (review F03): it is kept for the user, whatever the analysis says.
 *
 * "Don't forget to…" and its translations are reminders, not negations, so they are
 * taken out before the check.
 */
object IntentGuard {
    private val positivePhrases = listOf(
        "don't forget", "dont forget", "do not forget", "never forget", "remember to",
        "ära unusta", "ärge unustage", "ära unustaks",
        "не забудь", "не забыть", "не забудьте", "не забывай",
    )

    private val guardWords = listOf(
        // English
        "don't", "dont", "do not", "doesn't", "does not", "no longer", "never", "not need",
        "no need", "needn't", "if", "unless", "in case", "whether",
        // Estonian (informal and plural imperatives, "no need", "if")
        "ära", "ärge", "ei", "pole vaja", "pole tarvis", "mitte", "enam ei", "kui", "juhul kui",
        // Russian
        "не", "нельзя", "если", "больше не", "не надо", "не нужно", "незачем",
    )

    private val quoteMarks = charArrayOf('"', '“', '”', '«', '»', '„')

    /** True when the thought must not be acted on without the user. */
    fun needsTheUser(rawText: String): Boolean {
        if (rawText.any { it in quoteMarks }) return true
        var text = rawText.lowercase(Locale.ROOT).replace('’', '\'')
        positivePhrases.forEach { phrase -> text = text.replace(phrase, " ") }
        return guardWords.any { word -> text.containsWord(word) }
    }

    private fun String.containsWord(word: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}'])${Regex.escape(word)}(?![\\p{L}\\p{N}])").containsMatchIn(this)
}
