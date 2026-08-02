package com.orbit.app.ui.localization

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.ConfigurationCompat
import java.util.Locale

enum class AppLanguage(val languageTag: String) {
    SystemDefault(""),
    English("en"),
    Estonian("et"),
    Russian("ru"),
    ;

    companion object {
        fun fromLanguageTags(languageTags: String): AppLanguage {
            val primaryLanguage = languageTags
                .substringBefore(',')
                .substringBefore('-')
                .lowercase()
            return entries.firstOrNull { language ->
                language.languageTag == primaryLanguage
            } ?: SystemDefault
        }
    }
}

/**
 * Returns the locale that should be used for presentation generated outside Compose.
 *
 * AppCompat's per-app selection takes precedence. When the user chose "system default",
 * the current configuration supplies the system locale. English is only a defensive
 * fallback for configurations that contain no locale.
 */
fun effectiveAppLocale(context: Context): Locale = resolveEffectiveAppLocale(
    applicationLocale = AppCompatDelegate.getApplicationLocales()[0],
    configurationLocale = ConfigurationCompat.getLocales(context.resources.configuration)[0],
)

internal fun resolveEffectiveAppLocale(
    applicationLocale: Locale?,
    configurationLocale: Locale?,
): Locale = applicationLocale ?: configurationLocale ?: Locale.ENGLISH
