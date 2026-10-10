package com.orbit.app

import android.app.Application
import com.orbit.app.data.export.LocalDataExporter
import com.orbit.app.data.export.LocalDataRestorer
import com.orbit.app.data.export.LocalReminderRestoreReconciler
import com.orbit.app.data.export.RoomLocalDataRestoreStore
import com.orbit.app.data.local.OrbitDatabase
import com.orbit.app.data.repository.AppSettingsRepository
import com.orbit.app.data.repository.BrainDumpRepository
import com.orbit.app.data.repository.AiCorrectionHistoryRepository
import com.orbit.app.data.repository.AiSuggestionHistoryRepository
import com.orbit.app.data.repository.CaptureRepository
import com.orbit.app.data.repository.CalendarRepository
import com.orbit.app.data.repository.DataStoreAppSettingsRepository
import com.orbit.app.data.repository.LearnedRuleRepository
import com.orbit.app.data.repository.LabelRepository
import com.orbit.app.data.repository.NoteRepository
import com.orbit.app.data.repository.PersonMemoryRepository
import com.orbit.app.data.repository.ProjectMemoryRepository
import com.orbit.app.data.repository.ReminderRepository
import com.orbit.app.data.repository.RoomAiCorrectionHistoryRepository
import com.orbit.app.data.repository.RoomAiSuggestionHistoryRepository
import com.orbit.app.data.repository.RoomBrainDumpRepository
import com.orbit.app.data.repository.RoomCaptureRepository
import com.orbit.app.data.repository.RoomCalendarRepository
import com.orbit.app.data.repository.RoomLearnedRuleRepository
import com.orbit.app.data.repository.RoomLabelRepository
import com.orbit.app.data.repository.RoomNoteRepository
import com.orbit.app.data.repository.RoomPersonMemoryRepository
import com.orbit.app.data.repository.RoomProjectMemoryRepository
import com.orbit.app.data.repository.RoomReminderRepository
import com.orbit.app.data.repository.RoomSpaceAliasMemoryRepository
import com.orbit.app.data.repository.RoomSpaceRepository
import com.orbit.app.data.repository.RoomTaskRepository
import com.orbit.app.data.repository.SpaceAliasMemoryRepository
import com.orbit.app.data.repository.SpaceRepository
import com.orbit.app.data.repository.TaskRepository
import com.orbit.app.domain.ai.LocalLearningProfileProvider
import com.orbit.app.domain.ai.OrbitAiRouter
import com.orbit.app.domain.analyzer.CaptureAnalyzer
import com.orbit.app.domain.analyzer.LocalRulesCaptureAnalyzer
import com.orbit.app.domain.analyzer.LocalRulesSituationAnalyzer
import com.orbit.app.domain.analyzer.SituationAnalyzer
import com.orbit.app.domain.usecase.ConfirmCaptureActionUseCase
import com.orbit.app.domain.usecase.BrainDumpActions
import com.orbit.app.domain.usecase.RoomCaptureFinalizationTransaction
import com.orbit.app.domain.usecase.RecordAiLearningEventUseCase
import com.orbit.app.domain.usecase.ProposeLearnedRuleUseCase
import com.orbit.app.integrations.gemini.GeminiApiClient
import com.orbit.app.integrations.gemini.HttpGeminiApiClient
import com.orbit.app.reminders.ReminderScheduler
import com.orbit.app.reminders.ReminderNotifications
import com.orbit.app.reminders.WorkManagerReminderScheduler
import com.orbit.app.security.AndroidKeystoreGeminiApiKeyStore
import com.orbit.app.security.GeminiApiKeyStore
import com.orbit.app.ui.localization.effectiveAppLocale
import com.orbit.app.domain.capture.CaptureInbox
import com.orbit.app.domain.capture.ThoughtSplitter
import com.orbit.app.domain.capture.CaptureResolution
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first

class OrbitApplication : Application() {
    lateinit var container: OrbitContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        container = OrbitContainer(this)
    }
}

class OrbitContainer(application: Application) {
    val applicationContext = application.applicationContext

    /** Work that must finish even when the user leaves a screen (for example capture analysis). */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: OrbitDatabase by lazy { OrbitDatabase.getInstance(application) }

    val captureRepository: CaptureRepository by lazy {
        RoomCaptureRepository(database.captureDao())
    }
    val brainDumpRepository: BrainDumpRepository by lazy {
        RoomBrainDumpRepository(database.brainDumpDao())
    }
    val spaceRepository: SpaceRepository by lazy {
        RoomSpaceRepository(database.spaceDao())
    }
    val labelRepository: LabelRepository by lazy {
        RoomLabelRepository(database.labelDao())
    }
    val noteRepository: NoteRepository by lazy {
        RoomNoteRepository(database.noteDao())
    }
    val taskRepository: TaskRepository by lazy {
        RoomTaskRepository(database.taskDao())
    }
    val appSettingsRepository: AppSettingsRepository by lazy {
        DataStoreAppSettingsRepository(application)
    }
    val captureAnalyzer: CaptureAnalyzer by lazy {
        LocalRulesCaptureAnalyzer(locale = { effectiveAppLocale(applicationContext) })
    }
    val situationAnalyzer: SituationAnalyzer by lazy {
        LocalRulesSituationAnalyzer(locale = { effectiveAppLocale(applicationContext) })
    }
    val geminiApiKeyStore: GeminiApiKeyStore by lazy {
        AndroidKeystoreGeminiApiKeyStore(application)
    }
    val geminiApiClient: GeminiApiClient by lazy {
        HttpGeminiApiClient()
    }
    val reminderScheduler: ReminderScheduler by lazy {
        WorkManagerReminderScheduler(application)
    }
    val reminderRepository: ReminderRepository by lazy {
        RoomReminderRepository(database.reminderDao(), reminderScheduler)
    }
    val calendarRepository: CalendarRepository by lazy {
        RoomCalendarRepository(
            noteDao = database.noteDao(),
            taskDao = database.taskDao(),
            reminderDao = database.reminderDao(),
        )
    }
    val aiSuggestionHistoryRepository: AiSuggestionHistoryRepository by lazy {
        RoomAiSuggestionHistoryRepository(database.aiSuggestionHistoryDao())
    }
    val aiCorrectionHistoryRepository: AiCorrectionHistoryRepository by lazy {
        RoomAiCorrectionHistoryRepository(database.aiCorrectionHistoryDao())
    }
    val learnedRuleRepository: LearnedRuleRepository by lazy {
        RoomLearnedRuleRepository(database.learnedRuleDao())
    }
    val personMemoryRepository: PersonMemoryRepository by lazy {
        RoomPersonMemoryRepository(database.personMemoryDao())
    }
    val projectMemoryRepository: ProjectMemoryRepository by lazy {
        RoomProjectMemoryRepository(database.projectMemoryDao())
    }
    val spaceAliasMemoryRepository: SpaceAliasMemoryRepository by lazy {
        RoomSpaceAliasMemoryRepository(database.spaceAliasMemoryDao())
    }
    val learningProfileProvider: LocalLearningProfileProvider by lazy {
        LocalLearningProfileProvider(
            learnedRuleRepository = learnedRuleRepository,
            personMemoryRepository = personMemoryRepository,
            projectMemoryRepository = projectMemoryRepository,
            spaceRepository = spaceRepository,
            spaceAliasMemoryRepository = spaceAliasMemoryRepository,
            correctionHistoryRepository = aiCorrectionHistoryRepository,
            isLearningEnabled = {
                appSettingsRepository.settings.first().let { settings ->
                    settings.enableLocalAiLearning && settings.shareLocalLearningWithGemini
                }
            },
        )
    }
    val aiRouter: OrbitAiRouter by lazy {
        OrbitAiRouter(
            localCaptureAnalyzer = captureAnalyzer,
            geminiApiClient = geminiApiClient,
            geminiApiKeyStore = geminiApiKeyStore,
            learningProfileProvider = learningProfileProvider,
            locale = { effectiveAppLocale(applicationContext) },
        )
    }
    val confirmCaptureAction: ConfirmCaptureActionUseCase by lazy {
        ConfirmCaptureActionUseCase(
            captureRepository = captureRepository,
            noteRepository = noteRepository,
            taskRepository = taskRepository,
            reminderRepository = reminderRepository,
            transaction = RoomCaptureFinalizationTransaction(database),
            labelRepository = labelRepository,
        )
    }
    val captureInbox: CaptureInbox by lazy {
        CaptureInbox(
            captureRepository = captureRepository,
            suggestionDao = database.captureSuggestionDao(),
            brainDumpRepository = brainDumpRepository,
            spaceRepository = spaceRepository,
            suggester = { rawText, allowedSpaces ->
                aiRouter.analyzeCapture(
                    rawText = rawText,
                    settings = appSettingsRepository.settings.first(),
                    allowedSpaces = allowedSpaces,
                ).analysis
            },
            scope = applicationScope,
        )
    }
    val captureResolution: CaptureResolution by lazy {
        CaptureResolution(
            captureRepository = captureRepository,
            noteRepository = noteRepository,
            taskRepository = taskRepository,
            reminderRepository = reminderRepository,
            spaceRepository = spaceRepository,
            suggestionDao = database.captureSuggestionDao(),
            confirmCaptureAction = confirmCaptureAction,
            transaction = RoomCaptureFinalizationTransaction(database),
        )
    }
    val thoughtSplitter: ThoughtSplitter by lazy {
        ThoughtSplitter(
            captureRepository = captureRepository,
            brainDumpRepository = brainDumpRepository,
            suggestionDao = database.captureSuggestionDao(),
            analyzer = captureAnalyzer,
            geminiParts = { text -> aiRouter.thoughtParts(text, appSettingsRepository.settings.first()) },
            geminiReady = {
                aiRouter.canSplitWithGemini(appSettingsRepository.settings.first()) && geminiApiKeyStore.hasKey()
            },
        )
    }
    val brainDumpActions: BrainDumpActions by lazy {
        BrainDumpActions(database, reminderScheduler)
    }
    val recordAiLearningEvent: RecordAiLearningEventUseCase by lazy {
        RecordAiLearningEventUseCase(
            suggestionHistoryRepository = aiSuggestionHistoryRepository,
            correctionHistoryRepository = aiCorrectionHistoryRepository,
            isLearningEnabled = {
                appSettingsRepository.settings.first().enableLocalAiLearning
            },
        )
    }
    val proposeLearnedRule: ProposeLearnedRuleUseCase by lazy {
        ProposeLearnedRuleUseCase(
            correctionHistoryRepository = aiCorrectionHistoryRepository,
            learnedRuleRepository = learnedRuleRepository,
            isLearningEnabled = { appSettingsRepository.settings.first().enableLocalAiLearning },
        )
    }
    private val localDataStore: RoomLocalDataRestoreStore by lazy {
        RoomLocalDataRestoreStore(database)
    }
    val localDataExporter: LocalDataExporter by lazy {
        LocalDataExporter(
            context = application,
            snapshotReader = localDataStore,
        )
    }
    val localDataRestorer: LocalDataRestorer by lazy {
        LocalDataRestorer(
            store = localDataStore,
            reminderReconciler = LocalReminderRestoreReconciler(
                scheduler = reminderScheduler,
                reminderDao = database.reminderDao(),
            ),
        )
    }
}
