package `fun`.kirari.hanako.feature.settings.ui.settings

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import `fun`.kirari.hanako.core.data.FloatingWindowSettings
import `fun`.kirari.hanako.feature.overlay.window.DualBubbleView
import kotlin.math.roundToInt

@Composable
internal fun FloatingWindowSettingsCard(settings: FloatingWindowSettings, onChange: (FloatingWindowSettings) -> Unit) {
    var draft by remember(settings) { mutableStateOf(settings) }
    val commit = { onChange(draft) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                .padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AndroidView(
                factory = { context ->
                    DualBubbleView(context, draft.sizeScale, {}, {}, {}, { _, _ -> }, {}).apply {
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    }
                },
                update = { it.updateAppearance(draft.sizeScale, draft.backgroundOpacity) },
                modifier = Modifier.size((116 * draft.sizeScale).dp, (60 * draft.sizeScale).dp)
            )
            Box(Modifier.fillMaxWidth().background(Color.White.copy(alpha = draft.backgroundOpacity), RoundedCornerShape(12.dp))
                .padding(12.dp)) {
                Text("答案会显示在这里。", color = Color.Black, fontSize = draft.answerTextSizeSp.sp,
                    lineHeight = (draft.answerTextSizeSp * 1.5f).sp)
            }
        }
        SettingSlider("整体尺寸", draft.sizePercent, 60..100, "${draft.sizePercent}%",
            { draft = draft.copy(sizePercent = it) }, commit)
        SettingSlider("背景不透明度", draft.backgroundOpacityPercent, 60..100, "${draft.backgroundOpacityPercent}%",
            { draft = draft.copy(backgroundOpacityPercent = it) }, commit)
        SettingSlider("答案字号", draft.answerTextSizeSp, 12..22, "${draft.answerTextSizeSp} sp",
            { draft = draft.copy(answerTextSizeSp = it) }, commit)
        Text("同时用于悬浮窗和历史详情中的 AI 回答。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        SwitchSettingRow("逐字显示答案", "关闭后，收到的答案内容会立即显示。", draft.typewriterEnabled,
            { draft = draft.copy(typewriterEnabled = it); commit() })
        SwitchSettingRow("点空白处收起", "收起后保留当前答案，也不会中断生成。", draft.closeOnOutsideTap,
            { draft = draft.copy(closeOnOutsideTap = it); commit() })
    }
}

@Composable
private fun SettingSlider(title: String, value: Int, range: IntRange, valueText: String, onChange: (Int) -> Unit, onCommit: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value.toFloat(), onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(), onValueChangeFinished = onCommit)
    }
}
