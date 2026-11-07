package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** Preserve the selected image coordinates when a fitted preview changes size. */
internal fun reprojectCropPoint(point: Offset, image: IntSize, previous: IntSize, next: IntSize): Offset {
    if (image.width <= 0 || image.height <= 0 || previous.width <= 0 || previous.height <= 0 || next.width <= 0 || next.height <= 0) return point
    fun scale(canvas: IntSize) = minOf(canvas.width.toFloat() / image.width, canvas.height.toFloat() / image.height)
    fun origin(canvas: IntSize, scale: Float) = Offset((canvas.width - image.width * scale) / 2f, (canvas.height - image.height * scale) / 2f)
    val oldScale = scale(previous)
    val newScale = scale(next)
    return (point - origin(previous, oldScale)) * (newScale / oldScale) + origin(next, newScale)
}
