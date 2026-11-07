package `fun`.kirari.hanako.core.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.sp

/** Keep answer/Markdown sizing consistent without scaling the app's buttons or labels. */
@Composable
fun AnswerTextSize(sizeSp: Int, content: @Composable () -> Unit) {
    val size = sizeSp.coerceIn(12, 22)
    val base = MaterialTheme.typography
    val typography = base.copy(
        bodyLarge = base.bodyLarge.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp),
        bodyMedium = base.bodyMedium.copy(fontSize = size.sp, lineHeight = (size * 1.5f).sp)
    )
    MaterialTheme(typography = typography) {
        CompositionLocalProvider(LocalTextStyle provides typography.bodyLarge, content = content)
    }
}
