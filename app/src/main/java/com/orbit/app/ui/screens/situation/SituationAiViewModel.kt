package com.orbit.app.ui.screens.situation

import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.orbit.app.OrbitContainer
import com.orbit.app.domain.ai.LocalAiRetriever
import com.orbit.app.domain.ai.SituationSourceSummary
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SituationPanel {
    NextAction,
    OpenLoops,
    TinyPlan,
    ClearNoise,
}

data class SituationAiUiState(
    val isLoading: Boolean = true,
    val analysis: SituationAnalysis? = null,
    val sourceSummary: SituationSourceSummary? = null,
    val selectedPanel: SituationPanel? = null,
    val askQuery: String = "",
    val askAnswer: SourceLinkedAnswer? = null,
    val isAsking: Boolean = false,
    val mondayConfigured: Boolean = false,
)

class SituationAiViewModel(
    private val container: OrbitContainer,
    analyzer: SituationAnalyzer = container.situationAnalyzer,
    private val retriever: LocalAiRetriever = LocalAiRetriever(),
) : ViewModel() {
    private val selectedPanel = MutableStateFlow<SituationPanel?>(null)
    private val askQuery = MutableStateFlow("")
    private val askAnswer = MutableStateFlow<SourceLinkedAnswer?>(null)
    private val isAsking = MutableStateFlow(false)

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
    ) { corpus, settings ->
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
            ),
        )
        SituationContext(
            analysis = analysis,
            corpus = corpus,
            settings = settings,
        )
    }

    private val sourceSummary = context.map { data ->
        val sources = retriever.recentContext(data.corpus, limit = 8)
        container.aiRouter.summarizeSituation(
            sources = sources,
            settings = data.settings,
            localSummary = data.analysis.toSourceSummary(sources),
        )
    }

    private val displayState = combine(
        context,
        sourceSummary,
    ) { data, summary ->
        DisplayState(data.analysis, summary)
    }

    private val askState = combine(
        askQuery,
        askAnswer,
        isAsking,
    ) { query, answer, asking ->
        AskState(query, answer, asking)
    }

    val uiState = combine(
        displayState,
        selectedPanel,
        askState,
    ) { display, panel, ask ->
        SituationAiUiState(
            isLoading = false,
            analysis = display.analysis,
            sourceSummary = display.summary,
            selectedPanel = panel,
            askQuery = ask.query,
            askAnswer = ask.answer,
            isAsking = ask.isAsking,
            // Monday.com has no configured integration in this local MVP phase.
            mondayConfigured = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SituationAiUiState(),
    )

    fun show(panel: SituationPanel) {
        selectedPanel.value = panel
    }

    fun clearPanel() {
        selectedPanel.value = null
    }

    fun updateAskQuery(value: String) {
        askQuery.value = value.take(MaxAskQueryLength)
    }

    fun askLuma() {
        val question = askQuery.value.trim()
        if (question.length < 2 || isAsking.value) return
        viewModelScope.launch {
            isAsking.value = true
            try {
                val data = context.first()
                val sources = retriever.retrieve(question, data.corpus, limit = 10)
                askAnswer.value = container.aiRouter.askLuma(
                    question = question,
                    sources = sources,
                    settings = data.settings,
                )
            } finally {
                isAsking.value = false
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
        val settings: com.orbit.app.domain.model.AppSettings,
    )

    private data class DisplayState(
        val analysis: SituationAnalysis,
        val summary: SituationSourceSummary,
    )

    private data class AskState(
        val query: String,
        val answer: SourceLinkedAnswer?,
        val isAsking: Boolean,
    )

    private companion object {
        const val MaxAskQueryLength = 140
    }
}

private fun SituationAnalysis.toSourceSummary(
    sources: List<com.orbit.app.domain.ai.AiSourceItem>,
): SituationSourceSummary =
    SituationSourceSummary(
        rightNow = whereYouAre,
        whatMatters = whatMatters.firstOrNull() ?: "No urgent pattern stands out.",
        stuck = whatIsStuck.firstOrNull() ?: "Nothing specific looks stuck.",
        nextTinyStep = nextAction,
        sourceItemIds = sources.take(3).map { it.sourceId },
        sourceItems = sources.take(3),
        fromGemini = false,
    )
