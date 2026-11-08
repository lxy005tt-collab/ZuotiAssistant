package `fun`.kirari.hanako.feature.overlay.state

import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayResultContentTest {
    private val emptyResult = ProcessingResult(assistantName = "助手", route = ProcessingRoute.MULTIMODAL_DIRECT)

    @Test
    fun captureAndEmptyTaskKeepOnlyControls_untilTheFirstAnswerArrives() {
        val capturing = OverlayUiState(capturing = true)
        val waiting = capturing.copy(capturing = false, working = true, result = emptyResult)
        assertFalse(capturing.hasAnswerBubbleContent())
        assertFalse(waiting.hasAnswerBubbleContent())
        assertTrue(waiting.copy(liveAnswerText = "A").hasAnswerBubbleContent())
    }

    @Test
    fun failedCaptureStillShowsTheError_withoutAnAnswer() {
        assertTrue(OverlayUiState(error = "截图授权已失效").hasAnswerBubbleContent())
    }

    @Test
    fun waitingForFollowUpPreservesTheExistingAnswer() {
        val state = OverlayUiState(working = true, sendingFollowUp = true, result = emptyResult.copy(answer = "A"))
        assertTrue(state.hasAnswerBubbleContent())
        assertEquals("A", state.currentAnswerText())
    }

    @Test
    fun nextScreenshotKeepsContextButHidesOldBubbleUntilItsReplyArrives() {
        val state = OverlayUiState(working = true, sendingFollowUp = true,
            waitingForScreenshotAnswer = true, result = emptyResult.copy(answer = "previous answer"))
        assertFalse(state.hasAnswerBubbleContent())
        assertEquals("previous answer", state.currentAnswerText())
        assertTrue(state.copy(waitingForScreenshotAnswer = false).hasAnswerBubbleContent())
        assertTrue(state.copy(error = "截图失败").hasAnswerBubbleContent())
    }
}
