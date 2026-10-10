package com.orbit.app.domain.ai

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orbit.app.integrations.gemini.GeminiApiClient
import com.orbit.app.security.GeminiApiKeyStore
import com.orbit.app.ui.localization.ResourceLocalAnswerText
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Ask's local answers come from string resources, in the question's language. */
@RunWith(AndroidJUnit4::class)
class LocalAnswerTextTest {
    private val text = ResourceLocalAnswerText(ApplicationProvider.getApplicationContext())

    @Test
    fun noMatchSaysSoInEveryLanguage() {
        assertEquals("Nothing in Tallele mentions that.", text.text(Locale.ENGLISH, LocalAnswerKind.NoMatch, ""))
        assertEquals("Talleles pole selle kohta midagi.", text.text(Locale.forLanguageTag("et"), LocalAnswerKind.NoMatch, ""))
        assertEquals("В Tallele об этом ничего нет.", text.text(Locale.forLanguageTag("ru"), LocalAnswerKind.NoMatch, ""))
    }

    @Test
    fun matchesNameTheCitedItems() {
        assertEquals("These mention it: Garden plan.", text.text(Locale.ENGLISH, LocalAnswerKind.Matches, "Garden plan"))
        assertEquals("Tähtaeg on möödas: Arve.", text.text(Locale.forLanguageTag("et"), LocalAnswerKind.Overdue, "Arve"))
    }

    @Test
    fun aQuestionWithNoMatchOffersSearchAndCitesNothing() = runBlocking {
        val router = OrbitAiRouter(
            localCaptureAnalyzer = com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer(),
            geminiApiClient = UnusedGemini,
            geminiApiKeyStore = NoKey,
            answerText = text,
        )
        val answer = router.askLuma("car insurance", emptyList())
        assertEquals("Nothing in Tallele mentions that.", answer.answer)
        assertTrue(answer.sourceItems.isEmpty())
        assertEquals("car insurance", answer.searchQuery)
        assertFalse(answer.fromGemini)
    }
}

private object UnusedGemini : GeminiApiClient {
    override suspend fun generateJson(apiKey: String, modelId: String, prompt: String, maxOutputTokens: Int) =
        error("Ask answers locally")
    override suspend fun testConnection(apiKey: String, modelId: String) = error("not used")
}

private object NoKey : GeminiApiKeyStore {
    override suspend fun saveKey(apiKey: String) = Unit
    override suspend fun getKey(): String? = null
    override suspend fun hasKey(): Boolean = false
    override suspend fun deleteKey() = Unit
}
