package `fun`.kirari.hanako.feature.home.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.ui.components.SettingsGroup

/** Two compact groups, using the same surface and spacing as settings. */
@Composable
fun HeroSection(
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    captureMethod: ScreenCaptureMethod,
    stealthEnabled: Boolean,
    onToggleStealth: (Boolean) -> Unit,
    staticModeEnabled: Boolean,
    route: ProcessingRoute,
    onSelectRoute: (ProcessingRoute) -> Unit,
    onSelectCaptureMethod: (ScreenCaptureMethod) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onStartAutoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup {
        ProcessingRouteMenu(route, onSelectRoute)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CaptureMethodSelector(captureMethod, overlayEnabled, onSelectCaptureMethod)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        StealthModeRow(stealthEnabled, overlayEnabled, onToggleStealth)
        if (captureMethod == ScreenCaptureMethod.SHIZUKU_ADB) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ShizukuStatusCard()
        }
        }
        SettingsGroup {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (overlayEnabled) "悬浮窗 · 已开启" else "悬浮窗", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenOverlayPermission) { Text("权限设置") }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
                    .combinedClickable(
                        onClick = {
                            when {
                                !hasOverlayPermission -> onOpenOverlayPermission()
                                overlayEnabled -> onToggleOverlay(false)
                                staticModeEnabled -> onStartAutoMode()
                                else -> onToggleOverlay(true)
                            }
                        },
                        onLongClick = {
                            if (!overlayEnabled && hasOverlayPermission && !staticModeEnabled) onStartAutoMode()
                        }
                    ),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                    Text(when {
                        !hasOverlayPermission -> "允许悬浮窗"
                        overlayEnabled -> "关闭悬浮窗"
                        else -> "启动悬浮窗"
                    }, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text("左球打开菜单，可选择截图区域；右球截取整屏。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!overlayEnabled && hasOverlayPermission && captureMethod == ScreenCaptureMethod.MEDIA_PROJECTION) {
                Text("启动时请在系统弹窗中允许共享整个屏幕。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        }
    }
}

@Composable
private fun ProcessingRouteMenu(route: ProcessingRoute, onSelect: (ProcessingRoute) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("解题方式", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(if (route == ProcessingRoute.MULTIMODAL_DIRECT) "直接读取图片中的题目" else "先识字，再交给文本模型",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { expanded = true }) { Text(if (route == ProcessingRoute.MULTIMODAL_DIRECT) "多模态 ▾" else "OCR ▾") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("多模态 · 直接看图") }, onClick = { onSelect(ProcessingRoute.MULTIMODAL_DIRECT); expanded = false })
                DropdownMenuItem(text = { Text("OCR · 识字后解题") }, onClick = { onSelect(ProcessingRoute.OCR_THEN_LLM); expanded = false })
            }
        }
    }
}
