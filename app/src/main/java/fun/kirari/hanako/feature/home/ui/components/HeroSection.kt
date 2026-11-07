package `fun`.kirari.hanako.feature.home.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.app.ProjectInfo
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod
import `fun`.kirari.hanako.core.model.ProcessingRoute

/** Inline sections on the page surface; no nested cards or decorative containers. */
@Composable
fun HeroSection(
    overlayEnabled: Boolean,
    hasOverlayPermission: Boolean,
    captureMethod: ScreenCaptureMethod,
    staticModeEnabled: Boolean,
    route: ProcessingRoute,
    onSelectRoute: (ProcessingRoute) -> Unit,
    onSelectCaptureMethod: (ScreenCaptureMethod) -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onToggleOverlay: (Boolean) -> Unit,
    onStartAutoMode: () -> Unit,
    updateAvailable: Boolean = false,
    onShowUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ProjectInfo.NAME, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(if (overlayEnabled) "悬浮窗运行中" else "截取题目，直接获取答案",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (updateAvailable) TextButton(onClick = onShowUpdate) { Text("更新") }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.selectableGroup()) {
            Text("解题方式", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            HomeChoiceRow("多模态", "直接识别图片中的题目", route == ProcessingRoute.MULTIMODAL_DIRECT) {
                onSelectRoute(ProcessingRoute.MULTIMODAL_DIRECT)
            }
            HomeChoiceRow("OCR", "先提取文字，再交给模型分析", route == ProcessingRoute.OCR_THEN_LLM) {
                onSelectRoute(ProcessingRoute.OCR_THEN_LLM)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CaptureMethodSelector(captureMethod, overlayEnabled, onSelectCaptureMethod)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("悬浮窗", style = MaterialTheme.typography.labelLarge,
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
        if (captureMethod == ScreenCaptureMethod.SHIZUKU_ADB) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ShizukuStatusCard()
        }
    }
}

@Composable
internal fun HomeChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
