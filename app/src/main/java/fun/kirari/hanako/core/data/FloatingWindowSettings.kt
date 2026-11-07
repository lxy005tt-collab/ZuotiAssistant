package `fun`.kirari.hanako.core.data

import kotlinx.serialization.Serializable

@Serializable
data class FloatingWindowSettings(
    val sizePercent: Int = 75,
    val backgroundOpacityPercent: Int = 94,
    val answerTextSizeSp: Int = 16,
    val typewriterEnabled: Boolean = true,
    val closeOnOutsideTap: Boolean = true
) {
    val sizeScale: Float get() = sizePercent.coerceIn(60, 100) / 100f
    val backgroundOpacity: Float get() = backgroundOpacityPercent.coerceIn(60, 100) / 100f

    fun normalized(): FloatingWindowSettings = copy(
        sizePercent = sizePercent.coerceIn(60, 100),
        backgroundOpacityPercent = backgroundOpacityPercent.coerceIn(60, 100),
        answerTextSizeSp = answerTextSizeSp.coerceIn(12, 22)
    )
}
