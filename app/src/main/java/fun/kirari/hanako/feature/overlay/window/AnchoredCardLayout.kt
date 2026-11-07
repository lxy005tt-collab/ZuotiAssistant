package `fun`.kirari.hanako.feature.overlay.window

internal data class CardBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = (right - left).coerceAtLeast(1)
    val height get() = (bottom - top).coerceAtLeast(1)
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
}

internal enum class CardAttachment { ABOVE, BELOW, LEFT, RIGHT }

internal data class AnchoredCardPlacement(
    val bounds: CardBounds,
    val attachment: CardAttachment,
    val pointerFraction: Float
)

/** Screen coordinates, including the safe area reserved for system bars / keyboard. */
internal fun placeAnchoredCard(
    safeArea: CardBounds,
    bubble: CardBounds,
    desiredWidth: Int,
    desiredHeight: Int,
    gap: Int,
    preferBelow: Boolean = false,
    minimumHeight: Int = desiredHeight
): AnchoredCardPlacement {
    val width = desiredWidth.coerceIn(1, safeArea.width)
    val height = desiredHeight.coerceIn(1, safeArea.height)
    val anchorX = bubble.centerX.coerceIn(safeArea.left, safeArea.right)
    val anchorY = bubble.centerY.coerceIn(safeArea.top, safeArea.bottom)
    val above = (bubble.top.coerceAtMost(safeArea.bottom) - gap - safeArea.top).coerceAtLeast(0)
    val below = (safeArea.bottom - bubble.bottom.coerceAtLeast(safeArea.top) - gap).coerceAtLeast(0)
    val left = (bubble.left - gap - safeArea.left).coerceAtLeast(0)
    val right = (safeArea.right - bubble.right - gap).coerceAtLeast(0)
    val attachment = when {
        preferBelow && below >= minOf(height, minimumHeight) -> CardAttachment.BELOW
        above >= height -> CardAttachment.ABOVE
        below >= height -> CardAttachment.BELOW
        right >= width -> CardAttachment.RIGHT
        left >= width -> CardAttachment.LEFT
        above >= below -> CardAttachment.ABOVE
        else -> CardAttachment.BELOW
    }
    val actualHeight = when (attachment) {
        CardAttachment.ABOVE -> height.coerceAtMost(above.coerceAtLeast(1))
        CardAttachment.BELOW -> height.coerceAtMost(below.coerceAtLeast(1))
        else -> height
    }
    val x = when (attachment) {
        CardAttachment.LEFT -> bubble.left - gap - width
        CardAttachment.RIGHT -> bubble.right + gap
        else -> anchorX - width / 2
    }.coerceIn(safeArea.left, safeArea.right - width)
    val y = when (attachment) {
        CardAttachment.ABOVE -> bubble.top.coerceAtMost(safeArea.bottom) - gap - actualHeight
        CardAttachment.BELOW -> bubble.bottom + gap
        else -> anchorY - actualHeight / 2
    }.coerceIn(safeArea.top, safeArea.bottom - actualHeight)
    val pointer = when (attachment) {
        CardAttachment.ABOVE, CardAttachment.BELOW -> (anchorX - x).toFloat() / width
        else -> (anchorY - y).toFloat() / actualHeight
    }.coerceIn(0.08f, 0.92f)
    return AnchoredCardPlacement(CardBounds(x, y, x + width, y + actualHeight), attachment, pointer)
}
