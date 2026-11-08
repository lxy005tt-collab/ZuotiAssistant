package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.ModelProviderConfig
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.latestAnswerText
import `fun`.kirari.hanako.core.model.displayedAssistantVersions
import `fun`.kirari.hanako.core.model.latestAssistantText
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.llm.core.ChatMessage
import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import kotlinx.coroutines.CancellationException

internal data class PreparedConversationTurn(
    val historyId: String,
    val turnId: String,
    val startedResult: ProcessingResult,
    val provider: ModelProviderConfig,
    val model: String,
    val messages: List<ChatMessage>,
    val firstDeltaTimeoutMillis: Long,
    val trustAllHttpsCertificates: Boolean,
    val preparationError: Exception? = null,
    val tools: List<AgentTool> = emptyList()
)

internal interface ConversationWorkflowEngine {
    suspend fun prepareTurn(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent,
        turnId: String
    ): PreparedConversationTurn

    suspend fun runTurn(
        turn: PreparedConversationTurn,
        onAnswerDelta: suspend (String) -> Unit
    ): String
}

internal class ConversationWorkflow(
    private val unifiedClient: UnifiedLLMClient,
    private val pipeline: ProcessingPipeline,
    private val attachments: ConversationAttachments,
    private val toolsProvider: (ProcessingPipeline.ResolvedModels) -> List<AgentTool> = { emptyList() }
) : ConversationWorkflowEngine {
    override suspend fun prepareTurn(
        existingResult: ProcessingResult,
        models: ProcessingPipeline.ResolvedModels,
        intent: ConversationIntent,
        turnId: String
    ): PreparedConversationTurn {
        val provider = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> {
                require(models.textModel.isNotBlank()) { "请先在模型设置中配置文本模型" }
                requireNotNull(models.textProvider) { "请先在模型设置中配置文本模型" }
            }
            ProcessingRoute.MULTIMODAL_DIRECT -> {
                pipeline.validateVisionModels(models)
                requireNotNull(models.visionProvider)
            }
        }
        val model = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.textModel
            ProcessingRoute.MULTIMODAL_DIRECT -> models.visionModel
        }
        val assistantPrompt = when (existingResult.route) {
            ProcessingRoute.OCR_THEN_LLM -> models.assistant.textPrompt
            ProcessingRoute.MULTIMODAL_DIRECT -> models.assistant.visionPrompt
        }
        val (retainedTurns, retainedVersions) = when (intent) {
            is ConversationIntent.NewTurn -> Pair(
                existingResult.followUpTurns,
                emptyList()
            )
            ConversationIntent.RegenerateLatest -> {
                val latestTurn = existingResult.followUpTurns.lastOrNull()
                    ?: error("找不到要重试的对话轮次")
                Pair(
                    existingResult.followUpTurns.dropLast(1),
                    latestTurn.displayedAssistantVersions()
                )
            }
        }
        val pendingTurn = FollowUpTurn(
            id = turnId,
            userText = when (intent) {
                is ConversationIntent.NewTurn -> intent.prompt
                ConversationIntent.RegenerateLatest -> existingResult.followUpTurns.lastOrNull()?.userText.orEmpty()
            },
            quotedFragments = when (intent) {
                is ConversationIntent.NewTurn -> intent.quotedFragments
                ConversationIntent.RegenerateLatest -> existingResult.followUpTurns.lastOrNull()?.quotedFragments.orEmpty()
            },
            screenshotPaths = when (intent) {
                is ConversationIntent.NewTurn -> intent.screenshotPaths
                ConversationIntent.RegenerateLatest -> existingResult.followUpTurns.lastOrNull()?.screenshotPaths.orEmpty()
            },
            extractedText = if (intent == ConversationIntent.RegenerateLatest) {
                existingResult.followUpTurns.lastOrNull()?.extractedText.orEmpty()
            } else "",
            assistantVersions = retainedVersions,
            modelSummary = pipeline.buildModelSummary(model, provider.name)
        )
        var started = existingResult.copy(followUpTurns = retainedTurns + pendingTurn)
        val tools = toolsProvider(models)
        var preparationError: Exception? = null
        val messages = try {
            if (existingResult.route == ProcessingRoute.OCR_THEN_LLM) {
                if (started.extractedText.isBlank() && started.allScreenshotPaths.isNotEmpty()) {
                    pipeline.validateOcrThenLlmModels(models)
                    started = started.copy(extractedText = attachments.recognize(started.allScreenshotPaths, models))
                }
                // Persist recognized text on its own turn; a retry reuses it, while
                // an earlier failed OCR turn is recovered before sending new context.
                for (index in started.followUpTurns.indices) {
                    val turn = started.followUpTurns[index]
                    if (turn.screenshotPaths.isNotEmpty() && turn.extractedText.isBlank()) {
                        pipeline.validateOcrThenLlmModels(models)
                        val recognized = attachments.recognize(turn.screenshotPaths, models)
                        started = started.copy(followUpTurns = started.followUpTurns.mapIndexed { i, value ->
                            if (i == index) value.copy(extractedText = recognized) else value
                        })
                    }
                }
            }
            buildMessages(started, if (tools.isNotEmpty()) searchEnabledAssistantPrompt(assistantPrompt) else assistantPrompt)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Return the turn with its images even when OCR/loading fails, so the
            // task manager can save it and retry exactly this turn later.
            preparationError = error
            emptyList()
        }
        return PreparedConversationTurn(
            historyId = existingResult.id,
            turnId = turnId,
            startedResult = started,
            provider = provider,
            model = model,
            messages = messages,
            firstDeltaTimeoutMillis = models.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = models.trustAllHttpsCertificates,
            preparationError = preparationError,
            tools = tools
        )
    }

    override suspend fun runTurn(
        turn: PreparedConversationTurn,
        onAnswerDelta: suspend (String) -> Unit
    ): String {
        turn.preparationError?.let { throw it }
        return AnswerAgentRuntime(unifiedClient).run(
            provider = turn.provider,
            model = turn.model,
            assistantPrompt = "",
            basePrompt = "",
            imagesBase64 = emptyList(),
            firstDeltaTimeoutMillis = turn.firstDeltaTimeoutMillis,
            trustAllHttpsCertificates = turn.trustAllHttpsCertificates,
            tools = turn.tools,
            toolContext = ToolContext(workflowId = turn.historyId, nodeId = "conversation"),
            onAnswerDelta = onAnswerDelta,
            onToolEvents = {},
            initialMessages = turn.messages
        ).answer
    }

    private suspend fun buildMessages(
        result: ProcessingResult,
        assistantPrompt: String
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        messages += textMessage(
            role = "system",
            text = assistantPromptWithCopyMarker(assistantPrompt) +
                "\n\n用户在同一段对话中连续截图或追问。请按消息顺序结合原图、补充截图和已有对话回答。" +
                "最新截图可能补充原题，也可能包含新题；以最新问题为准，不要把不同题目的条件混用。"
        )
        val initialPrompt = when (result.route) {
            ProcessingRoute.OCR_THEN_LLM -> result.extractedText.takeIf(String::isNotBlank)?.let {
                "以下是用户通过悬浮窗提交题目的 OCR 结果，请完成任务：\n$it"
            } ?: "用户通过悬浮窗提交了一道题目。"
            ProcessingRoute.MULTIMODAL_DIRECT -> "请直接基于图片内容完成任务。"
        }
        val images = if (result.route == ProcessingRoute.MULTIMODAL_DIRECT) {
            attachments.encode(result.allScreenshotPaths, result.screenshotBase64).also {
                require(it.isNotEmpty()) { "找不到本轮对话的原始截图，请点击「新题」后重新截图" }
            }
        } else {
            emptyList()
        }
        messages += userMessage(initialPrompt, images)
        messages += textMessage(role = "assistant", text = result.initialAssistantContext())
        result.followUpTurns.forEach { followUp ->
            val text = buildQuotedPrompt(followUp.userText, followUp.quotedFragments) +
                if (followUp.extractedText.isNotBlank()) "\n\n本轮截图的 OCR 内容：\n${followUp.extractedText}" else ""
            val turnImages = if (result.route == ProcessingRoute.MULTIMODAL_DIRECT) {
                attachments.encode(followUp.screenshotPaths)
            } else emptyList()
            messages += userMessage(text, turnImages)
            val assistantText = followUp.latestAssistantText()
            if (followUp.completed && assistantText.isNotBlank() && followUp.errorMessage == null) {
                messages += textMessage(role = "assistant", text = assistantText)
            }
        }
        return messages
    }
}

internal fun buildQuotedPrompt(
    prompt: String,
    quotedFragments: List<`fun`.kirari.hanako.core.model.QuotedFragment>
): String {
    val trimmed = prompt.trim()
    if (quotedFragments.isEmpty()) return trimmed
    val quotes = quotedFragments.joinToString("\n\n") { fragment ->
        "[引用片段]\n${fragment.anchor.rawMarkdown}\n[/引用片段]"
    }
    return "$quotes\n\n$trimmed"
}

private fun ProcessingResult.initialAssistantContext(): String {
    latestAnswerText().takeIf(String::isNotBlank)?.let { return it }
    return buildString {
        if (automationThought.isNotBlank()) append(automationThought)
        automationAction?.text?.takeIf(String::isNotBlank)?.let { actionText ->
            if (isNotEmpty()) append("\n\n")
            append("执行结果：")
            append(actionText)
        }
    }.ifBlank { "首轮处理没有生成文本回答。" }
}
