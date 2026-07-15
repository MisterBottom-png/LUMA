package com.orbit.app.integrations.gemini

import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SourceLinkedGeminiValidatorTest {
    @Test
    fun answerRequiresValidatedSourceIds() {
        val sources = listOf(source("task:1"), source("note:2"))
        val answer = SourceLinkedGeminiValidator.answer(
            text = """{"answer":"Work is stuck on task one.","sourceItemIds":["task:1","task:999"]}""",
            sources = sources,
        )

        assertNotNull(answer)
        checkNotNull(answer)
        assertEquals(listOf("task:1"), answer.sourceItemIds)
        assertEquals(1, answer.sourceItems.size)
    }

    @Test
    fun answerRejectsUnsourcedClaims() {
        assertNull(
            SourceLinkedGeminiValidator.answer(
                text = """{"answer":"Something happened.","sourceItemIds":[]}""",
                sources = listOf(source("task:1")),
            ),
        )
    }

    @Test
    fun situationRequiresSources() {
        val sources = listOf(source("capture:3", ItemDetailType.Capture))
        val summary = SourceLinkedGeminiValidator.situation(
            text = """{"rightNow":"One capture is open.","whatMatters":"Place it.","stuck":"Nothing else.","nextTinyStep":"Review it.","sourceItemIds":["capture:3"]}""",
            sources = sources,
        )

        assertNotNull(summary)
        assertEquals(listOf("capture:3"), checkNotNull(summary).sourceItemIds)
    }

    private fun source(
        sourceId: String,
        type: ItemDetailType = ItemDetailType.Task,
    ): AiSourceItem =
        AiSourceItem(
            sourceId = sourceId,
            type = type,
            itemId = sourceId.substringAfter(":").toLong(),
            title = "Title",
            snippet = "Snippet",
            spaceName = "Work",
            status = "Open",
            timestamp = 1L,
        )
}
