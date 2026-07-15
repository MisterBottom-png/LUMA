package com.orbit.app.data.local

import com.orbit.app.data.local.entity.AiSuggestionOutcome
import com.orbit.app.data.local.entity.AiSuggestionSurface
import com.orbit.app.data.local.entity.LearnedRuleCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class OrbitTypeConvertersTest {
    private val converters = OrbitTypeConverters()

    @Test
    fun convertsLearningMemoryEnums() {
        assertEquals(
            AiSuggestionOutcome.Corrected,
            converters.stringToAiSuggestionOutcome(
                converters.aiSuggestionOutcomeToString(AiSuggestionOutcome.Corrected),
            ),
        )
        assertEquals(
            AiSuggestionSurface.Capture,
            converters.stringToAiSuggestionSurface(
                converters.aiSuggestionSurfaceToString(AiSuggestionSurface.Capture),
            ),
        )
        assertEquals(
            LearnedRuleCategory.Space,
            converters.stringToLearnedRuleCategory(
                converters.learnedRuleCategoryToString(LearnedRuleCategory.Space),
            ),
        )
    }
}
