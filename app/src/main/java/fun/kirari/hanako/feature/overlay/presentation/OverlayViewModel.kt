package `fun`.kirari.hanako.feature.overlay.presentation

import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.feature.overlay.state.isSelectingRegion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import `fun`.kirari.hanako.feature.overlay.service.OverlayService
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.ModelSelection
import `fun`.kirari.hanako.core.data.SettingsRepository
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.core.network.ProviderModelsApi
import `fun`.kirari.hanako.solve.application.SolveOperations
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.map
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import `fun`.kirari.hanako.core.model.saveToHistoryFile
import `fun`.kirari.hanako.feature.overlay.state.SheetAnimationDurationMs
import `fun`.kirari.hanako.feature.overlay.state.ScreenshotRetry

internal class OverlayViewModel(
    private val appContext: Context,
    private val repository: SettingsRepository,
    private val solveOperations: SolveOperations,
    val providerModelsApi: ProviderModelsApi,
    private val mainActivityIntent: (Context) -> Intent
) : ViewModel() {
    private val tag = "HanakoOverlayVM"
    
    // 新的状态机
    val bubbleStateMachine = BubbleStateMachine()
    
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()
    private var historyReturnMode = OverlaySheetMode.HOME
    private val multiPageCaptureController = MultiPageCaptureController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        bubbleStateMachine = bubbleStateMachine
    )
    private val autoProcessingController = AutoProcessingController(
        appContext = appContext,
        scope = viewModelScope,
        uiState = _uiState,
        solveOperations = solveOperations,
        bubbleStateMachine = bubbleStateMachine
    )
    private val answerController = OverlayAnswerController(
        scope = viewModelScope,
        uiState = _uiState,
        solveOperations = solveOperations,
        bubbleStateMachine = bubbleStateMachine,
        saveScreenshots = { historyId, bitmaps ->
            withContext(Dispatchers.IO) {
                val turnId = java.util.UUID.randomUUID().toString()
                bitmaps.mapIndexed { index, bitmap ->
                    bitmap.saveToHistoryFile(appContext, "${historyId}_${turnId}_$index")
                }
            }
        }
    )
    private val settingsController = OverlaySettingsController(
        scope = viewModelScope,
        repository = repository,
        uiState = _uiState
    )
    private val interactionController = OverlayInteractionController(
        uiState = _uiState,
        bubbleStateMachine = bubbleStateMachine,
        openCropSheet = ::openCropSheet,
        capturePage = ::capturePage,
        sendCaptures = ::sendCaptures,
        exitMultiPageCaptureMode = ::exitMultiPageCaptureMode,
        cancelActiveProcessing = {
            if (_uiState.value.launchMode == CaptureLaunchMode.NORMAL) answerController.cancel()
            else autoProcessingController.cancelActiveProcessing()
        },
        enterMultiPageCaptureMode = ::enterMultiPageCaptureMode,
        isBubbleMenuEnabled = { _uiState.value.settings.automation.bubbleMenuEnabled },
        selectRegion = ::captureNewQuestion,
        toggleWebSearch = ::toggleWebSearch,
        exitOverlay = {
            answerController.cancel()
            autoProcessingController.cancelActiveProcessing()
            ScreenCaptureManager.stop(appContext, _uiState.value.settings.screenCaptureMethod)
            appContext.stopService(Intent(appContext, OverlayService::class.java))
        },
        openAnswer = ::showCurrentAnswer,
        newConversation = ::startNewConversation
    )

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            solveOperations.observeHistory(repository.settings.map { it.history }).collect { history ->
                _uiState.update { it.copy(history = history.results, busyHistoryIds = history.activeTasks.keys) }
            }
        }
        
        // 监听状态机变化，同步到 UI 状态
        viewModelScope.launch {
            bubbleStateMachine.state.collect { bubbleState ->
                _uiState.update { it.copy(bubbleState = bubbleState) }
            }
        }
    }

    fun setLaunchMode(mode: CaptureLaunchMode) {
        AppDebugLogStore.i(tag, "setLaunchMode mode=$mode")
        _uiState.update { state ->
            state.copy(
                launchMode = mode,
                autoRunState = if (mode == CaptureLaunchMode.NORMAL) AutoRunState.IDLE else state.autoRunState,
                autoCopiedLabel = if (mode == CaptureLaunchMode.NORMAL) null else state.autoCopiedLabel,
                pendingVibrationLetters = if (mode == CaptureLaunchMode.NORMAL) null else state.pendingVibrationLetters,
                error = null
            )
        }
        // 普通模式重置状态机
        if (mode == CaptureLaunchMode.NORMAL) {
            bubbleStateMachine.forceState(BubbleState.Idle)
        }
    }

    fun openCropSheet() {
        AppDebugLogStore.i(tag, "openCropSheet launchMode=${_uiState.value.launchMode}")
        if (_uiState.value.launchMode == CaptureLaunchMode.AUTO) {
            AppDebugLogStore.i(tag, "openCropSheet delegated to processFullScreen for auto mode")
            processFullScreen()
            return
        }
        val state = _uiState.value
        if (state.capturing) return
        _uiState.update { it.copy(sheetVisible = !state.sheetVisible) }
    }

    fun toggleCardSize() {
        _uiState.update { it.copy(cardExpanded = !it.cardExpanded) }
    }

    fun setSettingsMenuVisible(visible: Boolean) {
        _uiState.update { it.copy(settingsMenuVisible = visible) }
    }

    fun toggleFloatingSettings() {
        _uiState.update { it.copy(settingsMenuVisible = !it.settingsMenuVisible, sheetVisible = !it.settingsMenuVisible) }
    }

    fun showCurrentAnswer() {
        _uiState.update {
            it.copy(sheetVisible = true, settingsMenuVisible = false,
                sheetMode = OverlaySheetMode.RESULT)
        }
    }

    fun startNewConversation() {
        if (_uiState.value.working || _uiState.value.capturing) return
        answerController.startNewConversation()
        android.widget.Toast.makeText(appContext, "已开始新对话，下次截图不会带上前文", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun captureAndSolveFullScreen(keepOverlayVisible: Boolean = false) {
        if (_uiState.value.working || _uiState.value.capturing) return
        _uiState.update {
            it.copy(capturing = true, settingsMenuVisible = false, sheetVisible = keepOverlayVisible,
                sheetMode = OverlaySheetMode.RESULT, screenshot = null,
                followUpDraft = "", sendingFollowUp = false, waitingForScreenshotAnswer = true, screenshotRetry = null,
                error = null, launchMode = CaptureLaunchMode.NORMAL, autoRunState = AutoRunState.IDLE)
        }
        AppDebugLogStore.i(tag, "capture full screen method=${_uiState.value.settings.screenCaptureMethod} visibleOverlay=$keepOverlayVisible")
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    `fun`.kirari.hanako.platform.capture.ScreenCaptureManager.captureLatestBitmap(
                        appContext, _uiState.value.settings.screenCaptureMethod, afterOverlayChange = true)
                }
                _uiState.update { it.copy(capturing = false, screenshot = bitmap) }
                answerController.process(bitmap)
            } catch (error: CancellationException) {
                _uiState.update { it.copy(capturing = false, waitingForScreenshotAnswer = false) }
                throw error
            } catch (error: Exception) {
                _uiState.update { it.copy(capturing = false, waitingForScreenshotAnswer = false,
                    screenshotRetry = ScreenshotRetry.FULL_SCREEN,
                    error = error.message ?: "截屏失败，请检查截屏授权",
                    sheetVisible = true, sheetMode = OverlaySheetMode.RESULT) }
            }
        }
    }

    fun captureNewQuestion() {
        if (_uiState.value.working || _uiState.value.capturing) return
        _uiState.update {
            it.copy(sheetVisible = false, settingsMenuVisible = false, capturing = true,
                error = null, screenshotRetry = null, launchMode = CaptureLaunchMode.NORMAL, autoRunState = AutoRunState.IDLE)
        }
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    `fun`.kirari.hanako.platform.capture.ScreenCaptureManager.captureLatestBitmap(
                        appContext, _uiState.value.settings.screenCaptureMethod, afterOverlayChange = true)
                }
                AppDebugLogStore.i(tag, "region capture success width=${bitmap.width} height=${bitmap.height}")
                _uiState.update {
                    it.copy(
                        screenshot = bitmap,
                        error = null,
                        capturing = false,
                        sheetVisible = true,
                        sheetMode = OverlaySheetMode.CROP,
                        cardExpanded = false,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            } catch (error: CancellationException) {
                _uiState.update { it.copy(capturing = false) }
                throw error
            } catch (error: Exception) {
                reportRegionSelectionFailure(error)
            }
        }
    }

    fun cancelRegionSelection() {
        _uiState.update {
            it.copy(screenshot = it.selectedBitmap, sheetVisible = false,
                sheetMode = OverlaySheetMode.RESULT, settingsMenuVisible = false)
        }
    }

    fun confirmRegionSelection(bitmap: Bitmap) {
        if (!_uiState.value.isSelectingRegion() || _uiState.value.working) return
        answerController.process(bitmap)
        _uiState.update { it.copy(screenshot = bitmap) }
    }

    fun reportRegionSelectionFailure(error: Throwable) {
        AppDebugLogStore.e(tag, "region selection failed", error)
        _uiState.update {
            it.copy(error = error.message ?: "无法打开区域截图，请检查截图授权", capturing = false,
                screenshot = null, waitingForScreenshotAnswer = false, screenshotRetry = ScreenshotRetry.REGION,
                sheetVisible = true, settingsMenuVisible = false, sheetMode = OverlaySheetMode.RESULT)
        }
    }

    fun processFullScreen() {
        autoProcessingController.processFullScreen()
    }

    fun process(bitmap: Bitmap) {
        answerController.process(bitmap)
    }

    fun process(bitmaps: List<Bitmap>) {
        answerController.process(bitmaps)
    }

    fun regenerateCurrentResult() {
        val state = _uiState.value
        if (state.working || state.capturing || state.result?.id in state.busyHistoryIds) return
        state.screenshotRetry?.let { retry ->
            if (retry == ScreenshotRetry.FULL_SCREEN) captureAndSolveFullScreen() else captureNewQuestion()
            return
        }
        if (state.retryCapturePending) {
            answerController.retryCapture()
            return
        }
        val result = state.result
        if (result == null) {
            state.selectedBitmap?.let(answerController::process) ?: captureAndSolveFullScreen()
        } else if (result.followUpTurns.isNotEmpty()) {
            answerController.sendFollowUp(retry = true)
        } else if (result.automationAction != null) {
            // Retrying an old conversation must not silently erase its follow-ups.
            val bitmaps = result.loadHistoryBitmaps()
            if (bitmaps.isEmpty()) {
                _uiState.update { it.copy(error = "找不到原始截图，无法重试") }
            } else {
                answerController.process(bitmaps)
            }
        } else {
            answerController.regenerateCurrentResult()
        }
    }

    fun updateFollowUpDraft(text: String) {
        _uiState.update { it.copy(followUpDraft = text) }
    }

    fun sendFollowUp() = answerController.sendFollowUp()

    fun retryFollowUp() = regenerateCurrentResult()

    fun cancelAnswer() = answerController.cancel()

    fun openHistory() {
        if (_uiState.value.working || _uiState.value.capturing) return
        if (_uiState.value.sheetMode != OverlaySheetMode.HISTORY) historyReturnMode = _uiState.value.sheetMode
        _uiState.update { it.copy(sheetMode = OverlaySheetMode.HISTORY, sheetVisible = true) }
    }

    fun leaveHistory() {
        _uiState.update { it.copy(sheetMode = historyReturnMode) }
    }

    fun openHistoryResult(id: String) {
        val state = _uiState.value
        if (state.working || id in state.busyHistoryIds) return
        val result = state.history.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(result = result, selectedBitmap = null, liveOcrText = result.extractedText,
                liveAnswerText = result.answer, sheetMode = OverlaySheetMode.RESULT,
                followUpDraft = "", sendingFollowUp = false, error = null)
        }
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { result.loadHistoryBitmaps().firstOrNull() }
            _uiState.update { if (it.result?.id == id) it.copy(selectedBitmap = bitmap) else it }
        }
    }

    fun handlePanelBack() {
        if (_uiState.value.sheetMode == OverlaySheetMode.HISTORY) leaveHistory() else closeSheet()
    }

    fun closeSheet() {
        _uiState.update { it.copy(sheetVisible = false, settingsMenuVisible = false) }
    }

    fun consumeAutoCompletedState() {
        interactionController.consumeAutoCompletedState()
    }

    fun consumePendingVibrationLetters() {
        interactionController.consumePendingVibrationLetters()
    }

    fun presentAutomationEffect(result: `fun`.kirari.hanako.core.model.ProcessingResult) {
        autoProcessingController.presentAutomationEffect(result)
    }

    fun onBubbleTappedAfterLettersShown() {
        interactionController.onBubbleTappedAfterLettersShown()
    }

    // 多页截图相关方法
    
    /**
     * 进入多页截图模式
     */
    fun enterMultiPageCaptureMode() {
        if (_uiState.value.working || _uiState.value.capturing) return
        multiPageCaptureController.enter()
    }

    /**
     * 截图一次
     */
    fun capturePage() {
        multiPageCaptureController.capturePage()
    }

    /**
     * 发送截图给 AI
     */
    fun sendCaptures() {
        AppDebugLogStore.i(tag, "sendCaptures called state=${bubbleStateMachine.currentState::class.simpleName}")
        if (!multiPageCaptureController.canSendCaptures()) {
            AppDebugLogStore.i(tag, "sendCaptures called but cannot send")
            return
        }
        
        val bitmaps = multiPageCaptureController.capturedBitmaps()
        AppDebugLogStore.i(tag, "sendCaptures count=${bitmaps.size}")
        
        if (bitmaps.isEmpty()) {
            AppDebugLogStore.i(tag, "sendCaptures no bitmaps available")
            return
        }
        
        multiPageCaptureController.markSendCaptures()
        AppDebugLogStore.i(tag, "sendCaptures dispatched SendCaptures, new state=${bubbleStateMachine.currentState::class.simpleName}")
        if (_uiState.value.launchMode == CaptureLaunchMode.NORMAL) {
            answerController.process(bitmaps)
        } else {
            autoProcessingController.processBitmaps(bitmaps)
        }
    }

    /**
     * 退出多页截图模式
     */
    fun exitMultiPageCaptureMode() {
        multiPageCaptureController.exit()
    }

    /**
     * 处理单击事件（根据当前状态决定行为）
     */
    fun handleSingleTap() {
        interactionController.handleSingleTap()
    }

    /**
     * 处理长按事件
     * - 多图模式下：发送已截图片
     * - Idle/ShowingLetters/Copied/Error：进入多图截图模式
     * - Processing 等：展开扇形菜单
     */
    fun handleLongPress(anchorX: Int = 0, anchorY: Int = 0) {
        interactionController.handleLongPress(anchorX, anchorY)
    }

    /**
     * 处理双击事件
     * - 多图模式：退出多图
     * - Processing：取消处理
     * - Idle/ShowingLetters/Copied/Error：展开扇形菜单
     */
    fun handleDoubleTap(anchorX: Int = 0, anchorY: Int = 0) {
        interactionController.handleDoubleTap(anchorX, anchorY)
    }

    /**
     * 菜单关闭后的回调
     */
    fun onMenuDismissed() {
        interactionController.onMenuDismissed()
    }

    /**
     * 处理菜单项点击
     */
    fun handleMenuSelect(item: BubbleMenuItem) {
        interactionController.handleMenuSelect(item)
    }

    fun toggleWebSearch() {
        settingsController.toggleWebSearch()
    }

    fun setReasoningEffort(effort: `fun`.kirari.llm.core.ReasoningEffort) {
        viewModelScope.launch { repository.update { it.copy(reasoningEffort = effort) } }
    }

    fun selectAssistant(assistantId: String) = settingsController.selectAssistant(assistantId)

    fun selectPreviousAssistant() {
        settingsController.selectPreviousAssistant()
    }

    fun selectNextAssistant() {
        settingsController.selectNextAssistant()
    }

    fun updateModelSelection(purpose: ModelPurpose, selection: ModelSelection) =
        settingsController.updateModelSelection(purpose, selection)

    fun updateModelSelectionWithFavorite(purpose: ModelPurpose, selection: ModelSelection, favoriteModel: Boolean = false) =
        settingsController.updateModelSelectionWithFavorite(purpose, selection, favoriteModel)

    fun toggleFavoriteModel(providerId: String, modelId: String) =
        settingsController.toggleFavoriteModel(providerId, modelId)

    fun toggleProcessingRoute() {
        settingsController.toggleProcessingRoute()
    }

    companion object {
        fun factory(
            appContext: Context,
            dependencies: `fun`.kirari.hanako.feature.overlay.OverlayDependencies
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return OverlayViewModel(
                        appContext = appContext,
                        repository = dependencies.settingsRepository,
                        solveOperations = dependencies.solveOperations,
                        providerModelsApi = dependencies.providerModelsApi,
                        mainActivityIntent = dependencies.mainActivityIntent
                    ) as T
                }
            }
        }
    }
}
