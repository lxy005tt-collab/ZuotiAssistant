package `fun`.kirari.hanako.feature.overlay.window

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedWindowLayoutTest {
    private val screen = CardBounds(8, 32, 352, 768)

    @Test
    fun opening_movesTheRightEdgeWhileKeepingTheLeftControlAnchored() {
        val anchor = CardBounds(24, 200, 111, 245)
        val expanded = placeUnifiedWindow(screen, anchor, 210, 160)
        assertEquals(anchor.left, expanded.left)
        assertEquals(anchor.top, expanded.top)
        assertTrue(expanded.right > anchor.right)
    }

    @Test
    fun collapsingNearBottomRight_movesAndShrinksDuringTheSameFrame() {
        val anchor = CardBounds(260, 700, 347, 745)
        val expanded = placeUnifiedWindow(screen, anchor, 210, 315)
        val middle = interpolateWindowBounds(expanded, anchor, .5f)
        assertTrue(middle.left > expanded.left && middle.left < anchor.left)
        assertTrue(middle.top > expanded.top && middle.top < anchor.top)
        assertTrue(middle.width < expanded.width && middle.width > anchor.width)
        assertTrue(middle.height < expanded.height && middle.height > anchor.height)
        assertEquals(anchor, interpolateWindowBounds(expanded, anchor, 1f))
        val host = motionWindowBounds(expanded, anchor)
        for (step in 0..20) {
            val frame = interpolateWindowBounds(expanded, anchor, step / 20f)
            assertTrue(frame.left >= host.left && frame.right <= host.right)
            assertTrue(frame.top >= host.top && frame.bottom <= host.bottom)
        }
    }

    @Test
    fun growingCard_preservesItsTopUntilItNeedsBottomClearance() {
        val anchor = CardBounds(120, 200, 236, 260)
        val small = placeUnifiedWindow(screen, anchor, 280, 100)
        val large = placeUnifiedWindow(screen, anchor, 280, 420)
        assertEquals(small.left, large.left)
        assertEquals(small.top, large.top)
        val lowAnchor = CardBounds(120, 650, 236, 710)
        val moved = placeUnifiedWindow(screen, lowAnchor, 280, 420)
        assertEquals(screen.bottom, moved.bottom)
        assertEquals(420, moved.height)
    }

    @Test
    fun collapsingAfterEdgeGrowth_restoresTheOriginalControlPosition() {
        val anchor = CardBounds(236, 680, 352, 740)
        val expanded = placeUnifiedWindow(screen, anchor, 280, 420)
        assertTrue(expanded.top < anchor.top)
        assertTrue(expanded.left < anchor.left)
        assertEquals(anchor, placeUnifiedWindow(screen, anchor, anchor.width, anchor.height))
    }

    @Test
    fun keyboardAndRotation_keepTheWholeWindowInsideTheSafeArea() {
        for (area in listOf(screen, CardBounds(8, 32, 352, 420), CardBounds(8, 24, 792, 340), CardBounds(8, 24, 228, 180))) {
            for (x in listOf(area.left, area.right - 116)) {
                for (y in listOf(area.top, area.bottom - 60)) {
                    val anchor = CardBounds(x, y, x + 116, y + 60)
                    val placed = placeUnifiedWindow(area, anchor, 280, 420)
                    assertTrue(placed.left >= area.left && placed.right <= area.right)
                    assertTrue(placed.top >= area.top && placed.bottom <= area.bottom)
                    assertTrue(placed.height > 0 && placed.width > 0)
                }
            }
        }
    }
}
