package `fun`.kirari.hanako.feature.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.ScreenCaptureMethod

@Composable
internal fun CaptureMethodSelector(
    selectedMethod: ScreenCaptureMethod,
    overlayEnabled: Boolean,
    onSelect: (ScreenCaptureMethod) -> Unit
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("截图方案", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = { showHelp = true }) {
                Icon(
                    Icons.Outlined.HelpOutline,
                    contentDescription = "了解两种截图方案的优缺点",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
                    }
                }
                Text(if (selectedMethod == ScreenCaptureMethod.MEDIA_PROJECTION) "由系统提供屏幕截图" else "通过 Shizuku 按需截图",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                TextButton(onClick = { expanded = true }) { Text(if (selectedMethod == ScreenCaptureMethod.MEDIA_PROJECTION) "系统录屏 ▾" else "Shizuku ▾") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("系统录屏") }, onClick = { onSelect(ScreenCaptureMethod.MEDIA_PROJECTION); expanded = false })
                    DropdownMenuItem(text = { Text("Shizuku") }, onClick = { onSelect(ScreenCaptureMethod.SHIZUKU_ADB); expanded = false })
                }
            }
        }
        if (overlayEnabled) Text("切换方案后需重新启动悬浮窗。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showHelp) CaptureMethodHelpDialog(onDismiss = { showHelp = false })
}

@Composable
private fun CaptureMethodHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("截图方案怎么选？") },
        containerColor = MaterialTheme.colorScheme.surface,
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CaptureMethodHelpItem(
                    title = "系统录屏",
                    advantages = "无需安装额外工具，按系统提示授权即可使用。",
                    limitations = "启动新的录屏会话需确认；使用时有屏幕共享提示。"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                CaptureMethodHelpItem(
                    title = "Shizuku",
                    advantages = "按需截取单张图片，无需启动系统录屏会话。",
                    limitations = "需安装、启动 Shizuku 并授权；通过调试启动后，手机重启需重新启动服务。"
                )
                Text(
                    "受保护的页面仍可能无法截取；Shizuku 不保证避开应用检测。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
    )
}

@Composable
private fun CaptureMethodHelpItem(title: String, advantages: String, limitations: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text("优点：$advantages", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("限制：$limitations", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
