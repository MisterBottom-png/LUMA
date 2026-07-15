package com.orbit.app.domain.ai

import com.orbit.app.domain.analyzer.CaptureAnalysis
import com.orbit.app.domain.analyzer.CaptureAnalyzer
import com.orbit.app.domain.analyzer.LocalReviewAnalyzer
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
) {
    suspend fun analyzeCapture(
        rawText: String,
        settings: AppSettings,
        allowedSpaces: List<String> = emptyList(),
    ): RoutedCaptureAnalysis {
        val local = { fallback(rawText, null) }
        val localAnalysis = localCaptureAnalyzer.analyze(rawText)
        val useBrainDumpGemini = localAnalysis.brainDumpItems.isNotEmpty() &&
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

        ReminderTimeStatus.Unspecified -> this
    }

    private suspend fun analyzeBrainDump(
        rawText: String,
        localAnalysis: CaptureAnalysis,
        settings: AppSettings,
        apiKey: String,
        allowedSpaces: List<String>,
    ): RoutedCaptureAnalysis =
        when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiReasoningModelId,
                prompt = GeminiPromptBuilders.brainDump(
                    rawText = rawText,
                    allowedSpaces = allowedSpaces,
                    learningProfile = learningProfileProvider.profileFor(rawText),
                ),
                maxOutputTokens = 1024,
            )
        ) {
            is GeminiApiResult.Success -> {
                val items = GeminiJsonValidator.brainDumpSuggestions(
                    text = result.text,
                    allowedSpaces = allowedSpaces,
                )
                if (items != null) {
                    RoutedCaptureAnalysis(
                        analysis = localAnalysis.copy(
                            suggestedTitle = "Brain Dump",
                            summary = "A multi-part capture ready to review.",
                            suggestedNextAction = "Review the split suggestions one at a time",
                            relatedTopics = items.map { it.suggestedSpaceName }.distinct(),
                            suggestionChips = listOf("Brain Dump", "Gemini split", "${items.size} items"),
                            brainDumpItems = items,
                            analyzerSource = com.orbit.app.domain.analyzer.CaptureAnalyzerSource.Gemini,
                        ),
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

    suspend fun makeSmaller(text: String, settings: AppSettings): RoutedTinyAction {
        val localAction = { error: GeminiApiError? ->
            RoutedTinyAction(
                action = LocalReviewAnalyzer.makeSmallerText(text),
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
        settings: AppSettings,
    ): SourceLinkedAnswer {
        if (sources.isEmpty()) return noDataAnswer()
        if (!settings.canUseGemini(settings.useGeminiForSituation)) {
            return localAnswer(question, sources)
        }
        val apiKey = geminiApiKeyStore.getKey() ?: return localAnswer(question, sources)
        return when (
            val result = geminiApiClient.generateJson(
                apiKey = apiKey,
                modelId = settings.geminiReasoningModelId,
                prompt = SourceLinkedPromptBuilders.askLuma(
                    question = question,
                    sources = sources,
                    learningProfile = learningProfileProvider.profileFor(question),
                ),
                maxOutputTokens = 320,
            )
        ) {
            is GeminiApiResult.Success ->
                SourceLinkedGeminiValidator.answer(result.text, sources) ?: localAnswer(question, sources)

            is GeminiApiResult.Failure -> localAnswer(question, sources)
        }
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
                        action = LocalReviewAnalyzer.makeSmallerText(sourceText),
                        metadata = AiRouteMetadata(
                            source = AiRouteSource.GeminiFailedLocalUsed,
                            cloudUsed = false,
                            error = geminiError(GeminiApiErrorKind.InvalidResponse),
                        ),
                    )
                }
            }

            is GeminiApiResult.Failure -> RoutedTinyAction(
                action = LocalReviewAnalyzer.makeSmallerText(sourceText),
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

    private fun CaptureAnalysis.routedLocal(): RoutedCaptureAnalysis =
        RoutedCaptureAnalysis(
            analysis = copy(analyzerSource = com.orbit.app.domain.analyzer.CaptureAnalyzerSource.Local),
            metadata = AiRouteMetadata(source = AiRouteSource.Local, cloudUsed = false),
        )

    private fun noDataAnswer(): SourceLinkedAnswer =
        SourceLinkedAnswer(
            answer = "No data found.",
            sourceItemIds = emptyList(),
            sourceItems = emptyList(),
            fromGemini = false,
        )

    private fun localAnswer(question: String, sources: List<AiSourceItem>): SourceLinkedAnswer {
        val top = sources.take(3)
        val answer = if (top.isEmpty()) {
            "No data found."
        } else {
            buildString {
                append("Based on local items, ")
                append(top.joinToString { it.title })
                append(".")
                if (question.contains("stuck", ignoreCase = true)) {
                    append(" Check the first waiting or open item.")
                }
            }
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
