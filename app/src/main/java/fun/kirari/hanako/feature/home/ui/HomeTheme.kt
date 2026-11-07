package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import `fun`.kirari.hanako.core.ui.theme.monochromeColorScheme

@Composable
internal fun hanakoHomeColorScheme(): ColorScheme = monochromeColorScheme(isSystemInDarkTheme())

@Composable
internal fun HanakoHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = hanakoHomeColorScheme(), content = content)
}
