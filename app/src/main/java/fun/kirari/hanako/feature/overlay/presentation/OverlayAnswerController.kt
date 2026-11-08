package `fun`.kirari.hanako.feature.overlay.presentation

import android.graphics.Bitmap
import `fun`.kirari.hanako.core.debug.AppDebugLogStore
import `fun`.kirari.hanako.feature.overlay.state.AutoRunState
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.feature.overlay.state.BubbleStateMachine
import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.solve.application.SolveOperations
import `fun`.kirari.hanako.solve.application.SolveTaskHandle
import `fun`.kirari.hanako.solve.application.SolveTaskSnapshot
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.solve.model.WorkflowTaskStatus
import `fun`.kirari.hanako.solve.model.WorkflowTaskKind
import `fun`.kirari.hanako.core.model.loadHistoryBitmaps
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

internal class OverlayAnswerController(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<OverlayUiState>,
    private val solveOperations: SolveOperations,
    private val bubbleStateMachine: BubbleStateMachine,
    private val saveScreenshots: suspend (String, List<Bitmap>) -> List<String>
) {
    private val tag = "HanakoOverlayAnswer"
    private var activeObservation: Job? = null
    private var activeTaskId: String? = null
    private var pendingCaptures: List<Bitmap> = emptyList()
    private var pendingScreenshotPaths: List<String> = emptyList()

    fun process(bitmap: Bitmap) {
        process(listOf(bitmap))
    }

    fun process(bitmaps: List<Bitmap>) {
        if (uiState.value.working) return
        val firstBitmap = bitmaps.firstOrNull() ?: return
        val state = uiState.value
        if (state.result?.id in state.busyHistoryIds) {
            showStartFailure(IllegalStateException("当前对话仍在处理中，请稍后再截图"))
            return
        }
        pendingCaptures = bitmaps
        pendingScreenshotPaths = emptyList()
        if (state.result != null && state.result.automationAction == null) {
            continueCapture()
            return
        }
        AppDebugLogStore.i(tag, "process start route=${state.settings.processingRoute} bitmapCount=${bitmaps.size}")
        uiState.update {
            it.copy(
                selectedBitmap = firstBitmap,
                liveOcrText = "",
                liveAnswerText = "",
                result = null,
                error = null,
                working = true,
                sendingFollowUp = false,
                retryCapturePending = true,
                screenshotRetry = null,
                waitingForScreenshotAnswer = true,
                followUpDraft = "",
                sheetVisible = true,
                sheetMode = OverlaySheetMode.RESULT,
                cardExpanded = false
            )
        }
        val handle = runCatching {
            solveOperations.startAnswer(state.settings, bitmaps)
        }.onFailure(::showStartFailure).getOrNull() ?: return
        observe(handle)
    }

    private fun continueCapture() {
        val state = uiState.value
        val result = state.result ?: return
        val captures = pendingCaptures
        if (captures.isEmpty() || state.working || result.id in state.busyHistoryIds) return
        uiState.update {
            it.copy(selectedBitmap = captures.first(), working = true, sendingFollowUp = true,
                retryCapturePending = true, waitingForScreenshotAnswer = true, screenshotRetry = null, error = null,
                followUpDraft = "", sheetVisible = true, settingsMenuVisible = false,
                sheetMode = OverlaySheetMode.RESULT, cardExpanded = false)
        }
        bubbleStateMachine.forceState(BubbleState.Processing)
        scope.launch {
            try {
                if (pendingScreenshotPaths.isEmpty()) pendingScreenshotPaths = saveScreenshots(result.id, captures)
                val handle = solveOperations.continueConversation(
                    settings = state.settings,
                    historyId = result.id,
                    prompt = "请结合这次截图和已有对话继续解答。",
                    screenshotPaths = pendingScreenshotPaths
                )
                if (handle == null) showStartFailure(IllegalStateException("无法继续当前对话，请检查模型配置后重试"))
                else observe(handle)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showStartFailure(error)
            }
        }
    }

    fun retryCapture() {
        if (pendingCaptures.isEmpty() || uiState.value.working) return
        if (uiState.value.result != null && uiState.value.result?.automationAction == null) continueCapture()
        else process(pendingCaptures)
    }

    fun startNewConversation() {
        if (uiState.value.working || uiState.value.capturing) return
        activeObservation?.cancel()
        activeObservation = null
        activeTaskId = null
        pendingCaptures = emptyList()
        pendingScreenshotPaths = emptyList()
        uiState.update {
            it.copy(result = null, screenshot = null, selectedBitmap = null,
                liveAnswerText = "", liveOcrText = "", followUpDraft = "", error = null,
                retryCapturePending = false, waitingForScreenshotAnswer = false, screenshotRetry = null,
                sendingFollowUp = false, sheetVisible = false, settingsMenuVisible = false,
                sheetMode = OverlaySheetMode.RESULT, cardExpanded = false)
        }
        bubbleStateMachine.forceState(BubbleState.Idle)
    }

    fun regenerateCurrentResult() {
        val existingResult = uiState.value.result ?: return
        if (existingResult.automationAction != null || uiState.value.working || existingResult.id in uiState.value.busyHistoryIds) return
        val bitmaps = existingResult.loadHistoryBitmaps()
        val firstBitmap = bitmaps.firstOrNull()
        if (firstBitmap == null) {
            uiState.update { it.copy(error = "找不到原始截图，无法重新生成") }
            return
        }
        uiState.update {
            it.copy(
                selectedBitmap = firstBitmap,
                liveOcrText = "",
                liveAnswerText = "",
                error = null,
                working = true,
                sheetVisible = true,
                sheetMode = OverlaySheetMode.RESULT,
                result = existingResult.copy(detail = "正在重新生成")
            )
        }
        val handle = runCatching {
            solveOperations.regenerate(uiState.value.settings, existingResult, bitmaps)
        }.onFailure(::showStartFailure).getOrNull()
        if (handle == null) {
            uiState.update { it.copy(working = false) }
            return
        }
        observe(handle)
    }

    fun sendFollowUp(retry: Boolean = false) {
        val state = uiState.value
        val result = state.result ?: return
        if (state.working || result.id in state.busyHistoryIds) return
        val prompt = state.followUpDraft.trim()
        if (!retry && prompt.isBlank()) return
        uiState.update { it.copy(working = true, sendingFollowUp = true, error = null,
            waitingForScreenshotAnswer = retry && result.followUpTurns.lastOrNull()?.screenshotPaths?.isNotEmpty() == true) }
        scope.launch {
            try {
                val handle = if (retry) {
                    solveOperations.retryLatestConversation(state.settings, result.id)
                } else {
                    solveOperations.continueConversation(state.settings, result.id, prompt)
                }
                if (handle == null) {
                    showStartFailure(IllegalStateException("无法开始追问，请检查模型配置或等待当前任务完成"))
                } else {
                    if (!retry) uiState.update { it.copy(followUpDraft = "") }
                    observe(handle)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showStartFailure(error)
            }
        }
    }

    fun cancel() {
        activeTaskId?.let(solveOperations::cancelTask)
    }

    private fun observe(handle: SolveTaskHandle) {
        activeObservation?.cancel()
        activeTaskId = handle.taskId
        bubbleStateMachine.forceState(BubbleState.Processing)
        val job = scope.launch {
            handle.updates
                .onEach(::applySnapshot)
                .takeWhile { it.task.status == WorkflowTaskStatus.RUNNING }
                .collect {}
        }
        activeObservation = job
        job.invokeOnCompletion {
            if (activeObservation === job) {
                activeObservation = null
                activeTaskId = null
            }
        }
    }

    private fun applySnapshot(snapshot: SolveTaskSnapshot) {
        val currentTurn = snapshot.result?.followUpTurns?.lastOrNull()
            ?.takeIf { it.id == snapshot.task.conversationTurnId }
        val prepared = if (snapshot.task.kind == WorkflowTaskKind.CONVERSATION) currentTurn != null else snapshot.result != null
        if (prepared) {
            pendingCaptures = emptyList()
            pendingScreenshotPaths = emptyList()
            val hasText = if (snapshot.task.kind == WorkflowTaskKind.CONVERSATION) {
                !currentTurn?.pendingAssistantText.isNullOrBlank()
            } else !snapshot.result?.answer.isNullOrBlank()
            uiState.update { it.copy(retryCapturePending = false,
                waitingForScreenshotAnswer = it.waitingForScreenshotAnswer && !hasText) }
        }
        snapshot.result?.let(::showResultProgress)
        when (snapshot.task.status) {
            WorkflowTaskStatus.RUNNING -> Unit
            WorkflowTaskStatus.SUCCESS -> {
                val result = snapshot.result ?: return
                AppDebugLogStore.i(tag, "task success resultId=${result.id} answerLength=${result.answer.length}")
                uiState.update {
                    it.copy(
                        working = false,
                        sendingFollowUp = false,
                        waitingForScreenshotAnswer = false,
                        result = result,
                        liveOcrText = result.extractedText,
                        liveAnswerText = result.answer,
                        autoRunState = AutoRunState.IDLE,
                        autoCopiedLabel = null,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
            WorkflowTaskStatus.ERROR -> {
                val message = snapshot.task.errorMessage ?: snapshot.result?.detail ?: "处理失败"
                AppDebugLogStore.e(tag, "task failed: $message")
                uiState.update {
                    it.copy(
                        working = false,
                        sendingFollowUp = false,
                        waitingForScreenshotAnswer = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null,
                        error = message
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
            WorkflowTaskStatus.CANCELLED -> {
                uiState.update {
                    it.copy(
                        working = false,
                        sendingFollowUp = false,
                        waitingForScreenshotAnswer = false,
                        autoRunState = AutoRunState.IDLE,
                        pendingVibrationLetters = null
                    )
                }
                bubbleStateMachine.forceState(BubbleState.Idle)
            }
        }
    }

    private fun showResultProgress(result: ProcessingResult) {
        uiState.update { current ->
            current.copy(
                result = result,
                liveOcrText = result.extractedText,
                liveAnswerText = result.answer
            )
        }
    }

    private fun showStartFailure(error: Throwable) {
        AppDebugLogStore.e(tag, "task start failed", error)
        uiState.update { it.copy(working = false, sendingFollowUp = false, waitingForScreenshotAnswer = false,
            error = error.message ?: "处理失败") }
        bubbleStateMachine.forceState(BubbleState.Idle)
    }
}
