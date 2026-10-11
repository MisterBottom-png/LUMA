package com.orbit.app.ui.screens.situation

import android.content.Context
import android.content.res.Configuration
import android.text.format.DateFormat
import com.orbit.app.R
import com.orbit.app.domain.ai.AskLumaAnswerKind
import com.orbit.app.domain.ai.AskLumaPromptAnswer
import com.orbit.app.domain.ai.AskLumaPromptAnswerer
import com.orbit.app.domain.ai.AskLumaQuestion
import com.orbit.app.domain.analyzer.LocalReviewAnalyzer
import com.orbit.app.ui.localization.effectiveAppLocale
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.ai.LocalAiRetriever
import com.orbit.app.domain.ai.SourceLinkedAnswer
import com.orbit.app.domain.analyzer.SituationAnalysis
import com.orbit.app.domain.analyzer.SituationAnalyzer
import com.orbit.app.domain.analyzer.SituationSnapshot
import com.orbit.app.domain.model.uses24HourClock
import com.orbit.app.domain.search.SearchCorpus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SituationAiUiState(
    val isLoading: Boolean = true,
    val analysis: SituationAnalysis? = null,
    val askQuery: String = "",
    val askAnswer: SourceLinkedAnswer? = null,
    val isAsking: Boolean = false,
)

class SituationAiViewModel(
    private val container: OrbitContainer,
    analyzer: SituationAnalyzer = container.situationAnalyzer,
    private val retriever: LocalAiRetriever = LocalAiRetriever(),
) : ViewModel() {
    private val askState = MutableStateFlow(AskState())

    private val corpus = combine(
        container.captureRepository.observeAll(),
        container.noteRepository.observeAll(),
        container.taskRepository.observeAll(),
        container.reminderRepository.observeAll(),
        container.spaceRepository.observeAll(),
    ) { captures, notes, tasks, reminders, spaces ->
        SearchCorpus(
            captures = captures,
            notes = notes,
            tasks = tasks,
            reminders = reminders,
            spaces = spaces,
        )
    }

    private val context = combine(
        corpus,
        container.appSettingsRepository.settings,
        localMinuteTicker(),
    ) { corpus, settings, now ->
        val analysis = analyzer.analyze(
            snapshot = SituationSnapshot(
                captures = corpus.captures,
                notes = corpus.notes,
                tasks = corpus.tasks,
                reminders = corpus.reminders,
                staleLoopDays = settings.staleLoopDays,
                use24HourClock = settings.timeFormatMode.uses24HourClock(
                    DateFormat.is24HourFormat(container.applicationContext),
                ),
                now = now,
            ),
        )
        SituationContext(
            analysis = analysis,
            corpus = corpus,
            now = now,
        )
    }

    val uiState = combine(
        context,
        askState,
    ) { data, ask ->
        SituationAiUiState(
            isLoading = false,
            analysis = data.analysis,
            askQuery = ask.query,
            // The answer stays until the question is edited (not just until the next minute).
            askAnswer = ask.answer,
            isAsking = ask.isAsking,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SituationAiUiState(),
    )

    fun updateAskQuery(value: String) {
        askState.value = askState.value.withQuery(value.take(MaxAskQueryLength))
    }

    fun askLuma() {
        val submission = askState.value.beginSubmission() ?: return
        askState.value = submission.loadingState
        viewModelScope.launch {
            try {
                val answer = answerOrCalmFailure(::failedAnswer) {
                    val data = context.first()
                    val sources = retriever.retrieve(submission.question, data.corpus, limit = 10, now = data.now)
                    container.aiRouter.askLuma(question = submission.question, sources = sources)
                }
                askState.value = askState.value.completeSubmission(submission.question, answer)
            } finally {
                askState.value = askState.value.finishSubmission(submission.question)
            }
        }
    }

    /** Answers one of Review's Ask LUMA questions from local items; "nothing needed" is valid. */
    fun askQuestion(question: AskLumaQuestion) {
        viewModelScope.launch {
            val localized = localizedContext()
            val label = localized.getString(question.labelRes())
            val answer = answerOrCalmFailure(::failedAnswer) {
                val data = context.first()
                val result = AskLumaPromptAnswerer.answer(question, data.corpus, data.now)
                SourceLinkedAnswer(
                    answer = result.toText(localized),
                    sourceItemIds = result.sources.map { it.sourceId },
                    sourceItems = result.sources,
                    fromGemini = false,
                )
            }
            val submission = AskState(query = label).beginSubmission() ?: return@launch
            askState.value = submission.loadingState.completeSubmission(submission.question, answer)
        }
    }

    private fun failedAnswer() = SourceLinkedAnswer(
        answer = localizedContext().getString(R.string.ask_answer_failed),
        sourceItemIds = emptyList(),
        sourceItems = emptyList(),
        fromGemini = false,
    )

    private fun localizedContext(): Context {
        val base = container.applicationContext
        return base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(effectiveAppLocale(base)) },
        )
    }

    class Factory(private val container: OrbitContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SituationAiViewModel::class.java))
            return SituationAiViewModel(container) as T
        }
    }

    private data class SituationContext(
        val analysis: SituationAnalysis,
        val corpus: SearchCorpus,
        val now: Long,
    )

    companion object {
        const val MaxAskQueryLength = 140
    }
}

/**
 * Runs [answer]; a failure becomes the calm [failure] answer instead of a crash or an
 * endless spinner. Cancellation (the sheet closing) is passed on.
 */
internal suspend fun answerOrCalmFailure(
    failure: () -> SourceLinkedAnswer,
    answer: suspend () -> SourceLinkedAnswer,
): SourceLinkedAnswer = try {
    answer()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    failure()
}

private fun localMinuteTicker() = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay((MinuteMillis - (now % MinuteMillis)).coerceAtLeast(1L))
    }
}

private const val MinuteMillis = 60_000L

internal data class AskState(
    val query: String = "",
    val answer: SourceLinkedAnswer? = null,
    val isAsking: Boolean = false,
    private val answeredQuestionKey: String? = null,
    private val activeQuestionKey: String? = null,
) {
    fun withQuery(value: String): AskState {
        val answerStillCurrent = answer != null && answeredQuestionKey == value.askQuestionKey()
        return copy(
            query = value,
            answer = answer.takeIf { answerStillCurrent },
            answeredQuestionKey = answeredQuestionKey.takeIf { answerStillCurrent },
        )
    }

    fun beginSubmission(): AskSubmission? {
        val question = query.trim()
        if (question.length < 2 || isAsking) return null
        return AskSubmission(
            question = question,
            loadingState = copy(
                answer = null,
                isAsking = true,
                answeredQuestionKey = null,
                activeQuestionKey = question.askQuestionKey(),
            ),
        )
    }

    fun completeSubmission(question: String, result: SourceLinkedAnswer): AskState {
        val questionKey = question.askQuestionKey()
        if (activeQuestionKey != questionKey) return this
        val answerStillCurrent = query.askQuestionKey() == questionKey
        return copy(
            answer = result.takeIf { answerStillCurrent },
            isAsking = false,
            answeredQuestionKey = questionKey.takeIf { answerStillCurrent },
            activeQuestionKey = null,
        )
    }

    fun finishSubmission(question: String): AskState =
        if (activeQuestionKey == question.askQuestionKey()) {
            copy(isAsking = false, activeQuestionKey = null)
        } else {
            this
        }
}

internal data class AskSubmission(
    val question: String,
    val loadingState: AskState,
)

private fun String.askQuestionKey(): String = trim().replace(Regex("\\s+"), " ")

private fun AskLumaQuestion.labelRes(): Int = when (this) {
    AskLumaQuestion.WhatNow -> R.string.review_ask_now
    AskLumaQuestion.WhatCanWait -> R.string.review_ask_can_wait
    AskLumaQuestion.DependsOnOthers -> R.string.review_ask_depends
    AskLumaQuestion.SmallestStep -> R.string.review_ask_smallest
    AskLumaQuestion.AnythingUrgent -> R.string.review_ask_urgent
}

internal fun AskLumaPromptAnswer.toText(context: Context): String {
    val titles = items.joinToString(", ") { it.title }
    return when (kind) {
        AskLumaAnswerKind.StartWith -> context.getString(R.string.ask_answer_start_with, titles)
        AskLumaAnswerKind.SortThoughts ->
            context.resources.getQuantityString(R.plurals.ask_answer_sort_thoughts, count, count)
        AskLumaAnswerKind.FromEarlier ->
            context.resources.getQuantityString(R.plurals.ask_answer_from_earlier, count, count)
        AskLumaAnswerKind.NothingNeeded -> context.getString(R.string.ask_answer_nothing_needed)
        AskLumaAnswerKind.CanWait -> listOfNotNull(
            titles.takeIf { items.isNotEmpty() }?.let { context.getString(R.string.ask_answer_can_wait, it) },
            undated.takeIf { it.isNotEmpty() }
                ?.let { context.getString(R.string.ask_answer_no_date_set, it.joinToString(", ") { item -> item.title }) },
        ).joinToString(" ")
        AskLumaAnswerKind.NothingCanWait -> context.getString(R.string.ask_answer_nothing_can_wait)
        AskLumaAnswerKind.WaitingOnOthers -> context.getString(R.string.ask_answer_waiting, titles)
        AskLumaAnswerKind.NothingWaiting -> context.getString(R.string.ask_answer_nothing_waiting)
        AskLumaAnswerKind.SmallestStep -> context.getString(
            R.string.ask_answer_smallest_step,
            LocalReviewAnalyzer.makeSmallerText(items.first().title, effectiveAppLocale(context)),
        )
        AskLumaAnswerKind.NothingToBreakDown -> context.getString(R.string.ask_answer_nothing_to_break)
        AskLumaAnswerKind.Urgent -> context.getString(R.string.ask_answer_urgent, titles)
        AskLumaAnswerKind.NothingUrgent -> context.getString(R.string.ask_answer_nothing_urgent)
    }
}
