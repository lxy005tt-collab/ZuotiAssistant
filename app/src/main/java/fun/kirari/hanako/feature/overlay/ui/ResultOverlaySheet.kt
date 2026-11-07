package `fun`.kirari.hanako.feature.overlay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.model.currentAssistantText
import `fun`.kirari.hanako.R
import `fun`.kirari.hanako.core.ui.richtext.MarkdownLatexText
import `fun`.kirari.hanako.core.ui.richtext.unwrapCopyMarkers
import `fun`.kirari.hanako.feature.overlay.state.OverlayUiState
import `fun`.kirari.hanako.platform.clipboard.copyToClipboardWithToast

@Composable
internal fun ResultOverlaySheet(
    uiState: OverlayUiState,
    onRegenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSendFollowUp: () -> Unit,
    onRetryFollowUp: () -> Unit,
    onContentHeightChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var streamedAnswer by remember(uiState.result?.id) { mutableStateOf(uiState.working) }
    var bodyHeight by remember { mutableStateOf(0) }
    var actionsHeight by remember { mutableStateOf(0) }
    var composerHeight by remember { mutableStateOf(0) }
    var showComposer by remember(uiState.result?.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val send = {
        if (!uiState.working && uiState.followUpDraft.isNotBlank()) {
            focusManager.clearFocus()
            keyboard?.hide()
            showComposer = false
            onSendFollowUp()
        }
    }
    val answer = if (uiState.working && !uiState.sendingFollowUp) {
        uiState.liveAnswerText
    } else {
        uiState.result?.latestAnswerText().orEmpty()
            .ifBlank { uiState.result?.automationAction?.text.orEmpty() }
    }
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
        if (uiState.working) streamedAnswer = true
        if (uiState.sendingFollowUp) streamedReply = true
        if (bodyHeight > 0) {
            onContentHeightChanged(bodyHeight + (if (actionsVisible) actionsHeight else 0) + (if (showComposer) composerHeight else 0))
        }
    }
    LaunchedEffect(showComposer) {
        if (showComposer) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(uiState.result?.followUpTurns?.size, uiState.sendingFollowUp) {
        if (uiState.sendingFollowUp) {
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).verticalScroll(scrollState)) {
            Column(
                Modifier.fillMaxWidth().onSizeChanged { bodyHeight = it.height }
                    .padding(start = 10.5.dp, end = 10.5.dp, top = 9.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    uiState.capturing -> Text("正在截图…", style = MaterialTheme.typography.bodyMedium)
                    answer.isNotBlank() -> MarkdownLatexText(revealed, Modifier.fillMaxWidth())
                    uiState.working -> Text("正在思考…", style = MaterialTheme.typography.bodyMedium)
                    uiState.error == null -> Text("暂无答案", style = MaterialTheme.typography.bodyMedium)
                }
                uiState.result?.followUpTurns?.forEachIndexed { index, turn ->
                    key(turn.id) {
                        Text("追问：${turn.userText}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f))
                        val text = turn.pendingAssistantText.ifBlank { turn.currentAssistantText() }
                        val sending = uiState.sendingFollowUp && index == uiState.result.followUpTurns.lastIndex
                        val reply = if (turn.id == lastFollowUp?.id) revealedReply else text
                        if (sending && text.isBlank()) Text("正在思考…")
                        else if (text.isNotBlank()) MarkdownLatexText(reply, Modifier.fillMaxWidth())
                        turn.errorMessage?.takeIf { it != uiState.error }?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        if (showComposer) {
            Box(Modifier.fillMaxWidth().onSizeChanged { composerHeight = it.height }
                .padding(horizontal = 9.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = uiState.followUpDraft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("继续问…") },
                    maxLines = 3,
                    enabled = !uiState.working,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    trailingIcon = {
                        IconButton(onClick = { send() }, enabled = !uiState.working && uiState.followUpDraft.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送追问", modifier = Modifier.size(18.dp))
                        }
                    }
                )
            }
        }
        if (actionsVisible) {
            val appearance = remember { Animatable(0f) }
            LaunchedEffect(Unit) { appearance.animateTo(1f, tween(160)) }
            Row(
                Modifier.fillMaxWidth().onSizeChanged { actionsHeight = it.height }
                    .alpha(appearance.value)
                    .padding(end = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { copyToClipboardWithToast(context, "Hanako 答案", unwrapCopyMarkers(copyText), "已复制答案") },
                    enabled = copyText.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制答案", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        showComposer = false
                        focusManager.clearFocus()
                        keyboard?.hide()
                        if (lastFollowUp != null) onRetryFollowUp() else onRegenerate()
                    },
                    enabled = idle,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重试", modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        showComposer = !showComposer
                        if (!showComposer) { focusManager.clearFocus(); keyboard?.hide() }
                    },
                    enabled = idle && uiState.result != null && uiState.result.automationAction == null,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_bubble_answer),
                        contentDescription = if (showComposer) "收起追问" else "追问",
                        modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
