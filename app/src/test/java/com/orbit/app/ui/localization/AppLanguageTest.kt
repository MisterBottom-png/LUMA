package com.orbit.app.ui.localization

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun `empty language tags use system default`() {
        assertEquals(AppLanguage.SystemDefault, AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun `supported regional language tags map to application language`() {
        assertEquals(AppLanguage.English, AppLanguage.fromLanguageTags("en-GB"))
        assertEquals(AppLanguage.Estonian, AppLanguage.fromLanguageTags("et-EE"))
        assertEquals(AppLanguage.Russian, AppLanguage.fromLanguageTags("ru-RU"))
    }

    @Test
    fun `first language tag controls the application language`() {
        assertEquals(AppLanguage.Russian, AppLanguage.fromLanguageTags("ru-RU,et-EE"))
    }

    @Test
    fun `unsupported explicit language falls back to system default`() {
        assertEquals(AppLanguage.SystemDefault, AppLanguage.fromLanguageTags("de-DE"))
    }

    @Test
    fun `explicit app locale takes precedence over the configured system locale`() {
        assertEquals(
            Locale.forLanguageTag("ru"),
            resolveEffectiveAppLocale(
                applicationLocale = Locale.forLanguageTag("ru"),
                configurationLocale = Locale.forLanguageTag("et-EE"),
            ),
        )
    }

    @Test
    fun `configuration locale is used when the app follows the system`() {
        assertEquals(
            Locale.forLanguageTag("et-EE"),
            resolveEffectiveAppLocale(
                applicationLocale = null,
                configurationLocale = Locale.forLanguageTag("et-EE"),
            ),
        )
    }
}
