package `fun`.kirari.hanako.feature.home.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightHomeColors = lightColorScheme(
    primary = Color(0xFF2F64D6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDF3FF),
    onPrimaryContainer = Color(0xFF244DA2),
    secondary = Color(0xFF59677C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EEF8),
    onSecondaryContainer = Color(0xFF244DA2),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF17243A),
    surface = Color.White,
    onSurface = Color(0xFF17243A),
    surfaceVariant = Color(0xFFF1F4F8),
    onSurfaceVariant = Color(0xFF59677C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F8FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF1F7),
    surfaceContainerHighest = Color(0xFFE4EAF2),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF8491A3),
    outlineVariant = Color(0xFFDEE4ED)
)

private val DarkHomeColors = darkColorScheme(
    primary = Color(0xFFA3C0FF),
    onPrimary = Color(0xFF153263),
    primaryContainer = Color(0xFF233A61),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFB0BED1),
    onSecondary = Color(0xFF243346),
    secondaryContainer = Color(0xFF2B3C56),
    onSecondaryContainer = Color(0xFFD5E3FF),
    background = Color(0xFF101722),
    onBackground = Color(0xFFE9EEF7),
    surface = Color(0xFF1C2736),
    onSurface = Color(0xFFE9EEF7),
    surfaceVariant = Color(0xFF243143),
    onSurfaceVariant = Color(0xFFB0BED1),
    surfaceContainerLowest = Color(0xFF101722),
    surfaceContainerLow = Color(0xFF172130),
    surfaceContainer = Color(0xFF1C2736),
    surfaceContainerHigh = Color(0xFF243143),
    surfaceContainerHighest = Color(0xFF2E3D52),
    surfaceTint = Color.Transparent,
    outline = Color(0xFF8292A8),
    outlineVariant = Color(0xFF344356)
)

@Composable
internal fun hanakoHomeColorScheme(): ColorScheme =
    if (isSystemInDarkTheme()) DarkHomeColors else LightHomeColors

@Composable
internal fun HanakoHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = hanakoHomeColorScheme(), content = content)
}
