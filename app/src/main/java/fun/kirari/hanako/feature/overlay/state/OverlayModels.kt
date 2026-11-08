package `fun`.kirari.hanako.feature.overlay.state

import android.graphics.Bitmap
import `fun`.kirari.hanako.feature.overlay.state.BubbleState
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode

internal const val SheetAnimationDurationMs = 260

internal enum class OverlaySheetMode {
    HOME,
    CROP,
    RESULT,
    HISTORY
}

internal enum class AutoRunState {
    IDLE,
    RUNNING,
    COMPLETED
}

internal enum class ScreenshotRetry { FULL_SCREEN, REGION }

internal data class OverlayUiState(
    val settings: AppSettings = AppSettings(),
    val screenshot: Bitmap? = null,
    val selectedBitmap: Bitmap? = null,
    val liveOcrText: String = "",
    val liveAnswerText: String = "",
    val result: ProcessingResult? = null,
    val error: String? = null,
    val working: Boolean = false,
    val sendingFollowUp: Boolean = false,
    val retryCapturePending: Boolean = false,
    val screenshotRetry: ScreenshotRetry? = null,
    val waitingForScreenshotAnswer: Boolean = false,
    val followUpDraft: String = "",
    val capturing: Boolean = false,
    val settingsMenuVisible: Boolean = false,
    val history: List<ProcessingResult> = emptyList(),
    val busyHistoryIds: Set<String> = emptySet(),
    val sheetVisible: Boolean = false,
    val sheetMode: OverlaySheetMode = OverlaySheetMode.HOME,
    val cardExpanded: Boolean = false,
    val launchMode: CaptureLaunchMode = CaptureLaunchMode.NORMAL,
    val autoRunState: AutoRunState = AutoRunState.IDLE,
    val autoCopiedLabel: String? = null,
    val pendingVibrationLetters: String? = null,
    val bubbleState: BubbleState = BubbleState.Idle
)
