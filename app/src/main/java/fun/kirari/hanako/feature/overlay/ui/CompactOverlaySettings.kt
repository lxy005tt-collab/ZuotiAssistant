package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.data.AppSettings
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem
import `fun`.kirari.hanako.feature.overlay.window.BubbleMenuRegistry

@Composable
internal fun CompactOverlaySettings(
    settings: AppSettings,
    onSelect: (BubbleMenuItem) -> Unit,
    onContentHeightChanged: (Int) -> Unit
) {
    var height by remember { mutableStateOf(0) }
    SideEffect { if (height > 0) onContentHeightChanged(height) }
    // Measure the whole icon-and-label row independently of the window's initial height.
    // Otherwise the constrained height feeds back into itself and keeps labels clipped.
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().onSizeChanged { height = it.height }
            .padding(horizontal = 6.dp, vertical = 4.dp)) {
            BubbleMenuRegistry.entries.forEach { entry ->
                val enabled = entry.isEnabled(settings)
                Column(
                    Modifier.weight(1f).heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = enabled, role = Role.Button) { onSelect(entry.item) }
                        .alpha(if (enabled) 1f else .38f)
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Icon(painterResource(entry.iconRes), contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (entry.isChecked(settings)) Color(0xFF2F83E8) else MaterialTheme.colorScheme.onSurface)
                    Text(entry.label, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
