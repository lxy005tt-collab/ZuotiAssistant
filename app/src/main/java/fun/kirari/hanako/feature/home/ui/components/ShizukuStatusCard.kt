package `fun`.kirari.hanako.feature.home.ui.components

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import `fun`.kirari.hanako.platform.capture.shizuku.ShizukuAuthorizationManager
import rikka.shizuku.Shizuku

private const val CAPTURE_PERMISSION_REQUEST = 2107

@Composable
internal fun ShizukuStatusCard() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var running by remember { mutableStateOf(false) }
    var authorized by remember { mutableStateOf(false) }
    fun refresh() {
        running = Shizuku.pingBinder()
        authorized = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        ShizukuAuthorizationManager.setAuthorized(authorized)
    }
    DisposableEffect(lifecycleOwner) {
        val received = Shizuku.OnBinderReceivedListener { refresh() }
        val dead = Shizuku.OnBinderDeadListener { refresh() }
        val permission = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == CAPTURE_PERMISSION_REQUEST) refresh()
        }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        lifecycleOwner.lifecycle.addObserver(observer)
        refresh()
        onDispose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(when {
            !running -> "Shizuku 未运行"
            !authorized -> "Shizuku 已运行 · 尚未授权"
            else -> "Shizuku 已运行 · 已授权"
        }, style = MaterialTheme.typography.bodyMedium)
        if (!running) Text("请先在 Shizuku 中启动服务，再回来开启悬浮窗。", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (running && !authorized) OutlinedButton(onClick = {
                runCatching { Shizuku.requestPermission(CAPTURE_PERMISSION_REQUEST) }.onFailure {
                    Toast.makeText(context, "无法请求授权，请打开 Shizuku 检查状态", Toast.LENGTH_SHORT).show()
                }
            }) { Text("授权本应用") }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent().setComponent(ComponentName(
                        "moe.shizuku.privileged.api", "moe.shizuku.manager.MainActivity")))
                }.onFailure {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/zh-hans/download/")))
                    }.onFailure {
                        Toast.makeText(context, "请先安装 Shizuku", Toast.LENGTH_SHORT).show()
                    }
                }
            }) { Text("打开 Shizuku") }
        }
    }
}
