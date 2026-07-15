package com.orbit.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orbit.app.domain.model.AppAccentColor
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.domain.model.AppTextColor
import com.orbit.app.domain.model.BackgroundPreset
import com.orbit.app.domain.model.SettingsTimeFormatMode
import com.orbit.app.domain.model.SettingsThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.orbitSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orbit_settings",
)

interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(settings: AppSettings)
    suspend fun reset()
}

class DataStoreAppSettingsRepository(context: Context) : AppSettingsRepository {
    private val dataStore = context.applicationContext.orbitSettingsDataStore

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences())
            else throw exception
        }
        .map(::toAppSettings)

    override suspend fun update(settings: AppSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.USER_NAME] = settings.userName
            preferences[Keys.THEME_MODE] = settings.themeMode.name
            preferences[Keys.TIME_FORMAT_MODE] = settings.timeFormatMode.name
            preferences[Keys.BACKGROUND_PRESET] = settings.backgroundPreset.name
            settings.customBackgroundUri?.let { preferences[Keys.CUSTOM_BACKGROUND_URI] = it }
                ?: preferences.remove(Keys.CUSTOM_BACKGROUND_URI)
            preferences[Keys.BACKGROUND_BLUR] = settings.backgroundBlur
            preferences[Keys.BACKGROUND_DIM] = settings.backgroundDim
            preferences[Keys.GLASS_STRENGTH] = settings.glassStrength
            preferences[Keys.ACCENT_COLOR] = settings.accentColor.name
            preferences[Keys.TEXT_COLOR] = settings.textColor.name
            preferences[Keys.STALE_LOOP_DAYS] = settings.staleLoopDays
            preferences[Keys.AI_MODE] = settings.aiMode.name
            preferences[Keys.GEMINI_FAST_MODEL_ID] = settings.geminiFastModelId
            preferences[Keys.GEMINI_REASONING_MODEL_ID] = settings.geminiReasoningModelId
            preferences[Keys.USE_GEMINI_FOR_CAPTURE] = settings.useGeminiForCapture
            preferences[Keys.USE_GEMINI_FOR_MAKE_SMALLER] = settings.useGeminiForMakeSmaller
            preferences[Keys.USE_GEMINI_FOR_BRAIN_DUMP] = settings.useGeminiForBrainDump
            preferences[Keys.USE_GEMINI_FOR_SITUATION] = settings.useGeminiForSituation
            preferences[Keys.USE_GEMINI_FOR_REVIEW] = settings.useGeminiForReview
        }
    }

    override suspend fun reset() {
        dataStore.edit { preferences -> preferences.clear() }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            userName = preferences[Keys.USER_NAME] ?: defaults.userName,
            themeMode = preferences[Keys.THEME_MODE]
                ?.let { runCatching { SettingsThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            timeFormatMode = preferences[Keys.TIME_FORMAT_MODE]
                ?.let { runCatching { SettingsTimeFormatMode.valueOf(it) }.getOrNull() }
                ?: defaults.timeFormatMode,
            backgroundPreset = preferences[Keys.BACKGROUND_PRESET]
                ?.let(::backgroundPresetFromStoredValue)
                ?: defaults.backgroundPreset,
            customBackgroundUri = preferences[Keys.CUSTOM_BACKGROUND_URI],
            backgroundBlur = preferences[Keys.BACKGROUND_BLUR] ?: defaults.backgroundBlur,
            backgroundDim = preferences[Keys.BACKGROUND_DIM] ?: defaults.backgroundDim,
            glassStrength = preferences[Keys.GLASS_STRENGTH] ?: defaults.glassStrength,
            accentColor = preferences[Keys.ACCENT_COLOR]
                ?.let { runCatching { AppAccentColor.valueOf(it) }.getOrNull() }
                ?: defaults.accentColor,
            textColor = preferences[Keys.TEXT_COLOR]
                ?.let { runCatching { AppTextColor.valueOf(it) }.getOrNull() }
                ?: defaults.textColor,
            staleLoopDays = preferences[Keys.STALE_LOOP_DAYS] ?: defaults.staleLoopDays,
            aiMode = preferences[Keys.AI_MODE]
                ?.let { runCatching { AiMode.valueOf(it) }.getOrNull() }
                ?: defaults.aiMode,
            geminiFastModelId = preferences[Keys.GEMINI_FAST_MODEL_ID]
                ?.takeIf { it.isNotBlank() }
                ?: defaults.geminiFastModelId,
            geminiReasoningModelId = preferences[Keys.GEMINI_REASONING_MODEL_ID]
                ?.takeIf { it.isNotBlank() }
                ?: defaults.geminiReasoningModelId,
            useGeminiForCapture = preferences[Keys.USE_GEMINI_FOR_CAPTURE]
                ?: defaults.useGeminiForCapture,
            useGeminiForMakeSmaller = preferences[Keys.USE_GEMINI_FOR_MAKE_SMALLER]
                ?: defaults.useGeminiForMakeSmaller,
            useGeminiForBrainDump = preferences[Keys.USE_GEMINI_FOR_BRAIN_DUMP]
                ?: defaults.useGeminiForBrainDump,
            useGeminiForSituation = preferences[Keys.USE_GEMINI_FOR_SITUATION]
                ?: defaults.useGeminiForSituation,
            useGeminiForReview = preferences[Keys.USE_GEMINI_FOR_REVIEW]
                ?: defaults.useGeminiForReview,
        )
    }

    private fun backgroundPresetFromStoredValue(value: String): BackgroundPreset? =
        BackgroundPreset.entries.firstOrNull { preset ->
            preset.name == value || preset.label == value
        }

    private object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val TIME_FORMAT_MODE = stringPreferencesKey("time_format_mode")
        val BACKGROUND_PRESET = stringPreferencesKey("background_preset")
        val CUSTOM_BACKGROUND_URI = stringPreferencesKey("custom_background_uri")
        val BACKGROUND_BLUR = floatPreferencesKey("background_blur")
        val BACKGROUND_DIM = floatPreferencesKey("background_dim")
        val GLASS_STRENGTH = floatPreferencesKey("glass_strength")
        val ACCENT_COLOR = stringPreferencesKey("accent_color")
        val TEXT_COLOR = stringPreferencesKey("text_color")
        val STALE_LOOP_DAYS = intPreferencesKey("stale_loop_days")
        val AI_MODE = stringPreferencesKey("ai_mode")
        val GEMINI_FAST_MODEL_ID = stringPreferencesKey("gemini_fast_model_id")
        val GEMINI_REASONING_MODEL_ID = stringPreferencesKey("gemini_reasoning_model_id")
        val USE_GEMINI_FOR_CAPTURE = booleanPreferencesKey("use_gemini_for_capture")
        val USE_GEMINI_FOR_MAKE_SMALLER = booleanPreferencesKey("use_gemini_for_make_smaller")
        val USE_GEMINI_FOR_BRAIN_DUMP = booleanPreferencesKey("use_gemini_for_brain_dump")
        val USE_GEMINI_FOR_SITUATION = booleanPreferencesKey("use_gemini_for_situation")
        val USE_GEMINI_FOR_REVIEW = booleanPreferencesKey("use_gemini_for_review")
    }
}
