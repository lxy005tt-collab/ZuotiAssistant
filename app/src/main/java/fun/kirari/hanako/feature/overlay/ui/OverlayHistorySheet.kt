package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import java.text.DateFormat
import java.util.Date

@Composable
internal fun OverlayHistorySheet(
    uiState: OverlayUiState,
    onOpenResult: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(horizontal = 8.dp)) { Text("‹ 返回当前题目") }
        if (uiState.history.isEmpty()) {
            Text("还没有搜题记录，点击上方「截图搜题」开始。", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.history.sortedByDescending { it.createdAtMillis }, key = { it.id }) { result ->
                    val busy = result.id in uiState.busyHistoryIds
                    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpenResult(result.id) }) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(result.extractedText.ifBlank { result.answer }.ifBlank { "截图题目" },
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(result.assistantName,
                                    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(result.createdAtMillis)),
                                    if (busy) "生成中" else "查看答案与追问").joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
