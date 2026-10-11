package com.orbit.app.domain.ai

import com.orbit.app.data.local.entity.SuggestedItemType
import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzer
import com.orbit.app.domain.analyzer.LocalReviewAnalyzer
import com.orbit.app.domain.analyzer.localGuidanceLocale
import com.orbit.app.domain.analyzer.ReminderTimeStatus
import com.orbit.app.domain.model.AiMode
import com.orbit.app.domain.model.AppSettings
import com.orbit.app.integrations.gemini.GeminiApiClient
import com.orbit.app.integrations.gemini.GeminiApiError
import com.orbit.app.integrations.gemini.GeminiApiResult
import com.orbit.app.integrations.gemini.GeminiJsonValidator
import com.orbit.app.integrations.gemini.GeminiPromptBuilders
import com.orbit.app.integrations.gemini.SourceLinkedGeminiValidator
import com.orbit.app.integrations.gemini.SourceLinkedPromptBuilders
import com.orbit.app.integrations.gemini.geminiError
import com.orbit.app.integrations.gemini.GeminiApiErrorKind
import com.orbit.app.security.GeminiApiKeyStore
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class AiRouteSource {
    Local,
    Gemini,
    GeminiFailedLocalUsed,
}

data class AiRouteMetadata(
    val source: AiRouteSource,
    val cloudUsed: Boolean,
    val modelId: String? = null,
    val error: GeminiApiError? = null,
)

data class RoutedCaptureAnalysis(
    val analysis: CaptureAnalysis,
    val metadata: AiRouteMetadata,
)

data class RoutedTinyAction(
    val action: String,
    val metadata: AiRouteMetadata,
)

class OrbitAiRouter(
    private val localCaptureAnalyzer: CaptureAnalyzer,
    private val geminiApiClient: GeminiApiClient,
    private val geminiApiKeyStore: GeminiApiKeyStore,
    private val learningProfileProvider: LearningProfileProvider = EmptyLearningProfileProvider,
    private val locale: () -> Locale = { Locale.ENGLISH },
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val answerText: LocalAnswerText = EnglishLocalAnswerText,
    private val retriever: LocalAiRetriever = LocalAiRetriever(),
) {
    suspend fun analyzeCapture(
        rawText: String,
        settings: AppSettings,
        allowedSpaces: List<String> = emptyList(),
    ): RoutedCaptureAnalysis {
        val local = { fallback(rawText, null) }
        val localAnalysis = localCaptureAnalyzer.analyze(rawText)
        val useBrainDumpGemini = localAnalysis.brainDumpItems.isNotEmpty() &&
            localAnalysis.brainDumpItems.size <= MaxGeminiBrainDumpItems &&
            settings.canUseGemini(settings.useGeminiForBrainDump)
        val useCaptureGemini = settings.canUseGemini(settings.useGeminiForCapture)
        if (!useBrainDumpGemini && !useCaptureGemini) {
            return localAnalysis.routedLocal()
        }
        val apiKey = geminiApiKeyStore.getKey() ?: return local()

        if (useBrainDumpGemini) {
            return analyzeBrainDump(
                rawText = rawText,
                localAnalysis = localAnalysis,
                settings = settings,
                apiKey = apiKey,
                allowedSpaces = allowedSpaces,
            )
        }

        return when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiFastModelId,
                prompt = GeminiPromptBuilders.captureAnalysis(
                    rawText = rawText,
                    allowedSpaces = allowedSpaces,
                    learningProfile = learningProfileProvider.profileFor(rawText),
                ),
                maxOutputTokens = 384,
            )
        ) {
            is GeminiApiResult.Success -> {
                val analysis = GeminiJsonValidator.captureAnalysis(
                    text = result.text,
                    fallbackRawText = rawText,
                    allowedSpaces = allowedSpaces,
                )
                if (analysis != null) {
                    RoutedCaptureAnalysis(
                        analysis = analysis.withCanonicalReminderTime(localAnalysis),
                        metadata = AiRouteMetadata(
                            source = AiRouteSource.Gemini,
                            cloudUsed = true,
                            modelId = result.modelId,
                        ),
                    )
                } else {
                    fallback(rawText, geminiError(GeminiApiErrorKind.InvalidResponse))
                }
            }

            is GeminiApiResult.Failure -> fallback(rawText, result.error)
        }
    }

    private fun CaptureAnalysis.withCanonicalReminderTime(
        localAnalysis: CaptureAnalysis,
    ): CaptureAnalysis = when (localAnalysis.reminderTimeStatus) {
        ReminderTimeStatus.Resolved -> copy(
            reminderPossible = true,
            suggestedReminderAt = localAnalysis.suggestedReminderAt,
            reminderPhrase = localAnalysis.reminderPhrase,
            reminderTimeStatus = ReminderTimeStatus.Resolved,
        )

        ReminderTimeStatus.NeedsClarification -> copy(
            reminderPossible = true,
            suggestedReminderAt = null,
            reminderPhrase = null,
            reminderTimeStatus = ReminderTimeStatus.NeedsClarification,
        )

        // A task gets a day, never a clock time: the day the local rules found, else
        // the day Gemini named.
        ReminderTimeStatus.Unspecified -> if (suggestedType == SuggestedItemType.Task || suggestedType == SuggestedItemType.MondayItem) {
            copy(
                suggestedReminderAt = null,
                taskDateEpochDay = localAnalysis.taskDateEpochDay
                    ?: suggestedReminderAt?.let { Instant.ofEpochMilli(it).atZone(zoneId()).toLocalDate().toEpochDay() },
            )
        } else {
            this
        }
    }

    /**
     * Gemini suggests title, type and Space per fragment, in groups of
     * [GeminiBrainDumpGroupSize] so a long dump is never cut short. Any group that
     * comes back wrong means the whole dump keeps the local suggestions.
     */
    private suspend fun analyzeBrainDump(
        rawText: String,
        localAnalysis: CaptureAnalysis,
        settings: AppSettings,
        apiKey: String,
        allowedSpaces: List<String>,
    ): RoutedCaptureAnalysis {
        val learningProfile = learningProfileProvider.profileFor(rawText)
        val enriched = mutableListOf<com.orbit.app.domain.analyzer.BrainDumpSuggestion>()
        var modelId: String? = null
        for (group in localAnalysis.brainDumpItems.chunked(GeminiBrainDumpGroupSize)) {
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiReasoningModelId,
                prompt = GeminiPromptBuilders.brainDump(
                    rawText = rawText,
                    sourceFragments = group,
                    allowedSpaces = allowedSpaces,
                    learningProfile = learningProfile,
                ),
                maxOutputTokens = brainDumpOutputTokens(group.size),
            )
            when (result) {
                is GeminiApiResult.Success -> {
                    val items = GeminiJsonValidator.brainDumpSuggestions(
                        text = result.text,
                        allowedSpaces = allowedSpaces,
                        expectedItems = group,
                    ) ?: return fallback(rawText, geminiError(GeminiApiErrorKind.InvalidResponse))
                    enriched += items
                    modelId = result.modelId
                }
                is GeminiApiResult.Failure -> return fallback(rawText, result.error)
            }
        }
        return RoutedCaptureAnalysis(
            // Title, summary and chips stay the local analysis's wording, which
            // follows the language of the thought; only the per-thought suggestions
            // come from Gemini.
            analysis = localAnalysis.copy(
                relatedTopics = enriched.map { it.suggestedSpaceName }.distinct(),
                brainDumpItems = enriched,
                analyzerSource = com.orbit.app.domain.analyzer.CaptureAnalyzerSource.Gemini,
            ),
            metadata = AiRouteMetadata(
                source = AiRouteSource.Gemini,
                cloudUsed = true,
                modelId = modelId,
            ),
        )
    }

    /**
     * Where a one-line thought should be cut, when the user asked to split it. Null
     * when Gemini is off or its answer is not an exact copy of the user's words.
     */
    suspend fun thoughtParts(rawText: String, settings: AppSettings): List<String>? {
        if (!settings.canUseGemini(settings.useGeminiForBrainDump)) return null
        val apiKey = geminiApiKeyStore.getKey() ?: return null
        val result = geminiApiClient.generateJson(
            apiKey = apiKey,
            modelId = settings.geminiReasoningModelId,
            prompt = GeminiPromptBuilders.thoughtParts(rawText),
            maxOutputTokens = (rawText.length / 2 + 128).coerceIn(256, 2048),
        )
        return (result as? GeminiApiResult.Success)?.let { GeminiJsonValidator.thoughtParts(it.text, rawText) }
    }

    fun canSplitWithGemini(settings: AppSettings): Boolean = settings.canUseGemini(settings.useGeminiForBrainDump)

    suspend fun makeSmaller(text: String, settings: AppSettings): RoutedTinyAction {
        val localAction = { error: GeminiApiError? ->
            RoutedTinyAction(
                action = LocalReviewAnalyzer.makeSmallerText(text, locale()),
                metadata = AiRouteMetadata(
                    source = if (error == null) AiRouteSource.Local else AiRouteSource.GeminiFailedLocalUsed,
                    cloudUsed = false,
                    error = error,
                ),
            )
        }
        if (!settings.canUseGemini(settings.useGeminiForMakeSmaller)) return localAction(null)
        val apiKey = geminiApiKeyStore.getKey() ?: return localAction(null)

        val prompt = GeminiPromptBuilders.tinyAction(
            text = text,
            learningProfile = learningProfileProvider.profileFor(text),
        )
        val firstAttempt = generateTinyAction(
            apiKey = apiKey,
            modelId = settings.geminiReasoningModelId,
            prompt = prompt,
            sourceText = text,
        )
        if (firstAttempt.metadata.source == AiRouteSource.Gemini) return firstAttempt

        val fastModel = settings.geminiFastModelId.takeIf {
            it.isNotBlank() && !it.equals(settings.geminiReasoningModelId, ignoreCase = true)
        }
        val secondAttempt = fastModel?.let { modelId ->
            generateTinyAction(
                apiKey = apiKey,
                modelId = modelId,
                prompt = prompt,
                sourceText = text,
            )
        }
        return if (secondAttempt?.metadata?.source == AiRouteSource.Gemini) {
            secondAttempt
        } else {
            localAction(secondAttempt?.metadata?.error ?: firstAttempt.metadata.error)
        }
    }

    suspend fun askLuma(
        question: String,
        sources: List<AiSourceItem>,
    ): SourceLinkedAnswer {
        if (sources.isEmpty()) {
            return SourceLinkedAnswer(
                answer = answerText.text(localGuidanceLocale(question, locale()), LocalAnswerKind.NoMatch, ""),
                sourceItemIds = emptyList(),
                sourceItems = emptyList(),
                fromGemini = false,
                searchQuery = question.trim(),
            )
        }
        // V1 keeps factual answers deterministic. Gemini is not allowed to add facts or state;
        // source-backed wording can be reintroduced only behind a validator that proves this.
        return localAnswer(question, sources)
    }

    suspend fun summarizeSituation(
        sources: List<AiSourceItem>,
        settings: AppSettings,
        localSummary: SituationSourceSummary,
    ): SituationSourceSummary {
        if (sources.isEmpty() || !settings.canUseGemini(settings.useGeminiForSituation)) return localSummary
        val apiKey = geminiApiKeyStore.getKey() ?: return localSummary
        return when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiReasoningModelId,
                prompt = SourceLinkedPromptBuilders.situationSummary(
                    sources = sources,
                    learningProfile = learningProfileProvider.profileFor(sources.toProfileQuery("situation")),
                ),
                maxOutputTokens = 360,
            )
        ) {
            is GeminiApiResult.Success ->
                SourceLinkedGeminiValidator.situation(result.text, sources) ?: localSummary

            is GeminiApiResult.Failure -> localSummary
        }
    }

    suspend fun summarizeReview(
        sources: List<AiSourceItem>,
        settings: AppSettings,
    ): SourceLinkedAnswer {
        if (sources.isEmpty()) return noDataAnswer()
        val local = localAnswer("weekly review", sources)
        if (!settings.canUseGemini(settings.useGeminiForReview)) return local
        val apiKey = geminiApiKeyStore.getKey() ?: return local
        return when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiReasoningModelId,
                prompt = SourceLinkedPromptBuilders.reviewSummary(
                    sources = sources,
                    learningProfile = learningProfileProvider.profileFor(sources.toProfileQuery("review")),
                ),
                maxOutputTokens = 320,
            )
        ) {
            is GeminiApiResult.Success ->
                SourceLinkedGeminiValidator.answer(result.text, sources) ?: local

            is GeminiApiResult.Failure -> local
        }
    }

    private suspend fun generateTinyAction(
        apiKey: String,
        modelId: String,
        prompt: String,
        sourceText: String,
    ): RoutedTinyAction =
        when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = modelId,
                prompt = prompt,
                maxOutputTokens = 128,
            )
        ) {
            is GeminiApiResult.Success -> {
                val action = GeminiJsonValidator.tinyAction(result.text)
                if (action != null) {
                    RoutedTinyAction(
                        action = action,
                        metadata = AiRouteMetadata(
                            source = AiRouteSource.Gemini,
                            cloudUsed = true,
                            modelId = result.modelId,
                        ),
                    )
                } else {
                    RoutedTinyAction(
                        action = LocalReviewAnalyzer.makeSmallerText(sourceText, locale()),
                        metadata = AiRouteMetadata(
                            source = AiRouteSource.GeminiFailedLocalUsed,
                            cloudUsed = false,
                            error = geminiError(GeminiApiErrorKind.InvalidResponse),
                        ),
                    )
                }
            }

            is GeminiApiResult.Failure -> RoutedTinyAction(
                action = LocalReviewAnalyzer.makeSmallerText(sourceText, locale()),
                metadata = AiRouteMetadata(
                    source = AiRouteSource.GeminiFailedLocalUsed,
                    cloudUsed = false,
                    error = result.error,
                ),
            )
        }

    private fun fallback(rawText: String, error: GeminiApiError?): RoutedCaptureAnalysis =
        RoutedCaptureAnalysis(
            analysis = localCaptureAnalyzer.analyze(rawText)
                .copy(
                    analyzerFailed = error != null,
                    analyzerSource = if (error == null) {
                        com.orbit.app.domain.analyzer.CaptureAnalyzerSource.Local
                    } else {
                        com.orbit.app.domain.analyzer.CaptureAnalyzerSource.GeminiFallback
                    },
                ),
            metadata = AiRouteMetadata(
                source = if (error == null) AiRouteSource.Local else AiRouteSource.GeminiFailedLocalUsed,
                cloudUsed = false,
                error = error,
            ),
        )

    private fun AppSettings.canUseGemini(featureEnabled: Boolean): Boolean =
        aiMode == AiMode.GeminiApi && featureEnabled

    private companion object {
        const val MaxGeminiBrainDumpItems = 60
        const val GeminiBrainDumpGroupSize = 10

        /** About 100 tokens of JSON per thought, within the client's 2048 cap. */
        fun brainDumpOutputTokens(items: Int): Int = (items * 110 + 160).coerceIn(384, 2048)
    }

    private fun CaptureAnalysis.routedLocal(): RoutedCaptureAnalysis =
        RoutedCaptureAnalysis(
            analysis = copy(analyzerSource = com.orbit.app.domain.analyzer.CaptureAnalyzerSource.Local),
            metadata = AiRouteMetadata(source = AiRouteSource.Local, cloudUsed = false),
        )

    private fun noDataAnswer(): SourceLinkedAnswer =
        SourceLinkedAnswer(
            answer = answerText.text(locale(), LocalAnswerKind.NoData, ""),
            sourceItemIds = emptyList(),
            sourceItems = emptyList(),
            fromGemini = false,
        )

    /** Cites the first three sources; the wording comes from string resources. */
    private fun localAnswer(question: String, sources: List<AiSourceItem>): SourceLinkedAnswer {
        val top = sources.take(3)
        val locale = localGuidanceLocale(question, locale())
        val answer = if (top.isEmpty()) {
            answerText.text(locale, LocalAnswerKind.NoData, "")
        } else {
            answerText.text(locale, retriever.answerKind(question), top.joinToString { it.title })
        }
        return SourceLinkedAnswer(
            answer = answer,
            sourceItemIds = top.map { it.sourceId },
            sourceItems = top,
            fromGemini = false,
        )
    }

    private fun List<AiSourceItem>.toProfileQuery(prefix: String): String =
        (listOf(prefix) + take(8).flatMap { item -> listOf(item.title, item.snippet, item.spaceName.orEmpty()) })
            .joinToString(" ")
}

/** English wording, for tests and as a fallback; the app passes string resources. */
object EnglishLocalAnswerText : LocalAnswerText {
    override fun text(locale: Locale, kind: LocalAnswerKind, titles: String): String = when (kind) {
        LocalAnswerKind.NoMatch -> "Nothing in Tallele mentions that."
        LocalAnswerKind.NoData -> "Nothing found."
        LocalAnswerKind.Matches -> "These mention it: $titles."
        LocalAnswerKind.Overdue -> "These are overdue: $titles."
        LocalAnswerKind.Waiting -> "These are waiting on something: $titles."
        LocalAnswerKind.Completed -> "These are done: $titles."
        LocalAnswerKind.Upcoming -> "These have dates coming up: $titles."
        LocalAnswerKind.Recent -> "These changed recently: $titles."
    }
}
