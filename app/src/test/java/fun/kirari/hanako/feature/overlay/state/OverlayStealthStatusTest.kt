package `fun`.kirari.hanako.feature.overlay.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayStealthStatusTest {
    @Test
    fun anUnsupportedSelectionWindowCannotBeMaskedByAHealthyAnswerWindow() {
        val answer = Any()
        val selection = Any()
        try {
            OverlayStealthStatus.report(answer, StealthStatus.REQUESTED)
            assertTrue(OverlayStealthStatus.report(selection, StealthStatus.UNSUPPORTED))
            assertFalse(OverlayStealthStatus.report(answer, StealthStatus.REQUESTED))
            assertEquals(StealthStatus.UNSUPPORTED, OverlayStealthStatus.status.value)
            assertFalse(OverlayStealthStatus.report(selection, StealthStatus.UNSUPPORTED))
            OverlayStealthStatus.remove(selection)
            assertEquals(StealthStatus.REQUESTED, OverlayStealthStatus.status.value)
        } finally {
            OverlayStealthStatus.remove(answer)
            OverlayStealthStatus.remove(selection)
        }
        assertEquals(StealthStatus.INACTIVE, OverlayStealthStatus.status.value)
    }

    @Test
    fun aNewWindowMustFinishRequestingExclusionBeforeTheCombinedStatusIsReady() {
        assertEquals(StealthStatus.CHECKING,
            aggregateStealthStatus(listOf(StealthStatus.REQUESTED, StealthStatus.CHECKING)))
        assertEquals(StealthStatus.UNSUPPORTED,
            aggregateStealthStatus(listOf(StealthStatus.CHECKING, StealthStatus.UNSUPPORTED)))
    }
}
