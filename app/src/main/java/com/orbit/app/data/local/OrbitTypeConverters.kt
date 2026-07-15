package com.orbit.app.data.local

import androidx.room.TypeConverter
import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.CaptureSource
import com.orbit.app.data.local.entity.CaptureStatus
import com.orbit.app.data.local.entity.LearnedRuleCategory
import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.data.local.entity.TaskStatus

class OrbitTypeConverters {
    @TypeConverter
    fun captureStatusToString(value: CaptureStatus): String = value.name

    @TypeConverter
    fun stringToCaptureStatus(value: String): CaptureStatus = CaptureStatus.valueOf(value)

    @TypeConverter
    fun captureSourceToString(value: CaptureSource): String = value.name

    @TypeConverter
    fun stringToCaptureSource(value: String): CaptureSource = CaptureSource.valueOf(value)

    @TypeConverter
    fun suggestedTypeToString(value: SuggestedItemType?): String? = value?.name

    @TypeConverter
    fun stringToSuggestedType(value: String?): SuggestedItemType? = value?.let(SuggestedItemType::valueOf)

    @TypeConverter
    fun taskStatusToString(value: TaskStatus): String = value.name

    @TypeConverter
    fun stringToTaskStatus(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun aiSuggestionOutcomeToString(value: AiSuggestionOutcome): String = value.name

    @TypeConverter
    fun stringToAiSuggestionOutcome(value: String): AiSuggestionOutcome =
        AiSuggestionOutcome.valueOf(value)

    @TypeConverter
    fun aiSuggestionSurfaceToString(value: AiSuggestionSurface): String = value.name

    @TypeConverter
    fun stringToAiSuggestionSurface(value: String): AiSuggestionSurface =
        AiSuggestionSurface.valueOf(value)

    @TypeConverter
    fun learnedRuleCategoryToString(value: LearnedRuleCategory): String = value.name

    @TypeConverter
    fun stringToLearnedRuleCategory(value: String): LearnedRuleCategory =
        LearnedRuleCategory.valueOf(value)
}
