package `fun`.kirari.hanako.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val MonochromeLightColors = lightColorScheme(
    primary = Color(0xFF171717), onPrimary = Color.White,
    primaryContainer = Color(0xFFECECEC), onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF555555), onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECEC), onSecondaryContainer = Color(0xFF171717),
    tertiary = Color(0xFF555555), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF0F0F0), onTertiaryContainer = Color(0xFF171717),
    background = Color.White, onBackground = Color(0xFF171717),
    surface = Color.White, onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF3F3F3), onSurfaceVariant = Color(0xFF626262),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5), surfaceContainerHigh = Color(0xFFEFEFEF),
    surfaceContainerHighest = Color(0xFFE7E7E7), surfaceTint = Color.Transparent,
    outline = Color(0xFF777777), outlineVariant = Color(0xFFE3E3E3),
    error = Color(0xFF282828), onError = Color.White,
    errorContainer = Color(0xFFECECEC), onErrorContainer = Color(0xFF171717),
    inverseSurface = Color(0xFF242424), inverseOnSurface = Color(0xFFF5F5F5),
    inversePrimary = Color.White, scrim = Color.Black
)

internal val MonochromeDarkColors = darkColorScheme(
    primary = Color(0xFFF3F3F3), onPrimary = Color(0xFF171717),
    primaryContainer = Color(0xFF303030), onPrimaryContainer = Color(0xFFF3F3F3),
    secondary = Color(0xFFBBBBBB), onSecondary = Color(0xFF171717),
    secondaryContainer = Color(0xFF303030), onSecondaryContainer = Color(0xFFF3F3F3),
    tertiary = Color(0xFFBBBBBB), onTertiary = Color(0xFF171717),
    tertiaryContainer = Color(0xFF303030), onTertiaryContainer = Color(0xFFF3F3F3),
    background = Color(0xFF111111), onBackground = Color(0xFFF3F3F3),
    surface = Color(0xFF111111), onSurface = Color(0xFFF3F3F3),
    surfaceVariant = Color(0xFF272727), onSurfaceVariant = Color(0xFFBBBBBB),
    surfaceContainerLowest = Color(0xFF0D0D0D), surfaceContainerLow = Color(0xFF171717),
    surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF292929),
    surfaceContainerHighest = Color(0xFF333333), surfaceTint = Color.Transparent,
    outline = Color(0xFF919191), outlineVariant = Color(0xFF383838),
    error = Color(0xFFF3F3F3), onError = Color(0xFF171717),
    errorContainer = Color(0xFF333333), onErrorContainer = Color(0xFFF3F3F3),
    inverseSurface = Color(0xFFEAEAEA), inverseOnSurface = Color(0xFF171717),
    inversePrimary = Color(0xFF171717), scrim = Color.Black
)

internal fun monochromeColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) MonochromeDarkColors else MonochromeLightColors
