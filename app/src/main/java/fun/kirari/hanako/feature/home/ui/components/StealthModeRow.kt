package `fun`.kirari.hanako.feature.home.ui.components

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import `fun`.kirari.hanako.feature.overlay.state.OverlayStealthStatus
import `fun`.kirari.hanako.feature.overlay.state.StealthStatus

@Composable
internal fun StealthModeRow(enabled: Boolean, overlayRunning: Boolean, onToggle: (Boolean) -> Unit) {
    val status by OverlayStealthStatus.status.collectAsStateWithLifecycle()
    val description = when {
        !enabled && overlayRunning && status == StealthStatus.UNSUPPORTED -> "隐匿切换失败，请关闭后重新启动悬浮窗。"
        !enabled -> "已关闭；搜题时仍会单独避开悬浮窗。"
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "不支持透明隐匿：需要 Android 12 或更新系统。"
        !overlayRunning -> "开启悬浮窗后检测截图、录屏隐匿支持。"
        status == StealthStatus.UNSUPPORTED -> "不支持透明隐匿：悬浮窗仍可能被截图或录屏。"
        status == StealthStatus.REQUESTED -> "已请求透明隐藏，请用系统截图和录屏确认效果。"
        else -> "正在检测透明隐匿支持…"
    }
    Row(
        Modifier.fillMaxWidth().toggleable(enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("隐匿模式", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled) Text("屏幕照常显示，不含系统键盘。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = null)
    }
}
