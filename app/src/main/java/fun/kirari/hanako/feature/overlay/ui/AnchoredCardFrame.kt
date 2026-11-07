package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.feature.overlay.window.AnchoredCardPlacement
import `fun`.kirari.hanako.feature.overlay.window.CardAttachment

@Composable
internal fun AnchoredCardFrame(placement: AnchoredCardPlacement, content: @Composable () -> Unit) {
    val color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
    val edge = placement.attachment
    val padding = PaddingValues(
        start = if (edge == CardAttachment.RIGHT) 14.dp else 6.dp,
        top = if (edge == CardAttachment.BELOW) 14.dp else 6.dp,
        end = if (edge == CardAttachment.LEFT) 14.dp else 6.dp,
        bottom = if (edge == CardAttachment.ABOVE) 14.dp else 6.dp
    )
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val tail = 9.dp.toPx()
            val path = Path()
            if (edge == CardAttachment.ABOVE || edge == CardAttachment.BELOW) {
                val x = (size.width * placement.pointerFraction).coerceIn(28.dp.toPx(), (size.width - 28.dp.toPx()).coerceAtLeast(28.dp.toPx()))
                val base = if (edge == CardAttachment.ABOVE) size.height - 15.dp.toPx() else 15.dp.toPx()
                val tip = if (edge == CardAttachment.ABOVE) size.height - 3.dp.toPx() else 3.dp.toPx()
                path.moveTo(x - tail, base)
                path.lineTo(x, tip)
                path.lineTo(x + tail, base)
            } else {
                val y = (size.height * placement.pointerFraction).coerceIn(28.dp.toPx(), (size.height - 28.dp.toPx()).coerceAtLeast(28.dp.toPx()))
                val base = if (edge == CardAttachment.LEFT) size.width - 15.dp.toPx() else 15.dp.toPx()
                val tip = if (edge == CardAttachment.LEFT) size.width - 3.dp.toPx() else 3.dp.toPx()
                path.moveTo(base, y - tail)
                path.lineTo(tip, y)
                path.lineTo(base, y + tail)
            }
            path.close()
            drawPath(path, color)
        }
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            shape = RoundedCornerShape(24.dp),
            color = color,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            shadowElevation = 4.dp,
            content = content
        )
    }
}
