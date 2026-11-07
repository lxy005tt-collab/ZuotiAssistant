package `fun`.kirari.hanako.app.navigation

import `fun`.kirari.hanako.feature.settings.presentation.ConnectionTestManager
import `fun`.kirari.hanako.feature.settings.presentation.ProviderRuntimeController
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaController
import `fun`.kirari.hanako.feature.settings.presentation.WebSearchQuotaState
import `fun`.kirari.hanako.feature.settings.presentation.SettingsEditorController
import `fun`.kirari.hanako.feature.settings.presentation.AppUpdateController
import `fun`.kirari.hanako.feature.settings.presentation.AppUpdateUiState

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.app.HanakoApplication
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.HistoryCommandResult
import `fun`.kirari.hanako.core.data.HistoryMarkerColor
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.data.QuestionCardArtifact
import `fun`.kirari.hanako.core.data.AssistantPreset
import `fun`.kirari.hanako.core.data.AutomationSettings
import `fun`.kirari.hanako.core.data.FloatingWindowSettings
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.platform.capture.ocr.LocalOcrManager
import `fun`.kirari.hanako.feature.history.presentation.HistoryWorkflowController
import `fun`.kirari.hanako.feature.history.application.QuestionCardExporter
import `fun`.kirari.hanako.feature.history.presentation.HistoryDetailUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "HanakoMainViewModel"
    private val container = (application as HanakoApplication).container
    private val repository: SettingsRepository = container.settingsRepository
    private val localOcrManager: LocalOcrManager = container.localOcrManager
    val settings: StateFlow<AppSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )
    private val historyWorkflowController = HistoryWorkflowController(
        scope = viewModelScope,
        settings = settings,
        solveOperations = container.workflow.operations,
        settingsRepository = repository,
        questionCardExporter = QuestionCardExporter(application, repository)
    )
    val historyDetailStates: StateFlow<Map<String, HistoryDetailUiState>> =
        historyWorkflowController.historyDetailStates
    val mergedHistory: StateFlow<List<ProcessingResult>> =
        historyWorkflowController.mergedHistory
    private val providerRuntimeController = ProviderRuntimeController(
        scope = viewModelScope,
        settings = settings,
        providerModelsApi = container.providerModelsApi
    )
    val connectionTestManager: ConnectionTestManager =
        providerRuntimeController.connectionTestManager
    private val settingsEditorController = SettingsEditorController(
        scope = viewModelScope,
        repository = repository
    )
    private val webSearchQuotaController = WebSearchQuotaController(
        scope = viewModelScope,
        settings = settings,
        tavilyUsageApi = container.tavilyUsageApi
    )
    val webSearchQuotaState: StateFlow<WebSearchQuotaState> =
        webSearchQuotaController.state
    private val appUpdateController = AppUpdateController(
        scope = viewModelScope,
        appUpdateApi = container.appUpdateApi
    )
    val appUpdateState: StateFlow<AppUpdateUiState> = appUpdateController.state
    init {
        syncLocalOcrInstallation()
        appUpdateController.checkOnceSilently()
    }

    fun updateProvider(provider: ModelProviderConfig) {
        settingsEditorController.updateProvider(provider)
    }

    fun addProvider() {
        settingsEditorController.addProvider()
    }

    fun selectProvider(providerId: String) {
        settingsEditorController.selectProvider(providerId)
    }

    fun deleteProvider(providerId: String) {
        settingsEditorController.deleteProvider(providerId)
    }

    fun updateAssistant(assistant: AssistantPreset) {
        settingsEditorController.updateAssistant(assistant)
    }

    fun addAssistant() {
        settingsEditorController.addAssistant()
    }

    fun selectAssistant(assistantId: String) = settingsEditorController.selectAssistant(assistantId)

    fun deleteAssistant(assistantId: String) {
        settingsEditorController.deleteAssistant(assistantId)
    }

    fun setRoute(route: ProcessingRoute) {
        settingsEditorController.setRoute(route)
    }

    fun setScreenCaptureMethod(method: ScreenCaptureMethod) {
        settingsEditorController.setScreenCaptureMethod(method)
    }

    fun updateFloatingWindowSettings(settings: FloatingWindowSettings) {
        settingsEditorController.updateFloatingWindowSettings(settings)
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        settingsEditorController.updateModelSelection(purpose, selection)

    fun syncLocalOcrInstallation() {
        viewModelScope.launch {
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation start")
            val status = withContext(Dispatchers.IO) { localOcrManager.installationStatus() }
            AppDebugLogStore.i("LocalOcrUi", "syncLocalOcrInstallation done installed=${status.installed}")
            repository.update { current ->
                current.copy(
                    localOcr = current.localOcr.copy(
                        installed = status.installed,
                        lastMessage = if (status.installed) "本地 ML Kit 已内置，可直接使用" else "本地 ML Kit 当前不可用"
                    )
                )
            }
        }
    }

    fun updateModelSelectionWithFavorite(
        purpose: ModelPurpose,
        selection: ModelSelection,
        favoriteModel: Boolean = false
    ) = settingsEditorController.updateModelSelectionWithFavorite(purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        settingsEditorController.toggleFavoriteModel(providerId, modelId)

    fun selectHistoryConversationModel(
        resultId: String,
        selection: ModelSelection,
        addToFavorites: Boolean = false
    ) {
        historyWorkflowController.selectConversationModel(resultId, selection)
        if (addToFavorites) {
            selection.providerId?.let { providerId ->
                settingsEditorController.addFavoriteModel(providerId, selection.model)
            }
        }
    }

    fun removeFavoriteModel(providerId: String, modelId: String) =
        settingsEditorController.removeFavoriteModel(providerId, modelId)

    fun updateAutomationSettings(transform: (AutomationSettings) -> AutomationSettings) {
        settingsEditorController.updateAutomationSettings(transform)
    }

    fun setReasoningEffort(effort: `fun`.kirari.llm.core.ReasoningEffort) {
        viewModelScope.launch { repository.update { it.copy(reasoningEffort = effort) } }
    }

    fun setTrustAllHttpsCertificates(enabled: Boolean) {
        settingsEditorController.setTrustAllHttpsCertificates(enabled)
    }

    fun updateWebSearchSettings(transform: (WebSearchSettings) -> WebSearchSettings) {
        settingsEditorController.updateWebSearchSettings(transform)
    }

    fun queryWebSearchQuota() {
        webSearchQuotaController.query()
    }

    fun resetWebSearchQuotaState() {
        webSearchQuotaController.reset()
    }

    fun clearHistory() {
        historyWorkflowController.clearHistory()
    }

    fun deleteHistoryItem(resultId: String) {
        historyWorkflowController.deleteHistoryItem(resultId)
    }

    fun regenerateHistoryResult(resultId: String) {
        historyWorkflowController.regenerateHistoryResult(resultId)
    }

    fun sendHistoryFollowUp(resultId: String, prompt: String, quotedFragments: List<QuotedFragment> = emptyList()) {
        historyWorkflowController.sendHistoryFollowUp(resultId, prompt, quotedFragments)
    }

    fun retryLatestHistoryFollowUp(resultId: String) {
        historyWorkflowController.retryLatestHistoryFollowUp(resultId)
    }

    fun createHistoryGroup(name: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.createGroup(name, onResult)

    fun renameHistoryGroup(id: String, name: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.renameGroup(id, name, onResult)

    fun deleteHistoryGroup(id: String, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.deleteGroup(id, onResult)

    fun setHistoryGroups(recordIds: Set<String>, groupIds: Set<String>, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.setGroups(recordIds, groupIds, onResult)

    fun setHistoryMarkerColor(recordIds: Set<String>, color: HistoryMarkerColor?, onResult: (HistoryCommandResult) -> Unit = {}) =
        historyWorkflowController.setMarkerColor(recordIds, color, onResult)

    fun createQuestionCard(resultId: String, onComplete: (QuestionCardArtifact?) -> Unit = {}) =
        historyWorkflowController.createQuestionCard(resultId, onComplete)

    fun testProviderConnection(provider: ModelProviderConfig) {
        providerRuntimeController.testProviderConnection(provider)
    }

    fun resetConnectionTest(providerId: String) {
        providerRuntimeController.resetConnectionTest(providerId)
    }

    fun clearDebugLogs() {
        AppDebugLogStore.clear()
    }

    fun showUpdateDialog() {
        appUpdateController.showDialog()
    }

    fun dismissUpdateDialog() {
        appUpdateController.dismissDialog()
    }

}
