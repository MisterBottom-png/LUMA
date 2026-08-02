package com.orbit.app.ui.screens.situation

import android.text.format.DateFormat
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
            askAnswer = ask.answerFor(data.dataKey),
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
                val data = context.first()
                val sources = retriever.retrieve(submission.question, data.corpus, limit = 10, now = data.now)
                val answer = container.aiRouter.askLuma(
                    question = submission.question,
                    sources = sources,
                )
                askState.value = askState.value.completeSubmission(submission.question, answer, data.dataKey)
            } finally {
                askState.value = askState.value.finishSubmission(submission.question)
            }
        }
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
    ) {
        val dataKey = SituationDataKey(corpus = corpus, minuteBucket = now / MinuteMillis)
    }

    private data class SituationDataKey(val corpus: SearchCorpus, val minuteBucket: Long)

    private companion object {
        const val MaxAskQueryLength = 140
    }
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
    private val answeredDataKey: Any? = null,
    private val activeQuestionKey: String? = null,
) {
    fun withQuery(value: String): AskState {
        val answerStillCurrent = answer != null && answeredQuestionKey == value.askQuestionKey()
        return copy(
            query = value,
            answer = answer.takeIf { answerStillCurrent },
            answeredQuestionKey = answeredQuestionKey.takeIf { answerStillCurrent },
            answeredDataKey = answeredDataKey.takeIf { answerStillCurrent },
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
                answeredDataKey = null,
                activeQuestionKey = question.askQuestionKey(),
            ),
        )
    }

    fun completeSubmission(question: String, result: SourceLinkedAnswer, dataKey: Any? = null): AskState {
        val questionKey = question.askQuestionKey()
        if (activeQuestionKey != questionKey) return this
        val answerStillCurrent = query.askQuestionKey() == questionKey
        return copy(
            answer = result.takeIf { answerStillCurrent },
            isAsking = false,
            answeredQuestionKey = questionKey.takeIf { answerStillCurrent },
            answeredDataKey = dataKey.takeIf { answerStillCurrent },
            activeQuestionKey = null,
        )
    }

    fun answerFor(dataKey: Any): SourceLinkedAnswer? =
        answer.takeIf { answeredDataKey == null || answeredDataKey == dataKey }

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
