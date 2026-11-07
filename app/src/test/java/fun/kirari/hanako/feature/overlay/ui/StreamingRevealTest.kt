package `fun`.kirari.hanako.feature.overlay.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingRevealTest {
    @Test
    fun mixedUnicode_neverLeavesHalfAnEmojiVisible() {
        val text = "答案：🌍，选 A。\n推导：𝑥² + 1 = 2。完成 ✅"
        var index = 0
        while (index < text.length) {
            val next = nextRevealIndex(text, index)
            assertTrue(next > index && next <= text.length)
            assertFalse(Character.isHighSurrogate(text[next - 1]))
            index = next
        }
        assertEquals(text.length, index)
    }

    @Test
    fun largeResponse_catchesUpWithinNinetyFrames() {
        val text = "这是一段较长的推导。".repeat(800)
        var index = 0
        repeat(90) { index = nextRevealIndex(text, index) }
        assertEquals(text.length, index)
        assertEquals(text.length, nextRevealIndex(text, index))
    }
}
