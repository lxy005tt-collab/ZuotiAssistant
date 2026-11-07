package `fun`.kirari.hanako.feature.settings.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.BuildConfig
import `fun`.kirari.hanako.app.ProjectInfo

@Composable
fun SettingsMenuScreen(
    onNavigateHistory: () -> Unit,
    onNavigateProvider: () -> Unit,
    onNavigateModel: () -> Unit,
    onNavigateWebSearch: () -> Unit,
    onNavigateAssistant: () -> Unit,
    onNavigateMore: () -> Unit,
    onNavigateDebugLogs: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SettingsEntryCard(
                title = "历史记录",
                subtitle = "查看题目、截图和对话",
                icon = Icons.Default.History,
                onClick = onNavigateHistory
            )
        }
        item {
            SettingsEntryCard(
                title = "模型提供方",
                subtitle = "配置 API 地址与模型",
                icon = Icons.Default.Build,
                onClick = onNavigateProvider
            )
        }
        item {
            SettingsEntryCard(
                title = "模型设置",
                subtitle = "为 OCR、文本、多模态分别指定提供方和模型",
                icon = Icons.Default.Memory,
                onClick = onNavigateModel
            )
        }
        item {
            SettingsEntryCard(
                title = "联网搜索",
                subtitle = "配置搜索工具、搜索引擎和 API Key",
                icon = Icons.Default.Search,
                onClick = onNavigateWebSearch
            )
        }
        item {
            SettingsEntryCard(
                title = "助手配置",
                subtitle = "管理助手名称与提示词",
                icon = Icons.Default.Person,
                onClick = onNavigateAssistant
            )
        }
        item {
            SettingsEntryCard(
                title = "更多",
                subtitle = "悬浮窗、答案显示、高级连接",
                icon = Icons.Default.SmartToy,
                onClick = onNavigateMore
            )
        }
        item {
            if (BuildConfig.SHOW_DEBUG_LOGS) {
                SettingsEntryCard(
                    title = "调试日志",
                    subtitle = "查看运行日志，便于排查模型、截图与悬浮窗问题",
                    icon = Icons.Default.BugReport,
                    onClick = onNavigateDebugLogs
                )
            }
        }
        item { ProjectAttribution() }
    }
}

@Composable
private fun ProjectAttribution() {
    val context = LocalContext.current
    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(context, "没有可用的浏览器", Toast.LENGTH_SHORT).show() }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("项目与开源", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("${ProjectInfo.NAME}基于 HanakoAI 改良，遵循 AGPL-3.0 开源协议。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ProjectLinkRow("项目源码", ProjectInfo.NAME) { openLink(ProjectInfo.REPOSITORY_URL) }
            ProjectLinkRow("原始项目", "HanakoAI") { openLink(ProjectInfo.UPSTREAM_URL) }
            ProjectLinkRow("开源许可", "AGPL-3.0") { openLink(ProjectInfo.LICENSE_URL) }
        }
    }
}

@Composable
private fun ProjectLinkRow(title: String, description: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsEntryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
