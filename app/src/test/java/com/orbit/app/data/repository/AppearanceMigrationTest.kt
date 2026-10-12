package com.orbit.app.data.repository

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.GlassEffect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** C7: violet for new installs only; Soft glass becomes Glass. */
@RunWith(AndroidJUnit4::class)
class AppearanceMigrationTest {
    private val repository = DataStoreAppSettingsRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun aNewInstallIsViolet() {
        assertEquals(AppAccentColor.LumaViolet, repository.toAppSettings(emptyPreferences()).accentColor)
    }

    @Test
    fun anInstallSetUpBeforeKeepsInkAndPaperOrWhatItChose() {
        val setUp = mutablePreferencesOf(booleanPreferencesKey("has_completed_first_time_tutorial") to true)
        assertEquals(AppAccentColor.InkPaper, repository.toAppSettings(setUp).accentColor)
        val chose = mutablePreferencesOf(
            booleanPreferencesKey("has_completed_first_time_tutorial") to true,
            stringPreferencesKey("accent_color") to AppAccentColor.Sage.name,
        )
        assertEquals(AppAccentColor.Sage, repository.toAppSettings(chose).accentColor)
    }

    @Test
    fun softGlassBecomesGlass() {
        val soft = mutablePreferencesOf(stringPreferencesKey("glass_effect") to GlassEffect.Soft.name)
        assertEquals(GlassEffect.Strong, repository.toAppSettings(soft).glassEffect)
        val solid = mutablePreferencesOf(stringPreferencesKey("glass_effect") to GlassEffect.Off.name)
        assertEquals(GlassEffect.Off, repository.toAppSettings(solid).glassEffect)
    }
}
