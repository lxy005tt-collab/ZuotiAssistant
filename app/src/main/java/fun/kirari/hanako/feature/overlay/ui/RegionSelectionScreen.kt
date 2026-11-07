package `fun`.kirari.hanako.feature.overlay.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable
internal fun RegionSelectionScreen(bitmap: Bitmap, onCancel: () -> Unit, onConfirm: (Bitmap) -> Unit) {
    var viewport by remember(bitmap) { mutableStateOf(IntSize.Zero) }
    var start by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var end by remember(bitmap) { mutableStateOf(Offset.Zero) }
    var submitting by remember(bitmap) { mutableStateOf(false) }
    val imageSize = IntSize(bitmap.width, bitmap.height)
    val selection = selectedImageRegion(imageSize, viewport, start, end)
    val preview = remember(bitmap) { bitmap.asImageBitmap() }
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("框选截图区域", style = MaterialTheme.typography.titleMedium)
            Text(if (selection == null) "拖动框选题目，确认后发送" else "可重新拖动调整范围",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF161616))
            .onSizeChanged { next ->
                if (start != end && viewport != next) {
                    start = reprojectCropPoint(start, imageSize, viewport, next)
                    end = reprojectCropPoint(end, imageSize, viewport, next)
                }
                viewport = next
            }) {
            Image(preview, contentDescription = "待框选的屏幕截图", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit)
            Canvas(Modifier.fillMaxSize().pointerInput(bitmap, viewport) {
                detectDragGestures(
                    onDragStart = { point -> start = point; end = point },
                    onDragCancel = { end = start },
                    onDrag = { change, amount -> change.consume(); end += amount }
                )
            }) {
                val rect = selection?.preview
                val shade = Color.Black.copy(alpha = .4f)
                if (rect == null) {
                    drawRect(Color.Black.copy(alpha = .18f))
                } else {
                    drawRect(shade, size = Size(size.width, rect.top))
                    drawRect(shade, topLeft = Offset(0f, rect.bottom), size = Size(size.width, size.height - rect.bottom))
                    drawRect(shade, topLeft = Offset(0f, rect.top), size = Size(rect.left, rect.height))
                    drawRect(shade, topLeft = Offset(rect.right, rect.top), size = Size(size.width - rect.right, rect.height))
                    drawRect(Color.White, topLeft = rect.topLeft, size = rect.size, style = Stroke(4.dp.toPx()))
                    drawRect(accent, topLeft = rect.topLeft, size = rect.size, style = Stroke(2.dp.toPx()))
                    for (corner in listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight)) {
                        drawCircle(Color.White, radius = 4.dp.toPx(), center = corner)
                        drawCircle(accent, radius = 4.dp.toPx(), center = corner, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("取消") }
            Button(
                onClick = {
                    val rect = selection?.pixels ?: return@Button
                    submitting = true
                    onConfirm(Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height))
                },
                enabled = selection != null && !submitting,
                modifier = Modifier.weight(1.5f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) { Text("发送选区") }
        }
    }
}
