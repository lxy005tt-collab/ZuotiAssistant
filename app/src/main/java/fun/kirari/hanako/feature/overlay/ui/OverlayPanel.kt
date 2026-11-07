package `fun`.kirari.hanako.feature.overlay.ui

import `fun`.kirari.hanako.feature.overlay.presentation.OverlayViewModel

import `fun`.kirari.hanako.feature.overlay.state.OverlaySheetMode

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import `fun`.kirari.hanako.core.data.ModelPurpose
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `fun`.kirari.hanako.core.ui.components.ReasoningEffortControl
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import `fun`.kirari.hanako.core.data.resolveModelName
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.feature.overlay.state.BubbleMenuItem

internal enum class AssistantSwitchDirection {
    PREVIOUS,
    NEXT,
    PICKER
}

@Composable
internal fun OverlayPanel(
    viewModel: OverlayViewModel,
    onDismiss: () -> Unit,
    onContentHeightChanged: (Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var modelPickerTarget by remember { mutableStateOf<ModelPurpose?>(null) }
    var moreExpanded by remember { mutableStateOf(false) }
    if (uiState.settingsMenuVisible) {
        AnswerBubbleTheme(enabled = true) {
            CompactOverlaySettings(uiState.settings,
                onSelect = { item ->
                    if (item == BubbleMenuItem.Settings) viewModel.closeSheet()
                    viewModel.handleMenuSelect(item)
                },
                onContentHeightChanged = onContentHeightChanged)
        }
        return
    }
    if (uiState.sheetMode == OverlaySheetMode.RESULT) {
        AnswerBubbleTheme(enabled = true, answerTextSizeSp = uiState.settings.floatingWindow.answerTextSizeSp) {
            Box(Modifier.fillMaxSize()) {
                ResultOverlaySheet(
                    uiState = uiState,
                    onRegenerate = viewModel::regenerateCurrentResult,
                    onDraftChange = viewModel::updateFollowUpDraft,
                    onSendFollowUp = viewModel::sendFollowUp,
                    onRetryFollowUp = viewModel::retryFollowUp,
                    onContentHeightChanged = onContentHeightChanged
                )
            }
        }
        return
    }
    AnswerBubbleTheme(enabled = true) {
        Box(Modifier.fillMaxSize()) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when (uiState.sheetMode) {
                            OverlaySheetMode.HOME -> "Hanako"
                            OverlaySheetMode.CROP -> "框选题目"
                            OverlaySheetMode.RESULT -> if (uiState.working) "正在解题…" else "答案"
                            OverlaySheetMode.HISTORY -> "搜题历史"
                        },
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Box {
                        IconButton(onClick = { moreExpanded = true }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = moreExpanded, onDismissRequest = { moreExpanded = false }) {
                            Row(Modifier.padding(horizontal = 12.dp)) {
                                ReasoningEffortControl(settings = uiState.settings,
                                    onChange = { moreExpanded = false; viewModel.setReasoningEffort(it) },
                                    enabled = !uiState.working,
                                    route = uiState.result?.route ?: uiState.settings.processingRoute)
                            }
                            DropdownMenuItem(text = { Text(if (uiState.settings.webSearch.enabled) "关闭联网搜索" else "开启联网搜索") },
                                enabled = !uiState.working,
                                onClick = { moreExpanded = false; viewModel.toggleWebSearch() })
                            DropdownMenuItem(text = { Text("历史记录") }, enabled = !uiState.working,
                                onClick = { moreExpanded = false; viewModel.openHistory() })
                            DropdownMenuItem(text = { Text("截整屏搜下一题") }, enabled = !uiState.working && !uiState.capturing,
                                onClick = { moreExpanded = false; viewModel.captureAndSolveFullScreen() })
                            if (uiState.working) {
                                DropdownMenuItem(text = { Text("停止生成") }, onClick = { moreExpanded = false; viewModel.cancelAnswer() })
                            }
                        }
                    }
                    IconButton(onClick = viewModel::toggleCardSize, modifier = Modifier.size(48.dp)) {
                        Icon(if (uiState.cardExpanded) Icons.Default.CloseFullscreen else Icons.Default.OpenInFull,
                            contentDescription = if (uiState.cardExpanded) "缩小卡片" else "放大卡片")
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "收起卡片")
                    }
                }
                if (uiState.sheetMode != OverlaySheetMode.HOME && uiState.sheetMode != OverlaySheetMode.RESULT) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp)
                    ) {
                        if (uiState.working) {
                            TextButton(onClick = viewModel::cancelAnswer) { Text("停止生成") }
                        } else {
                            TextButton(onClick = viewModel::captureAndSolveFullScreen, enabled = !uiState.capturing) { Text("新题") }
                        }
                        TextButton(onClick = viewModel::openHistory, enabled = !uiState.working && !uiState.capturing) {
                            Text("历史")
                        }
                        ReasoningEffortControl(
                            settings = uiState.settings,
                            onChange = viewModel::setReasoningEffort,
                            enabled = !uiState.working,
                            route = uiState.result?.route ?: uiState.settings.processingRoute
                        )
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.sheetMode) {
                        OverlaySheetMode.HOME -> Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("左球设置 · 右球截屏解题", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val purpose = if (uiState.settings.processingRoute == ProcessingRoute.OCR_THEN_LLM) ModelPurpose.TEXT else ModelPurpose.VISION
                            Text(uiState.settings.resolveModelName(purpose).ifBlank { "请先在应用内配置模型" },
                                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            ReasoningEffortControl(settings = uiState.settings, onChange = viewModel::setReasoningEffort)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::captureAndSolveFullScreen, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("整屏搜题") }
                                OutlinedButton(onClick = viewModel::openHistory, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("历史记录") }
                            }
                            uiState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        OverlaySheetMode.CROP -> CropOverlaySheet(
                            uiState = uiState,
                            onConfirm = viewModel::process,
                            modelPickerTarget = modelPickerTarget,
                            onModelPickerTargetChange = { modelPickerTarget = it },
                            onSelectAssistant = viewModel::selectAssistant,
                            onSelectPreviousAssistant = viewModel::selectPreviousAssistant,
                            onSelectNextAssistant = viewModel::selectNextAssistant,
                            onUpdateModelSelection = viewModel::updateModelSelectionWithFavorite,
                            onToggleFavoriteModel = viewModel::toggleFavoriteModel,
                            onToggleProcessingRoute = viewModel::toggleProcessingRoute,
                            providerModelsApi = viewModel.providerModelsApi
                        )
                        OverlaySheetMode.RESULT -> Unit
                        OverlaySheetMode.HISTORY -> OverlayHistorySheet(
                            uiState = uiState,
                            onOpenResult = viewModel::openHistoryResult,
                            onBack = viewModel::leaveHistory
                        )
                    }
                }
            }
        }
    }
}

internal fun assistantNameTransform(
    direction: AssistantSwitchDirection
): ContentTransform {
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
        durationMillis = 240,
        easing = FastOutSlowInEasing
    )
    val fadeSpec = tween<Float>(
        durationMillis = 180,
        easing = FastOutSlowInEasing
    )
    return when (direction) {
        AssistantSwitchDirection.PREVIOUS ->
            (slideInHorizontally(animationSpec = slideSpec) { -it / 2 } + fadeIn(fadeSpec))
                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { it / 2 } + fadeOut(fadeSpec))

        AssistantSwitchDirection.NEXT ->
            (slideInHorizontally(animationSpec = slideSpec) { it / 2 } + fadeIn(fadeSpec))
                .togetherWith(slideOutHorizontally(animationSpec = slideSpec) { -it / 2 } + fadeOut(fadeSpec))

        AssistantSwitchDirection.PICKER ->
            fadeIn(fadeSpec).togetherWith(fadeOut(fadeSpec))
    }
}
