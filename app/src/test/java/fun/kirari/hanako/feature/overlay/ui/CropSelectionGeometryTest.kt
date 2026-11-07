package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CropSelectionGeometryTest {
    @Test
    fun expandingCard_preservesSelectionAcrossLetterboxAndScaleChanges() {
        val image = IntSize(200, 400)
        val small = IntSize(100, 200)
        val wide = IntSize(300, 200)
        val tall = IntSize(300, 600)
        val selected = Offset(25f, 50f)
        val widePoint = reprojectCropPoint(selected, image, small, wide)
        assertEquals(Offset(125f, 50f), widePoint)
        assertEquals(Offset(75f, 150f), reprojectCropPoint(widePoint, image, wide, tall))
        assertEquals(selected, reprojectCropPoint(widePoint, image, wide, small))
    }
}
