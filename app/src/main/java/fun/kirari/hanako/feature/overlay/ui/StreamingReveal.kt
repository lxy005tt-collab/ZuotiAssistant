package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** Adaptive catch-up keeps streamed text fluid without adding seconds of artificial latency. */
internal fun nextRevealIndex(text: String, current: Int): Int {
    val remaining = text.length - current
    if (remaining <= 0) return text.length
    var end = (current + maxOf(1, remaining / 8)).coerceAtMost(text.length)
    if (end < text.length && end > 0 && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end++
    return end
}

@Composable
internal fun rememberStreamingReveal(text: String, animate: Boolean): String {
    // Reopening an answer shows the existing prefix immediately; only incoming text animates.
    var shown by remember { mutableStateOf(text) }
    LaunchedEffect(text, animate) {
        if (!animate || !text.startsWith(shown)) {
            shown = text
        } else {
            while (shown.length < text.length) {
                shown = text.substring(0, nextRevealIndex(text, shown.length))
                delay(16L)
            }
        }
    }
    return shown
}
