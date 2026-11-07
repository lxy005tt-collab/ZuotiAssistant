package `fun`.kirari.hanako.feature.overlay.window

/** One window grows around the collapsed control's center and stays inside the usable screen. */
internal fun placeUnifiedWindow(
    safeArea: CardBounds,
    anchor: CardBounds,
    desiredWidth: Int,
    desiredHeight: Int
): CardBounds {
    val width = desiredWidth.coerceIn(1, safeArea.width)
    val height = desiredHeight.coerceIn(1, safeArea.height)
    val left = (anchor.centerX - width / 2).coerceIn(safeArea.left, safeArea.right - width)
    val top = anchor.top.coerceIn(safeArea.top, safeArea.bottom - height)
    return CardBounds(left, top, left + width, top + height)
}
