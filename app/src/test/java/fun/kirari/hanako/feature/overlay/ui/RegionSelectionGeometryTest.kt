package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionSelectionGeometryTest {
    private val image = IntSize(1000, 2000)

    @Test
    fun selectionUsesOriginalImagePixels_notPreviewPixels() {
        val selected = selectedImageRegion(image, IntSize(500, 1000), Offset(50f, 100f), Offset(300f, 400f))
        assertEquals(IntRect(100, 200, 600, 800), selected?.pixels)
    }

    @Test
    fun letterboxPaddingIsExcludedFromCropCoordinates() {
        val selected = selectedImageRegion(image, IntSize(1000, 1000), Offset(300f, 100f), Offset(500f, 300f))
        assertEquals(IntRect(100, 200, 500, 600), selected?.pixels)
    }

    @Test
    fun backwardsDragBeyondEdgesClampsToTheWholeImage() {
        val selected = selectedImageRegion(image, IntSize(1000, 1000), Offset(1200f, 1100f), Offset(0f, -200f))
        assertEquals(IntRect(0, 0, 1000, 2000), selected?.pixels)
    }

    @Test
    fun selectingOnlyLetterboxSpaceCannotSendAnImage() {
        assertNull(selectedImageRegion(image, IntSize(1000, 1000), Offset(10f, 10f), Offset(100f, 100f)))
    }

    @Test
    fun tapsTinySelectionsAndUnmeasuredPreviewsCannotSendAnImage() {
        assertNull(selectedImageRegion(image, IntSize(500, 1000), Offset.Zero, Offset.Zero))
        assertNull(selectedImageRegion(image, IntSize(500, 1000), Offset.Zero, Offset(1f, 2f)))
        assertNull(selectedImageRegion(image, IntSize.Zero, Offset.Zero, Offset(50f, 50f)))
    }
}
