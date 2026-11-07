package `fun`.kirari.hanako.feature.overlay.window

/** Grow to the right of the controls; shift left only when the screen edge requires it. */
internal fun placeUnifiedWindow(
    safeArea: CardBounds,
    anchor: CardBounds,
    desiredWidth: Int,
    desiredHeight: Int
): CardBounds {
    val width = desiredWidth.coerceIn(1, safeArea.width)
    val height = desiredHeight.coerceIn(1, safeArea.height)
    val left = anchor.left.coerceIn(safeArea.left, safeArea.right - width)
    val top = anchor.top.coerceIn(safeArea.top, safeArea.bottom - height)
    return CardBounds(left, top, left + width, top + height)
}

/** A fixed host covers the complete motion, so animation frames never resize an OS window. */
internal fun motionWindowBounds(from: CardBounds, to: CardBounds): CardBounds = CardBounds(
    minOf(from.left, to.left), minOf(from.top, to.top),
    maxOf(from.right, to.right), maxOf(from.bottom, to.bottom)
)

internal fun interpolateWindowBounds(
    from: CardBounds,
    to: CardBounds,
    progress: Float,
    verticalProgress: Float = progress
): CardBounds {
    fun between(start: Int, end: Int, fraction: Float) =
        kotlin.math.round(start + (end - start) * fraction.coerceIn(0f, 1f)).toInt()
    return CardBounds(between(from.left, to.left, progress), between(from.top, to.top, verticalProgress),
        between(from.right, to.right, progress), between(from.bottom, to.bottom, verticalProgress))
}
