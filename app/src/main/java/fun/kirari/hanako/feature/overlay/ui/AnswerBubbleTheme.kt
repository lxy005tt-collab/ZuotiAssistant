package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import `fun`.kirari.hanako.core.ui.richtext.LocalCopyMarkerCards

private val AnswerBubbleColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    secondaryContainer = Color(0xFFEDF2F7),
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F5F9),
    surfaceContainer = Color(0xFFF5F8FC),
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF2F5F9),
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black
)

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
