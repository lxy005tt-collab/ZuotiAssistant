package `fun`.kirari.hanako.feature.overlay.window

import org.junit.Assert.*
import org.junit.Test

class AnchoredCardLayoutTest {
    private val portrait = CardBounds(8, 32, 392, 776)

    @Test
    fun cardAboveBottomBubble_andBelowTopBubble() {
        val bottom = CardBounds(330, 700, 386, 756)
        val above = placeAnchoredCard(portrait, bottom, 320, 380, 4)
        assertEquals(CardAttachment.ABOVE, above.attachment)
        assertTrue(above.bounds.bottom < bottom.top)
        val top = CardBounds(10, 40, 66, 96)
        val below = placeAnchoredCard(portrait, top, 320, 380, 4)
        assertEquals(CardAttachment.BELOW, below.attachment)
        assertTrue(below.bounds.top > top.bottom)
    }

    @Test
    fun growingAnswer_staysBelowUntilThereIsNoReadableRoom() {
        val bubble = CardBounds(10, 300, 126, 360)
        for (height in listOf(120, 220, 420, 560)) {
            val placed = placeAnchoredCard(portrait, bubble, 292, height, 4,
                preferBelow = true, minimumHeight = 140)
            assertEquals(CardAttachment.BELOW, placed.attachment)
            assertEquals(bubble.bottom + 4, placed.bounds.top)
            assertTrue(placed.bounds.bottom <= portrait.bottom)
        }
        val nearBottom = CardBounds(10, 620, 126, 680)
        val placed = placeAnchoredCard(portrait, nearBottom, 292, 220, 4,
            preferBelow = true, minimumHeight = 140)
        assertEquals(CardAttachment.ABOVE, placed.attachment)
        assertTrue(placed.bounds.bottom < nearBottom.top)
    }

    @Test
    fun landscape_usesSideWithoutCoveringBubble() {
        val bubble = CardBounds(24, 140, 80, 196)
        val placed = placeAnchoredCard(CardBounds(8, 24, 792, 352), bubble, 320, 380, 4)
        assertEquals(CardAttachment.RIGHT, placed.attachment)
        assertTrue(placed.bounds.left > bubble.right)
    }

    @Test
    fun dragging_movesCardAndKeepsPointerOnBubble() {
        val area = CardBounds(8, 32, 992, 776)
        val first = placeAnchoredCard(area, CardBounds(300, 700, 356, 756), 320, 380, 4)
        val moved = placeAnchoredCard(area, CardBounds(420, 700, 476, 756), 320, 380, 4)
        assertEquals(120, moved.bounds.left - first.bounds.left)
        assertEquals(0.5f, moved.pointerFraction, 0.001f)
    }

    @Test
    fun keyboard_pushesCardAboveItsTopEvenWhenBubbleIsCovered() {
        val safeAboveKeyboard = CardBounds(8, 32, 392, 448)
        val placed = placeAnchoredCard(safeAboveKeyboard, CardBounds(330, 700, 386, 756), 320, 380, 4)
        assertTrue(placed.bounds.bottom <= 448)
        assertTrue(placed.bounds.top >= 32)
    }

    @Test
    fun edgesAndSmallScreens_keepWholeCardInSafeArea() {
        val areas = listOf(portrait, CardBounds(8, 24, 792, 352), CardBounds(8, 32, 232, 400))
        for (area in areas) {
            for (x in listOf(area.left, area.centerX - 28, area.right - 56)) {
                for (y in listOf(area.top, area.centerY - 28, area.bottom - 56)) {
                    val bubble = CardBounds(x, y, x + 56, y + 56)
                    for (height in listOf(260, 380, 560)) {
                        val card = placeAnchoredCard(area, bubble, 320, height, 4).bounds
                        assertTrue("left $area $bubble", card.left >= area.left)
                        assertTrue("right $area $bubble", card.right <= area.right)
                        assertTrue("top $area $bubble", card.top >= area.top)
                        assertTrue("bottom $area $bubble", card.bottom <= area.bottom)
                        assertTrue(card.width > 0 && card.height > 0)
                        assertTrue("must leave bubble tappable", card.right <= bubble.left || card.left >= bubble.right || card.bottom <= bubble.top || card.top >= bubble.bottom)
                    }
                }
            }
        }
    }
}
