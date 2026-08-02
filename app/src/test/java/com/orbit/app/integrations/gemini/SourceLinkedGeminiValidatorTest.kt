package com.orbit.app.integrations.gemini

import com.orbit.app.domain.ai.AiSourceItem
import com.orbit.app.ui.navigation.ItemDetailType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun askPromptRequiresSourceLanguageAndStructuredLocalizedNoDataResponse() {
        val question = "Что важно завтра?"

        val prompt = SourceLinkedPromptBuilders.askLuma(
            question = question,
            sources = listOf(source("task:1")),
        )

        assertTrue(prompt.contains(question))
        assertTrue(prompt.contains("source or dominant language"))
        assertTrue(prompt.contains("explicitly asks for translation"))
        assertTrue(prompt.contains("hasSufficientData"))
        assertTrue(prompt.contains("empty sourceItemIds"))
    }

    @Test
    fun localizedNoDataAnswerUsesStructuredFlagAndNoSourceIds() {
        val answer = SourceLinkedGeminiValidator.answer(
            text = """{"answer":"Данных недостаточно.","sourceItemIds":[],"hasSufficientData":false}""",
            sources = listOf(source("task:1")),
        )

        assertNotNull(answer)
        assertEquals(emptyList<String>(), checkNotNull(answer).sourceItemIds)
    }

    @Test
    fun noDataFlagRejectsContradictorySourceIds() {
        assertNull(
            SourceLinkedGeminiValidator.answer(
                text = """{"answer":"Andmeid ei ole piisavalt.","sourceItemIds":["task:1"],"hasSufficientData":false}""",
                sources = listOf(source("task:1")),
            ),
        )
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
