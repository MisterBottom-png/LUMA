package com.orbit.app.domain.analyzer

import java.util.Locale

/**
 * Keeps local fallback guidance close to the capture's script when this can be
 * determined safely, otherwise uses the selected application locale.
 */
internal fun localGuidanceLocale(text: String, preferredLocale: Locale): Locale = when {
    text.any { character -> character in '\u0400'..'\u04ff' } -> Locale.forLanguageTag("ru")
    text.any { character -> character in EstonianDistinctLetters } -> Locale.forLanguageTag("et")
    preferredLocale.language in SupportedGuidanceLanguages -> preferredLocale
    else -> Locale.ENGLISH
}

private val EstonianDistinctLetters = setOf('õ', 'ä', 'ö', 'ü', 'Õ', 'Ä', 'Ö', 'Ü')
private val SupportedGuidanceLanguages = setOf("en", "et", "ru")
