package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.model.currentAssistantText
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import `fun`.kirari.hanako.core.ui.richtext.unwrapCopyMarkers
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.feature.overlay.state.currentAnswerText
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast
import kotlinx.coroutines.delay

@Composable
internal fun ResultOverlaySheet(
    uiState: OverlayUiState,
    onRegenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onComposerVisibleChange: (Boolean) -> Unit,
    onSendFollowUp: () -> Unit,
    onRetryFollowUp: () -> Unit,
    onContentHeightChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var streamedAnswer by remember(uiState.result?.id) { mutableStateOf(uiState.working && !uiState.sendingFollowUp) }
    var bodyHeight by remember { mutableStateOf(0) }
    var actionsHeight by remember { mutableStateOf(0) }
    var composerHeight by remember { mutableStateOf(0) }
    val showComposer = uiState.composerVisible
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        if (!uiState.working && uiState.result != null && uiState.followUpDraft.isNotBlank()) {
            focusManager.clearFocus()
            keyboard?.hide()
            onComposerVisibleChange(false)
            onSendFollowUp()
        }
    }
    val answer = uiState.currentAnswerText()
    val revealed = rememberStreamingReveal(answer, streamedAnswer && uiState.settings.floatingWindow.typewriterEnabled)
    val lastFollowUp = uiState.result?.followUpTurns?.lastOrNull()
    val followUpAnswer = lastFollowUp?.pendingAssistantText.orEmpty()
        .ifBlank { lastFollowUp?.currentAssistantText().orEmpty() }
    var streamedReply by remember(lastFollowUp?.id) { mutableStateOf(uiState.sendingFollowUp) }
    val revealedReply = key(lastFollowUp?.id) {
        rememberStreamingReveal(followUpAnswer, streamedReply && uiState.settings.floatingWindow.typewriterEnabled)
    }
    val copyText = followUpAnswer.ifBlank { answer }
    val idle = !uiState.working && !uiState.capturing && uiState.result?.id !in uiState.busyHistoryIds
    val actionsVisible = !uiState.working && !uiState.capturing && revealed == answer && revealedReply == followUpAnswer
    SideEffect {
        if (uiState.working && !uiState.sendingFollowUp) streamedAnswer = true
        if (uiState.sendingFollowUp) streamedReply = true
        if (bodyHeight > 0) {
            onContentHeightChanged(bodyHeight + (if (actionsVisible) actionsHeight else 0) + (if (showComposer) composerHeight else 0))
        }
    }
    LaunchedEffect(showComposer, uiState.working) {
        if (showComposer && !uiState.working && uiState.result != null) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    val followingReply = uiState.sendingFollowUp || revealedReply != followUpAnswer
    LaunchedEffect(lastFollowUp?.id, followingReply) {
        if (followingReply) {
            withFrameNanos { }
            snapshotFlow { scrollState.maxValue }.collect { bottom ->
                if (!scrollState.isScrollInProgress) scrollState.scrollTo(bottom)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).verticalScroll(scrollState)) {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { bodyHeight = it.height }
                    .padding(start = 10.5.dp, end = 10.5.dp, top = 9.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (answer.isNotBlank()) MarkdownLatexText(revealed, Modifier.fillMaxWidth())
                uiState.result?.followUpTurns?.forEach { turn ->
                    key(turn.id) {
                        Text(if (turn.screenshotPaths.isNotEmpty()) "截图 · ${turn.screenshotPaths.size} 张" else "追问：${turn.userText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        val text = turn.pendingAssistantText.ifBlank { turn.currentAssistantText() }
                        val reply = if (turn.id == lastFollowUp?.id) revealedReply else text
                        if (text.isNotBlank()) MarkdownLatexText(reply, Modifier.fillMaxWidth())
                        turn.errorMessage?.takeIf { it != uiState.error }?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (showComposer && uiState.result == null && !uiState.working) {
                    Text("先截一道题，就可以在这里继续聊。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (showComposer) {
            Box(Modifier.fillMaxWidth().onSizeChanged { composerHeight = it.height }
                .padding(horizontal = 9.dp, vertical = 3.dp)) {
                Surface(shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = uiState.followUpDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f).padding(vertical = 7.dp).focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    decorationBox = { field ->
                        Box {
                            if (uiState.followUpDraft.isEmpty()) Text("继续问…", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            field()
                        }
                    },
                    maxLines = 3,
                    enabled = !uiState.working && uiState.result != null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() })
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                    IconButton(onClick = { send() }, modifier = Modifier.size(40.dp),
                        enabled = !uiState.working && uiState.result != null && uiState.followUpDraft.isNotBlank()) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送追问", modifier = Modifier.size(18.dp))
                    }
                }
                }
                }
            }
        }
        if (actionsVisible) {
            val appearance = remember { Animatable(0f) }
            // The native window owns height/clip animation. Do not animate Compose height
            // every frame: that would repeatedly cancel and restart the window animation.
            LaunchedEffect(Unit) {
                delay(120)
                appearance.animateTo(1f, tween(180))
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
            Row(
                Modifier.fillMaxWidth().onSizeChanged { actionsHeight = it.height }
                    .graphicsLayer {
                        alpha = appearance.value
                        translationY = (1f - appearance.value) * 4.dp.toPx()
                    }
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterHorizontally)
            ) {
                IconButton(
                    onClick = { copyToClipboardWithToast(context, "答案", unwrapCopyMarkers(copyText), "已复制答案") },
                    enabled = copyText.isNotBlank(),
                    modifier = Modifier.width(48.dp).height(32.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制答案", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        onComposerVisibleChange(false)
                        focusManager.clearFocus()
                        keyboard?.hide()
                        if (lastFollowUp != null && !uiState.retryCapturePending) onRetryFollowUp() else onRegenerate()
                    },
                    enabled = idle,
                    modifier = Modifier.width(48.dp).height(32.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        onComposerVisibleChange(!showComposer)
                        if (showComposer) { focusManager.clearFocus(); keyboard?.hide() }
                    },
                    enabled = idle && !uiState.retryCapturePending && uiState.screenshotRetry == null &&
                        uiState.result != null && uiState.result.automationAction == null,
                    modifier = Modifier.width(48.dp).height(32.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_bubble_answer),
                        contentDescription = if (showComposer) "收起追问" else "追问",
                        modifier = Modifier.size(20.dp))
                }
            }
            }
        }
    }
}
