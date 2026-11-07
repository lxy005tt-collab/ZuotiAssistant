package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.core.ui.richtext.LocalCopyMarkerCards
import `fun`.kirari.hanako.core.ui.theme.MonochromeLightColors

private val AnswerBubbleColors = MonochromeLightColors

@Composable
internal fun AnswerBubbleTheme(enabled: Boolean, answerTextSizeSp: Int? = null, content: @Composable () -> Unit) {
    if (enabled) {
        val baseTypography = MaterialTheme.typography
        val typography = if (answerTextSizeSp != null) {
            val size = answerTextSizeSp.coerceIn(12, 22)
            baseTypography.copy(
                bodyLarge = baseTypography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp),
                bodyMedium = baseTypography.bodyMedium.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp)
            )
        } else baseTypography
        MaterialTheme(colorScheme = AnswerBubbleColors, typography = typography) {
            CompositionLocalProvider(
                LocalContentColor provides Color.Black,
                LocalTextStyle provides typography.bodyLarge,
                LocalCopyMarkerCards provides false,
                content = content
            )
        }
    } else {
        content()
    }
}
