package `fun`.kirari.hanako.feature.settings.ui.model

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.*
import `fun`.kirari.hanako.core.ui.components.ReasoningEffortControl
import `fun`.kirari.hanako.core.ui.components.SettingsGroup
import `fun`.kirari.llm.core.ReasoningEffort

@Composable
fun ModelSettingsScreen(
    settings: AppSettings,
    onPickModel: (ModelPurpose) -> Unit,
    onClearModel: (ModelPurpose) -> Unit,
    onReasoningEffortChange: (ReasoningEffort) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("多模态可独立解题。使用 OCR 解题时，才需要同时选择文本和 OCR 模型。未使用的模型可以留空。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        listOf(ModelPurpose.VISION, ModelPurpose.TEXT, ModelPurpose.OCR).forEach { purpose ->
            item(key = purpose.name) {
                SettingsGroup {
                    ModelPurposeItem(settings, purpose, { onPickModel(purpose) }, { onClearModel(purpose) })
                }
            }
        }
        item {
            SettingsGroup {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("模型思考强度", style = MaterialTheme.typography.titleSmall)
                ReasoningEffortControl(settings = settings, onChange = onReasoningEffortChange)
                Text("应用于当前解题方式的回答、重试和追问。自动沿用服务默认值；较高强度可能增加等待时间和用量。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            }
        }
    }
}

@Composable
private fun ModelPurposeItem(settings: AppSettings, purpose: ModelPurpose, onPick: () -> Unit, onClear: () -> Unit) {
    val provider = settings.resolveModelProvider(purpose)
    val model = settings.resolveModelName(purpose)
    val isLocal = purpose == ModelPurpose.OCR && settings.ocrModelSelection.isLocalOcrSelection()
    val hasSelection = isLocal || (provider != null && model.isNotBlank())
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${purpose.displayName}模型", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = hasSelection) { Text("留空") }
        }
        Text(when (purpose) {
            ModelPurpose.VISION -> "直接看图并回答，不依赖下面两个模型"
            ModelPurpose.TEXT -> "根据 OCR 识别的文字生成答案"
            ModelPurpose.OCR -> "只负责识字，也可选择本地 OCR"
        }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().clickable(onClick = onPick).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (hasSelection) model else "未选择 · 点击设置", maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                if (hasSelection) Text(if (isLocal) "本地设备" else provider?.name.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = "选择${purpose.displayName}模型",
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}
