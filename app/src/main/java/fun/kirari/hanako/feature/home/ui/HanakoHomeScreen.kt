package `fun`.kirari.hanako.feature.home.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.platform.capture.ScreenCaptureManager
import `fun`.kirari.hanako.platform.capture.ScreenCaptureStartResult
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.platform.capture.CaptureLaunchMode
import `fun`.kirari.hanako.feature.home.ui.components.HeroSection

@Composable
internal fun HanakoHomeScreen(
    settings: AppSettings,
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onSelectRoute: (ProcessingRoute) -> Unit,
    onSelectCaptureMethod: (ScreenCaptureMethod) -> Unit,
    updateAvailable: Boolean = false,
    onShowUpdate: () -> Unit = {}
) {
    HanakoHomeTheme {
        val context = LocalContext.current
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroSection(
                    updateAvailable = updateAvailable,
                    onShowUpdate = onShowUpdate,
                    overlayEnabled = overlayEnabled,
                    hasOverlayPermission = hasOverlayPermission,
                    captureMethod = settings.screenCaptureMethod,
                    staticModeEnabled = settings.automation.staticModeEnabled,
                    route = settings.processingRoute,
                    onSelectRoute = onSelectRoute,
                    onSelectCaptureMethod = onSelectCaptureMethod,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onToggleOverlay = onToggleOverlay,
                    onStartAutoMode = {
                        if (!hasOverlayPermission) return@HeroSection
                        when (
                            val result = ScreenCaptureManager.requestStart(
                                context = context,
                                method = settings.screenCaptureMethod,
                                launchMode = CaptureLaunchMode.AUTO
                            )
                        ) {
                            ScreenCaptureStartResult.Started -> Unit
                            is ScreenCaptureStartResult.UserActionRequired -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            }
                            is ScreenCaptureStartResult.Failed -> {
                                Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
            }
        }
    }
}
