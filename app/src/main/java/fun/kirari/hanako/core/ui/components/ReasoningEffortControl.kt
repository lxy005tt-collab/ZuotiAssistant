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
import `fun`.kirari.llm.core.effectiveReasoningEffort
import `fun`.kirari.llm.core.reasoningTokenBudget

val ReasoningEffort.displayLabel: String
    get() = when (this) {
        ReasoningEffort.AUTO -> "自动"
        ReasoningEffort.NONE -> "关闭思考"
        ReasoningEffort.MINIMAL -> "极低"
        ReasoningEffort.ON -> "开启思考"
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
    val model = settings.resolveModelName(purpose)
    val choices = provider?.let { supportedReasoningEfforts(it.kind, model, it.baseUrl) }
        ?: listOf(ReasoningEffort.AUTO)
    val selected = provider?.let { effectiveReasoningEffort(it.kind, model, settings.reasoningEffort, it.baseUrl) }
        ?: ReasoningEffort.AUTO
    fun label(effort: ReasoningEffort): String {
        val budget = provider?.let { reasoningTokenBudget(it.kind, model, effort, it.baseUrl) }
        return if (budget != null && budget > 0) "${effort.displayLabel} · $budget token" else effort.displayLabel
    }
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("思考：${selected.displayLabel} ▾")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { effort ->
                DropdownMenuItem(
                    text = { Text(if (effort == selected) "✓ ${label(effort)}" else label(effort)) },
                    onClick = { onChange(effort); expanded = false }
                )
            }
            if (choices.size == 1) {
                DropdownMenuItem(text = { Text(if (model.isBlank()) "请先选择解题模型" else "未识别可调档位，沿用服务默认值") }, onClick = {}, enabled = false)
            }
        }
    }
}
