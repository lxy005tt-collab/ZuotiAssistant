package `fun`.kirari.hanako.solve.workflow

import `fun`.kirari.hanako.core.data.WebSearchSettings
import `fun`.kirari.hanako.core.data.defaultAssistant
import `fun`.kirari.hanako.core.data.defaultProvider
import `fun`.kirari.hanako.core.network.UnifiedLLMClient
import `fun`.kirari.hanako.core.model.FollowUpTurn
import `fun`.kirari.hanako.core.model.AnswerVersion
import `fun`.kirari.hanako.core.model.ProcessingResult
import `fun`.kirari.hanako.core.model.ProcessingRoute
import `fun`.kirari.hanako.core.model.ContentAnchor
import `fun`.kirari.hanako.core.model.QuotedFragment
import `fun`.kirari.hanako.core.model.RichTextBlockKind
import `fun`.kirari.hanako.solve.model.ConversationIntent
import `fun`.kirari.hanako.solve.workflow.tools.AgentTool
import `fun`.kirari.hanako.solve.workflow.tools.ToolContext
import `fun`.kirari.hanako.solve.workflow.tools.ToolResult
import `fun`.kirari.hanako.core.network.ToolRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class ConversationWorkflowTest {

    private val attachments = FakeConversationAttachments()
    private val workflow = ConversationWorkflow(
        unifiedClient = UnifiedLLMClient(),
        pipeline = ProcessingPipeline(),
        attachments = attachments
    )

    @Test
    fun prepareTurn_buildsOrderedOcrConversationAndOmitsInvalidAssistantMessages() = runTest {
        val existing = baseResult().copy(
            extractedText = "question text",
            answer = "initial answer",
            followUpTurns = listOf(
                FollowUpTurn(userText = "first", assistantText = "first answer", completed = true),
                FollowUpTurn(userText = "partial", assistantText = "unfinished", completed = false),
                FollowUpTurn(userText = "failed", assistantText = "ignored", completed = true, errorMessage = "error")
            )
        )

        val prepared = workflow.prepareTurn(
            existingResult = existing,
            models = models(),
            intent = ConversationIntent.NewTurn("current"),
            turnId = "turn-current"
        )

        assertEquals(
            listOf("system", "user", "assistant", "user", "assistant", "user", "user", "user"),
            prepared.messages.map { it.role }
        )
        assertTrue(prepared.messages[1].text().contains("question text"))
        assertEquals("initial answer", prepared.messages[2].text())
        assertEquals(listOf("first", "partial", "failed", "current"), prepared.messages.drop(3).filter { it.role == "user" }.map { it.text() })
        assertEquals("turn-current", prepared.startedResult.followUpTurns.last().id)
    }

    @Test
    fun prepareTurn_regeneratesLatestTurnAndKeepsItsVersions() = runTest {
        val existing = baseResult().copy(
            followUpTurns = listOf(
                FollowUpTurn(userText = "keep", assistantText = "kept answer", completed = true),
                FollowUpTurn(
                    userText = "retry",
                    assistantText = "legacy answer",
                    assistantVersions = listOf(AnswerVersion("first answer"), AnswerVersion("second answer")),
                    completed = true
                )
            )
        )

        val prepared = workflow.prepareTurn(
            existingResult = existing,
            models = models(),
            intent = ConversationIntent.RegenerateLatest,
            turnId = "replacement"
        )

        assertEquals(listOf("keep", "retry"), prepared.startedResult.followUpTurns.map { it.userText })
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), prepared.messages.map { it.role })
        assertEquals("replacement", prepared.startedResult.followUpTurns.last().id)
        assertEquals(
            listOf("first answer", "second answer"),
            prepared.startedResult.followUpTurns.last().assistantVersions.map { it.text }
        )
        assertEquals("", prepared.startedResult.followUpTurns.last().assistantText)
        assertEquals("retry", prepared.messages.last().text())
    }

    @Test
    fun prepareTurn_keepsPlainUserTextAndAddsQuotedSnapshotToPrompt() = runTest {
        val quote = QuotedFragment(
            anchor = ContentAnchor(
                historyId = "history-1",
                messageId = "answer",
                answerVersionId = "version-1",
                blockId = "block-1",
                blockKind = RichTextBlockKind.DISPLAY_MATH,
                sourceRevision = 1L,
                rawMarkdown = "x^2 + y^2",
                previewLabel = "[公式]"
            )
        )
        val prepared = workflow.prepareTurn(
            existingResult = baseResult(),
            models = models(),
            intent = ConversationIntent.NewTurn("请解释", listOf(quote)),
            turnId = "turn-with-quote"
        )

        assertEquals("请解释", prepared.startedResult.followUpTurns.last().userText)
        assertEquals(listOf(quote), prepared.startedResult.followUpTurns.last().quotedFragments)
        assertTrue(prepared.messages.last().text().contains("[引用片段]"))
        assertTrue(prepared.messages.last().text().contains("x^2 + y^2"))
        assertTrue(prepared.messages.last().text().endsWith("请解释"))
    }

    @Test
    fun consecutiveScreenshots_keepImagesAndAnswersInTheirOriginalTurns() = runTest {
        val existing = baseResult().copy(
            route = ProcessingRoute.MULTIMODAL_DIRECT,
            screenshotPaths = listOf("question.jpg"), answer = "first answer",
            followUpTurns = listOf(FollowUpTurn(userText = "second screen", screenshotPaths = listOf("page2.jpg"),
                assistantText = "second answer", completed = true))
        )
        val prepared = workflow.prepareTurn(existing, models(),
            ConversationIntent.NewTurn("third screen", screenshotPaths = listOf("page3.jpg")), "third")

        assertNull(prepared.preparationError)
        assertEquals(existing.id, prepared.startedResult.id)
        assertEquals(listOf("question.jpg"), prepared.startedResult.allScreenshotPaths)
        assertEquals(listOf("question.jpg", "page2.jpg", "page3.jpg"), prepared.startedResult.conversationScreenshotPaths)
        assertEquals(listOf("system", "user", "assistant", "user", "assistant", "user"), prepared.messages.map { it.role })
        assertEquals(listOf("question.jpg"), prepared.messages[1].images())
        assertEquals("first answer", prepared.messages[2].text())
        assertEquals(listOf("page2.jpg"), prepared.messages[3].images())
        assertEquals("second answer", prepared.messages[4].text())
        assertEquals(listOf("page3.jpg"), prepared.messages[5].images())
    }

    @Test
    fun screenshotRetry_reusesImagesAndKeepsEarlierTurnsWithoutDuplicatingCurrentTurn() = runTest {
        val existing = baseResult().copy(route = ProcessingRoute.MULTIMODAL_DIRECT,
            screenshotPaths = listOf("original.jpg"), answer = "original answer",
            followUpTurns = listOf(
                FollowUpTurn(userText = "explain", assistantText = "explanation", completed = true),
                FollowUpTurn(userText = "new screen", screenshotPaths = listOf("detail.jpg"),
                    assistantText = "answer to replace", completed = true)))
        val prepared = workflow.prepareTurn(existing, models(), ConversationIntent.RegenerateLatest, "retry")

        assertNull(prepared.preparationError)
        assertEquals(2, prepared.startedResult.followUpTurns.size)
        assertEquals(listOf("detail.jpg"), prepared.startedResult.followUpTurns.last().screenshotPaths)
        assertEquals(listOf("original.jpg", "detail.jpg"), prepared.startedResult.conversationScreenshotPaths)
        assertEquals(listOf("original answer", "explanation"), prepared.messages.filter { it.role == "assistant" }.map { it.text() })
        assertEquals(listOf("detail.jpg"), prepared.messages.last().images())
    }

    @Test
    fun ocrScreenshots_keepOriginalTextAndRecognizeOnlyNewImage_retriesReuseSavedText() = runTest {
        val existing = baseResult().copy(extractedText = "original conditions", answer = "initial answer")
        val prepared = workflow.prepareTurn(existing, models(),
            ConversationIntent.NewTurn("continued", screenshotPaths = listOf("options.jpg")), "capture")

        assertNull(prepared.preparationError)
        assertTrue(prepared.messages[1].text().contains("original conditions"))
        assertTrue(prepared.messages.last().text().contains("OCR:options.jpg"))
        assertTrue(prepared.messages.all { it.images().isEmpty() })
        assertEquals("OCR:options.jpg", prepared.startedResult.followUpTurns.last().extractedText)

        val retry = workflow.prepareTurn(prepared.startedResult, models(), ConversationIntent.RegenerateLatest, "retry")
        assertNull(retry.preparationError)
        assertEquals(listOf(listOf("options.jpg")), attachments.recognized)
        assertTrue(retry.messages.last().text().contains("OCR:options.jpg"))
        assertEquals(1, retry.startedResult.followUpTurns.size)
    }

    @Test
    fun failedOcr_keepsImageForRetryAndDoesNotSendIncompleteContext() = runTest {
        val failure = IllegalStateException("OCR unavailable")
        attachments.failure = failure
        val prepared = workflow.prepareTurn(baseResult(), models(),
            ConversationIntent.NewTurn("capture", screenshotPaths = listOf("retry.jpg")), "failed")

        assertSame(failure, prepared.preparationError)
        assertTrue(prepared.messages.isEmpty())
        assertEquals(listOf("retry.jpg"), prepared.startedResult.followUpTurns.last().screenshotPaths)
        val thrown = runCatching { workflow.runTurn(prepared) { error("No delta expected") } }.exceptionOrNull()
        assertSame(failure, thrown)

        attachments.failure = null
        val retry = workflow.prepareTurn(prepared.startedResult, models(), ConversationIntent.RegenerateLatest, "recovered")
        assertNull(retry.preparationError)
        assertEquals(1, retry.startedResult.followUpTurns.size)
        assertTrue(retry.messages.last().text().contains("OCR:retry.jpg"))
    }

    @Test
    fun nextScreenshot_recoversOriginalOcrIfFirstCaptureFailedBeforeRecognition() = runTest {
        val prepared = workflow.prepareTurn(baseResult().copy(screenshotPaths = listOf("original.jpg")), models(),
            ConversationIntent.NewTurn("next", screenshotPaths = listOf("next.jpg")), "capture")
        assertNull(prepared.preparationError)
        assertTrue(prepared.messages[1].text().contains("OCR:original.jpg"))
        assertTrue(prepared.messages.last().text().contains("OCR:next.jpg"))
        assertEquals(listOf(listOf("original.jpg"), listOf("next.jpg")), attachments.recognized)
    }

    @Test
    fun textFollowUp_afterScreenshotStillContainsAllImagesAndTheirAnswers() = runTest {
        val existing = baseResult().copy(route = ProcessingRoute.MULTIMODAL_DIRECT,
            screenshotPaths = listOf("first.jpg"), answer = "first answer",
            followUpTurns = listOf(FollowUpTurn(userText = "capture", screenshotPaths = listOf("second.jpg"),
                assistantText = "second answer", completed = true)))
        val prepared = workflow.prepareTurn(existing, models(), ConversationIntent.NewTurn("why?"), "text")

        assertNull(prepared.preparationError)
        assertEquals(listOf("first.jpg", "second.jpg"), prepared.messages.flatMap { it.images() })
        assertEquals("why?", prepared.messages.last().text())
        assertTrue(prepared.messages.last().images().isEmpty())
    }

    @Test
    fun screenshotTurns_survivePersistenceAndOldTextOnlyHistoryRemainsReadable() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val legacy = json.decodeFromString<FollowUpTurn>("""{"userText":"old question","assistantText":"old answer","completed":true}""")
        assertTrue(legacy.screenshotPaths.isEmpty())
        assertEquals("", legacy.extractedText)
        val original = baseResult().copy(screenshotPath = "first.jpg", followUpTurns = listOf(
            legacy, FollowUpTurn(userText = "capture", screenshotPaths = listOf("second.jpg"), extractedText = "details")))
        val restored = json.decodeFromString<ProcessingResult>(json.encodeToString(ProcessingResult.serializer(), original))
        assertEquals(original, restored)
        assertEquals(listOf("first.jpg", "second.jpg"), restored.conversationScreenshotPaths)
    }

    @Test
    fun missingImage_failsExplicitlyInsteadOfSendingContextWithoutIt() = runTest {
        attachments.missingImage = "lost.jpg"
        val original = baseResult().copy(route = ProcessingRoute.MULTIMODAL_DIRECT, screenshotPaths = listOf("lost.jpg"))
        val prepared = workflow.prepareTurn(original, models(),
            ConversationIntent.NewTurn("capture", screenshotPaths = listOf("new.jpg")), "capture")
        assertNotNull(prepared.preparationError)
        assertTrue(prepared.messages.isEmpty())
        assertEquals(listOf("new.jpg"), prepared.startedResult.followUpTurns.last().screenshotPaths)
        assertFalse(prepared.startedResult.followUpTurns.last().completed)
    }

    @Test
    fun streamedScreenshotRequest_sendsPriorImageAnswerAndNewImageOverHttp() = runBlocking {
        val server = MockWebServer()
        server.enqueue(streamResponse("""{"choices":[{"delta":{"content":"continued answer"},"index":0}]}"""))
        server.start()
        try {
            val provider = defaultProvider().copy(baseUrl = server.url("/v1").toString(), apiKey = "test-key")
            val prepared = workflow.prepareTurn(baseResult().copy(route = ProcessingRoute.MULTIMODAL_DIRECT,
                screenshotPaths = listOf("first.jpg"), answer = "prior answer"),
                models().copy(visionProvider = provider),
                ConversationIntent.NewTurn("capture", screenshotPaths = listOf("next.jpg")), "next")
            val deltas = StringBuilder()
            val answer = withTimeout(10_000L) { workflow.runTurn(prepared) { deltas.append(it) } }
            assertEquals("continued answer", answer)
            assertEquals(answer, deltas.toString())
            val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).body.readUtf8()
            assertTrue(request.contains("data:image/jpeg;base64,first.jpg"))
            assertTrue(request.contains("data:image/jpeg;base64,next.jpg"))
            assertTrue(request.contains("prior answer"))
            assertFalse(Json.parseToJsonElement(request).jsonObject.containsKey("tools"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun searchAndFallback_keepFullScreenshotConversationOnEveryRequest() = runBlocking {
        val server = MockWebServer()
        server.enqueue(streamResponse("""{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_search","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"details\"}"}}]},"index":0}]}"""))
        server.enqueue(streamResponse())
        server.enqueue(streamResponse("""{"choices":[{"delta":{"content":"answer after search"},"index":0}]}"""))
        server.start()
        var invocations = 0
        val tool = object : AgentTool {
            override val name = ToolRegistry.WEB_SEARCH_TOOL.name
            override val definition = ToolRegistry.WEB_SEARCH_TOOL
            override suspend fun invoke(args: JsonObject, ctx: ToolContext): ToolResult {
                invocations++
                assertEquals("details", args.getValue("query").jsonPrimitive.content)
                return ToolResult("SEARCH_EVIDENCE", summary = "details")
            }
        }
        try {
            val withSearch = ConversationWorkflow(UnifiedLLMClient(), ProcessingPipeline(), attachments) { listOf(tool) }
            val provider = defaultProvider().copy(baseUrl = server.url("/v1").toString(), apiKey = "test-key")
            val prepared = withSearch.prepareTurn(baseResult().copy(route = ProcessingRoute.MULTIMODAL_DIRECT,
                screenshotPaths = listOf("first.jpg"), answer = "prior answer"),
                models().copy(visionProvider = provider),
                ConversationIntent.NewTurn("capture", screenshotPaths = listOf("next.jpg")), "next")
            val answer = withTimeout(10_000L) { withSearch.runTurn(prepared) {} }
            assertEquals("answer after search", answer)
            assertEquals(1, invocations)
            repeat(3) { index ->
                val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).body.readUtf8()
                assertTrue(request.contains("data:image/jpeg;base64,first.jpg"))
                assertTrue(request.contains("data:image/jpeg;base64,next.jpg"))
                assertTrue(request.contains("prior answer"))
                if (index > 0) assertTrue(request.contains("SEARCH_EVIDENCE"))
                if (index == 2) assertFalse(Json.parseToJsonElement(request).jsonObject.containsKey("tools"))
            }
        } finally {
            server.shutdown()
        }
    }

    private fun baseResult() = ProcessingResult(
        id = "history-1",
        assistantName = "assistant",
        route = ProcessingRoute.OCR_THEN_LLM
    )

    private fun models(): ProcessingPipeline.ResolvedModels {
        val provider = defaultProvider()
        return ProcessingPipeline.ResolvedModels(
            assistant = defaultAssistant(),
            ocrProvider = provider,
            ocrModel = "ocr",
            textProvider = provider,
            textModel = "text",
            visionProvider = provider,
            visionModel = "vision",
            firstDeltaTimeoutMillis = 1_000L,
            route = ProcessingRoute.OCR_THEN_LLM,
            usingLocalOcr = false,
            trustAllHttpsCertificates = false,
            webSearchSettings = WebSearchSettings()
        )
    }
}

private fun `fun`.kirari.llm.core.ChatMessage.text(): String {
    return requireNotNull(content).jsonArray
        .first { it.jsonObject["type"]?.jsonPrimitive?.content == "input_text" }
        .jsonObject.getValue("text").jsonPrimitive.content
}

private fun `fun`.kirari.llm.core.ChatMessage.images(): List<String> = requireNotNull(content).jsonArray
    .filter { it.jsonObject["type"]?.jsonPrimitive?.content == "input_image" }
    .map { it.jsonObject.getValue("image_url").jsonPrimitive.content.removePrefix("data:image/jpeg;base64,") }

private class FakeConversationAttachments : ConversationAttachments {
    val recognized = mutableListOf<List<String>>()
    var failure: Exception? = null
    var missingImage: String? = null
    override suspend fun encode(paths: List<String>, legacyBase64: String?): List<String> {
        check(missingImage == null || missingImage !in paths) { "Missing image" }
        return paths.ifEmpty { listOfNotNull(legacyBase64) }
    }
    override suspend fun recognize(paths: List<String>, models: ProcessingPipeline.ResolvedModels): String {
        failure?.let { throw it }
        recognized += paths
        return "OCR:${paths.joinToString()}"
    }
}

private fun streamResponse(event: String? = null): MockResponse = MockResponse()
    .setHeader("Content-Type", "text/event-stream")
    .setBody((event?.let { "data: $it\n\n" } ?: "") + "data: [DONE]\n\n")
