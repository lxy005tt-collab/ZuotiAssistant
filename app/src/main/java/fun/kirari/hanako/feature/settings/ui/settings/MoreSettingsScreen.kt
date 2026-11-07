package `fun`.kirari.hanako.feature.settings.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.FloatingWindowSettings
import `fun`.kirari.hanako.feature.home.presentation.RegisterScrollToTopHandler
import kotlinx.coroutines.launch

@Composable
fun MoreSettingsScreen(
    scrollRoute: String,
    floatingWindowSettings: FloatingWindowSettings,
    trustAllHttpsCertificates: Boolean,
    onUpdateFloatingWindowSettings: (FloatingWindowSettings) -> Unit,
    onToggleTrustAllHttpsCertificates: (Boolean) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var connectionExpanded by rememberSaveable { mutableStateOf(trustAllHttpsCertificates) }
    RegisterScrollToTopHandler(route = scrollRoute) {
        coroutineScope.launch { listState.animateScrollToItem(0) }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            MoreSettingCard(Icons.Default.Adjust, "悬浮窗", "调整入口、答案气泡与显示方式。",
                trailing = {
                    OutlinedButton(onClick = { onUpdateFloatingWindowSettings(FloatingWindowSettings()) }) { Text("重置") }
                }) {
                FloatingWindowSettingsCard(floatingWindowSettings, onUpdateFloatingWindowSettings)
            }
        }
        item {
            MoreSettingCard(Icons.Default.Security, "高级连接", "自建 API 的连接兼容选项。",
                trailing = {
                    TextButton(onClick = { connectionExpanded = !connectionExpanded }) {
                        Text(if (connectionExpanded) "收起" else "展开")
                    }
                }) {
                if (connectionExpanded) TrustAllHttpsSwitch(trustAllHttpsCertificates, onToggleTrustAllHttpsCertificates)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
