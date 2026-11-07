package `fun`.kirari.hanako.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.core.data.ModelPurpose
import `fun`.kirari.hanako.core.data.resolveModelName
import `fun`.kirari.hanako.core.data.resolveModelProvider
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.llm.core.ReasoningEffort
import `fun`.kirari.llm.core.supportedReasoningEfforts

val ReasoningEffort.displayLabel: String
    get() = when (this) {
        ReasoningEffort.AUTO -> "自动"
        ReasoningEffort.LOW -> "低"
        ReasoningEffort.MEDIUM -> "中"
        ReasoningEffort.HIGH -> "高"
        ReasoningEffort.XHIGH -> "极高"
        ReasoningEffort.MAX -> "最高"
    }

@Composable
fun ReasoningEffortControl(
    settings: AppSettings,
    onChange: (ReasoningEffort) -> Unit,
    enabled: Boolean = true,
    route: ProcessingRoute = settings.processingRoute
) {
    val purpose = if (route == ProcessingRoute.OCR_THEN_LLM) ModelPurpose.TEXT else ModelPurpose.VISION
    val provider = settings.resolveModelProvider(purpose)
    val choices = provider?.let { supportedReasoningEfforts(it.kind, settings.resolveModelName(purpose)) }
        ?: listOf(ReasoningEffort.AUTO)
    val selected = settings.reasoningEffort.takeIf { it in choices } ?: ReasoningEffort.AUTO
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("思考：${selected.displayLabel} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(if (effort == selected) "✓ ${effort.displayLabel}" else effort.displayLabel) },
                    onClick = { onChange(effort); expanded = false }
                )
            }
            if (choices.size == 1) {
                DropdownMenuItem(text = { Text("当前模型暂只支持自动") }, onClick = {}, enabled = false)
            }
        }
    }
}
