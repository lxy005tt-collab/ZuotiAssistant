package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.floor

internal data class SelectedImageRegion(val preview: Rect, val pixels: IntRect)

/** Map an aspect-fit preview selection to source pixels, excluding any letterboxing. */
internal fun selectedImageRegion(
    image: IntSize,
    viewport: IntSize,
    start: Offset,
    end: Offset
): SelectedImageRegion? {
    if (image.width <= 0 || image.height <= 0 || viewport.width <= 0 || viewport.height <= 0) return null
    val scale = minOf(viewport.width.toFloat() / image.width, viewport.height.toFloat() / image.height)
    val imageLeft = (viewport.width - image.width * scale) / 2f
    val imageTop = (viewport.height - image.height * scale) / 2f
    val left = maxOf(minOf(start.x, end.x), imageLeft)
    val top = maxOf(minOf(start.y, end.y), imageTop)
    val right = minOf(maxOf(start.x, end.x), imageLeft + image.width * scale)
    val bottom = minOf(maxOf(start.y, end.y), imageTop + image.height * scale)
    if (left >= right || top >= bottom) return null
    val pixels = IntRect(
        floor((left - imageLeft) / scale).toInt().coerceIn(0, image.width),
        floor((top - imageTop) / scale).toInt().coerceIn(0, image.height),
        ceil((right - imageLeft) / scale).toInt().coerceIn(0, image.width),
        ceil((bottom - imageTop) / scale).toInt().coerceIn(0, image.height)
    )
    if (pixels.width < 8 || pixels.height < 8) return null
    return SelectedImageRegion(Rect(left, top, right, bottom), pixels)
}
